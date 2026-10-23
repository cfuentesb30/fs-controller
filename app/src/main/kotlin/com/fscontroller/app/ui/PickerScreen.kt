package com.fscontroller.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.fscontroller.app.R
import com.fscontroller.app.model.AppButton
import com.fscontroller.app.model.InstalledApp

private const val COLUMNS = 4

@Composable
fun PickerScreen(
    button: AppButton,
    state: PickerUi,
    onPick: (InstalledApp) -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Fs.SafeHorizontal, vertical = Fs.SafeVertical),
    ) {
        Text(stringResource(R.string.picker_title, button.label), style = Fs.Title)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.picker_hint), style = Fs.Body, color = Fs.TextSecondary)
        Spacer(Modifier.height(12.dp))

        when (state) {
            PickerUi.Loading -> CenterMessage(stringResource(R.string.picker_loading))
            PickerUi.Error -> PickerError(onRetry)
            is PickerUi.Loaded ->
                if (state.apps.isEmpty()) CenterMessage(stringResource(R.string.picker_empty))
                else AppGrid(state.apps, state.currentPackage, onPick)
        }
    }
}

@Composable
private fun CenterMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = Fs.Body, color = Fs.TextSecondary)
    }
}

@Composable
private fun PickerError(onRetry: () -> Unit) {
    val retryReq = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { retryReq.requestFocus() }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.picker_error), style = Fs.Body)
            Spacer(Modifier.height(16.dp))
            FocusSurface(
                onClick = onRetry,
                modifier = Modifier
                    .focusRequester(retryReq)
                    .focusProperties {
                        up = FocusRequester.Cancel
                        down = FocusRequester.Cancel
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    },
            ) {
                Text(
                    stringResource(R.string.picker_retry),
                    style = Fs.Body,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun AppGrid(apps: List<InstalledApp>, currentPackage: String?, onPick: (InstalledApp) -> Unit) {
    // Current target if it's in the list, else the first card.
    val initialIndex = apps.indexOfFirst { it.packageName == currentPackage }.coerceAtLeast(0)
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = initialIndex - initialIndex % COLUMNS)
    val initialReq = remember { FocusRequester() }
    LaunchedEffect(apps) {
        withFrameNanos { }
        runCatching { initialReq.requestFocus() }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(COLUMNS),
        state = gridState,
        // Room for the 1.05x focus scale and border so they're never clipped.
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
            val col = index % COLUMNS
            val isLastInRow = col == COLUMNS - 1 || index == apps.lastIndex
            AppCard(
                app = app,
                isCurrent = app.packageName == currentPackage,
                onClick = { onPick(app) },
                modifier = Modifier
                    .then(if (index == initialIndex) Modifier.focusRequester(initialReq) else Modifier)
                    // Left/Right stay inside the row; Up from the top row does nothing.
                    .focusProperties {
                        if (col == 0) left = FocusRequester.Cancel
                        if (isLastInRow) right = FocusRequester.Cancel
                        if (index < COLUMNS) up = FocusRequester.Cancel
                    },
            )
        }
    }
}

@Composable
private fun AppCard(app: InstalledApp, isCurrent: Boolean, onClick: () -> Unit, modifier: Modifier) {
    FocusSurface(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            AppVisualBox(
                app.packageName,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    app.label,
                    style = Fs.Body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isCurrent) Text("  ✓", style = Fs.Body, fontWeight = FontWeight.Bold, color = Fs.Accent)
            }
            // Always reserve the tag line so every card in a row has the same height.
            Text(
                if (isCurrent) stringResource(R.string.picker_current) else "",
                style = Fs.Small,
                fontWeight = FontWeight.Medium,
                color = Fs.Accent,
            )
        }
    }
}

// ---- Previews ----

private val previewApps = listOf(
    InstalledApp("com.disney.disneyplus", "Disney+"),
    InstalledApp("org.xbmc.kodi", "Kodi"),
    InstalledApp("com.netflix.ninja", "Netflix"),
    InstalledApp("com.plexapp.android", "Plex"),
    InstalledApp("com.google.android.youtube.tv", "YouTube"),
    InstalledApp("com.example.longname", "An app with a very long name that ellipsizes"),
)

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun PickerLoadedPreview() = FsTheme {
    PickerScreen(AppButton.PEACOCK, PickerUi.Loaded(previewApps, "org.xbmc.kodi"), {}, {})
}

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun PickerLoadingPreview() = FsTheme { PickerScreen(AppButton.PEACOCK, PickerUi.Loading, {}, {}) }

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun PickerEmptyPreview() = FsTheme { PickerScreen(AppButton.PEACOCK, PickerUi.Loaded(emptyList(), null), {}, {}) }

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun PickerErrorPreview() = FsTheme { PickerScreen(AppButton.PEACOCK, PickerUi.Error, {}, {}) }
