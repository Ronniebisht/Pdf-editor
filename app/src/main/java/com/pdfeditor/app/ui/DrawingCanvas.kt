package com.pdfeditor.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.text.InputType
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pdfeditor.app.model.Annotation
import com.pdfeditor.app.model.AnnotationType
import java.util.Stack

@SuppressLint("ClickableViewAccessibility")
class DrawingCanvas @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Mode { DRAW, HIGHLIGHT, TEXT, ERASE, ARROW, RECTANGLE }

    private var currentMode = Mode.DRAW
    private var currentColor = Color.RED
    private var currentBrushSize = 8f
    private var currentPath = Path()
    private var startX = 0f
    private var startY = 0f

    private val drawPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val textPaint = Paint().apply {
        isAntiAlias = true
        textSize = 40f
        color = Color.BLACK
    }

    // Annotations per page: pageIndex -> list of Annotation
    private val annotationsMap = mutableMapOf<Int, MutableList<Annotation>>()
    private val undoStack = Stack<Annotation>()
    private val redoStack = Stack<Annotation>()

    private var currentPageIndex = 0
    private var pageBitmap: Bitmap? = null
    private var annotationChangedListener: (() -> Unit)? = null

    private var drawingBitmap: Bitmap? = null
    private var drawingCanvas: Canvas? = null

    fun setOnAnnotationChangedListener(listener: () -> Unit) {
        annotationChangedListener = listener
    }

    fun setMode(mode: Mode) {
        currentMode = mode
    }

    fun setColor(color: Int) {
        currentColor = color
    }

    fun setBrushSize(size: Float) {
        currentBrushSize = size
    }

    fun setPageBitmap(bitmap: Bitmap) {
        pageBitmap = bitmap
        // Create a drawing layer of same size
        drawingBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        drawingCanvas = Canvas(drawingBitmap!!)
        invalidate()
    }

    fun loadAnnotationsForPage(pageIndex: Int) {
        currentPageIndex = pageIndex
        redrawAnnotations()
    }

    fun saveAnnotationsForPage(pageIndex: Int) {
        // Current page annotations already stored in annotationsMap
    }

    fun getAllAnnotations(): Map<Int, List<Annotation>> = annotationsMap

    fun undo() {
        val pageAnnotations = annotationsMap[currentPageIndex] ?: return
        if (pageAnnotations.isNotEmpty()) {
            val removed = pageAnnotations.removeLast()
            redoStack.push(removed)
            redrawAnnotations()
            annotationChangedListener?.invoke()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val annotation = redoStack.pop()
            annotationsMap.getOrPut(currentPageIndex) { mutableListOf() }.add(annotation)
            redrawAnnotations()
            annotationChangedListener?.invoke()
        }
    }

    private fun redrawAnnotations() {
        val db = drawingBitmap ?: return
        val dc = drawingCanvas ?: return
        dc.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        annotationsMap[currentPageIndex]?.forEach { annotation ->
            drawAnnotation(dc, annotation)
        }
        invalidate()
    }

    private fun drawAnnotation(canvas: Canvas, annotation: Annotation) {
        val paint = Paint().apply {
            isAntiAlias = true
            color = annotation.color
            strokeWidth = annotation.strokeWidth
        }
        when (annotation.type) {
            AnnotationType.DRAW -> {
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
                canvas.drawPath(annotation.path!!, paint)
            }
            AnnotationType.HIGHLIGHT -> {
                paint.style = Paint.Style.STROKE
                paint.alpha = 80
                paint.strokeWidth = annotation.strokeWidth * 3
                canvas.drawPath(annotation.path!!, paint)
            }
            AnnotationType.TEXT -> {
                paint.textSize = 40f
                paint.style = Paint.Style.FILL
                canvas.drawText(annotation.text ?: "", annotation.x, annotation.y, paint)
            }
            AnnotationType.ARROW -> {
                paint.style = Paint.Style.STROKE
                canvas.drawLine(annotation.x, annotation.y, annotation.x2, annotation.y2, paint)
                // Arrow head
                drawArrowHead(canvas, paint, annotation.x, annotation.y, annotation.x2, annotation.y2)
            }
            AnnotationType.RECTANGLE -> {
                paint.style = Paint.Style.STROKE
                canvas.drawRect(annotation.x, annotation.y, annotation.x2, annotation.y2, paint)
            }
        }
    }

    private fun drawArrowHead(canvas: Canvas, paint: Paint, startX: Float, startY: Float, endX: Float, endY: Float) {
        val arrowLength = 30f
        val arrowAngle = Math.toRadians(30.0)
        val angle = Math.atan2((endY - startY).toDouble(), (endX - startX).toDouble())
        val x1 = (endX - arrowLength * Math.cos(angle - arrowAngle)).toFloat()
        val y1 = (endY - arrowLength * Math.sin(angle - arrowAngle)).toFloat()
        val x2 = (endX - arrowLength * Math.cos(angle + arrowAngle)).toFloat()
        val y2 = (endY - arrowLength * Math.sin(angle + arrowAngle)).toFloat()
        canvas.drawLine(endX, endY, x1, y1, paint)
        canvas.drawLine(endX, endY, x2, y2, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (drawingCanvas == null) return false
        // Scale touch coords to bitmap coords
        val bitmapWidth = drawingBitmap?.width?.toFloat() ?: width.toFloat()
        val bitmapHeight = drawingBitmap?.height?.toFloat() ?: height.toFloat()
        val scaleX = bitmapWidth / width
        val scaleY = bitmapHeight / height
        val bx = event.x * scaleX
        val by = event.y * scaleY

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = bx
                startY = by
                when (currentMode) {
                    Mode.DRAW, Mode.HIGHLIGHT -> {
                        currentPath = Path()
                        currentPath.moveTo(bx, by)
                    }
                    Mode.TEXT -> showTextInputDialog(bx, by)
                    Mode.ERASE -> eraseAt(bx, by)
                    else -> {}
                }
            }
            MotionEvent.ACTION_MOVE -> {
                when (currentMode) {
                    Mode.DRAW, Mode.HIGHLIGHT -> {
                        currentPath.lineTo(bx, by)
                        redrawAnnotations()
                        val tempPaint = Paint().apply {
                            isAntiAlias = true
                            color = currentColor
                            strokeWidth = if (currentMode == Mode.HIGHLIGHT) currentBrushSize * 3 else currentBrushSize
                            style = Paint.Style.STROKE
                            strokeCap = Paint.Cap.ROUND
                            if (currentMode == Mode.HIGHLIGHT) alpha = 80
                        }
                        drawingCanvas?.drawPath(currentPath, tempPaint)
                        invalidate()
                    }
                    Mode.ERASE -> eraseAt(bx, by)
                    else -> {}
                }
            }
            MotionEvent.ACTION_UP -> {
                when (currentMode) {
                    Mode.DRAW -> {
                        val annotation = Annotation(
                            type = AnnotationType.DRAW,
                            path = Path(currentPath),
                            color = currentColor,
                            strokeWidth = currentBrushSize
                        )
                        addAnnotation(annotation)
                    }
                    Mode.HIGHLIGHT -> {
                        val annotation = Annotation(
                            type = AnnotationType.HIGHLIGHT,
                            path = Path(currentPath),
                            color = currentColor,
                            strokeWidth = currentBrushSize
                        )
                        addAnnotation(annotation)
                    }
                    Mode.ARROW -> {
                        val annotation = Annotation(
                            type = AnnotationType.ARROW,
                            x = startX, y = startY,
                            x2 = bx, y2 = by,
                            color = currentColor,
                            strokeWidth = currentBrushSize
                        )
                        addAnnotation(annotation)
                    }
                    Mode.RECTANGLE -> {
                        val annotation = Annotation(
                            type = AnnotationType.RECTANGLE,
                            x = startX, y = startY,
                            x2 = bx, y2 = by,
                            color = currentColor,
                            strokeWidth = currentBrushSize
                        )
                        addAnnotation(annotation)
                    }
                    else -> {}
                }
            }
        }
        return true
    }

    private fun addAnnotation(annotation: Annotation) {
        annotationsMap.getOrPut(currentPageIndex) { mutableListOf() }.add(annotation)
        redoStack.clear()
        redrawAnnotations()
        annotationChangedListener?.invoke()
    }

    private fun eraseAt(x: Float, y: Float) {
        val pageAnnotations = annotationsMap[currentPageIndex] ?: return
        val eraserRadius = currentBrushSize * 5
        val toRemove = pageAnnotations.filter { annotation ->
            when (annotation.type) {
                AnnotationType.DRAW, AnnotationType.HIGHLIGHT -> {
                    val bounds = RectF()
                    annotation.path?.computeBounds(bounds, true)
                    bounds.intersects(x - eraserRadius, y - eraserRadius, x + eraserRadius, y + eraserRadius)
                }
                else -> {
                    val dist = Math.hypot((annotation.x - x).toDouble(), (annotation.y - y).toDouble())
                    dist < eraserRadius * 2
                }
            }
        }
        if (toRemove.isNotEmpty()) {
            pageAnnotations.removeAll(toRemove)
            redrawAnnotations()
            annotationChangedListener?.invoke()
        }
    }

    private fun showTextInputDialog(x: Float, y: Float) {
        val editText = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            hint = "Enter text"
        }
        MaterialAlertDialogBuilder(context)
            .setTitle("Add Text")
            .setView(editText)
            .setPositiveButton("Add") { _, _ ->
                val text = editText.text.toString()
                if (text.isNotEmpty()) {
                    val annotation = Annotation(
                        type = AnnotationType.TEXT,
                        text = text,
                        x = x,
                        y = y,
                        color = currentColor,
                        strokeWidth = currentBrushSize
                    )
                    addAnnotation(annotation)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        pageBitmap?.let {
            canvas.drawBitmap(it, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), null)
        }
        drawingBitmap?.let {
            canvas.drawBitmap(it, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), null)
        }
    }
}
