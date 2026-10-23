package com.fscontroller.app.ui

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fscontroller.app.AppGraph
import com.fscontroller.app.R
import com.fscontroller.app.model.AppButton
import com.fscontroller.app.model.InstalledApp
import com.fscontroller.app.model.RemapConfig
import com.fscontroller.app.model.SetupStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The three state-based views. */
sealed interface Screen {
    data object Main : Screen
    data class Picker(val button: AppButton) : Screen
    data object Setup : Screen
}

/** Which Main element gets focus when Main (re)appears. */
sealed interface MainFocus {
    data class Chip(val button: AppButton) : MainFocus
    data object ShowSteps : MainFocus
    data object Footer : MainFocus
}

data class RowUi(
    val button: AppButton,
    val targetPackage: String?,
    /** Null when there's a target but it isn't installed any more. */
    val targetLabel: String?,
    val enabled: Boolean,
) {
    val missing: Boolean get() = targetPackage != null && targetLabel == null
}

data class MainContent(val masterEnabled: Boolean, val rows: List<RowUi>)

data class StatusLine(val text: String, val isError: Boolean = false)

data class SetupUi(val status: SetupStatus, val steps: List<SetupStep>)

sealed interface PickerUi {
    data object Loading : PickerUi
    data object Error : PickerUi
    data class Loaded(val apps: List<InstalledApp>, val currentPackage: String?) : PickerUi
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = AppGraph.repository
    private val installedApps = AppGraph.installedApps
    private val setupSource = AppGraph.setupStatus

    private val _screen = MutableStateFlow<Screen>(Screen.Main)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    /** Read by Main each time it appears; the first appearance focuses the Netflix chip. */
    var mainFocus: MainFocus = MainFocus.Chip(AppButton.NETFLIX)
        private set

    /** Bumped on resume so labels are re-read (catches apps uninstalled while we were away). */
    private val labelRefresh = MutableStateFlow(0)

    /** Null until the first config emission: Main shows the title only (loading config). */
    val main: StateFlow<MainContent?> = combine(repository.config, labelRefresh) { config, _ -> toContent(config) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _setup = MutableStateFlow<SetupUi?>(null)
    val setup: StateFlow<SetupUi?> = _setup.asStateFlow()

    private val _status = MutableStateFlow<StatusLine?>(null)
    val status: StateFlow<StatusLine?> = _status.asStateFlow()
    private var statusClear: Job? = null

    private val _picker = MutableStateFlow<PickerUi>(PickerUi.Loading)
    val picker: StateFlow<PickerUi> = _picker.asStateFlow()
    private var pickerLoad: Job? = null

    private fun toContent(config: RemapConfig) = MainContent(
        masterEnabled = config.masterEnabled,
        rows = AppButton.entries.map { button ->
            val m = config.mappingFor(button)
            RowUi(button, m.targetPackage, m.targetPackage?.let { runCatching { installedApps.labelFor(it) }.getOrNull() }, m.enabled)
        },
    )

    // ---- Lifecycle hooks, called by the UI while Main or Setup help is visible and resumed ----

    fun onResumedVisible() {
        labelRefresh.value++
    }

    suspend fun refreshSetup() {
        val ui = withContext(Dispatchers.IO) {
            runCatching {
                val s = setupSource.current()
                SetupUi(s, SetupCommands.stepsFor(getApplication(), s))
            }.getOrNull()
        }
        if (ui != null) _setup.value = ui
    }

    // ---- Navigation ----

    fun openPicker(button: AppButton) {
        mainFocus = MainFocus.Chip(button)
        _screen.value = Screen.Picker(button)
        loadApps(button)
    }

    fun openSetup(from: MainFocus) {
        mainFocus = from
        _screen.value = Screen.Setup
    }

    fun backToMain() {
        pickerLoad?.cancel()
        _screen.value = Screen.Main
    }

    fun retryApps() {
        (screen.value as? Screen.Picker)?.let { loadApps(it.button) }
    }

    private fun loadApps(button: AppButton) {
        pickerLoad?.cancel()
        _picker.value = PickerUi.Loading
        val current = main.value?.rows?.firstOrNull { it.button == button }?.targetPackage
        pickerLoad = viewModelScope.launch {
            _picker.value = try {
                PickerUi.Loaded(withContext(Dispatchers.IO) { installedApps.launchableApps() }, current)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PickerUi.Error
            }
        }
    }

    // ---- Actions ----

    fun toggleMaster() {
        val content = main.value ?: return
        val newValue = !content.masterEnabled
        save({ repository.setMasterEnabled(newValue) }) {
            if (newValue) withSuffix(str(R.string.toast_master_on), notReadyOnly = true)
            else str(R.string.toast_master_off)
        }
    }

    /** Switch: toggles the row, or opens the picker when there's no target yet. */
    fun onSwitch(button: AppButton) {
        val row = main.value?.rows?.firstOrNull { it.button == button } ?: return
        if (row.targetPackage == null) {
            openPicker(button)
            return
        }
        val newValue = !row.enabled
        save({ repository.setButtonEnabled(button, newValue) }) {
            if (newValue) withSuffix(str(R.string.toast_button_on, button.label, row.targetLabel ?: row.targetPackage))
            else str(R.string.toast_button_off, button.label)
        }
    }

    fun pick(button: AppButton, app: InstalledApp) {
        mainFocus = MainFocus.Chip(button)
        backToMain()
        save({ repository.setTarget(button, app.packageName) }) {
            val base = str(R.string.toast_button_on, button.label, app.label)
            "$base ${suffix() ?: str(R.string.toast_try_it)}"
        }
    }

    private fun save(action: suspend () -> Unit, success: () -> String) {
        viewModelScope.launch {
            try {
                action()
                showStatus(StatusLine(success()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showStatus(StatusLine(str(R.string.toast_save_failed), isError = true))
            }
        }
    }

    /** "(Not active until setup is done.)" wins over "(All remaps is off.)": nothing works until setup is done. */
    private fun suffix(): String? = when {
        _setup.value?.status?.ready == false -> str(R.string.toast_suffix_not_ready)
        main.value?.masterEnabled == false -> str(R.string.toast_suffix_master_off)
        else -> null
    }

    private fun withSuffix(base: String, notReadyOnly: Boolean = false): String {
        val s = if (notReadyOnly) {
            if (_setup.value?.status?.ready == false) str(R.string.toast_suffix_not_ready) else null
        } else suffix()
        return if (s == null) base else "$base $s"
    }

    private fun showStatus(line: StatusLine) {
        statusClear?.cancel()
        _status.value = line
        statusClear = viewModelScope.launch {
            delay(STATUS_MS)
            _status.value = null
        }
    }

    private fun str(@StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    private companion object {
        const val STATUS_MS = 4_000L
    }
}
