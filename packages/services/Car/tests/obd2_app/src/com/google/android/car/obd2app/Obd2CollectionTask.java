/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.google.android.car.obd2app;

import android.annotation.Nullable;
import android.content.Context;
import android.os.Environment;
import android.os.SystemClock;
import android.util.JsonWriter;
import android.util.Log;
import com.android.car.obd2.Obd2Connection;
import com.android.car.obd2.Obd2FreezeFrameGenerator;
import com.android.car.obd2.Obd2LiveFrameGenerator;
import com.android.car.obd2.connections.BluetoothConnection;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Objects;
import java.util.TimerTask;

public class Obd2CollectionTask extends TimerTask {
    private final Obd2Connection mConnection;
    private final Obd2LiveFrameGenerator mLiveFrameGenerator;
    private final Obd2FreezeFrameGenerator mFreezeFrameGenerator;
    private final StatusNotification mStatusNotification;
    private final JsonWriter mJsonWriter;

    public static @Nullable Obd2CollectionTask create(
            Context context, StatusNotification statusNotification, String deviceAddress) {
        try {
            return new Obd2CollectionTask(
                    Objects.requireNonNull(context),
                    Objects.requireNonNull(statusNotification),
                    Objects.requireNonNull(deviceAddress));
        } catch (IOException | InterruptedException | IllegalStateException e) {
            Log.i(MainActivity.TAG, "Connection failed due to exception", e);
            return null;
        }
    }

    @Override
    public boolean cancel() {
        synchronized (mJsonWriter) {
            try {
                mJsonWriter.endArray();
                mJsonWriter.flush();
                mJsonWriter.close();
            } catch (IOException e) {
                Log.w(MainActivity.TAG, "IOException during close", e);
            }
            return super.cancel();
        }
    }

    @Override
    public void run() {
        if (!mConnection.isConnected()) {
            if (!mConnection.reconnect()) {
                mStatusNotification.notifyDisconnected();
                return;
            }
        }

        try {
            synchronized (mJsonWriter) {
                mLiveFrameGenerator.generate(mJsonWriter);
                mFreezeFrameGenerator.generate(mJsonWriter);
                mJsonWriter.flush();
            }
            mStatusNotification.notifyDataCapture();
        } catch (Exception e) {
            mStatusNotification.notifyException(e);
        }
    }

    Obd2CollectionTask(Context context, StatusNotification statusNotification, String deviceAddress)
            throws IOException, InterruptedException {
        if (!isExternalStorageWriteable())
            throw new IOException("Cannot write data to external storage");
        mStatusNotification = statusNotification;
        BluetoothConnection bluetoothConnection = new BluetoothConnection(deviceAddress);
        if (!bluetoothConnection.isConnected()) {
            statusNotification.notifyConnectionFailed();
            throw new IllegalStateException("Unable to connect to remote end.");
        }
        mConnection = new Obd2Connection(bluetoothConnection);
        mLiveFrameGenerator = new Obd2LiveFrameGenerator(mConnection);
        mFreezeFrameGenerator = new Obd2FreezeFrameGenerator(mConnection);
        mJsonWriter =
                new JsonWriter(
                        new OutputStreamWriter(
                                new FileOutputStream(getFilenameForStorage(context))));
        mJsonWriter.beginArray();
    }

    private static boolean isExternalStorageWriteable() {
        String state = Environment.getExternalStorageState();
        return (Environment.MEDIA_MOUNTED.equals(state));
    }

    private static File getFilenameForStorage(Context context) {
        String basename = String.format("obd2app.capture.%d", SystemClock.elapsedRealtimeNanos());
        return new File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), basename);
    }
}
