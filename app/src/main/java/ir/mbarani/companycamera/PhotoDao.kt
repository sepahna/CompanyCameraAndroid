package ir.mbarani.companycamera

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Insert
    suspend fun insert(photo: Photo): Long

    @Update
    suspend fun update(photo: Photo)

    @Query("SELECT * FROM photos ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Photo>>

    @Query("SELECT * FROM photos ORDER BY createdAt DESC")
    suspend fun all(): List<Photo>

    @Query("SELECT * FROM photos WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Photo?

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM photos")
    suspend fun clear()
}
