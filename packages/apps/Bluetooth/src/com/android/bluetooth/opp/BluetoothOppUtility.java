/*
 * Copyright (c) 2008-2009, Motorola, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Motorola, Inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.bluetooth.opp;

import com.android.bluetooth.R;
import com.google.android.collect.Lists;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.net.Uri;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class has some utilities for Opp application;
 */
public class BluetoothOppUtility {
    private static final String TAG = "BluetoothOppUtility";
    private static final boolean D = Constants.DEBUG;
    private static final boolean V = Constants.VERBOSE;

    private static final ConcurrentHashMap<Uri, BluetoothOppSendFileInfo> sSendFileMap
            = new ConcurrentHashMap<Uri, BluetoothOppSendFileInfo>();

    public static boolean isBluetoothShareUri(Uri uri) {
        return uri.toString().startsWith(BluetoothShare.CONTENT_URI.toString());
    }

    public static BluetoothOppTransferInfo queryRecord(Context context, Uri uri) {
        BluetoothOppTransferInfo info = new BluetoothOppTransferInfo();
        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                fillRecord(context, cursor, info);
            }
            cursor.close();
        } else {
            info = null;
            if (V) Log.v(TAG, "BluetoothOppManager Error: not got data from db for uri:" + uri);
        }
        return info;
    }

    public static void fillRecord(Context context, Cursor cursor, BluetoothOppTransferInfo info) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        info.mID = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare._ID));
        info.mStatus = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.STATUS));
        info.mDirection = cursor.getInt(cursor
                .getColumnIndexOrThrow(BluetoothShare.DIRECTION));
        info.mTotalBytes = cursor.getLong(cursor
                .getColumnIndexOrThrow(BluetoothShare.TOTAL_BYTES));
        info.mCurrentBytes = cursor.getLong(cursor
                .getColumnIndexOrThrow(BluetoothShare.CURRENT_BYTES));
        info.mTimeStamp = cursor.getLong(cursor
                .getColumnIndexOrThrow(BluetoothShare.TIMESTAMP));
        info.mDestAddr = cursor.getString(cursor
                .getColumnIndexOrThrow(BluetoothShare.DESTINATION));

        info.mFileName = cursor.getString(cursor
                .getColumnIndexOrThrow(BluetoothShare._DATA));
        if (info.mFileName == null) {
            info.mFileName = cursor.getString(cursor
                    .getColumnIndexOrThrow(BluetoothShare.FILENAME_HINT));
        }
        if (info.mFileName == null) {
            info.mFileName = context.getString(R.string.unknown_file);
        }

        info.mFileUri = cursor.getString(cursor.getColumnIndexOrThrow(BluetoothShare.URI));

        if (info.mFileUri != null) {
            Uri u = Uri.parse(info.mFileUri);
            info.mFileType = context.getContentResolver().getType(u);
        } else {
            Uri u = Uri.parse(info.mFileName);
            info.mFileType = context.getContentResolver().getType(u);
        }
        if (info.mFileType == null) {
            info.mFileType = cursor.getString(cursor
                    .getColumnIndexOrThrow(BluetoothShare.MIMETYPE));
        }

        BluetoothDevice remoteDevice = adapter.getRemoteDevice(info.mDestAddr);
        info.mDeviceName =
                BluetoothOppManager.getInstance(context).getDeviceName(remoteDevice);

        int confirmationType = cursor.getInt(
                cursor.getColumnIndexOrThrow(BluetoothShare.USER_CONFIRMATION));
        info.mHandoverInitiated =
                confirmationType == BluetoothShare.USER_CONFIRMATION_HANDOVER_CONFIRMED;

        if (V) Log.v(TAG, "Get data from db:" + info.mFileName + info.mFileType
                    + info.mDestAddr);
    }

    /**
     * Organize Array list for transfers in one batch
     */
    // This function is used when UI show batch transfer. Currently only show single transfer.
    public static ArrayList<String> queryTransfersInBatch(Context context, Long timeStamp) {
        ArrayList<String> uris = Lists.newArrayList();
        final String WHERE = BluetoothShare.TIMESTAMP + " == " + timeStamp;

        Cursor metadataCursor = context.getContentResolver().query(BluetoothShare.CONTENT_URI,
                new String[] {
                    BluetoothShare._DATA
                }, WHERE, null, BluetoothShare._ID);

        if (metadataCursor == null) {
            return null;
        }

        for (metadataCursor.moveToFirst(); !metadataCursor.isAfterLast(); metadataCursor
                .moveToNext()) {
            String fileName = metadataCursor.getString(0);
            Uri path = Uri.parse(fileName);
            // If there is no scheme, then it must be a file
            if (path.getScheme() == null) {
                path = Uri.fromFile(new File(fileName));
            }
            uris.add(path.toString());
            if (V) Log.d(TAG, "Uri in this batch: " + path.toString());
        }
        metadataCursor.close();
        return uris;
    }

    /**
     * Open the received file with appropriate application, if can not find
     * application to handle, display error dialog.
     */
    public static void openReceivedFile(Context context, String fileName, String mimetype,
            Long timeStamp, Uri uri) {
        if (fileName == null || mimetype == null) {
            Log.e(TAG, "ERROR: Para fileName ==null, or mimetype == null");
            return;
        }

        if (!isBluetoothShareUri(uri)) {
            Log.e(TAG, "Trying to open a file that wasn't transfered over Bluetooth");
            return;
        }

        File f = new File(fileName);
        if (!f.exists()) {
            Intent in = new Intent(context, BluetoothOppBtErrorActivity.class);
            in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            in.putExtra("title", context.getString(R.string.not_exist_file));
            in.putExtra("content", context.getString(R.string.not_exist_file_desc));
            context.startActivity(in);

            // Due to the file is not existing, delete related info in btopp db
            // to prevent this file from appearing in live folder
            if (V) Log.d(TAG, "This uri will be deleted: " + uri);
            context.getContentResolver().delete(uri, null, null);
            return;
        }

        Uri path = BluetoothOppFileProvider.getUriForFile(
                context, "com.android.bluetooth.opp.fileprovider", f);
        if (path == null) {
            Log.w(TAG, "Cannot get content URI for the shared file");
            return;
        }
        // If there is no scheme, then it must be a file
        if (path.getScheme() == null) {
            path = Uri.fromFile(new File(fileName));
        }

        if (isRecognizedFileType(context, path, mimetype)) {
            Intent activityIntent = new Intent(Intent.ACTION_VIEW);
            activityIntent.setDataAndTypeAndNormalize(path, mimetype);

            List<ResolveInfo> resInfoList = context.getPackageManager()
                .queryIntentActivities(activityIntent,
                        PackageManager.MATCH_DEFAULT_ONLY);

            activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activityIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            try {
                if (V) Log.d(TAG, "ACTION_VIEW intent sent out: " + path + " / " + mimetype);
                context.startActivity(activityIntent);
            } catch (ActivityNotFoundException ex) {
                if (V) Log.d(TAG, "no activity for handling ACTION_VIEW intent:  " + mimetype, ex);
            }
        } else {
            Intent in = new Intent(context, BluetoothOppBtErrorActivity.class);
            in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            in.putExtra("title", context.getString(R.string.unknown_file));
            in.putExtra("content", context.getString(R.string.unknown_file_desc));
            context.startActivity(in);
        }
    }

    /**
     * To judge if the file type supported (can be handled by some app) by phone
     * system.
     */
    public static boolean isRecognizedFileType(Context context, Uri fileUri, String mimetype) {
        boolean ret = true;

        if (D) Log.d(TAG, "RecognizedFileType() fileUri: " + fileUri + " mimetype: " + mimetype);

        Intent mimetypeIntent = new Intent(Intent.ACTION_VIEW);
        mimetypeIntent.setDataAndTypeAndNormalize(fileUri, mimetype);
        List<ResolveInfo> list = context.getPackageManager().queryIntentActivities(mimetypeIntent,
                PackageManager.MATCH_DEFAULT_ONLY);

        if (list.size() == 0) {
            if (D) Log.d(TAG, "NO application to handle MIME type " + mimetype);
            ret = false;
        }
        return ret;
    }

    /**
     * update visibility to Hidden
     */
    public static void updateVisibilityToHidden(Context context, Uri uri) {
        ContentValues updateValues = new ContentValues();
        updateValues.put(BluetoothShare.VISIBILITY, BluetoothShare.VISIBILITY_HIDDEN);
        context.getContentResolver().update(uri, updateValues, null, null);
    }

    /**
     * Helper function to build the progress text.
     */
    public static String formatProgressText(long totalBytes, long currentBytes) {
        if (totalBytes <= 0) {
            return "0%";
        }
        long progress = currentBytes * 100 / totalBytes;
        StringBuilder sb = new StringBuilder();
        sb.append(progress);
        sb.append('%');
        return sb.toString();
    }

    /**
     * Get status description according to status code.
     */
    public static String getStatusDescription(Context context, int statusCode, String deviceName) {
        String ret;
        if (statusCode == BluetoothShare.STATUS_PENDING) {
            ret = context.getString(R.string.status_pending);
        } else if (statusCode == BluetoothShare.STATUS_RUNNING) {
            ret = context.getString(R.string.status_running);
        } else if (statusCode == BluetoothShare.STATUS_SUCCESS) {
            ret = context.getString(R.string.status_success);
        } else if (statusCode == BluetoothShare.STATUS_NOT_ACCEPTABLE) {
            ret = context.getString(R.string.status_not_accept);
        } else if (statusCode == BluetoothShare.STATUS_FORBIDDEN) {
            ret = context.getString(R.string.status_forbidden);
        } else if (statusCode == BluetoothShare.STATUS_CANCELED) {
            ret = context.getString(R.string.status_canceled);
        } else if (statusCode == BluetoothShare.STATUS_FILE_ERROR) {
            ret = context.getString(R.string.status_file_error);
        } else if (statusCode == BluetoothShare.STATUS_ERROR_NO_SDCARD) {
            ret = context.getString(R.string.status_no_sd_card);
        } else if (statusCode == BluetoothShare.STATUS_CONNECTION_ERROR) {
            ret = context.getString(R.string.status_connection_error);
        } else if (statusCode == BluetoothShare.STATUS_ERROR_SDCARD_FULL) {
            ret = context.getString(R.string.bt_sm_2_1, deviceName);
        } else if ((statusCode == BluetoothShare.STATUS_BAD_REQUEST)
                || (statusCode == BluetoothShare.STATUS_LENGTH_REQUIRED)
                || (statusCode == BluetoothShare.STATUS_PRECONDITION_FAILED)
                || (statusCode == BluetoothShare.STATUS_UNHANDLED_OBEX_CODE)
                || (statusCode == BluetoothShare.STATUS_OBEX_DATA_ERROR)) {
            ret = context.getString(R.string.status_protocol_error);
        } else {
            ret = context.getString(R.string.status_unknown_error);
        }
        return ret;
    }

    /**
     * Retry the failed transfer: Will insert a new transfer session to db
     */
    public static void retryTransfer(Context context, BluetoothOppTransferInfo transInfo) {
        ContentValues values = new ContentValues();
        values.put(BluetoothShare.URI, transInfo.mFileUri);
        values.put(BluetoothShare.MIMETYPE, transInfo.mFileType);
        values.put(BluetoothShare.DESTINATION, transInfo.mDestAddr);

        final Uri contentUri = context.getContentResolver().insert(BluetoothShare.CONTENT_URI,
                values);
        if (V) Log.v(TAG, "Insert contentUri: " + contentUri + "  to device: " +
                transInfo.mDeviceName);
    }

    static void putSendFileInfo(Uri uri, BluetoothOppSendFileInfo sendFileInfo) {
        if (D) Log.d(TAG, "putSendFileInfo: uri=" + uri + " sendFileInfo=" + sendFileInfo);
        if (sendFileInfo == BluetoothOppSendFileInfo.SEND_FILE_INFO_ERROR) {
            Log.e(TAG, "putSendFileInfo: bad sendFileInfo, URI: " + uri);
        }
        sSendFileMap.put(uri, sendFileInfo);
    }

    static BluetoothOppSendFileInfo getSendFileInfo(Uri uri) {
        if (D) Log.d(TAG, "getSendFileInfo: uri=" + uri);
        BluetoothOppSendFileInfo info = sSendFileMap.get(uri);
        return (info != null) ? info : BluetoothOppSendFileInfo.SEND_FILE_INFO_ERROR;
    }

    static void closeSendFileInfo(Uri uri) {
        if (D) Log.d(TAG, "closeSendFileInfo: uri=" + uri);
        BluetoothOppSendFileInfo info = sSendFileMap.remove(uri);
        if (info != null && info.mInputStream != null) {
            try {
                info.mInputStream.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Checks if the URI is in Environment.getExternalStorageDirectory() as it
     * is the only directory that is possibly readable by both the sender and
     * the Bluetooth process.
     */
    static boolean isInExternalStorageDir(Uri uri) {
        if (!ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
            Log.e(TAG, "Not a file URI: " + uri);
            return false;
        }
        final File file = new File(uri.getCanonicalUri().getPath());
        return isSameOrSubDirectory(Environment.getExternalStorageDirectory(), file);
    }

    /**
     * Checks, whether the child directory is the same as, or a sub-directory of the base
     * directory. Neither base nor child should be null.
     */
    static boolean isSameOrSubDirectory(File base, File child) {
        try {
            base = base.getCanonicalFile();
            child = child.getCanonicalFile();
            File parentFile = child;
            while (parentFile != null) {
                if (base.equals(parentFile)) {
                    return true;
                }
                parentFile = parentFile.getParentFile();
            }
            return false;
        } catch (IOException ex) {
            Log.e(TAG, "Error while accessing file", ex);
            return false;
        }
    }

    static boolean isInValidStorageDir(Uri uri) {
        if (D) Log.d(TAG, "isInValidStorageDir uri = " + uri);
        if (!ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
            Log.e(TAG, "Not a file URI: " + uri);
            return false;
        }
        final File file = new File(uri.getCanonicalUri().getPath());
        if (D) Log.d(TAG, "file path = " + file.getAbsolutePath());
        boolean isValid = file.getAbsolutePath().startsWith("/storage");
        return isValid;
    }
}
