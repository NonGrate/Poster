package com.example.poster.ui.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.example.poster.domain.validation.ImageRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
actual fun rememberImagePicker(onPicked: (ByteArray?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onPicked)
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) {
            latest(null)
        } else {
            scope.launch {
                latest(withContext(Dispatchers.IO) { runCatching { readDownscaledJpeg(context, uri) }.getOrNull() })
            }
        }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

/**
 * Decodes the picked file scaled to at most [ImageRules.MAX_SIDE_PX] and
 * re-encodes it as JPEG. ImageDecoder (API 28+) applies the EXIF rotation;
 * the BitmapFactory path for older devices does not. ponytail: sideways
 * photos on API 24–27 would need ExifInterface, add it if anybody reports one.
 */
private fun readDownscaledJpeg(context: Context, uri: Uri): ByteArray {
    val maxSide = ImageRules.MAX_SIDE_PX
    val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val (w, h) = info.size.width to info.size.height
            val scale = maxSide.toFloat() / max(w, h)
            if (scale < 1f) decoder.setTargetSize((w * scale).roundToInt(), (h * scale).roundToInt())
            // Hardware bitmaps cannot be compressed; ask for a software one.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = context.contentResolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("could not decode")
        val scale = maxSide.toFloat() / max(decoded.width, decoded.height)
        if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).roundToInt(), (decoded.height * scale).roundToInt(), true)
        } else {
            decoded
        }
    }
    return ByteArrayOutputStream().also { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, ImageRules.JPEG_QUALITY, out)
    }.toByteArray()
}
