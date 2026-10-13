package com.kuro.app.ui.navigation

// Step 4 (Stage 2): four tabs — System | Terminal | Clones | Settings.
// The Stage 1 clone manager lives under Clones; System/Terminal are new.
enum class BottomDestination { System, Terminal, Clones, Settings }

object Routes {
    const val INTRO = "intro"
    const val SHELL = "shell"
    const val ADD_CLONE = "add_clone"
    const val ADD_DISTRO = "add_distro"
    const val TERMINAL_SESSION = "terminal_session/{containerId}"
    const val DIAGNOSTICS = "diagnostics"
    const val CLONE_DETAILS = "clone_details/{cloneId}"

    fun cloneDetails(cloneId: String) = "clone_details/$cloneId"
    fun terminalSession(containerId: String) = "terminal_session/$containerId"
}
