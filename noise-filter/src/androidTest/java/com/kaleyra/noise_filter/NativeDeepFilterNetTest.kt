package com.kaleyra.noise_filter

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.sin
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class NativeDeepFilterNetTest {

    private lateinit var context: Context
    private lateinit var deepFilterNet: DeepFilterNet

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        deepFilterNet = com.rikorose.deepfilternet.NativeDeepFilterNet(context)
    }

    @After
    fun tearDown() {
        deepFilterNet.release()
    }

    @Test
    fun testModelLoading() {
        val latch = CountDownLatch(1)
        deepFilterNet.onModelLoaded {
            assertNotEquals(-1L, it.frameLength)
            latch.countDown()
        }
        assertEquals(
            true,
            latch.await(5, TimeUnit.SECONDS)
        )
    }

    @Test
    fun testSetAttenuationLimit_setsValueSuccessfully() {
        val latch = CountDownLatch(1)
        deepFilterNet.onModelLoaded {
            assertEquals(true, it.setAttenuationLimit(60f))
            latch.countDown()
        }
        assertEquals(
            true,
            latch.await(5, TimeUnit.SECONDS)
        )
    }

    @Test
    fun testSetAttenuationLimit_failsOnModelNotLoaded() {
        assertEquals(false, deepFilterNet.setAttenuationLimit(60f))
    }

    @Test
    fun testSetAttenuationLimit_changesSNR() {
        val latch = CountDownLatch(1)
        var snrWithDefaultLimit = 0f
        var snrWithNewLimit = 0f

        deepFilterNet.onModelLoaded {
            val frameLength = it.frameLength.toInt()
            val inputFrame = createNoisyAudioFrame(frameLength)

            snrWithDefaultLimit = it.processFrame(inputFrame.duplicate())

            it.setAttenuationLimit(80f)
            snrWithNewLimit = it.processFrame(inputFrame.duplicate())

            latch.countDown()
        }

        assertEquals(true, latch.await(5, TimeUnit.SECONDS))

        assertNotEquals(snrWithDefaultLimit, snrWithNewLimit, 0.01f)
    }

    @Test
    fun testSetPostFilterBeta_setsValueSuccessfully() {
        val latch = CountDownLatch(1)
        deepFilterNet.onModelLoaded {
            assertEquals(true, it.setPostFilterBeta(0.5f))
            latch.countDown()
        }
        assertEquals(
            true,
            latch.await(5, TimeUnit.SECONDS)
        )
    }

    @Test
    fun testSetPostFilterBeta_failsOnModelNotLoaded() {
        assertEquals(false, deepFilterNet.setPostFilterBeta(0.5f))
    }

    @Test
    fun testProcessFrame() {
        val latch = CountDownLatch(1)
        deepFilterNet.onModelLoaded {
            val frameLength = it.frameLength.toInt()
            val inputFrame = createNoisyAudioFrame(frameLength)
            val snr = it.processFrame(inputFrame)
            assertNotEquals(0f, snr)
            latch.countDown()
        }
        assertEquals(
            true,
            latch.await(5, TimeUnit.SECONDS)
        )
    }

    @Test
    fun testRelease() {
        val latch = CountDownLatch(1)
        var frameLength: Long

        deepFilterNet.onModelLoaded {
            frameLength = it.frameLength
            assertEquals(true, frameLength > 0)

            it.release()

            val inputFrame = createNoisyAudioFrame(frameLength.toInt())
            assertEquals(-1L, it.frameLength)
            assertEquals(-1f, it.processFrame(inputFrame))

            latch.countDown()
        }
        assertEquals(
            true,
            latch.await(5, TimeUnit.SECONDS)
        )
    }

    @Test
    fun testModelNotLoaded() {
        // Create a new instance without waiting for load.
        val notLoaded = com.rikorose.deepfilternet.NativeDeepFilterNet(context)
        assertEquals(-1L, notLoaded.frameLength)
        assertEquals(-1f, notLoaded.processFrame(ByteBuffer.allocateDirect(10)))
    }

    private fun createNoisyAudioFrame(
        size: Int,
        signalFrequency: Double = 440.0,
        signalAmplitude: Double = 0.5,
        noiseAmplitude: Double = 0.3
    ): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(size * 2) // 16-bit PCM
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        val random = Random(System.currentTimeMillis())

        // Calculate the phase step for the desired frequency
        // Assuming a sample rate of 16000 Hz
        val sampleRate = 16000.0
        val phaseStep = 2.0 * Math.PI * signalFrequency / sampleRate

        // Generate sinusoidal signal + noise
        for (i in 0 until size) {
            // Sinusoidal signal
            val signal = signalAmplitude * sin(i * phaseStep)

            // White noise (random values between -1.0 and 1.0)
            val noise = noiseAmplitude * (random.nextDouble() * 2.0 - 1.0)

            // Combine signal and noise, keep within the range [-1.0, 1.0]
            val sample = (signal + noise).coerceIn(-1.0, 1.0)

            // Convert to 16-bit sample and write to buffer
            val sampleShort = (sample * Short.MAX_VALUE).toInt().toShort()
            buffer.putShort(sampleShort)
        }

        buffer.rewind()
        return buffer
    }
}