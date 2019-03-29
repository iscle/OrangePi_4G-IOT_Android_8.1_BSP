/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.cts.verifier.bluetooth;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

public class BleScannerHardwareScanFilterActivity extends PassFailButtons.Activity {

    private static final String TAG = "BleScannerHardwareScanFilter";

    private ListView mScanResultListView;
    private MapAdapter mAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ble_scanner_hardware_scan_filter);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.ble_scanner_scan_filter_name,
                         R.string.ble_scanner_scan_filter_info, -1);

        mScanResultListView = (ListView)findViewById(R.id.ble_scan_result_list);
        mAdapter = new MapAdapter();
        mScanResultListView.setAdapter(mAdapter);

        ((Button) findViewById(R.id.ble_scan_with_filter))
                .setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(BleScannerHardwareScanFilterActivity.this,
                                BleScannerService.class);
                        intent.putExtra(BleScannerService.EXTRA_COMMAND,
                                BleScannerService.COMMAND_SCAN_WITH_FILTER);
                        startService(intent);
                    }
                });

        ((Button) findViewById(R.id.ble_scan_without_filter))
                .setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(BleScannerHardwareScanFilterActivity.this,
                                BleScannerService.class);
                        intent.putExtra(BleScannerService.EXTRA_COMMAND,
                                BleScannerService.COMMAND_SCAN_WITHOUT_FILTER);
                        startService(intent);
                    }
                });
    }

    @Override
    public void onResume() {
        super.onResume();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BleScannerService.BLE_SCAN_RESULT);
        registerReceiver(onBroadcast, filter);
    }


    private BroadcastReceiver onBroadcast = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case BleScannerService.BLE_SCAN_RESULT:
                    String uuid = intent.getStringExtra(BleScannerService.EXTRA_UUID);
                    String data = intent.getStringExtra(BleScannerService.EXTRA_DATA);
                    if (data != null) {
                        mAdapter.addItem(uuid + " : " + data);
                    }
                    break;
            }
        }
    };

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(onBroadcast);
    }

    private void stop() {
        stopService(new Intent(this, BleScannerService.class));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stop();
    }

    public class MapAdapter extends BaseAdapter {
        private Map<String, Integer> mData;
        private ArrayList<String> mKeys;
        public MapAdapter() {
            mData = new HashMap<>();
            mKeys = new ArrayList<>();
        }

        @Override
        public int getCount() {
            return mData.size();
        }

        @Override
        public Object getItem(int position) {
            return mData.get(mKeys.get(position));
        }

        @Override
        public long getItemId(int arg0) {
            return arg0;
        }

        public void addItem(String key) {
            if (!mData.containsKey(key)) {
                mKeys.add(key);
                mData.put(key, new Integer(1));
            } else {
                mData.put(key, mData.get(key) + 1);
            }
            this.notifyDataSetChanged();
        }

        @Override
        public View getView(int pos, View view, ViewGroup parent) {
            if (view == null) {
                view = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            }
            String key = mKeys.get(pos);
            String value = getItem(pos).toString();
            TextView text1 = (TextView) view.findViewById(android.R.id.text1);
            TextView text2 = (TextView) view.findViewById(android.R.id.text2);
            text1.setText(key);
            text2.setText(value);
            return view;
        }
    }
}
