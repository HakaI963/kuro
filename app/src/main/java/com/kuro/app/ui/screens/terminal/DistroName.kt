package com.kuro.app.ui.screens.terminal

/**
 * Step 5d: pure wizard container-name policy (unit-tested in DistroNameTest).
 *
 * - The field auto-defaults to the selected distro id ("alpine", "debian", …).
 * - The default (re-)applies when the field is pristine (blank) or still
 *   holds the previous auto-default — i.e. the user has never typed a custom
 *   value. Anything the user typed is preserved across distro changes.
 * - The placeholder is always computed from the selection:
 *   "e.g. <selectedDistroId>-mobile". No hardcoded example strings anywhere.
 */
object DistroNamePolicy {

    /**
     * @param current current field value
     * @param lastAuto the last auto-default applied (null if never applied)
     * @param newDistroId the newly selected distro id
     * @return the field value to show after the selection change
     */
    fun onDistroSelected(current: String, lastAuto: String?, newDistroId: String): String =
        if (current.isBlank() || (lastAuto != null && current == lastAuto)) newDistroId
        else current

    /** True when [value] still counts as "untouched by the user". */
    fun isPristine(value: String, lastAuto: String?): Boolean =
        value.isBlank() || (lastAuto != null && value == lastAuto)

    /** Computed placeholder, e.g. "e.g. alpine-mobile". */
    fun placeholder(distroId: String?): String = "e.g. ${distroId?.ifBlank { null } ?: "container"}-mobile"
}
