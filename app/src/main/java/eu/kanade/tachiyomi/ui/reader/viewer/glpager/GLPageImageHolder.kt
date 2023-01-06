package eu.kanade.tachiyomi.ui.reader.viewer.glpager

import android.content.Context
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerViewer
import eu.kanade.tachiyomi.util.system.ImageUtil
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.io.ByteArrayInputStream

class GLPageImageHolder (
    readerThemedContext: Context,
    val viewer: PagerViewer,
    val page: ReaderPage,
    ){

    var isLoading = true
    var isError = false

    /**
     * Subscription used to read the header of the image. This is needed in order to instantiate
     * the appropiate image view depending if the image is animated (GIF).
     */
    private var readImageHeaderSubscription: Subscription? = null


    fun setImage() {
        isLoading = false
        isError = false

        unsubscribeReadImageHeader()
        val streamFn = page.stream ?: return

        readImageHeaderSubscription = Observable
            .fromCallable {
                val stream = streamFn().buffered(16)
                // SY <--
                val bais = ByteArrayInputStream(stream.readBytes())
                try {
                    val isAnimated = ImageUtil.isAnimatedAndSupported(bais)
                    bais.reset()
                    val background = if (!isAnimated && viewer.config.automaticBackground) {
                        ImageUtil.chooseBackground(viewer.getView().context, bais)
                    } else {
                        null
                    }
                    bais.reset()
                    Triple(bais, isAnimated, background)
                } finally {
                    stream.close()
                }
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { (bais, isAnimated, background) ->
                bais.use {
                    setImage(
                        it,
                    )
                    if (!isAnimated) {
                        pageBackground = background
                    }
                }
            }
            .subscribe({}, {})
    }

    fun setImage(stream: ByteArrayInputStream) {

    }

    /**
     * Unsubscribes from the status subscription.
     */
    private fun unsubscribeStatus(page: Int) {
        val subscription = if (page == 1) statusSubscription else extraStatusSubscription
        subscription?.unsubscribe()
        if (page == 1) statusSubscription = null else extraStatusSubscription = null
    }

    /**
     * Unsubscribes from the progress subscription.
     */
    private fun unsubscribeProgress(page: Int) {
        val subscription = if (page == 1) progressSubscription else extraProgressSubscription
        subscription?.unsubscribe()
        if (page == 1) progressSubscription = null else extraProgressSubscription = null
    }

    /**
     * Unsubscribes from the read image header subscription.
     */
    private fun unsubscribeReadImageHeader() {
        readImageHeaderSubscription?.unsubscribe()
        readImageHeaderSubscription = null
    }
}
