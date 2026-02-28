package com.pdfeditor.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pdfeditor.app.databinding.ActivityMainBinding
import com.pdfeditor.app.model.RecentFile
import com.pdfeditor.app.utils.FileUtils
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var recentFilesAdapter: RecentFilesAdapter
    private val recentFiles = mutableListOf<RecentFile>()

    private val openPdfLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { openPdfEditor(it) }
    }

    private val createPdfLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        uri?.let { createNewPdf(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupFab()
        loadRecentFiles()

        // Handle PDF opened from external app
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIncomingIntent(it) }
    }

    private fun handleIncomingIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            intent.data?.let { openPdfEditor(it) }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "PDF Editor"
    }

    private fun setupRecyclerView() {
        recentFilesAdapter = RecentFilesAdapter(recentFiles) { recentFile ->
            val file = File(recentFile.path)
            if (file.exists()) {
                openPdfEditor(Uri.fromFile(file))
            } else {
                Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
                recentFiles.remove(recentFile)
                recentFilesAdapter.notifyDataSetChanged()
                updateEmptyState()
            }
        }
        binding.rvRecentFiles.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = recentFilesAdapter
        }
    }

    private fun setupFab() {
        binding.fabAddPdf.setOnClickListener {
            showAddPdfOptions()
        }
    }

    private fun showAddPdfOptions() {
        val options = arrayOf("Open Existing PDF", "Create New PDF")
        MaterialAlertDialogBuilder(this)
            .setTitle("PDF Editor")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openPdfLauncher.launch("application/pdf")
                    1 -> createPdfLauncher.launch("new_document.pdf")
                }
            }
            .show()
    }

    private fun openPdfEditor(uri: Uri) {
        val intent = Intent(this, EditorActivity::class.java).apply {
            putExtra(EditorActivity.EXTRA_PDF_URI, uri.toString())
        }
        startActivity(intent)
    }

    private fun createNewPdf(uri: Uri) {
        val intent = Intent(this, EditorActivity::class.java).apply {
            putExtra(EditorActivity.EXTRA_PDF_URI, uri.toString())
            putExtra(EditorActivity.EXTRA_CREATE_NEW, true)
        }
        startActivity(intent)
    }

    private fun loadRecentFiles() {
        val prefs = getSharedPreferences("pdf_editor_prefs", MODE_PRIVATE)
        val recentFilesJson = prefs.getStringSet("recent_files", emptySet()) ?: emptySet()

        recentFiles.clear()
        recentFilesJson.mapNotNull { json ->
            try {
                val parts = json.split("|")
                if (parts.size >= 2) {
                    RecentFile(
                        name = parts[0],
                        path = parts[1],
                        lastModified = if (parts.size > 2) parts[2].toLong() else 0L,
                        size = if (parts.size > 3) parts[3].toLong() else 0L
                    )
                } else null
            } catch (e: Exception) { null }
        }.sortedByDescending { it.lastModified }
            .take(20)
            .let { recentFiles.addAll(it) }

        recentFilesAdapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun updateEmptyState() {
        binding.layoutEmpty.visibility = if (recentFiles.isEmpty()) View.VISIBLE else View.GONE
        binding.rvRecentFiles.visibility = if (recentFiles.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        loadRecentFiles()
    }
}
