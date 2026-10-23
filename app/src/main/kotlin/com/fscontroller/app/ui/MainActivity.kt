package com.fscontroller.app.ui

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Avoid a white flash from the platform theme before Compose draws.
        window.setBackgroundDrawable(ColorDrawable(Fs.Background.toArgb()))
        setContent {
            FsTheme { FsControllerApp() }
        }
    }
}

private const val SETUP_POLL_MS = 2_000L

/** Three views switched by state, no nav library. */
@Composable
fun FsControllerApp(vm: MainViewModel = viewModel()) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    val main by vm.main.collectAsStateWithLifecycle()
    val setup by vm.setup.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val picker by vm.picker.collectAsStateWithLifecycle()

    // Re-read setup status on resume and every 2 s while Main or Setup help is visible: commands run
    // from the PC don't trigger a resume. Labels are re-read on resume to catch uninstalls.
    val lifecycleOwner = LocalLifecycleOwner.current
    val polling = screen !is Screen.Picker
    LaunchedEffect(lifecycleOwner, polling) {
        if (!polling) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            vm.onResumedVisible()
            while (true) {
                vm.refreshSetup()
                delay(SETUP_POLL_MS)
            }
        }
    }

    // Back in the picker or Setup help returns to Main. On Main nothing is intercepted, so Back exits.
    BackHandler(enabled = screen != Screen.Main) { vm.backToMain() }

    when (val s = screen) {
        Screen.Main -> MainScreen(
            content = main,
            setup = setup?.status,
            status = status,
            initialFocus = vm.mainFocus,
            onShowSteps = { vm.openSetup(MainFocus.ShowSteps) },
            onToggleMaster = vm::toggleMaster,
            onChip = vm::openPicker,
            onSwitch = vm::onSwitch,
            onFooter = { vm.openSetup(MainFocus.Footer) },
            onClear = vm::clearTarget,
            onClearAll = vm::clearAll,
        )
        is Screen.Picker -> PickerScreen(
            button = s.button,
            state = picker,
            onPick = { app -> vm.pick(s.button, app) },
            onRetry = vm::retryApps,
        )
        Screen.Setup -> SetupScreen(setup)
    }
}
