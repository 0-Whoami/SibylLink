package sibyllink.vnc.ui.utils

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text

@Composable
fun TextField(icon: ImageVector, text:String, placeholder: String, onTextChange:(String)->Unit, keyboardType:KeyboardType = KeyboardType.Text){
    BasicTextField(keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = keyboardType), value = text, textStyle = TextStyle(color = Color.White), onValueChange =onTextChange){
        Row(modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = Color.White, shape = RoundedCornerShape(50))
            .padding(15.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically){
            Icon(imageVector =icon, contentDescription = null )
            it()

        }
        if (text.isEmpty())
            Text(text = placeholder, color = Color.White.copy(0.5f), modifier = Modifier.fillMaxWidth().padding(20.dp), textAlign = TextAlign.Center)
    }
}
@Composable
fun ArcLayout( modifier: Modifier = Modifier, content: @Composable () -> Unit={}
) {
    Layout(modifier = modifier, content = content) { measurables, constraints ->
        val placables = measurables.map { it.measure(constraints) }
        layout(width = constraints.maxWidth, height = constraints.maxHeight) {
            var xOffset=0
            var yOffset=0
            placables.forEach{
                it.placeRelative(xOffset,yOffset)
                xOffset+=it.width
                yOffset+=it.width
            }
        }
    }
}
