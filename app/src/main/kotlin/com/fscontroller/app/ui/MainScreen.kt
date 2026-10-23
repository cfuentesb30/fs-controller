package com.fscontroller.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.fscontroller.app.R
import com.fscontroller.app.model.AppButton
import com.fscontroller.app.model.SetupStatus

/** Banner box inside the chip: 16:9, sized so Main fits in 540dp. */
private val ChipVisualW = 72.dp
private val ChipVisualH = 40.5.dp
private val RowLabelWidth = 180.dp
private val SwitchWidth = 120.dp
private val ClearWidth = 120.dp

/** What the open confirmation dialog would clear. */
private sealed interface ClearAsk {
    data class One(val button: AppButton, val appLabel: String?) : ClearAsk
    data object All : ClearAsk
}

@Composable
fun MainScreen(
    content: MainContent?,
    setup: SetupStatus?,
    status: StatusLine?,
    initialFocus: MainFocus,
    onShowSteps: () -> Unit,
    onToggleMaster: () -> Unit,
    onChip: (AppButton) -> Unit,
    onSwitch: (AppButton) -> Unit,
    onFooter: () -> Unit,
    onClear: (AppButton) -> Unit,
    onClearAll: () -> Unit,
) {
    val showStepsReq = remember { FocusRequester() }
    val masterReq = remember { FocusRequester() }
    val footerReq = remember { FocusRequester() }
    val chipReqs = remember { AppButton.entries.associateWith { FocusRequester() } }
    val switchReqs = remember { AppButton.entries.associateWith { FocusRequester() } }
    val clearReqs = remember { AppButton.entries.associateWith { FocusRequester() } }
    val clearAllReq = remember { FocusRequester() }
    val firstChip = chipReqs.getValue(AppButton.entries.first())
    val lastChip = chipReqs.getValue(AppButton.entries.last())

    val bannerShown = setup != null && !setup.ready
    var showStepsFocused by remember { mutableStateOf(false) }

    var ask by remember { mutableStateOf<ClearAsk?>(null) }
    // Where focus goes once the dialog window has closed.
    var refocus by remember { mutableStateOf<FocusRequester?>(null) }
    LaunchedEffect(refocus) {
        val target = refocus ?: return@LaunchedEffect
        withFrameNanos { }
        runCatching { target.requestFocus() }
        refocus = null
    }

    // Initial focus / focus restore: only once the rows exist, so focus doesn't jump.
    val loaded = content != null
    LaunchedEffect(loaded) {
        if (!loaded) return@LaunchedEffect
        withFrameNanos { }
        val target = when (initialFocus) {
            is MainFocus.Chip -> chipReqs.getValue(initialFocus.button)
            MainFocus.Footer -> footerReq
            // The banner may be gone (setup finished while on Setup help): fall back to the next item down.
            MainFocus.ShowSteps -> if (bannerShown) showStepsReq else masterReq
        }
        runCatching { target.requestFocus() }
    }
    // If the banner disappears (setup finished, found by polling) while its button has focus, don't lose focus.
    LaunchedEffect(bannerShown) {
        if (!bannerShown && showStepsFocused) {
            showStepsFocused = false
            runCatching { masterReq.requestFocus() }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Fs.SafeHorizontal, vertical = Fs.SafeVertical),
    ) {
        Text(stringResource(R.string.app_title), style = Fs.Title)
        Spacer(Modifier.height(10.dp))
        if (content == null) return@Column

        if (bannerShown) {
            SetupBanner(
                status = setup,
                onShowSteps = onShowSteps,
                buttonModifier = Modifier
                    .focusRequester(showStepsReq)
                    .onFocusChanged { showStepsFocused = it.isFocused }
                    .focusProperties {
                        up = FocusRequester.Cancel
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                        down = masterReq
                    },
            )
            Spacer(Modifier.height(10.dp))
        }

        MasterRow(
            on = content.masterEnabled,
            onClick = onToggleMaster,
            modifier = Modifier
                .focusRequester(masterReq)
                .focusProperties {
                    if (!bannerShown) up = FocusRequester.Cancel
                    left = FocusRequester.Cancel
                    right = FocusRequester.Cancel
                    down = firstChip
                },
        )
        Spacer(Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Up/Down from a Clear lands on the neighbour's Clear, or its switch when it has none.
            fun clearOrSwitch(r: RowUi) =
                if (r.targetPackage != null) clearReqs.getValue(r.button) else switchReqs.getValue(r.button)
            content.rows.forEachIndexed { i, row ->
                val isFirst = i == 0
                val isLast = i == content.rows.lastIndex
                val hasClear = row.targetPackage != null
                ButtonRow(
                    row = row,
                    masterOn = content.masterEnabled,
                    onChip = { onChip(row.button) },
                    onSwitch = { onSwitch(row.button) },
                    onClear = { ask = ClearAsk.One(row.button, row.targetLabel) },
                    chipModifier = Modifier
                        .focusRequester(chipReqs.getValue(row.button))
                        .focusProperties {
                            left = FocusRequester.Cancel
                            right = switchReqs.getValue(row.button)
                            up = if (isFirst) masterReq else chipReqs.getValue(content.rows[i - 1].button)
                            down = if (isLast) footerReq else chipReqs.getValue(content.rows[i + 1].button)
                        },
                    switchModifier = Modifier
                        .focusRequester(switchReqs.getValue(row.button))
                        .focusProperties {
                            right = if (hasClear) clearReqs.getValue(row.button) else FocusRequester.Cancel
                            left = chipReqs.getValue(row.button)
                            up = if (isFirst) masterReq else switchReqs.getValue(content.rows[i - 1].button)
                            down = if (isLast) footerReq else switchReqs.getValue(content.rows[i + 1].button)
                        },
                    clearModifier = Modifier
                        .focusRequester(clearReqs.getValue(row.button))
                        .focusProperties {
                            right = FocusRequester.Cancel
                            left = switchReqs.getValue(row.button)
                            up = if (isFirst) masterReq else clearOrSwitch(content.rows[i - 1])
                            down = if (isLast) footerReq else clearOrSwitch(content.rows[i + 1])
                        },
                )
            }
        }

        Spacer(Modifier.weight(1f))
        // Status line: always reserves its height so nothing shifts when it appears or clears.
        Text(
            text = status?.text.orEmpty(),
            style = Fs.Body,
            color = if (status?.isError == true) Fs.Warning else Fs.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.footer_limits),
                style = Fs.Body,
                color = Fs.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            val anyTarget = content.rows.any { it.targetPackage != null }
            if (anyTarget) {
                FocusSurface(
                    onClick = { ask = ClearAsk.All },
                    containerColor = Fs.Background,
                    modifier = Modifier
                        .focusRequester(clearAllReq)
                        .focusProperties {
                            up = lastChip
                            down = FocusRequester.Cancel
                            left = FocusRequester.Cancel
                            right = footerReq
                        },
                ) {
                    Text(
                        stringResource(R.string.footer_clear_all),
                        style = Fs.Body,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
            }
            FocusSurface(
                onClick = onFooter,
                containerColor = Fs.Background,
                modifier = Modifier
                    .focusRequester(footerReq)
                    .focusProperties {
                        up = lastChip
                        down = FocusRequester.Cancel
                        left = if (anyTarget) clearAllReq else FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    },
            ) {
                Text(
                    stringResource(R.string.footer_setup_link) + "  ›",
                    style = Fs.Body,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                )
            }
        }
    }

    when (val a = ask) {
        null -> Unit
        is ClearAsk.One -> ClearOneDialog(
            button = a.button,
            appLabel = a.appLabel,
            onConfirm = {
                ask = null
                refocus = chipReqs.getValue(a.button)
                onClear(a.button)
            },
            onCancel = {
                ask = null
                refocus = clearReqs.getValue(a.button)
            },
        )
        ClearAsk.All -> ClearAllDialog(
            onConfirm = {
                ask = null
                refocus = firstChip
                onClearAll()
            },
            onCancel = {
                ask = null
                refocus = clearAllReq
            },
        )
    }
}

@Composable
private fun ClearOneDialog(button: AppButton, appLabel: String?, onConfirm: () -> Unit, onCancel: () -> Unit) =
    ConfirmDialog(
        title = stringResource(R.string.clear_one_title, button.label),
        body = clearOneBody(button, appLabel),
        confirmLabel = stringResource(R.string.clear_one_confirm),
        onConfirm = onConfirm,
        onCancel = onCancel,
    )

@Composable
private fun ClearAllDialog(onConfirm: () -> Unit, onCancel: () -> Unit) =
    ConfirmDialog(
        title = stringResource(R.string.clear_all_title),
        body = stringResource(R.string.clear_all_body),
        confirmLabel = stringResource(R.string.clear_all_confirm),
        onConfirm = onConfirm,
        onCancel = onCancel,
    )

@Composable
private fun clearOneBody(button: AppButton, appLabel: String?) = stringResource(
    R.string.clear_one_body,
    button.label,
    appLabel ?: stringResource(R.string.clear_one_unknown_app),
)

@Composable
private fun SetupBanner(status: SetupStatus, onShowSteps: () -> Unit, buttonModifier: Modifier) {
    val text = when {
        !status.serviceEnabled && !status.canReadLogs -> R.string.setup_banner_both
        !status.serviceEnabled -> R.string.setup_banner_service
        else -> R.string.setup_banner_logs
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(Fs.WarningSurface, ControlShape)
            .border(BorderStroke(1.dp, Fs.Warning), ControlShape)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("!", style = Fs.RowLabel, fontWeight = FontWeight.Bold, color = Fs.Warning)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(text), style = Fs.Body, modifier = Modifier.weight(1f))
        FocusSurface(onClick = onShowSteps, modifier = buttonModifier) {
            Text(
                stringResource(R.string.setup_banner_button),
                style = Fs.Body,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun MasterRow(on: Boolean, onClick: () -> Unit, modifier: Modifier) {
    FocusSurface(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.master_label), style = Fs.RowLabel, modifier = Modifier.width(RowLabelWidth - 16.dp))
            Text(
                stringResource(if (on) R.string.master_sub_on else R.string.master_sub_off),
                style = Fs.Body,
                color = Fs.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            StatePill(on)
        }
    }
}

@Composable
private fun ButtonRow(
    row: RowUi,
    masterOn: Boolean,
    onChip: () -> Unit,
    onSwitch: () -> Unit,
    onClear: () -> Unit,
    chipModifier: Modifier,
    switchModifier: Modifier,
    clearModifier: Modifier,
) {
    // Dimmed (secondary text) when the whole master is off, or this row is off but keeps its target.
    val dim = !masterOn || (row.targetPackage != null && !row.enabled)
    val textColor = if (dim) Fs.TextSecondary else Fs.TextPrimary
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            row.button.label,
            style = Fs.RowLabel,
            color = if (!masterOn) Fs.TextSecondary else Fs.TextPrimary,
            modifier = Modifier.width(RowLabelWidth),
        )
        FocusSurface(onClick = onChip, modifier = chipModifier.weight(1f)) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val slot = Modifier.size(ChipVisualW, ChipVisualH)
                when {
                    row.targetPackage == null -> {
                        Box(slot, contentAlignment = Alignment.Center) {
                            Text("+", style = Fs.Title, color = textColor)
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(stringResource(R.string.chip_choose), style = Fs.RowLabel, color = textColor)
                    }
                    row.missing -> {
                        Box(slot, contentAlignment = Alignment.Center) {
                            Text("!", style = Fs.Title, fontWeight = FontWeight.Bold, color = Fs.Warning)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(stringResource(R.string.chip_missing), style = Fs.Body, color = Fs.Warning)
                            Text(
                                row.targetPackage,
                                style = Fs.Small,
                                color = Fs.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    else -> {
                        AppVisualBox(row.targetPackage, slot, dimmed = dim)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            row.targetLabel.orEmpty(),
                            style = Fs.RowLabel,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(24.dp))
        FocusSurface(
            onClick = onSwitch,
            shape = PillShape,
            containerColor = Fs.Background,
            focusedContainerColor = Fs.SurfaceFocused,
            modifier = switchModifier.width(SwitchWidth),
        ) {
            Box(Modifier.fillMaxWidth().padding(6.dp), contentAlignment = Alignment.Center) {
                StatePill(on = row.enabled)
            }
        }
        Spacer(Modifier.width(16.dp))
        // Only rows with a chosen app (installed or not) can be cleared; otherwise keep the space.
        if (row.targetPackage != null) {
            FocusSurface(
                onClick = onClear,
                shape = PillShape,
                containerColor = Fs.Background,
                focusedContainerColor = Fs.SurfaceFocused,
                modifier = clearModifier.width(ClearWidth),
            ) {
                Box(Modifier.fillMaxWidth().padding(6.dp), contentAlignment = Alignment.Center) {
                    ActionPill(stringResource(R.string.row_clear))
                }
            }
        } else {
            Spacer(Modifier.width(ClearWidth))
        }
    }
}

// ---- Previews (focus can't be forced in a static preview; see FocusSurface for the focused style) ----

private val previewRows = listOf(
    RowUi(AppButton.NETFLIX, "org.xbmc.kodi", "Kodi", enabled = true),
    RowUi(AppButton.PRIME_VIDEO, "com.google.android.youtube.tv", "YouTube", enabled = false),
    RowUi(AppButton.DISNEY_PLUS, null, null, enabled = false),
    RowUi(AppButton.PEACOCK, "com.example.gone", null, enabled = true),
)

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun MainNotReadyPreview() = FsTheme {
    MainScreen(
        content = MainContent(masterEnabled = true, rows = previewRows),
        setup = SetupStatus(serviceEnabled = false, canReadLogs = true),
        status = StatusLine("Peacock button now opens Kodi. (Not active until setup is done.)"),
        initialFocus = MainFocus.Chip(AppButton.NETFLIX),
        onShowSteps = {}, onToggleMaster = {}, onChip = {}, onSwitch = {}, onFooter = {}, onClear = {}, onClearAll = {},
    )
}

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun MainMasterOffPreview() = FsTheme {
    MainScreen(
        content = MainContent(masterEnabled = false, rows = previewRows),
        setup = SetupStatus(serviceEnabled = true, canReadLogs = true),
        status = StatusLine("Couldn't save. Try again.", isError = true),
        initialFocus = MainFocus.Chip(AppButton.NETFLIX),
        onShowSteps = {}, onToggleMaster = {}, onChip = {}, onSwitch = {}, onFooter = {}, onClear = {}, onClearAll = {},
    )
}

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun MainEmptyPreview() = FsTheme {
    MainScreen(
        content = MainContent(true, AppButton.entries.map { RowUi(it, null, null, false) }),
        setup = SetupStatus(serviceEnabled = true, canReadLogs = true),
        status = null,
        initialFocus = MainFocus.Chip(AppButton.NETFLIX),
        onShowSteps = {}, onToggleMaster = {}, onChip = {}, onSwitch = {}, onFooter = {}, onClear = {}, onClearAll = {},
    )
}

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun MainLoadingPreview() = FsTheme {
    MainScreen(null, null, null, MainFocus.Chip(AppButton.NETFLIX), {}, {}, {}, {}, {}, {}, {})
}

// The dialog previews show the card only; on the device it sits on a dimmed Main.

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun ClearOneDialogPreview() = FsTheme {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ConfirmDialogCard(
            title = stringResource(R.string.clear_one_title, AppButton.PEACOCK.label),
            body = clearOneBody(AppButton.PEACOCK, "YouTube"),
            confirmLabel = stringResource(R.string.clear_one_confirm),
            onConfirm = {},
            onCancel = {},
        )
    }
}

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun ClearOneMissingDialogPreview() = FsTheme {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ConfirmDialogCard(
            title = stringResource(R.string.clear_one_title, AppButton.PRIME_VIDEO.label),
            body = clearOneBody(AppButton.PRIME_VIDEO, null),
            confirmLabel = stringResource(R.string.clear_one_confirm),
            onConfirm = {},
            onCancel = {},
        )
    }
}

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun ClearAllDialogPreview() = FsTheme {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ConfirmDialogCard(
            title = stringResource(R.string.clear_all_title),
            body = stringResource(R.string.clear_all_body),
            confirmLabel = stringResource(R.string.clear_all_confirm),
            onConfirm = {},
            onCancel = {},
        )
    }
}
