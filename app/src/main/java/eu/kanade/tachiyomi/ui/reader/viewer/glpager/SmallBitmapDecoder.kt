package eu.kanade.tachiyomi.ui.reader.viewer.glpager

import android.graphics.Bitmap
import eu.kanade.tachiyomi.util.system.logcat
import tachiyomi.decoder.ImageDecoder
import java.io.InputStream

class SmallBitmapDecoder(
    imageStream: InputStream,
    cropBorders: Boolean = false,
) {

    private var decoder: ImageDecoder

    private var bitmap: Bitmap? = null

    private var error: Boolean = false

    init {
        decoder = ImageDecoder.Companion.newInstance(imageStream, cropBorders)!!
        check(decoder.width > 0 && decoder.height > 0) { "Failed to initialize decoder" }
        bitmap = decoder?.decode()
        decoder.recycle()
    }

    fun getBitmap(): Bitmap? {
        logcat { "Trying to get bitmap" }
        if (bitmap == null) { logcat { "bitmap does not exist" } }
        return bitmap
    }
}
