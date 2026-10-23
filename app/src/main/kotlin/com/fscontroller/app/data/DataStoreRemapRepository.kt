package com.fscontroller.app.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import com.fscontroller.app.model.AppButton
import com.fscontroller.app.model.RemapConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class DataStoreRemapRepository(private val store: DataStore<RemapConfig>) : RemapRepository {

    override val config: Flow<RemapConfig> = store.data.catch { e ->
        if (e is IOException) emit(RemapConfig()) else throw e
    }

    override suspend fun setMasterEnabled(enabled: Boolean) {
        store.updateData { it.copy(masterEnabled = enabled) }
    }

    override suspend fun setTarget(button: AppButton, packageName: String) {
        store.updateData { config ->
            val mapping = config.mappingFor(button).copy(targetPackage = packageName, enabled = true)
            config.copy(mappings = config.mappings + (button to mapping))
        }
    }

    override suspend fun setButtonEnabled(button: AppButton, enabled: Boolean) {
        store.updateData { config ->
            val mapping = config.mappingFor(button).copy(enabled = enabled)
            config.copy(mappings = config.mappings + (button to mapping))
        }
    }
}

object RemapConfigSerializer : Serializer<RemapConfig> {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val defaultValue: RemapConfig = RemapConfig()

    override suspend fun readFrom(input: InputStream): RemapConfig =
        try {
            json.decodeFromString(RemapConfig.serializer(), input.readBytes().decodeToString())
        } catch (e: SerializationException) {
            throw CorruptionException("Unreadable remap config", e)
        } catch (e: IllegalArgumentException) {
            throw CorruptionException("Unreadable remap config", e)
        }

    override suspend fun writeTo(t: RemapConfig, output: OutputStream) {
        output.write(json.encodeToString(RemapConfig.serializer(), t).encodeToByteArray())
    }
}
