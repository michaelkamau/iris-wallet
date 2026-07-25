package com.iris.contributors

sealed interface ContributorsEvent {
    data object TryAgainButtonClicked : ContributorsEvent
}
