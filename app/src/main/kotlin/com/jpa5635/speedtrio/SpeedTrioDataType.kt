package com.jpa5635.speedtrio

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.models.UserProfile.PreferredUnit.UnitType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Double-width field: [average speed] [current speed] [ride distance].
 * Average speed and distance show two decimals, current speed one.
 * A flashing red dot in the upper-left corner shows while any camera is recording.
 */
class SpeedTrioDataType(
    karooSystem: KarooSystemService,
    extension: String,
) : TrioDataType(karooSystem, extension, "speed-trio") {

    override val previewContent = TrioContent("17.24", "28.3", "42.63", "MPH", "MI")

    override val showsRecordingDot = true

    override fun contentFlow(): Flow<TrioContent> = combine(
        valueFlow(DataType.Type.AVERAGE_SPEED),
        valueFlow(DataType.Type.SPEED),
        valueFlow(DataType.Type.DISTANCE),
        karooSystem.consumerFlow<UserProfile>()
            .map { it.preferredUnit.distance }
            .onStart { emit(UnitType.METRIC) },
    ) { avgMs, speedMs, distM, unit ->
        val imperial = unit == UnitType.IMPERIAL
        val speedFactor = if (imperial) MS_TO_MPH else MS_TO_KMH
        TrioContent(
            left = text(avgMs?.let { it * speedFactor }, 2),
            middle = text(speedMs?.let { it * speedFactor }, 1),
            right = text(distM?.let { it / if (imperial) M_PER_MILE else 1000.0 }, 2),
            leftLabel = if (imperial) "MPH" else "KM/H",
            rightLabel = if (imperial) "MI" else "KM",
        )
    }

    private companion object {
        const val MS_TO_KMH = 3.6
        const val MS_TO_MPH = 2.2369362920544
        const val M_PER_MILE = 1609.344
    }
}
