package com.example.poster.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.example.poster.domain.validation.ImageRules
import kotlinx.browser.document
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader

/**
 * A hidden `<input type="file">`, clicked from code. The file goes up as it
 * is: the browser has no cheap way to re-encode a JPEG, so the size rule is
 * applied and anything over it is refused rather than shrunk.
 */
@Composable
actual fun rememberImagePicker(onPicked: (ByteArray?) -> Unit): () -> Unit {
    val latest by rememberUpdatedState(onPicked)
    return {
        val input = document.createElement("input") as HTMLInputElement
        input.type = "file"
        input.accept = "image/jpeg,image/png,image/webp"
        input.onchange = {
            val file = input.files?.item(0)
            if (file == null) {
                latest(null)
            } else {
                val reader = FileReader()
                reader.onload = {
                    val bytes = reader.result?.unsafeCast<ArrayBuffer>()?.toByteArray()
                    latest(bytes?.takeIf { ImageRules.extensionOf(it) != null && !ImageRules.tooLarge(it) })
                    null
                }
                reader.readAsArrayBuffer(file)
            }
            null
        }
        input.click()
    }
}

private fun ArrayBuffer.toByteArray(): ByteArray {
    val view = Int8Array(this)
    return ByteArray(view.length) { view[it] }
}
