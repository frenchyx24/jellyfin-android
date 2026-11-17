package org.jellyfin.mobile.nflix.data

/** Kinds of distributor logo media, mirrored from the Nflix Auth plugin. */
object LogoMedia {
    const val IMAGE = "image"
    const val ANIMATED = "animated"
    const val VIDEO = "video"
    const val LOTTIE = "lottie"

    /** Kind and MIME type of [bytes] by signature, or null for a format the server refuses. */
    fun sniff(bytes: ByteArray): Pair<String, String>? {
        fun ascii(offset: Int, text: String) = bytes.size >= offset + text.length && text.indices.all { bytes[offset + it] == text[it].code.toByte() }
        fun u(i: Int) = bytes[i].toInt() and 0xFF
        return when {
            bytes.size >= 8 && u(0) == 0x89 && ascii(1, "PNG") -> IMAGE to "image/png"
            bytes.size >= 3 && u(0) == 0xFF && u(1) == 0xD8 && u(2) == 0xFF -> IMAGE to "image/jpeg"
            ascii(0, "GIF87a") || ascii(0, "GIF89a") -> ANIMATED to "image/gif"
            ascii(0, "RIFF") && ascii(8, "WEBP") -> {
                val animated = (ascii(12, "VP8X") && bytes.size > 20 && (u(20) and 0x02) != 0) ||
                    (12 until minOf(bytes.size - 4, 4096)).any { ascii(it, "ANIM") }
                (if (animated) ANIMATED else IMAGE) to "image/webp"
            }
            ascii(4, "ftyp") -> VIDEO to "video/mp4"
            bytes.size >= 4 && u(0) == 0x1A && u(1) == 0x45 && u(2) == 0xDF && u(3) == 0xA3 -> VIDEO to "video/webm"
            looksLikeLottie(bytes) -> LOTTIE to "application/json"
            else -> null
        }
    }

    private fun looksLikeLottie(bytes: ByteArray): Boolean {
        val text = runCatching { bytes.decodeToString(0, minOf(bytes.size, 64 * 1024)) }.getOrNull()?.trimStart('﻿', ' ', '\n', '\r', '\t') ?: return false
        return text.startsWith("{") && listOf("\"v\"", "\"fr\"", "\"layers\"").all { it in text }
    }

    /** Upload ceiling, the same as the server's. */
    fun maxBytes(kind: String): Int = when (kind) {
        VIDEO -> 8 * 1024 * 1024
        ANIMATED -> 6 * 1024 * 1024
        else -> 2 * 1024 * 1024
    }

    fun label(kind: String): String = when (kind) {
        VIDEO -> "Vidéo"
        ANIMATED -> "Image animée"
        LOTTIE -> "Animation Lottie"
        else -> "Image"
    }
}
