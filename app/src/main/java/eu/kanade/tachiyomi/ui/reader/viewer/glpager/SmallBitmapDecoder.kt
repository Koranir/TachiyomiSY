package eu.kanade.tachiyomi.ui.reader.viewer.glpager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.AsyncTask
import com.davemorrissey.labs.subscaleview.CustomImageSource
import com.davemorrissey.labs.subscaleview.ImageViewState
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.TilesInitTask
import com.davemorrissey.labs.subscaleview.provider.AssetInputProvider
import com.davemorrissey.labs.subscaleview.provider.InputProvider
import com.davemorrissey.labs.subscaleview.provider.OpenStreamProvider
import com.davemorrissey.labs.subscaleview.provider.ResourceInputProvider
import com.davemorrissey.labs.subscaleview.provider.UriInputProvider
import eu.kanade.tachiyomi.util.system.logcat
import java.io.InputStream

class SmallBitmapDecoder (
    holder: GLPageImageHolder,
    ){
    private var executor = AsyncTask.THREAD_POOL_EXECUTOR

    var sRegion: Rect(0)

    fun setImage(imageSource: CustomImageSource) {
        setImage(imageSource, null)
    }

    fun setImage(imageSource: CustomImageSource, state: ImageViewState?) {
        if (imageSource == null) {
            throw NullPointerException("imageSource must not be null")
        }
        //reset(true)
        if (imageSource.bitmap != null && imageSource.sRegion != null) {
            imageSource.bitmap?.let {
                Bitmap.createBitmap(
                    it,
                    imageSource.sRegion!!.left,
                    imageSource.sRegion!!.top,
                    imageSource.sRegion!!!!.width(),
                    imageSource.sRegion!!.height(),
                )
            }?.let {
                onImageLoaded(
                    it,
                    false,
                )
            }
        } else if (imageSource.bitmap != null) {
            onImageLoaded(imageSource.bitmap, imageSource.isCached)
        } else {
            // Load the bitmap using tile decoding.
            this.provider = imageSource.provider
            sRegion = imageSource.sRegion
            val task = TilesInitTask(this, getContext(), provider)
            execute(task)
        }
    }

    @Synchronized
    private fun onImageLoaded(bitmap: Bitmap, bitmapIsCached: Boolean) {
        logcat { "Loaded Image" }
        // If actual dimensions don't match the declared size, reset everything.
        if (this.sWidth > 0 && this.sHeight > 0 && (this.sWidth != bitmap.width || this.sHeight != bitmap.height)) {
            reset(false)
        }
        if (bitmap != null && !bitmapIsCached) {
            bitmap.recycle()
        }
        bitmapIsCached = bitmapIsCached
        bitmap = bitmap
        this.sWidth = bitmap.width
        this.sHeight = bitmap.height
        val ready: Boolean = checkReady()
        val imageLoaded: Boolean = checkImageLoaded()
        if (ready || imageLoaded) {
            invalidate()
            requestLayout()
        }
    }

    private fun execute(asyncTask: AsyncTask<Void, Void, *>) {
        asyncTask.executeOnExecutor(executor)
    }
}

/**
 * Helper class used to set the source and additional attributes from a variety of sources. Supports
 * use of a bitmap, asset, resource, external file or any other URI.
 *
 *
 * When you are using a preview image, you must set the dimensions of the full size image on the
 * CustomImageSource object for the full size image using the [.dimensions] method.
 */
class CustomImageSource {
    val bitmap: Bitmap?
    val provider: InputProvider?
    var sWidth = 0
        private set
    var sHeight = 0
        private set
    var sRegion: Rect? = null
        private set
    var isCached = false
        private set

    private constructor(bitmap: Bitmap, cached: Boolean) {
        this.bitmap = bitmap
        provider = null
        sWidth = bitmap.width
        sHeight = bitmap.height
        isCached = cached
    }

    private constructor(provider: InputProvider) {
        bitmap = null
        this.provider = provider
    }

    /**
     * Declare the dimensions of the image. This is only required for a full size image, when you are specifying a URI
     * and also a preview image. When displaying a bitmap object, or not using a preview, you do not need to declare
     * the image dimensions. Note if the declared dimensions are found to be incorrect, the view will reset.
     *
     * @param sWidth  width of the source image.
     * @param sHeight height of the source image.
     * @return this instance for chaining.
     */
    fun dimensions(sWidth: Int, sHeight: Int): CustomImageSource {
        if (bitmap == null) {
            this.sWidth = sWidth
            this.sHeight = sHeight
        }
        setInvariants()
        return this
    }

    private fun setInvariants() {
        if (this.sRegion != null) {
            this.sWidth = this.sRegion!!.width()
            this.sHeight = this.sRegion!!.height()
        }
    }

    companion object {
        /**
         * Create an instance from a resource. The correct resource for the device screen resolution will be used.
         *
         * @param resId resource ID.
         * @return an [CustomImageSource] instance.
         */
        fun resource(context: Context?, resId: Int): CustomImageSource {
            return CustomImageSource(ResourceInputProvider(context, resId))
        }

        /**
         * Create an instance from an asset name.
         *
         * @param assetName asset name.
         * @return an [CustomImageSource] instance.
         */
        fun asset(context: Context?, assetName: String): CustomImageSource {
            if (assetName == null) {
                throw java.lang.NullPointerException("Asset name must not be null")
            }
            return CustomImageSource(AssetInputProvider(context, assetName))
        }

        /**
         * Create an instance from a URI.
         *
         * @param uri image URI.
         * @return an [CustomImageSource] instance.
         */
        fun uri(context: Context?, uri: Uri): CustomImageSource {
            if (uri == null) {
                throw java.lang.NullPointerException("Uri must not be null")
            }
            return CustomImageSource(UriInputProvider(context, uri))
        }

        /**
         * Create an instance from an input provider.
         *
         * @param provider input stream provider.
         * @return an [CustomImageSource] instance.
         */
        fun provider(provider: InputProvider): CustomImageSource {
            if (provider == null) {
                throw java.lang.NullPointerException("Input provider must not be null")
            }
            return CustomImageSource(provider)
        }

        /**
         * Create an instance from an input stream.
         *
         * @param stream open input stream.
         * @return an [CustomImageSource] instance.
         */
        fun inputStream(stream: InputStream): CustomImageSource {
            if (stream == null) {
                throw java.lang.NullPointerException("Input stream must not be null")
            }
            return CustomImageSource(OpenStreamProvider(stream))
        }

        /**
         * Provide a loaded bitmap for display.
         *
         * @param bitmap bitmap to be displayed.
         * @return an [CustomImageSource] instance.
         */
        fun bitmap(bitmap: Bitmap): CustomImageSource {
            if (bitmap == null) {
                throw java.lang.NullPointerException("Bitmap must not be null")
            }
            return CustomImageSource(bitmap, false)
        }

        /**
         * Provide a loaded and cached bitmap for display. This bitmap will not be recycled when it is no
         * longer needed. Use this method if you loaded the bitmap with an image loader such as Picasso
         * or Volley.
         *
         * @param bitmap bitmap to be displayed.
         * @return an [CustomImageSource] instance.
         */
        fun cachedBitmap(bitmap: Bitmap): CustomImageSource {
            if (bitmap == null) {
                throw java.lang.NullPointerException("Bitmap must not be null")
            }
            return CustomImageSource(bitmap, true)
        }
        
    }
}
