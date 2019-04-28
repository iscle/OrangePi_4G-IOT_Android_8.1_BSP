package com.android.tv.tuner.util;

import android.content.Context;

/**
 * A helper class of tuner configuration.
 */
public class TisConfiguration {
    private static final String LC_PACKAGE_NAME = "com.android.tv";

    public static boolean isPackagedWithLiveChannels(Context context) {
        return (LC_PACKAGE_NAME.equals(context.getPackageName()));
    }

    public static boolean isInternalTunerTvInput(Context context) {
        return (!LC_PACKAGE_NAME.equals(context.getPackageName()));
    }

    public static int getTunerHwDeviceId(Context context) {
        return 0;  // FIXME: Make it OEM configurable
    }
}
