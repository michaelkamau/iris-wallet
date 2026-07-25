package com.iris.data.repository.fake

import com.iris.base.TestDispatchersProvider
import com.iris.data.DataObserver
import com.iris.data.repository.RepositoryMemoFactory
import org.jetbrains.annotations.VisibleForTesting

@VisibleForTesting
fun fakeRepositoryMemoFactory(): RepositoryMemoFactory = RepositoryMemoFactory(
    dataObserver = DataObserver(),
    dispatchers = TestDispatchersProvider
)