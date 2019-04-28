/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tv.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.media.tv.TvInputInfo;
import android.text.TextUtils;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * This file refers to Partner.java in LeanbackLauncher. Interact with partner customizations. There
 * can only be one set of customizations on a device, and it must be bundled with the system.
 */
public class Partner {
    private static final String TAG = "Partner";
    /** Marker action used to discover partner */
    private static final String ACTION_PARTNER_CUSTOMIZATION =
            "com.google.android.leanbacklauncher.action.PARTNER_CUSTOMIZATION";

    /** ID tags for device input types */
    public static final String INPUT_TYPE_BUNDLED_TUNER = "input_type_combined_tuners";
    public static final String INPUT_TYPE_TUNER = "input_type_tuner";
    public static final String INPUT_TYPE_CEC_LOGICAL = "input_type_cec_logical";
    public static final String INPUT_TYPE_CEC_RECORDER = "input_type_cec_recorder";
    public static final String INPUT_TYPE_CEC_PLAYBACK = "input_type_cec_playback";
    public static final String INPUT_TYPE_MHL_MOBILE = "input_type_mhl_mobile";
    public static final String INPUT_TYPE_HDMI = "input_type_hdmi";
    public static final String INPUT_TYPE_DVI = "input_type_dvi";
    public static final String INPUT_TYPE_COMPONENT = "input_type_component";
    public static final String INPUT_TYPE_SVIDEO = "input_type_svideo";
    public static final String INPUT_TYPE_COMPOSITE = "input_type_composite";
    public static final String INPUT_TYPE_DISPLAY_PORT = "input_type_displayport";
    public static final String INPUT_TYPE_VGA = "input_type_vga";
    public static final String INPUT_TYPE_SCART = "input_type_scart";
    public static final String INPUT_TYPE_OTHER = "input_type_other";

    private static final String INPUTS_ORDER = "home_screen_inputs_ordering";
    private static final String TYPE_ARRAY = "array";

    private static Partner sPartner;
    private static final Object sLock = new Object();

    private final String mPackageName;
    private final String mReceiverName;
    private final Resources mResources;

    private static final Map<String, Integer> INPUT_TYPE_MAP = new HashMap<>();
    static {
        INPUT_TYPE_MAP.put(INPUT_TYPE_BUNDLED_TUNER, TvInputManagerHelper.TYPE_BUNDLED_TUNER);
        INPUT_TYPE_MAP.put(INPUT_TYPE_TUNER, TvInputInfo.TYPE_TUNER);
        INPUT_TYPE_MAP.put(INPUT_TYPE_CEC_LOGICAL, TvInputManagerHelper.TYPE_CEC_DEVICE);
        INPUT_TYPE_MAP.put(INPUT_TYPE_CEC_RECORDER, TvInputManagerHelper.TYPE_CEC_DEVICE_RECORDER);
        INPUT_TYPE_MAP.put(INPUT_TYPE_CEC_PLAYBACK, TvInputManagerHelper.TYPE_CEC_DEVICE_PLAYBACK);
        INPUT_TYPE_MAP.put(INPUT_TYPE_MHL_MOBILE, TvInputManagerHelper.TYPE_MHL_MOBILE);
        INPUT_TYPE_MAP.put(INPUT_TYPE_HDMI, TvInputInfo.TYPE_HDMI);
        INPUT_TYPE_MAP.put(INPUT_TYPE_DVI, TvInputInfo.TYPE_DVI);
        INPUT_TYPE_MAP.put(INPUT_TYPE_COMPONENT, TvInputInfo.TYPE_COMPONENT);
        INPUT_TYPE_MAP.put(INPUT_TYPE_SVIDEO, TvInputInfo.TYPE_SVIDEO);
        INPUT_TYPE_MAP.put(INPUT_TYPE_COMPOSITE, TvInputInfo.TYPE_COMPOSITE);
        INPUT_TYPE_MAP.put(INPUT_TYPE_DISPLAY_PORT, TvInputInfo.TYPE_DISPLAY_PORT);
        INPUT_TYPE_MAP.put(INPUT_TYPE_VGA, TvInputInfo.TYPE_VGA);
        INPUT_TYPE_MAP.put(INPUT_TYPE_SCART, TvInputInfo.TYPE_SCART);
        INPUT_TYPE_MAP.put(INPUT_TYPE_OTHER, TvInputInfo.TYPE_OTHER);
    }

    private Partner(String packageName, String receiverName, Resources res) {
        mPackageName = packageName;
        mReceiverName = receiverName;
        mResources = res;
    }

    /** Returns partner instance. */
    public static Partner getInstance(Context context) {
        PackageManager pm = context.getPackageManager();
        synchronized (sLock) {
            ResolveInfo info = getPartnerResolveInfo(pm);
            if (info != null) {
                final String packageName = info.activityInfo.packageName;
                final String receiverName = info.activityInfo.name;
                try {
                    final Resources res = pm.getResourcesForApplication(packageName);
                    sPartner = new Partner(packageName, receiverName, res);
                    sPartner.sendInitBroadcast(context);
                } catch (PackageManager.NameNotFoundException e) {
                    Log.w(TAG, "Failed to find resources for " + packageName);
                }
            }
            if (sPartner == null) {
                sPartner = new Partner(null, null, null);
            }
        }
        return sPartner;
    }

    /** Resets the Partner instance to handle the partner package has changed. */
    public static void reset(Context context, String packageName) {
        synchronized (sLock) {
            if (sPartner != null && !TextUtils.isEmpty(packageName)) {
                if (packageName.equals(sPartner.mPackageName)) {
                    // Force a refresh, so we send an Init to the updated package
                    sPartner = null;
                    getInstance(context);
                }
            }
        }
    }

    /** This method is used to send init broadcast to the new/changed partner package. */
    private void sendInitBroadcast(Context context) {
        if (!TextUtils.isEmpty(mPackageName) && !TextUtils.isEmpty(mReceiverName)) {
            Intent intent = new Intent(ACTION_PARTNER_CUSTOMIZATION);
            final ComponentName componentName = new ComponentName(mPackageName, mReceiverName);
            intent.setComponent(componentName);
            intent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            context.sendBroadcast(intent);
        }
    }

    /** Returns the order of inputs. */
    public Map<Integer, Integer> getInputsOrderMap() {
        HashMap<Integer, Integer> map = new HashMap<>();
        if (mResources != null && !TextUtils.isEmpty(mPackageName)) {
            String[] inputsArray = null;
            final int resId = mResources.getIdentifier(INPUTS_ORDER, TYPE_ARRAY, mPackageName);
            if (resId != 0) {
                inputsArray = mResources.getStringArray(resId);
            }
            if (inputsArray != null) {
                int priority = 0;
                for (String input : inputsArray) {
                    Integer type = INPUT_TYPE_MAP.get(input);
                    if (type != null) {
                        map.put(type, priority++);
                    }
                }
            }
        }
        return map;
    }

    private static ResolveInfo getPartnerResolveInfo(PackageManager pm) {
        final Intent intent = new Intent(ACTION_PARTNER_CUSTOMIZATION);
        ResolveInfo partnerInfo = null;
        for (ResolveInfo info : pm.queryBroadcastReceivers(intent, 0)) {
            if (isSystemApp(info)) {
                partnerInfo = info;
                break;
            }
        }
        return partnerInfo;
    }

    protected static boolean isSystemApp(ResolveInfo info) {
        return (info.activityInfo != null
                && info.activityInfo.applicationInfo != null
                && (info.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
    }
}
