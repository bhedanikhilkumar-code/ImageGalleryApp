package com.example.imagegallery

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.example.imagegallery.databinding.ItemImageBinding

class ImageAdapter(
    private val images: List<Uri>,
    private val onImageClick: (Uri, Int) -> Unit
) : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ItemImageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(images[position], position)
    }

    override fun getItemCount(): Int = images.size

    inner class ImageViewHolder(
        private val binding: ItemImageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(uri: Uri, position: Int) {
            val requestOptions = RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .placeholder(com.example.imagegallery.R.drawable.placeholder_gradient)
                .error(com.example.imagegallery.R.drawable.placeholder_gradient)

            Glide.with(binding.root.context)
                .load(uri)
                .apply(requestOptions)
                .transition(DrawableTransitionOptions.withCrossFade(300))
                .into(binding.imageView)

            binding.root.setOnClickListener {
                binding.overlay.visibility = View.VISIBLE
                binding.overlay.animate().alpha(0f).setDuration(100).withEndAction {
                    binding.overlay.visibility = View.GONE
                    onImageClick(uri, position)
                }
            }

            binding.root.setOnLongClickListener {
                binding.overlay.visibility = View.VISIBLE
                true
            }
        }
    }
}
