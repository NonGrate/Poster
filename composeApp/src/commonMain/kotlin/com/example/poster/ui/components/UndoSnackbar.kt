package com.example.poster.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.poster.model.Post
import com.example.poster.viewmodel.FavoritesViewModel
import com.example.poster.ui.platform.AdaptiveUndoBar
import com.example.poster.viewmodel.FavoritesViewModel.UndoAction
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.undo
import poster.composeapp.generated.resources.undo_liked
import poster.composeapp.generated.resources.undo_unliked

/**
 * Undo affordance for favouriting. The ViewModel owns the state and its 4s
 * lifetime; this renders whatever the platform's idiom is (divergence #7).
 */
@Composable
fun UndoSnackbar(
    favoritesViewModel: FavoritesViewModel = koinInject(),
    modifier: Modifier = Modifier,
) {
    val undoState by favoritesViewModel.undoState.collectAsState()
    val state = undoState ?: return

    // Copy lives here, not in the ViewModel: the ViewModel reports which way the
    // toggle went, the UI says it in the user's language.
    AdaptiveUndoBar(
        message = when (state.action) {
            UndoAction.ADDED_TO_FAVORITES ->
                stringResource(Res.string.undo_liked, state.post.title)
            UndoAction.REMOVED_FROM_FAVORITES ->
                stringResource(Res.string.undo_unliked, state.post.title)
        },
        actionLabel = stringResource(Res.string.undo),
        onAction = { favoritesViewModel.performUndo() },
        modifier = modifier.testTag("undo_snackbar"),
    )
}

data class UndoState(
    val post: Post,
    val action: UndoAction,
)