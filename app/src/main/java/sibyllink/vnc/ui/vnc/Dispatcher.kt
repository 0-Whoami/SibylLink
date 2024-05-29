package sibyllink.vnc.ui.vnc

import android.graphics.PointF
import sibyllink.vnc.viewmodel.VncViewModel
import sibyllink.vnc.vnc.Messenger
import sibyllink.vnc.vnc.PointerButton
import kotlin.math.abs

/**
 * We allow users to customize the actions for different events.
 * This class reads those preferences and invokes proper handlers.
 *
 * Input handling overview:
 *
 *-     +----------------+     +--------------------+     +--------------+
 *-     |  Touch events  |     |     Key events     |     | Virtual keys |
 *-     +----------------+     +--------------------+     +--------------+
 *-             |                        |                        |
 *-             v                        v                        |
 *-     +----------------+     +--------------------+             |
 *-     | [TouchHandler] |     |    [KeyHandler]    |<------------+
 *-     +----------------+     +--------------------+
 *-             |                        |
 *-             |                        v
 *-             |              +--------------------+
 *-             +------------->+    [Dispatcher]    +
 *-                            +--------------------+
 *-                                      |
 *-                                      |
 *-                 +--------------------+---------------------+
 *-                 |                    |                     |
 *-                 v                    v                     v
 *-         +---------------+    +----------------+    +---------------+
 *-         |  [Messenger]  |    | [VncViewModel] |    | [VncActivity] |
 *-         +---------------+    +----------------+    +---------------+
 *-
 *-
 *
 * 1. First we identify which gesture/key was input by the user.
 * 2. Then we select an action based on user preferences. This is done here in [Dispatcher].
 * 3. Then that action is executed. Some actions change local app state (e.g. zoom in/out),
 *    while others send events to remote server (e.g. mouse click).
 */
class Dispatcher(private val viewModel: VncViewModel) {
    private val messenger = viewModel.messenger

    var mousePosition = PointF(0f, 0f)

    /**************************************************************************
     * Event receivers
     **************************************************************************/

    fun onTap1() = doClick(PointerButton.Left)
    fun onTap2() = doClick(PointerButton.Right)
    fun onDoubleTap() = doDoubleClick()
    fun onLongPress() {/* relativeMode.doClick(PointerButton.Middle, p)*/
    }

    fun onSwipe1(dx: Float, dy: Float) = doMovePointer(dx, dy)
    fun onSwipe2(dx: Float, dy: Float) = doRemoteScroll(dx, dy)
    fun onDoubleTapSwipe(dx: Float, dy: Float) = doRemoteDrag(dx, dy)

    fun onRotaryScroll(dy: Float) = doRemoteScroll(0f, dy)

    fun onXKey(keySym: Int, xtCode: Int, isDown: Boolean) = messenger.sendKey(keySym, xtCode, isDown)



    private val pointerPosition = PointF(0f, 0f)

    //Used for remote scrolling
    private var accumulatedDx = 0F
    private var accumulatedDy = 0F
    private val deltaPerScroll = 20F //For how much dx/dy, one scroll event will be sent

    private fun doButtonDown(button: PointerButton) {
        transformPoint().let { messenger.sendPointerButtonDown(button, it) }
    }

    private fun doButtonUp(button: PointerButton) {
        transformPoint().let { messenger.sendPointerButtonUp(button, it) }
    }

    private fun doButtonRelease() {
        transformPoint().let { messenger.sendPointerButtonRelease(it) }
    }

    private fun doClick(button: PointerButton) {
        doButtonDown(button)
        doButtonUp(button)
    }

    private fun doDoubleClick() {
        doClick(PointerButton.Left)
        doClick(PointerButton.Left)
    }

    private fun doRemoteScroll(dx: Float, dy: Float) {
        accumulatedDx += dx
        accumulatedDy += dy

        //Drain horizontal change
        while (abs(accumulatedDx) >= deltaPerScroll) {
            if (accumulatedDx > 0) {
                doClick(PointerButton.WheelLeft)
                accumulatedDx -= deltaPerScroll
            } else {
                doClick(PointerButton.WheelRight)
                accumulatedDx += deltaPerScroll
            }
        }

        //Drain vertical change
        while (abs(accumulatedDy) >= deltaPerScroll) {
            if (accumulatedDy > 0) {
                doClick(PointerButton.WheelUp)
                accumulatedDy -= deltaPerScroll
            } else {
                doClick(PointerButton.WheelDown)
                accumulatedDy += deltaPerScroll
            }
        }
    }

    fun onGestureStart() {
        //Initialize with the latest pointer position
        pointerPosition.apply {
            x = viewModel.client.pointerX.toFloat()
            y = viewModel.client.pointerY.toFloat()
        }
        viewModel.client.ignorePointerMovesByServer = true
    }

    fun onGestureStop() {
        doButtonRelease()
        viewModel.client.ignorePointerMovesByServer = false
    }

    private fun transformPoint() = pointerPosition

    private fun doMovePointer(dx: Float, dy: Float) {
        val xLimit = viewModel.frameState.fbWidth - 1
        val yLimit = viewModel.frameState.fbHeight - 1
        if (xLimit < 0 || yLimit < 0) return

        pointerPosition.apply {
            offset(dx, dy)
            x = x.coerceIn(0f, xLimit)
            y = y.coerceIn(0f, yLimit)
        }
        doButtonDown(PointerButton.None)

        //Try to keep the pointer centered on screen
        val vp = viewModel.frameState.toVP(pointerPosition)
        val centerDiffX = viewModel.frameState.vpWidth / 2 - vp.x
        val centerDiffY = viewModel.frameState.vpHeight / 2 - vp.y
        viewModel.panFrame(centerDiffX, centerDiffY)
    }

    private fun doRemoteDrag(dx: Float, dy: Float) {
        doButtonDown(PointerButton.Left)
        doMovePointer(dx, dy)
    }

}