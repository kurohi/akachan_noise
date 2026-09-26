package io.github.kurohi.akachannoise.engine

import io.github.kurohi.akachannoise.engine.dsp.Rng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class RngTest {
    @Test
    fun `same seed produces same sequence`() {
        val a = Rng(42)
        val b = Rng(42)
        repeat(1000) { assertEquals(a.nextLong(), b.nextLong()) }
    }

    @Test
    fun `different seeds produce different sequences`() {
        val a = Rng(1)
        val b = Rng(2)
        var different = false
        repeat(100) { if (a.nextLong() != b.nextLong()) different = true }
        assertTrue(different)
    }

    @Test
    fun `uniform moments are sane`() {
        val rng = Rng(7)
        val n = 1_000_000
        var sum = 0.0
        var sumSq = 0.0
        repeat(n) {
            val v = rng.nextFloat().toDouble()
            assertTrue(v in 0.0..1.0)
            sum += v
            sumSq += v * v
        }
        val mean = sum / n
        val variance = sumSq / n - mean * mean
        assertTrue("mean $mean", abs(mean - 0.5) < 0.01)
        assertTrue("variance $variance", abs(variance - 1.0 / 12.0) < 0.01)
    }

    @Test
    fun `bipolar draws are bounded and centered`() {
        val rng = Rng(9)
        val n = 100_000
        var sum = 0.0
        repeat(n) {
            val v = rng.nextBipolar()
            assertTrue(v in -1f..1f)
            sum += v
        }
        assertTrue(abs(sum / n) < 0.01)
        assertTrue(sqrt(1.0 / 3.0) > 0.57) // sanity: uniform bipolar RMS
    }
}
