package com.example.kanbanapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.kanbanapp.ui.KanbanScreen
import com.example.kanbanapp.ui.theme.KanbanAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KanbanAppTheme {
                KanbanScreen()
            }
        }
    }
} 