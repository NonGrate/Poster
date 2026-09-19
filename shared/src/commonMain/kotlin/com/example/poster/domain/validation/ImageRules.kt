package com.example.poster.domain.validation

/**
 * What an image on a post may be. The form checks these before uploading so
 * the failure is immediate; the server checks them again because the form is
 * not the only client.
 */
object ImageRules {
    /** After the device has downscaled it. A 1600 px JPEG is well under this. */
    const val MAX_BYTES = 5 * 1024 * 1024

    /** Longest side the device scales a picked photo down to before uploading. */
    const val MAX_SIDE_PX = 1600

    /** JPEG quality used for that downscale. */
    const val JPEG_QUALITY = 82

    fun tooLarge(bytes: ByteArray): Boolean = bytes.size > MAX_BYTES

    /**
     * The file extension for the bytes' real format, from their magic numbers,
     * or null for anything that is not a JPEG, PNG or WebP. The declared
     * content type is not consulted: it is whatever the sender said.
     */
    fun extensionOf(bytes: ByteArray): String? = when {
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "jpg"
        bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte() -> "png"
        bytes.size >= 12 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte() -> "webp"
        else -> null
    }

    /** A stored id looks like `<32 hex>.<ext>`; anything else is not one. */
    fun isValidId(id: String): Boolean = ID.matches(id)

    private val ID = Regex("[0-9a-f]{32}\\.[a-z0-9]{1,5}")
}
