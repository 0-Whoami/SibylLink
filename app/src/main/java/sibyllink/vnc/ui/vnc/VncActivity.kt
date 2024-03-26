/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package sibyllink.vnc.ui.vnc

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import sibyllink.vnc.model.ServerProfile
import sibyllink.vnc.viewmodel.VncViewModel
import sibyllink.vnc.viewmodel.VncViewModel.State.Companion.isConnected
import sibyllink.vnc.viewmodel.VncViewModel.State.Companion.isDisconnected
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import kotlin.math.sqrt

/********** [VncActivity] startup helpers *********************************/

private const val PROFILE_KEY = "sibyllink.vnc.server_profile"

fun createVncIntent(context: Context, profile: ServerProfile): Intent {
    return Intent(context, VncActivity::class.java).apply {
        putExtra(PROFILE_KEY, profile)
    }
}

fun startVncActivity(source: Activity, profile: ServerProfile) {
    source.startActivity(createVncIntent(source, profile))
}
/**************************************************************************/


/**
 * This activity handles the connection to a VNC server.
 */
class VncActivity : ComponentActivity() {

    lateinit var viewModel: VncViewModel
    private val dispatcher by lazy { Dispatcher(this) }
    val touchHandler by lazy { TouchHandler(viewModel, dispatcher) }
    val keyHandler by lazy { KeyHandler(dispatcher, viewModel.profile.fLegacyKeySym, viewModel.pref) }
    private var restoredFromBundle = false
    private var wasConnectedWhenStopped = false
    private var onStartTime = 0L
    private lateinit var frameView: FrameView

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        if (!loadViewModel(savedInstanceState)) {
            finish()
            return
        }

        viewModel.initConnection()

        //Main UI
        frameView = FrameView(this)
        frameView.initialize(this)

        setContent {
            Box(modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    val width = it.size.width.toFloat()
                    viewModel.frameState.setWindowSize(width, width)
                    viewModel.frameState.setViewportSize(width, width)
                }) {
                AndroidView(factory = { frameView })

            }
        }

        viewModel.frameViewRef = WeakReference(frameView)
        setupServerUnlock()

        //Observers
        viewModel.loginInfoRequest.observe(this) { showLoginDialog() }
        viewModel.state.observe(this) { onClientStateChanged(it) }

        savedInstanceState?.let {
            restoredFromBundle = true
            wasConnectedWhenStopped = it.getBoolean("wasConnectedWhenStopped")
        }
    }

    override fun onStart() {
        super.onStart()
        frameView.onResume()
        onStartTime = SystemClock.uptimeMillis()

        // Refresh framebuffer on activity restart:
        // - It forces read/write on the socket. This allows us to verify the socket, which might have
        //   been closed by the server while app process was frozen in background
        // - It also attempts to fix some unusual cases of old updates requests being lost while AVNC
        //   was frozen by the system
        if (wasConnectedWhenStopped) viewModel.refreshFrameBuffer()
    }

    override fun onStop() {
        super.onStop()
        frameView.onPause()
        wasConnectedWhenStopped = viewModel.state.value.isConnected
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(PROFILE_KEY, viewModel.profile)
        outState.putBoolean("wasConnectedWhenStopped", wasConnectedWhenStopped || viewModel.state.value.isConnected)
    }

    private fun loadViewModel(savedState: Bundle?): Boolean {
        @Suppress("DEPRECATION") val profile =
            (savedState?.getSerializable(PROFILE_KEY) ?: intent.getSerializableExtra(PROFILE_KEY)) as ServerProfile

        val factory = viewModelFactory { initializer { VncViewModel(profile, application) } }
        viewModel = viewModels<VncViewModel> { factory }.value
        return true
    }

    private fun retryConnection(seamless: Boolean = false) {
        //We simply create a new activity to force creation of new ViewModel
        //which effectively restarts the connection.
        if (!isFinishing) {
//            val savedFrameState = viewModel.frameState.let {
//                SavedFrameState(frameX = it.frameX, frameY = it.frameY, zoom = it.zoomScale)
//            }

            startVncActivity(this, viewModel.profile)

            if (seamless) {
                @Suppress("DEPRECATION") overridePendingTransition(0, 0)
            }
            finish()
        }
    }

    private fun setupServerUnlock() {

        viewModel.serverUnlockRequest.observe(this) {
            viewModel.serverUnlockRequest.offerResponse(true)
        }
    }

    private fun showLoginDialog() {
//        LoginFragment().show(supportFragmentManager, "LoginDialog")
    }

    fun showKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        frameView.requestFocus()
        imm.showSoftInput(frameView, 0)

//        virtualKeys.onKeyboardOpen()
    }

    private fun onClientStateChanged(newState: VncViewModel.State) {
        val isConnected = newState.isConnected

        frameView.isVisible = isConnected
        frameView.keepScreenOn = isConnected && viewModel.pref.viewer.keepScreenOn
//        updateStatusContainerVisibility(isConnected)
        autoReconnect(newState)

//        if (isConnected)
//            ViewerHelp().onConnected(this)

        if (isConnected && !restoredFromBundle) {
            incrementUseCount()
//            restoreFrameState()
        }
    }

    private fun incrementUseCount() {
        viewModel.profile.useCount += 1
    }

//    private fun updateStatusContainerVisibility(isConnected: Boolean) {
//        binding.statusContainer.isVisible = true
//        binding.statusContainer
//                .animate()
//                .alpha(if (isConnected) 0f else 1f)
//                .withEndAction { binding.statusContainer.isVisible = !isConnected }
//    }

//    private fun restoreFrameState() {
//        intent.extras?.let { extras ->
//            BundleCompat.getParcelable(extras, FRAME_STATE_KEY, SavedFrameState::class.java)?.let {
//                viewModel.setZoom(it.zoom)
//                viewModel.panFrame(it.frameX, it.frameY)
//            }
//        }
//    }

    private var autoReconnecting = false
    private fun autoReconnect(state: VncViewModel.State) {
        if (!state.isDisconnected) return

        // If disconnected when coming back from background, try to reconnect immediately
        if (wasConnectedWhenStopped && (SystemClock.uptimeMillis() - onStartTime) in 0..2000) {
            Log.d(javaClass.simpleName, "Disconnected while in background, reconnecting ...")
            retryConnection(true)
        }

        if (autoReconnecting || !viewModel.pref.server.autoReconnect) return

        autoReconnecting = true
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val timeout = 5 //seconds, must be >1
                repeat(timeout) {
//                    binding.autoReconnectProgress.setProgressCompat((100 * it) / (timeout - 1), true)
                    delay(1000)
                    if (it >= (timeout - 1)) retryConnection()
                }
            }
        }
    }


    /************************************************************************************
     * Input
     ************************************************************************************/

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return keyHandler.onKeyEvent(event) || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return keyHandler.onKeyEvent(event) || super.onKeyUp(keyCode, event)
    }

    override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent): Boolean {
        return keyHandler.onKeyEvent(event) || super.onKeyMultiple(keyCode, repeatCount, event)
    }

}