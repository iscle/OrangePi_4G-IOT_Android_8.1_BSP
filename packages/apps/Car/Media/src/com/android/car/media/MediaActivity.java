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
package com.android.car.media;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import com.android.car.app.CarDrawerActivity;
import com.android.car.app.CarDrawerAdapter;
import com.android.car.media.drawer.MediaDrawerController;

/**
 * This activity controls the UI of media. It also updates the connection status for the media app
 * by broadcast. Drawer menu is controlled by {@link MediaDrawerController}.
 */
public class MediaActivity extends CarDrawerActivity
        implements MediaPlaybackFragment.PlayQueueRevealer {
    private static final String ACTION_MEDIA_APP_STATE_CHANGE
            = "android.intent.action.MEDIA_APP_STATE_CHANGE";
    private static final String EXTRA_MEDIA_APP_FOREGROUND
            = "android.intent.action.MEDIA_APP_STATE";

    private static final String TAG = "MediaActivity";

    /**
     * Whether or not {@link #onStart()} has been called.
     */
    private boolean mIsStarted;

    /**
     * {@code true} if there was a request to change the content fragment of this Activity when
     * it is not started. Then, when onStart() is called, the content fragment will be added.
     *
     * <p>This prevents a bug where the content fragment is added when the app is not running,
     * causing a StateLossException.
     */
    private boolean mContentFragmentChangeQueued;

    private MediaDrawerController mDrawerController;
    private MediaPlaybackFragment mMediaPlaybackFragment;

    @Override
    protected void onStart() {
        super.onStart();
        Intent i = new Intent(ACTION_MEDIA_APP_STATE_CHANGE);
        i.putExtra(EXTRA_MEDIA_APP_FOREGROUND, true);
        sendBroadcast(i);

        mIsStarted = true;

        if (mContentFragmentChangeQueued) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Content fragment queued. Attaching now.");
            }
            showMediaPlaybackFragment();
            mContentFragmentChangeQueued = false;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Intent i = new Intent(ACTION_MEDIA_APP_STATE_CHANGE);
        i.putExtra(EXTRA_MEDIA_APP_FOREGROUND, false);
        sendBroadcast(i);

        mIsStarted = false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // The drawer must be initialized before the super call because CarDrawerActivity.onCreate
        // looks up the rootAdapter from its subclasses. The MediaDrawerController provides the
        // root adapter.
        mDrawerController = new MediaDrawerController(this);

        super.onCreate(savedInstanceState);

        setMainContent(R.layout.media_activity);
        MediaManager.getInstance(this).addListener(mListener);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Send the broadcast to let the current connected media app know it is disconnected now.
        sendMediaConnectionStatusBroadcast(
                MediaManager.getInstance(this).getCurrentComponent(),
                MediaConstants.MEDIA_DISCONNECTED);
        mDrawerController.cleanup();
        MediaManager.getInstance(this).removeListener(mListener);
        mMediaPlaybackFragment = null;
    }

    @Override
    protected CarDrawerAdapter getRootAdapter() {
        return mDrawerController.getRootAdapter();
    }

    @Override
    public void onResumeFragments() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onResumeFragments");
        }

        super.onResumeFragments();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "onNewIntent(); intent: " + (intent == null ? "<< NULL >>" : intent));
        }

        setIntent(intent);
        closeDrawer();
    }

    @Override
    public void onBackPressed() {
        if (mMediaPlaybackFragment != null) {
            mMediaPlaybackFragment.closeOverflowMenu();
        }
        super.onBackPressed();
    }

    private void handleIntent(Intent intent) {
        Bundle extras = null;
        if (intent != null) {
            extras = intent.getExtras();
        }

        // If the intent has a media component name set, connect to it directly
        if (extras != null && extras.containsKey(MediaManager.KEY_MEDIA_PACKAGE) &&
                extras.containsKey(MediaManager.KEY_MEDIA_CLASS)) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Media component in intent.");
            }

            ComponentName component = new ComponentName(
                    intent.getStringExtra(MediaManager.KEY_MEDIA_PACKAGE),
                    intent.getStringExtra(MediaManager.KEY_MEDIA_CLASS)
            );
            MediaManager.getInstance(this).setMediaClientComponent(component);
        } else {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Launching most recent / default component.");
            }

            // Set it to the default GPM component.
            MediaManager.getInstance(this).connectToMostRecentMediaComponent(
                    new CarClientServiceAdapter(getPackageManager()));
        }

        if (isSearchIntent(intent)) {
            MediaManager.getInstance(this).processSearchIntent(intent);
            setIntent(null);
        }
    }

    /**
     * Returns {@code true} if the given intent is one that contains a search query for the
     * attached media application.
     */
    private boolean isSearchIntent(Intent intent) {
        return (intent != null && intent.getAction() != null &&
                intent.getAction().equals(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH));
    }

    private void sendMediaConnectionStatusBroadcast(ComponentName componentName,
            String connectionStatus) {
        // There will be no current component if no media app has been chosen before.
        if (componentName == null) {
            return;
        }

        Intent intent = new Intent(MediaConstants.ACTION_MEDIA_STATUS);
        intent.setPackage(componentName.getPackageName());
        intent.putExtra(MediaConstants.MEDIA_CONNECTION_STATUS, connectionStatus);
        sendBroadcast(intent);
    }

    private void showMediaPlaybackFragment() {
        // If the fragment has already been created, then it has been attached already.
        if (mMediaPlaybackFragment != null) {
            return;
        }

        mMediaPlaybackFragment = new MediaPlaybackFragment();
        mMediaPlaybackFragment.setPlayQueueRevealer(this);

       getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, mMediaPlaybackFragment)
                .commit();
    }

    @Override
    public void showPlayQueue() {
        mDrawerController.showPlayQueue();
    }

    private final MediaManager.Listener mListener = new MediaManager.Listener() {
        @Override
        public void onMediaAppChanged(ComponentName componentName) {
            sendMediaConnectionStatusBroadcast(componentName, MediaConstants.MEDIA_CONNECTED);

            // Since this callback happens asynchronously, ensure that the Activity has been
            // started before changing fragments. Otherwise, the attach fragment will throw
            // an IllegalStateException due to Fragment's checkStateLoss.
            if (mIsStarted) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "onMediaAppChanged: attaching content fragment");
                }
                showMediaPlaybackFragment();
            } else {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "onMediaAppChanged: queuing content fragment change");
                }
                mContentFragmentChangeQueued = true;
            }
        }

        @Override
        public void onStatusMessageChanged(String msg) {}
    };
}
