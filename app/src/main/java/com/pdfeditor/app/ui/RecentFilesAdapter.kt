package com.pdfeditor.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pdfeditor.app.databinding.ItemRecentFileBinding
import com.pdfeditor.app.model.RecentFile
import java.text.SimpleDateFormat
import java.util.*

class RecentFilesAdapter(
    private val files: List<RecentFile>,
    private val onClick: (RecentFile) -> Unit
) : RecyclerView.Adapter<RecentFilesAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemRecentFileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(file: RecentFile) {
            binding.tvFileName.text = file.name
            binding.tvFileInfo.text = buildString {
                if (file.lastModified > 0) {
                    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    append(sdf.format(Date(file.lastModified)))
                }
                if (file.size > 0) {
                    append(" • ${formatFileSize(file.size)}")
                }
            }
            binding.root.setOnClickListener { onClick(file) }
        }

        private fun formatFileSize(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                else -> "${bytes / (1024 * 1024)} MB"
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentFileBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(files[position])
    }

    override fun getItemCount() = files.size
}
