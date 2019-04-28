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
package android.car.usb.handler;

import android.annotation.Nullable;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.List;

/**
 * Activity to handle USB device attached.
 * <p>
 * When user plugs in USB device: a) Device was used before and user selected handler for it. In
 * this case handler will be launched. b) Device has not handler assigned. In this case supported
 * handlers will be captured, and user will be presented with choice to assign default handler.
 * After that handler will be launched.
 */
public class UsbHostManagementActivity extends Activity
        implements UsbHostController.UsbHostControllerCallbacks {
    private static final String TAG = UsbHostManagementActivity.class.getSimpleName();

    private HandlersAdapter mListAdapter;
    private ListView mHandlersList;
    private LinearLayout mProgressInfo;
    private UsbHostController mController;
    private PackageManager mPackageManager;

    private final AdapterView.OnItemClickListener mHandlerClickListener =
            new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, final View view, int position, long id) {
            UsbDeviceSettings settings = (UsbDeviceSettings) parent.getItemAtPosition(position);
            settings.setDefaultHandler(true);
            mController.applyDeviceSettings(settings);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.usb_host);
        mHandlersList = (ListView) findViewById(R.id.usb_handlers_list);
        mProgressInfo = (LinearLayout) findViewById(R.id.usb_handlers_progress);
        mListAdapter = new HandlersAdapter(this);
        mHandlersList.setAdapter(mListAdapter);
        mHandlersList.setOnItemClickListener(mHandlerClickListener);
        mController = new UsbHostController(this, this);
        mPackageManager = getPackageManager();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mController.release();
    }

    @Override
    public void onResume() {
        super.onResume();
        UsbDevice connectedDevice = getDevice();
        if (connectedDevice != null) {
            mController.processDevice(connectedDevice);
        } else {
            finish();
        }
    }

    @Override
    public void shutdown() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        });
    }

    @Override
    public void processingStateChanged(final boolean processing) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mProgressInfo.setVisibility(processing ? View.VISIBLE : View.GONE);
            }
        });
    }

  @Override
  public void titleChanged(final String title) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setTitle(title);
            }
        });
    }

  @Override
  public void optionsUpdated(final List<UsbDeviceSettings> options) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mListAdapter.clear();
                mListAdapter.addAll(options);
            }
        });
    }


    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Nullable
    private UsbDevice getDevice() {
        if (!UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(getIntent().getAction())) {
            return null;
        }
        return (UsbDevice) getIntent().getParcelableExtra(UsbManager.EXTRA_DEVICE);
    }

    private class HandlersAdapter extends ArrayAdapter<UsbDeviceSettings> {
        class HandlerHolder {
            public TextView mAppName;
            public ImageView mAppIcon;
        }

        HandlersAdapter(Context context) {
            super(context, R.layout.usb_handler_row);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View rowView = convertView;
            if (rowView == null) {
                rowView = getLayoutInflater().inflate(R.layout.usb_handler_row, null);
                HandlerHolder holder = new HandlerHolder();
                holder.mAppName = (TextView) rowView.findViewById(R.id.usb_handler_title);
                holder.mAppIcon = (ImageView) rowView.findViewById(R.id.usb_handler_icon);
                rowView.setTag(holder);
            }

            HandlerHolder holder = (HandlerHolder) rowView.getTag();
            ComponentName handler = getItem(position).getHandler();

            try {
                ApplicationInfo appInfo =
                        mPackageManager.getApplicationInfo(handler.getPackageName(), 0);
                holder.mAppName.setText(appInfo.loadLabel(mPackageManager));
                holder.mAppIcon.setImageDrawable(appInfo.loadIcon(mPackageManager));
            } catch (NameNotFoundException e) {
                Log.e(TAG, "Handling package not found: " + handler.getPackageName());
                holder.mAppName.setText(handler.flattenToShortString());
                holder.mAppIcon.setImageResource(android.R.color.transparent);
            }
            return rowView;
        }
    }
}
