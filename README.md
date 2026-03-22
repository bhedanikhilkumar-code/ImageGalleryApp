# Image Gallery App

A simple Android Image Gallery app built with Kotlin.

## Features

- Display images from device gallery in a grid layout
- Pick and select multiple images
- View images in full screen (tap on image)
- Clean Material Design UI

## Requirements

- Android Studio (Arctic Fox or newer)
- JDK 17
- Android SDK 34

## How to Build

1. Open Android Studio
2. Select "Open an existing project"
3. Navigate to `Documents/ImageGalleryApp`
4. Wait for Gradle sync to complete
5. Click "Run" (green triangle) or press `Shift + F10`
6. Select your device/emulator

## Project Structure

```
ImageGalleryApp/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/imagegallery/
│   │   │   ├── MainActivity.kt
│   │   │   └── ImageAdapter.kt
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   ├── values/
│   │   │   └── drawable/
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
├── gradle.properties
└── local.properties
```

## Permissions

- `READ_MEDIA_IMAGES` (Android 13+)
- `READ_EXTERNAL_STORAGE` (Android 12 and below)
