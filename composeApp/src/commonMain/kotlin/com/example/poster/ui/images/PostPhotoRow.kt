package com.example.poster.ui.images

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.poster.domain.validation.ImageRules
import com.example.poster.theme.Spacing
import com.example.poster.ui.platform.rememberImagePicker
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.post_image
import poster.composeapp.generated.resources.post_photo_add
import poster.composeapp.generated.resources.post_photo_change
import poster.composeapp.generated.resources.post_photo_error_size
import poster.composeapp.generated.resources.post_photo_error_type
import poster.composeapp.generated.resources.post_photo_hint
import poster.composeapp.generated.resources.post_photo_preparing
import poster.composeapp.generated.resources.post_photo_remove

/**
 * The picture section of the post form: the current or newly picked picture
 * with Change / Remove, or one "Add a picture" button. The picker returns
 * bytes already downscaled on the device (ImageRules.MAX_SIDE_PX), and the
 * rules are checked here too so a bad file is refused before anything is sent.
 */
@Composable
fun PostPhotoRow(
    currentImage: String?,
    change: PostImageChange,
    onChange: (PostImageChange) -> Unit,
) {
    var error by remember { mutableStateOf<String?>(null) }
    var preparing by remember { mutableStateOf(false) }
    val typeError = stringResource(Res.string.post_photo_error_type)
    val sizeError = stringResource(Res.string.post_photo_error_size, ImageRules.MAX_BYTES / (1024 * 1024))
    val pick = rememberImagePicker { bytes ->
        preparing = false
        if (bytes == null) return@rememberImagePicker
        error = when {
            ImageRules.extensionOf(bytes) == null -> typeError
            ImageRules.tooLarge(bytes) -> sizeError
            else -> null
        }
        if (error == null) onChange(PostImageChange.New(bytes))
    }
    val picked = (change as? PostImageChange.New)?.bytes
    val pickedBitmap = remember(picked) { picked?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() } }
    val showingExisting = change is PostImageChange.Keep && currentImage != null
    val remote = if (showingExisting) rememberPostImage(currentImage) else null
    val shown = pickedBitmap ?: remote

    Column(modifier = Modifier.fillMaxWidth().testTag("post_photo_row")) {
        if (shown != null) {
            Image(
                bitmap = shown,
                contentDescription = stringResource(Res.string.post_image),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .testTag("post_photo_preview"),
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            OutlinedButton(
                onClick = { preparing = true; pick() },
                enabled = !preparing,
                modifier = Modifier.testTag("post_photo_pick"),
            ) {
                Text(
                    stringResource(
                        when {
                            preparing -> Res.string.post_photo_preparing
                            shown != null || showingExisting -> Res.string.post_photo_change
                            else -> Res.string.post_photo_add
                        },
                    ),
                )
            }
            if (shown != null || showingExisting) {
                TextButton(
                    onClick = { error = null; onChange(PostImageChange.Remove) },
                    modifier = Modifier.testTag("post_photo_remove"),
                ) { Text(stringResource(Res.string.post_photo_remove)) }
            }
        }
        Text(
            text = error ?: stringResource(Res.string.post_photo_hint),
            style = MaterialTheme.typography.bodySmall,
            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("post_photo_hint"),
        )
    }
}
