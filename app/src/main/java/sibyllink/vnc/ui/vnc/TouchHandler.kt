
package sibyllink.vnc.ui.vnc

import android.content.Context
import android.graphics.PointF
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import androidx.core.view.InputDeviceCompat
import androidx.core.view.MotionEventCompat
import sibyllink.vnc.util.AppPreferences.swipeSensitivity
import sibyllink.vnc.viewmodel.VncViewModel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max

/**
 * Handler for touch events. It detects various gestures and notifies [dispatcher].
 */
class TouchHandler(val viewModel: VncViewModel, private val dispatcher: Dispatcher) :
    ScaleGestureDetector.OnScaleGestureListener, SimpleOnGestureListener() {

    //Extension to easily access touch position
    private fun MotionEvent.point() = PointF(x, y)

    /****************************************************************************************
     * Touch Event receivers
     ****************************************************************************************/
    fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = handleGestureEvent(event)
        handleGestureStartStop(event)
        return handled
    }

    fun onGenericMotionEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_SCROLL && ev.isFromSource(InputDeviceCompat.SOURCE_ROTARY_ENCODER)) {
            val delta = ev.getAxisValue(MotionEventCompat.AXIS_SCROLL) * 20
            dispatcher.onRotaryScroll(delta)
        }
        return true
    }


    private fun handleGestureStartStop(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> dispatcher.onGestureStart()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dispatcher.onGestureStop()
        }
        dispatcher.mousePosition = event.point()
    }


    /****************************************************************************************
     * Finger Gestures (and everything else beside mouse & stylus)
     ****************************************************************************************/
    private val scaleDetector = ScaleGestureDetector(viewModel.app, this).apply { isQuickScaleEnabled = false }
    private val gestureDetector = GestureDetectorEx(viewModel.app, FingerGestureListener())
    private val swipeVsScale = SwipeVsScale()


    private fun handleGestureEvent(event: MotionEvent): Boolean {
        swipeVsScale.onTouchEvent(event)
        scaleDetector.onTouchEvent(event)
        return gestureDetector.onTouchEvent(event)
    }

    override fun onScaleBegin(detector: ScaleGestureDetector) = true
    override fun onScaleEnd(detector: ScaleGestureDetector) {}
    override fun onScale(detector: ScaleGestureDetector): Boolean {
        if (swipeVsScale.shouldScale()) viewModel.updateZoom(detector.scaleFactor, detector.focusX, detector.focusY)
        return true
    }

    private inner class FingerGestureListener : GestureDetectorEx.GestureListenerEx {

        override fun onSingleTapConfirmed(e: MotionEvent) = dispatcher.onTap1()
        override fun onDoubleTapConfirmed(e: MotionEvent) = dispatcher.onDoubleTap()

        override fun onMultiFingerTap(e: MotionEvent, fingerCount: Int) {
            when (fingerCount) {
                2 -> dispatcher.onTap2()
                // Taps by 3+ fingers are not exposed yet
            }
        }

        override fun onScroll(e2: MotionEvent, dx: Float, dy: Float) {
            val normalizedDx = dx * swipeSensitivity
            val normalizedDy = dy * swipeSensitivity

            when (e2.pointerCount) {
                1 -> dispatcher.onSwipe1(normalizedDx, normalizedDy)
                2 -> if (swipeVsScale.shouldSwipe()) dispatcher.onSwipe2(normalizedDx, normalizedDy)
            }
        }


        override fun onScrollAfterDoubleTap(e1: MotionEvent, e2: MotionEvent, dx: Float, dy: Float) {
            dispatcher.onDoubleTapSwipe(dx, dy)
        }
    }

    /**
     * Stock [GestureDetector] only detects the most common gestures. But we need to
     * detect some more gestures to provide maximum flexibility to the user.
     *
     * [GestureDetectorEx] is used to for this purpose. It internally uses stock
     * [GestureDetector], and some custom event processing to detect more gestures.
     */
    private class GestureDetectorEx(context: Context, val listener: GestureListenerEx) {

        /**
         * Detected gestures. Some of these come directly from stock [GestureDetector],
         * while the following are custom detected:
         *
         * [onDoubleTapConfirmed]
         * To support double-tap-swipe gesture, double-tap is not immediately triggered on
         * ACTION_DOWN of second tap. Instead, [doubleTapDetected] flag is set, and when
         * final ACTION_UP is received (within timeout), [onDoubleTapConfirmed] is called.
         *
         * [onMultiFingerTap]
         * Maximum number of fingers that went down is tracked in [maxFingerDown]. If
         * ACTION_UP is received within a timeout, and more than one finger went down
         * without any scrolling, [onMultiFingerTap] is called.
         *
         * [onLongPressConfirmed]
         * Similar to [onDoubleTapConfirmed], to support long-press-swipe, we wait for
         * ACTION_UP to confirm long-press.
         * Note: [onLongPress] is always called immediately. It enables haptic feedback
         * and supports cases where waiting for [onLongPressConfirmed] is not necessary.
         *
         * [onScrollAfterDoubleTap]
         * This is the double-tap-swipe gesture. If scrolling after [doubleTapDetected] flag
         * is set, [onScrollAfterDoubleTap] is called.
         *
         * [onScrollAfterLongPress]
         * This is the long-press-swipe gesture. If scrolling after [longPressDetected] flag
         * is set, [onScrollAfterLongPress] is called.
         */
        interface GestureListenerEx {
            fun onSingleTapConfirmed(e: MotionEvent)
            fun onDoubleTapConfirmed(e: MotionEvent)
            fun onMultiFingerTap(e: MotionEvent, fingerCount: Int)

            fun onScroll(e2: MotionEvent, dx: Float, dy: Float)

            //            fun onScrollAfterLongPress(e1: MotionEvent, e2: MotionEvent, dx: Float, dy: Float)
            fun onScrollAfterDoubleTap(e1: MotionEvent, e2: MotionEvent, dx: Float, dy: Float)

        }


        /**
         * Stock [GestureDetector] has two unwanted behaviours:
         * - If long-press or double-tap is detected, scroll events will not be reported anymore.
         * - If you don't lift the finger after double-tap, a long-press will be triggered.
         *
         * Fortunately, [GestureDetector] lets us disable long-press detection, which allows us
         * to use a combination of multiple [GestureDetector]s to overcome the restrictions:
         *
         * -                                 +------------------+
         * -                              +->| [innerDetector1] |
         * -                              |  +------------------+
         * -                              |   (tap, long-press)
         * -   +----------------+  event  |
         * -   | [onTouchEvent] |---------+
         * -   +----------------+         |
         * -                              |
         * -                              |  +------------------+  double-tap event   +------------------+
         * -                              +->| [innerDetector2] |-------------------->| [innerDetector3] |
         * -                                 +------------------+                     +------------------+
         * -                                    (double-tap)                           (double-tap-swipe)
         *
         */
        private val innerDetector1 = GestureDetector(context, InnerListener1())
        private val innerDetector2 = GestureDetector(context, InnerListener2()).apply { setIsLongpressEnabled(false) }
        private val innerDetector3 = GestureDetector(context, InnerListener3()).apply { setIsLongpressEnabled(false) }

        private var doubleTapDetected = false
        private var scrolling = false
        private var maxFingerDown = 0
        private var currentDownEvent: MotionEvent? = null


        private inner class InnerListener1 : SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                listener.onSingleTapConfirmed(e)
                return true
            }
        }

        private inner class InnerListener2 : SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                doubleTapDetected = true
                return true
            }

            override fun onDoubleTapEvent(e: MotionEvent) = innerDetector3.onTouchEvent(e)

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float) =
                handleScroll(e1, e2, dx, dy)
        }

        private inner class InnerListener3 : SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float) =
                handleScroll(e1, e2, dx, dy)
        }

        private fun handleScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            e1 ?: return false
            if (!scrolling) {
                scrolling = true
                // Send first scroll event on initial touch-down point, because GestureDetector
                // requires certain amount of finger movement before scroll is triggered, and
                // we don't want to 'loose' that small movement.
                callOnScroll(e1, e1, 0f, 0f)
            }

            callOnScroll(e1, e2, -dx, -dy)
            return true
        }

        private fun callOnScroll(e1: MotionEvent, e2: MotionEvent, dx: Float, dy: Float) {
            if (doubleTapDetected) listener.onScrollAfterDoubleTap(e1, e2, dx, dy)
            else listener.onScroll(e2, dx, dy)
        }

        /**
         * Event receiver
         */
        fun onTouchEvent(e: MotionEvent): Boolean {
            innerDetector1.onTouchEvent(e)
            innerDetector2.onTouchEvent(e)

            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    maxFingerDown = 1
                    currentDownEvent = MotionEvent.obtain(e)
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    maxFingerDown = max(maxFingerDown, e.pointerCount)
                }

                MotionEvent.ACTION_UP -> {
                    currentDownEvent?.let { downEvent ->

                        if (doubleTapDetected && !scrolling && maxFingerDown <= 1) listener.onDoubleTapConfirmed(
                            downEvent
                        )

                        val gestureDuration = (e.eventTime - downEvent.eventTime)
                        if (maxFingerDown > 1 && !scrolling && gestureDuration < ViewConfiguration.getDoubleTapTimeout()) listener.onMultiFingerTap(
                            downEvent, maxFingerDown
                        )
                    }

                    reset()
                }

                MotionEvent.ACTION_CANCEL -> reset()
            }

            return true
        }

        private fun reset() {
            doubleTapDetected = false
            scrolling = false
            maxFingerDown = 0
            currentDownEvent?.recycle()
            currentDownEvent = null
        }
    }


    /**
     * Swipe vs Scale detector.
     *
     * Many two-finger gestures are detected as both swipe & scale gestures because
     * [GestureDetector] & [ScaleGestureDetector] work independently. This works
     * very well when two-finger swipe pref is set to 'pan' (default value).
     * But when the pref is set to 'remote-scroll', this independent detection
     * becomes an issue. When user tries to scale, it frequently triggers remote
     * scrolling. And when user tries to scroll, it triggers scaling.
     *
     * This class tries to clearly differentiate between these two gestures.
     * It works by tracking the fingers, and calculating the angle between two paths.
     * Then [decide] between two gestures by comparing the angle to some thresholds.
     *
     * This class can mis-detect some gestures because fingers don't always move
     * perfectly, but it does provide huge improvement over existing situation.
     */
    private inner class SwipeVsScale {
        private var detecting = false
        private var scaleDetected = false
        private var swipeDetected = false

        private var f1Id = 0 // Finger 1
        private var f2Id = 0 // Finger 2
        private val f1Start = PointF()
        private val f2Start = PointF()
        private val f1Current = PointF()
        private val f2Current = PointF()


        fun onTouchEvent(e: MotionEvent) {

            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    detecting = false
                    scaleDetected = false
                    swipeDetected = false
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    detecting = e.pointerCount == 2
                    if (detecting) {
                        f1Id = e.getPointerId(0)
                        f2Id = e.getPointerId(1)
                        f1Start.set(e.getX(0), e.getY(0))
                        f2Start.set(e.getX(1), e.getY(1))
                        f1Current.set(f1Start)
                        f2Current.set(f2Start)
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (detecting) {
                        val i1 = e.findPointerIndex(f1Id)
                        val i2 = e.findPointerIndex(f2Id)
                        if (i1 != -1 && i2 != -1) {
                            f1Current.set(e.getX(i1), e.getY(i1))
                            f2Current.set(e.getX(i2), e.getY(i2))
                        }
                    }
                }
            }
        }

        fun shouldScale(): Boolean {
            decide()
            return (detecting && scaleDetected)
        }

        fun shouldSwipe(): Boolean {
            decide()
            return (detecting && swipeDetected)
        }

        /**
         * Decides if gesture can be considered a swipe/scale
         */
        private fun decide() {
            if (!detecting) return

            val t1 = theta(f1Start, f1Current)
            val t2 = theta(f2Start, f2Current)
            val diff = abs(t1 - t2)

            scaleDetected = diff > 45
            swipeDetected = diff < 30
        }

        /**
         * Returns the angle made by line [p1]->[p2] with the positive x-axis.
         * Returned angle will be in range [0, 360]
         */
        private fun theta(p1: PointF, p2: PointF): Double {
            val theta = atan2(p2.y - p1.y, p2.x - p1.x)
            val degree = (theta / PI) * 180
            return (degree + 360) % 360 // Map [-180, 180] to [0, 360]
        }
    }
}
