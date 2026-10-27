package com.example.dsmproyectodecatedra.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class NewsUiState(
    val loading: Boolean = true,
    val articles: List<NewsArticle> = emptyList(),
    val error: String? = null
)

class NewsViewModel(private val repository: NewsRepository = NewsRepository()) : ViewModel() {
    private val _state = MutableStateFlow(NewsUiState())
    val state: StateFlow<NewsUiState> = _state

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val articles = repository.fetchLatest(5)
                _state.value = NewsUiState(loading = false, articles = articles)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = "No se pudieron cargar las noticias. Verifica tu conexión."
                )
            }
        }
    }
}
