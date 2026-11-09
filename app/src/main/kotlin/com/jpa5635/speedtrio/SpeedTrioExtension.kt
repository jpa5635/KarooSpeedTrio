package com.jpa5635.speedtrio

import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension

class SpeedTrioExtension : KarooExtension("speedtrio", "1.0") {
    private lateinit var karooSystem: KarooSystemService

    override val types by lazy {
        listOf(
            SpeedTrioDataType(karooSystem, extension),
            PowerTrioDataType(karooSystem, extension),
            GearTrioDataType(karooSystem, extension),
        )
    }

    override fun onCreate() {
        super.onCreate()
        karooSystem = KarooSystemService(applicationContext)
        karooSystem.connect { connected -> Log.d(TAG, "Karoo system connected=$connected") }
    }

    override fun onDestroy() {
        karooSystem.disconnect()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "SpeedTrio"
    }
}
