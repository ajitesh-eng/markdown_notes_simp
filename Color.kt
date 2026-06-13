package com.example.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.Note
import com.example.data.NoteRepository
import com.example.sync.SyncClient
import com.example.sync.SyncServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotesViewModel(
    application: Application,
    private val repository: NoteRepository
) : AndroidViewModel(application) {

    // Reactive Notes list
    val notes: StateFlow<List<Note>> = repository.activeNotes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Editing Note State
    var selectedNoteId by mutableStateOf<Long?>(null)
        private set

    var currentEditTitle by mutableStateOf("")
    var currentEditContent by mutableStateOf("")

    // Sync States
    var syncServerStatus by mutableStateOf("Offline")
    var isSyncLoading by mutableStateOf(false)
    var syncClientResult by mutableStateOf<String?>(null)
    var targetServerIp by mutableStateOf("")
    var targetServerPort by mutableStateOf("9090")

    private val syncServer = SyncServer(repository)
    private val syncClient = SyncClient(repository)

    init {
        // Automatically bind server updates
        syncServer.onStatusChanged = { status ->
            viewModelScope.launch {
                syncServerStatus = status
            }
        }
        // Prefill some default IP based on current subnet to make it easy for user
        val myIp = SyncServer.getLocalIpAddress()
        if (myIp != "127.0.0.1") {
            val baseIp = myIp.substringBeforeLast(".")
            targetServerIp = "$baseIp."
        }
    }

    fun selectNoteId(noteId: Long?) {
        selectedNoteId = noteId
        if (noteId != null) {
            viewModelScope.launch {
                val dbNote = repository.getNoteById(noteId)
                if (dbNote != null) {
                    currentEditTitle = dbNote.title
                    currentEditContent = dbNote.content
                }
            }
        } else {
            currentEditTitle = ""
            currentEditContent = ""
        }
    }

    fun updateEditFields(title: String, content: String) {
        currentEditTitle = title
        currentEditContent = content
    }

    fun saveCurrentNote(onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            val titleText = currentEditTitle.ifBlank { "Untitled Note" }
            val noteId = selectedNoteId

            if (noteId == null) {
                // Insert new note
                val newNote = Note(title = titleText, content = currentEditContent)
                val generatedId = repository.insert(newNote)
                selectedNoteId = generatedId
                onSaved(generatedId)
            } else {
                // Update existing
                repository.getNoteById(noteId)?.let { existingNote ->
                    val updatedNote = existingNote.copy(
                        title = titleText,
                        content = currentEditContent
                    )
                    repository.update(updatedNote)
                    onSaved(noteId)
                }
            }
        }
    }

    fun createNewEmptyNote(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val newNote = Note(title = "New Note", content = "")
            val newId = repository.insert(newNote)
            selectNoteId(newId)
            onCreated(newId)
        }
    }

    fun deleteNoteById(id: Long, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.softDelete(id)
            if (selectedNoteId == id) {
                selectNoteId(null)
            }
            onDeleted()
        }
    }

    fun startSyncHost() {
        viewModelScope.launch {
            syncServer.start()
        }
    }

    fun stopSyncHost() {
        syncServer.stop()
    }

    fun triggerClientSync() {
        if (targetServerIp.isBlank()) {
            syncClientResult = "Error: Target IP address cannot be blank."
            return
        }
        val portInt = targetServerPort.toIntOrNull() ?: 9090
        isSyncLoading = true
        syncClientResult = null

        viewModelScope.launch {
            val result = syncClient.syncWithRemoteServer(targetServerIp, portInt)
            isSyncLoading = false
            syncClientResult = result.fold(
                onSuccess = { "Success! $it" },
                onFailure = { "Sync failed: ${it.localizedMessage}" }
            )
        }
    }

    fun handleExternalContentOverride(newContent: String) {
        currentEditContent = newContent
        val noteId = selectedNoteId
        if (noteId != null) {
            viewModelScope.launch {
                repository.getNoteById(noteId)?.let { existing ->
                    repository.update(existing.copy(content = newContent))
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        syncServer.stop()
    }

    // Direct Factory implementation
    class Factory(
        private val application: Application,
        private val repository: NoteRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(NotesViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return NotesViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
