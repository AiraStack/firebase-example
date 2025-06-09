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