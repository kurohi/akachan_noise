package io.github.kurohi.akachannoise.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class EngineTest {
    @Test
    fun engineHasVersion() {
        assertEquals("0.1.0", Engine.VERSION)
    }
}
