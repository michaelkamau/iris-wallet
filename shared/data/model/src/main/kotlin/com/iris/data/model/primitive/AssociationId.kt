package com.iris.data.model.primitive

import com.iris.data.model.sync.UniqueId
import java.util.UUID

@JvmInline
value class AssociationId(override val value: UUID) : UniqueId