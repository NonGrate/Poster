package com.example.poster.viewmodel

import com.example.poster.model.Comment
import com.example.poster.network.EmailNotVerified
import com.example.poster.repository.CommentRepository
import com.example.poster.util.DispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The thread under one post. A singleton, like the other ViewModels here:
 * [load] with a different post replaces the list, so two Details screens in
 * a row do not show each other's comments while the fetch is in flight.
 */
class CommentsViewModel(
    private val comments: CommentRepository,
    dispatchers: DispatcherProvider,
) : ScopedViewModel(dispatchers) {
    private var postGuid: String? = null

    private val _items = MutableStateFlow<List<Comment>>(emptyList())
    val items: StateFlow<List<Comment>> = _items.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private val _error = MutableStateFlow<UiError?>(null)
    val error: StateFlow<UiError?> = _error.asStateFlow()

    private val _needsVerifiedEmail = MutableStateFlow(false)
    val needsVerifiedEmail: StateFlow<Boolean> = _needsVerifiedEmail.asStateFlow()

    suspend fun load(post: String) {
        if (postGuid != post) {
            postGuid = post
            _items.value = emptyList()
        }
        _loading.value = true
        comments.forPost(post).fold(
            onSuccess = { if (postGuid == post) _items.value = it },
            onFailure = { _error.value = UiError("Unable to load comments", it) },
        )
        _loading.value = false
    }

    /** Returns whether the comment went through; the list is updated when it did. */
    suspend fun add(post: String, text: String): Boolean {
        _sending.value = true
        val sent = comments.add(post, text).fold(
            onSuccess = { added ->
                if (postGuid == post) _items.value = _items.value + added
                true
            },
            onFailure = {
                if (it is EmailNotVerified) _needsVerifiedEmail.value = true
                else _error.value = UiError("Unable to add comment", it)
                false
            },
        )
        _sending.value = false
        return sent
    }

    suspend fun delete(post: String, comment: Comment) {
        // Gone from the screen at once; put back if the server disagrees.
        val before = _items.value
        _items.value = before.filterNot { it.guid == comment.guid }
        comments.delete(post, comment.guid).onFailure {
            _items.value = before
            _error.value = UiError("Unable to remove comment", it)
        }
    }

    fun acknowledgeError() { _error.value = null }
    fun acknowledgeVerification() { _needsVerifiedEmail.value = false }
}
