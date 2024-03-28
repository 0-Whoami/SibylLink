/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package sibyllink.vnc.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import sibyllink.vnc.model.ServerProfile
import sibyllink.vnc.ui.vnc.FrameState
import sibyllink.vnc.ui.vnc.FrameView
import sibyllink.vnc.util.LiveRequest
import sibyllink.vnc.util.getClipboardText
import sibyllink.vnc.util.setClipboardText
import sibyllink.vnc.vnc.Messenger
import sibyllink.vnc.vnc.UserCredential
import sibyllink.vnc.vnc.VncClient
import java.io.IOException
import java.lang.ref.WeakReference

/**
 * ViewModel for VncActivity
 *
 * Connection
 * ==========
 *
 * At construction, we instantiate a [VncClient] referenced by [client]. Then
 * activity starts the connection by calling [initConnection] which starts a coroutine to
 * handle connection setup.
 *
 * After successful connection, we continue to operate normally until the remote
 * server closes the connection, or an error occurs. Once disconnected, we
 * wait for the activity to finish and then cleanup any acquired resources.
 *
 * Currently, lifecycle of [client] is tied to this view model. So one [VncViewModel]
 * manages only one [VncClient].
 *
 *
 * Threading
 * =========
 *
 * Receiver thread :- This thread is started (as a coroutine) in [launchConnection].
 * It handles the protocol initialization, and after that processes incoming messages.
 * Most of the callbacks of [VncClient.Observer] are invoked on this thread. In most
 * cases it is stopped when activity is finished and this view model is cleaned up.
 *
 * Sender thread :- This thread is created (as an executor) by [messenger]. It is
 * used to send messages to remote server. We use this dedicated thread instead
 * of coroutines to preserve the order of sent messages.
 *
 * UI thread :- Main thread of the app. Used for updating UI and controlling other
 * Threads. This is where [frameState] is updated.
 *
 * Renderer thread :- This is managed by [FrameView] and used for rendering frame
 * via OpenGL ES. [frameState] is read from this thread to decide how/where frame
 * should be drawn.
 */
class VncViewModel(val profile: ServerProfile, app: Application) : BaseViewModel(app), VncClient.Observer {

    /**
     * Connection lifecycle:
     *
     *            Created
     *               |
     *               v
     *          Connecting ----------+
     *               |               |
     *               v               |
     *           Connected           |
     *               |               |
     *               v               |
     *          Disconnected <-------+
     *
     */
    enum class State {
        Created,
        Connecting,
        Connected,
        Disconnected;

        companion object {
            val State?.isConnected get() = (this == Connected)
            val State?.isDisconnected get() = (this == Disconnected)
        }
    }

    val client = VncClient(this)

    /**
     * We have two places for connection state (both are synced):
     *
     * [VncClient.connected] - Simple boolean state, used most of the time
     * [state]               - More granular, used by observers & data binding
     */
    val state = MutableStateFlow(State.Created)

    /**
     * Reason for disconnecting.
     */
    var disconnectReason by mutableStateOf("Waiting For Connection")
   /**
     * Fired to unlock saved servers.
     */
    val serverUnlockRequest = LiveRequest<Any?, Boolean>(false, viewModelScope)

    /**
     * Holds a weak reference to [FrameView] instance.
     *
     * This is used to tell [FrameView] to re-render its content when VncClient's
     * framebuffer is updated. Instead of using LiveData/LiveEvent, we keep a
     * weak reference because:
     *
     *      1. It avoids a context-switch to UI thread. Rendering request to
     *         a GlSurfaceView can be sent from any thread.
     *
     *      2. We don't have to invoke the whole ViewModel machinery just for
     *         a single call to FrameView.
     */
    var frameViewRef = WeakReference<FrameView>(null)

    /**
     * Holds information about scaling, translation etc.
     */
    val frameState = FrameState()


    /**
     * Used for sending events to remote server.
     */
    val messenger = Messenger(client)

    /**************************************************************************
     * Connection management
     **************************************************************************/

    /**
     * Initialize VNC connection.
     * It can be called multiple times due to activity restarts.
     */
    fun initConnection() {
        if (state.value == State.Created) {
            state.value = State.Connecting
            frameState.setZoom(profile.zoom1)
            launchConnection()
        }
    }

    private fun launchConnection() {
        viewModelScope.launch(Dispatchers.IO) {

            runCatching {

                preConnect()
                connect()
                processMessages()
            }.onFailure {
                disconnectReason=it.message?:"Unknown Error"
                Log.e("ReceiverCoroutine", "Connection failed", it)
            }

            state.value=State.Disconnected

            //Wait until activity is finished and viewmodel is cleaned up.
            runCatching { awaitCancellation() }
            cleanup()
        }
    }

    private fun preConnect() {
        if (pref.server.lockSavedServer)
            if (!serverUnlockRequest.requestResponse(null))
                throw IOException("Could not unlock server")

        client.configure(profile.viewOnly, profile.securityType, true  /* Hardcoded to true */,
                         profile.imageQuality, profile.useRawEncoding)

        if (profile.useRepeater)
            client.setupRepeater(profile.idOnRepeater)
    }

    private fun connect() {
        when (profile.channelType) {
            ServerProfile.CHANNEL_TCP ->
                client.connect(profile.host, profile.port)

            else -> throw IOException("Unknown Channel: ${profile.channelType}")
        }

        state.value=State.Connected

        // Initial sync, slightly delayed to allow extended clipboard negotiations
//        launchIO { delay(1000L); sendClipboardText() }
    }

    private fun processMessages() {
        while (viewModelScope.isActive)
            client.processServerMessage()
    }

    private fun cleanup() {
        messenger.cleanup()
        client.cleanup()
    }

    /**************************************************************************
     * Frame management
     **************************************************************************/

    fun updateZoom(scaleFactor: Float, fx: Float, fy: Float) {
        if (profile.fZoomLocked) return

        val appliedScaleFactor = frameState.updateZoom(scaleFactor)

        //Calculate how much the focus would shift after scaling
        val dfx = (fx - frameState.frameX) * (appliedScaleFactor - 1)
        val dfy = (fy - frameState.frameY) * (appliedScaleFactor - 1)

        //Translate in opposite direction to keep focus fixed
        frameState.pan(-dfx, -dfy)

        frameViewRef.get()?.requestRender()
    }

    fun resetZoom() {
        frameState.setZoom(1f)
        frameViewRef.get()?.requestRender()
    }

    fun resetZoomToDefault() {
        frameState.setZoom(profile.zoom1)
        frameViewRef.get()?.requestRender()
    }

    fun setZoom(zoom1: Float) {
        frameState.setZoom(zoom1)
        frameViewRef.get()?.requestRender()
    }

    fun panFrame(deltaX: Float, deltaY: Float) {
        frameState.pan(deltaX, deltaY)
        frameViewRef.get()?.requestRender()
    }

    fun moveFrameTo(x: Float, y: Float) {
        frameState.moveTo(x, y)
        frameViewRef.get()?.requestRender()
    }

    fun toggleZoomLock(enabled: Boolean) {
        profile.fZoomLocked = enabled

    }

    fun saveZoom() {
        profile.zoom1 = frameState.zoomScale

    }

    /**************************************************************************
     * Miscellaneous
     **************************************************************************/

    fun sendClipboardText() {
        if (client.connected) launchIO {
            getClipboardText(app)?.let { messenger.sendClipboardText(it) }
        }
    }

    private var clipReceiverJob: Job? = null
    private fun receiveClipboardText(text: String) {

        // This is a protective measure against servers which send every 'selection' made on the server.
        // Setting clip text involves IPC, so these events can exhaust Binder resources, leading to ANRs.
        if (clipReceiverJob?.isActive == true) {
            Log.w(javaClass.simpleName, "Dropping clip text received from server, previous text is still pending")
            return
        }

        clipReceiverJob = launchIO {
            setClipboardText(app, text)
        }
    }


    /**
     * Resize remote desktop to match with local window size (if requested by user).
     * In portrait mode, safe area is used instead of window to exclude the keyboard.
     */
    fun resizeRemoteDesktop() {
        if (profile.resizeRemoteDesktop) frameState.let {
            if (it.windowWidth > it.windowHeight)
                messenger.setDesktopSize(it.windowWidth.toInt(), it.windowHeight.toInt())
        }
    }
    fun refreshFrameBuffer() {
        messenger.refreshFrameBuffer()
    }

    /**************************************************************************
     * [VncClient.Observer] Implementation
     **************************************************************************/

    override fun onPasswordRequired(): String {
        return profile.password
    }

    override fun onCredentialRequired(): UserCredential {
        return profile.let { UserCredential(it.username, it.password) }
    }

    override fun onFramebufferUpdated() {
        frameViewRef.get()?.requestRender()
    }

    override fun onGotXCutText(text: String) {
        receiveClipboardText(text)
    }

    override fun onFramebufferSizeChanged(width: Int, height: Int) {
        launchMain {
            frameState.setFramebufferSize(width.toFloat(), height.toFloat())
        }
    }

    override fun onPointerMoved(x: Int, y: Int) {
        frameViewRef.get()?.requestRender()
    }
}