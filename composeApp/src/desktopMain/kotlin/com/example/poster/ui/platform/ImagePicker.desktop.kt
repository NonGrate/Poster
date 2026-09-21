package com.example.poster.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.example.poster.domain.validation.ImageRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.max
import kotlin.math.roundToInt

/** The system file dialog; the picked image is scaled to the same limits the phones apply. */
@Composable
actual fun rememberImagePicker(onPicked: (ByteArray?) -> Unit): () -> Unit {
    val latest by rememberUpdatedState(onPicked)
    val scope = rememberCoroutineScope()
    return {
        val dialog = FileDialog(null as Frame?, "Choose an image", FileDialog.LOAD)
        dialog.setFilenameFilter { _, name -> name.lowercase().substringAfterLast('.') in setOf("jpg", "jpeg", "png", "webp") }
        dialog.isVisible = true
        val file = dialog.file?.let { File(dialog.directory, it) }
        if (file == null) {
            latest(null)
        } else {
            scope.launch { latest(withContext(Dispatchers.IO) { runCatching { prepared(file) }.getOrNull() }) }
        }
    }
}

private fun prepared(file: File): ByteArray? {
    val bytes = file.readBytes()
    val image = ImageIO.read(file) ?: return bytes.takeIf { ImageRules.extensionOf(it) != null && !ImageRules.tooLarge(it) }
    val longest = max(image.width, image.height)
    if (longest <= ImageRules.MAX_SIDE_PX && !ImageRules.tooLarge(bytes) && ImageRules.extensionOf(bytes) != null) return bytes
    val scale = minOf(1.0, ImageRules.MAX_SIDE_PX.toDouble() / longest)
    val scaled = BufferedImage((image.width * scale).roundToInt(), (image.height * scale).roundToInt(), BufferedImage.TYPE_INT_RGB)
    scaled.createGraphics().apply { drawImage(image, 0, 0, scaled.width, scaled.height, null); dispose() }
    val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
    val out = ByteArrayOutputStream()
    writer.output = ImageIO.createImageOutputStream(out)
    writer.write(null, IIOImage(scaled, null, null), writer.defaultWriteParam.apply {
        compressionMode = ImageWriteParam.MODE_EXPLICIT
        compressionQuality = ImageRules.JPEG_QUALITY / 100f
    })
    writer.dispose()
    return out.toByteArray()
}
