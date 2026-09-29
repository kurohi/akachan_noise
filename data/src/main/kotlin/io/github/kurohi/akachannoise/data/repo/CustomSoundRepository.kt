package io.github.kurohi.akachannoise.data.repo

import android.content.Context
import android.net.Uri
import io.github.kurohi.akachannoise.data.audio.AudioImporter
import io.github.kurohi.akachannoise.data.audio.MappedPcmSource
import io.github.kurohi.akachannoise.data.audio.VoiceRecorder
import io.github.kurohi.akachannoise.data.model.CustomSoundRef
import io.github.kurohi.akachannoise.engine.generators.SampleLoop
import io.github.kurohi.akachannoise.engine.generators.SoundGenerator
import io.github.kurohi.akachannoise.engine.model.EngineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The user's own sounds: imported files and recordings. Audio lives as raw
 * PCM in app-private storage and is played back memory-mapped through
 * [SampleLoop]; nothing is ever uploaded or shared unless the user exports
 * a backup themselves.
 */
class CustomSoundRepository(
    context: Context,
    private val mixes: MixRepository,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val soundsDir = File(appContext.filesDir, SOUNDS_DIR)
    private val takesDir = File(appContext.cacheDir, TAKES_DIR)
    private val importer = AudioImporter(appContext)

    val recorder = VoiceRecorder(appContext)

    val sounds: StateFlow<List<CustomSoundRef>> = mixes.data
        .map { it.customSounds }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Open mappings, reused across layers so a file is mapped once. */
    private val sources = ConcurrentHashMap<String, MappedPcmSource>()

    suspend fun importFrom(uri: Uri, name: String): CustomSoundRef? = withContext(Dispatchers.IO) {
        val id = newSoundId()
        val ref = importer.import(uri, id, name, soundsDir) ?: return@withContext null
        mixes.addCustomSound(ref)
        ref
    }

    /** File the recorder should write its raw take into. */
    fun newTakeFile(): File = File(takesDir, "take-${System.currentTimeMillis()}.pcm")

    /** Processes a finished take into the sound library. */
    suspend fun saveRecording(
        takeFile: File,
        name: String,
    ): CustomSoundRef? = withContext(Dispatchers.IO) {
        val id = newSoundId()
        val target = File(soundsDir, "$id.pcm")
        val durationMs = importer.finalizeRecording(
            takeFile,
            target,
            VoiceRecorder.CHANNELS,
            VoiceRecorder.SAMPLE_RATE,
        )
        takeFile.delete()
        if (durationMs <= 0) {
            target.delete()
            return@withContext null
        }
        val ref = CustomSoundRef(
            id = id,
            name = name,
            fileName = target.name,
            durationMs = durationMs,
            sampleRate = VoiceRecorder.SAMPLE_RATE,
            channels = VoiceRecorder.CHANNELS,
            wombFilter = 0f,
            loopCrossfadeMs = AudioImporter.CROSSFADE_MS,
            createdAt = System.currentTimeMillis(),
        )
        mixes.addCustomSound(ref)
        ref
    }

    suspend fun delete(id: String) {
        withContext(Dispatchers.IO) {
            sources.remove(id)?.close()
            runCatching { File(soundsDir, "$id.pcm").delete() }
        }
        mixes.removeCustomSound(id)
    }

    /**
     * Fire-and-forget delete for UI teardown paths (leaving the recorder
     * without saving), where suspending is not possible.
     */
    fun deleteAsync(id: String) {
        scope.launch { delete(id) }
    }

    suspend fun rename(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        mixes.updateCustomSound(id) { it.copy(name = trimmed) }
    }

    suspend fun setWombFilter(id: String, value: Float) {
        mixes.updateCustomSound(id) { it.copy(wombFilter = value.coerceIn(0f, 1f)) }
    }

    /**
     * Generator factory handed to the engine through the playback layer.
     * Called on the render thread, so it must stay allocation-light and must
     * not block: the mapping is opened once and then reused.
     */
    fun createGenerator(
        soundId: String,
        context: EngineContext,
        params: Map<String, Float>,
    ): SoundGenerator? {
        val ref = mixes.data.value.customSounds.firstOrNull { it.id == soundId } ?: return null
        val source = sources[soundId] ?: openSource(ref) ?: return null
        val loop = SampleLoop(source, context.sampleRate)
        val filter = params[SampleLoop.PARAM_WOMB_FILTER] ?: ref.wombFilter
        loop.setParam(SampleLoop.PARAM_WOMB_FILTER, filter)
        return loop
    }

    private fun openSource(ref: CustomSoundRef): MappedPcmSource? {
        val file = File(soundsDir, ref.fileName)
        val source = MappedPcmSource.open(file, ref.channels, ref.sampleRate) ?: return null
        val existing = sources.putIfAbsent(ref.id, source)
        if (existing != null) {
            source.close()
            return existing
        }
        return source
    }

    fun close() {
        sources.values.forEach { it.close() }
        sources.clear()
    }

    private fun newSoundId(): String = "custom." + UUID.randomUUID()

    companion object {
        private const val SOUNDS_DIR = "sounds"
        private const val TAKES_DIR = "takes"
    }
}
