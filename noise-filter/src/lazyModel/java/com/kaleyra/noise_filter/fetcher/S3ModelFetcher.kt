package fetcher

import android.util.Log
import utils.Sha256FileHashCalculator
import utils.StandardSha256FileHashCalculator
import com.kaleyra.video_utils.dispatcher.DispatcherProvider
import com.kaleyra.video_utils.dispatcher.StandardDispatchers
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Fetches a deep filter model from an S3 bucket, verifies its integrity, and saves it locally.
 * This class implements the [ModelFetcher] interface.
 *
 * The model is downloaded from a predefined URL and its integrity is checked against a SHA-256
 * checksum downloaded from a corresponding checksum file URL.
 *
 * @property httpClient The HTTP client used for downloading the model and checksum files.
 * @property dispatcherProvider Provides the coroutine dispatcher for I/O operations. Defaults to [StandardDispatchers].
 * @property fileHashCalculator The component used to calculate the integrity checksum of the downloaded file. Defaults to [Sha256FileHashCalculator].
 */
class S3ModelFetcher(
    private val httpClient: HttpClient,
    private val dispatcherProvider: DispatcherProvider = StandardDispatchers,
    private val fileHashCalculator: Sha256FileHashCalculator = StandardSha256FileHashCalculator
): ModelFetcher {

    private val modelUrl = "https://static.bandyer.com/corporate/deepfilternet/DeepFilterNet3.tar.gz"
    private val checksumUrl = modelUrl.replace(".tar.gz", ".checksum")

    /**
     * Asynchronously fetches a model from a predefined URL, saves it to the specified file,
     * and verifies its integrity using a SHA-256 checksum file.
     *
     * @param destinationFile The file where the downloaded model data will be saved.
     * @return `true` if the model was successfully fetched, saved, and passed the integrity check, `false` otherwise.
     */
    override suspend fun fetchModel(destinationFile: File): Boolean = withContext(dispatcherProvider.io) {
        try {
            if (!downloadFile(modelUrl, destinationFile)) {
                Log.e(TAG, "Failed to download model file.")
                destinationFile.delete()
                return@withContext false
            }
            Log.i(TAG, "Model file downloaded successfully.")

            val expectedChecksum = downloadChecksum(checksumUrl)
            if (expectedChecksum == null) {
                Log.e(TAG, "Failed to download checksum file.")
                destinationFile.delete()
                return@withContext false
            }
            Log.i(TAG, "Checksum file downloaded successfully. Expected: $expectedChecksum")

            if (!verifyIntegrity(destinationFile, expectedChecksum)) {
                Log.e(TAG, "Model file integrity check failed.")
                destinationFile.delete()
                return@withContext false
            }
            Log.i(TAG, "Model file integrity check successful.")

            return@withContext true
        } catch (e: IOException) {
            Log.e(TAG, "IO Error during model fetch process: ${e.message}", e)
            if (destinationFile.exists()) destinationFile.delete()
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during model fetch process: ${e.message}", e)
            if (destinationFile.exists()) destinationFile.delete()
            return@withContext false
        }
    }

    /**
     * Downloads a file from the specified URL to the destination file.
     *
     * @param url The URL of the file to download.
     * @param destinationFile The file where the downloaded data will be saved.
     * @return `true` if the download was successful, `false` otherwise.
     */
    private suspend fun downloadFile(url: String, destinationFile: File): Boolean {
        return try {
            val response: HttpResponse = httpClient.get(url)

            if (!response.status.isSuccess()) {
                Log.e(TAG, "Failed to download file from $url: HTTP status code ${response.status}")
                return false
            }

            val byteArray: ByteArray = response.body()
            destinationFile.writeBytes(byteArray)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error during file download from $url: ${e.message}", e)
            false
        }
    }

    /**
     * Downloads the checksum content from the specified URL.
     *
     * @param url The URL of the checksum file.
     * @return The checksum string if successful, `null` otherwise.
     */
    private suspend fun downloadChecksum(url: String): String? {
        return try {
            val response: HttpResponse = httpClient.get(url)

            if (!response.status.isSuccess()) {
                Log.e(TAG, "Failed to download checksum from $url: HTTP status code ${response.status}")
                return null
            }

            response.bodyAsText().trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error during checksum download from $url: ${e.message}", e)
            null
        }
    }

    /**
     * Verifies the integrity of a file by comparing its calculated SHA-256 hash
     * with an expected checksum string.
     *
     * @param file The file to verify.
     * @param expectedChecksum The expected SHA-256 checksum string.
     * @return `true` if the checksums match, `false` otherwise (including calculation errors).
     */
    private fun verifyIntegrity(file: File, expectedChecksum: String): Boolean {
        val actualChecksum = fileHashCalculator.calculateHash(file)

        if (actualChecksum.equals(expectedChecksum, ignoreCase = true)) {
            Log.i(TAG, "Checksum match: ${file.name}")
            return true
        } else {
            Log.e(TAG, "Checksum mismatch for file: ${file.name}. Expected: $expectedChecksum, Calculated: $actualChecksum")
            return false
        }
    }

    companion object {
        private const val TAG = "S3ModelFetcher"
    }
}
