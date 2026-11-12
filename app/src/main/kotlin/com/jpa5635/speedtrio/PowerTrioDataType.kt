package com.jpa5635.speedtrio

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.math.roundToInt

/**
 * Double-width field: [average power] [3 s power] [left/right balance].
 * Power in whole watts; balance is the 3 second average, as "left/right" percent, e.g. 49/51.
 */
class PowerTrioDataType(
    karooSystem: KarooSystemService,
    extension: String,
) : TrioDataType(karooSystem, extension, "power-trio") {

    override val previewContent = TrioContent(
        "152", "238", "49/51", "AVG", "L/R", "3SEC",
        leftIcon = TrioIcon.BOLT, middleIcon = TrioIcon.BOLT, rightIcon = TrioIcon.BOLT,
    )

    override fun contentFlow(): Flow<TrioContent> = combine(
        valueFlow(DataType.Type.AVERAGE_POWER),
        valueFlow(DataType.Type.SMOOTHED_3S_AVERAGE_POWER),
        // 3 second average balance (same stream the Karoo's own 3s balance field uses). Swap in PEDAL_POWER_BALANCE for
        // the instantaneous value or AVERAGE_PEDAL_POWER_BALANCE for the ride average.
        valueFlow(DataType.Type.SMOOTHED_3S_AVERAGE_PEDAL_POWER_BALANCE, DataType.Field.PEDAL_POWER_BALANCE_LEFT),
    ) { avg, power3s, balanceLeft ->
        TrioContent(
            left = text(avg, 0),
            middle = text(power3s, 0),
            right = balanceText(balanceLeft),
            leftLabel = "AVG",
            rightLabel = "L/R",
            middleLabel = "3SEC",
            leftIcon = TrioIcon.BOLT,
            middleIcon = TrioIcon.BOLT,
            rightIcon = TrioIcon.BOLT,
        )
    }

    /** The stream reports the left share in percent; right is the remainder so the pair always sums to 100. */
    private fun balanceText(left: Double?): String {
        if (left == null) return "--"
        val l = left.roundToInt().coerceIn(0, 100)
        return "$l/${100 - l}"
    }
}
