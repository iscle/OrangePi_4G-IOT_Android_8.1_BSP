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

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.EventLog;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * This class stores information about a single sending file It will only be
 * used for outbound share.
 */
public class BluetoothOppSendFileInfo {
    private static final String TAG = "BluetoothOppSendFileInfo";

    private static final boolean D = Constants.DEBUG;


    /** Reusable SendFileInfo for error status. */
    static final BluetoothOppSendFileInfo SEND_FILE_INFO_ERROR = new BluetoothOppSendFileInfo(
            null, null, 0, null, BluetoothShare.STATUS_FILE_ERROR);

    static final BluetoothOppSendFileInfo SEND_FILE_INFO_NOT_ACCEPTABLE =
            new BluetoothOppSendFileInfo(null, null, 0, null, BluetoothShare.STATUS_NOT_ACCEPTABLE);

    /** readable media file name */
    public final String mFileName;

    /** media file input stream */
    public final FileInputStream mInputStream;

    /** vCard string data */
    public final String mData;

    public final int mStatus;

    public final String mMimetype;

    public final long mLength;

    /** for media file */
    public BluetoothOppSendFileInfo(String fileName, String type, long length,
            FileInputStream inputStream, int status) {
        mFileName = fileName;
        mMimetype = type;
        mLength = length;
        mInputStream = inputStream;
        mStatus = status;
        mData = null;
    }

    /** for vCard, or later for vCal, vNote. Not used currently */
    public BluetoothOppSendFileInfo(String data, String type, long length, int status) {
        mFileName = null;
        mInputStream = null;
        mData = data;
        mMimetype = type;
        mLength = length;
        mStatus = status;
    }

    public static BluetoothOppSendFileInfo generateFileInfo(
            Context context, Uri uri, String type, boolean fromExternal) {
        if (D) Log.i(TAG, "generateFileInfo ++");
        ContentResolver contentResolver = context.getContentResolver();
        String scheme = uri.getScheme();
        String fileName = null;
        String contentType;
        long length = 0;
        // Support all Uri with "content" scheme
        // This will allow more 3rd party applications to share files via
        // bluetooth
        if ("content".equals(scheme)) {
            contentType = contentResolver.getType(uri);
            Cursor metadataCursor;
            try {
                metadataCursor = contentResolver.query(uri, new String[] {
                        OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE
                }, null, null, null);
            } catch (SQLiteException e) {
                // some content providers don't support the DISPLAY_NAME or SIZE columns
                metadataCursor = null;
                e.printStackTrace();
            } catch (SecurityException e) {
                Log.e(TAG, "generateFileInfo: Permission error, could not access URI: " + uri);
                e.printStackTrace();
                return SEND_FILE_INFO_ERROR;
            } catch (Exception e) {
                Log.e(TAG, "generateFileInfo: Content error, could not access URI: " + uri);
                e.printStackTrace();
                return SEND_FILE_INFO_NOT_ACCEPTABLE;
            }

            if (metadataCursor != null) {
                try {
                    if (metadataCursor.moveToFirst()) {
                        fileName = metadataCursor.getString(
                                metadataCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                        length = metadataCursor.getLong(
                                metadataCursor.getColumnIndex(OpenableColumns.SIZE));
                        if (D) Log.d(TAG, "fileName = " + fileName + " length = " + length);
                    }
                } finally {
                    metadataCursor.close();
                }
            }
            if (fileName == null) {
                // use last segment of URI if DISPLAY_NAME query fails
                String authority = uri.getAuthority();
                if(!ContactsContract.AUTHORITY.equals(authority)) {
                    fileName = getFileNameByUri(context, uri);
                } else {
                    fileName = uri.getLastPathSegment();
                }
                if (D) Log.d(TAG, "fileName = " + fileName + " authority = " + authority);
            }
        } else if ("file".equals(scheme)) {
            if (uri.getPath() == null) {
                Log.e(TAG, "Invalid URI path: " + uri);
                return SEND_FILE_INFO_ERROR;
            }
            /*
             * M : Modify AOSP behavior, we don't block sdcard file to share by BT.
             * It's really confuse when you getting send fail message for no reason.
             * TO DO : Maybe enable this behavior when Google enhance UX.
             *
            if (fromExternal && !BluetoothOppUtility.isInExternalStorageDir(uri)) {
                EventLog.writeEvent(0x534e4554, "35310991", -1, uri.getPath());
                Log.e(TAG,
                "File based URI not in Environment.getExternalStorageDirectory() is not allowed.");
                return SEND_FILE_INFO_ERROR;
            }
            */
            if (fromExternal && !BluetoothOppUtility.isInValidStorageDir(uri)) {
                EventLog.writeEvent(0x534e4554, "35310991", -1, uri.getPath());
                Log.e(TAG,
                "File based URI not in Environment.getExternalStorageDirectory() is not allowed.");
                return SEND_FILE_INFO_ERROR;
            }
            fileName = uri.getLastPathSegment();
            contentType = type;
            File f = new File(uri.getPath());
            length = f.length();
        } else {
            // currently don't accept other scheme
            return SEND_FILE_INFO_ERROR;
        }
        FileInputStream is = null;
        if (scheme.equals("content")) {
            try {
                // We've found that content providers don't always have the
                // right size in _OpenableColumns.SIZE
                // As a second source of getting the correct file length,
                // get a file descriptor and get the stat length
                AssetFileDescriptor fd = contentResolver.openAssetFileDescriptor(uri, "r");
                long statLength = fd.getLength();
                if (length != statLength && statLength > 0) {
                    Log.e(TAG, "Content provider length is wrong (" + Long.toString(length) +
                            "), using stat length (" + Long.toString(statLength) + ")");
                    length = statLength;
                }

                try {
                    // This creates an auto-closing input-stream, so
                    // the file descriptor will be closed whenever the InputStream
                    // is closed.
                    is = fd.createInputStream();

                    // If the database doesn't contain the file size, get the size
                    // by reading through the entire stream
                    if (length == 0) {
                        length = getStreamSize(is);
                        Log.w(TAG, "File length not provided. Length from stream = "
                                   + length);
                        // Reset the stream
                        fd = contentResolver.openAssetFileDescriptor(uri, "r");
                        is = fd.createInputStream();
                    }
                } catch (IOException e) {
                    try {
                        fd.close();
                    } catch (IOException e2) {
                        // Ignore
                    }
                }
            } catch (FileNotFoundException e) {
                // Ignore
            }
        }

        if (is == null) {
            try {
                is = (FileInputStream) contentResolver.openInputStream(uri);

                // If the database doesn't contain the file size, get the size
                // by reading through the entire stream
                if (length == 0) {
                    length = getStreamSize(is);
                    // Reset the stream
                    is = (FileInputStream) contentResolver.openInputStream(uri);
                }
            } catch (FileNotFoundException e) {
                return SEND_FILE_INFO_ERROR;
            } catch (IOException e) {
                return SEND_FILE_INFO_ERROR;
            }
        }

        if (length == 0) {
            Log.e(TAG, "Could not determine size of file");
            return SEND_FILE_INFO_ERROR;
        } else if (length > 0xffffffffL) {
            String msg = "Files bigger than 4GB can't be transferred";
            Log.e(TAG, msg);
            throw new IllegalArgumentException(msg);
        }

        return new BluetoothOppSendFileInfo(fileName, contentType, length, is, 0);
    }

    private static long getStreamSize(FileInputStream is) throws IOException {
        long length = 0;
        byte unused[] = new byte[4096];
        int bytesRead = is.read(unused, 0, 4096);
        while (bytesRead != -1) {
            length += bytesRead;
            bytesRead = is.read(unused, 0, 4096);
        }
        return length;
    }

    public static String getFileNameByUri(Context context, Uri uri) {
        String fileName="unknown"; //default fileName
        Uri filePathUri = uri;
        String[] filePathColumn = {MediaStore.Images.Media.DATA};
        if (uri.getScheme().toString().equalsIgnoreCase("content")) {
            Cursor cursor = context.getContentResolver().query(uri, filePathColumn, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    int column_index = cursor.getColumnIndexOrThrow(filePathColumn[0]);
                    filePathUri = Uri.parse(cursor.getString(column_index));
                    fileName = filePathUri.getLastPathSegment().toString();
                }
            } catch (Exception e) {
                // TODO: handle exception
            } finally {
                if(cursor != null) {
                    cursor.close();
                }
            }
        } else {
            fileName = fileName+"_"+filePathUri.getLastPathSegment();
        }

        Log.i(TAG, "getFileNameByUri file name = " + fileName);

        return fileName;
    }
}
