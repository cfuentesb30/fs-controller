package com.fscontroller.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
 * Outlined pill for a row action (e.g. Clear). Same size as [StatePill] but with no fill, so it
 * doesn't read as an Off state. Not focusable by itself.
 */
@Composable
fun ActionPill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .border(BorderStroke(1.5.dp, Fs.TextSecondary), PillShape)
            .padding(horizontal = 18.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = Fs.Body, fontWeight = FontWeight.SemiBold, color = Fs.TextPrimary)
    }
}

/**
 * Modal confirmation for a destructive action. The platform dialog window dims what's behind it
 * and keeps D-pad focus inside. Focus starts on Cancel (the safe choice); Back also cancels.
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        ConfirmDialogCard(title, body, confirmLabel, onConfirm, onCancel, focusCancel = true)
    }
}

/** The dialog's content, separate from the window so it can be previewed. */
@Composable
fun ConfirmDialogCard(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    focusCancel: Boolean = false,
) {
    val cancelReq = remember { FocusRequester() }
    val confirmReq = remember { FocusRequester() }
    if (focusCancel) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { cancelReq.requestFocus() }
        }
    }
    Column(
        Modifier
            .width(DialogWidth)
            .clip(ControlShape)
            .background(Fs.Background)
            .border(BorderStroke(1.dp, Fs.SurfaceFocused), ControlShape)
            .padding(horizontal = 32.dp, vertical = 28.dp),
    ) {
        Text(title, style = Fs.Title)
        Spacer(Modifier.height(12.dp))
        Text(body, style = Fs.Body)
        Spacer(Modifier.height(28.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
        ) {
            DialogButton(
                text = stringResource(R.string.dialog_cancel),
                onClick = onCancel,
                modifier = Modifier
                    .focusRequester(cancelReq)
                    .focusProperties {
                        left = FocusRequester.Cancel
                        right = confirmReq
                        up = FocusRequester.Cancel
                        down = FocusRequester.Cancel
                    },
            )
            DialogButton(
                text = confirmLabel,
                onClick = onConfirm,
                modifier = Modifier
                    .focusRequester(confirmReq)
                    .focusProperties {
                        left = cancelReq
                        right = FocusRequester.Cancel
                        up = FocusRequester.Cancel
                        down = FocusRequester.Cancel
                    },
            )
        }
    }
}

private val DialogWidth = 600.dp

@Composable
private fun DialogButton(text: String, onClick: () -> Unit, modifier: Modifier) {
    FocusSurface(onClick = onClick, modifier = modifier) {
        Text(
            text,
            style = Fs.Body,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
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
