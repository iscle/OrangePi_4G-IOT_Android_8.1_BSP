package com.android.storagemanager.automatic;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.provider.Settings;

/**
 * Utility class to check the status of some preconditions that are used by
 * {@link DownloadsBackupJobService} and {@link AutomaticStorageManagementJobService}.
 */
public class JobPreconditions {

    public static boolean isNetworkMetered(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            return connectivityManager.isActiveNetworkMetered();
        }
        return true;
    }

    public static boolean isWifiConnected(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            for (Network network : connectivityManager.getAllNetworks()) {
                NetworkInfo networkInfo = connectivityManager.getNetworkInfo(network);
                if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI
                        && networkInfo.isConnected()) {
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean isCharging(Context context) {
        BatteryManager batteryManager = (BatteryManager) context.getSystemService(
                Context.BATTERY_SERVICE);
        if (batteryManager != null) {
            return batteryManager.isCharging();
        }
        return false;
    }

    public static boolean isIdle(Context context) {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            return powerManager.isDeviceIdleMode();
        }
        return false;
    }

}
