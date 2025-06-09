# Kanban Task Tracker - Android Compose App

A beautiful Kanban board application built with Jetpack Compose and Firebase, featuring real-time synchronization, drag-and-drop functionality, and celebratory animations.

## Features

🎯 **Kanban Board**: Three columns (To Do, In Progress, Done)  
🔥 **Firebase Integration**: Real-time data synchronization across devices  
🎭 **Drag & Drop**: Intuitive task movement between columns  
🎉 **Confetti Animation**: Celebration when tasks are completed  
📱 **Modern UI**: Built with Material Design 3 and Jetpack Compose  
👤 **Anonymous Authentication**: No account required to start using  

## Tech Stack

- **Frontend**: Jetpack Compose, Material Design 3
- **Backend**: Firebase Firestore, Firebase Authentication
- **Architecture**: MVVM with StateFlow
- **Animation**: Konfetti library for celebrations
- **Language**: Kotlin

## Prerequisites

1. **Android Studio**: Latest version with Compose support
2. **Firebase Project**: 
   - Create a new Firebase project at [Firebase Console](https://console.firebase.google.com/)
   - Enable Firestore Database
   - Enable Authentication (Anonymous provider)
   - Download `google-services.json` and place it in the `app/` directory

## Setup Instructions

### 1. Clone and Open Project
```bash
git clone <your-repo-url>
cd todo-app
```

### 2. Firebase Configuration
1. Create a Firebase project
2. Enable Firestore Database in test mode
3. Enable Authentication with Anonymous provider
4. Download `google-services.json` from Project Settings
5. Place the file in `app/google-services.json`

### 3. Update SDK Path
Edit `local.properties` and set your Android SDK path:
```properties
sdk.dir=/path/to/your/Android/sdk
```

### 4. Build and Run
1. Open project in Android Studio
2. Sync Gradle files
3. Run the app on device or emulator

## Project Structure

```
app/
├── src/main/java/com/example/kanbanapp/
│   ├── data/
│   │   └── DataModels.kt          # Task, ColumnData, BoardState models
│   ├── ui/
│   │   ├── theme/                 # App theming
│   │   ├── KanbanViewModel.kt     # Business logic and Firebase integration
│   │   └── KanbanScreen.kt        # Main UI components
│   └── MainActivity.kt            # App entry point
├── src/main/res/                  # Android resources
└── build.gradle.kts               # App dependencies
```

## Key Components

### Data Models
- `Task`: Individual task with ID and content
- `ColumnData`: Column with name and task list
- `BoardState`: Complete board state with all columns

### ViewModel Features
- Real-time Firebase synchronization
- Anonymous authentication
- Task CRUD operations
- Drag-and-drop state management

### UI Features
- Responsive column layout
- Smooth drag-and-drop animations
- Task addition with text input
- Delete functionality for tasks
- Confetti celebration for completed tasks

## Firebase Database Structure

```
artifacts/
└── default-kanban-app/
    └── public/
        └── data/
            └── kanbanBoards/
                └── main-board/
                    └── columns: {
                        "todo": {
                            "name": "To Do",
                            "items": [...]
                        },
                        "inProgress": {
                            "name": "In Progress", 
                            "items": [...]
                        },
                        "done": {
                            "name": "Done",
                            "items": [...]
                        }
                    }
```

## Customization

### Change App ID
Update the `appId` in `KanbanViewModel.kt`:
```kotlin
private val appId = "your-custom-app-id"
```

### Modify Columns
Update `columnOrder` and initial columns in `KanbanViewModel.kt`:
```kotlin
val columnOrder = listOf("todo", "inProgress", "done", "review")
```

### Styling
Customize colors and themes in `app/src/main/java/com/example/kanbanapp/ui/theme/`

## Building for Release

1. Generate signed APK in Android Studio
2. Or use command line:
```bash
./gradlew assembleRelease
```

## Troubleshooting

**Firebase connection issues:**
- Ensure `google-services.json` is in the correct location
- Check Firebase project configuration
- Verify internet connectivity

**Build errors:**
- Clean and rebuild project
- Check Gradle sync
- Verify all dependencies are up to date

**Drag and drop not working:**
- Test on a physical device (emulator may have limitations)
- Ensure proper touch input handling

## Contributing

1. Fork the repository
2. Create feature branch
3. Make changes
4. Test thoroughly
5. Submit pull request

## License

This project is open source and available under the [MIT License](LICENSE). 