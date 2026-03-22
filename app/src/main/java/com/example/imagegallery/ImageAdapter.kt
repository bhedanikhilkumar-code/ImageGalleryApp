package com.example.imagegallery

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.example.imagegallery.databinding.ItemImageBinding

class ImageAdapter(
    private var images: MutableList<ImageItem>,
    private val onImageClick: (ImageItem, Int) -> Unit,
    private val onImageLongClick: (ImageItem, Int) -> Unit
) : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    private val selectedItems = mutableSetOf<Int>()
    var isSelectionMode = false
        private set

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

    fun updateImages(newImages: List<ImageItem>) {
        images.clear()
        images.addAll(newImages)
        notifyDataSetChanged()
    }

    fun getSelectedItems(): List<ImageItem> {
        return selectedItems.mapNotNull { index ->
            if (index in images.indices) images[index] else null
        }
    }

    fun getSelectedCount(): Int = selectedItems.size

    fun selectAll() {
        selectedItems.clear()
        selectedItems.addAll(images.indices)
        notifyDataSetChanged()
    }

    fun deselectAll() {
        selectedItems.clear()
        notifyDataSetChanged()
    }

    fun toggleSelection(position: Int) {
        if (selectedItems.contains(position)) {
            selectedItems.remove(position)
        } else {
            selectedItems.add(position)
        }
        notifyItemChanged(position)
    }

    fun enterSelectionMode() {
        isSelectionMode = true
        notifyDataSetChanged()
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedItems.clear()
        notifyDataSetChanged()
    }

    fun isSelected(position: Int): Boolean = selectedItems.contains(position)

    inner class ImageViewHolder(
        private val binding: ItemImageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ImageItem, position: Int) {
            val requestOptions = RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .placeholder(R.drawable.placeholder_gradient)
                .error(R.drawable.placeholder_gradient)

            Glide.with(binding.root.context)
                .load(item.uri)
                .apply(requestOptions)
                .transition(DrawableTransitionOptions.withCrossFade(300))
                .into(binding.imageView)

            binding.ivFavorite.visibility = if (item.isFavorite) View.VISIBLE else View.GONE

            if (isSelectionMode) {
                binding.ivSelection.visibility = View.VISIBLE
                binding.ivSelection.setImageResource(
                    if (selectedItems.contains(position)) R.drawable.ic_check_circle_outline
                    else R.drawable.ic_check_circle_outline
                )
                binding.ivSelection.alpha = if (selectedItems.contains(position)) 1f else 0.5f
            } else {
                binding.ivSelection.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(position)
                } else {
                    binding.overlay.visibility = View.VISIBLE
                    binding.overlay.animate().alpha(0f).setDuration(100).withEndAction {
                        binding.overlay.visibility = View.GONE
                        onImageClick(item, position)
                    }
                }
            }

            binding.root.setOnLongClickListener {
                if (!isSelectionMode) {
                    enterSelectionMode()
                    toggleSelection(position)
                    onImageLongClick(item, position)
                }
                true
            }
        }
    }
}

data class ImageItem(
    val uri: Uri,
    val name: String = "",
    val dateAdded: Long = 0,
    val size: Long = 0,
    var isFavorite: Boolean = false
)
