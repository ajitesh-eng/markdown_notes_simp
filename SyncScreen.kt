package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    fun getAllActiveNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    suspend fun getActiveNotesList(): List<Note>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun getNoteFlowById(id: Long): Flow<Note?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Long): Note?

    @Query("SELECT * FROM notes WHERE syncId = :syncId LIMIT 1")
    suspend fun getNoteBySyncId(syncId: String): Note?

    @Query("SELECT * FROM notes")
    suspend fun getAllNotesWithDeleted(): List<Note>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Query("UPDATE notes SET isDeleted = 1, updatedAt = :updatedAt, isSynced = 0 WHERE id = :id")
    suspend fun softDeleteNote(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET isDeleted = 1, updatedAt = :updatedAt, isSynced = 0 WHERE syncId = :syncId")
    suspend fun softDeleteNoteBySyncId(syncId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM notes WHERE syncId = :syncId")
    suspend fun hardDeleteBySyncId(syncId: String)
}
