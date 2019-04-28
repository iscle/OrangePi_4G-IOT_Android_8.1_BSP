/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.stream;

import android.app.Application;
import android.content.Intent;
import android.util.Log;
import com.android.car.stream.media.MediaStreamProducer;
import com.android.car.stream.radio.RadioStreamProducer;
import com.android.car.stream.telecom.CurrentCallStreamProducer;
import com.android.car.stream.telecom.RecentCallStreamProducer;

import java.util.ArrayList;
import java.util.List;

/**
 * Base application for {@link StreamService}
 */
public class StreamApplication extends Application {
    private static final String TAG = "StreamApplication";
    private List<StreamProducer> streamProducers;

    @Override
    public void onCreate() {
        // TODO(victorchan): start and bind stream service, then pass in bound instance to
        // producers.
        startService(new Intent(this, StreamService.class));

        super.onCreate();
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "stream application started");
        }
        streamProducers = new ArrayList<>();
        streamProducers.add(new CurrentCallStreamProducer(this /* context */));
        streamProducers.add(new RecentCallStreamProducer(this /* context */));
        streamProducers.add(new MediaStreamProducer(this /* context */));
        streamProducers.add(new RadioStreamProducer(this /* context */));

        startProducers();
    }

    @Override
    public void onTerminate() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "StreamApplication terminated");
        }
        super.onTerminate();
        stopProducers();
    }

    private void startProducers() {
        for (int i = 0; i < streamProducers.size(); i++) {
            streamProducers.get(i).start();
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Stream producers started: "
                        + streamProducers.get(i).getClass().getName());
            }
        }
    }

    private void stopProducers() {
        for (int i = 0; i < streamProducers.size(); i++) {
            streamProducers.get(i).stop();
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Stream producers stopped: "
                        + streamProducers.get(i).getClass().getName());
            }
        }
    }
}
