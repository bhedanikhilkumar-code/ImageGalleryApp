package com.example.imagegallery

import android.content.Intent
import android.content.SharedPreferences
import android.database.Cursor
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.bumptech.glide.Glide
import com.example.imagegallery.databinding.ActivityFullscreenImageBinding
import com.example.imagegallery.databinding.DialogImageInfoBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FullScreenImageViewer : AppCompatActivity() {

    private lateinit var binding: ActivityFullscreenImageBinding
    private var currentPosition = 0
    private lateinit var imageUris: ArrayList<Uri>
    private lateinit var prefs: SharedPreferences
    private lateinit var favoritesSet: MutableSet<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = getSharedPreferences("image_gallery_prefs", MODE_PRIVATE)
        favoritesSet = prefs.getStringSet("favorites", mutableSetOf()) ?: mutableSetOf()
        applyTheme()
        super.onCreate(savedInstanceState)
        binding = ActivityFullscreenImageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        @Suppress("DEPRECATION")
        imageUris = intent.getParcelableArrayListExtra(EXTRA_IMAGE_URIS) ?: arrayListOf()
        currentPosition = intent.getIntExtra(EXTRA_POSITION, 0)

        setupToolbar()
        setupPhotoView()
        loadImage(currentPosition)
        setupNavigation()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_viewer, menu)
        updateFavoriteIcon(menu.findItem(R.id.action_favorite))
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_info -> {
                showImageDetails()
                true
            }
            R.id.action_share -> {
                shareImage()
                true
            }
            R.id.action_favorite -> {
                toggleFavorite()
                true
            }
            R.id.action_delete -> {
                deleteImage()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = "${currentPosition + 1} / ${imageUris.size}"
        supportActionBar?.setDisplayShowTitleEnabled(true)
        
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupPhotoView() {
        binding.photoView.apply {
            minimumScale = 1.0f
            mediumScale = 2.5f
            maximumScale = 5.0f
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            
            setOnClickListener {
                toggleToolbar()
            }
        }
    }

    private fun toggleToolbar() {
        if (binding.toolbar.visibility == android.view.View.VISIBLE) {
            binding.toolbar.animate().alpha(0f).setDuration(200).withEndAction {
                binding.toolbar.visibility = android.view.View.GONE
            }
        } else {
            binding.toolbar.visibility = android.view.View.VISIBLE
            binding.toolbar.animate().alpha(1f).setDuration(200)
        }
    }

    private fun loadImage(position: Int) {
        if (position in imageUris.indices) {
            binding.toolbar.title = "${position + 1} / ${imageUris.size}"
            Glide.with(this)
                .load(imageUris[position])
                .into(binding.photoView)
            invalidateOptionsMenu()
        }
    }

    private fun setupNavigation() {
        binding.btnPrevious.setOnClickListener {
            if (currentPosition > 0) {
                currentPosition--
                loadImage(currentPosition)
            }
        }

        binding.btnNext.setOnClickListener {
            if (currentPosition < imageUris.size - 1) {
                currentPosition++
                loadImage(currentPosition)
            }
        }
    }

    private fun updateFavoriteIcon(menuItem: MenuItem?) {
        menuItem ?: return
        val uri = imageUris.getOrNull(currentPosition)?.toString() ?: return
        val isFavorite = favoritesSet.contains(uri)
        menuItem.setIcon(if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border)
        menuItem.setTitle(if (isFavorite) R.string.unfavorite else R.string.favorite)
    }

    private fun showImageDetails() {
        val uri = imageUris.getOrNull(currentPosition) ?: return
        val dialogBinding = DialogImageInfoBinding.inflate(layoutInflater)

        var fileName = ""
        var fileSize: Long = 0
        var dateTaken: Long = 0
        var width = 0
        var height = 0
        var filePath = ""

        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                    val dateIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                    val widthIndex = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                    val heightIndex = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                    val pathIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA)

                    if (nameIndex >= 0) fileName = cursor.getString(nameIndex) ?: ""
                    if (sizeIndex >= 0) fileSize = cursor.getLong(sizeIndex)
                    if (dateIndex >= 0) dateTaken = cursor.getLong(dateIndex)
                    if (widthIndex >= 0) width = cursor.getInt(widthIndex)
                    if (heightIndex >= 0) height = cursor.getInt(heightIndex)
                    if (pathIndex >= 0) filePath = cursor.getString(pathIndex) ?: ""
                }
            }

            if (fileName.isEmpty()) {
                fileName = uri.lastPathSegment ?: "Unknown"
            }
            if (fileSize == 0L) {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    fileSize = inputStream.available().toLong()
                }
            }
            if (width == 0 || height == 0) {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(inputStream, null, options)
                    width = options.outWidth
                    height = options.outHeight
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        dialogBinding.tvFileName.text = fileName
        dialogBinding.tvFileSize.text = formatFileSize(fileSize)
        dialogBinding.tvResolution.text = if (width > 0 && height > 0) "${width} x ${height}" else "Unknown"
        dialogBinding.tvDate.text = if (dateTaken > 0) formatDate(dateTaken) else "Unknown"
        dialogBinding.tvFilePath.text = filePath.ifEmpty { uri.toString() }

        AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
            size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0))
        }
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun shareImage() {
        val uri = imageUris.getOrNull(currentPosition) ?: return
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Image"))
    }

    private fun toggleFavorite() {
        val uri = imageUris.getOrNull(currentPosition)?.toString() ?: return
        if (favoritesSet.contains(uri)) {
            favoritesSet.remove(uri)
            Toast.makeText(this, R.string.removed_from_favorites, Toast.LENGTH_SHORT).show()
        } else {
            favoritesSet.add(uri)
            Toast.makeText(this, R.string.added_to_favorites, Toast.LENGTH_SHORT).show()
        }
        prefs.edit().putStringSet("favorites", favoritesSet).apply()
        invalidateOptionsMenu()
    }

    private fun deleteImage() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete)
            .setMessage(R.string.delete_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                val uri = imageUris.getOrNull(currentPosition) ?: return@setPositiveButton
                try {
                    contentResolver.delete(uri, null, null)
                    Toast.makeText(this, R.string.image_deleted, Toast.LENGTH_SHORT).show()
                    if (imageUris.size <= 1) {
                        finish()
                    } else {
                        imageUris.removeAt(currentPosition)
                        if (currentPosition >= imageUris.size) {
                            currentPosition = imageUris.size - 1
                        }
                        loadImage(currentPosition)
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, R.string.delete_failed, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun applyTheme() {
        val mode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    companion object {
        const val EXTRA_IMAGE_URIS = "extra_image_uris"
        const val EXTRA_POSITION = "extra_position"
    }
}
