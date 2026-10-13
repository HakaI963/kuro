plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.kuro.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kuro.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.0-stage1"
    }

    flavorDimensions += "engine"
    productFlavors {
        create("stub") {
            dimension = "engine"
        }
        create("virtual") {
            dimension = "engine"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(project(":engine"))

    "virtualImplementation"(project(":engine-blackbox"))

    ksp(libs.androidx.room.compiler)

    val composeBom = libs.androidx.compose.bom
    implementation(platform(composeBom))
    androidTestImplementation(platform(composeBom))

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(libs.junit.junit)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(project(":engine"))
}
