package com.example.poster.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.example.poster.domain.validation.ImageRules
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import platform.posix.memcpy
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSItemProvider
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * PHPicker, presented over the top-most view controller. The delegate is held
 * by the remembered holder for as long as the composable lives; PHPicker keeps
 * only a weak reference to it, so without that it would be collected before
 * the person picked anything.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberImagePicker(onPicked: (ByteArray?) -> Unit): () -> Unit {
    val latest by rememberUpdatedState(onPicked)
    val holder = remember { PickerHolder() }
    return {
        val configuration = PHPickerConfiguration().apply {
            filter = PHPickerFilter.imagesFilter
            selectionLimit = 1
        }
        val picker = PHPickerViewController(configuration)
        holder.delegate = PickerDelegate { bytes -> latest(bytes) }
        picker.delegate = holder.delegate
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}

private class PickerHolder {
    var delegate: PickerDelegate? = null
}

@OptIn(ExperimentalForeignApi::class)
private class PickerDelegate(private val onPicked: (ByteArray?) -> Unit) :
    NSObject(), PHPickerViewControllerDelegateProtocol {

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val provider: NSItemProvider? = (didFinishPicking.firstOrNull() as? PHPickerResult)?.itemProvider
        if (provider == null) {
            onPicked(null)
            return
        }
        provider.loadDataRepresentationForTypeIdentifier("public.image") { data: NSData?, _: NSError? ->
            val bytes = data?.let { UIImage.imageWithData(it) }?.let { downscaledJpeg(it) }
            dispatch_async(dispatch_get_main_queue()) { onPicked(bytes) }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun downscaledJpeg(image: UIImage): ByteArray? {
    val (width, height) = image.size.useContents { width * image.scale to height * image.scale }
    val maxSide = ImageRules.MAX_SIDE_PX.toDouble()
    val scale = (maxSide / max(width, height)).coerceAtMost(1.0)
    val target = CGSizeMake((width * scale).roundToInt().toDouble(), (height * scale).roundToInt().toDouble())
    // scale 1: one point per pixel, so the target is the pixel size.
    val format = UIGraphicsImageRendererFormat.defaultFormat().apply { this.scale = 1.0 }
    val scaled = UIGraphicsImageRenderer(size = target, format = format).imageWithActions { _ ->
        image.drawInRect(CGRectMake(0.0, 0.0, target.useContents { this.width }, target.useContents { this.height }))
    }
    val data = UIImageJPEGRepresentation(scaled, ImageRules.JPEG_QUALITY / 100.0) ?: return null
    return data.toByteArray()
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
}

private fun topViewController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (controller?.presentedViewController != null) controller = controller.presentedViewController
    return controller
}
