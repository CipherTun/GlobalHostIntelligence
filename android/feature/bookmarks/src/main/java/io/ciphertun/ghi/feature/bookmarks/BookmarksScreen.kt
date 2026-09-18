package io.ciphertun.ghi.feature.bookmarks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.ciphertun.ghi.core.database.entities.BookmarkEntity
import io.ciphertun.ghi.core.ui.components.GhiScreenScaffold

/** Purpose: saved entities across every type, one tap back to their Detail screen. */
@Composable
fun BookmarksScreen(
    bookmarks: List<BookmarkEntity>,
    onBack: () -> Unit,
    onOpenBookmark: (entityType: String, entityId: String) -> Unit,
) {
    GhiScreenScaffold(title = "Bookmarks", onBack = onBack) { modifier ->
        LazyColumn(modifier.fillMaxSize()) {
            items(bookmarks, key = { it.id }) { bookmark ->
                ListItem(
                    headlineContent = { Text(bookmark.entityId) },
                    supportingContent = { Text(bookmark.entityType) },
                    modifier = Modifier.clickable { onOpenBookmark(bookmark.entityType, bookmark.entityId) },
                )
            }
        }
    }
}
