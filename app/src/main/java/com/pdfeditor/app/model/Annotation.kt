package com.pdfeditor.app.model

import android.graphics.Path

enum class AnnotationType {
    DRAW,
    HIGHLIGHT,
    TEXT,
    ARROW,
    RECTANGLE
}

data class Annotation(
    val type: AnnotationType,
    val path: Path? = null,        // For DRAW and HIGHLIGHT
    val text: String? = null,      // For TEXT
    val x: Float = 0f,             // For TEXT, ARROW start, RECTANGLE start
    val y: Float = 0f,
    val x2: Float = 0f,            // For ARROW end, RECTANGLE end
    val y2: Float = 0f,
    val color: Int,
    val strokeWidth: Float = 8f
)
