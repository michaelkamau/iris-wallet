package com.iris.data.model.sync

interface Identifiable<ID : UniqueId> {
    val id: ID
}
