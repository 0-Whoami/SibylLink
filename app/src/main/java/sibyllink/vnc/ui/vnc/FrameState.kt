package sibyllink.vnc.ui.vnc

import android.graphics.PointF
import sibyllink.vnc.ui.vnc.FrameState.Snapshot
import kotlin.math.max
import kotlin.math.min

/**
 * Represents current 'view' state of the frame.
 *
 * Terminology
 * ===========
 *
 * Framebuffer: This is the buffer holding pixel data. It resides in native memory.
 *
 * Frame: This is the actual content rendered on screen. It can be thought of as
 * 'rendered framebuffer'. Its size changes based on current [scale] and its position
 * is stored in [frameX] & [frameY].
 *
 * Window: Top-level window of the application/activity.
 *
 * Viewport: This is the area of window where frame is rendered. It is denoted by [FrameView].
 *
 * Safe area: Area inside viewport which is safe for interaction with frame, maintained in safeArea.
 *
 *     Window denotes 'total' area available to our activity, viewport denotes 'visible to user'
 *     area, and safe area denotes 'able to click' area. Most of the time all three will be equal,
 *     but viewport can be smaller than window (e.g. if soft keyboard is visible), and safe area
 *     can be smaller than viewport (e.g. due to display cutout).
 *
 *     +---------------------------+   -   -
 *     |         \Cutout/          |   |   | Viewport
 *     |                           |   |   |   -
 *     |                           |   |   |   | SafeArea
 *     +---------------------------+   |   -   -
 *     |      Soft Keyboard        |   | Window
 *     +---------------------------+   -
 *
 *     Differentiating between these allows us to handle layout changes more easily and cleanly.
 *     We use window size to calculate base scale because we don't want to change scale when
 *     keyboard is shown/hidden. Viewport size is used for rendering the frame, fully immersive.
 *     Safe area is used to coerce frame position so that user can pan every part of frame inside
 *     safe area to interact with it.
 *
 *
 * State & Coordinates
 * ===================
 *
 * Both frame & viewport are in same coordinate space. Viewport is assumed to be fixed
 * in its place with [0,0] represented by top-left corner. Only frame is scaled/moved.
 * To make sure frame does not move off-screen, after each state change, values are
 * coerced within range by [coerceValues].
 *
 * Rendering is done by [sibyllink.vnc.ui.vnc.gl.Renderer] based on these values.
 *
 *
 * Scaling
 * =======
 *
 * Scaling controls the 'size' of rendered frame. It involves multiple factors, like window size,
 * framebuffer size, user choice etc. To achieve best experience, we split scaling in two parts.
 * One automatic, and one user controlled.
 *
 * 1. Base Scale [baseScale] :
 * Motivation behind base scale is to start with the most optimal frame size. It is automatically
 * calculated (and updated) using window size & framebuffer size. When orientation of local
 * device is such that longer edge of the window is aligned with longer edge of the frame,
 * base scale will satisfy following constraints (see [calculateBaseScale]):
 *
 * - Frame is completely visible
 * - Frame's aspect ratio is maintained
 * - Maximum window space is utilized
 *
 * 2. Zoom Scale [zoomScale] :
 * This is the user controlled part. It is updated only in response to pinch gestures.
 *
 * Conceptually, zoom scale works 'on top of' the base scale.
 * Effective scale [scale] is calculated as the product of these two parts, so:

 *      FrameSize = (FramebufferSize * BaseScale) * ZoomScale
 *
 *
 * Thread safety
 * =============
 *
 * Frame state is accessed from two threads: Its properties are updated in UI thread
 * and consumed by the renderer thread. There is a chance that Renderer thread may see
 * half-updated state (e.g. [frameX] is changed inside [pan] but [coerceValues] is not yet called).
 * This half-updated state can cause flickering issues.
 *
 * To avoid this we use [Snapshot]. All updates to frame state are guarded by [lock].
 * Render thread uses [getSnapshot] to retrieve a consistent state to render the frame.
 */
class FrameState(
    private val minZoomScale: Float = 1F, private val maxZoomScale: Float = 6F
) {

    //Frame position, relative to top-left corner (0,0)
    var frameX = 0F
    var frameY = 0F

    //VNC framebuffer size
    var fbWidth = 0F
    var fbHeight = 0F

    //Viewport/FrameView size
    var vpWidth = 0F
    var vpHeight = 0F

    //Size of activity window
    var windowWidth = 0F
    var windowHeight = 0F


    //Scaling
    private var zoomScale = 1F

    private var baseScale = 1F

    val scale get() = baseScale * zoomScale

    /**
     * Immutable wrapper for frame state
     */
    class Snapshot(
        val frameX: Float,
        val frameY: Float,
        val fbWidth: Float,
        val fbHeight: Float,
        val vpWidth: Float,
        val vpHeight: Float,
        val scale: Float
    )

    private val lock = Any()
    private inline fun <T> withLock(block: () -> T) = synchronized(lock) { block() }

    fun setFramebufferSize(w: Float, h: Float) = withLock {
        fbWidth = w
        fbHeight = h
        calculateBaseScale()
        coerceValues()
    }

    fun setViewportSize(w: Float, h: Float) = withLock {
        vpWidth = w
        vpHeight = h
        coerceValues()
    }

    fun setWindowSize(w: Float, h: Float) = withLock {
        windowWidth = w
        windowHeight = h
        calculateBaseScale()
        coerceValues()
    }

    /**
     * Adjust zoom scale according to give [scaleFactor].
     *
     * Returns 'how much' scale factor is actually applied (after coercing).
     */
    fun updateZoom(scaleFactor: Float): Float = withLock {
        val oldScale = zoomScale
        val newScale = zoomScale * scaleFactor

        zoomScale = newScale.coerceIn(minZoomScale, maxZoomScale)

        return zoomScale / oldScale //Applied scale factor
    }
    /**
     * Shift frame by given delta.
     */
    fun pan(deltaX: Float, deltaY: Float) = withLock {
        frameX += deltaX
        frameY += deltaY
    }

    /**
     * Converts given framebuffer point to corresponding point in viewport.
     */
    fun toVP(fbPoint: PointF): PointF {
        return PointF(fbPoint.x * scale + frameX, fbPoint.y * scale + frameY)
    }

    /**
     * Returns immutable & consistent snapshot of frame state.
     */
    fun getSnapshot(): Snapshot = withLock {
        return Snapshot(
            frameX = frameX,
            frameY = frameY,
            fbWidth = fbWidth,
            fbHeight = fbHeight,
            vpWidth = vpWidth,
            vpHeight = vpHeight,
            scale = scale
        )
    }

    private fun calculateBaseScale() {
        if (fbHeight == 0F || fbWidth == 0F || windowHeight == 0F) return  //Not enough info yet

        val s1 = max(windowWidth, windowHeight) / max(fbWidth, fbHeight)
        val s2 = min(windowWidth, windowHeight) / min(fbWidth, fbHeight)

        baseScale = min(s1, s2)
    }

    /**
     * Makes sure state values are within constraints.
     */
    private fun coerceValues() {
        zoomScale = zoomScale.coerceIn(minZoomScale, maxZoomScale)

        frameX = coercePosition(frameX, vpWidth, fbWidth)
        frameY = coercePosition(frameY, vpHeight, fbHeight)
    }

    /**
     * Coerce position value in a direction (horizontal/vertical).
     */
    private fun coercePosition(current: Float, safeMax: Float, fb: Float): Float {
        val scaledFb = (fb * scale)
        val diff = safeMax - scaledFb

        return if (diff >= 0) diff / 2       //Frame will be smaller than safe area, so center it
        else current.coerceIn(diff, 0f) //otherwise, make sure safe area is completely filled.
    }
}