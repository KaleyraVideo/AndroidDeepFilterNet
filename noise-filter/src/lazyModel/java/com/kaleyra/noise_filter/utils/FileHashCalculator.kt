package utils

import java.io.File

interface FileHashCalculator {
    fun calculateHash(file: File): String
}
