package com.jellio.android.data.offline

import android.content.Context
import android.util.Log
import com.jellio.android.data.books.AnnotationDto
import com.jellio.android.data.books.BooksApi
import com.jellio.android.data.books.ClaimConversionRequest
import com.jellio.android.data.books.ReadingProgressDto
import com.jellio.android.data.model.BaseItemDto
import com.jellio.android.data.model.PlaybackInfoRequest
import com.jellio.android.data.model.UserDataUpdate
import com.jellio.android.data.network.JellyfinApi
import com.jellio.android.data.session.SessionManager
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@JsonClass(generateAdapter = true)
data class OfflineAudiobookTrack(
    val trackId: String,
    val title: String = "",
    val durationTicks: Long = 0L,
    val indexNumber: Int? = null,
    val localFilePath: String? = null,
)

@JsonClass(generateAdapter = true)
data class OfflineDownloadItem(
    val id: String,
    val userId: String,
    val kind: String, // "video" | "book" | "manga" | "audiobook"
    val title: String,
    val subtitle: String = "",
    val seriesKey: String? = null,
    val status: String = STATUS_QUEUED, // "queued" | "downloading" | "paused" | "completed" | "failed"
    val progress: Float = 0f,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val localFilePath: String? = null,
    val coverPath: String? = null,
    val trackPaths: List<String> = emptyList(),
    val audiobookTracks: List<OfflineAudiobookTrack> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    // Auxiliary metadata for offline playback / reading / conversion slots:
    val streamChapterId: Int? = null,
    val streamMangaId: Int? = null,
    val videoQuality: String? = null, // "low" | "high" | "higher" | "original"
    val trackIds: List<String> = emptyList(),
    val itemSnapshot: BaseItemDto? = null,
    val tracksSnapshot: List<BaseItemDto> = emptyList(),
    val errorMessage: String? = null,
) {
    companion object {
        const val STATUS_QUEUED = "queued"
        const val STATUS_DOWNLOADING = "downloading"
        const val STATUS_PAUSED = "paused"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_FAILED = "failed"
    }
}

@JsonClass(generateAdapter = true)
data class OfflineSyncMutation(
    val seq: String = UUID.randomUUID().toString(),
    val userId: String,
    val method: String, // "POST" | "PUT" | "DELETE"
    val path: String, // relative path e.g. "Jellio/reading/progress/{id}"
    val bodyJson: String? = null,
    val kind: String? = null,
    val itemId: String? = null,
    val trackId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@JsonClass(generateAdapter = true)
data class OfflineDownloadManifest(
    val items: List<OfflineDownloadItem> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class OfflineSyncQueueManifest(
    val mutations: List<OfflineSyncMutation> = emptyList(),
)

@Singleton
class OfflineDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val retrofit: Retrofit,
    private val jellyfinApi: JellyfinApi,
    private val sessionManager: SessionManager,
    private val moshi: Moshi,
) {
    private val booksApi: BooksApi by lazy { retrofit.create(BooksApi::class.java) }

    private val downloadClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .build()
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("OfflineDownloadManager", "Unhandled coroutine error", throwable)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
    private val stateMutex = Mutex()
    private val queueWakeLock = Any()

    private val rootDir: File by lazy {
        File(context.filesDir, "jellio-offline").apply { mkdirs() }
    }
    private val manifestFile: File by lazy { File(rootDir, "downloads.json") }
    private val syncQueueFile: File by lazy { File(rootDir, "offline_queue.json") }

    private val manifestAdapter by lazy { moshi.adapter(OfflineDownloadManifest::class.java) }
    private val queueAdapter by lazy { moshi.adapter(OfflineSyncQueueManifest::class.java) }
    private val progressAdapter by lazy { moshi.adapter(ReadingProgressDto::class.java) }
    private val annotationsType by lazy { Types.newParameterizedType(List::class.java, AnnotationDto::class.java) }
    private val annotationsAdapter by lazy { moshi.adapter<List<AnnotationDto>>(annotationsType) }
    private val userDataUpdateAdapter by lazy { moshi.adapter(UserDataUpdate::class.java) }

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private var processorJob: Job? = null

    private val _downloads = MutableStateFlow<List<OfflineDownloadItem>>(emptyList())
    val downloads: StateFlow<List<OfflineDownloadItem>> = _downloads.asStateFlow()

    @Volatile
    private var cachedUserId: String? = context
        .getSharedPreferences("jellio_session_sync", Context.MODE_PRIVATE)
        .getString("user_id", null)

    @Volatile
    private var autoDeleteWatched: Boolean = true

    @Volatile
    private var autoDeleteRead: Boolean = true

    init {
        loadManifestSync()
        scope.launch {
            sessionManager.sessionFlow.collect { session ->
                cachedUserId = session?.userId
                if (session != null) {
                    triggerQueueProcessing()
                    replaySyncQueue()
                }
            }
        }
    }

    fun isAutoDeleteWatchedEnabled(): Boolean = autoDeleteWatched
    fun setAutoDeleteWatchedEnabled(enabled: Boolean) {
        autoDeleteWatched = enabled
    }

    fun isAutoDeleteReadEnabled(): Boolean = autoDeleteRead
    fun setAutoDeleteReadEnabled(enabled: Boolean) {
        autoDeleteRead = enabled
    }

    fun itemDir(userId: String, itemId: String): File =
        File(File(rootDir, sanitizeSegment(userId)), sanitizeSegment(itemId))

    fun getDownload(itemId: String, userId: String? = null): OfflineDownloadItem? {
        val normalized = normalizeId(itemId)
        val uid = userId?.let(::sanitizeSegment)
        return _downloads.value.firstOrNull { entry ->
            (uid == null || sanitizeSegment(entry.userId) == uid) &&
                (normalizeId(entry.id) == normalized || entry.trackIds.any { normalizeId(it) == normalized })
        }
    }

    fun getCompletedFile(itemId: String, userId: String? = null): File? {
        val entry = getDownload(itemId, userId) ?: return null
        if (entry.status != OfflineDownloadItem.STATUS_COMPLETED) return null
        val path = entry.localFilePath ?: return null
        val file = File(path)
        return file.takeIf { it.exists() && it.length() > 0L }
    }

    fun getCompletedTrackFile(itemId: String, trackId: String, userId: String? = null): File? {
        val entry = getDownload(itemId, userId) ?: return null
        if (entry.status != OfflineDownloadItem.STATUS_COMPLETED) return null
        val normalizedTrack = normalizeId(trackId)
        val expectedName = "track-$normalizedTrack.audio"
        val fromTracks = entry.audiobookTracks.firstOrNull { normalizeId(it.trackId) == normalizedTrack }?.localFilePath
        val fromList = fromTracks ?: entry.trackPaths.firstOrNull { File(it).name == expectedName }
        val file = fromList?.let(::File) ?: File(itemDir(entry.userId, entry.id), expectedName)
        return file.takeIf { it.exists() && it.length() > 0L }
    }

    fun getCompletedCoverFile(itemId: String, userId: String? = null): File? {
        val entry = getDownload(itemId, userId) ?: return null
        val path = entry.coverPath ?: return null
        val file = File(path)
        return file.takeIf { it.exists() && it.length() > 0L }
    }

    suspend fun queueVideoDownload(
        item: BaseItemDto,
        quality: String = "high",
        userIdOverride: String? = null,
    ): OfflineDownloadItem {
        val uid = resolveUserId(userIdOverride)
        val existing = getDownload(item.Id, uid)
        if (existing != null && existing.status != OfflineDownloadItem.STATUS_FAILED) {
            if (existing.status == OfflineDownloadItem.STATUS_PAUSED) resumeDownload(existing.id)
            return existing
        }
        val subtitle = if (item.Type == "Episode") {
            listOfNotNull(
                item.SeriesName,
                if (item.ParentIndexNumber != null && item.IndexNumber != null) {
                    "S${item.ParentIndexNumber} E${item.IndexNumber}"
                } else {
                    null
                },
            ).joinToString(" - ")
        } else {
            item.ProductionYear?.toString().orEmpty()
        }
        val entry = OfflineDownloadItem(
            id = normalizeId(item.Id),
            userId = uid,
            kind = "video",
            title = item.Name ?: "Video",
            subtitle = subtitle,
            seriesKey = item.SeriesId?.let(::normalizeId),
            status = OfflineDownloadItem.STATUS_QUEUED,
            videoQuality = quality,
            itemSnapshot = item,
        )
        upsertItem(entry)
        triggerQueueProcessing()
        return entry
    }

    suspend fun queueBookDownload(
        item: BaseItemDto,
        isManga: Boolean = false,
        seriesKey: String? = null,
        streamChapterId: Int? = null,
        streamMangaId: Int? = null,
        userIdOverride: String? = null,
    ): OfflineDownloadItem {
        val uid = resolveUserId(userIdOverride)
        val existing = getDownload(item.Id, uid)
        if (existing != null && existing.status != OfflineDownloadItem.STATUS_FAILED) {
            if (existing.status == OfflineDownloadItem.STATUS_PAUSED) resumeDownload(existing.id)
            return existing
        }
        val kind = if (isManga) "manga" else "book"
        val subtitle = if (isManga) {
            item.SeriesName.orEmpty()
        } else {
            item.People?.firstOrNull()?.Name.orEmpty()
        }
        val entry = OfflineDownloadItem(
            id = normalizeId(item.Id),
            userId = uid,
            kind = kind,
            title = item.Name ?: if (isManga) "Chapter" else "Book",
            subtitle = subtitle,
            seriesKey = seriesKey,
            status = OfflineDownloadItem.STATUS_QUEUED,
            streamChapterId = streamChapterId,
            streamMangaId = streamMangaId,
            itemSnapshot = item,
        )
        upsertItem(entry)
        triggerQueueProcessing()
        return entry
    }

    suspend fun queueAudiobookDownload(
        item: BaseItemDto,
        tracks: List<BaseItemDto>,
        userIdOverride: String? = null,
    ): OfflineDownloadItem {
        val uid = resolveUserId(userIdOverride)
        val existing = getDownload(item.Id, uid)
        if (existing != null && existing.status != OfflineDownloadItem.STATUS_FAILED) {
            if (existing.status == OfflineDownloadItem.STATUS_PAUSED) resumeDownload(existing.id)
            return existing
        }
        val resolvedTracks = tracks.ifEmpty { listOf(item) }
        val trackIds = resolvedTracks.map { normalizeId(it.Id) }
        val audiobookTracks = resolvedTracks.mapIndexed { idx, track ->
            OfflineAudiobookTrack(
                trackId = normalizeId(track.Id),
                title = track.Name ?: "Track ${idx + 1}",
                durationTicks = track.RunTimeTicks ?: 0L,
                indexNumber = track.IndexNumber ?: (idx + 1),
            )
        }
        val entry = OfflineDownloadItem(
            id = normalizeId(item.Id),
            userId = uid,
            kind = "audiobook",
            title = item.Name ?: "Audiobook",
            subtitle = item.AlbumArtist ?: item.Artists?.firstOrNull() ?: item.People?.firstOrNull()?.Name.orEmpty(),
            status = OfflineDownloadItem.STATUS_QUEUED,
            trackIds = trackIds,
            audiobookTracks = audiobookTracks,
            itemSnapshot = item,
            tracksSnapshot = resolvedTracks,
        )
        upsertItem(entry)
        triggerQueueProcessing()
        return entry
    }

    suspend fun pauseDownload(itemId: String) {
        val normalized = normalizeId(itemId)
        activeJobs.remove(normalized)?.cancel()
        updateItem(normalized) { current ->
            if (current.status == OfflineDownloadItem.STATUS_DOWNLOADING || current.status == OfflineDownloadItem.STATUS_QUEUED) {
                current.copy(status = OfflineDownloadItem.STATUS_PAUSED)
            } else {
                current
            }
        }
    }

    suspend fun resumeDownload(itemId: String) {
        val normalized = normalizeId(itemId)
        updateItem(normalized) { current ->
            if (current.status == OfflineDownloadItem.STATUS_PAUSED || current.status == OfflineDownloadItem.STATUS_FAILED) {
                current.copy(status = OfflineDownloadItem.STATUS_QUEUED, errorMessage = null)
            } else {
                current
            }
        }
        triggerQueueProcessing()
    }

    suspend fun cancelDownload(itemId: String) {
        deleteDownload(itemId)
    }

    suspend fun deleteDownload(itemId: String) {
        val normalized = normalizeId(itemId)
        activeJobs.remove(normalized)?.cancel()
        val removed = stateMutex.withLock {
            val current = _downloads.value
            val target = current.firstOrNull { normalizeId(it.id) == normalized }
            val next = current.filterNot { normalizeId(it.id) == normalized }
            _downloads.value = next
            persistManifestLocked(next)
            target
        }
        if (removed != null) {
            withContext(Dispatchers.IO) {
                runCatching { itemDir(removed.userId, removed.id).deleteRecursively() }
            }
        }
    }

    suspend fun onProgressUpdated(itemId: String, progressRatio: Double, kind: String) {
        if (progressRatio < 0.98) return
        val shouldDelete = when (kind) {
            "video" -> autoDeleteWatched
            "book", "manga", "audiobook" -> autoDeleteRead
            else -> false
        }
        if (!shouldDelete) return
        val existing = getDownload(itemId) ?: return
        if (existing.status == OfflineDownloadItem.STATUS_COMPLETED) {
            deleteDownload(existing.id)
        }
    }

    suspend fun saveLocalProgress(userId: String, itemId: String, dto: ReadingProgressDto) {
        val dir = itemDir(userId, normalizeId(itemId))
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            runCatching {
                File(dir, "progress.json").writeText(progressAdapter.toJson(dto))
            }
        }
    }

    suspend fun readLocalProgress(userId: String, itemId: String): ReadingProgressDto? = withContext(Dispatchers.IO) {
        val file = File(itemDir(userId, normalizeId(itemId)), "progress.json")
        if (!file.exists()) return@withContext null
        runCatching { progressAdapter.fromJson(file.readText()) }.getOrNull()
    }

    suspend fun saveLocalAnnotations(userId: String, itemId: String, annotations: List<AnnotationDto>) {
        val dir = itemDir(userId, normalizeId(itemId))
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            runCatching {
                File(dir, "annotations.json").writeText(annotationsAdapter.toJson(annotations))
            }
        }
    }

    suspend fun readLocalAnnotations(userId: String, itemId: String): List<AnnotationDto> = withContext(Dispatchers.IO) {
        val file = File(itemDir(userId, normalizeId(itemId)), "annotations.json")
        if (!file.exists()) return@withContext emptyList()
        runCatching { annotationsAdapter.fromJson(file.readText()) }.getOrNull().orEmpty()
    }

    suspend fun queueMutation(
        method: String,
        path: String,
        bodyJson: String? = null,
        userIdOverride: String? = null,
        kind: String? = null,
        itemId: String? = null,
        trackId: String? = null,
    ) {
        val uid = resolveUserId(userIdOverride)
        val mutation = OfflineSyncMutation(
            userId = uid,
            method = method.uppercase(Locale.US),
            path = path.trimStart('/'),
            bodyJson = bodyJson,
            kind = kind,
            itemId = itemId,
            trackId = trackId,
        )
        stateMutex.withLock {
            val current = readSyncQueueLocked().toMutableList()
            // Coalesce repeated progress saves for the same path.
            if (mutation.method == "POST" && (mutation.path.startsWith("Jellio/reading/progress/") || mutation.path.startsWith("UserItems/"))) {
                current.removeAll { it.userId == uid && it.method == mutation.method && it.path == mutation.path }
            }
            current.add(mutation)
            persistSyncQueueLocked(current)
        }
    }

    suspend fun queueVideoProgressMutation(itemId: String, positionTicks: Long, played: Boolean) {
        val body = UserDataUpdate(PlaybackPositionTicks = if (played) 0L else positionTicks.coerceAtLeast(0L))
        val json = if (played) {
            """{"PlaybackPositionTicks":0,"Played":true}"""
        } else {
            userDataUpdateAdapter.toJson(body)
        }
        queueMutation(
            method = "POST",
            path = "UserItems/${normalizeId(itemId)}/UserData",
            bodyJson = json,
            kind = "video",
            itemId = normalizeId(itemId),
        )
        if (played) {
            onProgressUpdated(itemId, 1.0, "video")
        }
    }

    suspend fun queueAudiobookProgressMutation(
        itemId: String,
        trackId: String?,
        positionTicks: Long,
        played: Boolean = false,
        userIdOverride: String? = null,
    ) {
        val targetId = normalizeId(trackId?.takeIf { it.isNotBlank() } ?: itemId)
        val body = UserDataUpdate(PlaybackPositionTicks = if (played) 0L else positionTicks.coerceAtLeast(0L))
        val json = if (played) {
            """{"PlaybackPositionTicks":0,"Played":true}"""
        } else {
            userDataUpdateAdapter.toJson(body)
        }
        queueMutation(
            method = "POST",
            path = "UserItems/$targetId/UserData",
            bodyJson = json,
            userIdOverride = userIdOverride,
            kind = "audiobook",
            itemId = normalizeId(itemId),
            trackId = targetId,
        )
        if (played) {
            onProgressUpdated(itemId, 1.0, "audiobook")
        }
    }

    suspend fun syncPendingProgress(): Int = replaySyncQueue()

    suspend fun replaySyncQueue(): Int = withContext(Dispatchers.IO) {
        val server = sessionManager.serverAddress() ?: return@withContext 0
        val uid = resolveUserId(null)
        val pending = stateMutex.withLock { readSyncQueueLocked() }.filter { it.userId == uid }
        if (pending.isEmpty()) return@withContext 0

        var sentCount = 0
        val completedSeqs = HashSet<String>()
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        for (entry in pending) {
            val resolvedPath = if (entry.kind == "audiobook" && !entry.itemId.isNullOrBlank()) {
                val targetId = normalizeId(entry.trackId?.takeIf { it.isNotBlank() } ?: entry.itemId)
                "UserItems/$targetId/UserData"
            } else {
                entry.path.trimStart('/')
            }
            val success = try {
                val url = "${server.trimEnd('/')}/$resolvedPath"
                val reqBuilder = Request.Builder().url(url)
                val body = entry.bodyJson?.toRequestBody(jsonMediaType)
                when (entry.method) {
                    "POST" -> reqBuilder.post(body ?: "".toRequestBody(null))
                    "PUT" -> reqBuilder.put(body ?: "".toRequestBody(null))
                    "DELETE" -> if (body != null) reqBuilder.delete(body) else reqBuilder.delete()
                    else -> reqBuilder.method(entry.method, body)
                }
                okHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    resp.isSuccessful || resp.code in 400..499
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                break
            }
            if (!success) break
            completedSeqs.add(entry.seq)
            if (entry.method == "POST") sentCount++
        }

        if (completedSeqs.isNotEmpty()) {
            stateMutex.withLock {
                val remaining = readSyncQueueLocked().filterNot { it.seq in completedSeqs }
                persistSyncQueueLocked(remaining)
            }
        }
        sentCount
    }

    private fun triggerQueueProcessing() {
        synchronized(queueWakeLock) {
            if (processorJob?.isActive == true) return
            processorJob = scope.launch {
                try {
                    while (isActive) {
                        val next = _downloads.value.firstOrNull {
                            it.status == OfflineDownloadItem.STATUS_QUEUED ||
                                (it.status == OfflineDownloadItem.STATUS_DOWNLOADING && !activeJobs.containsKey(it.id))
                        } ?: break

                        val job = scope.launch { executeDownload(next) }
                        activeJobs[next.id] = job
                        job.join()
                        activeJobs.remove(next.id)
                    }
                } finally {
                    synchronized(queueWakeLock) {
                        processorJob = null
                    }
                }
            }
        }
    }

    private suspend fun executeDownload(initial: OfflineDownloadItem) {
        val dir = itemDir(initial.userId, initial.id).apply { mkdirs() }
        updateItem(initial.id) {
            it.copy(status = OfflineDownloadItem.STATUS_DOWNLOADING, errorMessage = null)
        }
        try {
            when (initial.kind) {
                "video" -> downloadVideoItem(initial, dir)
                "book" -> downloadBookItem(initial, dir, isManga = false)
                "manga" -> downloadBookItem(initial, dir, isManga = true)
                "audiobook" -> downloadAudiobookItem(initial, dir)
                else -> throw IllegalArgumentException("Unsupported offline download kind: ${initial.kind}")
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            val current = getDownload(initial.id, initial.userId)
            if (current != null && current.status != OfflineDownloadItem.STATUS_PAUSED) {
                updateItem(initial.id) {
                    it.copy(
                        status = OfflineDownloadItem.STATUS_FAILED,
                        errorMessage = t.message ?: "Download failed",
                    )
                }
            }
        }
    }

    private suspend fun downloadVideoItem(item: OfflineDownloadItem, dir: File) {
        val server = sessionManager.serverAddress()?.trimEnd('/')
            ?: throw IOException("No server configured")
        val quality = item.videoQuality ?: "high"
        val (maxHeight, targetBitrate) = when (quality) {
            "low" -> 480 to 1_000_000L
            "high" -> 720 to 2_500_000L
            "higher" -> 1080 to 5_000_000L
            else -> 0 to 0L
        }

        val playbackInfo = runCatching {
            jellyfinApi.getPlaybackInfo(
                itemId = item.id,
                body = PlaybackInfoRequest(
                    UserId = item.userId,
                    EnableDirectPlay = true,
                    EnableDirectStream = true,
                    EnableTranscoding = true,
                ),
            )
        }.getOrNull()
        val mediaSource = playbackInfo?.MediaSources?.firstOrNull()
        val mediaSourceId = mediaSource?.Id
        val playSessionId = playbackInfo?.PlaySessionId ?: "jellio-offline-${System.currentTimeMillis()}"

        val needsTranscode = quality != "original" && mediaSource?.SupportsDirectPlay != true
        val transcodingUrl = mediaSource?.TranscodingUrl?.takeIf { it.isNotBlank() }
        val videoUrl = when {
            !needsTranscode -> {
                val msQuery = if (!mediaSourceId.isNullOrBlank()) "&MediaSourceId=$mediaSourceId" else ""
                "$server/Videos/${item.id}/stream?static=true$msQuery"
            }
            transcodingUrl != null -> {
                if (transcodingUrl.startsWith("http", ignoreCase = true)) transcodingUrl else "$server/${transcodingUrl.trimStart('/')}"
            }
            else -> {
                val msQuery = if (!mediaSourceId.isNullOrBlank()) "&MediaSourceId=$mediaSourceId" else ""
                "$server/Videos/${item.id}/stream.mp4?VideoCodec=h264&AudioCodec=aac&AudioChannels=2" +
                    "&MaxHeight=$maxHeight&VideoBitrate=$targetBitrate&AudioBitrate=128000" +
                    "&DeviceId=${sessionManager.getCachedDeviceId()}&PlaySessionId=$playSessionId$msQuery"
            }
        }

        var claimedSlotId: String? = null
        var heartbeatJob: Job? = null
        if (needsTranscode) {
            val claimResp = runCatching {
                booksApi.claimConversionSlot(ClaimConversionRequest(ItemId = item.id))
            }.getOrNull()
            if (claimResp?.code() == 429) {
                updateItem(item.id) {
                    it.copy(
                        status = OfflineDownloadItem.STATUS_QUEUED,
                        errorMessage = "Waiting for server conversion slot",
                    )
                }
                delay(15_000L)
                throw IOException("Server conversion slots busy")
            }
            claimedSlotId = claimResp?.body()?.SlotId
            if (!claimedSlotId.isNullOrBlank()) {
                val slotId = claimedSlotId
                heartbeatJob = scope.launch {
                    while (isActive) {
                        delay(20_000L)
                        runCatching { booksApi.heartbeatConversionSlot(slotId) }
                    }
                }
            }
        }

        try {
            val ext = if (needsTranscode) "mp4" else (mediaSource?.Container?.lowercase(Locale.US) ?: "mkv")
            val targetFile = File(dir, "video.$ext")
            streamToFile(
                url = videoUrl,
                dest = targetFile,
                allowResume = !needsTranscode,
            ) { done, total ->
                val progress = if (total > 0L) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                updateProgress(item.id, progress, done, total)
            }

            val coverFile = downloadOptionalCover(
                url = "$server/Items/${item.id}/Images/Primary?maxWidth=600",
                dest = File(dir, "cover.jpg"),
            )

            updateItem(item.id) {
                it.copy(
                    status = OfflineDownloadItem.STATUS_COMPLETED,
                    progress = 1f,
                    bytesDownloaded = targetFile.length(),
                    totalBytes = targetFile.length(),
                    localFilePath = targetFile.absolutePath,
                    coverPath = coverFile?.absolutePath,
                    errorMessage = null,
                )
            }
        } finally {
            heartbeatJob?.cancel()
            if (!claimedSlotId.isNullOrBlank()) {
                runCatching { booksApi.releaseConversionSlot(claimedSlotId) }
            }
        }
    }

    private suspend fun downloadBookItem(item: OfflineDownloadItem, dir: File, isManga: Boolean) {
        val server = sessionManager.serverAddress()?.trimEnd('/')
            ?: throw IOException("No server configured")

        val streamChapterId = item.streamChapterId
        val ext = if (isManga || streamChapterId != null) {
            "cbz"
        } else {
            val rawExt = item.itemSnapshot?.Path?.substringAfterLast('.', "")?.lowercase(Locale.US)
            when (rawExt) {
                "epub", "pdf", "cbz" -> rawExt
                "cbr", "cb7", "cbt" -> "cbz"
                else -> "bin"
            }
        }
        val targetFile = File(dir, "book.$ext")
        val fileUrl = if (streamChapterId != null) {
            "$server/Jellio/manga/stream/chapter/$streamChapterId/cbz"
        } else {
            "$server/Jellio/reading/file/${item.id}"
        }

        streamToFile(
            url = fileUrl,
            dest = targetFile,
            allowResume = streamChapterId == null,
        ) { done, total ->
            val progress = if (total > 0L) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
            updateProgress(item.id, progress, done, total)
        }

        // Rename .bin if Content-Type / magic bytes reveal epub/pdf/cbz
        val finalFile = refineBookExtensionIfNeeded(targetFile)

        val coverUrl = if (item.streamMangaId != null) {
            "$server/Jellio/manga/thumbnail/${item.streamMangaId}"
        } else if (isManga) {
            "$server/Jellio/manga/series-cover/${item.id}"
        } else {
            "$server/Items/${item.id}/Images/Primary?maxWidth=600"
        }
        val coverFile = downloadOptionalCover(coverUrl, File(dir, "cover.jpg"))

        // Cache current progress and annotations for offline reading.
        runCatching {
            val remoteProgress = booksApi.getReadingProgress(item.id)
            if (remoteProgress.Locator.isNotBlank()) {
                saveLocalProgress(item.userId, item.id, remoteProgress)
            }
        }
        if (streamChapterId == null) {
            runCatching {
                val remoteAnnotations = booksApi.getAnnotations(item.id)
                saveLocalAnnotations(item.userId, item.id, remoteAnnotations)
            }
        }

        updateItem(item.id) {
            it.copy(
                status = OfflineDownloadItem.STATUS_COMPLETED,
                progress = 1f,
                bytesDownloaded = finalFile.length(),
                totalBytes = finalFile.length(),
                localFilePath = finalFile.absolutePath,
                coverPath = coverFile?.absolutePath,
                errorMessage = null,
            )
        }
    }

    private suspend fun downloadAudiobookItem(item: OfflineDownloadItem, dir: File) {
        val server = sessionManager.serverAddress()?.trimEnd('/')
            ?: throw IOException("No server configured")

        val tracks = item.tracksSnapshot.ifEmpty {
            if (item.audiobookTracks.isNotEmpty()) {
                item.audiobookTracks.mapIndexed { idx, t ->
                    BaseItemDto(
                        Id = t.trackId,
                        Name = t.title.ifBlank { "Track ${idx + 1}" },
                        RunTimeTicks = t.durationTicks.takeIf { it > 0L },
                        IndexNumber = t.indexNumber ?: (idx + 1),
                    )
                }
            } else {
                listOf(BaseItemDto(Id = item.id, Name = item.title))
            }
        }
        val trackCount = tracks.size.coerceAtLeast(1)
        val savedPaths = mutableListOf<String>()
        val savedTrackMeta = mutableListOf<OfflineAudiobookTrack>()
        var cumulativeBytes = 0L

        for ((index, track) in tracks.withIndex()) {
            val normalizedTrackId = normalizeId(track.Id)
            val trackFile = File(dir, "track-$normalizedTrackId.audio")
            val url = "$server/Audio/${track.Id}/stream?static=true"
            streamToFile(
                url = url,
                dest = trackFile,
                allowResume = true,
            ) { done, total ->
                val trackProgress = if (total > 0L) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                val overall = ((index.toFloat() + trackProgress) / trackCount.toFloat()).coerceIn(0f, 1f)
                updateProgress(item.id, overall, cumulativeBytes + done, 0L)
            }
            cumulativeBytes += trackFile.length()
            savedPaths.add(trackFile.absolutePath)
            savedTrackMeta.add(
                OfflineAudiobookTrack(
                    trackId = normalizedTrackId,
                    title = track.Name ?: "Track ${index + 1}",
                    durationTicks = track.RunTimeTicks ?: 0L,
                    indexNumber = track.IndexNumber ?: (index + 1),
                    localFilePath = trackFile.absolutePath,
                ),
            )
        }

        val coverId = item.itemSnapshot?.AlbumId ?: item.id
        val coverFile = downloadOptionalCover(
            url = "$server/Items/$coverId/Images/Primary?maxWidth=600",
            dest = File(dir, "cover.jpg"),
        )

        updateItem(item.id) {
            it.copy(
                status = OfflineDownloadItem.STATUS_COMPLETED,
                progress = 1f,
                bytesDownloaded = cumulativeBytes,
                totalBytes = cumulativeBytes,
                localFilePath = savedPaths.firstOrNull(),
                coverPath = coverFile?.absolutePath,
                trackPaths = savedPaths,
                audiobookTracks = savedTrackMeta,
                errorMessage = null,
            )
        }
    }

    private suspend fun streamToFile(
        url: String,
        dest: File,
        allowResume: Boolean,
        onProgress: suspend (bytesDownloaded: Long, totalBytes: Long) -> Unit,
    ) {
        val partFile = File(dest.parentFile, "${dest.name}.part")
        val existingBytes = if (allowResume && partFile.exists()) partFile.length() else 0L
        if (!allowResume && partFile.exists()) {
            partFile.delete()
        }

        val reqBuilder = Request.Builder().url(url)
        if (existingBytes > 0L) {
            reqBuilder.header("Range", "bytes=$existingBytes-")
        }

        withContext(Dispatchers.IO) {
            downloadClient.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) {
                    throw IOException("HTTP ${response.code} downloading ${dest.name}")
                }
                val isResumed = response.code == 206 && existingBytes > 0L
                val body = response.body ?: throw IOException("Empty response body")
                val contentLength = body.contentLength()
                val totalBytes = when {
                    isResumed && contentLength > 0L -> existingBytes + contentLength
                    contentLength > 0L -> contentLength
                    else -> 0L
                }
                var downloaded = if (isResumed) existingBytes else 0L
                var lastEmit = 0L

                body.byteStream().use { input ->
                    FileOutputStream(partFile, isResumed).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastEmit >= 350L) {
                                lastEmit = now
                                onProgress(downloaded, totalBytes)
                            }
                        }
                        output.flush()
                    }
                }
                onProgress(downloaded, totalBytes)
            }

            if (dest.exists()) dest.delete()
            if (!partFile.renameTo(dest)) {
                partFile.copyTo(dest, overwrite = true)
                partFile.delete()
            }
        }
    }

    private suspend fun downloadOptionalCover(url: String, dest: File): File? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(url).build()
            downloadClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val bytes = resp.body?.bytes() ?: return@runCatching null
                if (bytes.isEmpty()) return@runCatching null
                dest.writeBytes(bytes)
                dest
            }
        }.getOrNull()
    }

    private fun refineBookExtensionIfNeeded(file: File): File {
        if (!file.name.endsWith(".bin") || !file.exists() || file.length() < 4) return file
        val header = ByteArray(4)
        runCatching {
            file.inputStream().use { it.read(header) }
        }.getOrNull() ?: return file

        val newExt = when {
            header[0] == '%'.code.toByte() && header[1] == 'P'.code.toByte() &&
                header[2] == 'D'.code.toByte() && header[3] == 'F'.code.toByte() -> "pdf"
            header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte() -> {
                val isEpub = runCatching {
                    java.util.zip.ZipFile(file).use { z ->
                        val hasContainer = z.getEntry("META-INF/container.xml") != null
                        val mimeEntry = z.getEntry("mimetype")
                        val hasEpubMime = mimeEntry != null &&
                            z.getInputStream(mimeEntry).use { it.readBytes().toString(Charsets.UTF_8) }
                                .contains("epub", ignoreCase = true)
                        hasContainer || hasEpubMime
                    }
                }.getOrDefault(false)
                if (isEpub) "epub" else "cbz"
            }
            else -> return file
        }
        val renamed = File(file.parentFile, "book.$newExt")
        return if (file.renameTo(renamed)) renamed else file
    }

    private suspend fun updateProgress(itemId: String, progress: Float, downloaded: Long, total: Long) {
        val normalized = normalizeId(itemId)
        val currentList = _downloads.value
        val next = currentList.map { entry ->
            if (normalizeId(entry.id) == normalized) {
                entry.copy(
                    progress = progress,
                    bytesDownloaded = downloaded,
                    totalBytes = if (total > 0L) total else entry.totalBytes,
                )
            } else {
                entry
            }
        }
        _downloads.value = next
    }

    private suspend fun upsertItem(item: OfflineDownloadItem) {
        val normalized = normalizeId(item.id)
        stateMutex.withLock {
            val next = _downloads.value.filterNot { normalizeId(it.id) == normalized } + item
            _downloads.value = next.sortedByDescending { it.createdAt }
            persistManifestLocked(_downloads.value)
        }
    }

    private suspend fun updateItem(itemId: String, transform: (OfflineDownloadItem) -> OfflineDownloadItem) {
        val normalized = normalizeId(itemId)
        stateMutex.withLock {
            var changed = false
            val next = _downloads.value.map { entry ->
                if (normalizeId(entry.id) == normalized) {
                    changed = true
                    transform(entry)
                } else {
                    entry
                }
            }
            if (changed) {
                _downloads.value = next
                persistManifestLocked(next)
            }
        }
    }

    private fun loadManifestSync() {
        if (!manifestFile.exists()) return
        runCatching {
            val loaded = manifestAdapter.fromJson(manifestFile.readText())?.items.orEmpty()
            // Any in-flight downloads from a previous app run return to queued.
            val sanitized = loaded.map { entry ->
                if (entry.status == OfflineDownloadItem.STATUS_DOWNLOADING) {
                    entry.copy(status = OfflineDownloadItem.STATUS_QUEUED)
                } else {
                    entry
                }
            }
            _downloads.value = sanitized.sortedByDescending { it.createdAt }
        }
    }

    private fun persistManifestLocked(items: List<OfflineDownloadItem>) {
        runCatching {
            rootDir.mkdirs()
            val tmp = File(rootDir, "downloads.json.tmp")
            tmp.writeText(manifestAdapter.toJson(OfflineDownloadManifest(items)))
            tmp.renameTo(manifestFile)
        }
    }

    private fun readSyncQueueLocked(): List<OfflineSyncMutation> {
        if (!syncQueueFile.exists()) return emptyList()
        return runCatching { queueAdapter.fromJson(syncQueueFile.readText())?.mutations.orEmpty() }
            .getOrDefault(emptyList())
    }

    private fun persistSyncQueueLocked(mutations: List<OfflineSyncMutation>) {
        runCatching {
            rootDir.mkdirs()
            val tmp = File(rootDir, "offline_queue.json.tmp")
            tmp.writeText(queueAdapter.toJson(OfflineSyncQueueManifest(mutations)))
            tmp.renameTo(syncQueueFile)
        }
    }

    private fun resolveUserId(override: String?): String {
        if (!override.isNullOrBlank()) return normalizeId(override)
        return cachedUserId?.takeIf { it.isNotBlank() }?.let(::normalizeId) ?: "default"
    }

    private fun sanitizeSegment(raw: String): String =
        raw.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "default" }

    private fun normalizeId(raw: String): String =
        raw.replace("-", "").lowercase(Locale.US)

    companion object {
        fun isoNowUtc(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            return sdf.format(Date())
        }
    }
}
