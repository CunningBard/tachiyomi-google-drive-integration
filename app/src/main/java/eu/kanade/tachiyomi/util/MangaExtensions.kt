package eu.kanade.tachiyomi.util

import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.model.SManga
import java.util.Date

fun Manga.isLocal() = source == LocalSource.ID

/**
 * Call before updating [Manga.thumbnail_url] to ensure old cover can be cleared from cache
 */
fun Manga.prepUpdateCover(coverCache: CoverCache, remoteManga: SManga, refreshSameUrl: Boolean) {
    // Never refresh covers if the new url is null, as the current url has possibly become invalid
    val newUrl = remoteManga.thumbnail_url ?: return

    // Never refresh covers if the url is empty to avoid "losing" existing covers
    if (newUrl.isEmpty()) return

    if (!refreshSameUrl && thumbnail_url == newUrl) return

    when {
        isLocal() -> {
            cover_last_modified = Date().time
        }
        hasCustomCover(coverCache) -> {
            coverCache.deleteFromCache(this, false)
        }
        else -> {
            cover_last_modified = Date().time
            coverCache.deleteFromCache(this, false)
        }
    }
}

fun Manga.hasCustomCover(coverCache: CoverCache): Boolean {
    return coverCache.getCustomCoverFile(id).exists()
}

fun Manga.removeCovers(coverCache: CoverCache) {
    if (isLocal()) return

    cover_last_modified = Date().time
    coverCache.deleteFromCache(this, true)
}

fun Manga.updateCoverLastModified(db: DatabaseHelper) {
    cover_last_modified = Date().time
    db.updateMangaCoverLastModified(this).executeAsBlocking()
}

fun Manga.shouldDownloadNewChapters(db: DatabaseHelper, prefs: PreferencesHelper): Boolean {
    if (!favorite) return false

    // Boolean to determine if user wants to automatically download new chapters.
    val downloadNewChapter = prefs.downloadNewChapter().get()
    if (!downloadNewChapter) return false

    val includedCategories = prefs.downloadNewChapterCategories().get().map { it.toInt() }
    val excludedCategories = prefs.downloadNewChapterCategoriesExclude().get().map { it.toInt() }

    // Default: Download from all categories
    if (includedCategories.isEmpty() && excludedCategories.isEmpty()) return true

    // Get all categories, else default category (0)
    val categoriesForManga =
        db.getCategoriesForManga(this).executeAsBlocking()
            .mapNotNull { it.id }
            .takeUnless { it.isEmpty() } ?: listOf(0)

    // In excluded category
    if (categoriesForManga.any { it in excludedCategories }) return false

    // Included category not selected
    if (includedCategories.isEmpty()) return true

    // In included category
    return categoriesForManga.any { it in includedCategories }
}
