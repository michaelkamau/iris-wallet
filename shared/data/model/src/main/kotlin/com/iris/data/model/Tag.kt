package com.iris.data.model

import androidx.compose.runtime.Immutable
import com.iris.data.model.primitive.AssociationId
import com.iris.data.model.primitive.ColorInt
import com.iris.data.model.primitive.IconAsset
import com.iris.data.model.primitive.NotBlankTrimmedString
import com.iris.data.model.sync.Identifiable
import com.iris.data.model.sync.UniqueId
import java.time.Instant
import java.util.UUID

data class Tag(
    override val id: TagId,
    val name: NotBlankTrimmedString,
    val description: String?,
    val color: ColorInt,
    val icon: IconAsset?,
    override val orderNum: Double,
    val creationTimestamp: Instant,
) : Identifiable<TagId>, Reorderable

@Suppress("DataClassTypedIDs")
data class TagAssociation(
    override val id: TagId,
    val associatedId: AssociationId,
) : Identifiable<TagId>

@JvmInline
@Immutable
value class TagId(override val value: UUID) : UniqueId