plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.kurohi.akachannoise"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.kurohi.akachannoise"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // Generates the locale config so English and Japanese appear in the
        // system's per-app language picker (Android 13+).
        generateLocaleConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":data"))
    implementation(project(":engine"))
    implementation(project(":playback"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

/**
 * Fails the build if the merged manifest requests a permission outside the
 * privacy allowlist. Akachan Noise must never request INTERNET or anything
 * that could exfiltrate data. Run after `assembleDebug`/`assembleRelease`.
 */
val mergedManifestRoot = layout.projectDirectory
    .dir("build/intermediates/merged_manifest").asFile
val allowedPermissions = setOf(
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
    "android.permission.FOREGROUND_SERVICE_MICROPHONE",
    "android.permission.RECORD_AUDIO",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.WAKE_LOCK",
    // Added by Glance so home-screen widgets refresh after a reboot. It only
    // tells the app that the device booted; it cannot read or send anything.
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "io.github.kurohi.akachannoise.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
)
tasks.register("checkMergedPermissions") {
    group = "verification"
    description = "Verify the merged manifest only contains allowed permissions."
    val manifestsRoot = mergedManifestRoot
    val allowed = allowedPermissions
    doLast {
        val manifests = manifestsRoot.walkTopDown()
            .filter { it.isFile && it.name == "AndroidManifest.xml" }
            .toList()
        check(manifests.isNotEmpty()) {
            "No merged manifests found under $manifestsRoot. Run an assemble task first."
        }
        val offenders = sortedSetOf<String>()
        manifests.forEach { file ->
            val regex = Regex("""<uses-permission[^>]*android:name="([^"]+)"""")
            regex.findAll(file.readText()).forEach { match ->
                val name = match.groupValues[1]
                if (name !in allowed) offenders.add(name)
            }
        }
        check(offenders.isEmpty()) {
            "Merged manifest contains permissions outside the privacy allowlist: $offenders"
        }
        println("checkMergedPermissions: OK (${manifests.size} manifest(s) checked)")
    }
}
