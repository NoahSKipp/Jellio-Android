package com.jellio.android.data.books

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ReadingListApi {
    @GET("Jellio/reading-list")
    suspend fun list(): ReadingListResponseDto

    // A 404 here means the plugin predates the reading list.
    @GET("Jellio/reading-list/keys")
    suspend fun keys(): ReadingKeysResponseDto

    // Refusals carry a JSON { Error } body, so the response is read by hand.
    @POST("Jellio/reading-list")
    suspend fun add(@Body body: ReadingAddBody): Response<ReadingAddResultDto>

    @DELETE("Jellio/reading-list")
    suspend fun remove(@Query("key") key: String): Response<ReadingRemoveResultDto>
}
