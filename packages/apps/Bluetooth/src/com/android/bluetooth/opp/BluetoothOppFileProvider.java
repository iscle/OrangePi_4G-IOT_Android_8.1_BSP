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
package com.android.bluetooth.opp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.UserHandle;
import android.os.UserManager;
import android.support.v4.content.FileProvider;
import android.util.Log;
import java.io.File;

/**
 * A FileProvider for files received by Bluetooth share
 */
public class BluetoothOppFileProvider extends FileProvider {
    private static final String TAG = "BluetoothOppFileProvider";

    private Context mContext = null;
    private ProviderInfo mProviderInfo = null;
    private boolean mRegisteredReceiver = false;
    private boolean mInitialized = false;

    /** Broadcast receiver that attach FileProvider info when user unlocks the phone for the
     *  first time after reboot and the credential-encrypted storage is available.
     */
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, Intent intent) {
            if (Intent.ACTION_USER_UNLOCKED.equals(intent.getAction())) {
                attachInfo(mContext, mProviderInfo);
            }
        }
    };

    /**
     * After the FileProvider is instantiated, this method is called to provide the system with
     * information about the provider. The actual initialization is delayed until user unlock the
     * device
     *
     * @param context A {@link Context} for the current component.
     * @param info A {@link ProviderInfo} for the new provider.
     */
    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        synchronized (this) {
            mContext = context;
            mProviderInfo = info;
            if (!mRegisteredReceiver) {
                IntentFilter userFilter = new IntentFilter();
                userFilter.addAction(Intent.ACTION_USER_UNLOCKED);
                mContext.registerReceiverAsUser(
                        mBroadcastReceiver, UserHandle.CURRENT, userFilter, null, null);
                mRegisteredReceiver = true;
            }
            UserManager userManager = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
            if (userManager.isUserUnlocked()) {
                if (!mInitialized) {
                    if (Constants.DEBUG) Log.d(TAG, "Initialized");
                    super.attachInfo(mContext, mProviderInfo);
                    mInitialized = true;
                }
                if (mRegisteredReceiver) {
                    mContext.unregisterReceiver(mBroadcastReceiver);
                    mRegisteredReceiver = false;
                }
            }
        }
    }

    /**
     * Return a content URI for a given {@link File}. Specific temporary
     * permissions for the content URI can be set with
     * {@link Context#grantUriPermission(String, Uri, int)}, or added
     * to an {@link Intent} by calling {@link Intent#setData(Uri) setData()} and then
     * {@link Intent#setFlags(int) setFlags()}; in both cases, the applicable flags are
     * {@link Intent#FLAG_GRANT_READ_URI_PERMISSION} and
     * {@link Intent#FLAG_GRANT_WRITE_URI_PERMISSION}. A FileProvider can only return a
     * <code>content</code> {@link Uri} for file paths defined in their <code>&lt;paths&gt;</code>
     * meta-data element. See the Class Overview for more information.
     *
     * @param context A {@link Context} for the current component.
     * @param authority The authority of a {@link FileProvider} defined in a
     *            {@code <provider>} element in your app's manifest.
     * @param file A {@link File} pointing to the filename for which you want a
     * <code>content</code> {@link Uri}.
     * @return A content URI for the file. Null if the user hasn't unlock the phone
     * @throws IllegalArgumentException When the given {@link File} is outside
     * the paths supported by the provider.
     */
    public static Uri getUriForFile(Context context, String authority, File file) {
        UserManager userManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
        if (!userManager.isUserUnlocked()) {
            return null;
        }
        context = context.createCredentialProtectedStorageContext();
        return FileProvider.getUriForFile(context, authority, file);
    }
}
