/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui.cloud

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import me.zhanghai.android.files.R
import me.zhanghai.android.files.about.AboutActivity
import me.zhanghai.android.files.databinding.CloudStorageFragmentBinding
import me.zhanghai.android.files.filelist.BottomNavHost
import me.zhanghai.android.files.filelist.FileListActivity
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.settings.SettingsActivity
import me.zhanghai.android.files.storage.AddLanSmbServerActivity
import me.zhanghai.android.files.storage.AddStorageDialogActivity
import me.zhanghai.android.files.storage.FileSystemRoot
import me.zhanghai.android.files.storage.PrimaryStorageVolume
import me.zhanghai.android.files.storage.EditFtpServerActivity
import me.zhanghai.android.files.storage.EditFtpServerFragment
import me.zhanghai.android.files.storage.EditSftpServerActivity
import me.zhanghai.android.files.storage.EditSftpServerFragment
import me.zhanghai.android.files.storage.EditWebDavServerActivity
import me.zhanghai.android.files.storage.EditWebDavServerFragment
import me.zhanghai.android.files.storage.Storage
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.valueCompat

class CloudStorageFragment : Fragment() {

    private lateinit var binding: CloudStorageFragmentBinding
    private lateinit var adapter: CloudStorageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.settings_about, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(SettingsActivity::class.createIntent())
                true
            }
            R.id.action_about -> {
                startActivity(AboutActivity::class.createIntent())
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = CloudStorageFragmentBinding.inflate(inflater, container, false)
        .also { binding = it }
        .root

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val activity = requireActivity() as AppCompatActivity
        activity.setTitle(R.string.cloud_title)
        activity.setSupportActionBar(binding.toolbar)
        adapter = CloudStorageAdapter(
            object : CloudStorageAdapter.Listener {
                override fun onClickEntry(entry: CloudStorageEntry) {
                    startActivitySafe(entry.intent())
                }

                override fun onClickSaved(storage: Storage) {
                    storage.path?.let { path ->
                        startActivitySafe(FileListActivity.createViewIntent(path))
                    }
                }
            }
        )
        val gridLayoutManager = GridLayoutManager(activity, 5).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int =
                    if (adapter.isSavedItem(position)) 5 else 1
            }
        }
        binding.recyclerView.layoutManager = gridLayoutManager
        binding.recyclerView.adapter = adapter
        binding.root.findViewById<View>(R.id.bottomActionBar).isVisible = false
        setupBottomNavBar()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun setupBottomNavBar() {
        binding.root.findViewById<View>(R.id.bottomNavCloud).isSelected = true
        val host = requireActivity() as? BottomNavHost
        binding.root.findViewById<View>(R.id.bottomNavBrowse).setOnClickListener { host?.showHome() }
        binding.root.findViewById<View>(R.id.bottomNavCloud).setOnClickListener { host?.showCloudStorage() }
    }

    private fun refresh() {
        val entries = buildEntries()
        val saved = Settings.STORAGES.valueCompat.filter {
            it.path != null && it !is FileSystemRoot && it !is PrimaryStorageVolume
        }
        val items = mutableListOf<CloudItem>()
        items += entries.map { CloudItem.Entry(it) as CloudItem }
        items += saved.map { CloudItem.Saved(it) as CloudItem }
        adapter.submit(items)
    }

    private fun buildEntries(): List<CloudStorageEntry> = listOf(
        CloudStorageEntry(
            getString(R.string.cloud_nas),
            getString(R.string.cloud_storage_entry_nas_desc),
            R.drawable.file_directory_icon,
            { AddStorageDialogActivity::class.createIntent() }
        ),
        CloudStorageEntry(
            getString(R.string.cloud_smb),
            getString(R.string.cloud_storage_entry_smb_desc),
            R.drawable.file_directory_icon,
            { AddLanSmbServerActivity::class.createIntent() }
        ),
        CloudStorageEntry(
            getString(R.string.cloud_ftp),
            getString(R.string.cloud_storage_entry_ftp_desc),
            R.drawable.device_icon_white_24dp,
            { EditFtpServerActivity::class.createIntent().putArgs(EditFtpServerFragment.Args()) }
        ),
        CloudStorageEntry(
            getString(R.string.cloud_sftp),
            getString(R.string.cloud_storage_entry_sftp_desc),
            R.drawable.lock_icon_white_24dp,
            { EditSftpServerActivity::class.createIntent().putArgs(EditSftpServerFragment.Args()) }
        ),
        CloudStorageEntry(
            getString(R.string.cloud_webdav),
            getString(R.string.cloud_storage_entry_webdav_desc),
            R.drawable.sd_card_icon_white_24dp,
            { EditWebDavServerActivity::class.createIntent().putArgs(EditWebDavServerFragment.Args()) }
        )
    )
}

sealed interface CloudItem {
    data class Entry(val entry: CloudStorageEntry) : CloudItem
    data class Saved(val storage: Storage) : CloudItem
}

class CloudStorageEntry(
    val name: String,
    val desc: String,
    @DrawableRes val iconRes: Int,
    val intent: () -> Intent
)

class CloudStorageAdapter(
    private val listener: Listener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface Listener {
        fun onClickEntry(entry: CloudStorageEntry)
        fun onClickSaved(storage: Storage)
    }

    private val items = mutableListOf<CloudItem>()

    fun submit(list: List<CloudItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun isSavedItem(position: Int): Boolean = items[position] is CloudItem.Saved

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int =
        if (items[position] is CloudItem.Entry) TYPE_ENTRY else TYPE_SAVED

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == TYPE_ENTRY) {
            EntryViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.cloud_storage_entry_item, parent, false)
            )
        } else {
            SavedViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.storage_item, parent, false)
            )
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is CloudItem.Entry -> (holder as EntryViewHolder).bind(item.entry, listener)
            is CloudItem.Saved -> (holder as SavedViewHolder).bind(item.storage, listener)
        }
    }

    private class EntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconImage: ImageView = itemView.findViewById(R.id.iconImage)
        private val nameText: TextView = itemView.findViewById(R.id.nameText)
        private val descText: TextView = itemView.findViewById(R.id.descriptionText)

        fun bind(entry: CloudStorageEntry, listener: Listener) {
            iconImage.setImageResource(entry.iconRes)
            nameText.text = entry.name
            descText.text = entry.desc
            itemView.setOnClickListener { listener.onClickEntry(entry) }
        }
    }

    private class SavedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconImage: ImageView = itemView.findViewById(R.id.iconImage)
        private val nameText: TextView = itemView.findViewById(R.id.nameText)
        private val descText: TextView = itemView.findViewById(R.id.descriptionText)

        fun bind(storage: Storage, listener: Listener) {
            iconImage.setImageResource(storage.iconRes)
            nameText.text = storage.getName(itemView.context)
            descText.text = storage.description
            itemView.setOnClickListener { listener.onClickSaved(storage) }
        }
    }

    companion object {
        private const val TYPE_ENTRY = 0
        private const val TYPE_SAVED = 1
    }
}
