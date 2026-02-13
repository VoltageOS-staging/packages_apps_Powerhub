/*
 * Copyright (C) 2026 VoltageOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.power.hub.fragments

import android.app.TimePickerDialog
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.settings.R
import java.util.Locale

/**
 * UI Controller for Nirvana Mode.
 * Allows selecting apps, setting schedules, and manual toggling.
 */
class NirvanaModeSettings : Fragment(R.layout.nirvana_mode_fragment) {

    private lateinit var packageManager: PackageManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppListAdapter
    private lateinit var packageList: List<PackageInfo>
    private lateinit var nirvanaUtils: NirvanaModeUtils

    private lateinit var scheduleStatusText: TextView
    private lateinit var scheduleSwitch: Switch
    private lateinit var scheduleCard: LinearLayout
    private lateinit var toggleButton: Button

    private var searchText = ""
    private var showSystem = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        packageManager = requireContext().packageManager
        packageList = packageManager.getInstalledPackages(PackageManager.MATCH_ANY_USER)
        nirvanaUtils = NirvanaModeUtils(requireContext())
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setTitle(R.string.nirvana_mode_title)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        scheduleStatusText = view.findViewById(R.id.schedule_status_text)
        scheduleSwitch = view.findViewById(R.id.schedule_switch)
        scheduleCard = view.findViewById(R.id.schedule_card)
        toggleButton = view.findViewById(R.id.btn_toggle_nirvana)
        recyclerView = view.findViewById(R.id.nirvana_app_list)

        adapter = AppListAdapter()
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        initScheduleUI()
        initManualToggleUI()
        refreshAppList()
    }

    private fun initScheduleUI() {
        updateScheduleText()
        scheduleSwitch.isChecked = nirvanaUtils.isScheduleEnabled()

        scheduleSwitch.setOnCheckedChangeListener { _, isChecked ->
            nirvanaUtils.setScheduleEnabled(isChecked)
            if (isChecked) {
                nirvanaUtils.scheduleNextAlarm()
                nirvanaUtils.setNirvanaModeActive(nirvanaUtils.shouldScheduleBeActive())
            } else {
                nirvanaUtils.cancelAlarms()
            }
            updateManualButtonState()
        }

        scheduleCard.setOnClickListener {
            showTimePickerSequence()
        }
    }

    private fun initManualToggleUI() {
        updateManualButtonState()
        toggleButton.setOnClickListener {
            val newState = !nirvanaUtils.isNirvanaModeActive()
            nirvanaUtils.setNirvanaModeActive(newState)
            updateManualButtonState()
        }
    }

    private fun updateManualButtonState() {
        val isActive = nirvanaUtils.isNirvanaModeActive()
        if (isActive) {
            toggleButton.text = getString(R.string.nirvana_mode_turn_off_now)
        } else {
            toggleButton.text = getString(R.string.nirvana_mode_turn_on_now)
        }
    }

    private fun updateScheduleText() {
        val start = formatTime(nirvanaUtils.getStartTime())
        val end = formatTime(nirvanaUtils.getEndTime())
        if (nirvanaUtils.isScheduleEnabled()) {
            scheduleStatusText.visibility = View.VISIBLE
            scheduleStatusText.text = "$start - $end"
        } else {
            scheduleStatusText.visibility = View.GONE
        }
    }

    private fun formatTime(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return String.format(Locale.getDefault(), "%02d:%02d", h, m)
    }

    private fun showTimePickerSequence() {
        val context = requireContext()
        val currentStart = nirvanaUtils.getStartTime()
        val currentEnd = nirvanaUtils.getEndTime()

        TimePickerDialog(context, { _, h, m ->
            val newStart = h * 60 + m
            TimePickerDialog(context, { _, h2, m2 ->
                val newEnd = h2 * 60 + m2
                nirvanaUtils.saveSchedule(newStart, newEnd)
                updateScheduleText()
                if (nirvanaUtils.isScheduleEnabled()) {
                    nirvanaUtils.scheduleNextAlarm()
                    nirvanaUtils.setNirvanaModeActive(nirvanaUtils.shouldScheduleBeActive())
                    updateManualButtonState()
                }
            }, currentEnd / 60, currentEnd % 60, true).apply {
                setTitle("Set End Time")
                show()
            }
        }, currentStart / 60, currentStart % 60, true).apply {
            setTitle("Set Start Time")
            show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.hide_applist_menu, menu)
        
        menu.findItem(R.id.show_overlay)?.isVisible = false
        menu.findItem(R.id.hide_overlay)?.isVisible = false

        val searchItem = menu.findItem(R.id.search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = getString(R.string.search_apps)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String) = false
            override fun onQueryTextChange(newText: String): Boolean {
                searchText = newText
                refreshAppList()
                return true
            }
        })

        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                ViewCompat.setNestedScrollingEnabled(recyclerView, false)
                return true
            }
            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                ViewCompat.setNestedScrollingEnabled(recyclerView, true)
                return true
            }
        })
        
        updateMenuState(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.show_system, R.id.hide_system -> {
                showSystem = !showSystem
                refreshAppList()
                activity?.invalidateOptionsMenu()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        updateMenuState(menu)
    }

    private fun updateMenuState(menu: Menu) {
        menu.findItem(R.id.show_system)?.isVisible = !showSystem
        menu.findItem(R.id.hide_system)?.isVisible = showSystem
    }

    private fun refreshAppList() {
        
        val filtered = packageList.filter {
            val appInfo = it.applicationInfo!!
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val pkg = it.packageName
            val label = getLabel(it)

            val isVisibleType = showSystem || (!isSystem || isUpdatedSystem)
            val searchFilter = label.contains(searchText, true)
            
            isVisibleType && searchFilter && pkg != "com.android.settings"
        }.sortedWith { a, b -> getLabel(a).compareTo(getLabel(b), true) }

        adapter.submitList(filtered.map { appInfoFromPackageInfo(it) })
    }

    private fun getLabel(packageInfo: PackageInfo): String {
        val label = packageInfo.applicationInfo!!.loadLabel(packageManager).toString()
        return if (isPackageSuspended(packageInfo.packageName)) {
            "$label (Suspended)"
        } else {
            label
        }
    }

    private fun isPackageSuspended(packageName: String): Boolean {
        return try {
            packageManager.isPackageSuspended(packageName)
        } catch (e: Exception) {
            false
        }
    }

    private fun appInfoFromPackageInfo(packageInfo: PackageInfo) =
        AppInfo(
            packageInfo.packageName,
            getLabel(packageInfo),
            packageInfo.applicationInfo!!.loadIcon(packageManager)
        )

    private fun onListUpdate(packageName: String, isChecked: Boolean) {
        if (isChecked) {
            nirvanaUtils.addApp(packageName)
        } else {
            nirvanaUtils.removeApp(packageName)
        }
    }

    private inner class AppListAdapter : ListAdapter<AppInfo, AppListViewHolder>(DiffCallback) {
        private var selectedList = nirvanaUtils.getSelectedApps().toMutableSet()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppListViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.hide_applist_list_item, parent, false)
            return AppListViewHolder(view)
        }

        override fun onBindViewHolder(holder: AppListViewHolder, position: Int) {
            val item = getItem(position)
            holder.bind(item, selectedList.contains(item.packageName))

            holder.itemView.setOnClickListener {
                val isSelected = selectedList.contains(item.packageName)
                if (isSelected) {
                    selectedList.remove(item.packageName)
                } else {
                    selectedList.add(item.packageName)
                }
                onListUpdate(item.packageName, !isSelected)
                
                holder.checkBox.isChecked = !isSelected
            }
        }
        
        override fun submitList(list: List<AppInfo>?) {
             selectedList = nirvanaUtils.getSelectedApps().toMutableSet()
             super.submitList(list)
        }
    }

    private class AppListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.app_icon)
        val label: TextView = itemView.findViewById(R.id.app_name)
        val pkg: TextView = itemView.findViewById(R.id.package_name)
        val checkBox: CheckBox = itemView.findViewById(R.id.check_box)

        fun bind(info: AppInfo, isChecked: Boolean) {
            label.text = info.label
            pkg.text = info.packageName
            icon.setImageDrawable(info.icon)
            checkBox.isChecked = isChecked
        }
    }

    data class AppInfo(val packageName: String, val label: String, val icon: Drawable)

    object DiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo) =
            oldItem.packageName == newItem.packageName
        override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo) =
            oldItem == newItem
    }
}
