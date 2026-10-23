package com.fscontroller.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.fscontroller.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val ControlShape = RoundedCornerShape(12.dp)
val PillShape = RoundedCornerShape(50)

/**
 * The one focusable building block. Focus = white 3dp border + 1.05x scale + lighter surface,
 * so focus never depends on color alone.
 */
@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = ControlShape,
    containerColor: Color = Fs.SurfaceRest,
    focusedContainerColor: Color = Fs.SurfaceFocused,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            contentColor = Fs.TextPrimary,
            focusedContainerColor = focusedContainerColor,
            focusedContentColor = Fs.TextPrimary,
            pressedContainerColor = focusedContainerColor,
            pressedContentColor = Fs.TextPrimary,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = Fs.FocusScale),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(Fs.FocusBorderWidth, Fs.FocusBorder), shape = shape),
            pressedBorder = Border(BorderStroke(Fs.FocusBorderWidth, Fs.FocusBorder), shape = shape),
        ),
        content = content,
    )
}

/** On/Off pill. The word is always shown; the fill is only an extra cue. Not focusable by itself. */
@Composable
fun StatePill(on: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(PillShape)
            .background(if (on) Fs.Accent else Color(0xFF4A4A4A))
            .padding(horizontal = 18.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(if (on) R.string.switch_on else R.string.switch_off),
            style = Fs.Body,
            fontWeight = FontWeight.SemiBold,
            color = Fs.TextPrimary,
        )
    }
}

/**
 * 16:9 box with the app's banner, or its icon centered when it has no banner. Loaded lazily off
 * the main thread; empty while loading or when there's no artwork.
 */
@Composable
fun AppVisualBox(packageName: String, modifier: Modifier = Modifier, dimmed: Boolean = false) {
    val preview = LocalInspectionMode.current
    val visual by produceState<AppVisual?>(null, packageName) {
        if (!preview) value = withContext(Dispatchers.IO) { AppVisuals.load(packageName) }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF1E1E1E))
            .alpha(if (dimmed) 0.6f else 1f),
        contentAlignment = Alignment.Center,
    ) {
        val v = visual ?: return@Box
        Image(
            bitmap = v.image,
            contentDescription = null,
            contentScale = if (v.isBanner) ContentScale.Crop else ContentScale.Fit,
            modifier = if (v.isBanner) Modifier.matchParentSize() else Modifier.fillMaxHeight(0.8f).aspectRatio(1f),
        )
    }
}
