package io.github.kurohi.akachannoise.data

import io.github.kurohi.akachannoise.data.model.UserData
import io.github.kurohi.akachannoise.data.store.UserDataMigrations
import io.github.kurohi.akachannoise.data.store.UserDataSerializer
import io.github.kurohi.akachannoise.engine.model.LayerSpec
import io.github.kurohi.akachannoise.engine.model.MixSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserDataSerializationTest {

    @Test
    fun `document round trips through the serializer`() {
        val data = UserData(
            mixes = listOf(MixSpec("user.1", "Rainy night", listOf(LayerSpec("rain", 0.8f)))),
            favoriteIds = listOf("user.1"),
            lastUsedMixId = "user.1",
        )
        val encoded = UserDataSerializer.encode(data)
        val decoded = UserDataSerializer.decode(encoded)
        assertEquals(data, decoded)
    }

    @Test
    fun `corrupt document decodes to null`() {
        assertNull(UserDataSerializer.decode("{{{"))
        assertNull(UserDataSerializer.decode(""))
    }

    @Test
    fun `unknown fields from a newer version are ignored`() {
        val json = """{"schemaVersion":1,"mixes":[],"somethingNew":{"a":1}}"""
        val decoded = UserDataSerializer.decode(json)
        assertEquals(UserData.CURRENT_SCHEMA_VERSION, decoded!!.schemaVersion)
    }

    @Test
    fun `older schema versions are upgraded`() {
        val old = UserData(schemaVersion = 0, favoriteIds = listOf("preset.womb"))
        val migrated = UserDataMigrations.migrate(old)
        assertEquals(UserData.CURRENT_SCHEMA_VERSION, migrated.schemaVersion)
        assertEquals(listOf("preset.womb"), migrated.favoriteIds)
    }

    @Test
    fun `default document is empty and current`() {
        assertEquals(UserData.CURRENT_SCHEMA_VERSION, UserData.EMPTY.schemaVersion)
        assertEquals(emptyList<MixSpec>(), UserData.EMPTY.mixes)
    }
}
