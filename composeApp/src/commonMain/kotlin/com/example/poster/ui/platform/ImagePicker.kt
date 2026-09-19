package com.example.poster.ui.platform

import androidx.compose.runtime.Composable

/**
 * The system photo picker. Returns a function that opens it; [onPicked] gets
 * the chosen picture as JPEG bytes no longer than ImageRules.MAX_SIDE_PX on
 * either side, or null when the person backed out or the file could not be
 * read. Android: the Photo Picker (no permission). iOS: PHPicker (no permission).
 */
@Composable
expect fun rememberImagePicker(onPicked: (ByteArray?) -> Unit): () -> Unit
