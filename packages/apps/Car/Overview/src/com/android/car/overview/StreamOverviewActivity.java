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
package com.android.car.overview;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;
import com.android.car.stream.IStreamConsumer;
import com.android.car.stream.IStreamService;
import com.android.car.stream.StreamCard;
import com.android.car.stream.StreamConstants;
import com.android.car.view.PagedListView;

import java.util.List;

/**
 * An overview activity that presents {@link StreamCard} as scrollable list.
 */
public class StreamOverviewActivity extends Activity {
    private static final String TAG = "Overview";
    private static final int SERVICE_CONNECTION_RETRY_DELAY_MS = 5000;
    private static final String ACTION_CAR_OVERVIEW_STATE_CHANGE
            = "android.intent.action.CAR_OVERVIEW_APP_STATE_CHANGE";
    private static final String EXTRA_CAR_OVERVIEW_FOREGROUND
            = "android.intent.action.CAR_APP_STATE";

    private static final int PERMISSION_ACTIVITY_REQUEST_CODE = 5151;

    private PagedListView mPageListView;
    private View mPermissionText;
    private StreamAdapter mAdapter;
    private final Handler mHandler = new Handler();
    private int mConnectionRetryCount;

    private IStreamService mService;
    private StreamServiceConnection mConnection;

    private int mCardBottomMargin;
    private Toast mToast;

    boolean mCheckPermissionsOnResume;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.overview_activity);
        mCardBottomMargin = getResources().getDimensionPixelSize(R.dimen.stream_card_bottom_margin);

        int statusBarHeight = getStatusBarHeight();

        FrameLayout.LayoutParams params
                = (FrameLayout.LayoutParams) findViewById(R.id.action_icon_bar).getLayoutParams();
        params.setMargins(0, statusBarHeight, 0, 0);

        mAdapter = new StreamAdapter(this /* context */);

        mPageListView = (PagedListView) findViewById(R.id.list_view);
        mPageListView.setAdapter(mAdapter);
        mPageListView.addItemDecoration(new DefaultDecoration());
        mPageListView.setLightMode();

        int listTopMargin = statusBarHeight
                + getResources().getDimensionPixelSize(R.dimen.lens_header_height);
        FrameLayout.LayoutParams listViewParams
                = (FrameLayout.LayoutParams) mPageListView.getLayoutParams();
        listViewParams.setMargins(0, listTopMargin, 0, 0);

        mPermissionText = findViewById(R.id.permission_text);
        mPermissionText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startPermissionsActivity(false /* checkPermissionsOnly */);
            }
        });

        mToast = Toast.makeText(StreamOverviewActivity.this,
                getString(R.string.voice_assistant_help_msg), Toast.LENGTH_SHORT);
        mToast.setGravity(Gravity.CENTER, 0, 0);
        findViewById(R.id.voice_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mToast.show();
            }
        });

        findViewById(R.id.gear_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
            }
        });

        startPermissionsActivity(true /* checkPermissionsOnly */);
    }

    private void startPermissionsActivity(boolean checkPermissionsOnly) {
        // Start StreamService's permission activity before binding to it.
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(
                getString(R.string.car_stream_item_manager_package_name),
                getString(R.string.car_stream_item_manager_permissions_activity)));
        intent.putExtra(StreamConstants.STREAM_PERMISSION_CHECK_PERMISSIONS_ONLY,
                checkPermissionsOnly);
        startActivityForResult(intent, PERMISSION_ACTIVITY_REQUEST_CODE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mCheckPermissionsOnResume) {
            startPermissionsActivity(true /* checkPermissionsOnly */);
        } else {
            mCheckPermissionsOnResume = true;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PERMISSION_ACTIVITY_REQUEST_CODE) {
            // onResume is called after onActivityResult, if the permissions activity has
            // already finished, then don't bother checking for permissions again on resume.
            mCheckPermissionsOnResume = false;
            if (resultCode == Activity.RESULT_OK) {
                mPermissionText.setVisibility(View.GONE);
                mPageListView.setVisibility(View.VISIBLE);
                bindStreamService();
            } else {
                mPermissionText.setVisibility(View.VISIBLE);
                mPageListView.setVisibility(View.GONE);
            }
        }
    }

    @Override
    protected void onDestroy() {
        // Do a tear down to avoid leaks
        mHandler.removeCallbacks(mServiceConnectionRetry);

        if (mConnection != null) {
            unbindService(mConnection);
        }
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent i = new Intent(ACTION_CAR_OVERVIEW_STATE_CHANGE);
        i.putExtra(EXTRA_CAR_OVERVIEW_FOREGROUND, true);
        sendBroadcast(i);
    }

    @Override
    protected void onStop() {
        Intent i = new Intent(ACTION_CAR_OVERVIEW_STATE_CHANGE);
        i.putExtra(EXTRA_CAR_OVERVIEW_FOREGROUND, false);
        sendBroadcast(i);
        super.onStop();
    }

    private class StreamServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mConnectionRetryCount = 1;
            // If there is currently a retry scheduled, cancel it.
            if (mServiceConnectionRetry != null) {
                mHandler.removeCallbacks(mServiceConnectionRetry);
            }

            mService = IStreamService.Stub.asInterface(service);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Service connected");
            }
            try {
                mService.registerConsumer(mStreamConsumer);

                List<StreamCard> cards = mService.fetchAllStreamCards();
                if (cards != null) {
                    for (StreamCard card : cards) {
                        mAdapter.addCard(card);
                    }
                }


            } catch (RemoteException e) {
                throw new IllegalStateException("not connected to IStreamItemManagerService");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            mAdapter.removeAllCards();
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Service disconnected, reconnecting...");
            }

            mHandler.removeCallbacks(mServiceConnectionRetry);
            mHandler.postDelayed(mServiceConnectionRetry, SERVICE_CONNECTION_RETRY_DELAY_MS);
        }
    }

    private Runnable mServiceConnectionRetry = new Runnable() {
        @Override
        public void run() {
            if (mService != null) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Stream service rebound by framework, no need to bind again");
                }
                return;
            }
            mConnectionRetryCount++;
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Rebinding disconnected Stream Service, retry count: "
                        + mConnectionRetryCount);
            }

            if (!bindStreamService()) {
                mHandler.postDelayed(mServiceConnectionRetry,
                        mConnectionRetryCount * SERVICE_CONNECTION_RETRY_DELAY_MS);
            }
        }
    };

    private final IStreamConsumer mStreamConsumer = new IStreamConsumer.Stub() {
        @Override
        public void onStreamCardAdded(StreamCard card) throws RemoteException {
            StreamOverviewActivity.this.runOnUiThread(new Runnable() {
                public void run() {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "Stream Card added: " + card);
                    }
                    mAdapter.addCard(card);
                }
            });
        }

        @Override
        public void onStreamCardRemoved(StreamCard card) throws RemoteException {
            StreamOverviewActivity.this.runOnUiThread(new Runnable() {
                public void run() {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "Stream Card removed: " + card);
                    }
                    mAdapter.removeCard(card);
                }
            });
        }

        @Override
        public void onStreamCardChanged(StreamCard newStreamCard) throws RemoteException {
        }
    };

    private boolean bindStreamService() {
        mConnection = new StreamServiceConnection();
        Intent intent = new Intent();
        intent.setAction(StreamConstants.STREAM_CONSUMER_BIND_ACTION);
        intent.setComponent(new ComponentName(
                getString(R.string.car_stream_item_manager_package_name),
                getString(R.string.car_stream_item_manager_class_name)));

        boolean bound = bindService(intent, mConnection, BIND_AUTO_CREATE);
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "IStreamItemManagerService bound: " + bound
                    + "; component: " + intent.getComponent());
        }

        return bound;
    }

    private class DefaultDecoration extends RecyclerView.ItemDecoration {
        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                RecyclerView.State state) {
            outRect.bottom = mCardBottomMargin;
        }
    }

    private int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }
}
