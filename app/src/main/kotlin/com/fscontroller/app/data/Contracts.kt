package com.fscontroller.app.data

import android.graphics.drawable.Drawable
import com.fscontroller.app.model.AppButton
import com.fscontroller.app.model.InstalledApp
import com.fscontroller.app.model.RemapConfig
import com.fscontroller.app.model.SetupStatus
import kotlinx.coroutines.flow.Flow

/** Single source of truth for remap settings, shared by the UI and RemapService (same process). */
interface RemapRepository {
    val config: Flow<RemapConfig>
    suspend fun setMasterEnabled(enabled: Boolean)
    /** Picking a target also enables that button's remap. */
    suspend fun setTarget(button: AppButton, packageName: String)
    suspend fun setButtonEnabled(button: AppButton, enabled: Boolean)
    /** Forgets [button]'s chosen app and turns its remap off; the button works as normal again. */
    suspend fun clearTarget(button: AppButton)
    /** [clearTarget] for every button. The master switch is left as it is. */
    suspend fun clearAll()
}

interface InstalledAppsSource {
    /** Launchable TV apps, sorted by label, excluding this app. Call off the main thread. */
    suspend fun launchableApps(): List<InstalledApp>
    /** Banner (preferred) or icon for [packageName], or null if not installed. */
    fun visualFor(packageName: String): Drawable?
    fun labelFor(packageName: String): String?
}

interface SetupStatusSource {
    /** Cheap, synchronous check; the UI re-reads it on resume. */
    fun current(): SetupStatus
}
