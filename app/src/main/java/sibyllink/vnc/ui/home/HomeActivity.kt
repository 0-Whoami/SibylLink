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
import androidx.compose.material.icons.twotone.MoreVert
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import sibyllink.vnc.model.ServerProfile
import sibyllink.vnc.ui.vnc.startVncActivity
import sibyllink.vnc.vnc.VncClient
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.rotaryinput.rotaryWithScroll

/**
 * Primary activity of the app.
 *
 * It Provides access to saved and discovered servers.
 */
@ExperimentalHorologistApi class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state = rememberScalingLazyListState()
            Box(modifier = Modifier.background(Color.Black)) {
                ScalingLazyColumn(
                    state = state,
                    modifier = Modifier
                        .fillMaxSize()
                        .rotaryWithScroll(state)) {
                    item{
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().background(Color(0xff242124), RoundedCornerShape(50)).padding(5.dp).clickable { startNewConnection(
                            ServerProfile(host = "localhost", port = 5901, password = "12345678")
                        ) }) {
                            Icon(imageVector = Icons.TwoTone.Airplay, contentDescription = null, modifier = Modifier.weight(1f))
                            Text(text = "Termux", modifier = Modifier.weight(3f))
                            Icon(imageVector = Icons.TwoTone.MoreVert, contentDescription = null, modifier = Modifier.weight(1f))
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
                )
            }
        }
    }


    private fun startNewConnection(profile: ServerProfile) {
        if (checkNativeLib())
            startVncActivity(this, profile)
    }



    /**
     * Warns about missing native library.
     * This can happen if AVNC is installed by copying APK from a device with different architecture.
     */
    private fun checkNativeLib(): Boolean {
        return runCatching {
            VncClient.loadLibrary()
        }.onFailure {
            Log.e("lib","Error Loading Lib")
            finish()
        }.isSuccess
    }

}