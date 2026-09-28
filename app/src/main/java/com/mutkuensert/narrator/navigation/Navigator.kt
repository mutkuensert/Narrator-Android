package com.mutkuensert.narrator.navigation

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Navigator @Inject constructor() {
    private val _commands = MutableSharedFlow<NavigationCommand>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    internal val commands = _commands.asSharedFlow()

    fun navigateToRoute(route: Any) {
        _commands.tryEmit(NavigationCommand.ToRoute(route))
    }

    fun navigateBack() {
        _commands.tryEmit(NavigationCommand.Back)
    }
}

internal sealed interface NavigationCommand {
    data class ToRoute(val route: Any) : NavigationCommand
    data object Back : NavigationCommand
}

