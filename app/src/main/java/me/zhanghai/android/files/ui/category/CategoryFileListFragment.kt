/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui.category

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import me.zhanghai.android.files.util.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java8.nio.file.Files
import java8.nio.file.Path
import me.zhanghai.android.files.R
import me.zhanghai.android.files.about.AboutActivity
import me.zhanghai.android.files.app.clipboardManager
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
import me.zhanghai.android.files.filelist.ConfirmDeleteFilesDialogFragment
import me.zhanghai.android.files.filelist.EditFileActivity
import me.zhanghai.android.files.filelist.FileItemSet
import me.zhanghai.android.files.filelist.FileListActivity
import me.zhanghai.android.files.filelist.FileListViewModel
import me.zhanghai.android.files.filelist.OpenFileActivity
import me.zhanghai.android.files.filelist.OpenFileAsDialogActivity
import me.zhanghai.android.files.filelist.OpenFileAsDialogFragment
import me.zhanghai.android.files.filelist.RenameFileDialogFragment
import me.zhanghai.android.files.filelist.fileItemSetOf
import me.zhanghai.android.files.filelist.name
import me.zhanghai.android.files.filelist.toUserFriendlyString
import me.zhanghai.android.files.filelist.supportsThumbnail
import me.zhanghai.android.files.filejob.FileJobService
import me.zhanghai.android.files.fileproperties.FilePropertiesDialogFragment
import me.zhanghai.android.files.navigation.BookmarkDirectories
import me.zhanghai.android.files.navigation.BookmarkDirectory
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.settings.SettingsActivity
import me.zhanghai.android.files.util.copyText
import me.zhanghai.android.files.util.createInstallPackageIntent
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.createSendStreamIntent
import me.zhanghai.android.files.util.createViewIntent
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.valueCompat
import me.zhanghai.android.files.util.withChooser

enum class Category(
    @DrawableRes val iconRes: Int,
    val titleRes: Int,
    val filters: List<Int>
) {
    DOCUMENTS(
        R.drawable.category_documents_icon,
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
        R.drawable.category_images_icon,
        R.string.category_images,
        listOf(R.string.category_filter_all)
    ),
    VIDEOS(
        R.drawable.category_videos_icon,
        R.string.category_videos,
        listOf(R.string.category_filter_all)
    ),
    AUDIO(
        R.drawable.category_audio_icon,
        R.string.category_audio,
        listOf(R.string.category_filter_all)
    ),
    ARCHIVES(
        R.drawable.category_archives_icon,
        R.string.category_archives,
        listOf(
            R.string.category_filter_all,
            R.string.category_filter_zip,
            R.string.category_filter_rar,
            R.string.category_filter_7z
        )
    ),
    APKS(
        R.drawable.category_apks_icon,
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

class CategoryFileListFragment : Fragment(), ConfirmDeleteFilesDialogFragment.Listener,
    RenameFileDialogFragment.Listener {

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
    private val viewModel by viewModels { { FileListViewModel() } }

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
        adapter = CategoryFileListAdapter(
            { file -> openFile(file) },
            { file, anchor -> showItemMenu(file, anchor) }
        )
        val gridLayoutManager = GridLayoutManager(activity, 3).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int =
                    if (adapter.isHeader(position)) 3 else 1
            }
        }
        binding.recyclerView.layoutManager = gridLayoutManager
        binding.recyclerView.adapter = adapter
        setupFilterBar()
        setupBottomActionBar()
        setupBottomNavBar()
        // 类别页 BottomBarLayout 是窗口内唯一 fitsSystemWindows 视图，会收到完整窗口
        // inset（顶部状态栏 + 底部导航条），导致底栏顶部被状态栏高度撑出大片空白，
        // 视觉上“第二菜单栏”过高。与浏览/云盘页（PersistentBarLayout 只向底栏分发
        // bottom inset）对齐：仅保留底部 inset，丢弃顶部 inset。
        // 空安全兜底：include 不带 id（merge 根不进 ViewBinding），运行时用
        // findViewById 引用 merge 内 BottomBarLayout；若找不到则跳过，避免闪退。
        binding.root.findViewById<View>(R.id.bottomBarLayout)?.let { bottomBarLayout ->
            ViewCompat.setOnApplyWindowInsetsListener(bottomBarLayout) { view, insets ->
                val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
                view.setPadding(0, 0, 0, bottom)
                insets
            }
        }
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
        setTextColor(if (isSelected) 0xFF2196F3.toInt() else 0xFF757575.toInt())
        background = if (isSelected) {
            context.getDrawable(R.drawable.filter_chip_selected)
        } else {
            null
        }
    }

    private fun setupBottomActionBar() {
        // 类别页恢复完整双行底栏（与浏览页/云盘目录页同构）；操作行仅启用对类别列表有意义的按钮
        binding.root.findViewById<View>(R.id.bottomActionRefresh).setOnClickListener { loadFiles() }
        binding.root.findViewById<View>(R.id.bottomActionMultiSelect).setOnClickListener {
            Toast.makeText(
                requireContext(),
                R.string.category_multi_select_not_supported,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setupBottomNavBar() {
        binding.root.findViewById<View>(R.id.bottomNavBrowse).isSelected = true
        val host = requireActivity() as? BottomNavHost
        binding.root.findViewById<View>(R.id.bottomNavBrowse).setOnClickListener { host?.showHome() }
        binding.root.findViewById<View>(R.id.bottomNavCloud).setOnClickListener { host?.showCloudStorage() }
        binding.root.findViewById<View>(R.id.bottomNavMenu).setOnClickListener { view ->
            val popupMenu = PopupMenu(requireContext(), view)
            popupMenu.menuInflater.inflate(R.menu.settings_about, popupMenu.menu)
            popupMenu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_settings -> {
                        startActivity(SettingsActivity::class.createIntent())
                        true
                    }
                    R.id.action_about -> {
                        startActivity(AboutActivity::class.createIntent())
                        true
                    }
                    else -> false
                }
            }
            popupMenu.show()
        }
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
                formatter.format(Date(file.attributes.lastModifiedTime().toMillis()))
            } catch (e: Exception) {
                "未知日期"
            }
            groups.getOrPut(key) { mutableListOf() }.add(file)
        }
        val items = mutableListOf<CategoryItem>()
        for ((date, list) in groups) {
            items += CategoryItem.Header(String.format(Locale.getDefault(), "%s | %d项", date, list.size))
            items += list.sortedByDescending { it.attributes.lastModifiedTime().toMillis() }
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

    private fun showItemMenu(file: FileItem, anchor: View) {
        val popupMenu = PopupMenu(requireContext(), anchor)
        popupMenu.menuInflater.inflate(R.menu.file_item, popupMenu.menu)
        // 类别页没有当前目录上下文，隐藏依赖目标目录的粘贴/解压/压缩项，其余功能全部恢复
        val hiddenIds = listOf(
            R.id.action_paste,
            R.id.action_extract,
            R.id.action_archive
        )
        for (id in hiddenIds) {
            popupMenu.menu.findItem(id)?.isVisible = false
        }
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_open_with -> {
                    openFileWithIntent(file, true)
                    true
                }
                R.id.action_share -> {
                    shareFile(file)
                    true
                }
                R.id.action_delete -> {
                    ConfirmDeleteFilesDialogFragment.show(fileItemSetOf(file), this)
                    true
                }
                R.id.action_copy_path -> {
                    copyPath(file.path)
                    true
                }
                R.id.action_copy -> {
                    copyFile(file)
                    true
                }
                R.id.action_cut -> {
                    cutFile(file)
                    true
                }
                R.id.action_rename -> {
                    RenameFileDialogFragment.show(file, this)
                    true
                }
                R.id.action_add_bookmark -> {
                    addBookmark(file)
                    true
                }
                R.id.action_create_shortcut -> {
                    createShortcut(file)
                    true
                }
                R.id.action_properties -> {
                    FilePropertiesDialogFragment.show(file, this)
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun openFileWithIntent(file: FileItem, withChooser: Boolean) {
        val path = file.path
        val mimeType = file.mimeType
        if (path.isArchivePath) {
            FileJobService.open(path, mimeType, withChooser, requireContext())
        } else {
            val intent = path.fileProviderUri.createViewIntent(mimeType)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                .let {
                    if (withChooser) {
                        it.withChooser(
                            EditFileActivity::class.createIntent()
                                .putArgs(EditFileActivity.Args(path, mimeType)),
                            OpenFileAsDialogActivity::class.createIntent()
                                .putArgs(OpenFileAsDialogFragment.Args(path))
                        )
                    } else {
                        it
                    }
                }
            startActivitySafe(intent)
        }
    }

    private fun shareFile(file: FileItem) {
        val uris = listOf(file.path.fileProviderUri)
        val intent = uris.createSendStreamIntent(listOf(file.mimeType)).withChooser()
        startActivitySafe(intent)
    }

    override fun deleteFiles(files: FileItemSet) {
        FileJobService.delete(files.map { it.path }, requireContext())
        loadFiles()
    }

    private fun copyPath(path: Path) {
        clipboardManager.copyText(path.toUserFriendlyString(), requireContext())
    }

    override fun renameFile(file: FileItem, newName: String) {
        FileJobService.rename(file.path, newName, requireContext())
        loadFiles()
    }

    override fun hasFileWithName(name: String): Boolean = allFiles.any { it.name == name }

    private fun copyFile(file: FileItem) {
        viewModel.addToPasteState(true, fileItemSetOf(file))
        Toast.makeText(requireContext(), R.string.file_category_copy_success, Toast.LENGTH_SHORT).show()
    }

    private fun cutFile(file: FileItem) {
        viewModel.addToPasteState(false, fileItemSetOf(file))
        Toast.makeText(requireContext(), R.string.file_category_cut_success, Toast.LENGTH_SHORT).show()
    }

    private fun addBookmark(file: FileItem) {
        BookmarkDirectories.add(BookmarkDirectory(null, file.path))
        Toast.makeText(requireContext(), R.string.file_add_bookmark_success, Toast.LENGTH_SHORT).show()
    }

    private fun createShortcut(file: FileItem) {
        val context = requireContext()
        val isDirectory = file.attributes.isDirectory
        val shortcutInfo = ShortcutInfoCompat.Builder(context, file.path.toString())
            .setShortLabel(file.name)
            .setIntent(
                if (isDirectory) {
                    FileListActivity.createViewIntent(file.path)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                } else {
                    OpenFileActivity.createIntent(file.path, file.mimeType)
                }
            )
            .setIcon(
                IconCompat.createWithResource(
                    context, if (isDirectory) {
                        R.mipmap.directory_shortcut_icon
                    } else {
                        R.mipmap.file_shortcut_icon
                    }
                )
            )
            .build()
        ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(context, R.string.shortcut_created, Toast.LENGTH_SHORT).show()
        }
    }
}

sealed interface CategoryItem {
    data class Header(val text: String) : CategoryItem
    data class Entry(val file: FileItem) : CategoryItem
}

class CategoryFileListAdapter(
    private val listener: (FileItem) -> Unit,
    private val longClickListener: (FileItem, View) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<CategoryItem>()

    fun submit(list: List<CategoryItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun isHeader(position: Int): Boolean = items[position] is CategoryItem.Header

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
                LayoutInflater.from(parent.context).inflate(R.layout.category_file_grid_item, parent, false)
            )
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is CategoryItem.Header -> (holder as HeaderViewHolder).textView.text = item.text
            is CategoryItem.Entry -> (holder as EntryViewHolder).bind(item.file, listener, longClickListener)
        }
    }

    private class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView as TextView
    }

    private class EntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnailImage: ImageView = itemView.findViewById(R.id.thumbnailImage)
        private val nameText: TextView = itemView.findViewById(R.id.nameText)

        fun bind(file: FileItem, listener: (FileItem) -> Unit, longClickListener: (FileItem, View) -> Unit) {
            nameText.text = file.name
            val path = file.path
            val attributes = file.attributes
            thumbnailImage.dispose()
            thumbnailImage.setImageDrawable(null)
            val isApk = file.mimeType.isApk || file.name.endsWith(".apk", ignoreCase = true)
            if (isApk || file.supportsThumbnail) {
                thumbnailImage.load(path to attributes)
            } else {
                thumbnailImage.setImageResource(file.mimeType.iconRes)
            }
            itemView.setOnClickListener { listener(file) }
            itemView.setOnLongClickListener {
                longClickListener(file, itemView)
                true
            }
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ENTRY = 1
    }
}
