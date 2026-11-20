package com.jellio.android.data

import com.jellio.android.data.model.IntroCreditsScanDto
import com.jellio.android.data.network.JellyfinApi
import javax.inject.Inject
import javax.inject.Singleton

// What the card options menu asks the plugin: the reader's Grouplist, the
// admin skip timestamp scan, and the flags that decide which entries show.
@Singleton
class CardMenuRepository @Inject constructor(
    private val api: JellyfinApi,
    private val repository: JellioRepository,
) {
    // GrouplistEnabled lives on the reader's own profile settings.
    suspend fun isGrouplistEnabled(): Boolean =
        catching { api.getProfileSettings().GrouplistEnabled }.getOrDefault(false)

    suspend fun grouplistIds(): Set<String> =
        catching { api.getGrouplist().ItemIds.map(::normalizeGuid).toSet() }.getOrDefault(emptySet())

    suspend fun setOnGrouplist(itemId: String, on: Boolean): Set<String> {
        val list = if (on) api.addToGrouplist(itemId) else api.removeFromGrouplist(itemId)
        return list.ItemIds.map(::normalizeGuid).toSet()
    }

    suspend fun isAdmin(userId: String): Boolean =
        catching { repository.getUser(userId).Policy?.IsAdministrator == true }.getOrDefault(false)

    suspend fun isSkipMenuEnabled(): Boolean =
        catching { api.getJellioConfig().SkipIntroCreditsAdminMenuEnabled }.getOrDefault(false)

    suspend fun scanSkipTimestamps(itemId: String, useAnalyzerFallback: Boolean): IntroCreditsScanDto =
        api.scanIntroCredits(itemId, useAnalyzerFallback)
}
