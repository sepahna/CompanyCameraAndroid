package ir.mbarani.companycamera

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "photo_comments",
    foreignKeys = [
        ForeignKey(
            entity = Photo::class,
            parentColumns = ["id"],
            childColumns = ["photoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("photoId")]
)
data class PhotoComment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val photoId: Long,
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)
