package com.fscontroller.app

import com.fscontroller.app.data.InstalledAppsSource
import com.fscontroller.app.data.RemapRepository
import com.fscontroller.app.data.SetupStatusSource

/** Manual DI: process-wide singletons, assigned in [FsApp.onCreate]. */
object AppGraph {
    lateinit var repository: RemapRepository
    lateinit var installedApps: InstalledAppsSource
    lateinit var setupStatus: SetupStatusSource
}
