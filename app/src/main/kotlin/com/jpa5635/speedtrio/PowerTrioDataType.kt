package com.jpa5635.speedtrio

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.math.roundToInt

/**
 * Double-width field: [average power] [3 s power] [left/right balance].
 * Power in whole watts; balance as "left/right" percent, e.g. 49/51.
 */
class PowerTrioDataType(
    karooSystem: KarooSystemService,
    extension: String,
) : TrioDataType(karooSystem, extension, "power-trio") {

    override val previewContent = TrioContent("152", "238", "49/51", "AVG", "L/R")

    override fun contentFlow(): Flow<TrioContent> = combine(
        valueFlow(DataType.Type.AVERAGE_POWER),
        valueFlow(DataType.Type.SMOOTHED_3S_AVERAGE_POWER),
        // Swap in AVERAGE_PEDAL_POWER_BALANCE or SMOOTHED_3S_AVERAGE_PEDAL_POWER_BALANCE for a calmer number.
        valueFlow(DataType.Type.PEDAL_POWER_BALANCE, DataType.Field.PEDAL_POWER_BALANCE_LEFT),
    ) { avg, power3s, balanceLeft ->
        TrioContent(
            left = text(avg, 0),
            middle = text(power3s, 0),
            right = balanceText(balanceLeft),
            leftLabel = "AVG",
            rightLabel = "L/R",
        )
    }

    /** The stream reports the left share in percent; right is the remainder so the pair always sums to 100. */
    private fun balanceText(left: Double?): String {
        if (left == null) return "--"
        val l = left.roundToInt().coerceIn(0, 100)
        return "$l/${100 - l}"
    }
}
