package com.example.imagegallery

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.example.imagegallery.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var imageAdapter: ImageAdapter
    private val allImages = mutableListOf<ImageItem>()
    private val displayedImages = mutableListOf<ImageItem>()
    private lateinit var prefs: SharedPreferences
    private lateinit var favoritesSet: MutableSet<String>

    private var isShowingFavorites = false
    private var currentSortType = SortType.DATE

    private enum class SortType {
        DATE, NAME, SIZE
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            loadImages()
        } else {
            showPermissionView()
        }
    }

    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            Toast.makeText(this, "${uris.size} images selected", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = getSharedPreferences("image_gallery_prefs", MODE_PRIVATE)
        favoritesSet = prefs.getStringSet("favorites", mutableSetOf()) ?: mutableSetOf()
        applyTheme()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
        checkPermissionAndLoad()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = getString(R.string.search_hint)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = true
            override fun onQueryTextChange(newText: String?): Boolean {
                filterImages(newText ?: "")
                return true
            }
        })
        updateFavoritesIcon(menu.findItem(R.id.action_favorites))
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        updateFavoritesIcon(menu.findItem(R.id.action_favorites))
        return super.onPrepareOptionsMenu(menu)
    }

    private fun updateFavoritesIcon(menuItem: MenuItem?) {
        menuItem?.setIcon(if (isShowingFavorites) R.drawable.ic_favorite else R.drawable.ic_favorite_border)
        menuItem?.setTitle(if (isShowingFavorites) R.string.show_all else R.string.show_favorites)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_favorites -> {
                toggleFavoritesView()
                true
            }
            R.id.sort_date -> {
                currentSortType = SortType.DATE
                sortImages()
                true
            }
            R.id.sort_name -> {
                currentSortType = SortType.NAME
                sortImages()
                true
            }
            R.id.sort_size -> {
                currentSortType = SortType.SIZE
                sortImages()
                true
            }
            R.id.action_theme -> {
                toggleTheme()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowHomeEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setDisplayShowTitleEnabled(true)
    }

    private fun setupRecyclerView() {
        imageAdapter = ImageAdapter(displayedImages,
            onImageClick = { item, position -> showImageOptions(item, position) },
            onImageLongClick = { _, _ -> enterSelectionMode() }
        )
        binding.recyclerView.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            adapter = imageAdapter
            layoutAnimation = AnimationUtils.loadLayoutAnimation(
                this@MainActivity,
                R.anim.layout_animation_fall_down
            )
        }
    }

    private fun setupClickListeners() {
        binding.fabAdd.setOnClickListener {
            if (imageAdapter.isSelectionMode) {
                exitSelectionMode()
            } else {
                pickImages()
            }
        }
        binding.btnSelectImages.setOnClickListener { pickImages() }
        binding.btnGrantPermission.setOnClickListener { requestStoragePermission() }

        binding.btnCloseSelection.setOnClickListener { exitSelectionMode() }
        binding.btnSelectAll.setOnClickListener {
            if (imageAdapter.getSelectedCount() == displayedImages.size) {
                imageAdapter.deselectAll()
            } else {
                imageAdapter.selectAll()
            }
            updateSelectionCount()
        }

        binding.fabShare.setOnClickListener { shareSelectedImages() }
        binding.fabFavorite.setOnClickListener { toggleFavoriteSelected() }
        binding.fabDelete.setOnClickListener { deleteSelectedImages() }
    }

    private fun checkPermissionAndLoad() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> loadImages()
            shouldShowRequestPermissionRationale(permission) -> showPermissionView()
            else -> requestStoragePermission()
        }
    }

    private fun requestStoragePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        permissionLauncher.launch(permission)
    }

    private fun loadImages() {
        allImages.clear()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                if (cursor.count == 0) {
                    Toast.makeText(this, "No images found in gallery", Toast.LENGTH_SHORT).show()
                }
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: ""
                    val date = cursor.getLong(dateColumn)
                    val size = cursor.getLong(sizeColumn)
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    val uriString = uri.toString()
                    allImages.add(ImageItem(uri, name, date, size, favoritesSet.contains(uriString)))
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading images: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
        
        Toast.makeText(this, "${allImages.size} images loaded", Toast.LENGTH_SHORT).show()
        sortImages()
    }

    private fun sortImages() {
        displayedImages.clear()
        val sourceList = if (isShowingFavorites) allImages.filter { it.isFavorite } else allImages
        displayedImages.addAll(when (currentSortType) {
            SortType.DATE -> sourceList.sortedByDescending { it.dateAdded }
            SortType.NAME -> sourceList.sortedBy { it.name.lowercase() }
            SortType.SIZE -> sourceList.sortedByDescending { it.size }
        })
        updateUI()
    }

    private fun filterImages(query: String) {
        displayedImages.clear()
        val sourceList = if (isShowingFavorites) allImages.filter { it.isFavorite } else allImages
        if (query.isEmpty()) {
            displayedImages.addAll(sourceList)
        } else {
            displayedImages.addAll(sourceList.filter { it.name.contains(query, ignoreCase = true) })
        }
        imageAdapter.updateImages(displayedImages)
        updateUI()
    }

    private fun toggleFavoritesView() {
        isShowingFavorites = !isShowingFavorites
        invalidateOptionsMenu()
        sortImages()
        binding.tvEmptyMessage.text = if (isShowingFavorites) getString(R.string.no_favorites) else getString(R.string.no_images)
    }

    private fun updateUI() {
        when {
            !hasStoragePermission() -> showPermissionView()
            displayedImages.isEmpty() -> {
                binding.tvEmptyMessage.text = if (isShowingFavorites) getString(R.string.no_favorites) else getString(R.string.no_images)
                showEmptyView()
            }
            else -> showImagesView()
        }
        imageAdapter.updateImages(displayedImages)
    }

    private fun showPermissionView() {
        binding.permissionView.visibility = View.VISIBLE
        binding.emptyView.visibility = View.GONE
        binding.recyclerView.visibility = View.GONE
    }

    private fun showEmptyView() {
        binding.permissionView.visibility = View.GONE
        binding.emptyView.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
    }

    private fun showImagesView() {
        binding.permissionView.visibility = View.GONE
        binding.emptyView.visibility = View.GONE
        binding.recyclerView.visibility = View.VISIBLE
    }

    private fun hasStoragePermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun pickImages() {
        pickImagesLauncher.launch("image/*")
    }

    private fun showImageOptions(item: ImageItem, position: Int) {
        val intent = Intent(this, FullScreenImageViewer::class.java).apply {
            putParcelableArrayListExtra(FullScreenImageViewer.EXTRA_IMAGE_URIS, ArrayList(displayedImages.map { it.uri }))
            putExtra(FullScreenImageViewer.EXTRA_POSITION, position)
        }
        startActivity(intent)
    }

    private fun enterSelectionMode() {
        imageAdapter.enterSelectionMode()
        binding.toolbar.visibility = View.GONE
        binding.selectionToolbar.visibility = View.VISIBLE
        binding.fabSelectionMenu.visibility = View.VISIBLE
        binding.fabAdd.visibility = View.GONE
        updateSelectionCount()
    }

    private fun exitSelectionMode() {
        imageAdapter.exitSelectionMode()
        binding.toolbar.visibility = View.VISIBLE
        binding.selectionToolbar.visibility = View.GONE
        binding.fabSelectionMenu.visibility = View.GONE
        binding.fabAdd.visibility = View.VISIBLE
    }

    private fun updateSelectionCount() {
        val count = imageAdapter.getSelectedCount()
        binding.tvSelectionCount.text = getString(R.string.selected_count, count)
    }

    private fun shareSelectedImages() {
        val selectedItems = imageAdapter.getSelectedItems()
        if (selectedItems.isEmpty()) {
            Toast.makeText(this, "No images selected", Toast.LENGTH_SHORT).show()
            return
        }
        val uris = selectedItems.map { it.uri }
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND_MULTIPLE
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Images"))
    }

    private fun toggleFavoriteSelected() {
        val selectedItems = imageAdapter.getSelectedItems()
        if (selectedItems.isEmpty()) {
            Toast.makeText(this, "No images selected", Toast.LENGTH_SHORT).show()
            return
        }
        val firstItem = selectedItems.first()
        val newFavoriteState = !firstItem.isFavorite
        selectedItems.forEach { item ->
            val uriString = item.uri.toString()
            if (newFavoriteState) {
                favoritesSet.add(uriString)
                item.isFavorite = true
            } else {
                favoritesSet.remove(uriString)
                item.isFavorite = false
            }
        }
        prefs.edit().putStringSet("favorites", favoritesSet).apply()
        Toast.makeText(this, if (newFavoriteState) getString(R.string.added_to_favorites) else getString(R.string.removed_from_favorites), Toast.LENGTH_SHORT).show()
        exitSelectionMode()
        sortImages()
    }

    private fun deleteSelectedImages() {
        val selectedItems = imageAdapter.getSelectedItems()
        if (selectedItems.isEmpty()) {
            Toast.makeText(this, "No images selected", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete))
            .setMessage(getString(R.string.delete_confirm))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                var deleted = 0
                selectedItems.forEach { item ->
                    try {
                        contentResolver.delete(item.uri, null, null)
                        allImages.remove(item)
                        deleted++
                    } catch (e: Exception) {
                        Toast.makeText(this, getString(R.string.delete_failed), Toast.LENGTH_SHORT).show()
                    }
                }
                Toast.makeText(this, "$deleted ${getString(R.string.image_deleted)}", Toast.LENGTH_SHORT).show()
                exitSelectionMode()
                sortImages()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun toggleTheme() {
        val currentMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val newMode = if (currentMode == AppCompatDelegate.MODE_NIGHT_YES) {
            AppCompatDelegate.MODE_NIGHT_NO
        } else {
            AppCompatDelegate.MODE_NIGHT_YES
        }
        prefs.edit().putInt("theme_mode", newMode).apply()
        AppCompatDelegate.setDefaultNightMode(newMode)
    }

    private fun applyTheme() {
        val mode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    override fun onResume() {
        super.onResume()
        if (hasStoragePermission()) {
            if (allImages.isEmpty()) {
                loadImages()
            } else {
                sortImages()
            }
        }
    }
}
