package com.example.imagegallery

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.bumptech.glide.Glide
import com.example.imagegallery.databinding.ActivitySlideshowBinding

class SlideshowActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySlideshowBinding
    private var imageUris: ArrayList<Uri> = arrayListOf()
    private var currentPosition = 0
    private lateinit var prefs: SharedPreferences
    private lateinit var favoritesSet: MutableSet<String>

    private var isPlaying = true
    private var slideInterval = 3000L
    private val handler = Handler(Looper.getMainLooper())
    private var isFullscreen = true

    private val slideshowRunnable = object : Runnable {
        override fun run() {
            if (isPlaying && imageUris.isNotEmpty()) {
                currentPosition = (currentPosition + 1) % imageUris.size
                loadImage(currentPosition)
                handler.postDelayed(this, slideInterval)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = getSharedPreferences("image_gallery_prefs", MODE_PRIVATE)
        favoritesSet = prefs.getStringSet("favorites", mutableSetOf()) ?: mutableSetOf()
        applyTheme()
        super.onCreate(savedInstanceState)
        binding = ActivitySlideshowBinding.inflate(layoutInflater)
        setContentView(binding.root)

        @Suppress("DEPRECATION")
        imageUris = intent.getParcelableArrayListExtra(EXTRA_IMAGE_URIS) ?: arrayListOf()
        currentPosition = intent.getIntExtra(EXTRA_POSITION, 0)

        if (imageUris.isEmpty()) {
            Toast.makeText(this, "No images to display", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupFullscreen()
        setupControls()
        loadImage(currentPosition)
        startSlideshow()
    }

    private fun setupFullscreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.toolbar.visibility = View.GONE
        binding.controlsLayout.visibility = View.GONE
    }

    private fun setupControls() {
        binding.btnPrevious.setOnClickListener {
            stopSlideshow()
            if (currentPosition > 0) currentPosition-- else currentPosition = imageUris.size - 1
            loadImage(currentPosition)
        }

        binding.btnNext.setOnClickListener {
            stopSlideshow()
            if (currentPosition < imageUris.size - 1) currentPosition++ else currentPosition = 0
            loadImage(currentPosition)
        }

        binding.btnPlayPause.setOnClickListener {
            if (isPlaying) {
                stopSlideshow()
            } else {
                startSlideshow()
            }
        }

        binding.btnInfo.setOnClickListener {
            showImageDetails()
        }

        binding.seekBar.max = imageUris.size - 1
        binding.seekBar.progress = currentPosition
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    stopSlideshow()
                    currentPosition = progress
                    loadImage(currentPosition)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                stopSlideshow()
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                startSlideshow()
            }
        })

        binding.tvSpeed.setOnClickListener {
            showSpeedDialog()
        }

        binding.photoView.setOnClickListener {
            toggleControls()
        }
    }

    private fun toggleControls() {
        if (binding.toolbar.visibility == View.VISIBLE) {
            binding.toolbar.visibility = View.GONE
            binding.controlsLayout.visibility = View.GONE
            isFullscreen = true
        } else {
            binding.toolbar.visibility = View.VISIBLE
            binding.controlsLayout.visibility = View.VISIBLE
            isFullscreen = false
        }
    }

    private fun loadImage(position: Int) {
        if (position in imageUris.indices) {
            binding.tvCounter.text = "${position + 1} / ${imageUris.size}"
            binding.seekBar.progress = position
            Glide.with(this)
                .load(imageUris[position])
                .into(binding.photoView)
        }
    }

    private fun startSlideshow() {
        isPlaying = true
        binding.btnPlayPause.setImageResource(R.drawable.ic_pause)
        handler.removeCallbacks(slideshowRunnable)
        handler.postDelayed(slideshowRunnable, slideInterval)
    }

    private fun stopSlideshow() {
        isPlaying = false
        binding.btnPlayPause.setImageResource(R.drawable.ic_play)
        handler.removeCallbacks(slideshowRunnable)
    }

    private fun showSpeedDialog() {
        val speeds = arrayOf("2 seconds", "3 seconds", "5 seconds", "10 seconds")
        val values = arrayOf(2000L, 3000L, 5000L, 10000L)
        AlertDialog.Builder(this)
            .setTitle("Slide Duration")
            .setItems(speeds) { _, which ->
                slideInterval = values[which]
                if (isPlaying) {
                    handler.removeCallbacks(slideshowRunnable)
                    handler.postDelayed(slideshowRunnable, slideInterval)
                }
            }
            .show()
    }

    private fun showImageDetails() {
        val uri = imageUris.getOrNull(currentPosition) ?: return
        val intent = Intent(this, FullScreenImageViewer::class.java).apply {
            putParcelableArrayListExtra(FullScreenImageViewer.EXTRA_IMAGE_URIS, imageUris)
            putExtra(FullScreenImageViewer.EXTRA_POSITION, currentPosition)
        }
        startActivity(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_slideshow, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_play_pause -> {
                if (isPlaying) stopSlideshow() else startSlideshow()
                true
            }
            R.id.action_speed -> {
                showSpeedDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun applyTheme() {
        val mode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    override fun onPause() {
        super.onPause()
        stopSlideshow()
    }

    override fun onResume() {
        super.onResume()
        if (isPlaying) startSlideshow()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(slideshowRunnable)
    }

    companion object {
        const val EXTRA_IMAGE_URIS = "extra_image_uris"
        const val EXTRA_POSITION = "extra_position"
    }
}
