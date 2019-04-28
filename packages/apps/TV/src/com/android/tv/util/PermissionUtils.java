package com.android.tv.util;

import android.content.Context;
import android.content.pm.PackageManager;

/**
 * Util class to handle permissions.
 */
public class PermissionUtils {
    /**
     * Permission to read the TV listings.
     */
    public static final String PERMISSION_READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS";

    private static Boolean sHasAccessAllEpgPermission;
    private static Boolean sHasAccessWatchedHistoryPermission;
    private static Boolean sHasModifyParentalControlsPermission;

    public static boolean hasAccessAllEpg(Context context) {
        if (sHasAccessAllEpgPermission == null) {
            sHasAccessAllEpgPermission = context.checkSelfPermission(
                    "com.android.providers.tv.permission.ACCESS_ALL_EPG_DATA")
                    == PackageManager.PERMISSION_GRANTED;
        }
        return sHasAccessAllEpgPermission;
    }

    public static boolean hasAccessWatchedHistory(Context context) {
        if (sHasAccessWatchedHistoryPermission == null) {
            sHasAccessWatchedHistoryPermission = context.checkSelfPermission(
                    "com.android.providers.tv.permission.ACCESS_WATCHED_PROGRAMS")
                    == PackageManager.PERMISSION_GRANTED;
        }
        return sHasAccessWatchedHistoryPermission;
    }

    public static boolean hasModifyParentalControls(Context context) {
        if (sHasModifyParentalControlsPermission == null) {
            sHasModifyParentalControlsPermission = context.checkSelfPermission(
                    "android.permission.MODIFY_PARENTAL_CONTROLS")
                    == PackageManager.PERMISSION_GRANTED;
        }
        return sHasModifyParentalControlsPermission;
    }

    public static boolean hasReadTvListings(Context context) {
        return context.checkSelfPermission(PERMISSION_READ_TV_LISTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasInternet(Context context) {
        return context.checkSelfPermission("android.permission.INTERNET")
                == PackageManager.PERMISSION_GRANTED;
    }
}
