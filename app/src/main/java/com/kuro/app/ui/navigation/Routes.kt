package com.kuro.app.ui.navigation

enum class BottomDestination { Home, Library, Activity, Settings }

object Routes {
    const val INTRO = "intro"
    const val HOME = "home"
    const val LIBRARY = "library"
    const val ACTIVITY = "activity"
    const val SETTINGS = "settings"
    const val ADD_CLONE = "add_clone"
    const val DIAGNOSTICS = "diagnostics"
    const val CLONE_DETAILS = "clone_details/{cloneId}"

    fun cloneDetails(cloneId: String) = "clone_details/$cloneId"
}
