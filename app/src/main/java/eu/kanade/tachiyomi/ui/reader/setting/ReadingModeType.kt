package eu.kanade.tachiyomi.ui.reader.setting

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.viewer.BaseViewer
import eu.kanade.tachiyomi.ui.reader.viewer.book.L2RBookViewer
import eu.kanade.tachiyomi.ui.reader.viewer.book.R2LBookViewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.L2RPagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.R2LPagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.VerticalPagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer

enum class ReadingModeType(val prefValue: Int, @StringRes val stringRes: Int, @DrawableRes val iconRes: Int, val flagValue: Int) {
    DEFAULT(0, R.string.label_default, R.drawable.ic_reader_default_24dp, 0x00000000),
    LEFT_TO_RIGHT(1, R.string.left_to_right_viewer, R.drawable.ic_reader_ltr_24dp, 0x00000001),
    RIGHT_TO_LEFT(2, R.string.right_to_left_viewer, R.drawable.ic_reader_rtl_24dp, 0x00000002),
    VERTICAL(3, R.string.vertical_viewer, R.drawable.ic_reader_vertical_24dp, 0x00000003),
    WEBTOON(4, R.string.webtoon_viewer, R.drawable.ic_reader_webtoon_24dp, 0x00000004),
    CONTINUOUS_VERTICAL(5, R.string.vertical_plus_viewer, R.drawable.ic_reader_continuous_vertical_24dp, 0x00000005),
    RIGHT_TO_LEFT_BOOK(6, R.string.book_viewer_rtl, R.drawable.ic_book_open_variant_24dp, 0x00000006),
    LEFT_TO_RIGHT_BOOK(7, R.string.book_viewer_ltr, R.drawable.ic_book_open_variant_24dp, 0x00000007),
    ;

    companion object {
        const val MASK = 0x00000008

        fun fromPreference(preference: Int?): ReadingModeType = values().find { it.flagValue == preference } ?: DEFAULT

        fun isPagerType(preference: Int): Boolean {
            val mode = fromPreference(preference)
            return mode == LEFT_TO_RIGHT || mode == RIGHT_TO_LEFT || mode == VERTICAL
        }

        fun isShaderType(preference: Int): Boolean {
            val mode = fromPreference(preference)
            return mode == RIGHT_TO_LEFT_BOOK || mode == LEFT_TO_RIGHT_BOOK
        }

        fun fromSpinner(position: Int?) = values().find { value -> value.prefValue == position } ?: DEFAULT

        fun toViewer(preference: Int?, activity: ReaderActivity): BaseViewer {
            return when (fromPreference(preference)) {
                LEFT_TO_RIGHT -> L2RPagerViewer(activity)
                RIGHT_TO_LEFT -> R2LPagerViewer(activity)
                VERTICAL -> VerticalPagerViewer(activity)
                WEBTOON -> WebtoonViewer(activity)
                CONTINUOUS_VERTICAL -> WebtoonViewer(activity, isContinuous = false)
                RIGHT_TO_LEFT_BOOK -> R2LBookViewer(activity)
                LEFT_TO_RIGHT_BOOK -> L2RBookViewer(activity)
                DEFAULT -> throw IllegalStateException("Preference value must be resolved: $preference")
            }
        }
    }
}
