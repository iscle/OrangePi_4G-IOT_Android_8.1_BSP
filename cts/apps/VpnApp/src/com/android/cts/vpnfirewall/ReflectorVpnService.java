/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.vpnfirewall;

import android.R;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.VpnService;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class ReflectorVpnService extends VpnService {

    private static String TAG = "ReflectorVpnService";
    private static final int NOTIFICATION_ID = 1;
    private static final String NOTIFICATION_CHANNEL_ID = TAG;
    private static int MTU = 1799;

    private ParcelFileDescriptor mFd = null;
    private PingReflector mPingReflector = null;

    public static final String RESTRICTION_ADDRESSES = "vpn.addresses";
    public static final String RESTRICTION_ROUTES = "vpn.routes";
    public static final String RESTRICTION_ALLOWED = "vpn.allowed";
    public static final String RESTRICTION_DISALLOWED = "vpn.disallowed";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Put ourself in the foreground to stop the system killing us while we wait for orders from
        // the hostside test.
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(new NotificationChannel(
                NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_ID,
                NotificationManager.IMPORTANCE_DEFAULT));
        startForeground(NOTIFICATION_ID, new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_dialog_alert)
                .build());
        start();
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        stop();
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_ID);
        super.onDestroy();
    }

    private void start() {
        VpnService.prepare(this);
        Builder builder = new Builder();

        final UserManager um = (UserManager) getSystemService(Context.USER_SERVICE);
        final Bundle restrictions = um.getApplicationRestrictions(getPackageName());

        String[] addressArray = restrictions.getStringArray(RESTRICTION_ADDRESSES);
        if (addressArray == null) {
            // Addresses for IPv4/IPv6 documentation purposes according to rfc5737/rfc3849.
            addressArray = new String[] {"192.0.2.3/32", "2001:db8:1:2::/128"};
        };
        for (int i = 0; i < addressArray.length; i++) {
            String[] prefixAndMask = addressArray[i].split("/");
            try {
                InetAddress address = InetAddress.getByName(prefixAndMask[0]);
                int prefixLength = Integer.parseInt(prefixAndMask[1]);
                builder.addAddress(address, prefixLength);
            } catch (NumberFormatException | UnknownHostException e) {
                Log.w(TAG, "Ill-formed address: " + addressArray[i]);
                continue;
            }
        }

        String[] routeArray = restrictions.getStringArray(RESTRICTION_ROUTES);
        if (routeArray == null) {
            routeArray = new String[] {"0.0.0.0/0", "::/0"};
        }
        for (int i = 0; i < routeArray.length; i++) {
            String[] prefixAndMask = routeArray[i].split("/");
            try {
                InetAddress address = InetAddress.getByName(prefixAndMask[0]);
                int prefixLength = Integer.parseInt(prefixAndMask[1]);
                builder.addRoute(address, prefixLength);
            } catch (NumberFormatException | UnknownHostException e) {
                Log.w(TAG, "Ill-formed route: " + routeArray[i]);
                continue;
            }
        }

        String[] allowedArray = restrictions.getStringArray(RESTRICTION_ALLOWED);
        if (allowedArray != null) {
            for (int i = 0; i < allowedArray.length; i++) {
                String allowedPackage = allowedArray[i];
                if (!TextUtils.isEmpty(allowedPackage)) {
                    try {
                        builder.addAllowedApplication(allowedPackage);
                    } catch(NameNotFoundException e) {
                        Log.w(TAG, "Allowed package not found: " + allowedPackage);
                        continue;
                    }
                }
            }
        }

        String[] disallowedArray = restrictions.getStringArray(RESTRICTION_DISALLOWED);
        if (disallowedArray != null) {
            for (int i = 0; i < disallowedArray.length; i++) {
                String disallowedPackage = disallowedArray[i];
                if (!TextUtils.isEmpty(disallowedPackage)) {
                    try {
                        builder.addDisallowedApplication(disallowedPackage);
                    } catch(NameNotFoundException e) {
                        Log.w(TAG, "Disallowed package not found: " + disallowedPackage);
                        continue;
                    }
                }
            }
        }

        builder.setMtu(MTU);
        builder.setBlocking(true);
        builder.setSession(TAG);

        mFd = builder.establish();
        if (mFd == null) {
            Log.e(TAG, "Unable to establish file descriptor for VPN connection");
            return;
        }
        Log.i(TAG, "Established, fd=" + mFd.getFd());

        mPingReflector = new PingReflector(mFd.getFileDescriptor(), MTU);
        mPingReflector.start();
    }

    private void stop() {
        if (mPingReflector != null) {
            mPingReflector.interrupt();
            mPingReflector = null;
        }
        try {
            if (mFd != null) {
                Log.i(TAG, "Closing filedescriptor");
                mFd.close();
            }
        } catch(IOException e) {
            Log.w(TAG, "Closing filedescriptor failed", e);
        } finally {
            mFd = null;
        }
    }
}
