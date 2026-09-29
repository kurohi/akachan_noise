package io.github.kurohi.akachannoise.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import io.github.kurohi.akachannoise.data.model.UserData
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * Persistence for the user's own content. Kept as an interface so the
 * repositories can be tested on the JVM with an in-memory implementation.
 */
interface UserDataStore {
    val data: Flow<UserData>

    suspend fun update(transform: (UserData) -> UserData)
}

/**
 * Serializer for the versioned JSON document. Unknown fields are ignored so
 * that a newer app version's file still loads, and a corrupt or unreadable
 * file falls back to empty data instead of crashing the app.
 */
object UserDataSerializer : Serializer<UserData> {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    override val defaultValue: UserData = UserData.EMPTY

    override suspend fun readFrom(input: InputStream): UserData {
        val text = input.readBytes().decodeToString()
        if (text.isBlank()) return defaultValue
        return try {
            UserDataMigrations.migrate(json.decodeFromString<UserData>(text))
        } catch (e: SerializationException) {
            defaultValue
        } catch (e: IllegalArgumentException) {
            defaultValue
        }
    }

    override suspend fun writeTo(t: UserData, output: OutputStream) {
        output.write(json.encodeToString(UserData.serializer(), t).encodeToByteArray())
    }

    fun encode(data: UserData): String = json.encodeToString(UserData.serializer(), data)

    fun decode(text: String): UserData? = try {
        UserDataMigrations.migrate(json.decodeFromString<UserData>(text))
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}

/**
 * Schema upgrades. Each step is small and pure so it can be unit tested.
 */
object UserDataMigrations {

    fun migrate(data: UserData): UserData {
        var current = data
        // Future schema steps chain here, e.g.:
        // if (current.schemaVersion < 2) current = migrateV1ToV2(current)
        if (current.schemaVersion != UserData.CURRENT_SCHEMA_VERSION) {
            current = current.copy(schemaVersion = UserData.CURRENT_SCHEMA_VERSION)
        }
        return current
    }
}

/** DataStore-backed implementation; the file lives in app-private storage. */
class DataStoreUserDataStore(context: Context) : UserDataStore {

    private val appContext = context.applicationContext

    private val store: DataStore<UserData> = DataStoreFactory.create(
        serializer = UserDataSerializer,
        produceFile = { appContext.dataStoreFile(FILE_NAME) },
    )

    override val data: Flow<UserData> = store.data

    override suspend fun update(transform: (UserData) -> UserData) {
        store.updateData { transform(it) }
    }

    companion object {
        const val FILE_NAME = "user_data.json"
    }
}
