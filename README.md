# Nada 🎵

Pemutar musik Android open source yang memutar lagu dari **YouTube Music**, tanpa iklan dan tanpa login.
Dibuat dengan Kotlin, Jetpack Compose, Media3 (ExoPlayer), Room, dan NewPipeExtractor.

## Fitur

- **Cari & putar**: cari lagu di YouTube Music (dengan saran pencarian dan infinite scroll), lalu putar di background.
  Ada notifikasi media, kontrol lockscreen, dan kontrol headset/Bluetooth.
- **Antrean**: putar berikutnya, tambah ke antrean, hapus dari antrean, acak, dan ulangi (semua / satu).
- **Radio otomatis**: saat antrean habis, lagu terkait otomatis ditambahkan supaya musik tidak berhenti.
- **Playlist & favorit lokal**: buat, ganti nama, dan hapus playlist; tandai lagu favorit. Riwayat dan
  "paling sering diputar" tersimpan di database lokal (Room).
- **Rekomendasi**: beranda menampilkan lagu terkait dari lagu terakhir yang kamu putar.
- **Lirik tersinkron**: lirik dari [LRCLIB](https://lrclib.net) yang bergulir mengikuti lagu.
  Ketuk satu baris untuk lompat ke bagian itu.
- **Cache audio** 512 MB, jadi lagu yang sering diputar tidak diunduh ulang.
- Material You (warna dinamis di Android 12+), mode gelap/terang.

## Cara kerja singkat

```
UI (Compose) ──► MusicRepository ──► YouTubeMusic (NewPipeExtractor → InnerTube API)
     │                         └──► Room (playlist, favorit, riwayat)
     │                         └──► LrcLib (lirik)
     ▼
PlayerConnection (MediaController) ──► PlaybackService (MediaSession + ExoPlayer)
                                            │
                     nada://song/<videoId>  ▼
                                   StreamResolver ──► URL googlevideo (dicari saat diputar)
                                            │
                                   CacheDataSource ──► OkHttp
```

- Setiap lagu di antrean hanya menyimpan `videoId` (`nada://song/<id>`). URL audio asli baru dicari tepat
  sebelum diputar, karena URL googlevideo kedaluwarsa setelah beberapa jam.
- NewPipeExtractor menangani bagian yang sulit: meniru klien InnerTube, mendekripsi signature dan
  parameter `n`, lalu memilih stream audio.
- Kalau pemutaran gagal karena URL kedaluwarsa (HTTP 403), URL diambil ulang dan lagu dicoba sekali lagi.
- Pencarian memakai filter "Songs" milik YouTube Music. Kalau kosong (terjadi di beberapa wilayah/jaringan),
  otomatis beralih ke filter "Videos".

## Build

Butuh JDK 17+ dan Android SDK (platform 35).

```bash
./gradlew assembleDebug
```

APK ada di `app/build/outputs/apk/debug/app-debug.apk`.

Build release (diperkecil dengan R8, ditandatangani debug key supaya bisa langsung dipasang):

```bash
./gradlew assembleRelease
```

GitHub Actions (`.github/workflows/build.yml`) otomatis membangun APK debug dan release di setiap push ke
`main`. APK bisa diunduh dari tab **Actions**, bagian artefak **nada-apk**.

## Struktur kode

```
app/src/main/java/com/nada/music/
├── NadaApp.kt                 # inisialisasi NewPipe, OkHttp, database, Coil
├── MainActivity.kt
├── data/
│   ├── Song.kt
│   ├── MusicRepository.kt
│   ├── youtube/               # NewPipeDownloader, YouTubeMusic (search, stream, related)
│   ├── lyrics/LrcLib.kt       # lirik + parser LRC + pembersih judul
│   └── db/                    # Room: lagu, playlist, relasi
├── playback/                  # PlaybackService, StreamResolver, PlayerCache, PlayerConnection
└── ui/                        # Compose: home, search, library, player, components, theme
```

## Catatan penting

- Aplikasi ini memakai API internal YouTube (bukan API resmi), sama seperti NewPipe, ViMusic, InnerTune,
  dan sejenisnya. Cara ini **melanggar Ketentuan Layanan YouTube**. Gunakan untuk belajar atau pemakaian
  pribadi, dan jangan mengunggahnya ke Play Store.
- YouTube rutin mengubah sistemnya. Kalau pencarian atau pemutaran tiba-tiba gagal, biasanya cukup
  memperbarui versi `NewPipeExtractor` di `app/build.gradle.kts` ke rilis terbaru
  (<https://github.com/TeamNewPipe/NewPipeExtractor/releases>).
- Terlalu banyak request dari satu IP (misalnya VPN atau server) bisa membuat YouTube meminta captcha atau
  membalas 403. Biasanya ini hilang sendiri setelah beberapa saat.
- Sebelum membagikan APK release, ganti `signingConfig` di `app/build.gradle.kts` dengan keystore milikmu.

## Lisensi

GPL-3.0, mengikuti lisensi NewPipeExtractor.
