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

package com.android.bluetooth.opp;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;

public class BluetoothOppHandoverReceiver extends BroadcastReceiver {
    public static final String TAG ="BluetoothOppHandoverReceiver";
    private static final boolean D = Constants.DEBUG;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (action.equals(Constants.ACTION_HANDOVER_SEND) ||
               action.equals(Constants.ACTION_HANDOVER_SEND_MULTIPLE)) {
            final BluetoothDevice device =
                    (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (device == null) {
                if (D) Log.d(TAG, "No device attached to handover intent.");
                return;
            }

            final String mimeType = intent.getType();
            ArrayList<Uri> uris = new ArrayList<Uri>();
            if (action.equals(Constants.ACTION_HANDOVER_SEND)) {
                Uri stream = (Uri)intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (stream != null) uris.add(stream);
            } else if (action.equals(Constants.ACTION_HANDOVER_SEND_MULTIPLE)) {
                uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            }

            if (mimeType != null && uris != null && !uris.isEmpty()) {
                final Context finalContext = context;
                final ArrayList<Uri> finalUris = uris;
                Thread t = new Thread(new Runnable() {
                    public void run() {
                        BluetoothOppManager.getInstance(finalContext)
                                .saveSendingFileInfo(mimeType, finalUris, true /* isHandover */,
                                        true /* fromExternal */);
                        BluetoothOppManager.getInstance(finalContext).startTransfer(device);
                    }
                });
                t.start();
            } else {
                if (D) Log.d(TAG, "No mimeType or stream attached to handover request");
                return;
            }
        } else if (action.equals(Constants.ACTION_WHITELIST_DEVICE)) {
            BluetoothDevice device =
                    (BluetoothDevice)intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (D) Log.d(TAG, "Adding " + device + " to whitelist");
            if (device == null) return;
            BluetoothOppManager.getInstance(context).addToWhitelist(device.getAddress());
        } else if (action.equals(Constants.ACTION_STOP_HANDOVER)) {
            int id = intent.getIntExtra(Constants.EXTRA_BT_OPP_TRANSFER_ID, -1);
            if (id != -1) {
                Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + id);

                if (D) Log.d(TAG, "Stopping handover transfer with Uri " + contentUri);
                context.getContentResolver().delete(contentUri, null, null);
            }
        } else {
            if (D) Log.d(TAG, "Unknown action: " + action);
        }
    }

}
