package ir.mbarani.companycamera

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CompanyCameraViewModel(app: Application) : AndroidViewModel(app) {
    private val db = Room.databaseBuilder(app, AppDatabase::class.java, "company-camera.db")
        .addMigrations(AppDatabase.MIGRATION_1_2)
        .build()

    private val repository = PhotoRepository(db.photoDao(), db.photoCommentDao())

    val photos = repository.observePhotos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _category = MutableStateFlow("MACHINERY")
    val category = _category.asStateFlow()

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    fun setCategory(value: String) {
        _category.value = value
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun add(photo: Photo) = viewModelScope.launch { repository.add(photo) }

    fun update(photo: Photo) = viewModelScope.launch { repository.update(photo) }

    fun delete(photo: Photo) = viewModelScope.launch { repository.delete(photo) }

    fun comments(photoId: Long): Flow<List<PhotoComment>> = repository.observeComments(photoId)

    fun addComment(photoId: Long, text: String) = viewModelScope.launch {
        if (text.isNotBlank()) repository.addComment(photoId, text)
    }

    fun deleteComment(id: Long) = viewModelScope.launch { repository.deleteComment(id) }
}
