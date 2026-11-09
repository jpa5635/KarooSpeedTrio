package com.jpa5635.speedtrio

import io.hammerhead.karooext.models.DataType

/**
 * Where "a camera is recording" comes from.
 *
 * The Insta360 extension (KarooInsta360Ctl, extension id `insta360cam`) already streams a data type
 * `recording_state` that is 1.0 while ANY of its saved cameras is recording and 0.0 otherwise, so this app
 * just subscribes to it through the Karoo system; nothing needs to change in that app.
 *
 * Any other camera extension that publishes the same convention (1.0 = recording) can be added here and the
 * dot will light up when any one of them is recording.
 */
object CameraRecording {
    val STREAMS: List<String> = listOf(
        DataType.dataTypeId("insta360cam", "recording_state"),
    )
}
