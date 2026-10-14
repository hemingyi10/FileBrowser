/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui.category

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java8.nio.file.Files
import java8.nio.file.Path
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.CategoryFileListFragmentBinding
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.fileProviderUri
import me.zhanghai.android.files.file.iconRes
import me.zhanghai.android.files.file.isApk
import me.zhanghai.android.files.file.isAudio
import me.zhanghai.android.files.file.isImage
import me.zhanghai.android.files.file.isPdf
import me.zhanghai.android.files.file.isSupportedArchive
import me.zhanghai.android.files.file.isVideo
import me.zhanghai.android.files.file.loadFileItem
import me.zhanghai.android.files.filelist.BottomNavHost
import me.zhanghai.android.files.filelist.name
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.createInstallPackageIntent
import me.zhanghai.android.files.util.createViewIntent
import me.zhanghai.android.files.util.valueCompat

enum class Category(
    @DrawableRes val iconRes: Int,
    val titleRes: Int,
    val filters: List<Int>
) {
    DOCUMENTS(
        R.drawable.document_icon_white_24dp,
        R.string.category_documents,
        listOf(
            R.string.category_filter_all,
            R.string.category_filter_doc,
            R.string.category_filter_xls,
            R.string.category_filter_pdf,
            R.string.category_filter_ppt,
            R.string.category_filter_other
        )
    ),
    IMAGES(
        R.drawable.image_icon_white_24dp,
        R.string.category_images,
        listOf(R.string.category_filter_all)
    ),
    VIDEOS(
        R.drawable.video_icon_white_24dp,
        R.string.category_videos,
        listOf(R.string.category_filter_all)
    ),
    AUDIO(
        R.drawable.audio_icon_white_24dp,
        R.string.category_audio,
        listOf(R.string.category_filter_all)
    ),
    ARCHIVES(
        R.drawable.file_archive_icon,
        R.string.category_archives,
        listOf(
            R.string.category_filter_all,
            R.string.category_filter_zip,
            R.string.category_filter_rar,
            R.string.category_filter_7z
        )
    ),
    APKS(
        R.drawable.file_apk_icon,
        R.string.category_apks,
        listOf(R.string.category_filter_all)
    );

    fun matches(file: FileItem): Boolean = when (this) {
        DOCUMENTS -> file.isDocumentLike()
        IMAGES -> file.mimeType.isImage
        VIDEOS -> file.mimeType.isVideo
        AUDIO -> file.mimeType.isAudio
        ARCHIVES -> file.mimeType.isSupportedArchive
        APKS -> file.mimeType.isApk || file.name.endsWith(".apk", ignoreCase = true)
    }

    fun matchesFilter(filterIndex: Int, file: FileItem): Boolean {
        if (filterIndex == 0) {
            return true
        }
        return when (this) {
            DOCUMENTS -> file.matchesDocFilter(filterIndex)
            ARCHIVES -> file.matchesArchiveFilter(filterIndex)
            else -> true
        }
    }
}

private fun FileItem.isDocumentLike(): Boolean {
    val value = mimeType.value
    return mimeType.isPdf
            || value.startsWith("text/")
            || value.contains("officedocument")
            || value.contains("msword")
            || value.contains("ms-excel")
            || value.contains("ms-powerpoint")
            || value.contains("document")
            || value.contains("word")
            || value.contains("excel")
            || value.contains("powerpoint")
}

private fun FileItem.matchesDocFilter(index: Int): Boolean {
    val value = mimeType.value
    return when (index) {
        1 -> value.contains("word") || value.contains("msword") || value.contains("officedocument.wordprocessingml")
        2 -> value.contains("excel") || value.contains("ms-excel") || value.contains("officedocument.spreadsheetml")
        3 -> mimeType.isPdf
        4 -> value.contains("powerpoint") || value.contains("ms-powerpoint") || value.contains("officedocument.presentationml")
        else -> {
            val matched = value.contains("word") || value.contains("excel")
                    || value.contains("powerpoint") || value.contains("msword")
                    || value.contains("ms-excel") || value.contains("ms-powerpoint")
                    || value.contains("officedocument.wordprocessingml")
                    || value.contains("officedocument.spreadsheetml")
                    || value.contains("officedocument.presentationml")
                    || mimeType.isPdf
            !matched
        }
    }
}

private fun FileItem.matchesArchiveFilter(index: Int): Boolean {
    val lowerName = name.lowercase()
    return when (index) {
        1 -> lowerName.endsWith(".zip")
        2 -> lowerName.endsWith(".rar")
        else -> lowerName.endsWith(".7z")
    }
}

class CategoryFileListFragment : Fragment() {

    companion object {
        private const val EXTRA_CATEGORY = "category"
        private const val MAX_RESULTS = 1500
        private const val MAX_DEPTH = 10

        fun newInstance(category: Category): CategoryFileListFragment =
            CategoryFileListFragment().apply {
                arguments = Bundle().apply { putString(EXTRA_CATEGORY, category.name) }
            }
    }

    private val category: Category
        get() = Category.valueOf(requireArguments().getString(EXTRA_CATEGORY)!!)

    private lateinit var binding: CategoryFileListFragmentBinding
    private lateinit var adapter: CategoryFileListAdapter
    private var filterIndex = 0
    private val allFiles = mutableListOf<FileItem>()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var loadJob: Future<*>? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = CategoryFileListFragmentBinding.inflate(inflater, container, false)
        .also { binding = it }
        .root

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val activity = requireActivity() as AppCompatActivity
        activity.setTitle(category.titleRes)
        activity.setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(activity)
        adapter = CategoryFileListAdapter { file -> openFile(file) }
        binding.recyclerView.adapter = adapter
        setupFilterBar()
        binding.bottomActionBar.isVisible = false
        setupBottomNavBar()
        loadFiles()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loadJob?.cancel(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    private fun setupFilterBar() {
        binding.filterBar.removeAllViews()
        val density = resources.displayMetrics.density
        val horizontalPadding = (density * 12).toInt()
        val verticalPadding = (density * 6).toInt()
        category.filters.forEachIndexed { index, titleRes ->
            val chip = TextView(requireContext()).apply {
                text = getString(titleRes)
                textSize = 14f
                isSelected = index == filterIndex
                setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
                updateChipStyle()
                setOnClickListener {
                    filterIndex = index
                    for (i in 0 until binding.filterBar.childCount) {
                        (binding.filterBar.getChildAt(i) as TextView).apply {
                            isSelected = i == filterIndex
                            updateChipStyle()
                        }
                    }
                    refreshUi()
                }
            }
            binding.filterBar.addView(chip)
        }
    }

    private fun TextView.updateChipStyle() {
        setTextColor(themeColor(if (isSelected) android.R.attr.colorAccent else android.R.attr.textColorSecondary))
        background = if (isSelected) {
            context.getDrawable(R.drawable.filter_chip_selected)
        } else {
            null
        }
    }

    private fun themeColor(attr: Int): Int {
        val value = TypedValue()
        requireContext().theme.resolveAttribute(attr, value, true)
        return value.data
    }

    private fun setupBottomNavBar() {
        binding.bottomNavBrowse.isSelected = true
        val host = requireActivity() as? BottomNavHost
        binding.bottomNavBrowse.setOnClickListener { host?.showHome() }
        binding.bottomNavCloud.setOnClickListener { host?.showCloudStorage() }
    }

    private fun loadFiles() {
        binding.emptyView.visibility = View.GONE
        adapter.submit(emptyList())
        val root = Settings.FILE_LIST_DEFAULT_DIRECTORY.valueCompat
        val category = this.category
        loadJob = executor.submit {
            val files = mutableListOf<FileItem>()
            collectFiles(root, 0, files, category)
            view?.post {
                if (isAdded) {
                    allFiles.clear()
                    allFiles.addAll(files)
                    refreshUi()
                }
            }
        }
    }

    private fun collectFiles(path: Path, depth: Int, out: MutableList<FileItem>, category: Category) {
        if (depth > MAX_DEPTH || out.size >= MAX_RESULTS) {
            return
        }
        val stream = try {
            Files.newDirectoryStream(path)
        } catch (e: Exception) {
            return
        }
        stream.use { directoryStream ->
            for (child in directoryStream.iterator()) {
                val fileName = child.fileName?.toString() ?: continue
                if (fileName.startsWith(".")) {
                    continue
                }
                if (fileName == "Android") {
                    collectAndroid(child, depth + 1, out, category)
                    if (out.size >= MAX_RESULTS) {
                        return
                    }
                    continue
                }
                val item = try {
                    child.loadFileItem()
                } catch (e: Exception) {
                    continue
                }
                if (item.attributes.isDirectory) {
                    collectFiles(child, depth + 1, out, category)
                } else if (item.attributes.isRegularFile && category.matches(item)) {
                    out.add(item)
                }
                if (out.size >= MAX_RESULTS) {
                    return
                }
            }
        }
    }

    private fun collectAndroid(path: Path, depth: Int, out: MutableList<FileItem>, category: Category) {
        if (depth > MAX_DEPTH || out.size >= MAX_RESULTS) {
            return
        }
        val stream = try {
            Files.newDirectoryStream(path)
        } catch (e: Exception) {
            return
        }
        stream.use { directoryStream ->
            for (child in directoryStream.iterator()) {
                val fileName = child.fileName?.toString() ?: continue
                if (fileName.startsWith(".") || fileName == "data" || fileName == "obb") {
                    continue
                }
                val item = try {
                    child.loadFileItem()
                } catch (e: Exception) {
                    continue
                }
                if (item.attributes.isDirectory) {
                    collectFiles(child, depth + 1, out, category)
                } else if (item.attributes.isRegularFile && category.matches(item)) {
                    out.add(item)
                }
                if (out.size >= MAX_RESULTS) {
                    return
                }
            }
        }
    }

    private fun refreshUi() {
        val filtered = allFiles.filter { category.matchesFilter(filterIndex, it) }
        val items = buildItems(filtered)
        adapter.submit(items)
        binding.emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun buildItems(files: List<FileItem>): List<CategoryItem> {
        val formatter = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
        val groups = sortedMapOf<String, MutableList<FileItem>>(compareByDescending { it })
        for (file in files) {
            val key = try {
                formatter.format(Date(file.attributes.lastModifiedTime.toMillis()))
            } catch (e: Exception) {
                "未知日期"
            }
            groups.getOrPut(key) { mutableListOf() }.add(file)
        }
        val items = mutableListOf<CategoryItem>()
        for ((date, list) in groups) {
            items += CategoryItem.Header(String.format(Locale.getDefault(), "%s | %d项", date, list.size))
            items += list.sortedByDescending { it.attributes.lastModifiedTime.toMillis() }
                .map { CategoryItem.Entry(it) }
        }
        return items
    }

    private fun openFile(file: FileItem) {
        val intent = if (file.mimeType.isApk || file.name.endsWith(".apk", ignoreCase = true)) {
            file.path.fileProviderUri.createInstallPackageIntent()
        } else {
            file.path.fileProviderUri.createViewIntent(file.mimeType)
        }
        try {
            requireContext().startActivity(intent)
        } catch (e: Exception) {
            // Ignore activities that cannot handle this file.
        }
    }
}

sealed interface CategoryItem {
    data class Header(val text: String) : CategoryItem
    data class Entry(val file: FileItem) : CategoryItem
}

class CategoryFileListAdapter(
    private val listener: (FileItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<CategoryItem>()

    fun submit(list: List<CategoryItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int =
        if (items[position] is CategoryItem.Header) TYPE_HEADER else TYPE_ENTRY

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == TYPE_HEADER) {
            HeaderViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.category_group_header, parent, false)
            )
        } else {
            EntryViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.category_file_item, parent, false)
            )
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is CategoryItem.Header -> (holder as HeaderViewHolder).textView.text = item.text
            is CategoryItem.Entry -> (holder as EntryViewHolder).bind(item.file, listener)
        }
    }

    private class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView as TextView
    }

    private class EntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconImage: ImageView = itemView.findViewById(R.id.iconImage)
        private val nameText: TextView = itemView.findViewById(R.id.nameText)

        fun bind(file: FileItem, listener: (FileItem) -> Unit) {
            iconImage.setImageResource(file.mimeType.iconRes)
            nameText.text = file.name
            itemView.setOnClickListener { listener(file) }
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ENTRY = 1
    }
}
