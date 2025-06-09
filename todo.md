/*
 * =====================================================================================
 * 1. build.gradle.kts (Module: app)
 * =====================================================================================
 *
 * Add these dependencies to your app-level build.gradle.kts file.
 * You'll also need to set up the Firebase Bill of Materials (BOM) in your
 * project-level build.gradle file.
 */

// plugins {
//     ...
//     id("com.google.gms.google-services")
// }
//
// dependencies {
//     // Core
//     implementation("androidx.core:core-ktx:1.12.0")
//     implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
//     implementation("androidx.activity:activity-compose:1.8.2")
//
//     // Jetpack Compose
//     implementation(platform("androidx.compose:compose-bom:2024.02.01"))
//     implementation("androidx.compose.ui:ui")
//     implementation("androidx.compose.ui:ui-graphics")
//     implementation("androidx.compose.ui:ui-tooling-preview")
//     implementation("androidx.compose.material3:material3")
//     implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
//
//     // Firebase
//     implementation(platform("com.google.firebase:firebase-bom:32.7.4"))
//     implementation("com.google.firebase:firebase-auth-ktx")
//     implementation("com.google.firebase:firebase-firestore-ktx")
//
//     // Confetti Animation
//     implementation("nl.dionsegijn:konfetti-compose:2.0.2")
//
//     // ... other dependencies
// }


/*
 * =====================================================================================
 * 2. DataModels.kt
 * =====================================================================================
 *
 * These data classes define the structure of our Kanban board, matching the
 * structure used in Firestore. Place this in its own file, e.g., `data/DataModels.kt`.
 * The @Keep annotation is recommended to prevent Proguard/R8 from removing fields
 * when you build a release version of the app.
 */

package com.example.kanbanapp.data

import androidx.annotation.Keep
import com.google.firebase.firestore.PropertyName

@Keep
data class Task(
    val id: String = "",
    val content: String = ""
)

@Keep
data class ColumnData(
    val name: String = "",
    val items: List<Task> = emptyList()
)

@Keep
data class BoardState(
    // Using @PropertyName to match the Firestore document field "columns"
    @get:PropertyName("columns") @set:PropertyName("columns")
    var columns: Map<String, ColumnData> = emptyMap()
)

/*
 * =====================================================================================
 * 3. KanbanViewModel.kt
 * =====================================================================================
 *
 * This ViewModel holds the application's state and business logic. It communicates
 * with Firestore and exposes the board state to the UI.
 * Place this in a file like `ui/KanbanViewModel.kt`.
 */

package com.example.kanbanapp.ui

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
    private val auth = Firebase.auth

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
        viewModelScope.launch {
            try {
                if (auth.currentUser == null) {
                    auth.signInAnonymously().await()
                }
                
                boardDocRef.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        // Handle error
                        return@addSnapshotListener
                    }

                    if (snapshot != null && snapshot.exists()) {
                        _boardState.value = snapshot.toObject<BoardState>()
                    } else {
                        // Board doesn't exist, create an initial one
                        createInitialBoard()
                    }
                     _isLoading.value = false
                }
            } catch (e: Exception) {
                // Handle exceptions during auth or initial fetch
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
             boardDocRef.set(initialBoard).await()
            _boardState.value = initialBoard
        }
    }
    
    private fun updateFirestore(newState: BoardState) {
        viewModelScope.launch {
            boardDocRef.set(newState).await()
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


/*
 * =====================================================================================
 * 4. KanbanScreen.kt (and related composables)
 * =====================================================================================
 *
 * This is the main UI of your application. It uses Jetpack Compose to build the
 * Kanban board. This demonstrates a basic drag-and-drop implementation.
 * Place this in `ui/KanbanScreen.kt`.
 */

package com.example.kanbanapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kanbanapp.data.Task
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.util.concurrent.TimeUnit

// --- Drag and Drop State Management ---
class DragDropState {
    var isDragging by mutableStateOf(false)
    var dragPosition by mutableStateOf(Offset.Zero)
    var draggableContent by mutableStateOf<(@Composable () -> Unit)?>(null)
    var dataToDrop by mutableStateOf<Any?>(null)
}

val LocalDragDropState = compositionLocalOf { DragDropState() }

// --- Main Screen ---
@Composable
fun KanbanScreen(viewModel: KanbanViewModel = viewModel()) {
    val boardState by viewModel.boardState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var showConfetti by remember { mutableStateOf(false) }

    val dragDropState = remember { DragDropState() }

    CompositionLocalProvider(LocalDragDropState provides dragDropState) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF0F4FF))) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                boardState?.let {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Header(viewModel.auth.currentUser?.uid)
                        AddTaskSection(onAddTask = { viewModel.addTask(it) })
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            viewModel.columnOrder.forEach { columnId ->
                                val columnData = it.columns[columnId]
                                if (columnData != null) {
                                    KanbanColumn(
                                        columnId = columnId,
                                        columnData = columnData,
                                        tasks = columnData.items,
                                        onMoveTask = { taskId, fromColumn ->
                                            viewModel.moveTask(taskId, fromColumn, columnId)
                                            if (columnId == "done") {
                                                showConfetti = true
                                            }
                                        },
                                        onDeleteTask = { taskId ->
                                            viewModel.deleteTask(columnId, taskId)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            if (dragDropState.isDragging) {
                 Box(modifier = Modifier.offset(dragDropState.dragPosition.x.dp, dragDropState.dragPosition.y.dp)) {
                    dragDropState.draggableContent?.invoke()
                }
            }

            if (showConfetti) {
                KonfettiView(
                    modifier = Modifier.fillMaxSize(),
                    parties = listOf(
                        Party(
                            speed = 0f,
                            maxSpeed = 30f,
                            damping = 0.9f,
                            spread = 360,
                            colors = listOf(0xfce18a, 0xff726d, 0xf4306d, 0xb48def),
                            emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(100),
                            position = Position.Relative(0.5, 0.3)
                        )
                    )
                )
                // Automatically hide confetti after a delay
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(4000)
                    showConfetti = false
                }
            }
        }
    }
}

// --- UI Components ---
@Composable
fun Header(userId: String?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Kanban Task Tracker", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E3A8A))
        Text("Organize your workflow", fontSize = 16.sp, color = Color.Gray)
        userId?.let {
             Text("User ID: $it", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun AddTaskSection(onAddTask: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Add new task to 'To Do'") },
            modifier = Modifier.weight(1f)
        )
        Button(onClick = {
            onAddTask(text)
            text = ""
        }) {
            Text("Add Task")
        }
    }
}

@Composable
fun KanbanColumn(
    columnId: String,
    columnData: com.example.kanbanapp.data.ColumnData,
    tasks: List<Task>,
    onMoveTask: (taskId: String, fromColumnId: String) -> Unit,
    onDeleteTask: (taskId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val dragDropState = LocalDragDropState.current
    var isCurrentDropTarget by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .background(
                if (isCurrentDropTarget) Color.LightGray.copy(alpha = 0.5f) else Color(0xFFF3F4F6),
                RoundedCornerShape(12.dp)
            )
            .pointerInput(Unit) {
                 detectDragGestures(
                    onDragStart = { },
                    onDragEnd = {
                        isCurrentDropTarget = false
                        dragDropState.isDragging = false
                        val data = dragDropState.dataToDrop as? Pair<*, *>
                        if (data != null) {
                            val (taskId, fromColumn) = data
                            onMoveTask(taskId as String, fromColumn as String)
                        }
                    },
                    onDragCancel = { 
                        isCurrentDropTarget = false
                        dragDropState.isDragging = false
                    },
                    onDrag = { change, _ ->
                        isCurrentDropTarget = true
                        change.consume()
                    }
                )
            }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = columnData.name,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color.DarkGray,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyColumn(modifier = Modifier.fillMaxHeight()) {
                items(tasks, key = { it.id }) { task ->
                    DraggableTaskCard(
                        task = task,
                        columnId = columnId,
                        onDeleteTask = onDeleteTask
                    )
                }
            }
        }
    }
}


@Composable
fun DraggableTaskCard(
    task: Task,
    columnId: String,
    onDeleteTask: (taskId: String) -> Unit,
) {
    val dragDropState = LocalDragDropState.current

    Card(
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        dragDropState.isDragging = true
                        dragDropState.dataToDrop = Pair(task.id, columnId)
                        dragDropState.draggableContent = { TaskCardContent(task, onDeleteTask) }
                    },
                    onDragEnd = { dragDropState.isDragging = false },
                    onDragCancel = { dragDropState.isDragging = false },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragDropState.dragPosition += dragAmount
                    }
                )
            }
    ) {
         if (!dragDropState.isDragging || dragDropState.dataToDrop != Pair(task.id, columnId)) {
            TaskCardContent(task, onDeleteTask)
        }
    }
}

@Composable
fun TaskCardContent(
    task: Task,
    onDeleteTask: (taskId: String) -> Unit
) {
     Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(task.content, modifier = Modifier.weight(1f))
        TextButton(onClick = { onDeleteTask(task.id) }) {
            Text("Delete", color = Color.Red)
        }
    }
}

/*
 * =====================================================================================
 * 5. MainActivity.kt
 * =====================================================================================
 *
 * This is the entry point of your Android app. It sets up the Compose content.
 */
package com.example.kanbanapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.kanbanapp.ui.KanbanScreen
import com.example.kanbanapp.ui.theme.YourAppTheme // Replace with your actual theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YourAppTheme { // Your app's theme composable
                KanbanScreen()
            }
        }
    }
}
