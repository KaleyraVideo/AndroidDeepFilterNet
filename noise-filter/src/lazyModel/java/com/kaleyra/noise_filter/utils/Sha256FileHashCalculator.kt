package utils

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

interface Sha256FileHashCalculator : FileHashCalculator

object StandardSha256FileHashCalculator: Sha256FileHashCalculator {

    private const val SHA_256 = "SHA-256"

    override fun calculateHash(file: File): String {
        val digest = MessageDigest.getInstance(SHA_256)
        FileInputStream(file).use { fis ->
            val byteArray = ByteArray(1024)
            var bytesCount: Int
            while ((fis.read(byteArray).also { bytesCount = it }) != -1) {
                digest.update(byteArray, 0, bytesCount)
            }
        }

        val bytes = digest.digest()
        val sb = StringBuilder()
        for (aByte in bytes) {
            sb.append(((aByte.toInt() and 0xff) + 0x100).toString(16).substring(1))
        }
        return sb.toString()
    }
}
