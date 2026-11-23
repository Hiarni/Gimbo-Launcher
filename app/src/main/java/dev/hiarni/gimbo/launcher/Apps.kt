package dev.hiarni.gimbo.launcher

import android.content.Intent
import android.content.pm.PackageManager

object AppResolver {
    fun launchIntent(pm: PackageManager, pkg: String): Intent? = try {
        pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
    } catch (_: Exception) {
        null
    }
}
