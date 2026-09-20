package com.example.poster.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.poster.cache.PostCache
import com.example.poster.repository.FeedbackRepository
import com.example.poster.repository.PostRepository
import com.example.poster.repository.SessionRepository
import com.example.poster.repository.TagRepository
import com.example.poster.util.AppPreferences
import com.example.poster.util.DispatcherProvider
import com.example.poster.viewmodel.AccountViewModel
import com.example.poster.viewmodel.FavoritesViewModel
import com.example.poster.viewmodel.FeedbackViewModel
import com.example.poster.viewmodel.PostsViewModel
import com.example.poster.viewmodel.TagViewModel
import com.example.poster.viewmodel.ThemeViewModel
import kotlinx.coroutines.Dispatchers

/**
 * The fake object graph every `@Preview` needs.
 *
 * Each preview used to assemble this by hand — nine copies of the same dozen
 * lines, which meant every constructor change was a nine-file edit and previews
 * silently drifted from how the app is actually wired.
 */
class PreviewGraph {
    val dispatchers = DispatcherProvider(main = Dispatchers.Default, io = Dispatchers.Default)
    val appPreferences = AppPreferences(FakePlatformDataStore())
    val userApi = FakeUserApi()
    val postApi = FakePostApi()
    val groupApi = FakeGroupApi()
    val tagApi = FakeTagApi()
    val feedbackApi = FakeFeedbackApi()

    val repository = PostRepository(
        postApi = postApi,
        cache = PostCache(),
        dispatchers = dispatchers,
    )
    val session = SessionRepository(userApi, appPreferences, dispatchers)

    val accountViewModel = AccountViewModel(session, dispatchers)
    val postsViewModel = PostsViewModel(repository, session, dispatchers)
    val favoritesViewModel = FavoritesViewModel(repository, session, dispatchers)
    val themeViewModel = ThemeViewModel(appPreferences, dispatchers)
    val tagViewModel = TagViewModel(TagRepository(tagApi), dispatchers)
    val feedbackViewModel = FeedbackViewModel(FeedbackRepository(feedbackApi, dispatchers), dispatchers)
}

/** Kept across recompositions so a preview does not rebuild its world on every frame. */
@Composable
fun rememberPreviewGraph(): PreviewGraph = remember { PreviewGraph() }
