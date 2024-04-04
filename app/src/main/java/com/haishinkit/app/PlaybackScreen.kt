package com.haishinkit.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.haishinkit.compose.HaishinKitView
import com.haishinkit.compose.rememberConnectionState
import com.haishinkit.rtmp.RtmpConnection

private const val TAG = "PlaybackScreen"

@Composable
fun PlaybackScreen(
    command: String,
    streamName: String,
    modifier: Modifier,
) {
    val context = LocalContext.current

    val connectionState =
        rememberConnectionState {
            RtmpConnection()
        }

    val stream = remember(connectionState) {
        connectionState.createStream(context)
    }

    LaunchedEffect(connectionState) {
        snapshotFlow { connectionState.code }.collect {
            when (it) {
                RtmpConnection.Code.CONNECT_SUCCESS.rawValue -> {
                    stream.play(streamName)
                }

                else -> {

                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            connectionState.dispose()
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomEnd,
    ) {
        HaishinKitView(
            stream = stream,
            modifier = Modifier.fillMaxSize(),
        )
        Button(
            modifier =
            Modifier
                .padding(16.dp)
                .width(100.dp)
                .height(50.dp),
            onClick = {
                if (connectionState.isConnected) {
                    connectionState.close()
                } else {
                    connectionState.connect(command)
                }
            },
        ) {
            if (connectionState.isConnected) {
                Text("STOP")
            } else {
                Text("PLAY")
            }
        }
    }
}

@Preview
@Composable
private fun PreviewPlaybackScreen() {
    PlaybackScreen(command = "", streamName = "", modifier = Modifier.fillMaxSize())
}
