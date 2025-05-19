package com.kaleyra.noise_filter

import android.content.Context
import com.kaleyra.noise_filter.mock.MockLog
import com.kaleyra.video_utils.dispatcher.DispatcherProvider
import fetcher.S3ModelFetcher
import io.ktor.client.HttpClient
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

@OptIn(ExperimentalPathApi::class)
class DefaultDeepFilterModelLoaderTest {

    private lateinit var testScheduler: TestCoroutineScheduler
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var mockContext: Context
    private lateinit var mockDispatcherProvider: DispatcherProvider
    private lateinit var mockModelFile: File

    private lateinit var loader: DefaultDeepFilterModelLoader

    private lateinit var tmpFilesDirPath: Path
    private lateinit var tmpFilesDirFile: File

    private val dummyModelBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
    private val modelFileName = "deep-filter-mobile-model"

    @Before
    fun setup() {
        MockLog.init()

        testScheduler = TestCoroutineScheduler()
        testDispatcher = StandardTestDispatcher(testScheduler)

        mockContext = mockk()
        tmpFilesDirPath = Files.createTempDirectory("test_filesDir")
        tmpFilesDirFile = tmpFilesDirPath.toFile()
        every { mockContext.filesDir } returns tmpFilesDirFile

        mockkConstructor(HttpClient::class)
        mockkConstructor(S3ModelFetcher::class)

        every { anyConstructed<HttpClient>().close() } just Runs
        mockDispatcherProvider = object : DispatcherProvider {
            override val default: CoroutineDispatcher = testDispatcher
            override val io: CoroutineDispatcher = testDispatcher
            override val main: CoroutineDispatcher = testDispatcher
            override val mainImmediate: CoroutineDispatcher = testDispatcher
        }

        loader = DefaultDeepFilterModelLoader(mockDispatcherProvider)
    }

    @After
    fun cleanup() {
        if (::tmpFilesDirPath.isInitialized) {
            tmpFilesDirPath.deleteRecursively()
        }
        MockLog.clear()
        testDispatcher.cancel()
        unmockkAll()
    }

    @Test
    fun load_cachedFileExistsAndValid_returnsBytes() = runTest(testDispatcher) {
        mockModelFile = mockModelFile(dummyModelBytes)

        val result = loader.load(mockContext)

        assertTrue(result.contentEquals(dummyModelBytes))

        coVerify(exactly = 0) { anyConstructed<S3ModelFetcher>().fetchModel(any()) }
        verify(exactly = 1) { anyConstructed<HttpClient>().close() }
    }

    @Test
    fun load_cachedFileExistsButReadFails_returnsNull() = runTest(testDispatcher) {
        val readException = IOException("Simulated read error")
        mockkStatic(File::readBytes)

        mockModelFile = mockModelFile(dummyModelBytes)
        every { mockModelFile.readBytes() } throws readException

        val result = loader.load(mockContext)

        assertNull(result)
        assertFalse(mockModelFile.exists())

        coVerify(exactly = 0) { anyConstructed<S3ModelFetcher>().fetchModel(any()) }
        verify(exactly = 1) { anyConstructed<HttpClient>().close() }
    }

    @Test
    fun load_cachedFileExistsButIsEmpty_returnsNullAndAttemptsDownload() = runTest(testDispatcher) {
        mockModelFile = mockModelFile(null)
        mockkStatic(File::readBytes)

        coEvery { anyConstructed<S3ModelFetcher>().fetchModel(mockModelFile) } returns false

        val result = loader.load(mockContext)

        assertNull(result)
        assertFalse(mockModelFile.exists()) 

        coVerify(exactly = 1) { anyConstructed<S3ModelFetcher>().fetchModel(mockModelFile) }
        verify(exactly = 0) { mockModelFile.readBytes() }
        verify(exactly = 1) { anyConstructed<HttpClient>().close() }
    }

    @Test
    fun load_notCached_downloadSuccess_returnsBytes() = runTest(testDispatcher) {
        mockModelFile = mockModelFile(null)
        coEvery { anyConstructed<S3ModelFetcher>().fetchModel(mockModelFile) } answers {
            FileOutputStream(mockModelFile).use { it.write(dummyModelBytes) }
            true
        }

        val result = loader.load(mockContext)

        assertTrue(result.contentEquals(dummyModelBytes))

        coVerify(exactly = 1) { anyConstructed<S3ModelFetcher>().fetchModel(mockModelFile) }
        verify(exactly = 1) { anyConstructed<HttpClient>().close() }
    }

    @Test
    fun load_notCached_downloadSuccessButReadFails_returnsNull() = runTest(testDispatcher) {
        mockModelFile = mockModelFile(null)
        mockkStatic(File::readBytes)

        coEvery { anyConstructed<S3ModelFetcher>().fetchModel(mockModelFile) } returns true
        val readException = IOException("Simulated read error after download")
        every { mockModelFile.readBytes() } throws readException

        val result = loader.load(mockContext)

        assertNull(result)
        assertFalse(mockModelFile.exists()) 

        coVerify(exactly = 1) { anyConstructed<S3ModelFetcher>().fetchModel(mockModelFile) }
        verify(exactly = 1) { anyConstructed<HttpClient>().close() }
    }

    @Test
    fun load_notCached_downloadFails_returnsNull() = runTest(testDispatcher) {
        mockModelFile = mockModelFile(null)
        mockkStatic(File::readBytes)

        coEvery { anyConstructed<S3ModelFetcher>().fetchModel(mockModelFile) } returns false

        val result = loader.load(mockContext)

        assertNull(result)
        assertFalse(mockModelFile.exists()) 

        coVerify(exactly = 1) { anyConstructed<S3ModelFetcher>().fetchModel(mockModelFile) }
        verify(exactly = 0) { mockModelFile.readBytes() }
        verify(exactly = 1) { anyConstructed<HttpClient>().close() }
    }

    @Test
    fun load_unexpectedExceptionInTryBlock_returnsNull() = runTest(testDispatcher) {
        mockModelFile = mockModelFile(null)
        mockkStatic(File::readBytes)
        every { mockContext.filesDir } returns spyk(File("fil:")) // Intentionally provoke an IOException when calling createNewFile()

        coEvery { anyConstructed<S3ModelFetcher>().fetchModel(mockModelFile) } returns false

        val result = loader.load(mockContext)

        assertNull(result)
        assertFalse(mockModelFile.exists())

        coVerify(exactly = 0) { anyConstructed<S3ModelFetcher>().fetchModel(any()) }
        verify(exactly = 0) { mockModelFile.readBytes() }
        verify(exactly = 1) { anyConstructed<HttpClient>().close() }
    }

    private fun mockModelFile(bytes: ByteArray?): File =
        File(tmpFilesDirFile, modelFileName).apply {
            if (bytes == null) return@apply
            FileOutputStream(this).use { it.write(bytes) }
        }

}