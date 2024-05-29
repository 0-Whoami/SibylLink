package sibyllink.vnc.ui.vnc

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.opengl.GLES20
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.twotone.KeyboardTab
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Error
import androidx.compose.material.icons.twotone.Keyboard
import androidx.compose.material.icons.twotone.KeyboardArrowDown
import androidx.compose.material.icons.twotone.KeyboardArrowUp
import androidx.compose.material.icons.twotone.KeyboardCommandKey
import androidx.compose.material.icons.twotone.Upload
import androidx.compose.material.icons.twotone.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sibyllink.vnc.model.ServerProfile
import sibyllink.vnc.viewmodel.VncViewModel
import sibyllink.vnc.viewmodel.VncViewModel.State.Companion.isConnected
import sibyllink.vnc.viewmodel.VncViewModel.State.Companion.isDisconnected
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.nio.IntBuffer

@Composable
fun Loading() {

    val alpha by rememberInfiniteTransition(label = "").animateFloat(
        initialValue = 1f, targetValue = 0.1f, animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1000, delayMillis = 500
            ), repeatMode = RepeatMode.Reverse
        ), label = ""
    )
    Box(Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .padding(10.dp)
                .size(30.dp)
        ) {
            drawCircle(
                color = Color.White,
                alpha = alpha,
                style = Stroke(width = 5f),
                radius = size.minDimension / 2 * (1 - alpha)
            )
        }
    }

}

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

class PopupPosition : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize
    ): IntOffset {
        return IntOffset(
            (windowSize.width - popupContentSize.width) / 2, (windowSize.height - popupContentSize.height) / 2
        )
    }
}

/**
 * This activity handles the connection to a VNC server.
 */
class VncActivity : ComponentActivity() {

    lateinit var viewModel: VncViewModel
    private val dispatcher by lazy { Dispatcher(viewModel) }
    val touchHandler by lazy { TouchHandler(viewModel, dispatcher) }
    val keyHandler by lazy { KeyHandler(dispatcher) }
    private var restoredFromBundle = false
    private var wasConnectedWhenStopped = false
    private var onStartTime = 0L
    private lateinit var frameView: FrameView
    private var showMenu by mutableStateOf(false)

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
            if (viewModel.state.collectAsState().value.isConnected) {
                var showKeys by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned {
                            val width = it.size.width.toFloat()
                            viewModel.frameState.setWindowSize(width, width)
                            viewModel.frameState.setViewportSize(width, width)
                        }, contentAlignment = Alignment.BottomCenter
                ) {

                    AndroidView(factory = { frameView })
                    if (showKeys) ButtonArray()
                }
                AnimatedVisibility(showMenu, enter = slideInVertically(), exit = slideOutVertically()) {
                    Popup(
                        properties = PopupProperties(),
                        onDismissRequest = { showMenu = false },
                        popupPositionProvider = PopupPosition()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(.5f), verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Buttons { showMenu = false;showKeyboard() }
                            Buttons(text = "VirtualKeys", icon = Icons.TwoTone.KeyboardCommandKey) {
                                showMenu = false;showKeys = !showKeys
                            }
                            Buttons(icon = Icons.TwoTone.Close, text = "Disconnect") {
                                finish()
                            }
                        }
                    }
                }
            } else Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (viewModel.state.collectAsState().value == VncViewModel.State.Connecting) {
                    Loading()
                } else {
                    Icon(
                        imageVector = Icons.TwoTone.Error, contentDescription = null, tint = MaterialTheme.colors.error
                    )
                    Text(
                        text = "Failed to connect!\nEnsure that you have entered the correct credentials and try connecting again.",
                        textAlign = TextAlign.Center
                    )
                }

            }
        }

        viewModel.frameViewRef = WeakReference(frameView)

        savedInstanceState?.let {
            restoredFromBundle = true
            wasConnectedWhenStopped = it.getBoolean("wasConnectedWhenStopped")
        }
    }

    @Composable
    fun Buttons(text: String = "Keyboard", icon: ImageVector = Icons.TwoTone.Keyboard, onClick: () -> Unit = {}) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(50))
                .padding(5.dp)
                .clickable { onClick() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.Black)
            Text(text = text, color = Color.Black)
        }
    }

    @Composable
    fun IconButton(enable: Boolean = false, icon: ImageVector, onClick: () -> Unit) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .padding(5.dp)
                .background(if (enable) Color(0xf58692fc) else Color.White.copy(alpha = .8f), CircleShape)
                .padding(5.dp)
                .aspectRatio(1f)
                .clickable { onClick() },
            tint = if (enable) Color.White else Color.Black
        )
    }

    @Composable
    fun TextButton(enable: Boolean = false, text: String, onClick: () -> Unit) {
        Text(
            text = text,
            modifier = Modifier
                .aspectRatio(1f)
                .padding(5.dp)
                .background(if (enable) Color(0xf58692fc) else Color.White.copy(alpha = .8f), CircleShape)
                .padding(5.dp)
                .wrapContentHeight()
                .clickable { onClick() },
            color = if (enable) Color.White else Color.Black,
            fontSize = 8.sp,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )

    }

    @Composable
    fun ButtonArray() {
        ScalingLazyColumn(modifier = Modifier.width(70.dp), horizontalAlignment = Alignment.Start) {
            //meta keys
            item {
                var enable by remember { mutableStateOf(false) }; IconButton(
                enable = enable, icon = Icons.TwoTone.Window
            ) { enable = !enable;keyHandler.onKeyEvent(KeyEvent.KEYCODE_META_LEFT, enable) }
            }
            item {
                var enable by remember { mutableStateOf(false) }; IconButton(
                enable = enable, icon = Icons.TwoTone.Upload
            ) { enable = !enable;keyHandler.onKeyEvent(KeyEvent.KEYCODE_SHIFT_RIGHT, enable) }
            }
            item {
                var enable by remember { mutableStateOf(false) }; TextButton(
                enable = enable, text = "Ctrl"
            ) { enable = !enable;keyHandler.onKeyEvent(KeyEvent.KEYCODE_CTRL_RIGHT, enable) }
            }
            item {
                var enable by remember { mutableStateOf(false) }; TextButton(
                enable = enable, text = "Alt"
            ) { enable = !enable;keyHandler.onKeyEvent(KeyEvent.KEYCODE_ALT_RIGHT, enable) }
            }
            item {
                var enable by remember { mutableStateOf(false) }; TextButton(enable = enable, text = "Fn") {
                enable = !enable;keyHandler.onKeyEvent(KeyEvent.KEYCODE_FUNCTION, enable)
            }
            }
            //normal button
            item { IconButton(icon = Icons.AutoMirrored.TwoTone.KeyboardTab) { keyHandler.onKey(KeyEvent.KEYCODE_TAB) } }
            item { TextButton(text = "Esc") { keyHandler.onKey(KeyEvent.KEYCODE_ESCAPE) } }
            item { IconButton(icon = Icons.AutoMirrored.TwoTone.KeyboardArrowLeft) { keyHandler.onKey(KeyEvent.KEYCODE_DPAD_LEFT) } }
            item { IconButton(icon = Icons.TwoTone.KeyboardArrowUp) { keyHandler.onKey(KeyEvent.KEYCODE_DPAD_UP) } }
            item { IconButton(icon = Icons.TwoTone.KeyboardArrowDown) { keyHandler.onKey(KeyEvent.KEYCODE_DPAD_DOWN) } }
            item { IconButton(icon = Icons.AutoMirrored.TwoTone.KeyboardArrowLeft) { keyHandler.onKey(KeyEvent.KEYCODE_DPAD_RIGHT) } }
            item { TextButton(text = "Home") { keyHandler.onKey(KeyEvent.KEYCODE_MOVE_HOME) } }
            item { TextButton(text = "End") { keyHandler.onKey(KeyEvent.KEYCODE_MOVE_END) } }
            item { TextButton(text = "PgUp") { keyHandler.onKey(KeyEvent.KEYCODE_PAGE_UP) } }
            item { TextButton(text = "PgDn") { keyHandler.onKey(KeyEvent.KEYCODE_PAGE_DOWN) } }
            item { TextButton(text = "Insert") { keyHandler.onKey(KeyEvent.KEYCODE_INSERT) } }
            item { TextButton(text = "Delete") { keyHandler.onKey(KeyEvent.KEYCODE_FORWARD_DEL) } }
            items(12) {
                TextButton(text = "F${it + 1}") { keyHandler.onKey(KeyEvent.KEYCODE_F1 + it) }
            }

        }
    }

    @Deprecated("Deprecated in Java")
    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        if (viewModel.state.value.isDisconnected) finish()
        showMenu = !showMenu
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

    private fun captureGLSurfaceView(): Bitmap {
        val w = viewModel.frameState.getSnapshot().vpWidth.toInt()
        val h = viewModel.frameState.getSnapshot().vpHeight.toInt()

        val bitmapBuffer = IntArray(w * h)
        val bitmapSource = IntArray(w * h)
        val intBuffer = IntBuffer.wrap(bitmapBuffer)
        intBuffer.position(0)

        GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, intBuffer)
        var offset1: Int
        var offset2: Int
        for (i in 0 until h) {
            offset1 = i * w
            offset2 = (h - i - 1) * w
            for (j in 0 until w) {
                val texturePixel = bitmapBuffer[offset1 + j]
                val blue = (texturePixel shr 16) and 0xff
                val red = (texturePixel shl 16) and 0x00ff0000
                val pixel = (texturePixel and 0xff00ff00.toInt()) or red or blue
                bitmapSource[offset2 + j] = pixel
            }
        }
        return Bitmap.createBitmap(bitmapSource, w, h, Bitmap.Config.ARGB_8888)
    }


    private fun saveBitmap(bitmap: Bitmap) {
        bitmap.compress(
            Bitmap.CompressFormat.PNG, 100, FileOutputStream(File(filesDir, "/${viewModel.profile.id}.png"))
        )
    }

    override fun onPause() {
        super.onPause()
        frameView.queueEvent {
            saveBitmap(captureGLSurfaceView())
        }
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

    fun showKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        frameView.requestFocus()
        imm.showSoftInput(frameView, 0)

    }


    private var autoReconnecting = false
    private fun autoReconnect(state: VncViewModel.State) {
        if (!state.isDisconnected) return

        // If disconnected when coming back from background, try to reconnect immediately
        if (wasConnectedWhenStopped && (SystemClock.uptimeMillis() - onStartTime) in 0..2000) {
            retryConnection(true)
        }

        if (autoReconnecting) return

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