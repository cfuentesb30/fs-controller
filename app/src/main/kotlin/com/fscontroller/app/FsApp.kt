package com.fscontroller.app

import android.app.Application
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.fscontroller.app.data.DataStoreRemapRepository
import com.fscontroller.app.data.PackageManagerAppsSource
import com.fscontroller.app.data.RemapConfigSerializer
import com.fscontroller.app.data.SystemSetupStatusSource
import com.fscontroller.app.model.RemapConfig

class FsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // The only DataStore for this file in the process; UI and RemapService share it via AppGraph.
        val store = DataStoreFactory.create(
            serializer = RemapConfigSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { RemapConfig() },
            produceFile = { dataStoreFile("remap_config.json") },
        )
        AppGraph.repository = DataStoreRemapRepository(store)
        AppGraph.installedApps = PackageManagerAppsSource(this)
        AppGraph.setupStatus = SystemSetupStatusSource(this)
    }
}
