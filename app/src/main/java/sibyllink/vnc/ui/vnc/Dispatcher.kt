/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

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
class Dispatcher(private val activity: VncActivity) {

    private val viewModel = activity.viewModel
    private val profile = viewModel.profile
    private val messenger = viewModel.messenger


    private val relativeMode = RelativeMode()

    var mousePosition=PointF(0f,0f)

    /**************************************************************************
     * Event receivers
     **************************************************************************/
    fun onGestureStart() = relativeMode.onGestureStart()
    fun onGestureStop(p: PointF) {
        relativeMode.onGestureStop(p)
        viewModel.frameState.onGestureStop()
    }

    fun onTap1(p: PointF) =  relativeMode.doClick(PointerButton.Left, p)
    fun onTap2(p: PointF) = relativeMode.doClick(PointerButton.Right, p)
    fun onDoubleTap(p: PointF) = relativeMode.doDoubleClick(PointerButton.Left, p)
    fun onLongPress(p: PointF) {/* relativeMode.doClick(PointerButton.Middle, p)*/ }

    fun onSwipe1(cp: PointF, dx: Float, dy: Float) = relativeMode.doMovePointer(cp, dx, dy)
    fun onSwipe2(sp: PointF, dx: Float, dy: Float) = relativeMode.doRemoteScroll(sp, dx, dy)
    fun onDoubleTapSwipe(cp: PointF, dx: Float, dy: Float) = relativeMode.doRemoteDrag(PointerButton.Left, cp, dx, dy)

    fun onScale(scaleFactor: Float, fx: Float, fy: Float) = doScale(scaleFactor, fx, fy)
    fun onRotaryScroll(dy: Float) = relativeMode.doRemoteScroll(mousePosition, dy, dy)

    fun onXKey(keySym: Int, xtCode: Int, isDown: Boolean) = messenger.sendKey(keySym, xtCode, isDown)

    /**************************************************************************
     * Available actions
     **************************************************************************/

    private fun doOpenKeyboard() = activity.showKeyboard()
    private fun doScale(scaleFactor: Float, fx: Float, fy: Float) = viewModel.updateZoom(scaleFactor, fx, fy)
    private fun doPan(dx: Float, dy: Float) = viewModel.panFrame(dx, dy)

    /**
     * Most actions have the same implementation in both modes, only difference being
     * the point where event is sent. [transformPoint] is used for this mode-specific
     * point selection.
     */
    private abstract inner class AbstractMode {
        //Used for remote scrolling
        private var accumulatedDx = 0F
        private var accumulatedDy = 0F
        private val deltaPerScroll = 20F //For how much dx/dy, one scroll event will be sent

        abstract fun transformPoint(p: PointF): PointF?
        abstract fun doMovePointer(p: PointF, dx: Float, dy: Float)
        abstract fun doRemoteDrag(button: PointerButton, p: PointF, dx: Float, dy: Float)

        open fun onGestureStart() {}
        open fun onGestureStop(p: PointF) = doButtonRelease(p)

        fun doButtonDown(button: PointerButton, p: PointF) {
            transformPoint(p)?.let { messenger.sendPointerButtonDown(button, it) }
        }

        fun doButtonUp(button: PointerButton, p: PointF) {
            transformPoint(p)?.let { messenger.sendPointerButtonUp(button, it) }
        }

        fun doButtonRelease(p: PointF) {
            transformPoint(p)?.let { messenger.sendPointerButtonRelease(it) }
        }

        fun doClick(button: PointerButton, p: PointF) {
            doButtonDown(button, p)
            // Some (obscure) apps seems to ignore click event if button-up is received too early
            if (button == PointerButton.Left && profile.fButtonUpDelay)
                messenger.insertButtonUpDelay()
            doButtonUp(button, p)
        }

        fun doDoubleClick(button: PointerButton, p: PointF) {
            doClick(button, p)
            doClick(button, p)
        }

        fun doRemoteScroll(focus: PointF, dx: Float, dy: Float) {
            accumulatedDx += dx
            accumulatedDy += dy

            //Drain horizontal change
            while (abs(accumulatedDx) >= deltaPerScroll) {
                if (accumulatedDx > 0) {
                    doClick(PointerButton.WheelLeft, focus)
                    accumulatedDx -= deltaPerScroll
                } else {
                    doClick(PointerButton.WheelRight, focus)
                    accumulatedDx += deltaPerScroll
                }
            }

            //Drain vertical change
            while (abs(accumulatedDy) >= deltaPerScroll) {
                if (accumulatedDy > 0) {
                    doClick(PointerButton.WheelUp, focus)
                    accumulatedDy -= deltaPerScroll
                } else {
                    doClick(PointerButton.WheelDown, focus)
                    accumulatedDy += deltaPerScroll
                }
            }
        }

    }

    /**
     * Actions happen at [pointerPosition], which is updated by [doMovePointer].
     */
    private inner class RelativeMode : AbstractMode() {
        private val pointerPosition = PointF(0f, 0f)

        override fun onGestureStart() {
            super.onGestureStart()
            //Initialize with the latest pointer position
            pointerPosition.apply {
                x = viewModel.client.pointerX.toFloat()
                y = viewModel.client.pointerY.toFloat()
            }
            viewModel.client.ignorePointerMovesByServer = true
        }

        override fun onGestureStop(p: PointF) {
            super.onGestureStop(p)
            viewModel.client.ignorePointerMovesByServer = false
        }

        override fun transformPoint(p: PointF) = pointerPosition

        override fun doMovePointer(p: PointF, dx: Float, dy: Float) {
            val xLimit = viewModel.frameState.fbWidth - 1
            val yLimit = viewModel.frameState.fbHeight - 1
            if (xLimit < 0 || yLimit < 0)
                return

            pointerPosition.apply {
                offset(dx, dy)
                x = x.coerceIn(0f, xLimit)
                y = y.coerceIn(0f, yLimit)
            }
            doButtonDown(PointerButton.None, pointerPosition)

            //Try to keep the pointer centered on screen
            val vp = viewModel.frameState.toVP(pointerPosition)
            val centerDiffX = viewModel.frameState.vpWidth/2 - vp.x
            val centerDiffY = viewModel.frameState.vpHeight/2 - vp.y
            viewModel.panFrame(centerDiffX, centerDiffY)
        }

        override fun doRemoteDrag(button: PointerButton, p: PointF, dx: Float, dy: Float) {
            doButtonDown(button, p)
            doMovePointer(p, dx, dy)
        }
    }
}