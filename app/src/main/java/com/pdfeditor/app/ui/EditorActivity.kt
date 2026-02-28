package com.pdfeditor.app.ui

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pdfeditor.app.R
import com.pdfeditor.app.databinding.ActivityEditorBinding
import com.pdfeditor.app.databinding.BottomSheetToolsBinding
import com.pdfeditor.app.model.Annotation
import com.pdfeditor.app.model.AnnotationType
import com.pdfeditor.app.utils.PdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PDF_URI = "extra_pdf_uri"
        const val EXTRA_CREATE_NEW = "extra_create_new"
    }

    private lateinit var binding: ActivityEditorBinding
    private var pdfRenderer: PdfRenderer? = null
    private var currentPage: PdfRenderer.Page? = null
    private var currentPageIndex = 0
    private var totalPages = 0
    private var pdfUri: Uri? = null
    private var isCreateNew = false
    private var localFile: File? = null
    private var hasUnsavedChanges = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupDrawingCanvas()
        setupToolbar()
        setupNavigation()
        setupTools()

        val uriString = intent.getStringExtra(EXTRA_PDF_URI)
        isCreateNew = intent.getBooleanExtra(EXTRA_CREATE_NEW, false)

        if (uriString != null) {
            pdfUri = Uri.parse(uriString)
            if (isCreateNew) {
                createNewPdf()
            } else {
                loadPdf(pdfUri!!)
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_editor, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_save -> {
                savePdf()
                true
            }
            R.id.action_undo -> {
                binding.drawingCanvas.undo()
                true
            }
            R.id.action_redo -> {
                binding.drawingCanvas.redo()
                true
            }
            R.id.action_pages -> {
                showPageOverview()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupDrawingCanvas() {
        binding.drawingCanvas.setOnAnnotationChangedListener {
            hasUnsavedChanges = true
        }
    }

    private fun setupNavigation() {
        binding.btnPrevPage.setOnClickListener {
            if (currentPageIndex > 0) {
                renderPage(--currentPageIndex)
            }
        }
        binding.btnNextPage.setOnClickListener {
            if (currentPageIndex < totalPages - 1) {
                renderPage(++currentPageIndex)
            }
        }
    }

    private fun setupTools() {
        binding.fabTools.setOnClickListener {
            showToolsBottomSheet()
        }
    }

    private fun showToolsBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetToolsBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        sheetBinding.btnDraw.setOnClickListener {
            binding.drawingCanvas.setMode(DrawingCanvas.Mode.DRAW)
            updateToolIndicator("Draw")
            dialog.dismiss()
        }
        sheetBinding.btnHighlight.setOnClickListener {
            binding.drawingCanvas.setMode(DrawingCanvas.Mode.HIGHLIGHT)
            updateToolIndicator("Highlight")
            dialog.dismiss()
        }
        sheetBinding.btnText.setOnClickListener {
            binding.drawingCanvas.setMode(DrawingCanvas.Mode.TEXT)
            updateToolIndicator("Add Text")
            dialog.dismiss()
        }
        sheetBinding.btnErase.setOnClickListener {
            binding.drawingCanvas.setMode(DrawingCanvas.Mode.ERASE)
            updateToolIndicator("Erase")
            dialog.dismiss()
        }
        sheetBinding.btnArrow.setOnClickListener {
            binding.drawingCanvas.setMode(DrawingCanvas.Mode.ARROW)
            updateToolIndicator("Arrow")
            dialog.dismiss()
        }
        sheetBinding.btnRect.setOnClickListener {
            binding.drawingCanvas.setMode(DrawingCanvas.Mode.RECTANGLE)
            updateToolIndicator("Rectangle")
            dialog.dismiss()
        }
        sheetBinding.sliderBrushSize.addOnChangeListener { _, value, _ ->
            binding.drawingCanvas.setBrushSize(value)
        }
        sheetBinding.colorPickerRed.setOnClickListener {
            binding.drawingCanvas.setColor(Color.RED)
            dialog.dismiss()
        }
        sheetBinding.colorPickerBlue.setOnClickListener {
            binding.drawingCanvas.setColor(Color.BLUE)
            dialog.dismiss()
        }
        sheetBinding.colorPickerGreen.setOnClickListener {
            binding.drawingCanvas.setColor(Color.GREEN)
            dialog.dismiss()
        }
        sheetBinding.colorPickerBlack.setOnClickListener {
            binding.drawingCanvas.setColor(Color.BLACK)
            dialog.dismiss()
        }
        sheetBinding.colorPickerYellow.setOnClickListener {
            binding.drawingCanvas.setColor(Color.YELLOW)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateToolIndicator(toolName: String) {
        binding.tvCurrentTool.text = toolName
    }

    private fun loadPdf(uri: Uri) {
        lifecycleScope.launch {
            binding.progressBar.isVisible = true
            try {
                withContext(Dispatchers.IO) {
                    // Copy URI to local file for editing
                    localFile = PdfUtils.copyUriToLocalFile(this@EditorActivity, uri)
                    localFile?.let { file ->
                        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                        pdfRenderer = PdfRenderer(pfd)
                        totalPages = pdfRenderer!!.pageCount
                    }
                }
                if (pdfRenderer != null) {
                    renderPage(0)
                    supportActionBar?.title = localFile?.name ?: "PDF Editor"
                    saveToRecentFiles()
                } else {
                    Toast.makeText(this@EditorActivity, "Could not open PDF", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@EditorActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.isVisible = false
            }
        }
    }

    private fun createNewPdf() {
        lifecycleScope.launch {
            binding.progressBar.isVisible = true
            try {
                withContext(Dispatchers.IO) {
                    localFile = PdfUtils.createBlankPdf(this@EditorActivity)
                    localFile?.let { file ->
                        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                        pdfRenderer = PdfRenderer(pfd)
                        totalPages = pdfRenderer!!.pageCount
                    }
                }
                renderPage(0)
                supportActionBar?.title = "New Document"
            } catch (e: Exception) {
                Toast.makeText(this@EditorActivity, "Error creating PDF: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.isVisible = false
            }
        }
    }

    private fun renderPage(pageIndex: Int) {
        lifecycleScope.launch(Dispatchers.Main) {
            val renderer = pdfRenderer ?: return@launch
            binding.progressBar.isVisible = true

            val bitmap = withContext(Dispatchers.IO) {
                currentPage?.close()
                currentPage = renderer.openPage(pageIndex)
                val page = currentPage!!

                val screenWidth = binding.pdfImageView.width.takeIf { it > 0 } ?: 1080
                val scale = screenWidth.toFloat() / page.width
                val bitmapWidth = screenWidth
                val bitmapHeight = (page.height * scale).toInt()

                val bmp = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)

                val matrix = Matrix().apply { setScale(scale, scale) }
                page.render(bmp, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }

            binding.pdfImageView.setImageBitmap(bitmap)
            binding.drawingCanvas.setPageBitmap(bitmap)
            binding.drawingCanvas.loadAnnotationsForPage(pageIndex)
            updatePageInfo()
            binding.progressBar.isVisible = false
        }
    }

    private fun updatePageInfo() {
        binding.tvPageInfo.text = "${currentPageIndex + 1} / $totalPages"
        binding.btnPrevPage.isEnabled = currentPageIndex > 0
        binding.btnNextPage.isEnabled = currentPageIndex < totalPages - 1
    }

    private fun savePdf() {
        lifecycleScope.launch {
            binding.progressBar.isVisible = true
            try {
                // Save annotations to current page before saving
                binding.drawingCanvas.saveAnnotationsForPage(currentPageIndex)

                withContext(Dispatchers.IO) {
                    val outputFile = localFile ?: return@withContext
                    val annotations = binding.drawingCanvas.getAllAnnotations()

                    // Merge annotations into PDF
                    PdfUtils.applyAnnotationsToPdf(this@EditorActivity, outputFile, annotations, pdfRenderer!!.pageCount)

                    // If original was from external URI, also write back
                    pdfUri?.let { uri ->
                        if (!isCreateNew) {
                            PdfUtils.writeFileToUri(this@EditorActivity, outputFile, uri)
                        }
                    }
                }
                hasUnsavedChanges = false
                Toast.makeText(this@EditorActivity, "PDF saved successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@EditorActivity, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.isVisible = false
            }
        }
    }

    private fun showPageOverview() {
        // Simple page count dialog; could be extended to a grid thumbnail picker
        Toast.makeText(this, "Total pages: $totalPages\nCurrent: ${currentPageIndex + 1}", Toast.LENGTH_SHORT).show()
    }

    private fun saveToRecentFiles() {
        val file = localFile ?: return
        val prefs = getSharedPreferences("pdf_editor_prefs", Context.MODE_PRIVATE)
        val existing = prefs.getStringSet("recent_files", mutableSetOf())?.toMutableSet() ?: mutableSetOf()

        // Remove old entry for same file if exists
        existing.removeAll { it.contains(file.absolutePath) }
        existing.add("${file.name}|${file.absolutePath}|${System.currentTimeMillis()}|${file.length()}")

        prefs.edit().putStringSet("recent_files", existing).apply()
    }

    override fun onBackPressed() {
        if (hasUnsavedChanges) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Unsaved Changes")
                .setMessage("You have unsaved changes. Save before leaving?")
                .setPositiveButton("Save") { _, _ ->
                    savePdf()
                    super.onBackPressed()
                }
                .setNegativeButton("Discard") { _, _ ->
                    super.onBackPressed()
                }
                .setNeutralButton("Cancel", null)
                .show()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        currentPage?.close()
        pdfRenderer?.close()
    }
}
