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
 * limitations under the License
 */

package com.android.calllogbackup;

import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.Settings;
import android.telecom.PhoneAccountHandle;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Call log backup agent.
 */
public class CallLogBackupAgent extends BackupAgent {

    @VisibleForTesting
    static class CallLogBackupState {
        int version;
        SortedSet<Integer> callIds;
    }

    @VisibleForTesting
    static class Call {
        int id;
        long date;
        long duration;
        String number;
        String postDialDigits = "";
        String viaNumber = "";
        int type;
        int numberPresentation;
        String accountComponentName;
        String accountId;
        String accountAddress;
        Long dataUsage;
        int features;
        int addForAllUsers = 1;
        @Override
        public String toString() {
            if (isDebug()) {
                return  "[" + id + ", account: [" + accountComponentName + " : " + accountId +
                    "]," + number + ", " + date + "]";
            } else {
                return "[" + id + "]";
            }
        }
    }

    static class OEMData {
        String namespace;
        byte[] bytes;

        public OEMData(String namespace, byte[] bytes) {
            this.namespace = namespace;
            this.bytes = bytes == null ? ZERO_BYTE_ARRAY : bytes;
        }
    }

    private static final String TAG = "CallLogBackupAgent";

    private static final String USER_FULL_DATA_BACKUP_AWARE = "user_full_data_backup_aware";

    /** Current version of CallLogBackup. Used to track the backup format. */
    @VisibleForTesting
    static final int VERSION = 1005;
    /** Version indicating that there exists no previous backup entry. */
    @VisibleForTesting
    static final int VERSION_NO_PREVIOUS_STATE = 0;

    static final String NO_OEM_NAMESPACE = "no-oem-namespace";

    static final byte[] ZERO_BYTE_ARRAY = new byte[0];

    static final int END_OEM_DATA_MARKER = 0x60061E;


    private static final String[] CALL_LOG_PROJECTION = new String[] {
        CallLog.Calls._ID,
        CallLog.Calls.DATE,
        CallLog.Calls.DURATION,
        CallLog.Calls.NUMBER,
        CallLog.Calls.POST_DIAL_DIGITS,
        CallLog.Calls.VIA_NUMBER,
        CallLog.Calls.TYPE,
        CallLog.Calls.COUNTRY_ISO,
        CallLog.Calls.GEOCODED_LOCATION,
        CallLog.Calls.NUMBER_PRESENTATION,
        CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME,
        CallLog.Calls.PHONE_ACCOUNT_ID,
        CallLog.Calls.PHONE_ACCOUNT_ADDRESS,
        CallLog.Calls.DATA_USAGE,
        CallLog.Calls.FEATURES,
        CallLog.Calls.ADD_FOR_ALL_USERS,
    };

    /** ${inheritDoc} */
    @Override
    public void onBackup(ParcelFileDescriptor oldStateDescriptor, BackupDataOutput data,
            ParcelFileDescriptor newStateDescriptor) throws IOException {

        if (shouldPreventBackup(this)) {
            if (isDebug()) {
                Log.d(TAG, "Skipping onBackup");
            }
            return;
        }

        // Get the list of the previous calls IDs which were backed up.
        DataInputStream dataInput = new DataInputStream(
                new FileInputStream(oldStateDescriptor.getFileDescriptor()));
        final CallLogBackupState state;
        try {
            state = readState(dataInput);
        } finally {
            dataInput.close();
        }

        // Run the actual backup of data
        runBackup(state, data, getAllCallLogEntries());

        // Rewrite the backup state.
        DataOutputStream dataOutput = new DataOutputStream(new BufferedOutputStream(
                new FileOutputStream(newStateDescriptor.getFileDescriptor())));
        try {
            writeState(dataOutput, state);
        } finally {
            dataOutput.close();
        }
    }

    /** ${inheritDoc} */
    @Override
    public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState)
            throws IOException {

        if (isDebug()) {
            Log.d(TAG, "Performing Restore");
        }

        while (data.readNextHeader()) {
            Call call = readCallFromData(data);
            if (call != null) {
                writeCallToProvider(call);
                if (isDebug()) {
                    Log.d(TAG, "Restored call: " + call);
                }
            }
        }
    }

    @VisibleForTesting
    void runBackup(CallLogBackupState state, BackupDataOutput data, Iterable<Call> calls) {
        SortedSet<Integer> callsToRemove = new TreeSet<>(state.callIds);

        // Loop through all the call log entries to identify:
        // (1) new calls
        // (2) calls which have been deleted.
        for (Call call : calls) {
            if (!state.callIds.contains(call.id)) {

                if (isDebug()) {
                    Log.d(TAG, "Adding call to backup: " + call);
                }

                // This call new (not in our list from the last backup), lets back it up.
                addCallToBackup(data, call);
                state.callIds.add(call.id);
            } else {
                // This call still exists in the current call log so delete it from the
                // "callsToRemove" set since we want to keep it.
                callsToRemove.remove(call.id);
            }
        }

        // Remove calls which no longer exist in the set.
        for (Integer i : callsToRemove) {
            if (isDebug()) {
                Log.d(TAG, "Removing call from backup: " + i);
            }

            removeCallFromBackup(data, i);
            state.callIds.remove(i);
        }
    }

    private Iterable<Call> getAllCallLogEntries() {
        List<Call> calls = new LinkedList<>();

        // We use the API here instead of querying ContactsDatabaseHelper directly because
        // CallLogProvider has special locks in place for sychronizing when to read.  Using the APIs
        // gives us that for free.
        ContentResolver resolver = getContentResolver();
        Cursor cursor = resolver.query(
                CallLog.Calls.CONTENT_URI, CALL_LOG_PROJECTION, null, null, null);
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    Call call = readCallFromCursor(cursor);
                    if (call != null) {
                        calls.add(call);
                    }
                }
            } finally {
                cursor.close();
            }
        }

        return calls;
    }

    private void writeCallToProvider(Call call) {
        Long dataUsage = call.dataUsage == 0 ? null : call.dataUsage;

        PhoneAccountHandle handle = null;
        if (call.accountComponentName != null && call.accountId != null) {
            handle = new PhoneAccountHandle(
                    ComponentName.unflattenFromString(call.accountComponentName), call.accountId);
        }
        boolean addForAllUsers = call.addForAllUsers == 1;
        // We backup the calllog in the user running this backup agent, so write calls to this user.
        Calls.addCall(null /* CallerInfo */, this, call.number, call.postDialDigits, call.viaNumber,
                call.numberPresentation, call.type, call.features, handle, call.date,
                (int) call.duration, dataUsage, addForAllUsers, null, true /* is_read */);
    }

    @VisibleForTesting
    CallLogBackupState readState(DataInput dataInput) throws IOException {
        CallLogBackupState state = new CallLogBackupState();
        state.callIds = new TreeSet<>();

        try {
            // Read the version.
            state.version = dataInput.readInt();

            if (state.version >= 1) {
                // Read the size.
                int size = dataInput.readInt();

                // Read all of the call IDs.
                for (int i = 0; i < size; i++) {
                    state.callIds.add(dataInput.readInt());
                }
            }
        } catch (EOFException e) {
            state.version = VERSION_NO_PREVIOUS_STATE;
        }

        return state;
    }

    @VisibleForTesting
    void writeState(DataOutput dataOutput, CallLogBackupState state)
            throws IOException {
        // Write version first of all
        dataOutput.writeInt(VERSION);

        // [Version 1]
        // size + callIds
        dataOutput.writeInt(state.callIds.size());
        for (Integer i : state.callIds) {
            dataOutput.writeInt(i);
        }
    }

    @VisibleForTesting
    Call readCallFromData(BackupDataInput data) {
        final int callId;
        try {
            callId = Integer.parseInt(data.getKey());
        } catch (NumberFormatException e) {
            Log.e(TAG, "Unexpected key found in restore: " + data.getKey());
            return null;
        }

        try {
            byte [] byteArray = new byte[data.getDataSize()];
            data.readEntityData(byteArray, 0, byteArray.length);
            DataInputStream dataInput = new DataInputStream(new ByteArrayInputStream(byteArray));

            Call call = new Call();
            call.id = callId;

            int version = dataInput.readInt();
            if (version >= 1) {
                call.date = dataInput.readLong();
                call.duration = dataInput.readLong();
                call.number = readString(dataInput);
                call.type = dataInput.readInt();
                call.numberPresentation = dataInput.readInt();
                call.accountComponentName = readString(dataInput);
                call.accountId = readString(dataInput);
                call.accountAddress = readString(dataInput);
                call.dataUsage = dataInput.readLong();
                call.features = dataInput.readInt();
            }

            if (version >= 1002) {
                String namespace = dataInput.readUTF();
                int length = dataInput.readInt();
                byte[] buffer = new byte[length];
                dataInput.read(buffer);
                readOEMDataForCall(call, new OEMData(namespace, buffer));

                int marker = dataInput.readInt();
                if (marker != END_OEM_DATA_MARKER) {
                    Log.e(TAG, "Did not find END-OEM marker for call " + call.id);
                    // The marker does not match the expected value, ignore this call completely.
                    return null;
                }
            }

            if (version >= 1003) {
                call.addForAllUsers = dataInput.readInt();
            }

            if (version >= 1004) {
                call.postDialDigits = readString(dataInput);
            }

            if(version >= 1005) {
                call.viaNumber = readString(dataInput);
            }

            return call;
        } catch (IOException e) {
            Log.e(TAG, "Error reading call data for " + callId, e);
            return null;
        }
    }

    private Call readCallFromCursor(Cursor cursor) {
        Call call = new Call();
        call.id = cursor.getInt(cursor.getColumnIndex(CallLog.Calls._ID));
        call.date = cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DATE));
        call.duration = cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DURATION));
        call.number = cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER));
        call.postDialDigits = cursor.getString(
                cursor.getColumnIndex(CallLog.Calls.POST_DIAL_DIGITS));
        call.viaNumber = cursor.getString(cursor.getColumnIndex(CallLog.Calls.VIA_NUMBER));
        call.type = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.TYPE));
        call.numberPresentation =
                cursor.getInt(cursor.getColumnIndex(CallLog.Calls.NUMBER_PRESENTATION));
        call.accountComponentName =
                cursor.getString(cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME));
        call.accountId =
                cursor.getString(cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID));
        call.accountAddress =
                cursor.getString(cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ADDRESS));
        call.dataUsage = cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DATA_USAGE));
        call.features = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.FEATURES));
        call.addForAllUsers = cursor.getInt(cursor.getColumnIndex(Calls.ADD_FOR_ALL_USERS));
        return call;
    }

    private void addCallToBackup(BackupDataOutput output, Call call) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(baos);

        try {
            data.writeInt(VERSION);
            data.writeLong(call.date);
            data.writeLong(call.duration);
            writeString(data, call.number);
            data.writeInt(call.type);
            data.writeInt(call.numberPresentation);
            writeString(data, call.accountComponentName);
            writeString(data, call.accountId);
            writeString(data, call.accountAddress);
            data.writeLong(call.dataUsage == null ? 0 : call.dataUsage);
            data.writeInt(call.features);

            OEMData oemData = getOEMDataForCall(call);
            data.writeUTF(oemData.namespace);
            data.writeInt(oemData.bytes.length);
            data.write(oemData.bytes);
            data.writeInt(END_OEM_DATA_MARKER);

            data.writeInt(call.addForAllUsers);

            writeString(data, call.postDialDigits);

            writeString(data, call.viaNumber);

            data.flush();

            output.writeEntityHeader(Integer.toString(call.id), baos.size());
            output.writeEntityData(baos.toByteArray(), baos.size());

            if (isDebug()) {
                Log.d(TAG, "Wrote call to backup: " + call + " with byte array: " + baos);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to backup call: " + call, e);
        }
    }

    /**
     * Allows OEMs to provide proprietary data to backup along with the rest of the call log
     * data. Because there is no way to provide a Backup Transport implementation
     * nor peek into the data format of backup entries without system-level permissions, it is
     * not possible (at the time of this writing) to write CTS tests for this piece of code.
     * It is, therefore, important that if you alter this portion of code that you
     * test backup and restore of call log is working as expected; ideally this would be tested by
     * backing up and restoring between two different Android phone devices running M+.
     */
    private OEMData getOEMDataForCall(Call call) {
        return new OEMData(NO_OEM_NAMESPACE, ZERO_BYTE_ARRAY);

        // OEMs that want to add their own proprietary data to call log backup should replace the
        // code above with their own namespace and add any additional data they need.
        // Versioning and size-prefixing the data should be done here as needed.
        //
        // Example:

        /*
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(baos);

        String customData1 = "Generic OEM";
        int customData2 = 42;

        // Write a version for the data
        data.writeInt(OEM_DATA_VERSION);

        // Write the data and flush
        data.writeUTF(customData1);
        data.writeInt(customData2);
        data.flush();

        String oemNamespace = "com.oem.namespace";
        return new OEMData(oemNamespace, baos.toByteArray());
        */
    }

    /**
     * Allows OEMs to read their own proprietary data when doing a call log restore. It is important
     * that the implementation verify the namespace of the data matches their expected value before
     * attempting to read the data or else you may risk reading invalid data.
     *
     * See {@link #getOEMDataForCall} for information concerning proper testing of this code.
     */
    private void readOEMDataForCall(Call call, OEMData oemData) {
        // OEMs that want to read proprietary data from a call log restore should do so here.
        // Before reading from the data, an OEM should verify that the data matches their
        // expected namespace.
        //
        // Example:

        /*
        if ("com.oem.expected.namespace".equals(oemData.namespace)) {
            ByteArrayInputStream bais = new ByteArrayInputStream(oemData.bytes);
            DataInputStream data = new DataInputStream(bais);

            // Check against this version as we read data.
            int version = data.readInt();
            String customData1 = data.readUTF();
            int customData2 = data.readInt();
            // do something with data
        }
        */
    }


    private void writeString(DataOutputStream data, String str) throws IOException {
        if (str == null) {
            data.writeBoolean(false);
        } else {
            data.writeBoolean(true);
            data.writeUTF(str);
        }
    }

    private String readString(DataInputStream data) throws IOException {
        if (data.readBoolean()) {
            return data.readUTF();
        } else {
            return null;
        }
    }

    private void removeCallFromBackup(BackupDataOutput output, int callId) {
        try {
            output.writeEntityHeader(Integer.toString(callId), -1);
        } catch (IOException e) {
            Log.e(TAG, "Failed to remove call: " + callId, e);
        }
    }

    static boolean shouldPreventBackup(Context context) {
        // Check to see that the user is full-data aware before performing calllog backup.
        return Settings.Secure.getInt(
                context.getContentResolver(), USER_FULL_DATA_BACKUP_AWARE, 0) == 0;
    }

    private static boolean isDebug() {
        return Log.isLoggable(TAG, Log.DEBUG);
    }
}
