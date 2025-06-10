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
import kotlinx.coroutines.delay
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
    
    private val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode = _isOfflineMode.asStateFlow()
    
    val columnOrder = listOf("todo", "inProgress", "done")

    init {
        initializeApp()
    }

    private fun initializeApp() {
        viewModelScope.launch {
            try {
                Log.d("KanbanViewModel", "Initializing app...")
                
                // First try to initialize Firebase with a timeout
                val initSuccess = initializeFirebaseWithTimeout()
                
                if (initSuccess) {
                    Log.d("KanbanViewModel", "Firebase initialized successfully")
                    signInAndFetchBoard()
                } else {
                    Log.w("KanbanViewModel", "Firebase initialization failed, starting offline mode")
                    startOfflineMode()
                }
                
            } catch (e: Exception) {
                Log.e("KanbanViewModel", "App initialization failed", e)
                startOfflineMode()
            }
        }
    }

    private suspend fun initializeFirebaseWithTimeout(): Boolean {
        return try {
            // Try to access Firebase with a timeout
            kotlinx.coroutines.withTimeout(10000) { // 10 second timeout
                Log.d("KanbanViewModel", "Testing Firebase connection...")
                
                // Test Firebase connection by trying to get current user
                val currentUser = auth.currentUser
                Log.d("KanbanViewModel", "Firebase connection test completed. Current user: $currentUser")
                
                true
            }
        } catch (e: Exception) {
            Log.e("KanbanViewModel", "Firebase connection test failed", e)
            false
        }
    }

    private fun startOfflineMode() {
        Log.d("KanbanViewModel", "Starting offline mode")
        _isOfflineMode.value = true
        
        // Create initial board data for offline use
        val initialColumns = mapOf(
            "todo" to ColumnData(name = "To Do", items = listOf(
                Task(id = "offline-1", content = "欢迎使用离线模式！"),
                Task(id = "offline-2", content = "您可以添加、移动和删除任务")
            )),
            "inProgress" to ColumnData(name = "In Progress", items = listOf(
                Task(id = "offline-3", content = "离线数据不会同步到服务器")
            )),
            "done" to ColumnData(name = "Done", items = listOf(
                Task(id = "offline-4", content = "配置Firebase后可启用云同步")
            ))
        )
        
        val offlineBoard = BoardState(columns = initialColumns)
        _boardState.value = offlineBoard
        _isLoading.value = false
    }

    private fun signInAndFetchBoard() {
        viewModelScope.launch {
            try {
                Log.d("KanbanViewModel", "Starting authentication...")
                
                if (auth.currentUser == null) {
                    Log.d("KanbanViewModel", "No current user, attempting anonymous sign-in...")
                    val result = auth.signInAnonymously().await()
                    Log.d("KanbanViewModel", "Anonymous sign-in successful: ${result.user?.uid}")
                } else {
                    Log.d("KanbanViewModel", "User already signed in: ${auth.currentUser?.uid}")
                }
                
                Log.d("KanbanViewModel", "Setting up Firestore listener...")
                boardDocRef.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("KanbanViewModel", "Firestore listener error", error)
                        if (_boardState.value == null) {
                            // If no data loaded yet, start offline mode
                            startOfflineMode()
                        }
                        return@addSnapshotListener
                    }

                    if (snapshot != null && snapshot.exists()) {
                        Log.d("KanbanViewModel", "Board data found, updating state")
                        _boardState.value = snapshot.toObject<BoardState>()
                        _isOfflineMode.value = false
                    } else {
                        Log.d("KanbanViewModel", "No board data found, creating initial board")
                        createInitialBoard()
                    }
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e("KanbanViewModel", "Authentication or setup failed", e)
                startOfflineMode()
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
                _boardState.value = initialBoard // Use local data
            }
        }
    }
    
    private fun updateFirestore(newState: BoardState) {
        // Update local state immediately for better UX
        _boardState.value = newState
        
        if (_isOfflineMode.value) {
            Log.d("KanbanViewModel", "Offline mode: changes saved locally only")
            return
        }
        
        viewModelScope.launch {
            try {
                Log.d("KanbanViewModel", "Updating Firestore...")
                boardDocRef.set(newState).await()
                Log.d("KanbanViewModel", "Firestore update successful")
            } catch (e: Exception) {
                Log.e("KanbanViewModel", "Failed to update Firestore", e)
                // Keep the local changes even if sync fails
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