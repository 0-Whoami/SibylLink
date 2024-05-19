package sibyllink.vnc.ui.home

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Bookmark
import androidx.compose.material.icons.twotone.DesktopWindows
import androidx.compose.material.icons.twotone.Done
import androidx.compose.material.icons.twotone.ImportExport
import androidx.compose.material.icons.twotone.Password
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import sibyllink.vnc.R
import sibyllink.vnc.model.Database
import sibyllink.vnc.model.ServerProfile
import sibyllink.vnc.ui.utils.TextField
import java.io.File

class Editor : ComponentActivity() {
    @OptIn(ExperimentalWearFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val serverProfile = (intent.getSerializableExtra("profile") as ServerProfile?) ?: ServerProfile(-1)
        val database = Database(this)
        val  bitmap= try{BitmapFactory.decodeFile(File(filesDir, "${serverProfile.id}.png").absolutePath).asImageBitmap()} catch (e:Exception) {
            null
        }
        setContent {
            var name by remember { mutableStateOf(serverProfile.username) }
            var host by remember { mutableStateOf(serverProfile.host) }
            var port by remember { mutableStateOf(serverProfile.port.toString()) }
            var password by remember { mutableStateOf(serverProfile.password) }
            val state = rememberScrollState()
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .background(Color(0xff0b0b0b))
                    .verticalScroll(state)
                    .rotaryScrollable(
                        RotaryScrollableDefaults.behavior(state), rememberActiveFocusRequester()
                    )
            ) {
                Spacer(modifier = Modifier.height(50.dp))
                Image(
                    bitmap = bitmap?:ImageBitmap.imageResource(R.drawable.image),
                    contentDescription = null,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(color = Color.White)
                        .zIndex(1f)
                        .size(50.dp)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-15).dp)
                        .padding(10.dp)
                        .background(Color(0xff242124), RoundedCornerShape(25.dp))
                        .padding(5.dp)
                ) {
                    TextField(
                        icon = Icons.TwoTone.Bookmark,
                        text = name,
                        placeholder = "UserName",
                        onTextChange = { name = it })

                    TextField(
                        icon = Icons.TwoTone.DesktopWindows,
                        text = host,
                        placeholder = "Host",
                        onTextChange = { host = it })

                    TextField(
                        icon = Icons.TwoTone.ImportExport,
                        text = port,
                        placeholder = "Port",
                        onTextChange = { port = it },
                        keyboardType = KeyboardType.Number
                    )

                    TextField(
                        icon = Icons.TwoTone.Password,
                        text = password,
                        placeholder = "Password",
                        onTextChange = { password = it })

                    Row(modifier = Modifier
                        .fillMaxWidth()
                        .padding(5.dp)
                        .clickable {
                            if (name.isBlank()) return@clickable
                            if (serverProfile.id == -1) database.addServerProfile(host, port.toInt(), name, password)
                            else database.updateServerProfile(serverProfile.id, host, port.toInt(), name, password)
                            finish()
                        },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.TwoTone.Done, contentDescription = null)
                        Text(text = "Save")
                    }
                }

                Spacer(modifier = Modifier.height(50.dp))
            }
            PositionIndicator(state)
        }
    }

}