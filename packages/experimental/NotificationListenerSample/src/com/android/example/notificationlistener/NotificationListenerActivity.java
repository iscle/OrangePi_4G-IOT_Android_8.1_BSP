/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.example.notificationlistener;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.provider.Settings.Secure;
import android.service.notification.StatusBarNotification;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.support.v7.widget.helper.ItemTouchHelper.SimpleCallback;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class NotificationListenerActivity extends Activity {
    private static final String LISTENER_PATH = "com.android.example.notificationlistener/" +
            "com.android.example.notificationlistener.Listener";
    private static final String TAG = "NotificationListenerActivity";

    private Button mLaunchButton;
    private Button mSnoozeButton;
    private TextView mEmptyText;
    private StatusAdaptor mStatusAdaptor;
    private final BroadcastReceiver mRefreshListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "update tickle");
            updateList(intent.getStringExtra(Listener.EXTRA_KEY));
        }
    };
    private final BroadcastReceiver mStateListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "state tickle");
            checkEnabled();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.long_app_name);
        setContentView(R.layout.main);
        mLaunchButton = (Button) findViewById(R.id.launch_settings);
        mSnoozeButton = (Button) findViewById(R.id.snooze);
        mEmptyText = (TextView) findViewById(android.R.id.empty);
        mStatusAdaptor = new StatusAdaptor(this);
        RecyclerView list = (RecyclerView) findViewById(android.R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(mStatusAdaptor);

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new SimpleCallback(0,
                ItemTouchHelper.RIGHT) {
            @Override
            public int getMovementFlags(RecyclerView recyclerView, ViewHolder viewHolder) {
                List<StatusBarNotification> notifications = mStatusAdaptor.mNotifications;
                int position = viewHolder.getAdapterPosition();
                if (notifications == null || position >= notifications.size()) {
                    return 0;
                }
                StatusBarNotification notification = notifications.get(position);
                return notification.isClearable() ? super.getMovementFlags(recyclerView, viewHolder)
                        : 0;
            }

            @Override
            public boolean onMove(RecyclerView recyclerView, ViewHolder viewHolder,
                    ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(ViewHolder viewHolder, int direction) {
                dismiss(viewHolder.itemView);
            }
        });
        itemTouchHelper.attachToRecyclerView(list);
    }

    @Override
    protected void onStop() {
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
        localBroadcastManager.unregisterReceiver(mRefreshListener);
        localBroadcastManager.unregisterReceiver(mStateListener);
        super.onStop();
    }

    @Override
    public void onStart() {
        super.onStart();
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
        localBroadcastManager.registerReceiver(mRefreshListener,
                new IntentFilter(Listener.ACTION_REFRESH));
        localBroadcastManager.registerReceiver(mStateListener,
                new IntentFilter(Listener.ACTION_STATE_CHANGE));
        updateList(null);
    }

    @Override
    public void onResume() {
        super.onResume();
        checkEnabled();
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        checkEnabled();
    }

    private void checkEnabled() {
        String listeners = Secure.getString(getContentResolver(),
                "enabled_notification_listeners");
        if (listeners != null && listeners.contains(LISTENER_PATH)) {
            mLaunchButton.setText(R.string.launch_to_disable);
            mEmptyText.setText(R.string.waiting_for_content);
            mSnoozeButton.setEnabled(true);
            if (Listener.isConnected()) {
                mSnoozeButton.setText(R.string.snooze);
            } else {
                mSnoozeButton.setText(R.string.unsnooze);
            }
        } else {
            mLaunchButton.setText(R.string.launch_to_enable);
            mSnoozeButton.setEnabled(false);
            mEmptyText.setText(R.string.nothing_to_see);
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.explanation)
                    .setTitle(R.string.disabled);
            builder.setPositiveButton(R.string.enable_it, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    launchSettings(null);
                }
            });
            builder.setNegativeButton(R.string.cancel, null);
            builder.create().show();
        }
    }

    public void launchSettings(View v) {
        startActivityForResult(
                new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"), 0);
    }

    public void snooze(View v) {
        Log.d(TAG, "clicked snooze");
        Listener.toggleSnooze(this);
    }

    public void dismiss(View v) {
        Log.d(TAG, "clicked dismiss ");
        Object tag = v.getTag();
        if (tag instanceof StatusBarNotification) {
            StatusBarNotification sbn = (StatusBarNotification) tag;
            Log.d(TAG, "  on " + sbn.getKey());
            LocalBroadcastManager.getInstance(this).
                    sendBroadcast(new Intent(Listener.ACTION_DISMISS)
                            .putExtra(Listener.EXTRA_KEY, sbn.getKey()));
        }
    }

    public void launch(View v) {
        Log.d(TAG, "clicked launch");
        Object tag = v.getTag();
        if (tag instanceof StatusBarNotification) {
            StatusBarNotification sbn = (StatusBarNotification) tag;
            Log.d(TAG, "  on " + sbn.getKey());
            LocalBroadcastManager.getInstance(this).
                    sendBroadcast(new Intent(Listener.ACTION_LAUNCH)
                            .putExtra(Listener.EXTRA_KEY, sbn.getKey()));
        }
    }

    private void updateList(String key) {
        final List<StatusBarNotification> notifications = Listener.getNotifications();
        if (notifications != null) {
            mStatusAdaptor.setData(notifications);
            findViewById(android.R.id.empty).setVisibility(notifications.size() == 0
                    ? View.VISIBLE : View.GONE);
        } else {
            findViewById(android.R.id.empty).setVisibility(View.VISIBLE);
        }
        mStatusAdaptor.update(key);
    }

    private class StatusAdaptor extends RecyclerView.Adapter<Holder> {
        private final Context mContext;
        private List<StatusBarNotification> mNotifications;
        private HashMap<String, Long> mKeyToId;
        private HashSet<String> mKeys;
        private long mNextId;
        private String mUpdateKey;

        public StatusAdaptor(Context context) {
            mContext = context;
            mKeyToId = new HashMap<String, Long>();
            mKeys = new HashSet<String>();
            mNextId = 0;
            setHasStableIds(true);
        }

        @Override
        public int getItemCount() {
            return mNotifications == null ? 0 : mNotifications.size();
        }

        @Override
        public long getItemId(int position) {
            final StatusBarNotification sbn = mNotifications.get(position);
            final String key = sbn.getKey();
            if (!mKeyToId.containsKey(key)) {
                mKeyToId.put(key, mNextId);
                mNextId++;
            }
            return mKeyToId.get(key);
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item,
                    parent, false));
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            FrameLayout container = holder.container;
            StatusBarNotification sbn = mNotifications.get(position);
            if (container.getTag() instanceof StatusBarNotification &&
                    container.getChildCount() > 0) {
                // recycle the view
                StatusBarNotification old = (StatusBarNotification) container.getTag();
                if (sbn.getKey().equals(mUpdateKey)) {
                    //this view is out of date, discard it
                    mUpdateKey = null;
                } else {
                    View content = container.getChildAt(0);
                    container.removeView(content);
                }
            }
            Notification.Builder builder =
                    Notification.Builder.recoverBuilder(mContext, sbn.getNotification());
            View child = builder.createContentView().apply(mContext, null);
            container.setTag(sbn);
            holder.itemView.setTag(sbn);
            container.removeAllViews();
            container.addView(child);
        }

        public void update(String key) {
            if (mNotifications != null) {
                synchronized (mNotifications) {
                    for (int i = 0; i < mNotifications.size(); i++) {
                        if (mNotifications.get(i).getKey().equals(key)) {
                            Log.d(TAG, "notifyItemChanged " + i);
                            notifyItemChanged(i);
                        }
                    }
                }
            } else {
                Log.d(TAG, "missed and update");
            }
        }

        public void setData(List<StatusBarNotification> notifications) {
            mNotifications = notifications;
            notifyDataSetChanged();
        }
    }

    private static class Holder extends RecyclerView.ViewHolder {
        private final FrameLayout container;

        public Holder(View itemView) {
            super(itemView);
            container = (FrameLayout) itemView.findViewById(R.id.remote_view);
        }
    }
}
