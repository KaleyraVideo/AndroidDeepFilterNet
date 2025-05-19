import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing
import com.google.common.io.Files
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.DeleteSpec
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.FileType
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.util.GradleVersion
import org.gradle.work.ChangeType
import org.gradle.work.FileChange
import org.gradle.work.InputChanges
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.function.Consumer
import javax.inject.Inject

abstract class Checksum @Inject constructor(private val objectFactory: ObjectFactory) :
    DefaultTask() {

    @get:OutputDirectory
    val outputDirectory: DirectoryProperty =
        objectFactory.directoryProperty().convention(project.layout.buildDirectory.dir("checksums"))

    @get:Input
    val checksumAlgorithms: ListProperty<Algorithm>

    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    @get:IgnoreEmptyDirectories
    @get:SkipWhenEmpty
    @get:InputFiles
    val inputFiles: ConfigurableFileCollection

    @Suppress("DEPRECATION")
    enum class Algorithm(internal val hashFunction: HashFunction) {
        MD5(Hashing.md5()),
        SHA1(Hashing.sha1()),
        SHA256(Hashing.sha256()),
        SHA384(Hashing.sha384()),
        SHA512(Hashing.sha512())
    }

    init {
        this.checksumAlgorithms = objectFactory.listProperty(Algorithm::class.java).convention(listOf(Algorithm.SHA256))
        this.inputFiles = objectFactory.fileCollection()
    }

    @TaskAction
    @Throws(IOException::class)
    fun generateChecksumFiles() {
        logger.lifecycle("outputDir:${outputDirectory.asFile}")
        val outputDirAsFile = outputDirectory.asFile.get()
        if (!outputDirAsFile.exists()) {
            if (!outputDirAsFile.mkdirs()) {
                throw IOException("Could not create directory:$outputDirAsFile")
            }
        }
        project.delete(allPossibleChecksumFiles(outputDirAsFile))

        val inputFiles = inputFiles.files
        checksumAlgorithms.get().forEach { algo ->
            inputFiles.forEach { file ->
                val sumFile = outputFileFor(outputDirAsFile, file, algo)
                try {
                    val hashCode = Files.asByteSource(file).hash(algo.hashFunction)
                    val content = hashCode.toString()
                    Files.write(content.toByteArray(StandardCharsets.UTF_8), sumFile)
                } catch (e: IOException) {
                    throw IllegalStateException("Error creating checksum", e)
                }
            }
        }
    }

    private fun outputFileFor(outputDirAsFile: File, inputFile: File, algo: Algorithm): File {
        return File(
            outputDirAsFile,
            inputFile.name + "." + algo.toString().lowercase(Locale.getDefault())
        )
    }

    private fun allPossibleChecksumFiles(outputDirAsFile: File): FileCollection? {
        var possibleFiles: FileCollection? = null
        for (algo in Algorithm.entries) {
            possibleFiles = if (possibleFiles == null) {
                filesFor(outputDirAsFile, algo)
            } else {
                possibleFiles.plus(filesFor(outputDirAsFile, algo))
            }
        }
        return possibleFiles
    }

    private fun filesFor(outputDirAsFile: File, algo: Algorithm): FileCollection {
        return project.fileTree(
            outputDirAsFile
        ) { files: ConfigurableFileTree ->
            files.include(
                "**/*." + algo.toString().lowercase(Locale.getDefault())
            )
        }
    }
}