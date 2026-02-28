# PDF Editor - Android App

A fully-featured Android PDF editor built with Kotlin, supporting annotation, drawing, text insertion, and more.

---

## Features

| Feature | Description |
|---|---|
| 📂 **Open PDFs** | Open any PDF from storage or from other apps (share sheet) |
| ✏️ **Freehand Drawing** | Draw directly on PDF pages with customizable color & brush size |
| 🖊 **Highlight** | Highlight text and areas with a translucent brush |
| **T** **Add Text** | Insert text annotations anywhere on the page |
| → **Arrows** | Draw directional arrows for callouts |
| ▭ **Rectangles** | Draw boxes to highlight regions |
| ⌫ **Eraser** | Remove annotations by touch |
| ↩ **Undo / Redo** | Full undo/redo support per page |
| 💾 **Save** | Saves annotations back into the PDF file |
| 📄 **Multi-page** | Navigate and annotate all pages |
| 📋 **Recent Files** | Keeps a history of recently opened PDFs |
| ➕ **Create New** | Create blank PDFs from scratch |

---

## Project Structure

```
PDFEditor/
├── app/
│   ├── src/main/
│   │   ├── java/com/pdfeditor/app/
│   │   │   ├── ui/
│   │   │   │   ├── MainActivity.kt          # Home screen / recent files
│   │   │   │   ├── EditorActivity.kt        # Main PDF editor screen
│   │   │   │   ├── DrawingCanvas.kt         # Custom annotation canvas
│   │   │   │   └── RecentFilesAdapter.kt    # RecyclerView adapter
│   │   │   ├── model/
│   │   │   │   ├── Annotation.kt            # Annotation data model
│   │   │   │   └── RecentFile.kt            # Recent file data model
│   │   │   └── utils/
│   │   │       └── PdfUtils.kt              # PDF file operations
│   │   ├── res/
│   │   │   ├── layout/                      # XML layouts
│   │   │   ├── drawable/                    # Vector icons & shapes
│   │   │   ├── menu/                        # Toolbar menus
│   │   │   ├── values/                      # Colors, strings, themes
│   │   │   └── xml/file_paths.xml           # FileProvider config
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
└── gradle.properties
```

---

## Requirements

- **Android Studio** Hedgehog (2023.1.1) or newer
- **Min SDK:** Android 8.0 (API 26)
- **Target SDK:** Android 14 (API 34)
- **Kotlin** 1.9.20
- Internet not required — fully offline

---

## Setup & Build

### 1. Open in Android Studio
```
File → Open → select the PDFEditor/ folder
```

### 2. Sync Gradle
Android Studio will auto-sync. If not:
```
File → Sync Project with Gradle Files
```

### 3. Run
Connect an Android device or start an emulator, then click ▶ Run.

---

## Key Dependencies

```groovy
// PDF rendering (built-in Android API)
android.graphics.pdf.PdfRenderer

// PDF manipulation
com.tom_roush:pdfbox-android:2.0.27.0

// UI
com.google.android.material:material:1.11.0
androidx.recyclerview:recyclerview:1.3.2
androidx.constraintlayout:constraintlayout:2.1.4
```

---

## How It Works

### Viewing
- Uses Android's native **`PdfRenderer`** API to render each PDF page to a `Bitmap`
- Displayed in an `ImageView` for fast, smooth rendering

### Annotating
- **`DrawingCanvas`** is a transparent custom `View` layered over the PDF image
- All touches are captured and converted to `Annotation` objects
- Annotations are stored per-page in memory with full undo/redo support

### Saving
- **`PdfUtils.applyAnnotationsToPdf()`** uses **Apache PDFBox for Android** to write annotations as native PDF content streams
- Text, lines, rectangles, and paths are all written as vector PDF objects — not flattened images

---

## Extending the App

### Add Signature Support
```kotlin
// In DrawingCanvas, add a SIGNATURE mode that captures a bounded path
// then embed as a PDF form signature field via PDFBox
```

### Add Page Reordering
```kotlin
// Use PDDocument.removePage() + insertPage() in PdfUtils
// Show a drag-and-drop thumbnail grid
```

### Add PDF Form Filling
```kotlin
// Use PDFBox's PDAcroForm and PDField to fill interactive form fields
```

### Add Image Insertion
```kotlin
// Use PDFBox's PDImageXObject.createFromFile() to stamp images onto pages
```

---

## Permissions

| Permission | Why |
|---|---|
| `READ_EXTERNAL_STORAGE` | Open PDFs from external storage (Android ≤ 12) |
| `WRITE_EXTERNAL_STORAGE` | Save files on external storage (Android ≤ 9) |
| `READ_MEDIA_IMAGES` | Modern storage access (Android 13+) |

On Android 13+, file access uses the system file picker — no runtime permissions needed for most flows.

---

## License

MIT License — free to use and modify.
