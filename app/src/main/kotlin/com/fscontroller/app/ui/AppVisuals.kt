package com.fscontroller.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.fscontroller.app.AppGraph
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A rendered app banner or icon. [isBanner] is true for wide (about 16:9) artwork. */
class AppVisual(val image: ImageBitmap, val isBanner: Boolean)

/** Lazy, cached rendering of [com.fscontroller.app.data.InstalledAppsSource.visualFor]. */
object AppVisuals {
    private const val MAX_EDGE_PX = 480f
    private val cache = LruCache<String, AppVisual>(64)

    /** Blocking. Call from Dispatchers.IO. Null if the app isn't installed or has no artwork. */
    fun load(packageName: String): AppVisual? {
        cache.get(packageName)?.let { return it }
        val drawable = runCatching { AppGraph.installedApps.visualFor(packageName) }.getOrNull() ?: return null
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 320
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 180
        val scale = min(1f, MAX_EDGE_PX / max(w, h))
        val bw = (w * scale).roundToInt().coerceAtLeast(1)
        val bh = (h * scale).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val d = drawable.mutate()
        d.setBounds(0, 0, bw, bh)
        d.draw(Canvas(bitmap))
        val visual = AppVisual(bitmap.asImageBitmap(), isBanner = w.toFloat() / h >= 1.5f)
        cache.put(packageName, visual)
        return visual
    }
}
