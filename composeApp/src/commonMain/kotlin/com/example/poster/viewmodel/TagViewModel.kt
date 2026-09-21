package com.example.poster.viewmodel

import com.example.poster.model.Tag
import com.example.poster.repository.TagRepository
import com.example.poster.util.DispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * ViewModel for managing tags.
 * This handles loading tag suggestions and other tag-related operations.
 */
class TagViewModel(
    private val tagRepository: TagRepository,
    dispatchers: DispatcherProvider
) : ScopedViewModel(dispatchers) {

    private val _tagSuggestions = MutableStateFlow<List<String>>(emptyList())
    val tagSuggestions: StateFlow<List<String>> = _tagSuggestions.asStateFlow()

    /**
     * The tags themselves, with their labels. The picker searches these, so
     * typing "здор" and typing "heal" find the same tag rather than creating
     * two that mean the same thing.
     */
    private val _tags = MutableStateFlow<List<Tag>>(emptyList())
    val tags: StateFlow<List<Tag>> = _tags.asStateFlow()

    private val _error = MutableStateFlow<UiError?>(null)
    val error: StateFlow<UiError?> = _error.asStateFlow()

    init {
        loadTagSuggestions()
    }

    fun loadTagSuggestions(): Job = scope.launch {
            runCatching { tagRepository.getAllTags() }
                .onSuccess { tags ->
                    _tagSuggestions.value = tags.map { it.name }
                    _tags.value = tags
                }
                .onFailure { throwable ->
                    _tagSuggestions.value = emptyList()
                    _error.value = UiError("Unable to load tags", throwable)
                }
    }

    fun refreshTagSuggestions(): Job = scope.launch {
        runCatching { tagRepository.refreshTags() }
            .onSuccess { tags ->
                _tagSuggestions.value = tags.map { it.name }
                _tags.value = tags
            }
            .onFailure { throwable ->
                _error.value = UiError("Unable to refresh tags", throwable)
            }
    }

    fun acknowledgeError() {
        _error.value = null
    }
}
