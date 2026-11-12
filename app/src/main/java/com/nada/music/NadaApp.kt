package com.nada.music

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.nada.music.data.MusicRepository
import com.nada.music.data.db.AppDatabase
import com.nada.music.data.lyrics.LrcLib
import com.nada.music.data.youtube.NewPipeDownloader
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale
import java.util.concurrent.TimeUnit

class NadaApp : Application(), ImageLoaderFactory {

    lateinit var okHttpClient: OkHttpClient
        private set
    lateinit var repository: MusicRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val locale = Locale.getDefault()
        val country = locale.country.ifBlank { "ID" }
        NewPipe.init(
            NewPipeDownloader(okHttpClient),
            Localization(locale.language.ifBlank { "id" }, country),
            ContentCountry(country),
        )

        val db = AppDatabase.create(this)
        repository = MusicRepository(db.dao(), LrcLib(okHttpClient))
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("images"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()

    companion object {
        lateinit var instance: NadaApp
            private set
    }
}
