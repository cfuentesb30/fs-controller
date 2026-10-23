package com.fscontroller.app.model

import kotlinx.serialization.Serializable

/** The 4 app shortcut buttons on the Fire TV remote, in the order they appear on the remote. */
enum class AppButton(val label: String) {
    NETFLIX("Netflix"),
    PRIME_VIDEO("Prime Video"),
    DISNEY_PLUS("Disney+"),
    PEACOCK("Peacock"),
}

/**
 * One button's remap. The chosen target is kept when the remap is turned off, so turning it back
 * on is a single action.
 */
@Serializable
data class ButtonMapping(
    val targetPackage: String? = null,
    val enabled: Boolean = false,
)

@Serializable
data class RemapConfig(
    val masterEnabled: Boolean = true,
    val mappings: Map<AppButton, ButtonMapping> = emptyMap(),
) {
    fun mappingFor(button: AppButton): ButtonMapping = mappings[button] ?: ButtonMapping()

    /** Package to launch for [button], or null when the button should keep its stock behavior. */
    fun activeTargetFor(button: AppButton): String? =
        if (!masterEnabled) null
        else mappingFor(button).takeIf { it.enabled }?.targetPackage
}

/** An installed app the user can pick as a target. */
data class InstalledApp(
    val packageName: String,
    val label: String,
)

/** What the one-time ADB setup has done so far; the UI shows instructions for anything missing. */
data class SetupStatus(
    val serviceEnabled: Boolean,
    val canReadLogs: Boolean,
) {
    val ready: Boolean get() = serviceEnabled && canReadLogs
}
