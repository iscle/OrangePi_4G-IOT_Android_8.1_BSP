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
 * limitations under the License
 */

package com.android.phone.testapps.embmsfrontend;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.telephony.MbmsStreamingSession;
import android.telephony.mbms.MbmsStreamingSessionCallback;
import android.telephony.mbms.StreamingService;
import android.telephony.mbms.StreamingServiceInfo;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

public class EmbmsTestStreamingApp extends Activity {
    private MbmsStreamingSessionCallback mStreamingListener = new MbmsStreamingSessionCallback() {
        @Override
        public void onStreamingServicesUpdated(List<StreamingServiceInfo> services) {
            EmbmsTestStreamingApp.this.runOnUiThread(() ->
                    Toast.makeText(EmbmsTestStreamingApp.this,
                            "Got services length " + services.size(),
                            Toast.LENGTH_SHORT).show());
            updateStreamingServicesList(services);
        }

        @Override
        public void onMiddlewareReady() {
            runOnUiThread(() -> Toast.makeText(EmbmsTestStreamingApp.this, "Successfully bound",
                    Toast.LENGTH_SHORT).show());
        }
    };

    private final class StreamingServiceInfoAdapter
            extends ArrayAdapter<StreamingServiceInfo> {
        public StreamingServiceInfoAdapter(Context context, int resource) {
            super(context, resource);
        }

        private String getName(StreamingServiceInfo info) {
            Locale locale = Locale.getDefault();
            try {
                return info.getNameForLocale(locale).toString();
            } catch (NoSuchElementException e) {
                locale = info.getLocales().iterator().next();
                return info.getNameForLocale(locale).toString();
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            StreamingServiceInfo info = getItem(position);
            TextView result = new TextView(EmbmsTestStreamingApp.this);
            result.setText(getName(info));
            return result;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            StreamingServiceInfo info = getItem(position);
            TextView result = new TextView(EmbmsTestStreamingApp.this);
            String text = "name="
                    + getName(info)
                    + ", "
                    + "serviceId="
                    + info.getServiceId();
            result.setText(text);
            return result;
        }

        public void update(List<StreamingServiceInfo> services) {
            clear();
            addAll(services);
        }
    }

    private final class TrackedStreamAdapter extends ArrayAdapter<String> {
        public TrackedStreamAdapter(Context context) {
            super(context, android.R.layout.simple_spinner_item);
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            String serviceId = getItem(position);
            StreamingServiceTracker tracker = mStreamingServiceTrackerById.get(serviceId);
            TextView result = new TextView(EmbmsTestStreamingApp.this);
            result.setText(tracker == null ? "" : tracker.toString());
            return result;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            String serviceId = getItem(position);
            StreamingServiceTracker tracker = mStreamingServiceTrackerById.get(serviceId);
            TextView result = new TextView(EmbmsTestStreamingApp.this);
            result.setText(tracker.toString());
            return result;
        }
    }

    private MbmsStreamingSession mStreamingManager = null;

    private Handler mHandler;
    private HandlerThread mHandlerThread;

    private TrackedStreamAdapter mTrackedStreamingServiceAdapter;
    private Spinner mStreamSelector;
    private StreamingServiceInfoAdapter mStreamingServicesDisplayAdapter;
    private final Map<String, StreamingServiceTracker> mStreamingServiceTrackerById =
            new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mHandlerThread = new HandlerThread("EmbmsSampleFrontendWorker");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        mStreamingServicesDisplayAdapter =
                new StreamingServiceInfoAdapter(this, android.R.layout.simple_spinner_item);
        mTrackedStreamingServiceAdapter = new TrackedStreamAdapter(this);

        Button bindButton = (Button) findViewById(R.id.bind_button);
        bindButton.setOnClickListener((view) -> {
            mStreamingManager = MbmsStreamingSession.create(
                    EmbmsTestStreamingApp.this, mStreamingListener, mHandler);
        });

        Button getStreamingServicesButton = (Button)
                findViewById(R.id.get_streaming_services_button);
        getStreamingServicesButton.setOnClickListener((view) -> {
            if (mStreamingManager == null) {
                Toast.makeText(EmbmsTestStreamingApp.this,
                        "No streaming service bound", Toast.LENGTH_SHORT).show();
                return;
            }
            mStreamingManager.requestUpdateStreamingServices(Collections.singletonList("Class1"));
        });

        final Spinner serviceSelector = (Spinner) findViewById(R.id.available_streaming_services);
        mStreamingServicesDisplayAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        serviceSelector.setAdapter(mStreamingServicesDisplayAdapter);

        mStreamSelector = (Spinner) findViewById(R.id.curr_streams);
        mTrackedStreamingServiceAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        mStreamSelector.setAdapter(mTrackedStreamingServiceAdapter);
        mStreamSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String serviceId = (String) mStreamSelector.getItemAtPosition(position);
                StreamingServiceTracker tracker = mStreamingServiceTrackerById.get(serviceId);

                setStreamStateDisplay(String.valueOf(tracker.getState()));
                setUriDisplay(tracker.getUri());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                clearStateAndUriDisplay();
            }
        });

        Button startStreamingButton = (Button) findViewById(R.id.start_streaming_button);
        startStreamingButton.setOnClickListener((view) -> {
            if (mStreamingManager == null) {
                Toast.makeText(EmbmsTestStreamingApp.this,
                        "No streaming service bound", Toast.LENGTH_SHORT).show();
                return;
            }
            StreamingServiceInfo serviceInfo =
                    (StreamingServiceInfo) serviceSelector.getSelectedItem();
            if (serviceInfo == null) {
                Toast.makeText(EmbmsTestStreamingApp.this,
                        "No streaming service selected", Toast.LENGTH_SHORT).show();
                return;
            }

            StreamingServiceTracker tracker = new StreamingServiceTracker(this, serviceInfo);
            if (tracker.startStreaming(mStreamingManager)) {
                mStreamingServiceTrackerById.put(serviceInfo.getServiceId(), tracker);
                mTrackedStreamingServiceAdapter.add(serviceInfo.getServiceId());
            }
        });

        Button stopStreamingButton = (Button) findViewById(R.id.stop_streaming_button);
        stopStreamingButton.setOnClickListener((view) -> {
            if (getSelectedTrackedStream() == null) {
                Toast.makeText(EmbmsTestStreamingApp.this,
                        "No streams selected", Toast.LENGTH_SHORT).show();
                return;
            }
            StreamingServiceTracker stream = getSelectedTrackedStream();
            stream.stopStreaming();
        });

        Button disposeManagerButton = (Button) findViewById(R.id.dispose_manager_button);
        disposeManagerButton.setOnClickListener((view) -> {
            clearStateAndUriDisplay();
            mTrackedStreamingServiceAdapter.clear();
            mStreamingServicesDisplayAdapter.update(Collections.emptyList());
            mStreamingServiceTrackerById.clear();
            mStreamingManager.close();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandlerThread.quit();
    }

    private void updateStreamingServicesList(List<StreamingServiceInfo> services) {
        runOnUiThread(() -> mStreamingServicesDisplayAdapter.update(services));
    }

    private StreamingServiceTracker getSelectedTrackedStream() {
        String serviceId = (String) mStreamSelector.getSelectedItem();
        return mStreamingServiceTrackerById.get(serviceId);
    }

    private void setUriDisplay(Uri uri) {
        runOnUiThread(() -> {
            TextView uriField = (TextView) findViewById(R.id.curr_streaming_uri);
            uriField.setText(uri.toSafeString());
        });
    }

    private void setStreamStateDisplay(String stateString) {
        runOnUiThread(() -> {
            TextView uriField = (TextView) findViewById(R.id.stream_state);
            uriField.setText(stateString);
        });
    }

    private void setStreamMethodDisplay(int method) {
        runOnUiThread(() -> {
            String methodString = "UNKNOWN METHOD";
            switch (method) {
                case StreamingService.BROADCAST_METHOD: {
                    methodString = "BROADCAST";
                    break;
                }
                case StreamingService.UNICAST_METHOD: {
                    methodString = "UNICAST";
                    break;
                }
            }
            TextView methodField = (TextView) findViewById(R.id.curr_streaming_method);
            methodField.setText(methodString);
        });
    }

    private void clearStateAndUriDisplay() {
        setUriDisplay(Uri.EMPTY);
        setStreamStateDisplay("");
        setStreamMethodDisplay(StreamingService.UNICAST_METHOD);
    }

    public void updateUri() {
        Uri uri = getSelectedTrackedStream() == null ?
            Uri.EMPTY : getSelectedTrackedStream().getUri();
        setUriDisplay(uri);
    }

    public void updateStreamingState() {
        String stateString = getSelectedTrackedStream() == null ?
            "" : String.valueOf(getSelectedTrackedStream().getState());
        setStreamStateDisplay(stateString);
    }

    /** implementation of updateMethod callback */
    public void updateMethod() {
        StreamingServiceTracker serviceTracker = getSelectedTrackedStream();
        if (serviceTracker == null) {
            setStreamMethodDisplay(StreamingService.UNICAST_METHOD);
        } else {
            setStreamMethodDisplay(serviceTracker.getMethod());
        }
    }
}
