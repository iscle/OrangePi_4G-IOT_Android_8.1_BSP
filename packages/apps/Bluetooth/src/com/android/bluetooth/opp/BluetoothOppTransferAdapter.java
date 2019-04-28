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
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.text.format.DateUtils;
import android.text.format.DateFormat;
import android.text.format.Formatter;
import android.view.View;
import android.widget.ImageView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

import java.util.Date;

/**
 * This class is used to represent the data for the transfer history list box.
 * The only real work done by this class is to construct a custom view for the
 * line items.
 */
public class BluetoothOppTransferAdapter extends ResourceCursorAdapter {
    private Context mContext;

    public BluetoothOppTransferAdapter(Context context, int layout, Cursor c) {
        super(context, layout, c);
        mContext = context;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        Resources r = context.getResources();

        // Retrieve the icon for this transfer
        ImageView iv = (ImageView)view.findViewById(R.id.transfer_icon);
        int status = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.STATUS));
        int dir = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.DIRECTION));
        if (BluetoothShare.isStatusError(status)) {
            iv.setImageResource(android.R.drawable.stat_notify_error);
        } else {
            if (dir == BluetoothShare.DIRECTION_OUTBOUND) {
                iv.setImageResource(android.R.drawable.stat_sys_upload_done);
            } else {
                iv.setImageResource(android.R.drawable.stat_sys_download_done);
            }
        }

        // Set title
        TextView tv = (TextView)view.findViewById(R.id.transfer_title);
        String title = cursor.getString(
                cursor.getColumnIndexOrThrow(BluetoothShare.FILENAME_HINT));
        if (title == null) {
            title = mContext.getString(R.string.unknown_file);
        }
        tv.setText(title);

        // target device
        tv = (TextView)view.findViewById(R.id.targetdevice);
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        int destinationColumnId = cursor.getColumnIndexOrThrow(BluetoothShare.DESTINATION);
        BluetoothDevice remoteDevice = adapter.getRemoteDevice(cursor
                .getString(destinationColumnId));
        String deviceName = BluetoothOppManager.getInstance(context).getDeviceName(remoteDevice);
        tv.setText(deviceName);

        // complete text and complete date
        long totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(BluetoothShare.TOTAL_BYTES));
        if (BluetoothShare.isStatusCompleted(status)) {
            tv = (TextView)view.findViewById(R.id.complete_text);
            tv.setVisibility(View.VISIBLE);
            if (BluetoothShare.isStatusError(status)) {
                tv.setText(BluetoothOppUtility.getStatusDescription(mContext, status, deviceName));
            } else {
                String completeText;
                if (dir == BluetoothShare.DIRECTION_INBOUND) {
                    completeText = r.getString(R.string.download_success, Formatter.formatFileSize(
                            mContext, totalBytes));
                } else {
                    completeText = r.getString(R.string.upload_success, Formatter.formatFileSize(
                            mContext, totalBytes));
                }
                tv.setText(completeText);
            }

            int dateColumnId = cursor.getColumnIndexOrThrow(BluetoothShare.TIMESTAMP);
            long time = cursor.getLong(dateColumnId);
            Date d = new Date(time);
            CharSequence str = DateUtils.isToday(time) ? DateFormat.getTimeFormat(mContext).format(
                    d) : DateFormat.getDateFormat(mContext).format(d);
            tv = (TextView)view.findViewById(R.id.complete_date);
            tv.setVisibility(View.VISIBLE);
            tv.setText(str);
        }
    }
}
