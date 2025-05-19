package com.kaleyra.noise_filter

import android.content.Context
import android.content.res.Resources
import com.kaleyra.noise_filter.mock.MockLog
import com.kaleyra.noise_filter.model_loader.DeepFilterModelLoader
import com.kaleyra.video_utils.dispatcher.DispatcherProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

@ExperimentalCoroutinesApi
class DefaultDeepFilterModelLoaderTest {

    private lateinit var testScheduler: TestCoroutineScheduler
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var mockContext: Context
    private lateinit var mockResources: Resources
    private lateinit var mockDispatcherProvider: DispatcherProvider
    private lateinit var loader: DeepFilterModelLoader

    @Before
    fun setUp() {
        MockLog.init()

        testScheduler = TestCoroutineScheduler()
        testDispatcher = StandardTestDispatcher(testScheduler)

        mockContext = mockk(relaxed = true)
        mockResources = mockk(relaxed = true)
        every { mockContext.resources } returns mockResources

        mockDispatcherProvider = object : DispatcherProvider {
            override val default: CoroutineDispatcher = testDispatcher
            override val io: CoroutineDispatcher = testDispatcher
            override val main: CoroutineDispatcher = testDispatcher
            override val mainImmediate: CoroutineDispatcher = testDispatcher
        }

        loader = DefaultDeepFilterModelLoader(mockDispatcherProvider)
    }

    @After
    fun tearDown() {
        MockLog.clear()
        testDispatcher.cancel()
        unmockkAll()
    }

    @Test
    fun load_resourceFoundAndReadSuccessfully_returnsByteArray() = runTest(testScheduler) {
        val dummyBytes = byteArrayOf(1, 2, 3, 4, 5)
        val inputStream = ByteArrayInputStream(dummyBytes)

        every { mockResources.openRawResource(R.raw.deep_filter_mobile_model) } returns inputStream

        val result = loader.load(mockContext)

        assertTrue(result.contentEquals(dummyBytes))
    }

    @Test
    fun load_resourceNotFoundExceptionOccurs_returnsNull() = runTest(testScheduler) {
        every { mockResources.openRawResource(R.raw.deep_filter_mobile_model) } throws Resources.NotFoundException("Resource not found")

        val result = loader.load(mockContext)

        assertNull(result)
    }

    @Test
    fun load_ioExceptionDuringReading_returnsNull() = runTest(testScheduler) {
        mockkStatic(InputStream::class)
        val throwingInputStream = mockk<ByteArrayInputStream>()
        every { throwingInputStream.read(any<ByteArray>()) } throws IOException("Simulated read error during buffer read")
        every { throwingInputStream.read(any<ByteArray>(), any<Int>(), any<Int>()) } throws IOException("Simulated read error during offset/length read")
        every { throwingInputStream.read() } throws IOException("Simulated read error during single byte read")
        every { throwingInputStream.close() } returns Unit

        every { mockResources.openRawResource(R.raw.deep_filter_mobile_model) } returns throwingInputStream

        val result = loader.load(mockContext)

        assertNull(result)
    }

    @Test
    fun load_genericExceptionOccurs_returnsNull() = runTest(testScheduler) {
        every { mockResources.openRawResource(R.raw.deep_filter_mobile_model) } throws RuntimeException("Some unexpected error")

        val result = loader.load(mockContext)

        assertNull(result)
    }
}