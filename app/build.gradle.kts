import org.gradle.api.tasks.bundling.AbstractArchiveTask
import java.util.Properties

// F-Droid reproducible builds: disable baseline profiles using Groovy script
apply(from = "fix-baseline-profiles.gradle")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.kuro.app"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.kuro.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 13
        versionName = "2.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        // Host binaries (libbash/loader/proot) are arm64-v8a; x86_64 devices
        // run them via NDK translation (native bridge).
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    // Kuro ships as a single standalone app (com.kuro.app). The host
    // bootstrap + jniLibs ship from native/bootstrap/com.kuro.app/,
    // staged by :app:packageHostAssets (runs scripts/package_host_assets.sh).

    androidResources {
        // Disable PNG crunching for reproducible builds
        @Suppress("UnstableApiUsage")
        ignoreAssetsPattern = "!.svn:!.git:.*:!CVS:!thumbs.db:!picasa.ini:!*.scc:*~"
        // Critical — do not recompress archives; they must stay STORED in the APK.
        // Rootfs archives are no longer packaged (GitHub release download) —
        // only bootstrap.tar and the xfce theme assets remain.
        @Suppress("UnstableApiUsage")
        noCompress += listOf("xz", "tar")
    }

    // Disable dependency metadata block for F-Droid
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(keystorePropertiesFile.inputStream())
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            packaging {
                resources.excludes.add("META-INF/**")
            }
        }
        release {
            // F-Droid has no keystore.properties; an empty signingConfig
            // ("release" without storeFile) fails assemble. Sign only locally.
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Disable baseline profiles for F-Droid reproducible builds
            packaging {
                resources.excludes.add("META-INF/**")
                resources.excludes.add("**.prof")
                resources.excludes.add("assets/dexopt/baseline.prof")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        unitTests {
            // android.util.Log etc. are stubs in JVM tests — return defaults instead of throwing
            isReturnDefaultValues = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // W^X host binaries (libbash/libproot/libloader) must be extracted to
            // nativeLibraryDir so the app-data ET_DYN loader can exec them.
            useLegacyPackaging = true
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

// Reproducible builds configuration for F-Droid
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

// ─────────────────────────────────────────────────────────────────────────────
// Package host assets (fail-closed gate, plan P0-T6/T7)
//   :app:packageHostAssets — stages native/bootstrap/com.kuro.app into the
//   main source set (app/src/main/assets/bootstrap.tar + jniLibs).
// Fails assemble with a clear error when bootstrap.tar / jniLibs are missing.
// Rootfs archives are NOT packaged: the selected distro's rootfs is downloaded
// on demand from the GitHub release tag `rootfs` at install time
// (docs/plans/rootfs-github-release-no-apk-bloat.md).
// ─────────────────────────────────────────────────────────────────────────────
val KURO_APP_ID = "com.kuro.app"

// assets/loader.apk is built from native/loader/Loader.java by
// scripts/build_loader_apk.sh (F-Droid rebuilds it after the scanner).

tasks.register<Exec>("packageHostAssets") {
    group = "build"
    description = "Stage host bootstrap + jniLibs for com.kuro.app from native/bootstrap/com.kuro.app"
    workingDir = rootProject.projectDir
    commandLine("bash", "scripts/package_host_assets.sh", KURO_APP_ID)

    val bootstrapTree = fileTree(rootProject.file("native/bootstrap/$KURO_APP_ID"))
    inputs.files(bootstrapTree)
    outputs.file(file("src/main/assets/bootstrap.tar"))
    outputs.dir(file("src/main/jniLibs"))

    doFirst {
        val required = listOf(
            rootProject.file("native/bootstrap/$KURO_APP_ID/bootstrap.tar"),
            rootProject.file("native/bootstrap/$KURO_APP_ID/jniLibs/arm64-v8a/libbash.so"),
            rootProject.file("native/bootstrap/$KURO_APP_ID/jniLibs/arm64-v8a/libproot.so"),
            rootProject.file("native/bootstrap/$KURO_APP_ID/jniLibs/arm64-v8a/libloader.so"),
            rootProject.file("native/bootstrap/$KURO_APP_ID/jniLibs/arm64-v8a/libloader32.so"),
            rootProject.file("native/bootstrap/$KURO_APP_ID/jniLibs/arm64-v8a/libpulseaudio.so"),
            rootProject.file("native/bootstrap/$KURO_APP_ID/jniLibs/arm64-v8a/libpactl.so")
        )
        val missing = required.filter { !it.isFile }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Host bootstrap assets missing for applicationId '$KURO_APP_ID':\n" +
                    missing.joinToString("\n") { "  - $it" } +
                    "\n\nAssemble the bootstrap first:\n" +
                    "  ./scripts/assemble_bootstrap.py --package-name $KURO_APP_ID --mode full\n" +
                    "  ./scripts/verify_bootstrap.sh $KURO_APP_ID"
            )
        }
    }
}

// Every assemble / bundle / pre-build depends on the packaging task so
// mergeAssets / mergeJniLibFolders never see an empty host (P0-T7).
tasks.matching {
    it.name.startsWith("assemble") ||
        it.name.startsWith("bundle") ||
        it.name.startsWith("pre")
}.configureEach {
    dependsOn("packageHostAssets")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    
    // Glassmorphism FX
    implementation(libs.haze)
    implementation(libs.haze.materials)
    
    // Permissions
    implementation(libs.accompanist.permissions)
    
    // Networking
    implementation(libs.okhttp)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // Embedded terminal (termux-app GPLv3 — app stays open source; see LICENSE/README)
    // Only com.termux.terminal + com.termux.view are used. terminal-emulator is built
    // from v0.118.0 source (:terminal-emulator) so libtermux.so gets 16 KB alignment.
    implementation(libs.termux.terminal.view) {
        exclude(group = "com.github.termux.termux-app", module = "terminal-emulator")
    }
    implementation(project(":terminal-emulator"))

    // Embedded Termux:X11 (cloned + integrated directly, same-package rendering)
    implementation(project(":termux-x11"))

    // Kuro app-clone engine (BlackBox-family virtual engine, Apache-2.0 vendored)
    implementation(project(":clone-engine"))
    implementation(project(":blackbox"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation("org.json:json:20180813") // android.jar org.json is a stub in unit tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
