package io.github.kurohi.akachannoise.engine.tools

import io.github.kurohi.akachannoise.engine.model.LayerSpec
import io.github.kurohi.akachannoise.engine.model.MixSpec
import io.github.kurohi.akachannoise.engine.model.SoundCatalog
import java.io.File

/**
 * Renders every built-in sound to WAV files for desktop listening.
 * Invoked by the `:engine:renderSamples` Gradle task; output lands in
 * engine/build/samples.
 */
fun main() {
    val outDir = File(System.getProperty("user.dir")).apply { mkdirs() }
    val seconds = 10.0

    SoundCatalog.specs.forEach { spec ->
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
    println("renderSamples: done (${SoundCatalog.specs.size} sound(s))")
}
