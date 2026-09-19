package com.example.poster.ui.images

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.example.poster.network.PostApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.post_image

/**
 * Fetches post images through the authenticated client and keeps the last few
 * decoded. Images are served under the post's visibility, so a plain image
 * library with its own HTTP stack would have to be taught the bearer token;
 * this rides on the client that already has it.
 *
 * ponytail: a small in-memory map, no disk cache. Add one when scrolling a
 * long feed re-downloads noticeably; the server sends `Cache-Control` so an
 * HTTP cache plugin would be the cheap upgrade.
 */
class PostImageLoader(private val api: PostApi) {
    private val cache = LinkedHashMap<String, ImageBitmap>()
    private val lock = Mutex()

    fun cached(id: String?): ImageBitmap? = id?.let { cache[it] }

    suspend fun load(id: String): ImageBitmap? {
        cache[id]?.let { return it }
        val bitmap = withContext(Dispatchers.Default) {
            runCatching { api.fetchImage(id)?.decodeToImageBitmap() }.getOrNull()
        } ?: return null
        lock.withLock {
            cache[id] = bitmap
            while (cache.size > MAX_CACHED) cache.remove(cache.keys.first())
        }
        return bitmap
    }

    private companion object {
        const val MAX_CACHED = 32
    }
}

/** The decoded picture for [id], null while loading or when it cannot be fetched. */
@Composable
fun rememberPostImage(id: String?): ImageBitmap? {
    val loader: PostImageLoader = koinInject()
    return produceState(loader.cached(id), id) {
        value = id?.let { loader.load(it) }
    }.value
}

/**
 * A post's picture, full width. [crop] fixes the height to a comfortable band
 * and crops into it (lists); off, the picture keeps its own proportions
 * (details). Until the bytes arrive a quiet block of the same shape holds the
 * place, so cards do not jump when it lands.
 */
@Composable
fun PostImage(id: String, modifier: Modifier = Modifier, crop: Boolean = true) {
    val bitmap = rememberPostImage(id)
    val shape = MaterialTheme.shapes.medium
    val ratio = when {
        bitmap == null -> PLACEHOLDER_RATIO
        crop -> (bitmap.width.toFloat() / bitmap.height).coerceIn(MIN_CROP_RATIO, MAX_CROP_RATIO)
        else -> bitmap.width.toFloat() / bitmap.height
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = stringResource(Res.string.post_image),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(ratio),
            )
        }
    }
}

private const val PLACEHOLDER_RATIO = 16f / 10f
private const val MIN_CROP_RATIO = 4f / 5f
private const val MAX_CROP_RATIO = 2.2f
