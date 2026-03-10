package app.kitsunping

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

data class InstalledAppEntry(
    val packageName: String,
    val label: String,
    val iconBitmap: Bitmap? = null
)

class AppScanner(private val context: Context) {
    private val iconCache = mutableMapOf<String, Bitmap?>()

    fun getLaunchableApps(): List<InstalledAppEntry> {
        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val entries = mutableMapOf<String, InstalledAppEntry>()
        val resolveInfos = packageManager.queryIntentActivities(launcherIntent, 0)
        resolveInfos.forEach { info ->
            val pkg = info.activityInfo?.packageName?.trim().orEmpty()
            if (pkg.isBlank()) return@forEach
            if (pkg == context.packageName) return@forEach
            val cacheKey = pkg.lowercase()
            val launcherLabel = info.loadLabel(packageManager).toString().trim()
            val label = resolveBestLabel(packageManager, pkg, launcherLabel)
            val iconBitmap = iconCache[cacheKey] ?: run {
                val loaded = runCatching {
                    val iconDrawable = info.loadIcon(packageManager)
                    convertDrawableToBitmap(iconDrawable, 96, 96)
                }.getOrNull()
                if (loaded != null) {
                    iconCache[cacheKey] = loaded
                }
                loaded
            }
            val current = entries[pkg]
            if (current == null || current.label.equals(pkg, ignoreCase = true)) {
                entries[pkg] = InstalledAppEntry(packageName = pkg, label = label, iconBitmap = iconBitmap)
            }
        }

        val installedApps = runCatching { packageManager.getInstalledApplications(0) }.getOrDefault(emptyList())
        installedApps.forEach { appInfo ->
            val pkg = appInfo.packageName?.trim().orEmpty()
            if (pkg.isBlank()) return@forEach
            if (pkg == context.packageName) return@forEach
            if (entries.containsKey(pkg)) return@forEach

            val launchIntent = runCatching { packageManager.getLaunchIntentForPackage(pkg) }.getOrNull()
            if (launchIntent == null) return@forEach

            val label = runCatching { packageManager.getApplicationLabel(appInfo).toString().trim() }
                .getOrDefault(humanizePackageName(pkg))
            val cacheKey = pkg.lowercase()
            val iconBitmap = iconCache[cacheKey] ?: run {
                val loaded = runCatching {
                    val iconDrawable = appInfo.loadIcon(packageManager)
                    convertDrawableToBitmap(iconDrawable, 96, 96)
                }.getOrNull()
                if (loaded != null) {
                    iconCache[cacheKey] = loaded
                }
                loaded
            }

            entries[pkg] = InstalledAppEntry(packageName = pkg, label = label, iconBitmap = iconBitmap)
        }

        return entries.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    private fun resolveBestLabel(packageManager: PackageManager, packageName: String, launcherLabel: String): String {
        if (launcherLabel.isNotBlank() && !launcherLabel.equals(packageName, ignoreCase = true)) {
            return launcherLabel
        }

        val appLabel = runCatching {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString().trim()
        }.getOrNull().orEmpty()

        if (appLabel.isNotBlank() && !appLabel.equals(packageName, ignoreCase = true)) {
            return appLabel
        }

        return humanizePackageName(packageName)
    }

    private fun humanizePackageName(packageName: String): String {
        val tail = packageName.substringAfterLast('.', packageName)
        return tail
            .replace('_', ' ')
            .replace('-', ' ')
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            .ifBlank { packageName }
    }

    private fun convertDrawableToBitmap(drawable: Drawable, width: Int, height: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            val bitmap = drawable.bitmap
            return if (bitmap.width != width || bitmap.height != height) {
                Bitmap.createScaledBitmap(bitmap, width, height, true)
            } else {
                bitmap
            }
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
