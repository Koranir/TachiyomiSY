package exh.pagepreview

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.core.os.bundleOf
import eu.kanade.tachiyomi.ui.base.controller.FullComposeController
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import exh.pagepreview.components.PagePreviewScreen

class PagePreviewController : FullComposeController<PagePreviewPresenter> {

    @Suppress("unused")
    constructor(bundle: Bundle? = null) : super(bundle)

    constructor(mangaId: Long) : super(
        bundleOf(MANGA_ID to mangaId),
    )

    override fun createPresenter() = PagePreviewPresenter(args.getLong(MANGA_ID, -1))

    @Composable
    override fun ComposeContent() {
        PagePreviewScreen(
            state = presenter.state.collectAsState().value,
            pageDialogOpen = presenter.pageDialogOpen,
            onPageSelected = presenter::moveToPage,
            onOpenPage = this::openPage,
            onOpenPageDialog = { presenter.pageDialogOpen = true },
            onDismissPageDialog = { presenter.pageDialogOpen = false },
            navigateUp = router::popCurrentController,
        )
    }

    fun openPage(page: Int) {
        val state = presenter.state.value as? PagePreviewState.Success ?: return
        activity?.run {
            startActivity(ReaderActivity.newIntent(this, state.manga.id, state.chapter.id, page))
        }
    }

    companion object {
        const val MANGA_ID = "manga_id"
    }
}
