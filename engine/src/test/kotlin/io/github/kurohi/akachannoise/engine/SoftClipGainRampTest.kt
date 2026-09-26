package io.github.kurohi.akachannoise.engine

import io.github.kurohi.akachannoise.engine.dsp.GainRamp
import io.github.kurohi.akachannoise.engine.dsp.SoftClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftClipTest {
    @Test
    fun `unity below the knee`() {
        assertEquals(0.5f, SoftClip.process(0.5f), 0f)
        assertEquals(-0.3f, SoftClip.process(-0.3f), 0f)
        assertEquals(0.85f, SoftClip.process(0.85f), 1e-6f)
    }

    @Test
    fun `never exceeds one and stays continuous`() {
        var prev = SoftClip.process(0.85f)
        var x = 0.86f
        while (x <= 3f) {
            val y = SoftClip.process(x)
            assertTrue(y in 0.85f..1f)
            assertTrue("jump at $x", y - prev < 0.01f)
            prev = y
            x += 0.01f
        }
        assertTrue(SoftClip.process(1000f) <= 1f)
        assertTrue(SoftClip.process(-1000f) >= -1f)
        assertEquals(SoftClip.process(1000f), -SoftClip.process(-1000f), 0f)
    }
}

class GainRampTest {
    @Test
    fun `reaches target exactly`() {
        val ramp = GainRamp()
        ramp.begin(0.2f, 1f, 100)
        repeat(100) { ramp.next() }
        assertEquals(1f, ramp.next(), 0f)
        assertTrue(!ramp.isActive)
    }

    @Test
    fun `reaches zero exactly`() {
        val ramp = GainRamp()
        ramp.begin(0.5f, 0f, 50)
        repeat(50) { ramp.next() }
        assertEquals(0f, ramp.next(), 0f)
    }

    @Test
    fun `monotonic during ramp`() {
        val ramp = GainRamp()
        ramp.begin(0f, 1f, 1000)
        var last = -1f
        repeat(1000) {
            val v = ramp.next()
            assertTrue(v >= last)
            last = v
        }
    }

    @Test
    fun `zero duration jumps immediately`() {
        val ramp = GainRamp()
        ramp.begin(0.1f, 0.9f, 0)
        assertEquals(0.9f, ramp.next(), 0f)
    }
}
