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
package com.android.bluetooth.pbapclient;

import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.os.RemoteException;
import android.provider.CallLog;
import android.util.Log;
import android.util.Pair;

import com.android.vcard.VCardEntry;
import com.android.vcard.VCardEntry.PhoneData;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CallLogPullRequest extends PullRequest {
    private static boolean DBG = true;
    private static boolean VDBG = false;
    private static String TAG = "PbapCallLogPullRequest";
    private static final String TIMESTAMP_PROPERTY = "X-IRMC-CALL-DATETIME";
    private static final String TIMESTAMP_FORMAT = "yyyyMMdd'T'HHmmss";

    private Context mContext;

    public CallLogPullRequest(Context context, String path) {
        mContext = context;
        this.path = path;
    }

    @Override
    public void onPullComplete() {
        if (mEntries == null) {
            Log.e(TAG, "onPullComplete entries is null.");
            return;
        }

        if (DBG) {
            Log.d(TAG, "onPullComplete");
            if (VDBG) {
                Log.d(TAG, " with " + mEntries.size() + " count.");
            }
        }
        int type;
        try {
            if (path.equals(PbapClientConnectionHandler.ICH_PATH)) {
                type = CallLog.Calls.INCOMING_TYPE;
            } else if (path.equals(PbapClientConnectionHandler.OCH_PATH)) {
                type = CallLog.Calls.OUTGOING_TYPE;
            } else if (path.equals(PbapClientConnectionHandler.MCH_PATH)) {
                type = CallLog.Calls.MISSED_TYPE;
            } else {
                Log.w(TAG, "Unknown path type:" + path);
                return;
            }

            ArrayList<ContentProviderOperation> ops = new ArrayList<>();
            for (VCardEntry vcard : mEntries) {
                ContentValues values = new ContentValues();

                values.put(CallLog.Calls.TYPE, type);

                List<PhoneData> phones = vcard.getPhoneList();
                if (phones == null || phones.get(0).getNumber().equals(";")) {
                    values.put(CallLog.Calls.NUMBER, "");
                } else {
                    values.put(CallLog.Calls.NUMBER, phones.get(0).getNumber());
                }

                List<Pair<String, String>> irmc = vcard.getUnknownXData();
                SimpleDateFormat parser = new SimpleDateFormat(TIMESTAMP_FORMAT);
                if (irmc != null) {
                    for (Pair<String, String> pair : irmc) {
                        if (pair.first.startsWith(TIMESTAMP_PROPERTY)) {
                            try {
                                values.put(CallLog.Calls.DATE, parser.parse(pair.second).getTime());
                            } catch (ParseException e) {
                                Log.d(TAG, "Failed to parse date ");
                                if (VDBG) {
                                    Log.d(TAG, pair.second);
                                }
                            }
                        }
                    }
                }

                ops.add(ContentProviderOperation.newInsert(CallLog.Calls.CONTENT_URI)
                        .withValues(values).withYieldAllowed(true).build());
            }
            mContext.getContentResolver().applyBatch(CallLog.AUTHORITY, ops);
            Log.d(TAG, "Updated call logs.");
        } catch (RemoteException | OperationApplicationException e) {
            Log.d(TAG, "Failed to update call log for path=" + path, e);
        } finally {
            synchronized (this) {
                this.notify();
            }
        }
    }
}
