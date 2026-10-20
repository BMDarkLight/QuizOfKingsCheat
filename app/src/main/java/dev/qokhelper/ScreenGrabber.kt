package dev.qokhelper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.view.WindowManager
import java.io.ByteArrayOutputStream

/**
 * Mirrors the screen into an ImageReader and always holds on to the newest frame,
 * so a capture is instant instead of waiting for the next frame.
 */
class ScreenGrabber(context: Context, projection: MediaProjection) {

    private val thread = HandlerThread("screen-grabber").apply { start() }
    private val handler = Handler(thread.looper)
    private val width: Int
    private val height: Int
    private val reader: ImageReader
    private val display: VirtualDisplay

    private val lock = Any()
    private var latest: Image? = null

    init {
        val wm = context.getSystemService(WindowManager::class.java)
        val bounds = wm.maximumWindowMetrics.bounds
        width = bounds.width()
        height = bounds.height()
        val dpi = context.resources.displayMetrics.densityDpi

        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
        reader.setOnImageAvailableListener({ r ->
            val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            synchronized(lock) {
                latest?.close()
                latest = img
            }
        }, handler)

        display = projection.createVirtualDisplay(
            "qok-helper", width, height, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, handler,
        ) ?: error("Could not create virtual display")
    }

    /** Newest frame as a base64 JPEG scaled to at most [maxWidth] px wide, or null if no frame yet. */
    fun captureJpegBase64(maxWidth: Int = 1000): String? {
        val bitmap = synchronized(lock) { latest?.let(::toBitmap) } ?: return null
        val scaled = if (bitmap.width > maxWidth) {
            Bitmap.createScaledBitmap(bitmap, maxWidth, bitmap.height * maxWidth / bitmap.width, true)
                .also { bitmap.recycle() }
        } else bitmap
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        scaled.recycle()
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun toBitmap(img: Image): Bitmap {
        val plane = img.planes[0]
        val buffer = plane.buffer.apply { rewind() }
        // Rows may be padded past the visible width.
        val paddedWidth = plane.rowStride / plane.pixelStride
        val padded = Bitmap.createBitmap(paddedWidth, img.height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(buffer)
        if (paddedWidth == img.width) return padded
        return Bitmap.createBitmap(padded, 0, 0, img.width, img.height).also { padded.recycle() }
    }

    fun release() {
        display.release()
        synchronized(lock) {
            latest?.close()
            latest = null
        }
        reader.close()
        thread.quitSafely()
    }
}
