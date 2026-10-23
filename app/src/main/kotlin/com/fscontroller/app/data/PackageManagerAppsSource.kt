package com.fscontroller.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.fscontroller.app.model.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator

class PackageManagerAppsSource(context: Context) : InstalledAppsSource {
    private val pm: PackageManager = context.packageManager
    private val ownPackage: String = context.packageName

    override suspend fun launchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val collator = Collator.getInstance()
        listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
            .flatMap { category ->
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
            }
            .map { it.activityInfo.applicationInfo }
            .filter { it.packageName != ownPackage }
            .distinctBy { it.packageName }
            .map { InstalledApp(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedWith(compareBy(collator) { it.label })
    }

    override fun visualFor(packageName: String): Drawable? = try {
        pm.getLeanbackLaunchIntentForPackage(packageName)?.let { pm.getActivityBanner(it) }
            ?: pm.getApplicationBanner(packageName)
            ?: pm.getApplicationIcon(packageName)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    override fun labelFor(packageName: String): String? = try {
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}
