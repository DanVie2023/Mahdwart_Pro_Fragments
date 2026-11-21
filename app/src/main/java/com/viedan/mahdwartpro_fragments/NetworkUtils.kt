package com.viedan.mahdwartpro_fragments

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Helper for checking the state of the device's network connection.
 *
 * Requires the permission `android.permission.ACCESS_NETWORK_STATE` in the manifest.
 */
object NetworkUtils {

    /**
     * Checks whether the device currently has a working internet connection.
     *
     * A connection counts as working if the active network offers the internet capability
     * and the system has validated it, i.e. it is not just connected to a Wi-Fi without
     * internet access (for example a captive portal).
     *
     * Note: This is a snapshot. The connection can still drop right after the check,
     * so network errors have to be handled by the caller anyway.
     *
     * @param context Any context, used to obtain the [ConnectivityManager].
     * @return `true` if the device is online, `false` if offline or if the state is unknown.
     */
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
