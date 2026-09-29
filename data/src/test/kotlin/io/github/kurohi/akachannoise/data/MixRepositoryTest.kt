package io.github.kurohi.akachannoise.data

import io.github.kurohi.akachannoise.data.model.UserData
import io.github.kurohi.akachannoise.data.repo.MixRepository
import io.github.kurohi.akachannoise.data.store.UserDataStore
import io.github.kurohi.akachannoise.engine.model.BuiltInPresets
import io.github.kurohi.akachannoise.engine.model.LayerSpec
import io.github.kurohi.akachannoise.engine.model.MixSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class InMemoryUserDataStore(initial: UserData = UserData.EMPTY) : UserDataStore {
    private val state = MutableStateFlow(initial)
    override val data: Flow<UserData> = state
    override suspend fun update(transform: (UserData) -> UserData) {
        state.value = transform(state.value)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MixRepositoryTest {

    private fun repo(
        store: InMemoryUserDataStore,
        scope: CoroutineScope,
    ) = MixRepository(store, scope)

    private fun userMix(id: String, name: String) = MixSpec(id, name, listOf(LayerSpec("noise_pink", 0.8f)))

    @Test
    fun `presets are available and read-only`() = runTest {
        val store = InMemoryUserDataStore()
        val repo = repo(store, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        assertEquals(BuiltInPresets.presets.size, repo.presets.size)
        assertEquals(BuiltInPresets.presets.size, repo.allMixes.value.size)
        assertTrue(MixRepository.isPresetId(repo.presets.first().id))
        try {
            repo.saveMix(repo.presets.first())
            throw AssertionError("saving a preset should be rejected")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `save retrieve rename and delete`() = runTest {
        val store = InMemoryUserDataStore()
        val repo = repo(store, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val mix = userMix(MixRepository.newMixId(), "Night one")

        repo.saveMix(mix)
        assertEquals(listOf(mix), repo.userMixes.value)
        assertEquals(mix, repo.mixById(mix.id))

        repo.renameMix(mix.id, "  Calm night  ")
        assertEquals("Calm night", repo.mixById(mix.id)!!.name)

        repo.renameMix(mix.id, "   ")
        assertEquals("Calm night", repo.mixById(mix.id)!!.name)

        repo.saveMix(mix.copy(name = "Updated"))
        assertEquals(1, repo.userMixes.value.size)
        assertEquals("Updated", repo.mixById(mix.id)!!.name)

        repo.deleteMix(mix.id)
        assertNull(repo.mixById(mix.id))
        assertTrue(repo.userMixes.value.isEmpty())
    }

    @Test
    fun `duplicate preset gets a fresh id and a unique name`() = runTest {
        val store = InMemoryUserDataStore()
        val repo = repo(store, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val preset = repo.presets.first { it.name == "Womb" }

        val copy = repo.duplicateMix(preset.id)!!
        assertNotEquals(preset.id, copy.id)
        assertTrue(MixRepository.isPresetId(preset.id))
        assertEquals("Womb (2)", copy.name)
        assertEquals(1, repo.userMixes.value.size)

        val second = repo.duplicateMix(preset.id)!!
        assertEquals("Womb (3)", second.name)
    }

    @Test
    fun `favorites and ordering`() = runTest {
        val store = InMemoryUserDataStore()
        val repo = repo(store, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val a = userMix(MixRepository.newMixId(), "A")
        val b = userMix(MixRepository.newMixId(), "B")
        repo.saveMix(a)
        repo.saveMix(b)

        repo.setFavorite(a.id, true)
        repo.setFavorite(b.id, true)
        repo.setFavorite(b.id, true) // idempotent
        assertEquals(listOf(a.id, b.id), repo.favoriteIds.value)
        assertEquals(listOf(a.name, b.name), repo.favoriteMixes.value.map { it.name })

        repo.reorderFavorites(listOf(b.id, a.id))
        assertEquals(listOf(b.id, a.id), repo.favoriteIds.value)

        repo.setFavorite(a.id, false)
        assertEquals(listOf(b.id), repo.favoriteIds.value)

        // Deleting a mix also removes it from favorites.
        repo.deleteMix(b.id)
        assertTrue(repo.favoriteIds.value.isEmpty())
    }

    @Test
    fun `reorder mixes follows the requested order`() = runTest {
        val store = InMemoryUserDataStore()
        val repo = repo(store, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val a = userMix(MixRepository.newMixId(), "A")
        val b = userMix(MixRepository.newMixId(), "B")
        val c = userMix(MixRepository.newMixId(), "C")
        listOf(a, b, c).forEach { repo.saveMix(it) }

        repo.reorderMixes(listOf(c.id, a.id))
        assertEquals(listOf("C", "A", "B"), repo.userMixes.value.map { it.name })
    }

    @Test
    fun `last used mix is tracked and cleared on delete`() = runTest {
        val store = InMemoryUserDataStore()
        val repo = repo(store, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val mix = userMix(MixRepository.newMixId(), "A")
        repo.saveMix(mix)

        repo.setLastUsed(mix.id)
        assertEquals(mix.id, repo.lastUsedMixId.value)

        repo.deleteMix(mix.id)
        assertNull(repo.lastUsedMixId.value)
    }

    @Test
    fun `import always creates a new mix`() = runTest {
        val store = InMemoryUserDataStore()
        val repo = repo(store, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val preset = repo.presets.first { it.name == "Shush" }

        val first = repo.importMix(preset)
        val second = repo.importMix(preset)
        assertNotEquals(first.id, second.id)
        assertNotEquals(preset.id, first.id)
        // The preset keeps its name, so imported copies are numbered.
        assertEquals("Shush (2)", first.name)
        assertEquals("Shush (3)", second.name)
        assertEquals(2, repo.userMixes.value.size)

        // A mix whose name is free keeps its name.
        val custom = repo.importMix(userMix("user.imported", "My rainy night"))
        assertEquals("My rainy night", custom.name)
        assertNotEquals("user.imported", custom.id)
    }

    @Test
    fun `replaceAll and clearAll`() = runTest {
        val store = InMemoryUserDataStore()
        val repo = repo(store, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val mix = userMix("user.42", "Restored")
        repo.replaceAll(UserData(mixes = listOf(mix), favoriteIds = listOf("user.42")))
        assertEquals("Restored", repo.mixById("user.42")!!.name)
        assertEquals(listOf("user.42"), repo.favoriteIds.value)

        repo.clearAll()
        assertTrue(repo.userMixes.value.isEmpty())
        assertTrue(repo.favoriteIds.value.isEmpty())
    }
}
