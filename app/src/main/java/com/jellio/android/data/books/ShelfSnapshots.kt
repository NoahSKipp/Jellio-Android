package com.jellio.android.data.books

import android.content.Context
import com.jellio.android.data.model.BaseItemDto
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// A continue card without its cover: cover URLs carry the access token, so
// they are rebuilt on load and never written to disk.
@JsonClass(generateAdapter = true)
data class CachedContinueCard(
    val ItemId: String = "",
    val Title: String = "",
    val Subtitle: String = "",
    val Progress: Double = 0.0,
    val Kind: String = "book",
    val MangaId: Int? = null,
    val ChapterId: Int? = null,
)

@JsonClass(generateAdapter = true)
data class ShelfSnapshot(
    val Items: List<BaseItemDto> = emptyList(),
    val Info: Map<String, BookShelfInfoDto> = emptyMap(),
    val Continue: List<CachedContinueCard> = emptyList(),
    val Stream: List<MangaSeriesSummaryDto> = emptyList(),
    val Local: List<BaseItemDto> = emptyList(),
    val Updates: List<MangaUpdateItemDto> = emptyList(),
    val Progress: Map<String, ReadingProgressDto> = emptyMap(),
    val Shelf: ShelfDto? = null,
)

// The last shelf per user and kind, shown first while the fresh one loads.
@Singleton
class ShelfSnapshotStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
) {
    private val memory = HashMap<String, ShelfSnapshot>()
    private val adapter by lazy { moshi.adapter(ShelfSnapshot::class.java) }
    private val dir: File by lazy { File(context.cacheDir, "jellio_shelf_snapshots").apply { mkdirs() } }

    private fun key(userId: String, kind: String) = "${kind}_${userId.filter { it.isLetterOrDigit() }}"

    suspend fun load(userId: String, kind: String): ShelfSnapshot? {
        val k = key(userId, kind)
        synchronized(memory) { memory[k] }?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val file = File(dir, "$k.json")
                if (!file.exists()) return@withContext null
                adapter.fromJson(file.readText())?.also { synchronized(memory) { memory[k] = it } }
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun save(userId: String, kind: String, snapshot: ShelfSnapshot) {
        val k = key(userId, kind)
        synchronized(memory) { memory[k] = snapshot }
        withContext(Dispatchers.IO) {
            try {
                val tmp = File(dir, "$k.json.tmp")
                tmp.writeText(adapter.toJson(snapshot))
                tmp.renameTo(File(dir, "$k.json"))
            } catch (_: Exception) {
            }
        }
    }
}
