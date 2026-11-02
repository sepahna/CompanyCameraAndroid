package ir.mbarani.companycamera

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoCommentDao {
    @Insert
    suspend fun insert(comment: PhotoComment): Long

    @Query("SELECT * FROM photo_comments WHERE photoId = :photoId ORDER BY createdAt ASC")
    fun observeForPhoto(photoId: Long): Flow<List<PhotoComment>>

    @Query("DELETE FROM photo_comments WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM photo_comments WHERE photoId = :photoId")
    suspend fun deleteForPhoto(photoId: Long)
}
