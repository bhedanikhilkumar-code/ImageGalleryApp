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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.imagegallery.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var imageAdapter: ImageAdapter
    private lateinit var albumAdapter: AlbumAdapter
    private val allImages = mutableListOf<ImageItem>()
    private val displayedImages = mutableListOf<ImageItem>()
    private val albums = mutableListOf<Album>()
    private lateinit var prefs: SharedPreferences
    private lateinit var favoritesSet: MutableSet<String>

    private var currentTab = Tab.ALL
    private var currentSortType = SortType.DATE
    private var selectedAlbum: Album? = null

    private enum class Tab { ALL, ALBUMS, FAVORITES, SCREENSHOTS }
    private enum class SortType { DATE, NAME, SIZE }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) loadImages() else showPermissionView()
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
        setupBottomNavigation()
        setupClickListeners()
        checkPermissionAndLoad()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(true)
    }

    private fun setupRecyclerView() {
        imageAdapter = ImageAdapter(displayedImages,
            onImageClick = { item, position -> showImageOptions(item, position) },
            onImageLongClick = { _, _ -> enterSelectionMode() }
        )
        
        albumAdapter = AlbumAdapter(albums) { album ->
            selectedAlbum = album
            currentTab = Tab.ALL
            updateTitle()
            displayImagesForAlbum(album)
        }

        binding.recyclerView.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            adapter = imageAdapter
            layoutAnimation = AnimationUtils.loadLayoutAnimation(
                this@MainActivity,
                R.anim.layout_animation_fall_down
            )
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_all -> {
                    selectedAlbum = null
                    currentTab = Tab.ALL
                    updateTitle()
                    showImageGrid()
                    sortImages()
                    true
                }
                R.id.nav_albums -> {
                    currentTab = Tab.ALBUMS
                    updateTitle()
                    showAlbumGrid()
                    true
                }
                R.id.nav_favorites -> {
                    selectedAlbum = null
                    currentTab = Tab.FAVORITES
                    updateTitle()
                    filterFavorites()
                    true
                }
                R.id.nav_screenshots -> {
                    selectedAlbum = null
                    currentTab = Tab.SCREENSHOTS
                    updateTitle()
                    filterScreenshots()
                    true
                }
                else -> false
            }
        }
    }

    private fun updateTitle() {
        when (currentTab) {
            Tab.ALL -> {
                supportActionBar?.title = if (selectedAlbum != null) selectedAlbum!!.name else getString(R.string.all_photos)
                supportActionBar?.setDisplayHomeAsUpEnabled(selectedAlbum != null)
                if (selectedAlbum != null) {
                    supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back)
                }
            }
            Tab.ALBUMS -> {
                supportActionBar?.title = getString(R.string.albums)
                supportActionBar?.setDisplayHomeAsUpEnabled(false)
            }
            Tab.FAVORITES -> {
                supportActionBar?.title = getString(R.string.favorites)
                supportActionBar?.setDisplayHomeAsUpEnabled(false)
            }
            Tab.SCREENSHOTS -> {
                supportActionBar?.title = getString(R.string.screenshots)
                supportActionBar?.setDisplayHomeAsUpEnabled(false)
            }
        }
    }

    private fun showImageGrid() {
        binding.recyclerView.layoutManager = GridLayoutManager(this, 2)
        binding.recyclerView.adapter = imageAdapter
        binding.recyclerView.scheduleLayoutAnimation()
    }

    private fun showAlbumGrid() {
        albumAdapter.updateAlbums(albums)
        binding.recyclerView.layoutManager = GridLayoutManager(this, 2)
        binding.recyclerView.adapter = albumAdapter
        binding.recyclerView.scheduleLayoutAnimation()
        
        if (albums.isEmpty()) {
            showEmptyView(getString(R.string.no_albums), getString(R.string.tap_to_select))
        } else {
            showImagesView()
        }
    }

    private fun displayImagesForAlbum(album: Album) {
        displayedImages.clear()
        displayedImages.addAll(album.images)
        imageAdapter.updateImages(displayedImages)
        showImageGrid()
    }

    private fun setupClickListeners() {
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = getString(R.string.search_hint)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = true
            override fun onQueryTextChange(newText: String?) = if (currentTab == Tab.ALL) { filterImages(newText ?: ""); true } else false
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                selectedAlbum = null
                updateTitle()
                showImageGrid()
                sortImages()
                true
            }
            R.id.sort_date -> { currentSortType = SortType.DATE; sortImages(); true }
            R.id.sort_name -> { currentSortType = SortType.NAME; sortImages(); true }
            R.id.sort_size -> { currentSortType = SortType.SIZE; sortImages(); true }
            R.id.action_theme -> { toggleTheme(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun checkPermissionAndLoad() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> loadImages()
            shouldShowRequestPermissionRationale(permission) -> showPermissionView()
            else -> requestStoragePermission()
        }
    }

    private fun requestStoragePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
        permissionLauncher.launch(permission)
    }

    private fun loadImages() {
        allImages.clear()
        albums.clear()
        
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATA
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: ""
                    val date = cursor.getLong(dateColumn)
                    val size = cursor.getLong(sizeColumn)
                    val path = cursor.getString(dataColumn) ?: ""
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    val uriString = uri.toString()
                    
                    val imageItem = ImageItem(uri, name, date, size, favoritesSet.contains(uriString))
                    allImages.add(imageItem)

                    val folderName = extractFolderName(path)
                    var album = albums.find { it.name == folderName }
                    if (album == null) {
                        album = Album(folderName, mutableListOf())
                        albums.add(album)
                    }
                    album.images.add(imageItem)
                }
            }
            
            albums.sortByDescending { it.images.size }
            sortImages()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun extractFolderName(path: String): String {
        return try {
            val parts = path.split("/")
            if (parts.size >= 2) parts[parts.size - 2] else "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun sortImages() {
        displayedImages.clear()
        val sourceList = when (currentTab) {
            Tab.ALL -> if (selectedAlbum != null) selectedAlbum!!.images else allImages
            Tab.FAVORITES -> allImages.filter { it.isFavorite }
            Tab.SCREENSHOTS -> filterScreenshotsList()
            Tab.ALBUMS -> emptyList()
        }
        
        displayedImages.addAll(when (currentSortType) {
            SortType.DATE -> sourceList.sortedByDescending { it.dateAdded }
            SortType.NAME -> sourceList.sortedBy { it.name.lowercase() }
            SortType.SIZE -> sourceList.sortedByDescending { it.size }
        })
        
        if (currentTab != Tab.ALBUMS) {
            imageAdapter.updateImages(displayedImages)
            updateUI()
        }
    }

    private fun filterFavorites() {
        displayedImages.clear()
        displayedImages.addAll(allImages.filter { it.isFavorite }.sortedByDescending { it.dateAdded })
        imageAdapter.updateImages(displayedImages)
        updateUI()
    }

    private fun filterScreenshots() {
        displayedImages.clear()
        displayedImages.addAll(filterScreenshotsList())
        imageAdapter.updateImages(displayedImages)
        updateUI()
    }

    private fun filterScreenshotsList(): List<ImageItem> {
        return allImages.filter { item ->
            val name = item.name.lowercase()
            name.contains("screenshot") || name.contains("screen shot") || 
            name.contains("capture") || name.startsWith("screenshot_") ||
            name.startsWith("screen_") || name.contains("img_") && (name.contains("screenshot") || name.contains("capture"))
        }.sortedByDescending { it.dateAdded }
    }

    private fun filterImages(query: String) {
        displayedImages.clear()
        if (query.isEmpty()) {
            displayedImages.addAll(if (selectedAlbum != null) selectedAlbum!!.images else allImages)
        } else {
            val source = if (selectedAlbum != null) selectedAlbum!!.images else allImages
            displayedImages.addAll(source.filter { it.name.contains(query, ignoreCase = true) })
        }
        imageAdapter.updateImages(displayedImages)
    }

    private fun updateUI() {
        when {
            !hasStoragePermission() -> showPermissionView()
            displayedImages.isEmpty() -> {
                val (msg, sub) = when (currentTab) {
                    Tab.FAVORITES -> getString(R.string.no_favorites) to getString(R.string.tap_to_select)
                    Tab.SCREENSHOTS -> getString(R.string.no_screenshots) to getString(R.string.tap_screenshots_hint)
                    else -> getString(R.string.no_images) to getString(R.string.tap_to_select)
                }
                showEmptyView(msg, sub)
            }
            else -> showImagesView()
        }
    }

    private fun showPermissionView() {
        binding.permissionView.visibility = View.VISIBLE
        binding.emptyView.visibility = View.GONE
        binding.recyclerView.visibility = View.GONE
    }

    private fun showEmptyView(message: String, subtitle: String) {
        binding.permissionView.visibility = View.GONE
        binding.emptyView.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
        binding.tvEmptyMessage.text = message
        binding.tvEmptySubtitle.text = subtitle
    }

    private fun showImagesView() {
        binding.permissionView.visibility = View.GONE
        binding.emptyView.visibility = View.GONE
        binding.recyclerView.visibility = View.VISIBLE
        if (currentTab != Tab.ALBUMS) binding.recyclerView.scheduleLayoutAnimation()
    }

    private fun hasStoragePermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun pickImages() = pickImagesLauncher.launch("image/*")

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
        updateSelectionCount()
    }

    private fun exitSelectionMode() {
        imageAdapter.exitSelectionMode()
        binding.toolbar.visibility = View.VISIBLE
        binding.selectionToolbar.visibility = View.GONE
        binding.fabSelectionMenu.visibility = View.GONE
    }

    private fun updateSelectionCount() {
        binding.tvSelectionCount.text = getString(R.string.selected_count, imageAdapter.getSelectedCount())
    }

    private fun shareSelectedImages() {
        val selectedItems = imageAdapter.getSelectedItems()
        if (selectedItems.isEmpty()) {
            Toast.makeText(this, "No images selected", Toast.LENGTH_SHORT).show()
            return
        }
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND_MULTIPLE
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(selectedItems.map { it.uri }))
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
        val newFavoriteState = !selectedItems.first().isFavorite
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
        val newMode = if (currentMode == AppCompatDelegate.MODE_NIGHT_YES) AppCompatDelegate.MODE_NIGHT_NO
        else AppCompatDelegate.MODE_NIGHT_YES
        prefs.edit().putInt("theme_mode", newMode).apply()
        AppCompatDelegate.setDefaultNightMode(newMode)
    }

    private fun applyTheme() {
        val mode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    override fun onResume() {
        super.onResume()
        if (hasStoragePermission() && allImages.isNotEmpty()) sortImages()
    }
}

data class Album(
    val name: String,
    val images: MutableList<ImageItem>
) {
    val coverImage: ImageItem? get() = images.firstOrNull()
    val imageCount: Int get() = images.size
}