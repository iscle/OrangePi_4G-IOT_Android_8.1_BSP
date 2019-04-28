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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Process;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * This class provides a simplified interface on top of other Bluetooth service
 * layer components; Also it handles some Opp application level variables. It's
 * a singleton got from BluetoothOppManager.getInstance(context);
 */
public class BluetoothOppManager {
    private static final String TAG = "BluetoothOppManager";
    private static final boolean V = Constants.VERBOSE;

    private static BluetoothOppManager INSTANCE;

    /** Used when obtaining a reference to the singleton instance. */
    private static Object INSTANCE_LOCK = new Object();

    private boolean mInitialized;

    private Context mContext;

    private BluetoothAdapter mAdapter;

    private String mMimeTypeOfSendingFile;

    private String mUriOfSendingFile;

    private String mMimeTypeOfSendingFiles;

    private ArrayList<Uri> mUrisOfSendingFiles;

    private boolean mIsHandoverInitiated;

    private static final String OPP_PREFERENCE_FILE = "OPPMGR";

    private static final String SENDING_FLAG = "SENDINGFLAG";

    private static final String MIME_TYPE = "MIMETYPE";

    private static final String FILE_URI = "FILE_URI";

    private static final String MIME_TYPE_MULTIPLE = "MIMETYPE_MULTIPLE";

    private static final String FILE_URIS = "FILE_URIS";

    private static final String MULTIPLE_FLAG = "MULTIPLE_FLAG";

    private static final String ARRAYLIST_ITEM_SEPERATOR = ";";

    private static final int ALLOWED_INSERT_SHARE_THREAD_NUMBER = 3;

    // used to judge if need continue sending process after received a
    // ENABLED_ACTION
    public boolean mSendingFlag;

    public boolean mMultipleFlag;

    private int mfileNumInBatch;

    private int mInsertShareThreadNum = 0;

    // A list of devices that may send files over OPP to this device
    // without user confirmation. Used for connection handover from forex NFC.
    private List<Pair<String,Long> > mWhitelist = new ArrayList<Pair<String, Long> >();

    // The time for which the whitelist entries remain valid.
    private static final int WHITELIST_DURATION_MS = 15000;

    /**
     * Get singleton instance.
     */
    public static BluetoothOppManager getInstance(Context context) {
        synchronized (INSTANCE_LOCK) {
            if (INSTANCE == null) {
                INSTANCE = new BluetoothOppManager();
            }
            INSTANCE.init(context);

            return INSTANCE;
        }
    }

    /**
     * init
     */
    private boolean init(Context context) {
        if (mInitialized)
            return true;
        mInitialized = true;

        mContext = context;

        mAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mAdapter == null) {
            if (V) Log.v(TAG, "BLUETOOTH_SERVICE is not started! ");
        }

        // Restore data from preference
        restoreApplicationData();

        return true;
    }


    private void cleanupWhitelist() {
        // Removes expired entries
        long curTime = SystemClock.elapsedRealtime();
        for (Iterator<Pair<String,Long>> iter = mWhitelist.iterator(); iter.hasNext(); ) {
            Pair<String,Long> entry = iter.next();
            if (curTime - entry.second > WHITELIST_DURATION_MS) {
                if (V) Log.v(TAG, "Cleaning out whitelist entry " + entry.first);
                iter.remove();
            }
        }
    }

    public synchronized void addToWhitelist(String address) {
        if (address == null) return;
        // Remove any existing entries
        for (Iterator<Pair<String,Long>> iter = mWhitelist.iterator(); iter.hasNext(); ) {
            Pair<String,Long> entry = iter.next();
            if (entry.first.equals(address)) {
                iter.remove();
            }
        }
        mWhitelist.add(new Pair<String, Long>(address, SystemClock.elapsedRealtime()));
    }

    public synchronized boolean isWhitelisted(String address) {
        cleanupWhitelist();
        for (Pair<String,Long> entry : mWhitelist) {
            if (entry.first.equals(address)) return true;
        }
        return false;
    }

    /**
     * Restore data from preference
     */
    private void restoreApplicationData() {
        SharedPreferences settings = mContext.getSharedPreferences(OPP_PREFERENCE_FILE, 0);

        // All member vars are not initialized till now
        mSendingFlag = settings.getBoolean(SENDING_FLAG, false);
        mMimeTypeOfSendingFile = settings.getString(MIME_TYPE, null);
        mUriOfSendingFile = settings.getString(FILE_URI, null);
        mMimeTypeOfSendingFiles = settings.getString(MIME_TYPE_MULTIPLE, null);
        mMultipleFlag = settings.getBoolean(MULTIPLE_FLAG, false);

        if (V) Log.v(TAG, "restoreApplicationData! " + mSendingFlag + mMultipleFlag
                    + mMimeTypeOfSendingFile + mUriOfSendingFile);

        String strUris = settings.getString(FILE_URIS, null);
        mUrisOfSendingFiles = new ArrayList<Uri>();
        if (strUris != null) {
            String[] splitUri = strUris.split(ARRAYLIST_ITEM_SEPERATOR);
            for (int i = 0; i < splitUri.length; i++) {
                mUrisOfSendingFiles.add(Uri.parse(splitUri[i]));
                if (V) Log.v(TAG, "Uri in batch:  " + Uri.parse(splitUri[i]));
            }
        }

        mContext.getSharedPreferences(OPP_PREFERENCE_FILE, 0).edit().clear().apply();
    }

    /**
     * Save application data to preference, need restore these data when service restart
     */
    private void storeApplicationData() {
        SharedPreferences.Editor editor = mContext.getSharedPreferences(OPP_PREFERENCE_FILE, 0)
                .edit();
        editor.putBoolean(SENDING_FLAG, mSendingFlag);
        editor.putBoolean(MULTIPLE_FLAG, mMultipleFlag);
        if (mMultipleFlag) {
            editor.putString(MIME_TYPE_MULTIPLE, mMimeTypeOfSendingFiles);
            StringBuilder sb = new StringBuilder();
            for (int i = 0, count = mUrisOfSendingFiles.size(); i < count; i++) {
                Uri uriContent = mUrisOfSendingFiles.get(i);
                sb.append(uriContent);
                sb.append(ARRAYLIST_ITEM_SEPERATOR);
            }
            String strUris = sb.toString();
            editor.putString(FILE_URIS, strUris);

            editor.remove(MIME_TYPE);
            editor.remove(FILE_URI);
        } else {
            editor.putString(MIME_TYPE, mMimeTypeOfSendingFile);
            editor.putString(FILE_URI, mUriOfSendingFile);

            editor.remove(MIME_TYPE_MULTIPLE);
            editor.remove(FILE_URIS);
        }
        editor.apply();
        if (V) Log.v(TAG, "Application data stored to SharedPreference! ");
    }

    public void saveSendingFileInfo(String mimeType, String uriString, boolean isHandover,
            boolean fromExternal) throws IllegalArgumentException {
        synchronized (BluetoothOppManager.this) {
            mMultipleFlag = false;
            mMimeTypeOfSendingFile = mimeType;
            mUriOfSendingFile = uriString;
            mIsHandoverInitiated = isHandover;
            Uri uri = Uri.parse(uriString);
            BluetoothOppUtility.putSendFileInfo(
                    uri, BluetoothOppSendFileInfo.generateFileInfo(
                                 mContext, uri, mimeType, fromExternal));
            storeApplicationData();
        }
    }

    public void saveSendingFileInfo(String mimeType, ArrayList<Uri> uris, boolean isHandover,
            boolean fromExternal) throws IllegalArgumentException {
        synchronized (BluetoothOppManager.this) {
            mMultipleFlag = true;
            mMimeTypeOfSendingFiles = mimeType;
            mUrisOfSendingFiles = uris;
            mIsHandoverInitiated = isHandover;
            for (Uri uri : uris) {
                BluetoothOppUtility.putSendFileInfo(
                        uri, BluetoothOppSendFileInfo.generateFileInfo(
                                     mContext, uri, mimeType, fromExternal));
            }
            storeApplicationData();
        }
    }

    /**
     * Get the current status of Bluetooth hardware.
     * @return true if Bluetooth enabled, false otherwise.
     */
    public boolean isEnabled() {
        if (mAdapter != null) {
            return mAdapter.isEnabled();
        } else {
            if (V) Log.v(TAG, "BLUETOOTH_SERVICE is not available! ");
            return false;
        }
    }

    /**
     * Enable Bluetooth hardware.
     */
    public void enableBluetooth() {
        if (mAdapter != null) {
            mAdapter.enable();
        }
    }

    /**
     * Disable Bluetooth hardware.
     */
    public void disableBluetooth() {
        if (mAdapter != null) {
            mAdapter.disable();
        }
    }

    /**
     * Get device name per bluetooth address.
     */
    public String getDeviceName(BluetoothDevice device) {
        String deviceName;

        deviceName = BluetoothOppPreference.getInstance(mContext).getName(device);

        if (deviceName == null && mAdapter != null) {
            deviceName = device.getName();
        }

        if (deviceName == null) {
            deviceName = mContext.getString(R.string.unknown_device);
        }

        return deviceName;
    }

    public int getBatchSize() {
        synchronized (BluetoothOppManager.this) {
            return mfileNumInBatch;
        }
    }

    /**
     * Fork a thread to insert share info to db.
     */
    public void startTransfer(BluetoothDevice device) {
        if (V) Log.v(TAG, "Active InsertShareThread number is : " + mInsertShareThreadNum);
        InsertShareInfoThread insertThread;
        synchronized (BluetoothOppManager.this) {
            if (mInsertShareThreadNum > ALLOWED_INSERT_SHARE_THREAD_NUMBER) {
                Log.e(TAG, "Too many shares user triggered concurrently!");

                // Notice user
                Intent in = new Intent(mContext, BluetoothOppBtErrorActivity.class);
                in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                in.putExtra("title", mContext.getString(R.string.enabling_progress_title));
                in.putExtra("content", mContext.getString(R.string.ErrorTooManyRequests));
                mContext.startActivity(in);

                return;
            }
            insertThread = new InsertShareInfoThread(device, mMultipleFlag, mMimeTypeOfSendingFile,
                    mUriOfSendingFile, mMimeTypeOfSendingFiles, mUrisOfSendingFiles,
                    mIsHandoverInitiated);
            if (mMultipleFlag) {
                mfileNumInBatch = mUrisOfSendingFiles.size();
            }
        }

        insertThread.start();
    }

    /**
     * Thread to insert share info to db. In multiple files (say 100 files)
     * share case, the inserting share info to db operation would be a time
     * consuming operation, so need a thread to handle it. This thread allows
     * multiple instances to support below case: User select multiple files to
     * share to one device (say device 1), and then right away share to second
     * device (device 2), we need insert all these share info to db.
     */
    private class InsertShareInfoThread extends Thread {
        private final BluetoothDevice mRemoteDevice;

        private final String mTypeOfSingleFile;

        private final String mUri;

        private final String mTypeOfMultipleFiles;

        private final ArrayList<Uri> mUris;

        private final boolean mIsMultiple;

        private final boolean mIsHandoverInitiated;

        public InsertShareInfoThread(BluetoothDevice device, boolean multiple,
                String typeOfSingleFile, String uri, String typeOfMultipleFiles,
                ArrayList<Uri> uris, boolean handoverInitiated) {
            super("Insert ShareInfo Thread");
            this.mRemoteDevice = device;
            this.mIsMultiple = multiple;
            this.mTypeOfSingleFile = typeOfSingleFile;
            this.mUri = uri;
            this.mTypeOfMultipleFiles = typeOfMultipleFiles;
            this.mUris = uris;
            this.mIsHandoverInitiated = handoverInitiated;

            synchronized (BluetoothOppManager.this) {
                mInsertShareThreadNum++;
            }

            if (V) Log.v(TAG, "Thread id is: " + this.getId());
        }

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            if (mRemoteDevice == null) {
                Log.e(TAG, "Target bt device is null!");
                return;
            }
            if (mIsMultiple) {
                insertMultipleShare();
            } else {
                insertSingleShare();
            }
            synchronized (BluetoothOppManager.this) {
                mInsertShareThreadNum--;
            }
        }

        /**
         * Insert multiple sending sessions to db, only used by Opp application.
         */
        private void insertMultipleShare() {
            int count = mUris.size();
            Long ts = System.currentTimeMillis();
            for (int i = 0; i < count; i++) {
                Uri fileUri = mUris.get(i);
                ContentResolver contentResolver = mContext.getContentResolver();
                String contentType = contentResolver.getType(fileUri);
                if (V) Log.v(TAG, "Got mimetype: " + contentType + "  Got uri: " + fileUri);
                if (TextUtils.isEmpty(contentType)) {
                    contentType = mTypeOfMultipleFiles;
                }

                ContentValues values = new ContentValues();
                values.put(BluetoothShare.URI, fileUri.toString());
                values.put(BluetoothShare.MIMETYPE, contentType);
                values.put(BluetoothShare.DESTINATION, mRemoteDevice.getAddress());
                values.put(BluetoothShare.TIMESTAMP, ts);
                if (mIsHandoverInitiated) {
                    values.put(BluetoothShare.USER_CONFIRMATION,
                            BluetoothShare.USER_CONFIRMATION_HANDOVER_CONFIRMED);
                }
                final Uri contentUri = mContext.getContentResolver().insert(
                        BluetoothShare.CONTENT_URI, values);
                if (V) Log.v(TAG, "Insert contentUri: " + contentUri + "  to device: "
                            + getDeviceName(mRemoteDevice));
            }
        }

         /**
         * Insert single sending session to db, only used by Opp application.
         */
        private void insertSingleShare() {
            ContentValues values = new ContentValues();
            values.put(BluetoothShare.URI, mUri);
            values.put(BluetoothShare.MIMETYPE, mTypeOfSingleFile);
            values.put(BluetoothShare.DESTINATION, mRemoteDevice.getAddress());
            if (mIsHandoverInitiated) {
                values.put(BluetoothShare.USER_CONFIRMATION,
                        BluetoothShare.USER_CONFIRMATION_HANDOVER_CONFIRMED);
            }
            final Uri contentUri = mContext.getContentResolver().insert(BluetoothShare.CONTENT_URI,
                    values);
            if (V) Log.v(TAG, "Insert contentUri: " + contentUri + "  to device: "
                                + getDeviceName(mRemoteDevice));
        }
    }

    void cleanUpSendingFileInfo() {
        synchronized (BluetoothOppManager.this) {
            if (V) Log.v(TAG, "cleanUpSendingFileInfo: mMultipleFlag = " + mMultipleFlag);
            if (!mMultipleFlag && (mUriOfSendingFile != null)) {
                Uri uri = Uri.parse(mUriOfSendingFile);
                BluetoothOppUtility.closeSendFileInfo(uri);
            } else if (mUrisOfSendingFiles != null) {
                for (Uri uri : mUrisOfSendingFiles) {
                    BluetoothOppUtility.closeSendFileInfo(uri);
                }
            }
        }
    }
}
