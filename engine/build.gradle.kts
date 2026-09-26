plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

/** Renders every built-in sound and preset to WAV files under build/samples for desktop listening. */
val samplesDir = layout.buildDirectory.dir("samples")
tasks.register<JavaExec>("renderSamples") {
    group = "verification"
    description = "Render every built-in sound and preset to WAV in build/samples."
    mainClass = "io.github.kurohi.akachannoise.engine.tools.RenderSamplesKt"
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = samplesDir.get().asFile.apply { mkdirs() }
}
