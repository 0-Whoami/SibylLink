/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package sibyllink.vnc.ui.home

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Airplay
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Done
import androidx.compose.material.icons.twotone.DriveFileRenameOutline
import androidx.compose.material.icons.twotone.Edit
import androidx.compose.material.icons.twotone.ImportExport
import androidx.compose.material.icons.twotone.LaptopMac
import androidx.compose.material.icons.twotone.MoreVert
import androidx.compose.material.icons.twotone.Password
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Alert
import androidx.wear.compose.material.dialog.Dialog
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.rotaryinput.rotaryWithScroll
import sibyllink.vnc.model.ServerProfile
import sibyllink.vnc.ui.utils.TextField
import sibyllink.vnc.ui.vnc.startVncActivity
import sibyllink.vnc.vnc.VncClient
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class PopupPosition : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize
    ): IntOffset {
        return IntOffset(anchorBounds.right - popupContentSize.width, anchorBounds.bottom)
    }
}

/**
 * Primary activity of the app.
 *
 * It Provides access to saved and discovered servers.
 */
@ExperimentalHorologistApi class HomeActivity : ComponentActivity() {
    val servers = mutableStateListOf<ServerProfile>()
    private fun saveServerList(){
        val serverList= mutableListOf<ServerProfile>().apply { addAll(servers) }
        ObjectOutputStream(FileOutputStream("${filesDir.absoluteFile}/serverList")).use {
            it.writeObject(serverList)
        }
    }
    private fun loadList(){
        val serverList= try{
            ObjectInputStream(FileInputStream("${filesDir.absoluteFile}/serverList")).use {
                it.readObject() as MutableList<ServerProfile>
            }
        }catch (e:Exception){
            mutableListOf()
        }
        servers.clear()
        servers.addAll(serverList)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var showDialog by remember { mutableStateOf(false) }
            var edibleServerProfile = remember {ServerProfile()}
            val state = rememberScalingLazyListState()
            Box(modifier = Modifier.background(Color.Black)) {
                ScalingLazyColumn(
                    state = state,
                    modifier = Modifier
                        .fillMaxSize()
                        .rotaryWithScroll(state),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item { Text("Servers") }

                    items(servers) {
                        var more by remember { mutableStateOf(false) }
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xff242124), RoundedCornerShape(50))
                                .padding(10.dp)
                                .clickable {
                                    startNewConnection(it)
                                },
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Icon(
                                imageVector = Icons.TwoTone.Airplay,
                                contentDescription = null,
                                modifier = Modifier.weight(1f)
                            )
                            Text(text = it.username, modifier = Modifier.weight(3f))
                            Icon(imageVector = Icons.TwoTone.MoreVert,
                                 contentDescription = null,
                                 modifier = Modifier
                                     .weight(1f)
                                     .clickable { more = true })
                        }
                        if (more) Popup(properties = PopupProperties(), popupPositionProvider = PopupPosition()) {
                            Row(
                                modifier = Modifier
                                    .background(Color(0xff242124), RoundedCornerShape(50))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Icon(imageVector = Icons.TwoTone.Edit, contentDescription = null, modifier = Modifier.clickable { edibleServerProfile=it
                                    showDialog=true })
                                Icon(
                                    imageVector = Icons.TwoTone.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.clickable { servers.remove(it) })
                            }
                        }
                    }

                }
                Icon(
                    imageVector = Icons.TwoTone.Add,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .size(60.dp, 40.dp)
                        .padding(5.dp)
                        .background(
                            Color.White, RoundedCornerShape(50)
                        )
                        .padding(5.dp)
                        .clickable { showDialog = true }
                )
            }
            Dialog(showDialog = showDialog, onDismissRequest = { showDialog = false }) {
                var name by remember{ mutableStateOf(edibleServerProfile.username) }
                var host by remember{ mutableStateOf(edibleServerProfile.host) }
                var port by remember{ mutableStateOf(edibleServerProfile.port.toString()) }
                var password by remember{ mutableStateOf(edibleServerProfile.password) }
                Alert(title ={ Text(text ="Set Server Details")}) {
                    item { TextField(icon = Icons.TwoTone.DriveFileRenameOutline, text = name, placeholder = "Name", onTextChange = {name=it})}
                    item { TextField(icon = Icons.TwoTone.LaptopMac, text = host, placeholder = "Host", onTextChange = {host=it})}
                    item { TextField(icon = Icons.TwoTone.ImportExport, text = port, placeholder = "Port", onTextChange = {port=it}, keyboardType = KeyboardType.Number)}
                    item { TextField(icon = Icons.TwoTone.Password, text = password, placeholder = "Password", onTextChange = {password=it})}
                    item{Icon(
                        imageVector = Icons.TwoTone.Done,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier
                            .size(60.dp, 40.dp)
                            .padding(5.dp)
                            .background(
                                Color.White, RoundedCornerShape(50)
                            )
                            .padding(5.dp)
                            .clickable {
                                servers.remove(edibleServerProfile)
                                edibleServerProfile.username = name
                                edibleServerProfile.host = host
                                edibleServerProfile.port = try {
                                    port.toInt()
                                } catch (e: Exception) {
                                    5900
                                }
                                edibleServerProfile.password = password
                                servers.add(edibleServerProfile)
                                showDialog = false
                            }
                    )}
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        saveServerList()
    }

    override fun onResume() {
        super.onResume()
        loadList()
    }

    private fun startNewConnection(profile: ServerProfile) {
        if (checkNativeLib()) startVncActivity(this, profile)
    }


    /**
     * Warns about missing native library.
     * This can happen if AVNC is installed by copying APK from a device with different architecture.
     */
    private fun checkNativeLib(): Boolean {
        return runCatching {
            VncClient.loadLibrary()
        }.onFailure {
            Log.e("lib", "Error Loading Lib")
            finish()
        }.isSuccess
    }

}