package com.jellio.android.di

import com.jellio.android.data.network.GitHubApi
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GitHubModule {
    @Provides
    @Singleton
    fun provideGitHubApi(moshi: Moshi): GitHubApi = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(
            com.jellio.android.data.network.SharedHttp.plainClient.newBuilder()
                // GitHub's API asks every client for a User-Agent naming it.
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("User-Agent", "Jellio-Android/${com.jellio.android.data.network.APP_VERSION}")
                            .header("Accept", "application/vnd.github+json")
                            .build(),
                    )
                }
                .build(),
        )
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GitHubApi::class.java)
}
