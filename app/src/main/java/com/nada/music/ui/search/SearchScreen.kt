package com.nada.music.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nada.music.NadaApp
import com.nada.music.data.MusicRepository
import com.nada.music.data.Song
import com.nada.music.ui.LocalPlayer
import com.nada.music.ui.components.EmptyState
import com.nada.music.ui.components.SongRow
import com.nada.music.ui.components.friendlyError
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Error(val message: String) : SearchState
    data class Results(
        val query: String,
        val songs: List<Song>,
        val filter: String,
        val nextPage: Page?,
        val loadingMore: Boolean = false,
    ) : SearchState
}

@OptIn(FlowPreview::class)
class SearchViewModel(private val repository: MusicRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private val _recentQueries = MutableStateFlow<List<String>>(emptyList())
    val recentQueries: StateFlow<List<String>> = _recentQueries.asStateFlow()

    /** true saat pengguna sedang mengetik (tampilkan saran, bukan hasil). */
    private val _editing = MutableStateFlow(true)
    val editing: StateFlow<Boolean> = _editing.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            _query.debounce(300)
                .distinctUntilChanged()
                .filter { _editing.value }
                .collect { q ->
                    _suggestions.value = if (q.isBlank()) emptyList() else repository.suggestions(q)
                }
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value
        _editing.value = true
    }

    fun search(q: String = _query.value) {
        val text = q.trim()
        if (text.isEmpty()) return
        _query.value = text
        _editing.value = false
        _recentQueries.update { (listOf(text) + it.filter { old -> !old.equals(text, true) }).take(10) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.value = SearchState.Loading
            _state.value = runCatching { repository.search(text) }.fold(
                onSuccess = { SearchState.Results(text, it.songs, it.filter, it.nextPage) },
                onFailure = { SearchState.Error(friendlyError(it)) },
            )
        }
    }

    fun loadMore() {
        val current = _state.value as? SearchState.Results ?: return
        val page = current.nextPage ?: return
        if (current.loadingMore) return
        _state.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            val more = runCatching { repository.searchMore(current.query, current.filter, page) }.getOrNull()
            val latest = _state.value as? SearchState.Results ?: return@launch
            if (latest.query != current.query) return@launch
            _state.value = if (more == null) {
                latest.copy(loadingMore = false, nextPage = null)
            } else {
                val known = latest.songs.map { it.id }.toSet()
                latest.copy(
                    songs = latest.songs + more.songs.filter { it.id !in known },
                    nextPage = more.nextPage,
                    loadingMore = false,
                )
            }
        }
    }

    fun clear() {
        _query.value = ""
        _editing.value = true
        _suggestions.value = emptyList()
        _state.value = SearchState.Idle
    }
}

@Composable
fun SearchScreen(contentPadding: PaddingValues) {
    val vm: SearchViewModel = viewModel { SearchViewModel(NadaApp.instance.repository) }
    val player = LocalPlayer.current
    val query by vm.query.collectAsState()
    val state by vm.state.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
    val recent by vm.recentQueries.collectAsState()
    val editing by vm.editing.collectAsState()
    val currentSong by player.currentSong.collectAsState()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (state is SearchState.Idle) focusRequester.requestFocus()
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TextField(
            value = query,
            onValueChange = vm::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .focusRequester(focusRequester),
            placeholder = { Text("Cari lagu, artis…") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = vm::clear) { Icon(Icons.Rounded.Close, "Hapus") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                vm.search()
                focusManager.clearFocus()
            }),
        )

        val showSuggestions = editing && (state !is SearchState.Results || query.isNotBlank()) &&
            (suggestions.isNotEmpty() || (query.isBlank() && recent.isNotEmpty()))

        if (showSuggestions) {
            val items = if (query.isBlank()) recent else suggestions
            LazyColumn(contentPadding = contentPadding) {
                items(items) { text ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                vm.search(text)
                                focusManager.clearFocus()
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (query.isBlank()) Icons.Rounded.History else Icons.AutoMirrored.Rounded.TrendingUp,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            return@Column
        }

        when (val s = state) {
            SearchState.Idle -> EmptyState(
                icon = Icons.Rounded.Search,
                title = "Cari musik",
                message = "Ketik judul lagu atau nama artis dari YouTube Music.",
                modifier = Modifier.padding(top = 48.dp),
            )

            SearchState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            is SearchState.Error -> EmptyState(
                icon = Icons.Rounded.CloudOff,
                title = "Pencarian gagal",
                message = s.message,
                modifier = Modifier.padding(top = 48.dp),
                action = { Button(onClick = { vm.search() }) { Text("Coba lagi") } },
            )

            is SearchState.Results -> {
                if (s.songs.isEmpty()) {
                    EmptyState(
                        icon = Icons.Rounded.MusicOff,
                        title = "Tidak ditemukan",
                        message = "Tidak ada hasil untuk \"${s.query}\".",
                        modifier = Modifier.padding(top = 48.dp),
                    )
                } else {
                    ResultsList(
                        results = s,
                        currentSongId = currentSong?.id,
                        contentPadding = contentPadding,
                        onPlay = { index -> player.playSongs(s.songs, index) },
                        onLoadMore = vm::loadMore,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultsList(
    results: SearchState.Results,
    currentSongId: String?,
    contentPadding: PaddingValues,
    onPlay: (Int) -> Unit,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 5
        }
    }
    LaunchedEffect(shouldLoadMore, results.songs.size) {
        if (shouldLoadMore && results.nextPage != null) onLoadMore()
    }

    LazyColumn(state = listState, contentPadding = contentPadding) {
        itemsIndexed(results.songs, key = { _, song -> song.id }) { index, song ->
            SongRow(song = song, isCurrent = song.id == currentSongId, onClick = { onPlay(index) })
        }
        if (results.loadingMore) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
