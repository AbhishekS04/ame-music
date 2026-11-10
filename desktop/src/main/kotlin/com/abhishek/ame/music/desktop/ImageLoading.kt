package com.abhishek.ame.music.desktop

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient

/**
 * Coil's loader for the window.
 *
 * sharedUi asks Coil for every piece of artwork, and Coil with no network
 * fetcher answers nothing — which is what an empty shelf card looks like. The
 * phone supplies this itself in app/build.gradle.kts; here it is the desktop's
 * job. The User-Agent is the same one the phone sends, because Wikimedia
 * (which serves the artist artwork) answers generic clients with 403.
 */
object ImageLoading : SingletonImageLoader.Factory {

    val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "Ame/0.1.1 (Linux; +https://github.com/AbhishekS04/ame-music)")
                    .build(),
            )
        }
        .build()

    override fun newImageLoader(context: coil3.PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .build()
}
