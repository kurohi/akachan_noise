package io.github.kurohi.akachannoise.engine.tools

import io.github.kurohi.akachannoise.engine.model.BuiltInPresets
import io.github.kurohi.akachannoise.engine.model.LayerSpec
import io.github.kurohi.akachannoise.engine.model.MixSpec
import io.github.kurohi.akachannoise.engine.model.SoundCatalog
import java.io.File

/**
 * Renders every built-in sound and preset to WAV files for desktop listening.
 * Invoked by the `:engine:renderSamples` Gradle task; output lands in
 * engine/build/samples.
 */
fun main() {
    val outDir = File(System.getProperty("user.dir")).apply { mkdirs() }

    SoundCatalog.specs.forEach { spec ->
        val seconds = if (spec.calibrationSeconds > 5.0) 20.0 else 10.0
        val mix = MixSpec(
            id = "preview-${spec.id.id}",
            name = spec.id.id,
            layers = listOf(
                LayerSpec(
                    soundId = spec.id.id,
                    volume = 1f,
                    params = spec.params.associate { it.id to it.default },
                ),
            ),
        )
        val file = File(outDir, "${spec.id.id}.wav")
        OfflineRender.renderToFile(file, mix, seconds)
        println("rendered ${file.absolutePath} (${spec.id})")
    }

    BuiltInPresets.presets.forEach { preset ->
        val file = File(outDir, "${preset.id.replace('.', '_')}.wav")
        OfflineRender.renderToFile(file, preset, 20.0)
        println("rendered ${file.absolutePath} (${preset.id})")
    }
    println(
        "renderSamples: done (${SoundCatalog.specs.size} sound(s), " +
            "${BuiltInPresets.presets.size} preset(s))",
    )
}
