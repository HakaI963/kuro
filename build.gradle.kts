// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // All library modules request com.android.library: it must be version-pinned
    // here, otherwise their requests fail with "already on the classpath with
    // an unknown version" (the AGP artifact is shared with the app plugin).
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
}
