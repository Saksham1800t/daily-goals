package com.example.dailygoals.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

class InstalledApp(val packageName: String, val label: String)

/** Apps with a launcher icon, without Skriti itself, sorted by name. */
fun loadInstalledApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
        .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}
