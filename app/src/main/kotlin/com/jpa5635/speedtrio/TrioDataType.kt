package com.jpa5635.speedtrio

import android.content.Context
import android.widget.RemoteViews
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.util.Log
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Base for the double-width "left | middle | right" fields. Subclasses only say what the three
 * values are; drawing, pacing and theming are shared.
 */
abstract class TrioDataType(
    protected val karooSystem: KarooSystemService,
    extension: String,
    typeId: String,
) : DataTypeImpl(extension, typeId) {

    /** Representative content shown while the page is being edited. */
    protected abstract val previewContent: TrioContent

    /** Live content; emits whenever any underlying value changes. */
    protected abstract fun contentFlow(): Flow<TrioContent>

    /** When true, a flashing red dot is drawn in the upper-left corner while any camera is recording. */
    protected open val showsRecordingDot: Boolean = false

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        Log.d(TAG, "$dataTypeId view start: $config")
        val renderer = TrioRenderer(context)
        val (width, height) = config.viewSize
        val dark = TrioTheme.isDark(context)

        // The whole field height is used for the numbers, so no title row.
        emitter.onNext(UpdateGraphicConfig(showHeader = false))

        fun push(content: TrioContent, dot: Boolean) {
            val bitmap = renderer.render(width, height, content, dark, dot)
            val views = RemoteViews(context.packageName, R.layout.speed_trio_view)
            views.setImageViewBitmap(R.id.trio_image, bitmap)
            emitter.updateView(views)
        }

        val job = CoroutineScope(Dispatchers.IO).launch {
            if (config.preview) {
                push(previewContent, false)
                return@launch
            }
            val latest = AtomicReference<TrioContent?>(null)
            val recording = AtomicBoolean(false)
            launch { contentFlow().collect { latest.set(it) } }
            if (showsRecordingDot) launch { recordingFlow().collect { recording.set(it) } }

            // Single render loop. ViewEmitter drops updates faster than ~1 Hz, so draw at most once per
            // second. While recording, every draw flips the dot (1 s on / 1 s off); otherwise only draw
            // when the numbers changed (or to clear the dot after recording stops).
            var lastContent: TrioContent? = null
            var lastDot = false
            var lastPush = 0L
            while (isActive) {
                val now = System.currentTimeMillis()
                val content = latest.get()
                if (content != null && now - lastPush >= PUSH_INTERVAL_MS) {
                    val recordingNow = recording.get()
                    val dot = recordingNow && !lastDot
                    if (recordingNow || lastDot || content != lastContent) {
                        push(content, dot)
                        lastContent = content
                        lastDot = dot
                        lastPush = now
                    }
                }
                delay(TICK_MS)
            }
        }
        emitter.setCancellable {
            Log.d(TAG, "$dataTypeId view stop")
            job.cancel()
        }
    }

    /** All field values of a stream's current data point, or null while searching / not available. */
    protected fun pointFlow(dataTypeId: String): Flow<Map<String, Double>?> =
        karooSystem.streamDataFlow(dataTypeId)
            .map { state -> (state as? StreamState.Streaming)?.dataPoint?.values }
            .onStart { emit(null) }

    /**
     * Numeric value of a stream, or null while searching / not available.
     * Pass [fieldId] for data types that carry more than one field.
     */
    protected fun valueFlow(dataTypeId: String, fieldId: String? = null): Flow<Double?> =
        pointFlow(dataTypeId).map { values ->
            if (values == null) null else if (fieldId != null) values[fieldId] else values.values.firstOrNull()
        }

    /** True while ANY camera extension in [CameraRecording.STREAMS] reports that it is recording. */
    protected fun recordingFlow(): Flow<Boolean> {
        val flows = CameraRecording.STREAMS.map { id -> valueFlow(id).map { it != null && it >= 0.5 } }
        return if (flows.isEmpty()) flowOf(false) else combine(flows) { states -> states.any { it } }
    }

    protected fun text(value: Double?, decimals: Int): String =
        if (value == null) "--" else String.format(Locale.US, "%.${decimals}f", value)

    private companion object {
        const val TAG = "SpeedTrio"
        const val PUSH_INTERVAL_MS = 1000L
        const val TICK_MS = 100L
    }
}
