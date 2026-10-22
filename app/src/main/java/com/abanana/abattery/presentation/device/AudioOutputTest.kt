package com.abanana.abattery.presentation.device

import android.media.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun AudioOutputTest(earpiece: Boolean) {
    val context = LocalContext.current
    val manager = context.getSystemService(AudioManager::class.java)
    var track by remember { mutableStateOf<AudioTrack?>(null) }
    var message by remember {
        mutableStateOf(
            "Play a short tone at the current system volume. Choose a channel to check stereo separation."
        )
    }
    var channel by remember { mutableStateOf("Both") }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { track?.stop() }
            track?.release()
        }
    }
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeading(if (earpiece) "Earpiece" else "Speakers")
        DeviceText(message)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Left", "Right", "Both").forEach { name ->
                FilterChip(channel == name, { channel = name }, { DeviceText(name) })
            }
        }
        Button(
            onClick = {
                message =
                    runCatching {
                            runCatching { track?.stop() }
                            track?.release()
                            val rate = 44100
                            val samples =
                                ShortArray(rate * 2) { i ->
                                    val muted =
                                        (channel == "Left" && i % 2 == 1) ||
                                            (channel == "Right" && i % 2 == 0)
                                    if (muted) 0
                                    else
                                        (sin(2 * PI * 440 * (i / 2).toDouble() / rate) *
                                                Short.MAX_VALUE *
                                                .15)
                                            .toInt()
                                            .toShort()
                                }
                            val desired =
                                manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
                                    it.type ==
                                        if (earpiece) AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                                        else AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                                }
                            if (desired == null)
                                "The selected output is not reported by this device"
                            else {
                                val audio =
                                    AudioTrack.Builder()
                                        .setAudioAttributes(
                                            AudioAttributes.Builder()
                                                .setUsage(
                                                    if (earpiece)
                                                        AudioAttributes.USAGE_VOICE_COMMUNICATION
                                                    else AudioAttributes.USAGE_MEDIA
                                                )
                                                .setContentType(
                                                    AudioAttributes.CONTENT_TYPE_SONIFICATION
                                                )
                                                .build()
                                        )
                                        .setAudioFormat(
                                            AudioFormat.Builder()
                                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                                .setSampleRate(rate)
                                                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                                                .build()
                                        )
                                        .setTransferMode(AudioTrack.MODE_STATIC)
                                        .setBufferSizeInBytes(samples.size * 2)
                                        .build()
                                track = audio
                                check(audio.state == AudioTrack.STATE_INITIALIZED)
                                val preferred = audio.setPreferredDevice(desired)
                                audio.write(samples, 0, samples.size)
                                audio.play()
                                "One-second 440 Hz tone · $channel\nPreferred route: ${desired.productName} · accepted: $preferred\nConfirm the selected speaker is audible. Android may override routing."
                            }
                        }
                        .getOrElse { "Audio test unavailable: ${it.message}" }
            }
        ) {
            DeviceText("Run test")
        }
        TextButton(
            onClick = {
                runCatching { track?.stop() }
                message = "Stopped"
            }
        ) {
            DeviceText("Stop")
        }
    }
}
