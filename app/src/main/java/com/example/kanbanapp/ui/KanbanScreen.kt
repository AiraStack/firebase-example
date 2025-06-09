package com.example.kanbanapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
                boardState?.let { it ->
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
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(vertical = 2.dp)
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
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable { showDialog = true },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = task.content,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp,
            color = Color.DarkGray
        )
        
        IconButton(
            onClick = { onDeleteTask(task.id) },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Delete task",
                tint = Color.Red,
                modifier = Modifier.size(16.dp)
            )
        }
    }

    if (showDialog) {
        Dialog(onDismissRequest = { showDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "任务详情",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    Text(
                        text = task.content,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { showDialog = false }
                        ) {
                            Text("关闭")
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        TextButton(
                            onClick = { 
                                onDeleteTask(task.id)
                                showDialog = false
                            }
                        ) {
                            Text("删除", color = Color.Red)
                        }
                    }
                }
            }
        }
    }
} 