/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.bluetooth;

import android.app.ActivityManager;
import android.app.ActivityThread;
import android.app.AppOpsManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.os.Binder;
import android.os.Build;
import android.os.ParcelUuid;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @hide
 */

final public class Utils {
    private static final String TAG = "BluetoothUtils";
    private static final int MICROS_PER_UNIT = 625;

    static final int BD_ADDR_LEN = 6; // bytes
    static final int BD_UUID_LEN = 16; // bytes

    public static String getAddressStringFromByte(byte[] address) {
        if (address == null || address.length != BD_ADDR_LEN) {
            return null;
        }

        return String.format("%02X:%02X:%02X:%02X:%02X:%02X",
                address[0], address[1], address[2], address[3], address[4],
                address[5]);
    }

    public static byte[] getByteAddress(BluetoothDevice device) {
        return getBytesFromAddress(device.getAddress());
    }

    public static byte[] getBytesFromAddress(String address) {
        int i, j = 0;
        byte[] output = new byte[BD_ADDR_LEN];

        for (i = 0; i < address.length(); i++) {
            if (address.charAt(i) != ':') {
                output[j] = (byte) Integer.parseInt(address.substring(i, i + 2), BD_UUID_LEN);
                j++;
                i++;
            }
        }

        return output;
    }

    public static int byteArrayToInt(byte[] valueBuf) {
        return byteArrayToInt(valueBuf, 0);
    }

    public static short byteArrayToShort(byte[] valueBuf) {
        ByteBuffer converter = ByteBuffer.wrap(valueBuf);
        converter.order(ByteOrder.nativeOrder());
        return converter.getShort();
    }

    public static int byteArrayToInt(byte[] valueBuf, int offset) {
        ByteBuffer converter = ByteBuffer.wrap(valueBuf);
        converter.order(ByteOrder.nativeOrder());
        return converter.getInt(offset);
    }

    public static String byteArrayToString(byte[] valueBuf) {
        StringBuilder sb = new StringBuilder();
        for (int idx = 0; idx < valueBuf.length; idx++) {
            if (idx != 0) sb.append(" ");
            sb.append(String.format("%02x", valueBuf[idx]));
        }
        return sb.toString();
    }

    public static byte[] intToByteArray(int value) {
        ByteBuffer converter = ByteBuffer.allocate(4);
        converter.order(ByteOrder.nativeOrder());
        converter.putInt(value);
        return converter.array();
    }

    public static byte[] uuidToByteArray(ParcelUuid pUuid) {
        int length = BD_UUID_LEN;
        ByteBuffer converter = ByteBuffer.allocate(length);
        converter.order(ByteOrder.BIG_ENDIAN);
        long msb, lsb;
        UUID uuid = pUuid.getUuid();
        msb = uuid.getMostSignificantBits();
        lsb = uuid.getLeastSignificantBits();
        converter.putLong(msb);
        converter.putLong(8, lsb);
        return converter.array();
    }

    public static byte[] uuidsToByteArray(ParcelUuid[] uuids) {
        int length = uuids.length * BD_UUID_LEN;
        ByteBuffer converter = ByteBuffer.allocate(length);
        converter.order(ByteOrder.BIG_ENDIAN);
        UUID uuid;
        long msb, lsb;
        for (int i = 0; i < uuids.length; i++) {
            uuid = uuids[i].getUuid();
            msb = uuid.getMostSignificantBits();
            lsb = uuid.getLeastSignificantBits();
            converter.putLong(i * BD_UUID_LEN, msb);
            converter.putLong(i * BD_UUID_LEN + 8, lsb);
        }
        return converter.array();
    }

    public static ParcelUuid[] byteArrayToUuid(byte[] val) {
        int numUuids = val.length / BD_UUID_LEN;
        ParcelUuid[] puuids = new ParcelUuid[numUuids];
        UUID uuid;
        int offset = 0;

        ByteBuffer converter = ByteBuffer.wrap(val);
        converter.order(ByteOrder.BIG_ENDIAN);

        for (int i = 0; i < numUuids; i++) {
            puuids[i] = new ParcelUuid(new UUID(converter.getLong(offset),
                    converter.getLong(offset + 8)));
            offset += BD_UUID_LEN;
        }
        return puuids;
    }

    public static String debugGetAdapterStateString(int state) {
        switch (state) {
            case BluetoothAdapter.STATE_OFF:
                return "STATE_OFF";
            case BluetoothAdapter.STATE_ON:
                return "STATE_ON";
            case BluetoothAdapter.STATE_TURNING_ON:
                return "STATE_TURNING_ON";
            case BluetoothAdapter.STATE_TURNING_OFF:
                return "STATE_TURNING_OFF";
            default:
                return "UNKNOWN";
        }
    }

    public static String ellipsize(String s) {
        // Only ellipsize release builds
        if (!Build.TYPE.equals("user")) return s;
        if (s == null) return null;
        if (s.length() < 3) return s;
        return s.charAt(0) + "⋯" + s.charAt(s.length() - 1);
    }

    public static void copyStream(InputStream is, OutputStream os, int bufferSize)
            throws IOException {
        if (is != null && os != null) {
            byte[] buffer = new byte[bufferSize];
            int bytesRead = 0;
            while ((bytesRead = is.read(buffer)) >= 0) {
                os.write(buffer, 0, bytesRead);
            }
        }
    }

    public static void safeCloseStream(InputStream is) {
        if (is != null) {
            try {
                is.close();
            } catch (Throwable t) {
                Log.d(TAG, "Error closing stream", t);
            }
        }
    }

    public static void safeCloseStream(OutputStream os) {
        if (os != null) {
            try {
                os.close();
            } catch (Throwable t) {
                Log.d(TAG, "Error closing stream", t);
            }
        }
    }

    public static boolean checkCaller() {
        boolean ok;
        // Get the caller's user id then clear the calling identity
        // which will be restored in the finally clause.
        int callingUser = UserHandle.getCallingUserId();
        int callingUid = Binder.getCallingUid();
        long ident = Binder.clearCallingIdentity();

        try {
            // With calling identity cleared the current user is the foreground user.
            int foregroundUser = ActivityManager.getCurrentUser();
            ok = (foregroundUser == callingUser);
            if (!ok) {
                // Always allow SystemUI/System access.
                final int systemUiUid = ActivityThread.getPackageManager().getPackageUid(
                        "com.android.systemui", PackageManager.MATCH_SYSTEM_ONLY,
                        UserHandle.USER_SYSTEM);
                ok = (systemUiUid == callingUid) || (Process.SYSTEM_UID == callingUid);
            }
        } catch (Exception ex) {
            Log.e(TAG, "checkIfCallerIsSelfOrForegroundUser: Exception ex=" + ex);
            ok = false;
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
        return ok;
    }

    public static boolean checkCallerAllowManagedProfiles(Context mContext) {
        if (mContext == null) {
            return checkCaller();
        }
        boolean ok;
        // Get the caller's user id and if it's a managed profile, get it's parents
        // id, then clear the calling identity
        // which will be restored in the finally clause.
        int callingUser = UserHandle.getCallingUserId();
        int callingUid = Binder.getCallingUid();
        long ident = Binder.clearCallingIdentity();
        try {
            UserManager um = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
            UserInfo ui = um.getProfileParent(callingUser);
            int parentUser = (ui != null) ? ui.id : UserHandle.USER_NULL;
            // With calling identity cleared the current user is the foreground user.
            int foregroundUser = ActivityManager.getCurrentUser();
            ok = (foregroundUser == callingUser) ||
                    (foregroundUser == parentUser);
            if (!ok) {
                // Always allow SystemUI/System access.
                final int systemUiUid = ActivityThread.getPackageManager().getPackageUid(
                        "com.android.systemui", PackageManager.MATCH_SYSTEM_ONLY,
                        UserHandle.USER_SYSTEM);
                ok = (systemUiUid == callingUid) || (Process.SYSTEM_UID == callingUid);
            }
        } catch (Exception ex) {
            Log.e(TAG, "checkCallerAllowManagedProfiles: Exception ex=" + ex);
            ok = false;
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
        return ok;
    }

    /**
     * Enforce the context has android.Manifest.permission.BLUETOOTH_ADMIN permission. A
     * {@link SecurityException} would be thrown if neither the calling process or the application
     * does not have BLUETOOTH_ADMIN permission.
     *
     * @param context Context for the permission check.
     */
    public static void enforceAdminPermission(ContextWrapper context) {
        context.enforceCallingOrSelfPermission(android.Manifest.permission.BLUETOOTH_ADMIN,
                "Need BLUETOOTH_ADMIN permission");
    }

    /**
     * Checks that calling process has android.Manifest.permission.ACCESS_COARSE_LOCATION or
     * android.Manifest.permission.ACCESS_FINE_LOCATION and a corresponding app op is allowed
     */
    public static boolean checkCallerHasLocationPermission(Context context, AppOpsManager appOps,
            String callingPackage) {
        if (context.checkCallingOrSelfPermission(android.Manifest.permission.
                ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && isAppOppAllowed(appOps, AppOpsManager.OP_FINE_LOCATION, callingPackage)) {
            return true;
        }

        if (context.checkCallingOrSelfPermission(android.Manifest.permission.
                ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && isAppOppAllowed(appOps, AppOpsManager.OP_COARSE_LOCATION, callingPackage)) {
            return true;
        }
        // Enforce location permission for apps targeting M and later versions
        if (isMApp(context, callingPackage)) {
            // PEERS_MAC_ADDRESS is another way to get scan results without
            // requiring location permissions, so only throw an exception here
            // if PEERS_MAC_ADDRESS permission is missing as well
            if (!checkCallerHasPeersMacAddressPermission(context)) {
                throw new SecurityException("Need ACCESS_COARSE_LOCATION or "
                        + "ACCESS_FINE_LOCATION permission to get scan results");
            }
        } else {
            // Pre-M apps running in the foreground should continue getting scan results
            if (isForegroundApp(context, callingPackage)) {
                return true;
            }
            Log.e(TAG, "Permission denial: Need ACCESS_COARSE_LOCATION or ACCESS_FINE_LOCATION "
                    + "permission to get scan results");
        }
        return false;
    }

    /**
     * Returns true if the caller holds PEERS_MAC_ADDRESS.
     */
    public static boolean checkCallerHasPeersMacAddressPermission(Context context) {
        return context.checkCallingOrSelfPermission(
                android.Manifest.permission.PEERS_MAC_ADDRESS) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isLegacyForegroundApp(Context context, String pkgName) {
        return !isMApp(context, pkgName) && isForegroundApp(context, pkgName);
    }

    private static boolean isMApp(Context context, String pkgName) {
        try {
            return context.getPackageManager().getApplicationInfo(pkgName, 0)
                    .targetSdkVersion >= Build.VERSION_CODES.M;
        } catch (PackageManager.NameNotFoundException e) {
            // In case of exception, assume M app
        }
        return true;
    }

    /**
     * Return true if the specified package name is a foreground app.
     *
     * @param pkgName application package name.
     */
    private static boolean isForegroundApp(Context context, String pkgName) {
        ActivityManager am = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
        return !tasks.isEmpty() && pkgName.equals(tasks.get(0).topActivity.getPackageName());
    }

    private static boolean isAppOppAllowed(AppOpsManager appOps, int op, String callingPackage) {
        return appOps.noteOp(op, Binder.getCallingUid(), callingPackage)
                == AppOpsManager.MODE_ALLOWED;
    }

    /**
     * Converts {@code millisecond} to unit. Each unit is 0.625 millisecond.
     */
    public static int millsToUnit(int milliseconds) {
        return (int) (TimeUnit.MILLISECONDS.toMicros(milliseconds) / MICROS_PER_UNIT);
    }
}
