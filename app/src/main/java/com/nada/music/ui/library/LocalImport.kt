package com.nada.music.ui.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.nada.music.data.local.LocalMusic
import com.nada.music.ui.LocalRepository
import kotlinx.coroutines.launch

class LocalImportActions(
    val scan: () -> Unit,
    val pickFiles: () -> Unit,
    val busy: Boolean,
)

/** Aksi "Pindai HP" (MediaStore, minta izin dulu) dan "Pilih file" (pemilih file Android). */
@Composable
fun rememberLocalImport(): LocalImportActions {
    val context = LocalContext.current
    val repository = LocalRepository.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    fun runScan() {
        busy = true
        scope.launch {
            val found = runCatching { LocalMusic.scanDevice(context) }.getOrDefault(emptyList())
            repository.addLocalSongs(found)
            busy = false
            Toast.makeText(
                context,
                if (found.isEmpty()) "Tidak ada lagu ditemukan di HP" else "${found.size} lagu ditemukan",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            runScan()
        } else {
            Toast.makeText(context, "Izin akses musik ditolak. Gunakan \"Pilih file\" sebagai gantinya.", Toast.LENGTH_LONG).show()
        }
    }

    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val songs = LocalMusic.fromPickedFiles(context, uris)
            repository.addLocalSongs(songs)
            busy = false
            Toast.makeText(context, "${songs.size} lagu ditambahkan", Toast.LENGTH_SHORT).show()
        }
    }

    return LocalImportActions(
        scan = {
            if (ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED) {
                runScan()
            } else {
                permissionLauncher.launch(audioPermission)
            }
        },
        pickFiles = { pickerLauncher.launch(arrayOf("audio/*")) },
        busy = busy,
    )
}
