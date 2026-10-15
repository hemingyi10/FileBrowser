/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.HomeFragmentBinding
import me.zhanghai.android.files.filelist.FileListActivity
import me.zhanghai.android.files.filelist.FileListFragment
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.ui.category.Category
import me.zhanghai.android.files.ui.category.CategoryFileListFragment
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.valueCompat

class HomeFragment : Fragment() {

    private lateinit var binding: HomeFragmentBinding
    private lateinit var adapter: HomeCategoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = HomeFragmentBinding.inflate(inflater, container, false)
        .also { binding = it }
        .root

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val activity = requireActivity() as AppCompatActivity
        activity.setTitle(R.string.home_title)
        activity.setSupportActionBar(binding.toolbar)
        binding.recyclerView.isNestedScrollingEnabled = false
        binding.recyclerView.layoutManager = GridLayoutManager(activity, 3)
        adapter = HomeCategoryAdapter { category -> openCategory(category) }
        binding.recyclerView.adapter = adapter
        // 浏览页底栏由内嵌 FileListFragment 自身渲染（与云盘目录页同构），隐藏 HomeFragment 根布局 include 的底栏
        // 必须经唯一容器 homeBottomBarContainer 隐藏：findViewById 会先命中内嵌 FileListFragment
        // 同 id(bottomBarLayout) 的底栏（从类别页返回恢复时尤其如此），导致隐藏错对象、自身底栏残留并叠加
        binding.homeBottomBarContainer.isVisible = false
        embedInternalStorage()
    }

    private fun embedInternalStorage() {
        val existing = childFragmentManager.findFragmentById(R.id.fileListContainer)
        if (existing == null) {
            val intent = FileListActivity.createViewIntent(
                Settings.FILE_LIST_DEFAULT_DIRECTORY.valueCompat
            )
            val fileListFragment = FileListFragment().putArgs(
                FileListFragment.Args(intent, embedded = true)
            )
            childFragmentManager.commit {
                add(R.id.fileListContainer, fileListFragment)
            }
        }
        binding.homeBottomBarContainer.isVisible = false
    }

    private fun openCategory(category: Category) {
        requireActivity().supportFragmentManager.commit {
            replace(android.R.id.content, CategoryFileListFragment.newInstance(category))
            addToBackStack(category.name)
        }
    }

    private class HomeCategoryAdapter(
        private val listener: (Category) -> Unit
    ) : RecyclerView.Adapter<HomeCategoryAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.home_category_item, parent, false)
            )

        override fun getItemCount(): Int = Category.values().size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val category = Category.values()[position]
            holder.iconImage.setImageResource(category.iconRes)
            holder.nameText.text = holder.itemView.context.getString(category.titleRes)
            holder.itemView.setOnClickListener { listener(category) }
        }

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val iconImage: android.widget.ImageView = itemView.findViewById(R.id.iconImage)
            val nameText: android.widget.TextView = itemView.findViewById(R.id.nameText)
        }
    }
}
