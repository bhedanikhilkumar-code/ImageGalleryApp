package com.example.imagegallery

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.example.imagegallery.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var imageAdapter: ImageAdapter
    private val imageList = mutableListOf<Uri>()

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
            imageList.clear()
            imageList.addAll(uris)
            imageAdapter.notifyDataSetChanged()
            updateUI()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
        checkPermissionAndLoad()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowHomeEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setDisplayShowTitleEnabled(true)
    }

    private fun setupRecyclerView() {
        imageAdapter = ImageAdapter(imageList) { uri, position ->
            showImageOptions(uri, position)
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

    private fun setupClickListeners() {
        binding.fabAdd.setOnClickListener {
            pickImages()
        }
        binding.btnSelectImages.setOnClickListener {
            pickImages()
        }
        binding.btnGrantPermission.setOnClickListener {
            requestStoragePermission()
        }
    }

    private fun checkPermissionAndLoad() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                loadImages()
            }
            shouldShowRequestPermissionRationale(permission) -> {
                showPermissionView()
            }
            else -> {
                requestStoragePermission()
            }
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
        val projection = arrayOf(
            android.provider.MediaStore.Images.Media._ID,
            android.provider.MediaStore.Images.Media.DISPLAY_NAME,
            android.provider.MediaStore.Images.Media.DATE_ADDED
        )

        val sortOrder = "${android.provider.MediaStore.Images.Media.DATE_ADDED} DESC"

        val cursor = contentResolver.query(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID)
            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val contentUri = Uri.withAppendedPath(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )
                imageList.add(contentUri)
            }
        }

        updateUI()
    }

    private fun updateUI() {
        when {
            !hasStoragePermission() -> showPermissionView()
            imageList.isEmpty() -> showEmptyView()
            else -> showImagesView()
        }
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

    private fun showImageOptions(uri: Uri, position: Int) {
        val intent = Intent(this, FullScreenImageViewer::class.java).apply {
            putParcelableArrayListExtra(FullScreenImageViewer.EXTRA_IMAGE_URIS, ArrayList(imageList))
            putExtra(FullScreenImageViewer.EXTRA_POSITION, position)
        }
        startActivity(intent)
    }
}
