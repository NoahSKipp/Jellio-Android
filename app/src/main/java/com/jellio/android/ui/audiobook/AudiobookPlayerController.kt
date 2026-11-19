package com.jellio.android.ui.audiobook

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.snapshotFlow
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import com.jellio.android.MainActivity
import com.jellio.android.data.JellioRepository
import com.jellio.android.data.PlaybackRepository
import com.jellio.android.data.books.AnnotationDto
import com.jellio.android.data.books.AudiobookChapterDto
import com.jellio.android.data.books.BooksRepository
import com.jellio.android.data.books.CreateAnnotationRequest
import com.jellio.android.data.books.ReadingSessionRequest
import com.jellio.android.data.catching
import com.jellio.android.data.model.BaseItemDto
import com.jellio.android.data.network.APP_VERSION
import com.jellio.android.data.network.buildEmbyAuthorizationHeader
import com.jellio.android.data.offline.OfflineDownloadItem
import com.jellio.android.data.offline.OfflineDownloadManager
import com.jellio.android.data.session.SessionManager
import com.jellio.android.ui.player.PlayerPresence
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

private const val TICKS_PER_MS = 10_000L
private const val TICKS_PER_SEC = 10_000_000.0
private const val PREFS_NAME = "jellio_audiobook_prefs"
private const val KEY_SPEED_PREFIX = "speed:"
private const val KEY_RESUME_PREFIX = "resume:"
private const val KEY_CHAPTERS_PREFIX = "chapters:"
private const val LISTEN_FLUSH_SECONDS = 300.0
private const val PROGRESS_REPORT_INTERVAL_MS = 10_000L
private const val POSITION_TICK_MS = 400L
private const val FADE_WINDOW_MS = 15_000L
private const val RESTART_CHAPTER_THRESHOLD_MS = 5_000L

@Immutable
data class AudiobookPlaybackState(
    val isActive: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val bookItem: BaseItemDto? = null,
    val tracks: List<BaseItemDto> = emptyList(),
    val chapters: List<AudiobookChapterDto> = emptyList(),
    val currentChapterIndex: Int = 0,
    val currentTrackIndex: Int = 0,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val sleepTimerRemainingSec: Int? = null,
    val sleepStopAtChapterEnd: Boolean = false,
    val coverUrl: String? = null,
    val author: String = "",
    val offlineDownload: OfflineDownloadItem? = null,
    val bookmarks: List<AnnotationDto> = emptyList(),
)

typealias AudiobookPlayerState = AudiobookPlaybackState
typealias BookAnnotation = AnnotationDto

@Singleton
class AudiobookPlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val booksRepository: BooksRepository,
    private val jellioRepository: JellioRepository,
    private val playbackRepository: PlaybackRepository,
    private val offlineDownloadManager: OfflineDownloadManager,
    private val sessionManager: SessionManager,
    private val moshi: Moshi,
) {
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("AudiobookPlayer", "Unhandled coroutine error", throwable)
    }
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + exceptionHandler)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val chaptersListType = Types.newParameterizedType(List::class.java, AudiobookChapterDto::class.java)
    private val chaptersAdapter by lazy { moshi.adapter<List<AudiobookChapterDto>>(chaptersListType) }

    private val _state = MutableStateFlow(AudiobookPlaybackState())
    val state: StateFlow<AudiobookPlaybackState> = _state.asStateFlow()

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var playbackService: AudiobookPlaybackService? = null

    private var loadJob: Job? = null
    private var positionTickerJob: Job? = null
    private var progressReportJob: Job? = null
    private var sleepTimerJob: Job? = null

    private var trackStartsMs: LongArray = LongArray(0)
    private var trackDurationsMs: LongArray = LongArray(0)

    private var currentTrackIndex: Int = 0
    private var currentTrackIsLocal: Boolean = false
    private var usingTranscodeFallback: Boolean = false
    private var reportedTrackId: String? = null
    private var pendingSeekMs: Long = 0L

    private var listenedSecondsAccum: Double = 0.0
    private var lastPositionSampleAtMs: Long = 0L
    private var finishedReported: Boolean = false

    private var sleepDeadlineMs: Long? = null
    private var sleepArmedChapterIndex: Int? = null
    private var baseVolume: Float = 1.0f

    init {
        mainScope.launch {
            offlineDownloadManager.downloads.collect { list ->
                val book = _state.value.bookItem ?: return@collect
                val normalized = BooksRepository.normalizeId(book.Id)
                val match = list.firstOrNull { entry ->
                    BooksRepository.normalizeId(entry.id) == normalized ||
                        entry.trackIds.any { BooksRepository.normalizeId(it) == normalized }
                }
                if (match != _state.value.offlineDownload) {
                    _state.value = _state.value.copy(offlineDownload = match)
                }
            }
        }
        mainScope.launch {
            snapshotFlow { PlayerPresence.active }.collect { videoActive ->
                if (videoActive && player?.isPlaying == true) {
                    player?.pause()
                }
            }
        }
    }

    fun loadAndPlayById(itemId: String) {
        val currentBook = _state.value.bookItem
        if (
            _state.value.isActive &&
            currentBook != null &&
            BooksRepository.normalizeId(currentBook.Id) == BooksRepository.normalizeId(itemId)
        ) {
            return
        }
        loadJob?.cancel()
        loadJob = mainScope.launch {
            _state.value = _state.value.copy(
                isActive = true,
                isLoading = true,
                error = null,
            )
            val resolved = withContext(Dispatchers.IO) {
                val userId = booksRepository.currentUserId()
                val offlineItem = offlineDownloadManager.getDownload(itemId, userId)?.itemSnapshot
                val remote = if (!userId.isNullOrBlank()) {
                    catching { jellioRepository.getItemDetails(userId, itemId) }.getOrNull()
                        ?: catching { jellioRepository.getItem(userId, itemId) }.getOrNull()
                } else {
                    null
                }
                remote ?: offlineItem
            }
            if (resolved != null) {
                loadAndPlayInternal(resolved)
            } else {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Could not load audiobook details",
                )
            }
        }
    }

    fun loadAndPlay(book: BaseItemDto) {
        val currentBook = _state.value.bookItem
        if (
            _state.value.isActive &&
            currentBook != null &&
            BooksRepository.normalizeId(currentBook.Id) == BooksRepository.normalizeId(book.Id) &&
            !_state.value.isLoading &&
            _state.value.error == null
        ) {
            if (player?.isPlaying != true) {
                player?.play()
            }
            return
        }
        loadJob?.cancel()
        loadJob = mainScope.launch {
            loadAndPlayInternal(book)
        }
    }

    private suspend fun loadAndPlayInternal(book: BaseItemDto) {
        if (_state.value.bookItem != null) {
            teardownSession(resetState = false)
        }

        _state.value = AudiobookPlaybackState(
            isActive = true,
            isLoading = true,
            bookItem = book,
            author = resolveAuthor(book),
        )

        val userId = withContext(Dispatchers.IO) { booksRepository.currentUserId() } ?: "default"
        val offlineRecord = offlineDownloadManager.getDownload(book.Id, userId)
        val tracks = withContext(Dispatchers.IO) {
            val loaded = runCatching { booksRepository.getAudiobookTracks(userId, book) }.getOrNull().orEmpty()
            val isSyntheticSingleZero = loaded.size <= 1 && (loaded.firstOrNull()?.RunTimeTicks ?: 0L) <= 0L
            when {
                offlineRecord != null && offlineRecord.audiobookTracks.isNotEmpty() && (loaded.isEmpty() || isSyntheticSingleZero) -> {
                    offlineRecord.audiobookTracks.mapIndexed { idx, t ->
                        BaseItemDto(
                            Id = t.trackId,
                            Name = t.title.ifBlank { "Track ${t.indexNumber ?: (idx + 1)}" },
                            Type = "AudioBook",
                            RunTimeTicks = t.durationTicks.takeIf { it > 0L },
                            IndexNumber = t.indexNumber ?: (idx + 1),
                            ParentId = book.ParentId,
                            Album = book.Album ?: book.Name,
                            AlbumId = book.AlbumId ?: book.Id,
                            AlbumArtist = book.AlbumArtist,
                        )
                    }
                }
                offlineRecord != null && offlineRecord.tracksSnapshot.isNotEmpty() && (loaded.isEmpty() || isSyntheticSingleZero) -> {
                    offlineRecord.tracksSnapshot
                }
                loaded.isNotEmpty() -> loaded
                offlineRecord != null && offlineRecord.tracksSnapshot.isNotEmpty() -> offlineRecord.tracksSnapshot
                else -> listOf(book)
            }
        }

        if (tracks.isEmpty()) {
            _state.value = _state.value.copy(
                isLoading = false,
                error = "No playable audio tracks found",
            )
            return
        }

        val normalizedTitle = BooksRepository.audiobookTitle(book, tracks.size).ifBlank { book.Name ?: "Audiobook" }
        val resolvedBook = if (normalizedTitle != book.Name) book.copy(Name = normalizedTitle) else book

        val apiChapters = withContext(Dispatchers.IO) {
            val remote = booksRepository.getAudiobookChapters(resolvedBook.Id)
            if (remote.isNotEmpty()) {
                saveCachedChapters(resolvedBook.Id, remote)
                remote
            } else {
                readCachedChapters(resolvedBook.Id)
            }
        }

        val initialBookmarks = withContext(Dispatchers.IO) {
            booksRepository.getAnnotations(resolvedBook.Id).filter { it.Kind == "bookmark" }
        }

        val cover = withContext(Dispatchers.IO) { resolveCoverUrl(resolvedBook, userId) }
        val speed = readBookSpeed(resolvedBook)

        rebuildTrackOffsets(tracks)
        val chapters = buildChapters(tracks, apiChapters)
        val totalMs = computeTotalDurationMs(chapters)
        val (resumeTrackIdx, resumeOffsetMs) = resolveResumePoint(resolvedBook, tracks)

        val initialBookPosMs = (trackStartsMs.getOrElse(resumeTrackIdx) { 0L } + resumeOffsetMs)
            .coerceIn(0L, totalMs.coerceAtLeast(0L))
        val initialChapterIdx = chapterIndexAt(chapters, initialBookPosMs)

        finishedReported = totalMs > 0L && initialBookPosMs >= (totalMs * 0.98).toLong()
        listenedSecondsAccum = 0.0
        lastPositionSampleAtMs = 0L

        _state.value = AudiobookPlaybackState(
            isActive = true,
            isLoading = false,
            error = null,
            bookItem = resolvedBook,
            tracks = tracks,
            chapters = chapters,
            currentChapterIndex = initialChapterIdx,
            currentTrackIndex = resumeTrackIdx,
            isPlaying = false,
            isBuffering = true,
            currentPositionMs = initialBookPosMs,
            totalDurationMs = totalMs,
            playbackSpeed = speed,
            sleepTimerRemainingSec = null,
            sleepStopAtChapterEnd = false,
            coverUrl = cover,
            author = resolveAuthor(resolvedBook),
            offlineDownload = offlineRecord,
            bookmarks = initialBookmarks,
        )

        ensurePlayerCreated(speed)
        startBackgroundTickers()
        loadTrack(index = resumeTrackIdx, offsetMs = resumeOffsetMs, autoPlay = true)
        startPlaybackForegroundService()
    }

    fun togglePlayPause() {
        mainScope.launch {
            val p = player ?: return@launch
            if (p.isPlaying) {
                p.pause()
            } else {
                if (p.playbackState == Player.STATE_ENDED) {
                    loadTrack(0, 0L, autoPlay = true)
                } else {
                    p.play()
                }
            }
        }
    }

    fun skipForward30s() {
        mainScope.launch {
            val target = _state.value.currentPositionMs + 30_000L
            seekToBookPositionMs(target)
        }
    }

    fun skipBackward15s() {
        mainScope.launch {
            val target = (_state.value.currentPositionMs - 15_000L).coerceAtLeast(0L)
            seekToBookPositionMs(target)
        }
    }

    fun seekToBookPositionMs(ms: Long) {
        mainScope.launch {
            val current = _state.value
            val tracks = current.tracks
            if (tracks.isEmpty()) return@launch

            val maxPos = (current.totalDurationMs - 250L).coerceAtLeast(0L)
            val clamped = ms.coerceIn(0L, maxPos)

            var targetTrack = tracks.lastIndex
            for (i in tracks.indices) {
                val endMs = trackStartsMs[i] + trackDurationsMs[i]
                if (clamped < endMs || i == tracks.lastIndex) {
                    targetTrack = i
                    break
                }
            }
            val offsetMs = (clamped - trackStartsMs[targetTrack]).coerceAtLeast(0L)
            val p = player ?: return@launch

            lastPositionSampleAtMs = 0L
            if (targetTrack == currentTrackIndex && p.playbackState != Player.STATE_IDLE) {
                p.seekTo(offsetMs)
                updatePositionState(clamped)
            } else {
                loadTrack(targetTrack, offsetMs, autoPlay = p.playWhenReady || p.isPlaying)
            }
        }
    }

    fun selectChapter(index: Int) {
        mainScope.launch {
            val current = _state.value
            val chapters = current.chapters
            if (index !in chapters.indices) return@launch
            val chapter = chapters[index]
            val tracks = current.tracks
            val p = player

            val trackId = chapter.TrackId
            val matchingTrackIdx = if (!trackId.isNullOrBlank()) {
                val normalizedTarget = BooksRepository.normalizeId(trackId)
                tracks.indexOfFirst { BooksRepository.normalizeId(it.Id) == normalizedTarget }
            } else {
                -1
            }

            if (matchingTrackIdx >= 0) {
                val offsetMs = (chapter.TrackOffsetSeconds * 1000.0).toLong().coerceAtLeast(0L)
                lastPositionSampleAtMs = 0L
                if (matchingTrackIdx == currentTrackIndex && p != null && p.playbackState != Player.STATE_IDLE) {
                    p.seekTo(offsetMs)
                    val bookPos = trackStartsMs.getOrElse(matchingTrackIdx) { 0L } + offsetMs
                    updatePositionState(bookPos)
                } else {
                    loadTrack(matchingTrackIdx, offsetMs, autoPlay = p?.playWhenReady ?: true)
                }
            } else {
                seekToBookPositionMs((chapter.StartSeconds * 1000.0).toLong())
            }
        }
    }

    fun nextChapter() {
        val current = _state.value
        if (current.chapters.isEmpty()) return
        val next = (current.currentChapterIndex + 1).coerceAtMost(current.chapters.lastIndex)
        selectChapter(next)
    }

    fun previousChapter() {
        val current = _state.value
        if (current.chapters.isEmpty()) return
        val chapter = current.chapters.getOrNull(current.currentChapterIndex)
        val chapterStartMs = ((chapter?.StartSeconds ?: 0.0) * 1000.0).toLong()
        val target = if (current.currentPositionMs - chapterStartMs > RESTART_CHAPTER_THRESHOLD_MS) {
            current.currentChapterIndex
        } else {
            (current.currentChapterIndex - 1).coerceAtLeast(0)
        }
        selectChapter(target)
    }

    fun setSpeed(speed: Float) {
        mainScope.launch {
            val clamped = speed.coerceIn(0.75f, 2.0f)
            player?.playbackParameters = PlaybackParameters(clamped)
            _state.value.bookItem?.let { saveBookSpeed(it, clamped) }
            _state.value = _state.value.copy(playbackSpeed = clamped)
        }
    }

    /**
     * Configures the sleep timer:
     * - 0: turn off
     * - 15, 30, 45, 60: pause after [minutes] minutes with a 15-second fade-out
     * - -1: pause at the end of the current chapter with a 15-second fade-out
     */
    fun setSleepTimer(minutes: Int) {
        mainScope.launch {
            sleepTimerJob?.cancel()
            sleepTimerJob = null
            sleepDeadlineMs = null
            sleepArmedChapterIndex = null
            restoreVolume()

            when {
                minutes == -1 -> {
                    sleepArmedChapterIndex = _state.value.currentChapterIndex
                    _state.value = _state.value.copy(
                        sleepTimerRemainingSec = null,
                        sleepStopAtChapterEnd = true,
                    )
                    startSleepTicker()
                }
                minutes > 0 -> {
                    val totalMs = minutes * 60_000L
                    sleepDeadlineMs = System.currentTimeMillis() + totalMs
                    _state.value = _state.value.copy(
                        sleepTimerRemainingSec = minutes * 60,
                        sleepStopAtChapterEnd = false,
                    )
                    startSleepTicker()
                }
                else -> {
                    _state.value = _state.value.copy(
                        sleepTimerRemainingSec = null,
                        sleepStopAtChapterEnd = false,
                    )
                }
            }
        }
    }

    fun stopAndDismiss() {
        mainScope.launch {
            loadJob?.cancel()
            loadJob = null
            teardownSession(resetState = true)
        }
    }

    fun queueOfflineDownload() {
        val current = _state.value
        val book = current.bookItem ?: return
        val tracks = current.tracks
        mainScope.launch {
            offlineDownloadManager.queueAudiobookDownload(book, tracks)
        }
    }

    fun pauseOfflineDownload() {
        val dl = _state.value.offlineDownload ?: return
        mainScope.launch {
            offlineDownloadManager.pauseDownload(dl.id)
        }
    }

    fun resumeOfflineDownload() {
        val dl = _state.value.offlineDownload ?: return
        mainScope.launch {
            offlineDownloadManager.resumeDownload(dl.id)
        }
    }

    fun deleteOfflineDownload() {
        val dl = _state.value.offlineDownload ?: return
        mainScope.launch {
            offlineDownloadManager.deleteDownload(dl.id)
        }
    }

    fun addBookmark(note: String? = null) {
        val current = _state.value
        val book = current.bookItem ?: return
        val positionSec = (current.currentPositionMs / 1000.0).coerceAtLeast(0.0)
        val totalSec = (current.totalDurationMs / 1000.0).coerceAtLeast(1.0)
        val progress = (positionSec / totalSec).coerceIn(0.0, 1.0)
        val currentChapterTitle = current.chapters.getOrNull(current.currentChapterIndex)?.Title?.takeIf { it.isNotBlank() }
            ?: "Chapter ${current.currentChapterIndex + 1}"
        val clockText = formatDurationClock(positionSec)

        mainScope.launch {
            val created = withContext(Dispatchers.IO) {
                booksRepository.addAnnotation(
                    itemId = book.Id,
                    request = CreateAnnotationRequest(
                        Kind = "bookmark",
                        Locator = "audio:${positionSec.toInt()}",
                        Position = progress,
                        Text = clockText,
                        Note = note?.trim()?.ifBlank { null },
                        Chapter = currentChapterTitle,
                    ),
                )
            }
            _state.value = _state.value.copy(
                bookmarks = (_state.value.bookmarks + created).sortedBy { parseBookmarkSeconds(it.Locator) },
            )
        }
    }

    fun deleteBookmark(annotationId: String) {
        val book = _state.value.bookItem ?: return
        mainScope.launch {
            withContext(Dispatchers.IO) {
                booksRepository.deleteAnnotation(book.Id, annotationId)
            }
            _state.value = _state.value.copy(
                bookmarks = _state.value.bookmarks.filterNot { it.Id == annotationId },
            )
        }
    }

    fun jumpToBookmark(bookmark: AnnotationDto) {
        val sec = parseBookmarkSeconds(bookmark.Locator)
        if (sec >= 0L) {
            seekToBookPositionMs(sec * 1000L)
        } else if (bookmark.Position > 0.0 && _state.value.totalDurationMs > 0L) {
            seekToBookPositionMs((_state.value.totalDurationMs * bookmark.Position).toLong())
        }
    }

    fun attachPlaybackService(service: AudiobookPlaybackService) {
        playbackService = service
        service.syncSession(mediaSession)
    }

    fun detachPlaybackService(service: AudiobookPlaybackService) {
        if (playbackService === service) {
            playbackService = null
        }
    }

    fun getActiveMediaSession(): MediaSession? = mediaSession

    fun getOrCreateMediaSession(): MediaSession? {
        ensurePlayerCreated(_state.value.playbackSpeed)
        return mediaSession
    }

    private fun startPlaybackForegroundService() {
        val session = mediaSession
        val service = playbackService
        if (service != null) {
            service.syncSession(session)
            return
        }
        val intent = Intent(context, AudiobookPlaybackService::class.java)
        runCatching {
            context.startService(intent)
        }.onFailure { err ->
            Log.w("AudiobookPlayer", "startService failed, retrying foreground service", err)
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun ensurePlayerCreated(speed: Float) {
        if (player != null) {
            player?.playbackParameters = PlaybackParameters(speed)
            return
        }

        val token = sessionManager.getCachedAccessToken().orEmpty()
        val deviceId = sessionManager.getCachedDeviceId()
        val httpFactory = OkHttpDataSource.Factory(playbackRepository.streamClient)
            .setUserAgent("Jellio-Android/$APP_VERSION")
            .apply {
                if (token.isNotEmpty()) {
                    setDefaultRequestProperties(
                        mapOf(
                            "X-Emby-Token" to token,
                            "Authorization" to buildEmbyAuthorizationHeader(deviceId, APP_VERSION, token),
                        ),
                    )
                }
            }
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()

        val exo = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(15_000L)
            .setSeekForwardIncrementMs(30_000L)
            .build()
            .apply {
                setWakeMode(C.WAKE_MODE_NETWORK)
                playbackParameters = PlaybackParameters(speed)
                volume = baseVolume
                addListener(playerListener)
            }

        val forwardingPlayer = object : ForwardingPlayer(exo) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands()
                    .buildUpon()
                    .addAll(
                        Player.COMMAND_SEEK_TO_NEXT,
                        Player.COMMAND_SEEK_TO_PREVIOUS,
                        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                        Player.COMMAND_SEEK_BACK,
                        Player.COMMAND_SEEK_FORWARD,
                    )
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    Player.COMMAND_SEEK_BACK,
                    Player.COMMAND_SEEK_FORWARD,
                    -> true
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun seekToNext() {
                nextChapter()
            }

            override fun seekToNextMediaItem() {
                nextChapter()
            }

            override fun seekToPrevious() {
                previousChapter()
            }

            override fun seekToPreviousMediaItem() {
                previousChapter()
            }

            override fun seekBack() {
                skipBackward15s()
            }

            override fun seekForward() {
                skipForward30s()
            }
        }

        val sessionActivityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_AUDIOBOOK, true)
        }
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingIntent = PendingIntent.getActivity(context, 1001, sessionActivityIntent, pendingFlags)

        player = exo
        mediaSession = runCatching {
            MediaSession.Builder(context, forwardingPlayer)
                .setId("jellio_audiobook")
                .setSessionActivity(pendingIntent)
                .build()
        }.getOrNull()
        playbackService?.syncSession(mediaSession)
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(isPlaying = isPlaying)
            if (isPlaying) {
                lastPositionSampleAtMs = System.currentTimeMillis()
                startPlaybackForegroundService()
            } else {
                sampleListeningTime(forceReset = true)
                saveLocalResumePosition()
                val trackId = reportedTrackId
                val bookId = _state.value.bookItem?.Id
                val trackPosTicks = (player?.currentPosition ?: 0L).coerceAtLeast(0L) * TICKS_PER_MS
                if (trackId != null) {
                    mainScope.launch(Dispatchers.IO) {
                        val res = catching {
                            jellioRepository.reportPlaybackProgress(trackId, trackId, trackPosTicks, isPaused = true)
                        }
                        if (res.isFailure && !bookId.isNullOrBlank()) {
                            offlineDownloadManager.queueAudiobookProgressMutation(
                                itemId = bookId,
                                trackId = trackId,
                                positionTicks = trackPosTicks,
                                played = false,
                            )
                        }
                    }
                }
                flushListeningSession(finishedBook = false)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _state.value = _state.value.copy(isBuffering = true)
                }
                Player.STATE_READY -> {
                    _state.value = _state.value.copy(isBuffering = false, error = null)
                    val exoDuration = player?.duration ?: C.TIME_UNSET
                    if (exoDuration != C.TIME_UNSET && exoDuration > 0L) {
                        updateTrackDurationIfUnknown(currentTrackIndex, exoDuration)
                    }
                    if (pendingSeekMs > 0L) {
                        val target = pendingSeekMs
                        pendingSeekMs = 0L
                        player?.seekTo(target)
                    }
                    reportTrackStartIfNeeded()
                    refreshPositionFromPlayer()
                }
                Player.STATE_ENDED -> {
                    _state.value = _state.value.copy(isBuffering = false)
                    handleTrackEnded()
                }
                Player.STATE_IDLE -> Unit
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (!currentTrackIsLocal && !usingTranscodeFallback) {
                val track = _state.value.tracks.getOrNull(currentTrackIndex)
                if (track != null) {
                    usingTranscodeFallback = true
                    val resumeAt = (player?.currentPosition ?: 0L).coerceAtLeast(pendingSeekMs)
                    mainScope.launch {
                        val fallbackUrl = withContext(Dispatchers.IO) { buildTranscodeUrl(track.Id) }
                        if (fallbackUrl != null) {
                            prepareTrackUri(
                                track = track,
                                uriString = fallbackUrl,
                                offsetMs = resumeAt,
                                autoPlay = player?.playWhenReady ?: true,
                            )
                        } else {
                            _state.value = _state.value.copy(
                                isBuffering = false,
                                error = error.localizedMessage ?: "Audio playback error",
                            )
                        }
                    }
                    return
                }
            }
            _state.value = _state.value.copy(
                isBuffering = false,
                isPlaying = false,
                error = error.localizedMessage ?: "Audio playback error",
            )
        }
    }

    private suspend fun loadTrack(index: Int, offsetMs: Long, autoPlay: Boolean) {
        val current = _state.value
        val track = current.tracks.getOrNull(index) ?: return
        val book = current.bookItem ?: return

        if (index != currentTrackIndex) {
            reportTrackStopped(finishedTrack = false)
        }

        currentTrackIndex = index
        usingTranscodeFallback = false
        pendingSeekMs = offsetMs.coerceAtLeast(0L)
        lastPositionSampleAtMs = 0L

        _state.value = _state.value.copy(
            currentTrackIndex = index,
            isBuffering = true,
            error = null,
        )

        val (uriString, isLocal) = withContext(Dispatchers.IO) {
            val uid = booksRepository.currentUserId() ?: "default"
            val offlineFile = offlineDownloadManager.getCompletedTrackFile(book.Id, track.Id, uid)
                ?: offlineDownloadManager.getCompletedTrackFile(track.Id, track.Id, uid)
                ?: offlineDownloadManager.getCompletedFile(track.Id, uid)
                ?: offlineDownloadManager.getDownload(book.Id, uid)?.let { dl ->
                    if (dl.status == OfflineDownloadItem.STATUS_COMPLETED) {
                        dl.trackPaths.getOrNull(index)?.let(::File)?.takeIf { it.exists() && it.length() > 0L }
                    } else {
                        null
                    }
                }
            if (offlineFile != null) {
                Uri.fromFile(offlineFile).toString() to true
            } else {
                val streamUrl = booksRepository.audiobookStreamUrl(track.Id)
                streamUrl to (streamUrl?.startsWith("file:", ignoreCase = true) == true)
            }
        }

        if (uriString.isNullOrBlank()) {
            _state.value = _state.value.copy(
                isBuffering = false,
                error = "No server configured for audio streaming",
            )
            return
        }

        currentTrackIsLocal = isLocal
        prepareTrackUri(track, uriString, offsetMs, autoPlay)
    }

    private fun prepareTrackUri(
        track: BaseItemDto,
        uriString: String,
        offsetMs: Long,
        autoPlay: Boolean,
    ) {
        val p = player ?: return
        val current = _state.value
        val bookTitle = current.bookItem?.Name ?: track.Name ?: "Audiobook"
        val chapterTitle = current.chapters.getOrNull(current.currentChapterIndex)?.Title?.takeIf { it.isNotBlank() }

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(chapterTitle ?: bookTitle)
            .setAlbumTitle(bookTitle)
            .setArtist(current.author.ifBlank { null })
            .setDisplayTitle(bookTitle)
            .setSubtitle(chapterTitle)
        current.coverUrl?.let { raw ->
            runCatching { Uri.parse(raw) }.getOrNull()?.let { metadataBuilder.setArtworkUri(it) }
        }

        val mediaItem = MediaItem.Builder()
            .setMediaId(track.Id)
            .setUri(uriString)
            .setMediaMetadata(metadataBuilder.build())
            .build()

        pendingSeekMs = offsetMs.coerceAtLeast(0L)
        p.setMediaItem(mediaItem, offsetMs.coerceAtLeast(0L))
        p.playWhenReady = autoPlay
        p.playbackParameters = PlaybackParameters(current.playbackSpeed)
        p.prepare()
    }

    private fun handleTrackEnded() {
        val current = _state.value
        sampleListeningTime(forceReset = true)
        reportTrackStopped(finishedTrack = true)

        val nextIdx = currentTrackIndex + 1
        if (nextIdx < current.tracks.size) {
            mainScope.launch {
                loadTrack(nextIdx, 0L, autoPlay = true)
            }
        } else {
            val total = current.totalDurationMs
            updatePositionState(total)
            saveLocalResumePosition()
            flushListeningSession(finishedBook = true)
            _state.value = _state.value.copy(isPlaying = false)
        }
    }

    private fun startBackgroundTickers() {
        positionTickerJob?.cancel()
        positionTickerJob = mainScope.launch {
            while (isActive) {
                val p = player
                if (p != null && p.playbackState != Player.STATE_IDLE) {
                    if (p.isPlaying) {
                        sampleListeningTime(forceReset = false)
                    }
                    refreshPositionFromPlayer()
                }
                delay(POSITION_TICK_MS)
            }
        }

        progressReportJob?.cancel()
        progressReportJob = mainScope.launch {
            while (isActive) {
                delay(PROGRESS_REPORT_INTERVAL_MS)
                val p = player
                val trackId = reportedTrackId
                if (p != null && trackId != null && p.isPlaying) {
                    val ticks = p.currentPosition.coerceAtLeast(0L) * TICKS_PER_MS
                    saveLocalResumePosition()
                    launch(Dispatchers.IO) {
                        jellioRepository.reportPlaybackProgress(trackId, trackId, ticks, isPaused = false)
                    }
                }
                if (listenedSecondsAccum >= LISTEN_FLUSH_SECONDS) {
                    flushListeningSession(finishedBook = false)
                }
            }
        }
    }

    private fun startSleepTicker() {
        sleepTimerJob?.cancel()
        sleepTimerJob = mainScope.launch {
            while (isActive) {
                val deadline = sleepDeadlineMs
                val armedChapter = sleepArmedChapterIndex
                val p = player

                if (deadline != null) {
                    val remainingMs = deadline - System.currentTimeMillis()
                    if (remainingMs <= 0L) {
                        p?.pause()
                        setSleepTimer(0)
                        break
                    } else {
                        if (remainingMs <= FADE_WINDOW_MS && p != null) {
                            val factor = (remainingMs.toFloat() / FADE_WINDOW_MS.toFloat()).coerceIn(0f, 1f)
                            p.volume = baseVolume * factor
                        }
                        val sec = ((remainingMs + 999L) / 1000L).toInt().coerceAtLeast(1)
                        _state.value = _state.value.copy(
                            sleepTimerRemainingSec = sec,
                            sleepStopAtChapterEnd = false,
                        )
                    }
                } else if (armedChapter != null) {
                    val current = _state.value
                    val chapter = current.chapters.getOrNull(armedChapter)
                    if (chapter == null || current.currentChapterIndex > armedChapter) {
                        p?.pause()
                        setSleepTimer(0)
                        break
                    }
                    val chapterEndMs = ((chapter.StartSeconds + chapter.DurationSeconds) * 1000.0).toLong()
                    val remainingMs = chapterEndMs - current.currentPositionMs
                    if (remainingMs <= 300L) {
                        p?.pause()
                        setSleepTimer(0)
                        break
                    } else if (remainingMs <= FADE_WINDOW_MS && p != null) {
                        val factor = (remainingMs.toFloat() / FADE_WINDOW_MS.toFloat()).coerceIn(0f, 1f)
                        p.volume = baseVolume * factor
                    }
                } else {
                    break
                }
                delay(500L)
            }
        }
    }

    private fun restoreVolume() {
        player?.volume = baseVolume
    }

    private fun refreshPositionFromPlayer() {
        val p = player ?: return
        val trackOffsetMs = p.currentPosition.coerceAtLeast(0L)
        val startMs = trackStartsMs.getOrElse(currentTrackIndex) { 0L }
        val bookPosMs = (startMs + trackOffsetMs).coerceAtLeast(0L)
        updatePositionState(bookPosMs)
    }

    private fun updatePositionState(bookPosMs: Long) {
        val current = _state.value
        val total = current.totalDurationMs.coerceAtLeast(bookPosMs)
        val chIdx = chapterIndexAt(current.chapters, bookPosMs)
        val chapterChanged = current.currentChapterIndex != chIdx
        if (
            current.currentPositionMs != bookPosMs ||
            chapterChanged ||
            current.totalDurationMs != total
        ) {
            _state.value = current.copy(
                currentPositionMs = bookPosMs,
                currentChapterIndex = chIdx,
                totalDurationMs = total,
            )
            if (chapterChanged) {
                updateActiveMediaItemMetadata(chIdx)
            }
        }
    }

    private fun updateActiveMediaItemMetadata(chapterIndex: Int) {
        val p = player ?: return
        val currentItem = p.currentMediaItem ?: return
        val current = _state.value
        val bookTitle = current.bookItem?.Name ?: "Audiobook"
        val chapterTitle = current.chapters.getOrNull(chapterIndex)?.Title?.takeIf { it.isNotBlank() } ?: bookTitle
        val updatedMeta = currentItem.mediaMetadata.buildUpon()
            .setTitle(chapterTitle)
            .setAlbumTitle(bookTitle)
            .setDisplayTitle(chapterTitle)
            .setSubtitle(bookTitle)
            .build()
        val updatedItem = currentItem.buildUpon()
            .setMediaMetadata(updatedMeta)
            .build()
        runCatching {
            p.replaceMediaItem(p.currentMediaItemIndex, updatedItem)
        }
    }

    private fun sampleListeningTime(forceReset: Boolean) {
        val now = System.currentTimeMillis()
        if (lastPositionSampleAtMs > 0L) {
            val deltaSec = (now - lastPositionSampleAtMs) / 1000.0
            if (deltaSec > 0.0 && deltaSec < 5.0) {
                listenedSecondsAccum += deltaSec
            }
        }
        lastPositionSampleAtMs = if (forceReset) 0L else now
    }

    private fun reportTrackStartIfNeeded() {
        val track = _state.value.tracks.getOrNull(currentTrackIndex) ?: return
        if (reportedTrackId == track.Id) return
        reportedTrackId = track.Id
        val ticks = (player?.currentPosition ?: 0L).coerceAtLeast(0L) * TICKS_PER_MS
        mainScope.launch(Dispatchers.IO) {
            jellioRepository.reportPlaybackStart(track.Id, track.Id, ticks)
        }
    }

    private fun reportTrackStopped(finishedTrack: Boolean) {
        val trackId = reportedTrackId ?: return
        reportedTrackId = null
        val bookId = _state.value.bookItem?.Id
        val trackDurMs = trackDurationsMs.getOrElse(currentTrackIndex) { 0L }
        val posMs = if (finishedTrack && trackDurMs > 0L) {
            trackDurMs
        } else {
            (player?.currentPosition ?: 0L).coerceAtLeast(0L)
        }
        val ticks = posMs * TICKS_PER_MS
        mainScope.launch(Dispatchers.IO) {
            val res = catching {
                jellioRepository.reportPlaybackStopped(trackId, trackId, ticks)
            }
            if (res.isFailure && !bookId.isNullOrBlank()) {
                offlineDownloadManager.queueAudiobookProgressMutation(
                    itemId = bookId,
                    trackId = trackId,
                    positionTicks = ticks,
                    played = finishedTrack,
                )
            }
        }
    }

    private fun flushListeningSession(finishedBook: Boolean) {
        val current = _state.value
        val book = current.bookItem ?: return
        val total = current.totalDurationMs
        val reachedEnd = finishedBook || (total > 0L && current.currentPositionMs >= (total * 0.98).toLong())
        val isFinishedNow = !finishedReported && reachedEnd
        val seconds = listenedSecondsAccum.roundToInt()

        if (seconds < 1 && !isFinishedNow) return
        listenedSecondsAccum = 0.0
        if (isFinishedNow) {
            finishedReported = true
        }

        mainScope.launch(Dispatchers.IO) {
            booksRepository.reportReadingSession(
                ReadingSessionRequest(
                    ItemId = book.Id,
                    Kind = "audiobook",
                    PagesRead = 0,
                    ListenedSeconds = seconds.coerceAtLeast(0),
                    Finished = isFinishedNow,
                ),
            )
            if (isFinishedNow) {
                offlineDownloadManager.onProgressUpdated(book.Id, 1.0, "audiobook")
            }
        }
    }

    private suspend fun teardownSession(resetState: Boolean) {
        sampleListeningTime(forceReset = true)
        saveLocalResumePosition()
        flushListeningSession(finishedBook = false)
        reportTrackStopped(finishedTrack = false)

        positionTickerJob?.cancel()
        positionTickerJob = null
        progressReportJob?.cancel()
        progressReportJob = null
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepDeadlineMs = null
        sleepArmedChapterIndex = null

        playbackService?.syncSession(null)
        mediaSession?.release()
        mediaSession = null
        player?.let { exo ->
            exo.removeListener(playerListener)
            exo.stop()
            exo.release()
        }
        player = null

        reportedTrackId = null
        currentTrackIndex = 0
        pendingSeekMs = 0L
        listenedSecondsAccum = 0.0
        lastPositionSampleAtMs = 0L

        if (resetState) {
            _state.value = AudiobookPlaybackState()
            runCatching {
                val stopIntent = Intent(context, AudiobookPlaybackService::class.java).apply {
                    action = AudiobookPlaybackService.ACTION_STOP
                }
                context.startService(stopIntent)
            }
        }
    }

    private suspend fun buildTranscodeUrl(trackId: String): String? {
        val server = sessionManager.serverAddress()?.trimEnd('/') ?: return null
        val token = sessionManager.accessToken()
        val deviceId = sessionManager.getCachedDeviceId()
        return buildString {
            append(server)
            append("/Audio/").append(trackId).append("/stream.mp3")
            append("?static=false&AudioCodec=mp3&AudioBitRate=128000&DeviceId=").append(deviceId)
            if (!token.isNullOrEmpty()) {
                append("&ApiKey=").append(token).append("&api_key=").append(token)
            }
        }
    }

    private suspend fun resolveCoverUrl(book: BaseItemDto, userId: String): String? {
        val offlineCover = offlineDownloadManager.getCompletedCoverFile(book.Id, userId)
            ?: book.AlbumId?.let { offlineDownloadManager.getCompletedCoverFile(it, userId) }
        if (offlineCover != null) return offlineCover.toURI().toString()

        val server = sessionManager.serverAddress()?.trimEnd('/')
        val token = sessionManager.accessToken()
        if (!server.isNullOrBlank()) {
            val primaryTag = book.ImageTags?.get("Primary") ?: book.PrimaryImageTag
            if (!primaryTag.isNullOrBlank()) {
                return jellioRepository.imageUrl(server, book.Id, primaryTag, "Primary", 600, token)
            }
            if (!book.AlbumId.isNullOrBlank()) {
                return jellioRepository.imageUrl(server, book.AlbumId, book.AlbumPrimaryImageTag, "Primary", 600, token)
            }
        }
        return booksRepository.bookCoverUrl(book)
    }

    private fun resolveAuthor(book: BaseItemDto): String =
        book.AlbumArtist?.takeIf { it.isNotBlank() }
            ?: book.Artists?.firstOrNull { it.isNotBlank() }
            ?: book.People?.firstOrNull { !it.Name.isNullOrBlank() }?.Name
            ?: ""

    private fun rebuildTrackOffsets(tracks: List<BaseItemDto>) {
        trackStartsMs = LongArray(tracks.size)
        trackDurationsMs = LongArray(tracks.size)
        var cursor = 0L
        for (i in tracks.indices) {
            val durMs = ((tracks[i].RunTimeTicks ?: 0L) / TICKS_PER_MS).coerceAtLeast(0L)
            trackStartsMs[i] = cursor
            trackDurationsMs[i] = durMs
            cursor += durMs
        }
    }

    private fun updateTrackDurationIfUnknown(trackIndex: Int, durationMs: Long) {
        if (trackIndex !in trackDurationsMs.indices || durationMs <= 0L) return
        if (trackDurationsMs[trackIndex] > 0L) return
        trackDurationsMs[trackIndex] = durationMs
        var cursor = 0L
        for (i in trackDurationsMs.indices) {
            trackStartsMs[i] = cursor
            cursor += trackDurationsMs[i]
        }
        val current = _state.value
        val updatedTracks = current.tracks.mapIndexed { idx, t ->
            if (idx == trackIndex && (t.RunTimeTicks ?: 0L) <= 0L) {
                t.copy(RunTimeTicks = durationMs * TICKS_PER_MS)
            } else {
                t
            }
        }
        val updatedChapters = when {
            current.chapters.size == 1 && current.chapters[0].DurationSeconds <= 0.0 -> {
                listOf(current.chapters[0].copy(DurationSeconds = cursor / 1000.0))
            }
            current.chapters.size == updatedTracks.size && current.chapters.any { it.DurationSeconds <= 0.0 } -> {
                updatedTracks.mapIndexed { idx, track ->
                    val startSec = trackStartsMs.getOrElse(idx) { 0L } / 1000.0
                    val durSec = trackDurationsMs.getOrElse(idx) { 0L } / 1000.0
                    val existingTitle = current.chapters.getOrNull(idx)?.Title?.takeIf { it.isNotBlank() }
                    AudiobookChapterDto(
                        Title = existingTitle ?: track.Name?.takeIf { it.isNotBlank() } ?: "Part ${idx + 1}",
                        StartSeconds = startSec,
                        DurationSeconds = durSec,
                        TrackId = track.Id,
                        TrackOffsetSeconds = 0.0,
                    )
                }
            }
            else -> current.chapters
        }
        val newTotal = maxOf(current.totalDurationMs, computeTotalDurationMs(updatedChapters), cursor)
        _state.value = current.copy(
            tracks = updatedTracks,
            chapters = updatedChapters,
            totalDurationMs = newTotal,
        )
    }

    private fun buildChapters(
        tracks: List<BaseItemDto>,
        apiChapters: List<AudiobookChapterDto>,
    ): List<AudiobookChapterDto> {
        if (apiChapters.isNotEmpty()) {
            return apiChapters.mapIndexed { idx, ch ->
                if (ch.Title.isBlank()) ch.copy(Title = "Chapter ${idx + 1}") else ch
            }
        }
        if (tracks.size == 1) {
            val track = tracks[0]
            val trackDurSec = trackDurationsMs.getOrElse(0) { 0L } / 1000.0
            val embedded = track.Chapters.orEmpty().sortedBy { it.StartPositionTicks }
            if (embedded.isNotEmpty()) {
                return embedded.mapIndexed { idx, ch ->
                    val startSec = ch.StartPositionTicks / TICKS_PER_SEC
                    val nextStartSec = embedded.getOrNull(idx + 1)?.let { it.StartPositionTicks / TICKS_PER_SEC }
                        ?: trackDurSec.coerceAtLeast(startSec)
                    AudiobookChapterDto(
                        Title = ch.Name?.takeIf { it.isNotBlank() } ?: "Chapter ${idx + 1}",
                        StartSeconds = startSec,
                        DurationSeconds = (nextStartSec - startSec).coerceAtLeast(0.0),
                        TrackId = track.Id,
                        TrackOffsetSeconds = startSec,
                    )
                }
            }
        } else if (tracks.size > 1) {
            return tracks.mapIndexed { idx, track ->
                val startSec = trackStartsMs.getOrElse(idx) { 0L } / 1000.0
                val durSec = trackDurationsMs.getOrElse(idx) { 0L } / 1000.0
                AudiobookChapterDto(
                    Title = track.Name?.takeIf { it.isNotBlank() } ?: "Part ${idx + 1}",
                    StartSeconds = startSec,
                    DurationSeconds = durSec,
                    TrackId = track.Id,
                    TrackOffsetSeconds = 0.0,
                )
            }
        }
        val totalSec = trackDurationsMs.sum() / 1000.0
        return listOf(
            AudiobookChapterDto(
                Title = "Chapter 1",
                StartSeconds = 0.0,
                DurationSeconds = totalSec,
                TrackId = tracks.firstOrNull()?.Id,
                TrackOffsetSeconds = 0.0,
            ),
        )
    }

    private fun computeTotalDurationMs(chapters: List<AudiobookChapterDto>): Long {
        val tracksSum = trackDurationsMs.sum()
        val lastChapterEndMs = chapters.maxOfOrNull { ((it.StartSeconds + it.DurationSeconds) * 1000.0).toLong() } ?: 0L
        return maxOf(tracksSum, lastChapterEndMs, 0L)
    }

    private fun chapterIndexAt(chapters: List<AudiobookChapterDto>, positionMs: Long): Int {
        if (chapters.isEmpty()) return 0
        var found = 0
        for (i in chapters.indices) {
            val startMs = (chapters[i].StartSeconds * 1000.0).toLong()
            if (positionMs + 250L >= startMs) {
                found = i
            }
        }
        return found.coerceIn(0, chapters.lastIndex)
    }

    private fun resolveResumePoint(book: BaseItemDto, tracks: List<BaseItemDto>): Pair<Int, Long> {
        var remoteBestIdx = -1
        var remoteBestOffsetMs = 0L
        var remoteBestTime = 0L

        for (i in tracks.indices) {
            val userData = tracks[i].UserData ?: continue
            if (userData.PlaybackPositionTicks > 0L) {
                val playedAt = parseIsoTimestampMs(userData.LastPlayedDate)
                if (remoteBestIdx == -1 || playedAt > remoteBestTime) {
                    remoteBestIdx = i
                    remoteBestOffsetMs = userData.PlaybackPositionTicks / TICKS_PER_MS
                    remoteBestTime = playedAt
                }
            }
        }

        val local = readLocalResumePosition(book)
        if (local != null) {
            val (localTrackId, localOffsetMs, localSavedAt) = local
            val localTrackIdx = tracks.indexOfFirst {
                BooksRepository.normalizeId(it.Id) == BooksRepository.normalizeId(localTrackId)
            }
            if (localTrackIdx >= 0 && (remoteBestIdx == -1 || localSavedAt >= remoteBestTime)) {
                return localTrackIdx to localOffsetMs
            }
        }

        if (remoteBestIdx >= 0) {
            return remoteBestIdx to remoteBestOffsetMs
        }

        val firstUnplayed = tracks.indexOfFirst { it.UserData?.Played != true }
        return (if (firstUnplayed >= 0) firstUnplayed else 0) to 0L
    }

    private fun bookPrefsKey(book: BaseItemDto): String =
        BooksRepository.normalizeId(book.Id)

    private fun readBookSpeed(book: BaseItemDto): Float {
        val raw = prefs.getFloat(KEY_SPEED_PREFIX + bookPrefsKey(book), 1.0f)
        return raw.coerceIn(0.75f, 2.0f)
    }

    private fun saveBookSpeed(book: BaseItemDto, speed: Float) {
        prefs.edit().putFloat(KEY_SPEED_PREFIX + bookPrefsKey(book), speed.coerceIn(0.75f, 2.0f)).apply()
    }

    private fun saveLocalResumePosition() {
        val current = _state.value
        val book = current.bookItem ?: return
        val track = current.tracks.getOrNull(currentTrackIndex) ?: return
        val offsetMs = (player?.currentPosition ?: 0L).coerceAtLeast(0L)
        val payload = "${track.Id}|$offsetMs|${System.currentTimeMillis()}"
        prefs.edit().putString(KEY_RESUME_PREFIX + bookPrefsKey(book), payload).apply()
    }

    private fun readLocalResumePosition(book: BaseItemDto): Triple<String, Long, Long>? {
        val raw = prefs.getString(KEY_RESUME_PREFIX + bookPrefsKey(book), null) ?: return null
        val parts = raw.split('|')
        if (parts.size < 3) return null
        val trackId = parts[0].ifBlank { return null }
        val offsetMs = parts[1].toLongOrNull()?.coerceAtLeast(0L) ?: return null
        val savedAt = parts[2].toLongOrNull() ?: 0L
        return Triple(trackId, offsetMs, savedAt)
    }

    private fun saveCachedChapters(bookId: String, chapters: List<AudiobookChapterDto>) {
        runCatching {
            val json = chaptersAdapter.toJson(chapters)
            prefs.edit().putString(KEY_CHAPTERS_PREFIX + BooksRepository.normalizeId(bookId), json).apply()
        }
    }

    private fun readCachedChapters(bookId: String): List<AudiobookChapterDto> {
        val json = prefs.getString(KEY_CHAPTERS_PREFIX + BooksRepository.normalizeId(bookId), null) ?: return emptyList()
        return runCatching { chaptersAdapter.fromJson(json).orEmpty() }.getOrDefault(emptyList())
    }

    private fun parseIsoTimestampMs(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        val cleaned = raw.replace(Regex("\\.\\d+(?=Z|$)"), "")
        return runCatching {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            sdf.parse(cleaned)?.time ?: 0L
        }.getOrDefault(0L)
    }

    companion object {
        fun parseBookmarkSeconds(locator: String): Long {
            val clean = locator.trim()
            if (clean.startsWith("audio:", ignoreCase = true)) {
                return clean.substringAfter(':').toDoubleOrNull()?.toLong() ?: -1L
            }
            return clean.toDoubleOrNull()?.toLong() ?: -1L
        }

        fun formatDurationClock(seconds: Double): String {
            val totalSec = seconds.toLong().coerceAtLeast(0L)
            val hrs = totalSec / 3600L
            val mins = (totalSec % 3600L) / 60L
            val secs = totalSec % 60L
            return if (hrs > 0L) {
                String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
            } else {
                String.format(Locale.US, "%d:%02d", mins, secs)
            }
        }
    }
}
