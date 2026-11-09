package com.jpa5635.speedtrio

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.math.roundToInt

/**
 * Double-width field: [gear, e.g. 48-13] [heart rate] [cadence].
 * Gear is front chainring teeth - rear cog teeth; if the Karoo has no tooth counts for the drivetrain it
 * falls back to the gear numbers (e.g. 2-5).
 */
class GearTrioDataType(
    karooSystem: KarooSystemService,
    extension: String,
) : TrioDataType(karooSystem, extension, "gear-trio") {

    override val previewContent = TrioContent("48-13", "152", "92", "GEAR", "CAD")

    override fun contentFlow(): Flow<TrioContent> = combine(
        pointFlow(DataType.Type.SHIFTING_GEARS),
        valueFlow(DataType.Type.HEART_RATE),
        valueFlow(DataType.Type.CADENCE),
    ) { gears, heartRate, cadence ->
        TrioContent(
            left = gearText(gears),
            middle = text(heartRate, 0),
            right = text(cadence, 0),
            leftLabel = "GEAR",
            rightLabel = "CAD",
        )
    }

    private fun gearText(values: Map<String, Double>?): String {
        if (values == null) return "--"
        fun pair(front: String, rear: String): String? {
            val f = values[front]?.roundToInt()
            val r = values[rear]?.roundToInt()
            return if (f != null && r != null) "$f-$r" else null
        }
        return pair(DataType.Field.SHIFTING_FRONT_GEAR_TEETH, DataType.Field.SHIFTING_REAR_GEAR_TEETH)
            ?: pair(DataType.Field.SHIFTING_FRONT_GEAR, DataType.Field.SHIFTING_REAR_GEAR)
            ?: "--"
    }
}
