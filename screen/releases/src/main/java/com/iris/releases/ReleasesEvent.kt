package com.iris.releases

sealed interface ReleasesEvent {
    data object OnTryAgainClick : ReleasesEvent
}
