package com.pdfeditor.app.model

data class RecentFile(
    val name: String,
    val path: String,
    val lastModified: Long = 0L,
    val size: Long = 0L
)
