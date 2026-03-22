package com.example.imagegallery

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.imagegallery.databinding.ActivityFullscreenImageBinding

class FullScreenImageViewer : AppCompatActivity() {

    private lateinit var binding: ActivityFullscreenImageBinding
    private var currentPosition = 0
    private lateinit var imageUris: List<Uri>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFullscreenImageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageUris = intent.getParcelableArrayListExtra(EXTRA_IMAGE_URIS) ?: arrayListOf()
        currentPosition = intent.getIntExtra(EXTRA_POSITION, 0)

        setupToolbar()
        setupPhotoView()
        loadImage(currentPosition)
        setupNavigation()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = "${currentPosition + 1} / ${imageUris.size}"
        
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

    companion object {
        const val EXTRA_IMAGE_URIS = "extra_image_uris"
        const val EXTRA_POSITION = "extra_position"
    }
}
