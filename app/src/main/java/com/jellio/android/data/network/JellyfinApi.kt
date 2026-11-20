package com.jellio.android.data.network

import com.jellio.android.data.model.AchievementsDto
import com.jellio.android.data.model.AuthenticateByNameRequest
import com.jellio.android.data.model.AuthenticationResultDto
import com.jellio.android.data.model.AvatarPresetDto
import com.jellio.android.data.model.BaseItemDto
import com.jellio.android.data.model.CalendarEntryDto
import com.jellio.android.data.model.ClientConfigDto
import com.jellio.android.data.model.FeedEntryDto
import com.jellio.android.data.model.ForgotPasswordPinRequest
import com.jellio.android.data.model.ForgotPasswordRequest
import com.jellio.android.data.model.IntroSkipperSegmentsDto
import com.jellio.android.data.model.ItemsResultDto
import com.jellio.android.data.model.PinRedeemResultDto
import com.jellio.android.data.model.PlaybackInfoRequest
import com.jellio.android.data.model.PlaybackInfoResponseDto
import com.jellio.android.data.model.PlaybackReportRequest
import com.jellio.android.data.model.ProfileDto
import com.jellio.android.data.model.ProfileRecommendationsRequest
import com.jellio.android.data.model.PublicSystemInfoDto
import com.jellio.android.data.model.RealWatchRequest
import com.jellio.android.data.model.ReportDurationRequest
import com.jellio.android.data.model.SetBioRequest
import com.jellio.android.data.model.SetPrivacyRequest
import com.jellio.android.data.model.SleepTimerStartRequest
import com.jellio.android.data.model.SleepTimerStatusDto
import com.jellio.android.data.model.UpdatePasswordRequest
import com.jellio.android.data.model.UserDto
import com.jellio.android.data.model.UserItemDataDto
import com.jellio.android.data.model.WatchlistAutoRemoveRequest
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface JellyfinApi {
    @GET("System/Info/Public")
    suspend fun getPublicSystemInfo(): PublicSystemInfoDto

    @POST("Users/AuthenticateByName")
    suspend fun authenticateByName(@Body body: AuthenticateByNameRequest): AuthenticationResultDto

    @GET("Users/{userId}")
    suspend fun getUser(@Path("userId") userId: String): UserDto

    @GET("Users/Public")
    suspend fun getPublicUsers(): List<UserDto>

    @GET("Users/{userId}")
    suspend fun getUserWithToken(
        @Query("ApiKey") token: String,
        @Path("userId") userId: String,
        // Built from the saved token: otherwise the signed in user's own
        // header wins over ApiKey and the check runs as the wrong user.
        @Header("Authorization") authorization: String,
    ): UserDto

    // Raw JSON so a configuration update can send back every field it
    // does not change: the server replaces the whole configuration.
    @GET("Users/{userId}")
    suspend fun getUserJson(@Path("userId") userId: String): ResponseBody

    @POST("Users/{userId}/Configuration")
    suspend fun updateUserConfiguration(
        @Path("userId") userId: String,
        @Body body: RequestBody,
    )

    @GET("Users/{userId}/Views")
    suspend fun getUserViews(@Path("userId") userId: String): ItemsResultDto

    // Every genre a library's items carry (GenresController).
    @GET("Genres")
    suspend fun getGenres(
        @Query("UserId") userId: String,
        @Query("ParentId") parentId: String? = null,
        @Query("IncludeItemTypes") includeItemTypes: String? = null,
        @Query("SortBy") sortBy: String = "SortName",
        @Query("Recursive") recursive: Boolean = true,
    ): ItemsResultDto

    @GET("Users/{userId}/Items")
    suspend fun getItems(
        @Path("userId") userId: String,
        @Query("ParentId") parentId: String? = null,
        @Query("IncludeItemTypes") includeItemTypes: String? = null,
        @Query("Recursive") recursive: Boolean = true,
        @Query("Limit") limit: Int? = null,
        @Query("StartIndex") startIndex: Int? = null,
        @Query("SortBy") sortBy: String? = null,
        @Query("SortOrder") sortOrder: String? = null,
        @Query("Fields") fields: String? = null,
        @Query("Genres") genres: String? = null,
        @Query("Filters") filters: String? = null,
        @Query("searchTerm") searchTerm: String? = null,
        @Query("personIds") personIds: String? = null,
    ): ItemsResultDto

    @GET("gelato/search/movie")
    suspend fun searchGelatoMovies(@Query("q") query: String): List<BaseItemDto>

    @GET("gelato/search/series")
    suspend fun searchGelatoSeries(@Query("q") query: String): List<BaseItemDto>

    // Title info by id without Gelato resolving its streams first, which
    // the single-item request below does (2.5-4 s cold on a Gelato server).
    @GET("Items")
    suspend fun getItemsByIds(
        @Query("userId") userId: String,
        @Query("ids") ids: String,
        @Query("Fields") fields: String? = null,
    ): ItemsResultDto

    @GET("Users/{userId}/Items/{itemId}")
    suspend fun getItem(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String,
        @Query("Fields") fields: String? = null,
        @Header("X-Read-Timeout-Seconds") readTimeoutSeconds: Int? = null,
    ): BaseItemDto

    @GET("Users/{userId}/Items/Resume")
    suspend fun getResumeItems(
        @Path("userId") userId: String,
        @Query("Limit") limit: Int = 20,
        @Query("Fields") fields: String? = null,
    ): ItemsResultDto

    @GET("Shows/NextUp")
    suspend fun getNextUp(
        @Query("userId") userId: String,
        @Query("Limit") limit: Int = 20,
        @Query("Fields") fields: String? = null,
        @Query("seriesId") seriesId: String? = null,
        @Query("enableResumable") enableResumable: Boolean? = null,
    ): ItemsResultDto

    @GET("Shows/{seriesId}/Seasons")
    suspend fun getSeasons(
        @Path("seriesId") seriesId: String,
        @Query("userId") userId: String,
    ): ItemsResultDto

    @GET("Shows/{seriesId}/Episodes")
    suspend fun getEpisodes(
        @Path("seriesId") seriesId: String,
        @Query("userId") userId: String,
        @Query("seasonId") seasonId: String? = null,
        @Query("Fields") fields: String? = null,
    ): ItemsResultDto

    @GET("Episode/{itemId}/Timestamps")
    suspend fun getIntroSkipperSegments(@Path("itemId") itemId: String): IntroSkipperSegmentsDto

    @GET("Jellio/segments/{itemId}")
    suspend fun getJellioSegments(@Path("itemId") itemId: String): retrofit2.Response<com.jellio.android.data.playback.JellioSegmentsDto>

    @GET("MediaSegments/{itemId}")
    suspend fun getMediaSegments(@Path("itemId") itemId: String): com.jellio.android.data.model.MediaSegmentsResultDto

    @GET("Jellio/introcredits/community/{itemId}")
    suspend fun getCommunityIntroCredits(@Path("itemId") itemId: String): IntroSkipperSegmentsDto

    @GET("Jellio/introcredits/{itemId}")
    suspend fun getJellioIntroCredits(@Path("itemId") itemId: String): IntroSkipperSegmentsDto

    @GET("Jellio/grouplist")
    suspend fun getGrouplist(): com.jellio.android.data.model.GrouplistDto

    @POST("Jellio/grouplist/{itemId}")
    suspend fun addToGrouplist(@Path("itemId") itemId: String): com.jellio.android.data.model.GrouplistDto

    @DELETE("Jellio/grouplist/{itemId}")
    suspend fun removeFromGrouplist(@Path("itemId") itemId: String): com.jellio.android.data.model.GrouplistDto

    @POST("Items/{itemId}/PlaybackInfo")
    suspend fun getPlaybackInfo(
        @Path("itemId") itemId: String,
        @Body body: PlaybackInfoRequest,
    ): PlaybackInfoResponseDto

    @POST("Sessions/Playing")
    suspend fun reportPlaybackStart(@Body body: PlaybackReportRequest)

    @POST("Jellio/playback/start")
    suspend fun reportPlayStart(@Body body: com.jellio.android.data.model.PlayStartRecord): retrofit2.Response<Unit>

    @POST("Sessions/Playing/Progress")
    suspend fun reportPlaybackProgress(@Body body: PlaybackReportRequest)

    @POST("Sessions/Playing/Stopped")
    suspend fun reportPlaybackStopped(@Body body: PlaybackReportRequest)

    @POST("Sessions/Capabilities/Full")
    suspend fun postCapabilities(@Body body: com.jellio.android.data.model.ClientCapabilitiesDto): retrofit2.Response<Unit>

    @GET("LiveTv/Channels")
    suspend fun getLiveChannels(
        @Query("UserId") userId: String,
        @Query("Limit") limit: Int = 1000,
        @Query("SortBy") sortBy: String = "SortName",
        @Query("AddCurrentProgram") addCurrentProgram: Boolean = true,
        @Query("EnableUserData") enableUserData: Boolean = true,
        @Query("EnableImageTypes") imageTypes: String = "Primary",
        @Query("ImageTypeLimit") imageTypeLimit: Int = 1,
    ): com.jellio.android.data.model.LiveChannelsResponse

    @POST("LiveTv/Programs")
    suspend fun getLivePrograms(@Body body: com.jellio.android.data.model.LiveProgramsRequest): com.jellio.android.data.model.LiveProgramsResponse

    // AutoOpenLiveStream opens the tuner (about 7 s the first time).
    @POST("Items/{itemId}/PlaybackInfo")
    suspend fun openLiveChannel(
        @Path("itemId") channelId: String,
        @Query("UserId") userId: String,
        @Body body: com.jellio.android.data.model.LivePlaybackInfoRequest,
        @Query("AutoOpenLiveStream") autoOpen: Boolean = true,
    ): com.jellio.android.data.model.LivePlaybackInfoResponse

    @POST("LiveStreams/Close")
    suspend fun closeLiveStream(@Query("liveStreamId") liveStreamId: String): retrofit2.Response<Unit>

    // Remove from Coming Soon, and back.
    @POST("Jellio/calendar/hidden/{itemId}")
    suspend fun hideFromComingSoon(@Path("itemId") itemId: String): retrofit2.Response<Unit>

    @DELETE("Jellio/calendar/hidden/{itemId}")
    suspend fun unhideFromComingSoon(@Path("itemId") itemId: String): retrofit2.Response<Unit>

    // Closing the live stream does not stop a transcode reading the channel.
    @DELETE("Videos/ActiveEncodings")
    suspend fun stopEncoding(@Query("deviceId") deviceId: String, @Query("playSessionId") playSessionId: String): retrofit2.Response<Unit>

    @POST("Jellio/now-playing/live")
    suspend fun reportLiveWatching(@Body body: com.jellio.android.data.model.LiveWatchingDto): retrofit2.Response<Unit>

    @DELETE("Jellio/now-playing/live")
    suspend fun stopLiveWatching(): retrofit2.Response<Unit>

    @GET("Jellio/now-playing")
    suspend fun getNowPlaying(): List<com.jellio.android.data.model.NowPlayingSessionDto>

    @GET("Jellio/LiveHealth")
    suspend fun getLiveHealth(): Map<String, com.jellio.android.data.model.LiveChannelStatusDto>

    @POST("Jellio/LiveHealth/{channelId}")
    suspend fun reportLiveChannel(@Path("channelId") channelId: String, @Body body: com.jellio.android.data.model.LiveChannelReportDto): retrofit2.Response<Unit>

    @POST("Jellio/StreamLinks/{id}/report")
    suspend fun reportStreamLinkPlay(@Path("id") id: String, @Body body: com.jellio.android.data.model.StreamLinkPlayReportDto): retrofit2.Response<Unit>

    @GET("Jellio/StreamLinks")
    suspend fun getStreamLinks(): List<com.jellio.android.data.model.StreamLinkDto>

    @Headers("X-Read-Timeout-Seconds: 140")
    @POST("Jellio/StreamLinks/{id}/play")
    suspend fun playStreamLink(@Path("id") id: String, @Query("entry") entry: Int?): com.jellio.android.data.model.StreamLinkPlayDto

    @DELETE("Jellio/StreamLinks/{id}")
    suspend fun removeStreamLink(@Path("id") id: String): retrofit2.Response<Unit>

    @DELETE("Jellio/StreamLinks/{id}/entries/{index}")
    suspend fun removeStreamLinkEntry(@Path("id") id: String, @Path("index") index: Int): retrofit2.Response<Unit>

    @Headers("X-Read-Timeout-Seconds: 140")
    @POST("Jellio/StreamLinks/{id}/refresh")
    suspend fun refreshStreamLink(@Path("id") id: String): com.jellio.android.data.model.StreamLinkDto

    @POST("Jellio/StreamLinks/pair")
    suspend fun createStreamPairing(): com.jellio.android.data.model.StreamPairingDto

    @GET("Jellio/StreamLinks/pair/{code}")
    suspend fun streamPairingStatus(@Path("code") code: String): retrofit2.Response<com.jellio.android.data.model.StreamPairingStatusDto>

    @POST("Users/{userId}/FavoriteItems/{itemId}")
    suspend fun addFavorite(@Path("userId") userId: String, @Path("itemId") itemId: String): UserItemDataDto

    @DELETE("Users/{userId}/FavoriteItems/{itemId}")
    suspend fun removeFavorite(@Path("userId") userId: String, @Path("itemId") itemId: String): UserItemDataDto

    @POST("Users/{userId}/PlayedItems/{itemId}")
    suspend fun markPlayed(@Path("userId") userId: String, @Path("itemId") itemId: String): UserItemDataDto

    @DELETE("Users/{userId}/PlayedItems/{itemId}")
    suspend fun markUnplayed(@Path("userId") userId: String, @Path("itemId") itemId: String): UserItemDataDto

    // Sets only the given user data fields; used to clear a resume position
    // without marking the title watched.
    @POST("UserItems/{itemId}/UserData")
    suspend fun updateUserData(
        @Path("itemId") itemId: String,
        @Query("userId") userId: String,
        @Body body: com.jellio.android.data.model.UserDataUpdate,
    ): UserItemDataDto

    @POST("Users/{userId}/Items/{itemId}/Rating")
    suspend fun setRating(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String,
        @Query("likes") likes: Boolean,
    ): UserItemDataDto

    @DELETE("Users/{userId}/Items/{itemId}/Rating")
    suspend fun clearRating(@Path("userId") userId: String, @Path("itemId") itemId: String): UserItemDataDto

    @GET("Jellio/search/suggestions")
    suspend fun getSearchSuggestions(
        @Query("query") query: String,
        @Query("tmdbId") tmdbId: String? = null,
        @Query("mediaType") mediaType: String? = null,
        @Query("enableVirtual") enableVirtual: Boolean = true,
        @Query("userId") userId: String? = null,
    ): com.jellio.android.data.model.SearchSuggestionsDto

    @GET("Jellio/search/fuzzy")
    suspend fun searchFuzzy(
        @Query("query") query: String,
        @Query("userId") userId: String,
        @Query("limit") limit: Int = 24,
    ): com.jellio.android.data.model.FuzzySearchResponseDto

    @GET("Jellio/recommendations/top-picks")
    suspend fun getTopPicksRecommendations(
        @Query("userId") userId: String,
        @Query("limit") limit: Int = 20,
        @Query("enableVirtual") enableVirtual: Boolean = true,
    ): ItemsResultDto

    @GET("Jellio/calendar")
    suspend fun getCalendarEntries(): List<CalendarEntryDto>

    @GET("Jellio/notifications")
    suspend fun getJellioNotifications(): List<com.jellio.android.data.model.JellioNotificationDto>

    // Jellyfin SyncPlay (SyncPlayController) and server time for its clock sync.
    @GET("GetUtcTime")
    suspend fun getUtcTime(): com.jellio.android.data.model.UtcTimeDto

    @GET("SyncPlay/List")
    suspend fun getSyncPlayGroups(): List<com.jellio.android.data.model.SyncPlayGroupDto>

    @POST("SyncPlay/New")
    suspend fun syncPlayNew(@Body body: com.jellio.android.data.model.SyncPlayNewBody)

    @POST("SyncPlay/Join")
    suspend fun syncPlayJoin(@Body body: com.jellio.android.data.model.SyncPlayJoinBody)

    @POST("SyncPlay/Leave")
    suspend fun syncPlayLeave()

    @POST("SyncPlay/Pause")
    suspend fun syncPlayPause()

    @POST("SyncPlay/Unpause")
    suspend fun syncPlayUnpause()

    @POST("SyncPlay/Seek")
    suspend fun syncPlaySeek(@Body body: com.jellio.android.data.model.SyncPlaySeekBody)

    @POST("SyncPlay/Buffering")
    suspend fun syncPlayBuffering(@Body body: com.jellio.android.data.model.SyncPlayStateBody)

    @POST("SyncPlay/Ready")
    suspend fun syncPlayReady(@Body body: com.jellio.android.data.model.SyncPlayStateBody)

    @POST("SyncPlay/SetNewQueue")
    suspend fun syncPlaySetNewQueue(@Body body: com.jellio.android.data.model.SyncPlayQueueBody)

    // Jellio's own group watch extras (Controllers/GroupWatch*Controller.cs).
    @GET("Jellio/groupwatch/invites")
    suspend fun getGroupWatchInvites(@Query("after") after: Long): List<com.jellio.android.data.model.GroupWatchInviteDto>

    @GET("Jellio/groupwatch/{groupId}/messages")
    suspend fun getGroupWatchMessages(@Path("groupId") groupId: String, @Query("after") after: Long): List<com.jellio.android.data.model.GroupWatchMessageDto>

    @POST("Jellio/groupwatch/{groupId}/messages")
    suspend fun sendGroupWatchMessage(@Path("groupId") groupId: String, @Body body: com.jellio.android.data.model.GroupWatchSendBody)

    @GET("Jellio/parties")
    suspend fun getParties(): com.jellio.android.data.model.PartiesDto

    @GET("Jellio/parties/{groupId}/state")
    suspend fun getPartyState(@Path("groupId") groupId: String, @Query("after") after: Long): com.jellio.android.data.model.PartyStateDto

    @POST("Jellio/parties/{groupId}/setup")
    suspend fun setupParty(@Path("groupId") groupId: String, @Body body: com.jellio.android.data.model.PartySetupBody)

    @POST("Jellio/parties/{groupId}/item")
    suspend fun setPartyItem(@Path("groupId") groupId: String, @Body body: com.jellio.android.data.model.PartyItemBody)

    @POST("Jellio/parties/{groupId}/host")
    suspend fun transferPartyHost(@Path("groupId") groupId: String, @Body body: com.jellio.android.data.model.PartyHostBody)

    @POST("Jellio/parties/{groupId}/request")
    suspend fun requestPartyControl(@Path("groupId") groupId: String, @Body body: com.jellio.android.data.model.PartyControlBody)

    @POST("Jellio/parties/{groupId}/ready")
    suspend fun setPartyReady(@Path("groupId") groupId: String, @Body body: com.jellio.android.data.model.PartyReadyBody)

    @POST("Jellio/parties/{groupId}/countdown")
    suspend fun startPartyCountdown(@Path("groupId") groupId: String, @Body body: com.jellio.android.data.model.PartyCountdownBody): com.jellio.android.data.model.PartyCountdownDto

    @POST("Jellio/parties/code")
    suspend fun resolvePartyCode(@Body body: com.jellio.android.data.model.PartyCodeBody): com.jellio.android.data.model.PartyCodeDto

    @POST("Jellio/parties/schedule/{id}/rsvp")
    suspend fun rsvpParty(@Path("id") id: String, @Body body: com.jellio.android.data.model.PartyRsvpBody)

    @POST("Jellio/parties/schedule/{id}/started")
    suspend fun markScheduledPartyStarted(@Path("id") id: String, @Body body: com.jellio.android.data.model.PartyStartedBody)

    @GET("Jellio/overlays/pending")
    suspend fun getPendingOverlays(@Query("after") after: Long): com.jellio.android.data.model.PendingOverlaysDto

    @GET("Jellio/sounds/pending")
    suspend fun getPendingSounds(@Query("after") after: Long): com.jellio.android.data.model.PendingSoundsDto

    @POST("Jellio/notifications/read")
    suspend fun markJellioNotificationsRead()

    @DELETE("Jellio/notifications/{id}")
    suspend fun deleteJellioNotification(@Path("id") id: String): retrofit2.Response<Unit>

    @DELETE("Jellio/notifications")
    suspend fun clearJellioNotifications(): retrofit2.Response<Unit>

    @GET("Jellio/anime/ids")
    suspend fun getAnimeIds(): com.jellio.android.data.model.AnimeIdsDto

    @GET("Jellio/feed")
    suspend fun getFeed(): List<FeedEntryDto>

    @GET("Jellio/config")
    suspend fun getJellioConfig(): ClientConfigDto

    @POST("Jellio/playback/decision")
    suspend fun logPlaybackDecision(@Body body: com.jellio.android.data.model.PlaybackDecisionBody)

    @POST("Jellio/playback/failover")
    suspend fun logFailover(@Body body: com.jellio.android.data.model.FailoverLogBody)

    @POST("Jellio/sleep-timer/start")
    suspend fun startSleepTimer(@Body body: SleepTimerStartRequest): SleepTimerStatusDto

    @POST("Jellio/sleep-timer/cancel")
    suspend fun cancelSleepTimer()

    @GET("Jellio/sleep-timer/status")
    suspend fun getSleepTimerStatus(): SleepTimerStatusDto

    @POST("gelato/prefetch/{itemId}")
    suspend fun prefetchGelatoStreams(@Path("itemId") itemId: String)

    @POST("gelato/prefetch-batch")
    suspend fun prefetchGelatoBatch(@Body body: com.jellio.android.data.model.PrefetchBatchRequest): retrofit2.Response<com.jellio.android.data.model.PrefetchBatchResponse>

    @GET("gelato/warm/{itemId}")
    suspend fun getWarmSources(@Path("itemId") itemId: String): retrofit2.Response<Map<String, String>>

    @GET("gelato/tiers/{itemId}")
    suspend fun getQualityTiers(@Path("itemId") itemId: String): retrofit2.Response<Map<String, String>>

    @GET("Jellio/next-up-hidden")
    suspend fun getHiddenNextUpSeries(): List<String>

    @POST("Jellio/next-up-hidden/{seriesId}")
    suspend fun hideSeriesFromNextUp(@Path("seriesId") seriesId: String)

    // Cached on the server: the genre list with counts, and a genre row.
    @GET("Jellio/genres")
    suspend fun getGenreCounts(
        @Query("types") types: String,
        @Query("parentId") parentId: String? = null,
    ): com.jellio.android.data.model.GenreCountsDto

    @GET("Jellio/genres/items")
    suspend fun getCachedGenreItems(
        @Query("genre") genre: String,
        @Query("types") types: String,
        @Query("parentId") parentId: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("startIndex") startIndex: Int? = null,
        @Query("enableVirtual") enableVirtual: Boolean? = true,
    ): ItemsResultDto

    @GET("Jellio/genres/search")
    suspend fun searchGenreOrCategory(
        @Query("query") query: String,
    ): com.jellio.android.data.model.GenreSearchResultDto

    @GET("Jellio/community/users")
    suspend fun getCommunityUsers(): List<com.jellio.android.data.model.CommunityUserDto>

    @GET("Jellio/community/watching")
    suspend fun getCommunityWatching(
        @Query("excludedUserIds") excludedUserIds: String? = null,
    ): com.jellio.android.data.model.CommunityWatchingResponse

    @POST("Users/{userId}/Password")
    suspend fun updatePassword(@Path("userId") userId: String, @Body body: UpdatePasswordRequest)

    @POST("Users/ForgotPassword")
    suspend fun requestPasswordReset(@Body body: ForgotPasswordRequest)

    @POST("Users/ForgotPassword/Pin")
    suspend fun redeemPasswordResetPin(@Body body: ForgotPasswordPinRequest): PinRedeemResultDto

    @GET("QuickConnect/Enabled")
    suspend fun isQuickConnectEnabled(): Boolean

    @POST("QuickConnect/Authorize")
    suspend fun authorizeQuickConnect(@Query("code") code: String): Boolean

    @POST("QuickConnect/Initiate")
    suspend fun initiateQuickConnect(): com.jellio.android.data.model.QuickConnectResultDto

    @GET("QuickConnect/Connect")
    suspend fun checkQuickConnect(@Query("Secret") secret: String): com.jellio.android.data.model.QuickConnectResultDto

    @POST("Users/AuthenticateWithQuickConnect")
    suspend fun authenticateWithQuickConnect(@Body body: com.jellio.android.data.model.QuickConnectAuthBody): AuthenticationResultDto

    @GET("Jellio/avatars")
    suspend fun getAvatarPresets(): List<AvatarPresetDto>

    @Streaming
    @GET("Jellio/avatars/{id}")
    suspend fun getAvatarPresetImage(@Path(value = "id", encoded = true) id: String): ResponseBody

    @POST("Users/{userId}/Images/Primary")
    suspend fun uploadUserAvatar(@Path("userId") userId: String, @Body body: RequestBody)

    @GET("Jellio/profile/{userId}")
    suspend fun getProfile(@Path("userId") userId: String): ProfileDto

    @GET("Jellio/profile/settings")
    suspend fun getProfileSettings(): ProfileDto

    @POST("Jellio/profile/recommendations")
    suspend fun saveProfileRecommendations(@Body body: ProfileRecommendationsRequest): ProfileDto

    @POST("Jellio/profile/watchlist-auto-remove")
    suspend fun setWatchlistAutoRemove(@Body request: WatchlistAutoRemoveRequest)

    @POST("Jellio/profile/bio")
    suspend fun setProfileBio(@Body body: SetBioRequest)

    @POST("Jellio/profile/privacy")
    suspend fun setProfilePrivacy(@Body body: SetPrivacyRequest)

    @POST("Jellio/profile/subtitles")
    suspend fun saveSubtitleStyle(@Body body: com.jellio.android.data.model.SubtitleStyleDto): ProfileDto

    @GET("Jellio/friends")
    suspend fun getFriends(): com.jellio.android.data.model.FriendsListDto

    @GET("Jellio/friends/status/{userId}")
    suspend fun getFriendStatus(@Path("userId") userId: String): com.jellio.android.data.model.FriendStatusDto

    @GET("Jellio/friends/people")
    suspend fun findFriendPeople(@Query("query") query: String): List<com.jellio.android.data.model.FriendPersonDto>

    @POST("Jellio/friends/requests")
    suspend fun sendFriendRequest(@Body body: com.jellio.android.data.model.FriendRequestBody): retrofit2.Response<com.jellio.android.data.model.FriendStatusResultDto>

    @POST("Jellio/friends/requests/{requestId}/accept")
    suspend fun acceptFriendRequest(@Path("requestId") requestId: String): retrofit2.Response<com.jellio.android.data.model.FriendStatusResultDto>

    @POST("Jellio/friends/requests/{requestId}/decline")
    suspend fun declineFriendRequest(@Path("requestId") requestId: String): retrofit2.Response<com.jellio.android.data.model.FriendStatusResultDto>

    @DELETE("Jellio/friends/requests/{requestId}")
    suspend fun cancelFriendRequest(@Path("requestId") requestId: String): retrofit2.Response<com.jellio.android.data.model.FriendStatusResultDto>

    @DELETE("Jellio/friends/{userId}")
    suspend fun removeFriend(@Path("userId") userId: String): com.jellio.android.data.model.FriendStatusResultDto

    @POST("Jellio/friends/{userId}/mute")
    suspend fun muteFriend(@Path("userId") userId: String, @Body body: com.jellio.android.data.model.FriendMuteBody): com.jellio.android.data.model.FriendMuteResultDto

    @POST("Jellio/watchlist/friends")
    suspend fun addToFriendWatchlists(@Body body: com.jellio.android.data.model.WatchlistFriendsBody): com.jellio.android.data.model.WatchlistFriendsResponseDto

    @GET("Jellio/watchlist/added-by")
    suspend fun getWatchlistAddedBy(): com.jellio.android.data.model.AddedByResponseDto

    @POST("Jellio/profile/friends-settings")
    suspend fun setFriendsSettings(@Body body: com.jellio.android.data.model.FriendsSettingsBody): com.jellio.android.data.model.FriendsSettingsDto

    @GET("Jellio/achievements/{userId}")
    suspend fun getAchievements(@Path("userId") userId: String): AchievementsDto

    @DELETE("Jellio/profile/banner")
    suspend fun deleteProfileBanner()

    @POST("Jellio/achievements/real-watch")
    suspend fun creditRealWatch(@Body body: RealWatchRequest)

    @POST("Jellio/real-duration")
    suspend fun reportRealDuration(@Body body: ReportDurationRequest)

    @GET("Jellio/anisync/settings")
    suspend fun getAniSyncSettings(): com.jellio.android.data.model.AniSyncSettingsDto

    @retrofit2.http.PUT("Jellio/anisync/preferences")
    suspend fun updateAniSyncPreferences(@Body body: com.jellio.android.data.model.AniSyncPreferencesRequest): com.jellio.android.data.model.AniSyncSettingsDto

    @POST("Jellio/anisync/connect")
    suspend fun connectAniSync(@Body body: com.jellio.android.data.model.AniSyncConnectRequest): com.jellio.android.data.model.AniSyncConnectResultDto

    @DELETE("Jellio/anisync/disconnect")
    suspend fun disconnectAniSync(@Query("service") service: String): com.jellio.android.data.model.AniSyncSettingsDto

    @POST("Jellio/anisync/test")
    suspend fun testAniSync(@Body body: com.jellio.android.data.model.AniSyncServiceRequest): com.jellio.android.data.model.AniSyncTestResultDto

    @POST("Jellio/anisync/sync-now")
    suspend fun syncAniSyncNow(@Body body: com.jellio.android.data.model.AniSyncSyncNowRequest): com.jellio.android.data.model.AniSyncSyncNowResultDto

    @POST("Jellio/anisync/simkl/pin")
    suspend fun startSimklPin(@Body body: com.jellio.android.data.model.SimklPinRequest): com.jellio.android.data.model.SimklPinStartDto

    @POST("Jellio/anisync/simkl/pin/poll")
    suspend fun pollSimklPin(@Body body: com.jellio.android.data.model.SimklPinPollRequest): com.jellio.android.data.model.SimklPinPollResultDto
}

// Sent on every request's Authorization header, so Jellyfin's device list
// shows the installed release.
val APP_VERSION: String = "1.0.0"

fun buildEmbyAuthorizationHeader(deviceId: String, appVersion: String, token: String? = null): String {
    val deviceName = android.os.Build.MODEL.replace("\"", "")
    val base = "MediaBrowser Client=\"Jellio Android\", Device=\"$deviceName\", DeviceId=\"$deviceId\", Version=\"$appVersion\""
    return if (!token.isNullOrEmpty()) "$base, Token=\"$token\"" else base
}
