package com.kaleyra.noise_filter.utils

import org.junit.After
import org.junit.Assert
import org.junit.Test
import utils.StandardSha256FileHashCalculator
import java.io.File
import java.io.FileNotFoundException

class StandardSha256FileHashCalculatorTest {

    private val testFiles = mutableListOf<File>()

    @After
    fun cleanup() {
        testFiles.forEach { it.delete() }
        testFiles.clear()
    }

    @Test
    fun calculateHash_shouldReturnCorrectHash_forHelloWorld() {
        val content = "hello world"
        val expectedHash = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9"

        val testFile = createTempFileWithContent(content)
        val actualHash = StandardSha256FileHashCalculator.calculateHash(testFile)

        Assert.assertEquals(expectedHash, actualHash)
    }

    @Test
    fun calculateHash_shouldReturnCorrectHash_forEmptyFile() {
        val expectedHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

        val testFile = createEmptyTempFile()
        val actualHash = StandardSha256FileHashCalculator.calculateHash(testFile)

        Assert.assertEquals(expectedHash, actualHash)
    }

    @Test
    fun calculateHash_shouldReturnCorrectHash_forContentWithSpecialChars() {
        val content = "test with special characters: !@#$%^&*()_+ {}[]|\"':;<>,.?/~` e ÀÈÌÒÙ"
        val expectedHash = "524fbc8e31d996d41a61260daabb6655c27607cae6599329ab0d9bf54e6184a7"

        val testFile = createTempFileWithContent(content)
        val actualHash = StandardSha256FileHashCalculator.calculateHash(testFile)

        Assert.assertEquals(expectedHash, actualHash)
    }

    @Test(expected = FileNotFoundException::class)
    fun calculateHash_shouldThrowException_forNonExistentFile() {
        val nonExistentFile = File("non_existent_file_${System.currentTimeMillis()}.tmp")
        StandardSha256FileHashCalculator.calculateHash(nonExistentFile)
    }

    private fun createTempFileWithContent(content: String): File {
        val tempFile = File.createTempFile("test", ".tmp")
        tempFile.writeText(content)
        testFiles.add(tempFile)
        return tempFile
    }

    private fun createEmptyTempFile(): File {
        val tempFile = File.createTempFile("empty_test", ".tmp")
        testFiles.add(tempFile)
        return tempFile
    }
}