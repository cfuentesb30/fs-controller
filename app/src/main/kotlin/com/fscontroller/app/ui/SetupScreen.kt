package com.fscontroller.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.fscontroller.app.R
import com.fscontroller.app.model.SetupStatus

@Composable
fun SetupScreen(setup: SetupUi?) {
    val firstStepReq = remember { FocusRequester() }
    val emergencyReq = remember { FocusRequester() }
    val steps = setup?.steps.orEmpty()
    val stepIds = steps.map { it.id }

    // Initial focus, and again whenever polling changes which steps are shown (the focused block may be gone).
    LaunchedEffect(setup != null, stepIds) {
        if (setup == null) return@LaunchedEffect
        withFrameNanos { }
        runCatching { (if (steps.isEmpty()) emergencyReq else firstStepReq).requestFocus() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Fs.SafeHorizontal, vertical = Fs.SafeVertical),
    ) {
        Text(stringResource(R.string.setup_title), style = Fs.Title)
        Spacer(Modifier.height(4.dp))
        val done = setup?.status?.ready == true
        Text(
            stringResource(if (done) R.string.setup_all_done else R.string.setup_intro),
            style = Fs.Body,
            color = if (done) Fs.TextPrimary else Fs.TextSecondary,
        )
        if (setup == null) return@Column
        Spacer(Modifier.height(14.dp))

        CheckLine(stringResource(R.string.setup_check_logs), setup.status.canReadLogs)
        CheckLine(stringResource(R.string.setup_check_service), setup.status.serviceEnabled)

        if (steps.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Numbered 1..n as shown, even when only some of the 4 steps apply.
                steps.forEachIndexed { i, step ->
                    CommandBlock(
                        number = "${i + 1}",
                        command = step.command,
                        modifier = if (i == 0) Modifier.focusRequester(firstStepReq) else Modifier,
                    )
                }
            }
            if (steps.any { it.id == 4 }) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.setup_reopen), style = Fs.Body, color = Fs.TextSecondary)
            }
        }

        Spacer(Modifier.height(22.dp))
        Text(stringResource(R.string.setup_emergency_title), style = Fs.RowLabel, color = Fs.Warning)
        Spacer(Modifier.height(8.dp))
        CommandBlock(number = null, command = SetupCommands.KILL_SWITCH, modifier = Modifier.focusRequester(emergencyReq))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.setup_back_hint), style = Fs.Body, color = Fs.TextSecondary)
    }
}

@Composable
private fun CheckLine(label: String, ok: Boolean) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Fs.Body, modifier = Modifier.width(320.dp))
        Text(
            stringResource(if (ok) R.string.setup_done_mark else R.string.setup_missing_mark),
            style = Fs.Body,
            fontWeight = FontWeight.SemiBold,
            color = if (ok) Fs.TextPrimary else Fs.Warning,
        )
    }
}

/** Focusable only so the D-pad can scroll and keep the user's place. Select does nothing. */
@Composable
private fun CommandBlock(number: String?, command: String, modifier: Modifier) {
    FocusSurface(
        onClick = {},
        modifier = modifier
            .fillMaxWidth()
            .focusProperties {
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
            },
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            if (number != null) {
                Text(number, style = Fs.Mono, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp))
            }
            Text(command, style = Fs.Mono)
        }
    }
}

// ---- Previews ----

private val previewList = "com.example.a11y/.Service:com.fscontroller.app/com.fscontroller.app.service.RemapService"

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun SetupNothingDonePreview() = FsTheme {
    SetupScreen(
        SetupUi(
            SetupStatus(serviceEnabled = false, canReadLogs = false),
            listOf(
                SetupStep(1, "adb shell pm grant com.fscontroller.app android.permission.READ_LOGS"),
                SetupStep(2, "adb shell settings put secure enabled_accessibility_services $previewList"),
                SetupStep(3, "adb shell settings put secure accessibility_enabled 1"),
                SetupStep(4, "adb reboot"),
            ),
        ),
    )
}

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun SetupDonePreview() = FsTheme {
    SetupScreen(SetupUi(SetupStatus(serviceEnabled = true, canReadLogs = true), emptyList()))
}
