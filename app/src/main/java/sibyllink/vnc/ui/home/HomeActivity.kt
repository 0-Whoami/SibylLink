
package sibyllink.vnc.ui.home

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Edit
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.RevealValue
import androidx.wear.compose.foundation.SwipeToReveal
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.foundation.rememberRevealState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sibyllink.vnc.R
import sibyllink.vnc.model.Database
import sibyllink.vnc.model.ServerProfile
import sibyllink.vnc.ui.vnc.startVncActivity
import sibyllink.vnc.vnc.VncClient
import java.io.File

/**
 * Primary activity of the app.
 *
 * It Provides access to saved and discovered servers.
 */
class HomeActivity : ComponentActivity() {
    var reload by mutableStateOf(false)
    fun reload() {
        reload = !reload
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    @OptIn(ExperimentalWearFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = Database(this)
        val scope = CoroutineScope(Dispatchers.Main)
        setContent {
            val state = rememberScalingLazyListState()
            val listOfServers = remember(reload) { database.getAll() }
            ScalingLazyColumn(
                state = state, modifier = Modifier
                    .fillMaxSize()
                    .rotaryScrollable(
                        RotaryScrollableDefaults.behavior(state), rememberActiveFocusRequester()
                    )
                    .background(Color(0xff0d0d0d)), horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item { Text("Servers") }
                items(listOfServers) {
                    val image = remember(reload) {
                        try {
                            BitmapFactory.decodeFile(File(filesDir, "${it.id}.png").absolutePath).asImageBitmap()
                        } catch (e: Exception) {
                            null
                        }
                    }
                    val revealState = rememberRevealState()
                    SwipeToReveal(state = revealState, primaryAction = {
                        Icon(imageVector = Icons.TwoTone.Delete,
                             contentDescription = null,
                             modifier = Modifier
                                 .clickable { database.deleteServerProfile(it.id);reload() }
                                 .background(MaterialTheme.colors.error, RoundedCornerShape(50))
                                 .fillParentMaxSize()
                                 .wrapContentSize(),
                             tint = MaterialTheme.colors.onError)
                    }, onFullSwipe = {
                        database.deleteServerProfile(it.id);reload()
                        scope.launch {
                            revealState.snapTo(
                                RevealValue.Covered
                            )
                        }
                    }, secondaryAction = {
                        Icon(
                            imageVector = Icons.TwoTone.Edit,
                            contentDescription = null,
                            modifier = Modifier.clickable {
                                startActivity(
                                    Intent(this@HomeActivity, Editor::class.java).putExtra(
                                        "profile", it
                                    )
                                )
                            })
                    }) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(25))
                                .background(Color(0x5f242124))
                                .fillMaxWidth()
                                .clickable {
                                    startNewConnection(it)
                                    finish()
                                },
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (image == null) Image(
                                imageVector = ImageVector.vectorResource(id = R.drawable.ic_launcher_foreground),
                                contentDescription = null,
                                modifier = Modifier.size(75.dp)
                            )
                            else Image(
                                bitmap = image, contentDescription = null, modifier = Modifier.size(75.dp)
                            )
                            Text(text = it.username)
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(imageVector = Icons.Rounded.Add,
                             contentDescription = null,
                             modifier = Modifier
                                 .clip(RoundedCornerShape(25))
                                 .clickable { startActivity(Intent(this@HomeActivity, Editor::class.java)) }
                                 .background(Color(0x5f242124))
                                 .padding(10.dp)
                                 .weight(1f))
                        Icon(
                            imageVector = Icons.TwoTone.Settings,
                            contentDescription = null,
                            modifier = Modifier
                                .clip(RoundedCornerShape(25))
                                .background(Color(0x5f242124))
                                .padding(10.dp)
                        )

                    }
                }
            }


            PositionIndicator(state)
        }
    }

    private fun startNewConnection(profile: ServerProfile) {
        if (checkNativeLib()) startVncActivity(this, profile)
    }

    private fun checkNativeLib(): Boolean {
        return runCatching {
            VncClient.loadLibrary()
        }.onFailure {
            Log.e("lib", "Error Loading Lib")
            finish()
        }.isSuccess
    }

}