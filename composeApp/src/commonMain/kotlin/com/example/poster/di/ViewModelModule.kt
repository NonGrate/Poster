package com.example.poster.di

import com.example.poster.viewmodel.BookmarksViewModel
import com.example.poster.viewmodel.FollowsViewModel
import com.example.poster.notification.PushRegistrar
import com.example.poster.viewmodel.NotificationsViewModel
import com.example.poster.viewmodel.CommentsViewModel
import com.example.poster.billing.SupportRepository
import com.example.poster.di.AppConfig
import com.example.poster.viewmodel.SupportViewModel
import org.koin.dsl.module
import com.example.poster.viewmodel.AccountViewModel
import com.example.poster.viewmodel.FavoritesViewModel
import com.example.poster.viewmodel.PostsViewModel
import com.example.poster.viewmodel.GroupViewModel
import com.example.poster.viewmodel.FeedbackViewModel
import com.example.poster.viewmodel.TagViewModel
import com.example.poster.viewmodel.ThemeViewModel
import com.example.poster.config.Features

fun viewModelModule() = module {
    single { SupportRepository(apiKey = get<AppConfig>().revenueCatApiKey, dispatchers = get()) }
    single {
        SupportViewModel(
            support = get(),
            session = get(),
            dispatchers = get(),
            demoPaywall = get<AppConfig>().demoPaywall,
        )
    }
    single {
        PostsViewModel(
            repository = get(),
            session = get(),
            dispatchers = get(),
            events = get(),
        )
    }
    single { AccountViewModel(get(), get(), get()) }
    single {
        FavoritesViewModel(
            repository = get(),
            session = get(),
            dispatchers = get()
        )
    }
    single { TagViewModel(get(), get()) }
    single { com.example.poster.ui.images.PostImageLoader(get()) }
    single { GroupViewModel(get(), get(), get()) }
    // Gated where the only screen that resolves one is itself behind the flag.
    // FollowsViewModel, BookmarksViewModel and PushRegistrar are not: HomeScreen
    // and MainScreen inject them before any flag is looked at.
    if (Features.FEEDBACK) single { FeedbackViewModel(get(), get()) }
    if (Features.COMMENTS) single { CommentsViewModel(get(), get()) }
    if (Features.PUSH_NOTIFICATIONS) single { NotificationsViewModel(get(), get()) }
    single { FollowsViewModel(get(), get()) }
    single { BookmarksViewModel(get(), get()) }
    single { PushRegistrar(session = get(), notifications = get(), dispatchers = get()) }
    single { ThemeViewModel(get(), get()) }
}
