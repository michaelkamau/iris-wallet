package com.iris.wallet.domain.data

interface Reorderable {
    fun getItemOrderNum(): Double

    fun withNewOrderNum(newOrderNum: Double): Reorderable
}
