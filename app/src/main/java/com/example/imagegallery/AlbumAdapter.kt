package com.example.imagegallery

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.example.imagegallery.databinding.ItemAlbumBinding

class AlbumAdapter(
    private var albums: List<Album>,
    private val onAlbumClick: (Album) -> Unit
) : RecyclerView.Adapter<AlbumAdapter.AlbumViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val binding = ItemAlbumBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AlbumViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        holder.bind(albums[position])
    }

    override fun getItemCount(): Int = albums.size

    fun updateAlbums(newAlbums: List<Album>) {
        albums = newAlbums
        notifyDataSetChanged()
    }

    inner class AlbumViewHolder(
        private val binding: ItemAlbumBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(album: Album) {
            binding.tvAlbumName.text = album.name
            binding.tvAlbumCount.text = album.imageCount.toString()

            val requestOptions = RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .placeholder(R.drawable.placeholder_gradient)
                .error(R.drawable.placeholder_gradient)

            album.coverImage?.let { cover ->
                Glide.with(binding.root.context)
                    .load(cover.uri)
                    .apply(requestOptions)
                    .transition(DrawableTransitionOptions.withCrossFade(300))
                    .into(binding.ivAlbumCover)
            }

            binding.root.setOnClickListener {
                onAlbumClick(album)
            }
        }
    }
}