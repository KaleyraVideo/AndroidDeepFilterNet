package com.kaleyra.noise_filter.fetcher

import com.kaleyra.noise_filter.mock.MockLog
import utils.Sha256FileHashCalculator
import com.kaleyra.video_utils.dispatcher.DispatcherProvider
import fetcher.S3ModelFetcher
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

class S3ModelFetcherTest {

    private lateinit var testScheduler: TestCoroutineScheduler
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var mockHttpClient: HttpClient
    private lateinit var mockDispatcherProvider: DispatcherProvider
    private lateinit var mockFileHashCalculator: Sha256FileHashCalculator
    private lateinit var mockDestinationFile: File

    private lateinit var s3ModelFetcher: S3ModelFetcher

    private val modelUrl = "https://static.bandyer.com/corporate/deepfilternet/DeepFilterNet3.tar.gz"
    private val checksumUrl = modelUrl.replace(".tar.gz", ".checksum")
    private val dummyModelContent = "Dummy model content".toByteArray()
    private val dummyChecksumContentRaw = "dummyhash1234567890abcdef1234567890abcdef1234567890abcdef"
    private val dummyExpectedChecksum = "dummyhash1234567890abcdef1234567890abcdef1234567890abcdef"

    @Before
    fun setup() {
        MockLog.init()

        testScheduler = TestCoroutineScheduler()
        testDispatcher = StandardTestDispatcher(testScheduler)
        mockDispatcherProvider = object : DispatcherProvider {
            override val default: CoroutineDispatcher = testDispatcher
            override val io: CoroutineDispatcher = testDispatcher
            override val main: CoroutineDispatcher = testDispatcher
            override val mainImmediate: CoroutineDispatcher = testDispatcher
        }

        mockHttpClient = HttpClient(MockEngine { _ ->
            respond("", HttpStatusCode.NoContent)
        })

        mockFileHashCalculator = spyk(object : Sha256FileHashCalculator {
            override fun calculateHash(file: File): String = dummyExpectedChecksum
        })

        mockkStatic(File::writeBytes)
        mockDestinationFile = mockk<File>(relaxed = true)
        every { mockDestinationFile.exists() } returns true
        every { mockDestinationFile.delete() } returns true
        every { mockDestinationFile.writeBytes(any()) } returns Unit

        s3ModelFetcher = S3ModelFetcher(
            mockHttpClient,
            mockDispatcherProvider,
            mockFileHashCalculator
        )
    }

    @After
    fun cleanup() {
        mockHttpClient.close()
        testDispatcher.cancel()
        MockLog.clear()
        unmockkAll()
    }

    @Test
    fun fetchModel_success() = runTest(testDispatcher) {
        val mockEngine = mockHttpClient.engine as MockEngine
        with(mockEngine.config) {
            requestHandlers.clear()
            addHandler { request ->
                when (request.url.toString()) {
                    modelUrl -> respond(dummyModelContent, HttpStatusCode.OK)
                    checksumUrl -> respond(dummyChecksumContentRaw, HttpStatusCode.OK)
                    else -> error("Unhandled URL ${request.url}")
                }
            }
        }

        val result = s3ModelFetcher.fetchModel(mockDestinationFile)

        Assert.assertEquals(true, result)

        verify(exactly = 1) { mockDestinationFile.writeBytes(dummyModelContent) }
        verify(exactly = 1) { mockFileHashCalculator.calculateHash(mockDestinationFile) }
        verify(exactly = 0) { mockDestinationFile.delete() }
    }

    @Test
    fun fetchModel_downloadModelFailsHttp() = runTest(testScheduler) {
        val mockEngine = mockHttpClient.engine as MockEngine
        with(mockEngine.config) {
            requestHandlers.clear()
            addHandler { request ->
                when (request.url.toString()) {
                    modelUrl -> respond("Error", HttpStatusCode.NotFound)
                    checksumUrl -> error("Should not be called")
                    else -> error("Unhandled URL ${request.url}")
                }
            }
        }

        val result = s3ModelFetcher.fetchModel(mockDestinationFile)

        Assert.assertEquals(false, result)

        verify(exactly = 0) { mockDestinationFile.writeBytes(any()) }
        verify(exactly = 0) { mockFileHashCalculator.calculateHash(any()) }
        verify(exactly = 1) { mockDestinationFile.delete() }
    }

    @Test
    fun fetchModel_downloadModelFailsException() = runTest(testScheduler) {
        val mockEngine = mockHttpClient.engine as MockEngine
        with(mockEngine.config) {
            requestHandlers.clear()
            addHandler { request ->
                when (request.url.toString()) {
                    modelUrl -> throw IOException("Simulated network error")
                    checksumUrl -> error("Should not be called")
                    else -> error("Unhandled URL ${request.url}")
                }
            }
        }

        val result = s3ModelFetcher.fetchModel(mockDestinationFile)

        Assert.assertEquals(false, result)

        verify(exactly = 0) { mockDestinationFile.writeBytes(any()) }
        verify(exactly = 1) { mockDestinationFile.delete() }
    }

    @Test
    fun fetchModel_downloadChecksumFailsHttp() = runTest(testScheduler) {
        val mockEngine = mockHttpClient.engine as MockEngine
        with(mockEngine.config) {
            requestHandlers.clear()
            addHandler { request ->
                when (request.url.toString()) {
                    modelUrl -> respond(dummyModelContent, HttpStatusCode.OK)
                    checksumUrl -> respond("Error", HttpStatusCode.NotFound)
                    else -> error("Unhandled URL ${request.url}")
                }
            }
        }

        val result = s3ModelFetcher.fetchModel(mockDestinationFile)

        Assert.assertEquals(false, result)

        verify(exactly = 1) { mockDestinationFile.writeBytes(dummyModelContent) }
        verify(exactly = 0) { mockFileHashCalculator.calculateHash(any()) }
        verify(exactly = 1) { mockDestinationFile.delete() }
    }

    @Test
    fun fetchModel_downloadChecksumFailsException() = runTest(testScheduler) {
        val mockEngine = mockHttpClient.engine as MockEngine
        with(mockEngine.config) {
            requestHandlers.clear()
            addHandler { request ->
                when (request.url.toString()) {
                    modelUrl -> respond(
                        dummyModelContent,
                        HttpStatusCode.OK
                    )
                    checksumUrl -> throw IOException("Simulated checksum network error")
                    else -> error("Unhandled URL ${request.url}")
                }
            }
        }

        val result = s3ModelFetcher.fetchModel(mockDestinationFile)

        Assert.assertEquals(false, result)

        verify(exactly = 1) { mockDestinationFile.writeBytes(dummyModelContent) }
        verify(exactly = 0) { mockFileHashCalculator.calculateHash(any()) }
        verify(exactly = 1) { mockDestinationFile.delete() }
    }

    @Test
    fun fetchModel_checksumMismatch() = runTest(testScheduler) {
        val mockEngine = mockHttpClient.engine as MockEngine
        with(mockEngine.config) {
            requestHandlers.clear()
            addHandler { request ->
                when (request.url.toString()) {
                    modelUrl -> respond(dummyModelContent, HttpStatusCode.OK)
                    checksumUrl -> respond(dummyChecksumContentRaw, HttpStatusCode.OK)
                    else -> error("Unhandled URL ${request.url}")
                }
            }
        }
        val calculatedHash = "differenthash1234567890abcdef1234567890abcdef1234567890abcdef"
        every { mockFileHashCalculator.calculateHash(mockDestinationFile) } returns calculatedHash

        val result = s3ModelFetcher.fetchModel(mockDestinationFile)

        Assert.assertEquals(false, result)

        verify(exactly = 1) { mockDestinationFile.writeBytes(dummyModelContent) }
        verify(exactly = 1) { mockFileHashCalculator.calculateHash(mockDestinationFile) }
        verify(exactly = 1) { mockDestinationFile.delete() }
    }

    @Test
    fun fetchModel_hashCalculationFails() = runTest(testScheduler) {
        val mockEngine = mockHttpClient.engine as MockEngine
        with(mockEngine.config) {
            requestHandlers.clear()
            addHandler { request ->
                when (request.url.toString()) {
                    modelUrl -> respond(dummyModelContent, HttpStatusCode.OK)
                    checksumUrl -> respond(dummyChecksumContentRaw, HttpStatusCode.OK)
                    else -> error("Unhandled URL ${request.url}")
                }
            }
        }
        every { mockFileHashCalculator.calculateHash(mockDestinationFile) } throws RuntimeException("Simulated hash calculation error")

        val result = s3ModelFetcher.fetchModel(mockDestinationFile)

        Assert.assertEquals(false, result)

        verify(exactly = 1) { mockDestinationFile.writeBytes(dummyModelContent) }
        verify(exactly = 1) { mockFileHashCalculator.calculateHash(mockDestinationFile) }
        verify(exactly = 1) { mockDestinationFile.delete() }
    }

    @Test
    fun fetchModel_topLevelCatchHandlesExceptionAfterChecksumDownload() = runTest(testScheduler) {
        val mockEngine = mockHttpClient.engine as MockEngine
        with(mockEngine.config) {
            requestHandlers.clear()
            addHandler { request ->
                when (request.url.toString()) {
                    modelUrl -> respond(dummyModelContent, HttpStatusCode.OK)
                    checksumUrl -> respond(dummyChecksumContentRaw, HttpStatusCode.OK)
                    else -> error("Unhandled URL ${request.url}")
                }
            }
        }

        // Simulate an exception occurring *after* downloadChecksum but before verifyIntegrity
        // We can do this by making the verifyIntegrity method call trigger an exception
        // or by making the calculateHash call within verifyIntegrity throw (already tested above).
        // Let's simulate an unexpected exception during the verifyIntegrity call itself (e.g., a null pointer if mocks were not set up correctly, but we can force it)
        every { mockFileHashCalculator.calculateHash(mockDestinationFile) } throws RuntimeException(
            "Simulated unexpected error in verification step"
        )

        val result = s3ModelFetcher.fetchModel(mockDestinationFile)

        Assert.assertEquals(false, result)

        verify(exactly = 1) { mockDestinationFile.writeBytes(dummyModelContent) }
        verify(exactly = 1) { mockFileHashCalculator.calculateHash(mockDestinationFile) }
        verify(exactly = 1) { mockDestinationFile.delete() }
    }
}