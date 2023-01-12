package eu.kanade.tachiyomi.ui.reader.viewer.glpager

import android.content.Context
import android.graphics.Bitmap
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.util.system.logcat
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

class GLPageImageHolder(
    readerThemedContext: Context,
    val viewer: GLPagerViewer,
    val page: ReaderPage,
    private var extraPage: ReaderPage? = null,
) {

    var isLoading = true
    var isError = false

    /**
     * Subscription for status changes of the page.
     */
    private var statusSubscription: Subscription? = null

    /**
     * Subscription for progress changes of the page.
     */
    private var progressSubscription: Subscription? = null

    /**
     * Subscription for status changes of the page.
     */
    private var extraStatusSubscription: Subscription? = null

    /**
     * Subscription for progress changes of the page.
     */
    private var extraProgressSubscription: Subscription? = null

    lateinit var decoder: SmallBitmapDecoder

    private var bitmap: Bitmap? = null

    // SY -->
    var status: Int = 0
    var extraStatus: Int = 0
    var progress: Int = 0
    var extraProgress: Int = 0
    // SY <--

    var temvar: Int = 0

    init {
        logcat { "Created page holder for ${page.index}" }
        observeStatus()
    }

    fun getImage(): Bitmap? {
        logcat { "Bitmap size $bitmap, ${bitmap?.width}, ${bitmap?.height}" }
        return bitmap
    }

    /**
     * Observes the progress of the page and updates view.
     */
    private fun observeProgress() {
        progressSubscription?.unsubscribe()

        progressSubscription = Observable.interval(100, TimeUnit.MILLISECONDS)
            .map { page.progress }
            .distinctUntilChanged()
            .onBackpressureLatest()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { value -> temvar = value }
    }

    /**
     * Observes the status of the page and notify the changes.
     *
     * @see processStatus
     */
    private fun observeStatus() {
        statusSubscription?.unsubscribe()

        val loader = page.chapter.pageLoader ?: return
        statusSubscription = loader.getPage(page)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                status = it
                processStatus(it)
            }

        val extraPage = extraPage ?: return
        val loader2 = extraPage.chapter.pageLoader ?: return
        extraStatusSubscription = loader2.getPage(extraPage)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                extraStatus = it
                // processStatus2(it)
            }
    }

    /**
     * Called when the status of the page changes.
     *
     * @param status the new status of the page.
     */
    private fun processStatus(status: Int) {
        when (status) {
            // Page.QUEUE -> //setQueued()
            // Page.LOAD_PAGE -> //setLoading()
            Page.DOWNLOAD_IMAGE -> {
                observeProgress()
                // setDownloading()
            }
            Page.READY -> {
                logcat { "Page ${page.index} ready" }
                if (extraStatus == Page.READY || extraPage == null) {
                    val streamFn = page.stream ?: return
                    val stream = streamFn().buffered(16)
                    val bais = ByteArrayInputStream(stream.readBytes())
                    decoder = SmallBitmapDecoder(bais)
                    bitmap = decoder.getBitmap()
                    if (viewer.currentPage == page.index) {
                        bitmap?.let { viewer.viewer.mRenderer.loadTexture(0, it) }
                        logcat { "Loading texture from holder index ${page.index}" }
                    }
                }
                unsubscribeProgress(1)
            }
            // Page.ERROR -> {
            // setError()
            // unsubscribeProgress(1)
            // }
        }
    }

    /**
     * Unsubscribes from the progress subscription.
     */
    private fun unsubscribeProgress(page: Int) {
        val subscription = if (page == 1) progressSubscription else extraProgressSubscription
        subscription?.unsubscribe()
        if (page == 1) progressSubscription = null else extraProgressSubscription = null
    }
}
