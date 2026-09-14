package dev.snapseek.core.download

import java.security.MessageDigest

fun ByteArray.sha256Hex(): String = digestHex("SHA-256")

fun ByteArray.md5Hex(): String = digestHex("MD5")

private fun ByteArray.digestHex(algorithm: String): String =
    MessageDigest.getInstance(algorithm).digest(this).joinToString("") { "%02x".format(it) }
