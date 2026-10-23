package com.fscontroller.app.data

import androidx.datastore.core.CorruptionException
import com.fscontroller.app.model.AppButton
import com.fscontroller.app.model.ButtonMapping
import com.fscontroller.app.model.RemapConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class RemapConfigSerializerTest {

    @Test
    fun roundTrip() = runTest {
        val config = RemapConfig(
            masterEnabled = false,
            mappings = mapOf(
                AppButton.PEACOCK to ButtonMapping("com.example.tv", enabled = true),
                AppButton.NETFLIX to ButtonMapping("com.example.other", enabled = false),
            ),
        )
        val out = ByteArrayOutputStream()
        RemapConfigSerializer.writeTo(config, out)
        assertEquals(config, RemapConfigSerializer.readFrom(ByteArrayInputStream(out.toByteArray())))
    }

    @Test
    fun unknownKeys_areIgnored() = runTest {
        val json = """{"masterEnabled":true,"future":1,"mappings":{"PEACOCK":{"targetPackage":"a.b","enabled":true,"x":2}}}"""
        val config = RemapConfigSerializer.readFrom(ByteArrayInputStream(json.encodeToByteArray()))
        assertEquals("a.b", config.activeTargetFor(AppButton.PEACOCK))
    }

    @Test
    fun garbage_isCorruption() = runTest {
        for (bad in listOf("not json", """{"mappings":{"NOT_A_BUTTON":{}}}""", "")) {
            try {
                RemapConfigSerializer.readFrom(ByteArrayInputStream(bad.encodeToByteArray()))
                fail("expected CorruptionException for: $bad")
            } catch (e: CorruptionException) {
                // expected; DataStore's ReplaceFileCorruptionHandler then writes the defaults
            }
        }
    }
}
