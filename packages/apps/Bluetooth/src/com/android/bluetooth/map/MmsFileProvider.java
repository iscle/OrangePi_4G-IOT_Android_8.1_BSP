/*
* Copyright (C) 2014 Samsung System LSI
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
package com.android.bluetooth.map;

import android.annotation.TargetApi;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.Telephony.Mms;
import android.util.Log;

import com.google.android.mms.MmsException;
import com.google.android.mms.pdu.GenericPdu;
import com.google.android.mms.pdu.PduComposer;
import com.google.android.mms.pdu.PduPersister;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Provider to let the MMS subsystem read data from it own database from another process.
 * Workaround for missing access to sendStoredMessage().
 */
@TargetApi(19)
public class MmsFileProvider extends ContentProvider {
    static final String TAG = "BluetoothMmsFileProvider";
    private PipeWriter mPipeWriter = new PipeWriter();

    /*package*/
    static final Uri CONTENT_URI = Uri.parse("content://com.android.bluetooth.map.MmsFileProvider");

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        // Don't support queries.
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // Don't support inserts.
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // Don't support deletes.
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // Don't support updates.
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        // For this sample, assume all files have no type.
        return null;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String fileMode) throws FileNotFoundException {
        String idStr = uri.getLastPathSegment();
        if(idStr == null) {
            throw new FileNotFoundException("Unable to extract message handle from: " + uri);
        }
        try {
            long id = Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            Log.w(TAG,e);
            throw new FileNotFoundException("Unable to extract message handle from: " + uri);
        }
        Uri messageUri = Mms.CONTENT_URI.buildUpon().appendEncodedPath(idStr).build();

        return openPipeHelper (messageUri, null, null, null, mPipeWriter);
    }


    public class PipeWriter implements PipeDataWriter<Cursor> {
        /**
         * Generate a message based on the cursor, and write the encoded data to the stream.
         */

        public void writeDataToPipe(ParcelFileDescriptor output, Uri uri, String mimeType,
                Bundle opts, Cursor c) {
            if (BluetoothMapService.DEBUG) Log.d(TAG, "writeDataToPipe(): uri=" + uri.toString() +
                    " - getLastPathSegment() = " + uri.getLastPathSegment());

            FileOutputStream fout = null;
            GenericPdu pdu = null;
            PduPersister pduPersister = null;

            try {
                fout = new FileOutputStream(output.getFileDescriptor());
                pduPersister = PduPersister.getPduPersister(getContext());
                pdu = pduPersister.load(uri);
                byte[] bytes = (new PduComposer(getContext(), pdu)).make();
                fout.write(bytes);

            } catch (IOException e) {
                Log.w(TAG, e);
                /* TODO: How to signal the error to the calling entity? Had expected writeDataToPipe
                 *       to throw IOException?
                 */
            } catch (MmsException e) {
                Log.w(TAG, e);
                /* TODO: How to signal the error to the calling entity? Had expected writeDataToPipe
                 *       to throw IOException?
                 */
            } finally {
                if(pduPersister != null) pduPersister.release();
                try {
                    fout.flush();
                } catch (IOException e) {
                    Log.w(TAG, "IOException: ", e);
                }
                try {
                    fout.close();
                } catch (IOException e) {
                    Log.w(TAG, "IOException: ", e);
                }
            }
        }
    }


}
