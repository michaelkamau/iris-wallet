package com.iris.accounts

sealed interface AccountsEvent {
    data class OnReorder(val reorderedList: List<com.iris.legacy.data.model.AccountData>) :
        AccountsEvent
    data class OnReorderModalVisible(val reorderVisible: Boolean) : AccountsEvent
}
