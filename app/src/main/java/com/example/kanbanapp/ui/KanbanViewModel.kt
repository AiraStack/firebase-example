package com.example.kanbanapp.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kanbanapp.data.BoardState
import com.example.kanbanapp.data.ColumnData
import com.example.kanbanapp.data.Task
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class KanbanViewModel : ViewModel() {

    private val db = Firebase.firestore
    val auth = Firebase.auth

    // Replace with your App ID from the web version for compatibility
    private val appId = "default-kanban-app" 
    private val boardId = "main-board"
    private val boardDocRef = db.collection("artifacts/$appId/public/data/kanbanBoards").document(boardId)

    private val _boardState = MutableStateFlow<BoardState?>(null)
    val boardState = _boardState.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()
    
    val columnOrder = listOf("todo", "inProgress", "done")

    init {
        signInAndFetchBoard()
    }

    private fun signInAndFetchBoard() {
        Log.d("KanbanViewModel", "Attempting to sign in and fetch board...")
        viewModelScope.launch {
            try {
                if (auth.currentUser == null) {
                    Log.d("KanbanViewModel", "No current user. Calling signInAnonymously().")
                    auth.signInAnonymously().await()
                    Log.d("KanbanViewModel", "Sign-in successful. User UID: ${auth.currentUser?.uid}")
                } else {
                    Log.d("KanbanViewModel", "User already signed in. UID: ${auth.currentUser?.uid}")
                }

                Log.d("KanbanViewModel", "Setting up Firestore snapshot listener.")
                boardDocRef.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w("KanbanViewModel", "Firestore listener error", error)
                        return@addSnapshotListener
                    }

                    if (snapshot != null && snapshot.exists()) {
                        Log.d("KanbanViewModel", "Board data found, updating state.")
                        _boardState.value = snapshot.toObject<BoardState>()
                    } else {
                        Log.d("KanbanViewModel", "No board data found, creating initial board.")
                        createInitialBoard()
                    }
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                // This is the updated, more detailed error log.
                Log.e("KanbanViewModel", "Authentication or setup failed", e)
                _isLoading.value = false
            }
        }
    }

    private fun createInitialBoard() {
        val initialColumns = mapOf(
            "todo" to ColumnData(name = "To Do", items = emptyList()),
            "inProgress" to ColumnData(name = "In Progress", items = emptyList()),
            "done" to ColumnData(name = "Done", items = emptyList())
        )
        val initialBoard = BoardState(columns = initialColumns)
        viewModelScope.launch {
            try {
                Log.d("KanbanViewModel", "Creating initial board...")
                boardDocRef.set(initialBoard).await()
                Log.d("KanbanViewModel", "Initial board created successfully")
                _boardState.value = initialBoard
            } catch (e: Exception) {
                Log.e("KanbanViewModel", "Failed to create initial board", e)
            }
        }
    }
    
    private fun updateFirestore(newState: BoardState) {
        viewModelScope.launch {
            try {
                Log.d("KanbanViewModel", "Updating Firestore...")
                boardDocRef.set(newState).await()
                Log.d("KanbanViewModel", "Firestore update successful")
            } catch (e: Exception) {
                Log.e("KanbanViewModel", "Failed to update Firestore", e)
            }
        }
    }

    fun addTask(content: String) {
        val currentState = _boardState.value ?: return
        if (content.isBlank()) return

        val newTask = Task(id = "task-${UUID.randomUUID()}", content = content)
        
        val newColumns = currentState.columns.toMutableMap()
        val todoColumn = newColumns["todo"] ?: return
        
        val updatedItems = todoColumn.items.toMutableList().apply { add(newTask) }
        newColumns["todo"] = todoColumn.copy(items = updatedItems)

        updateFirestore(BoardState(columns = newColumns))
    }

    fun deleteTask(columnId: String, taskId: String) {
        val currentState = _boardState.value ?: return
        val newColumns = currentState.columns.toMutableMap()
        val column = newColumns[columnId] ?: return
        
        val updatedItems = column.items.filterNot { it.id == taskId }
        newColumns[columnId] = column.copy(items = updatedItems)
        
        updateFirestore(BoardState(columns = newColumns))
    }
    
    fun moveTask(taskId: String, fromColumnId: String, toColumnId: String) {
        if (fromColumnId == toColumnId) return
        
        val currentState = _boardState.value ?: return
        val newColumns = currentState.columns.toMutableMap()

        val fromColumn = newColumns[fromColumnId] ?: return
        val toColumn = newColumns[toColumnId] ?: return
        
        val taskToMove = fromColumn.items.find { it.id == taskId } ?: return

        val newFromItems = fromColumn.items.filterNot { it.id == taskId }
        val newToItems = toColumn.items.toMutableList().apply { add(taskToMove) }
        
        newColumns[fromColumnId] = fromColumn.copy(items = newFromItems)
        newColumns[toColumnId] = toColumn.copy(items = newToItems)

        updateFirestore(BoardState(columns = newColumns))
    }
} 