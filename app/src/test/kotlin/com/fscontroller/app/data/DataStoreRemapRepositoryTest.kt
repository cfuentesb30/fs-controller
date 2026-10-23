package com.fscontroller.app.data

import androidx.datastore.core.DataStore
import com.fscontroller.app.model.AppButton
import com.fscontroller.app.model.ButtonMapping
import com.fscontroller.app.model.RemapConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DataStoreRemapRepositoryTest {

    private class InMemoryStore(initial: RemapConfig) : DataStore<RemapConfig> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<RemapConfig> = state
        override suspend fun updateData(transform: suspend (RemapConfig) -> RemapConfig): RemapConfig =
            transform(state.value).also { state.value = it }
    }

    private val start = RemapConfig(
        masterEnabled = false,
        mappings = mapOf(
            AppButton.PEACOCK to ButtonMapping("com.example.tv", enabled = true),
            AppButton.NETFLIX to ButtonMapping("com.example.other", enabled = false),
        ),
    )

    @Test
    fun clearTarget_forgetsAppAndTurnsOff_onlyForThatButton() = runTest {
        val repo = DataStoreRemapRepository(InMemoryStore(start))
        repo.clearTarget(AppButton.PEACOCK)
        val config = repo.config.first()
        assertEquals(ButtonMapping(), config.mappingFor(AppButton.PEACOCK))
        assertNull(config.activeTargetFor(AppButton.PEACOCK))
        assertEquals(ButtonMapping("com.example.other", enabled = false), config.mappingFor(AppButton.NETFLIX))
        assertEquals(false, config.masterEnabled)
    }

    @Test
    fun clearAll_forgetsEveryApp_keepsMasterSwitch() = runTest {
        val repo = DataStoreRemapRepository(InMemoryStore(start))
        repo.clearAll()
        val config = repo.config.first()
        AppButton.entries.forEach { assertEquals(ButtonMapping(), config.mappingFor(it)) }
        assertEquals(false, config.masterEnabled)
    }

    @Test
    fun clearedButton_canBePickedAgain() = runTest {
        val repo = DataStoreRemapRepository(InMemoryStore(start))
        repo.clearTarget(AppButton.PEACOCK)
        repo.setTarget(AppButton.PEACOCK, "com.example.new")
        assertEquals(ButtonMapping("com.example.new", enabled = true), repo.config.first().mappingFor(AppButton.PEACOCK))
    }
}
