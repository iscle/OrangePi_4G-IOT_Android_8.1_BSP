/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tv.tuner;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.tv.Features;
import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.tuner.setup.TunerSetupActivity;
import com.android.tv.tuner.tvinput.TunerTvInputService;
import com.android.tv.tuner.util.SystemPropertiesProxy;
import com.android.tv.tuner.util.TunerInputInfoUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Controls the package visibility of {@link TunerTvInputService}.
 * <p>
 * Listens to broadcast intent for {@link Intent#ACTION_BOOT_COMPLETED},
 * {@code UsbManager.ACTION_USB_DEVICE_ATTACHED}, and {@code UsbManager.ACTION_USB_DEVICE_ATTACHED}
 * to update the connection status of the supported USB TV tuners.
 */
public class TunerInputController {
    private static final boolean DEBUG = true;
    private static final String TAG = "TunerInputController";
    private static final String PREFERENCE_IS_NETWORK_TUNER_ATTACHED = "network_tuner";
    private static final String SECURITY_PATCH_LEVEL_KEY = "ro.build.version.security_patch";
    private static final String SECURITY_PATCH_LEVEL_FORMAT = "yyyy-MM-dd";

    /**
     * Action of {@link Intent} to check network connection repeatedly when it is necessary.
     */
    private static final String CHECKING_NETWORK_CONNECTION =
            "com.android.tv.action.CHECKING_NETWORK_CONNECTION";

    private static final String EXTRA_CHECKING_DURATION =
            "com.android.tv.action.extra.CHECKING_DURATION";

    private static final long INITIAL_CHECKING_DURATION_MS = TimeUnit.SECONDS.toMillis(10);
    private static final long MAXIMUM_CHECKING_DURATION_MS = TimeUnit.MINUTES.toMillis(10);

    private static final TunerDevice[] TUNER_DEVICES = {
        new TunerDevice(0x2040, 0xb123, null), // WinTV-HVR-955Q
        new TunerDevice(0x07ca, 0x0837, null), // AverTV Volar Hybrid Q
        // WinTV-dualHD (bulk) will be supported after 2017 April security patch.
        new TunerDevice(0x2040, 0x826d, "2017-04-01"), // WinTV-dualHD (bulk)
        // STOPSHIP: Add WinTV-soloHD (Isoc) temporary for test. Remove this after test complete.
        new TunerDevice(0x2040, 0x0264, null),
    };

    private static final int MSG_ENABLE_INPUT_SERVICE = 1000;
    private static final long DVB_DRIVER_CHECK_DELAY_MS = 300;

    /**
     * Checks status of USB devices to see if there are available USB tuners connected.
     */
    public static void onCheckingUsbTunerStatus(Context context, String action) {
        onCheckingUsbTunerStatus(context, action, new CheckDvbDeviceHandler());
    }

    private static void onCheckingUsbTunerStatus(Context context, String action,
            @NonNull CheckDvbDeviceHandler handler) {
        SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        if (TunerHal.useBuiltInTuner(context)) {
            enableTunerTvInputService(context, true, false, TunerHal.TUNER_TYPE_BUILT_IN);
            return;
        }
        // Falls back to the below to check USB tuner devices.
        boolean enabled = isUsbTunerConnected(context);
        handler.removeMessages(MSG_ENABLE_INPUT_SERVICE);
        if (enabled) {
            // Need to check if DVB driver is accessible. Since the driver creation
            // could be happen after the USB event, delay the checking by
            // DVB_DRIVER_CHECK_DELAY_MS.
            handler.sendMessageDelayed(handler.obtainMessage(MSG_ENABLE_INPUT_SERVICE, context),
                    DVB_DRIVER_CHECK_DELAY_MS);
        } else {
            if (sharedPreferences.getBoolean(PREFERENCE_IS_NETWORK_TUNER_ATTACHED, false)) {
                // Since network tuner is attached, do not disable TunerTvInput,
                // just updates the TvInputInfo.
                TunerInputInfoUtils.updateTunerInputInfo(context);
                return;
            }
            enableTunerTvInputService(context, false, false, TextUtils
                    .equals(action, UsbManager.ACTION_USB_DEVICE_DETACHED) ?
                    TunerHal.TUNER_TYPE_USB : null);
        }
    }

    private static void onNetworkTunerChanged(Context context, boolean enabled) {
        SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        if (enabled) {
            // Network tuner detection is initiated by UI. So the app should not
            // be killed.
            sharedPreferences.edit().putBoolean(PREFERENCE_IS_NETWORK_TUNER_ATTACHED, true).apply();
            enableTunerTvInputService(context, true, true, TunerHal.TUNER_TYPE_NETWORK);
        } else {
            sharedPreferences.edit()
                    .putBoolean(PREFERENCE_IS_NETWORK_TUNER_ATTACHED, false).apply();
            if(!isUsbTunerConnected(context) && !TunerHal.useBuiltInTuner(context)) {
                // Network tuner detection is initiated by UI. So the app should not
                // be killed.
                enableTunerTvInputService(context, false, true, TunerHal.TUNER_TYPE_NETWORK);
            } else {
                // Since USB tuner is attached, do not disable TunerTvInput,
                // just updates the TvInputInfo.
                TunerInputInfoUtils.updateTunerInputInfo(context);
            }
        }
    }

    /**
     * See if any USB tuner hardware is attached in the system.
     *
     * @param context {@link Context} instance
     * @return {@code true} if any tuner device we support is plugged in
     */
    private static boolean isUsbTunerConnected(Context context) {
        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        Map<String, UsbDevice> deviceList = manager.getDeviceList();
        String currentSecurityLevel =
                SystemPropertiesProxy.getString(SECURITY_PATCH_LEVEL_KEY, null);

        for (UsbDevice device : deviceList.values()) {
            if (DEBUG) {
                Log.d(TAG, "Device: " + device);
            }
            for (TunerDevice tuner : TUNER_DEVICES) {
                if (tuner.equals(device) && tuner.isSupported(currentSecurityLevel)) {
                    Log.i(TAG, "Tuner found");
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Enable/disable the component {@link TunerTvInputService}.
     *
     * @param context {@link Context} instance
     * @param enabled {@code true} to enable the service; otherwise {@code false}
     */
    private static void enableTunerTvInputService(Context context, boolean enabled,
            boolean forceDontKillApp, Integer tunerType) {
        if (DEBUG) Log.d(TAG, "enableTunerTvInputService: " + enabled);
        PackageManager pm  = context.getPackageManager();
        ComponentName componentName = new ComponentName(context, TunerTvInputService.class);

        // Don't kill app by enabling/disabling TvActivity. If LC is killed by enabling/disabling
        // TvActivity, the following pm.setComponentEnabledSetting doesn't work.
        ((TvApplication) context.getApplicationContext()).handleInputCountChanged(
                true, enabled, true);
        // Since PackageManager.DONT_KILL_APP delays the operation by 10 seconds
        // (PackageManagerService.BROADCAST_DELAY), we'd better avoid using it. It is used only
        // when the LiveChannels app is active since we don't want to kill the running app.
        int flags = forceDontKillApp
                || TvApplication.getSingletons(context).getMainActivityWrapper().isCreated()
                ? PackageManager.DONT_KILL_APP : 0;
        int newState = enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        if (newState != pm.getComponentEnabledSetting(componentName)) {
            // Send/cancel the USB tuner TV input setup notification.
            TunerSetupActivity.onTvInputEnabled(context, enabled, tunerType);
            // Enable/disable the USB tuner TV input.
            pm.setComponentEnabledSetting(componentName, newState, flags);
            if (!enabled && tunerType != null) {
                if (tunerType == TunerHal.TUNER_TYPE_USB) {
                    Toast.makeText(context, R.string.msg_usb_tuner_disconnected,
                            Toast.LENGTH_SHORT).show();
                } else if (tunerType == TunerHal.TUNER_TYPE_NETWORK) {
                    Toast.makeText(context, R.string.msg_network_tuner_disconnected,
                            Toast.LENGTH_SHORT).show();
                }
            }
            if (DEBUG) Log.d(TAG, "Status updated:" + enabled);
        } else if (enabled) {
            // When # of tuners is changed or the tuner input service is switching from/to using
            // network tuners or the device just boots.
            TunerInputInfoUtils.updateTunerInputInfo(context);
        }
    }

    /**
     * Discovers a network tuner. If the network connection is down, it won't repeatedly checking.
     */
    public static void executeNetworkTunerDiscoveryAsyncTask(final Context context) {
        boolean runningInMainProcess =
                TvApplication.getSingletons(context).isRunningInMainProcess();
        SoftPreconditions.checkState(runningInMainProcess);
        if (!runningInMainProcess) {
            return;
        }
        executeNetworkTunerDiscoveryAsyncTask(context, 0);
    }

    /**
     * Discovers a network tuner.
     * @param context {@link Context}
     * @param repeatedDurationMs the time length to wait to repeatedly check network status to start
     *                           finding network tuner when the network connection is not available.
     *                           {@code 0} to disable repeatedly checking.
     */
    private static void executeNetworkTunerDiscoveryAsyncTask(final Context context,
            final long repeatedDurationMs) {
        if (!Features.NETWORK_TUNER.isEnabled(context)) {
            return;
        }
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                if (isNetworkConnected(context)) {
                    // Implement and execute network tuner discovery AsyncTask here.
                } else if (repeatedDurationMs > 0) {
                    AlarmManager alarmManager =
                            (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                    Intent networkCheckingIntent = new Intent(context, IntentReceiver.class);
                    networkCheckingIntent.setAction(CHECKING_NETWORK_CONNECTION);
                    networkCheckingIntent.putExtra(EXTRA_CHECKING_DURATION, repeatedDurationMs);
                    PendingIntent alarmIntent = PendingIntent.getBroadcast(
                            context, 0, networkCheckingIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                    alarmManager.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime()
                            + repeatedDurationMs, alarmIntent);
                }
                return null;
            }

            @Override
            protected void onPostExecute(Boolean result) {
                if (result == null) {
                    return;
                }
                onNetworkTunerChanged(context, result);
            }
        }.execute();
    }

    private static boolean isNetworkConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    public static class IntentReceiver extends BroadcastReceiver {
        private final CheckDvbDeviceHandler mHandler = new CheckDvbDeviceHandler();

        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.d(TAG, "Broadcast intent received:" + intent);
            TvApplication.setCurrentRunningProcess(context, true);
            if (!Features.TUNER.isEnabled(context)) {
                enableTunerTvInputService(context, false, false, null);
                return;
            }
            switch (intent.getAction()) {
                case Intent.ACTION_BOOT_COMPLETED:
                    executeNetworkTunerDiscoveryAsyncTask(context, INITIAL_CHECKING_DURATION_MS);
                case TvApplication.ACTION_APPLICATION_FIRST_LAUNCHED:
                case UsbManager.ACTION_USB_DEVICE_ATTACHED:
                case UsbManager.ACTION_USB_DEVICE_DETACHED:
                    onCheckingUsbTunerStatus(context, intent.getAction(), mHandler);
                    break;
                case CHECKING_NETWORK_CONNECTION:
                    long repeatedDurationMs = intent.getLongExtra(EXTRA_CHECKING_DURATION,
                            INITIAL_CHECKING_DURATION_MS);
                    executeNetworkTunerDiscoveryAsyncTask(context,
                            Math.min(repeatedDurationMs * 2, MAXIMUM_CHECKING_DURATION_MS));
                    break;
            }
        }
    }

    /**
     * Simple data holder for a USB device. Used to represent a tuner model, and compare
     * against {@link UsbDevice}.
     */
    private static class TunerDevice {
        private final int vendorId;
        private final int productId;

        // security patch level from which the specific tuner type is supported.
        private final String minSecurityLevel;

        private TunerDevice(int vendorId, int productId, String minSecurityLevel) {
            this.vendorId = vendorId;
            this.productId = productId;
            this.minSecurityLevel = minSecurityLevel;
        }

        private boolean equals(UsbDevice device) {
            return device.getVendorId() == vendorId && device.getProductId() == productId;
        }

        private boolean isSupported(String currentSecurityLevel) {
            if (minSecurityLevel == null) {
                return true;
            }

            long supportSecurityLevelTimeStamp = 0;
            long currentSecurityLevelTimestamp = 0;
            try {
                SimpleDateFormat format = new SimpleDateFormat(SECURITY_PATCH_LEVEL_FORMAT);
                supportSecurityLevelTimeStamp = format.parse(minSecurityLevel).getTime();
                currentSecurityLevelTimestamp = format.parse(currentSecurityLevel).getTime();
            } catch (ParseException e) {
            }
            return supportSecurityLevelTimeStamp != 0
                    && supportSecurityLevelTimeStamp <= currentSecurityLevelTimestamp;
        }
    }

    private static class CheckDvbDeviceHandler extends Handler {
        private DvbDeviceAccessor mDvbDeviceAccessor;

        CheckDvbDeviceHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_ENABLE_INPUT_SERVICE:
                    Context context = (Context) msg.obj;
                    if (mDvbDeviceAccessor == null) {
                        mDvbDeviceAccessor = new DvbDeviceAccessor(context);
                    }
                    boolean enabled = mDvbDeviceAccessor.isDvbDeviceAvailable();
                    enableTunerTvInputService(
                            context, enabled, false, enabled ? TunerHal.TUNER_TYPE_USB : null);
                    break;
            }
        }
    }
}
