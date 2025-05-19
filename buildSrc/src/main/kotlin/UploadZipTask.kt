import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.IOException
import java.util.Base64

abstract class UploadZipTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val zipFile: RegularFileProperty

    @get:Input
    abstract val authUsername: Property<String>

    @get:Input
    abstract val authToken: Property<String>

    @get:Input
    abstract val uploadUrl: Property<String>

    @TaskAction
    fun uploadFile() {
        val file: File = zipFile.get().asFile
        val username: String = authUsername.get()
        val token: String = authToken.get()
        val url: String = uploadUrl.get()

        if (!file.exists() || !file.isFile) {
            throw IllegalArgumentException("File not found or is not a file: ${file.absolutePath}")
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val base64Token = generateBase64(username, token)

        val fileRequestBody = file.asRequestBody("application/zip".toMediaTypeOrNull())
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "bundle",
                file.name,
                fileRequestBody
            )
            .build()

        val request = Request.Builder()
            .url(url)
            .post(multipartBody)
            .header("Authorization", "Bearer $base64Token")
            .build()

        logger.lifecycle("Uploading file: ${file.absolutePath} to $url")

        var response: Response? = null
        try {
            response = okHttpClient.newCall(request).execute()

            logger.lifecycle("Upload response status: ${response.code}")

            val responseBodyString = try {
                response.body?.string()
            } catch (e: IOException) {
                "Could not read response body: ${e.message}"
            }

            logger.lifecycle("Upload response body: $responseBodyString")

            if (response.isSuccessful) {
                logger.lifecycle("File uploaded successfully.")
            } else {
                logger.error("File upload failed with status: ${response.code}. Body: $responseBodyString")
                // Throw an exception to fail the build on upload failure
                throw RuntimeException("Upload failed with status: ${response.code}. Body: $responseBodyString")
            }
        } catch (e: Exception) {
            logger.error("Error during file upload: ${e.message}", e)
            throw e // Re-throw the exception to fail the build
        } finally {
            response?.body?.close()
            okHttpClient.dispatcher.executorService.shutdown()
        }
    }

    private fun generateBase64(username: String, token: String): String {
        val combinedString = "$username:$token"
        val bytes = combinedString.toByteArray(Charsets.UTF_8)
        return Base64.getEncoder().encodeToString(bytes)
    }
}