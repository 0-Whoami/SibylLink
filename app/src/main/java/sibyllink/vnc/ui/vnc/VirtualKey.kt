package sibyllink.vnc.ui.vnc

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.asinh
import kotlin.math.cos
import kotlin.math.sin

private const val spacing=3.14f/50
private const val uplift=5
class VirtualKey(activity: Context):View(activity) {
    val radius=30f
    private var angle=0f
    var a=0f
    private var bottomCenterAngleOffset=(3.14f/4)
    private var scrollby=0f
    private val paint=Paint().apply { color= Color.BLACK }
    private var centerScreen=0
    private var numberOfKeysToShow=0
    private val mapOfMetaKeys= mapOf(KeyEvent.KEYCODE_META_LEFT to false, KeyEvent.KEYCODE_SHIFT_RIGHT to false, KeyEvent.KEYCODE_CTRL_RIGHT to false, KeyEvent.KEYCODE_ALT_RIGHT to false, KeyEvent.KEYCODE_FUNCTION to false)
    private val normalKeys= listOf(KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_INSERT, KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN)
    private val repeatedKeys= listOf(KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)
    private val total_keys=mapOfMetaKeys.size+normalKeys.size+repeatedKeys.size
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        a=w/2f
        centerScreen=w/2
        angle= asinh(radius*2/centerScreen) + spacing
        a -= (radius + uplift)
        bottomCenterAngleOffset=(3.14f-total_keys*angle)/2
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (i in 0..total_keys) {
            val t=angle*i +scrollby + bottomCenterAngleOffset
            if(t<3.14f) {
                paint.alpha = (255 * sin(t)).toInt()
                canvas.drawCircle(centerScreen + (a * cos(t)), centerScreen + (a * sin(t)), radius * sin(t), paint)
            }
        }
    }
    private var scrollX=0f
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action==MotionEvent.ACTION_DOWN){
            scrollX=event.x
        }
        if (event.action==MotionEvent.ACTION_MOVE){
            scrollby+=asinh((scrollX-event.x)/centerScreen)/20
//            if (scrollby !in 0f..6.28f) scrollby=0f
            invalidate()
        }
        return true
    }
}