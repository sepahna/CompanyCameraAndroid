package ir.mbarani.companycamera

import kotlinx.coroutines.flow.Flow
import java.io.File

class PhotoRepository(
    private val dao: PhotoDao,
    private val commentDao: PhotoCommentDao
) {
    fun observePhotos(): Flow<List<Photo>> = dao.observeAll()

    suspend fun add(photo: Photo): Long = dao.insert(photo)

    suspend fun all() = dao.all()

    suspend fun update(photo: Photo) {
        val current = dao.getById(photo.id) ?: return
        var updated = photo
        if (current.fileName != photo.fileName || current.project != photo.project) {
            val oldFile = File(current.relativePath)
            val targetDir = oldFile.parentFile?.let { parent ->
                if (current.project != photo.project) {
                    val photosRoot = parent.parentFile
                    File(photosRoot, photo.project).apply { mkdirs() }
                } else {
                    parent
                }
            } ?: oldFile.parentFile
            if (targetDir != null) {
                var target = File(targetDir, photo.fileName)
                if (target.exists() && target.absolutePath != oldFile.absolutePath) {
                    target = File(targetDir, "${System.currentTimeMillis()}-${photo.fileName}")
                    updated = photo.copy(fileName = target.name)
                }
                if (oldFile.exists() && oldFile.absolutePath != target.absolutePath) {
                    oldFile.copyTo(target, overwrite = false)
                    oldFile.delete()
                }
                updated = updated.copy(relativePath = target.absolutePath)
            }
        }
        dao.update(updated)
    }

    suspend fun delete(photo: Photo) {
        commentDao.deleteForPhoto(photo.id)
        dao.delete(photo.id)
        val file = File(photo.relativePath)
        if (file.exists()) file.delete()
    }

    fun observeComments(photoId: Long): Flow<List<PhotoComment>> =
        commentDao.observeForPhoto(photoId)

    suspend fun addComment(photoId: Long, text: String): Long =
        commentDao.insert(
            PhotoComment(
                photoId = photoId,
                text = text.trim(),
                createdAt = System.currentTimeMillis()
            )
        )

    suspend fun deleteComment(id: Long) = commentDao.delete(id)
}
