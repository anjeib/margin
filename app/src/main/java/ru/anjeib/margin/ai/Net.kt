package ru.anjeib.margin.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Whether the phone can reach the AI right now: a network that is really online, not just connected. */
object Net {
    fun online(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
