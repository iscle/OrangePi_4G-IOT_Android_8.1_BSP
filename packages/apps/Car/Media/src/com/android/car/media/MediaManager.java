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

import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.service.media.MediaBrowserService;
import android.text.TextUtils;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages which media app we should connect to. The manager also retrieves various attributes
 * from the media app and share among different components in GearHead media app.
 */
public class MediaManager {
    private static final String TAG = "GH.MediaManager";
    private static final String PREFS_FILE_NAME = "MediaClientManager.Preferences";
    /** The package of the most recently used media component **/
    private static final String PREFS_KEY_PACKAGE = "media_package";
    /** The class of the most recently used media class **/
    private static final String PREFS_KEY_CLASS = "media_class";
    /** Third-party defined application theme to use **/
    private static final String THEME_META_DATA_NAME = "com.google.android.gms.car.application.theme";

    public static final String KEY_MEDIA_COMPONENT = "media_component";
    /** Intent extra specifying the package with the MediaBrowser **/
    public static final String KEY_MEDIA_PACKAGE = "media_package";
    /** Intent extra specifying the MediaBrowserService **/
    public static final String KEY_MEDIA_CLASS = "media_class";

    /**
     * Flag for when GSA is not 100% confident on the query and therefore, the result in the
     * {@link #KEY_MEDIA_PACKAGE_FROM_GSA} should be ignored.
     */
    private static final String KEY_IGNORE_ORIGINAL_PKG =
            "com.google.android.projection.gearhead.ignore_original_pkg";

    /**
     * Intent extra specifying the package name of the media app that should handle
     * {@link android.provider.MediaStore#INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH}. This must match
     * KEY_PACKAGE defined in ProjectionIntentStarter in GSA.
     */
    public static final String KEY_MEDIA_PACKAGE_FROM_GSA =
            "android.car.intent.extra.MEDIA_PACKAGE";

    private static final String GOOGLE_PLAY_MUSIC_PACKAGE = "com.google.android.music";
    // Extras along with the Knowledge Graph that are not meant to be seen by external apps.
    private static final String[] INTERNAL_EXTRAS = {"KEY_LAUNCH_HANDOVER_UNDERNEATH",
            "com.google.android.projection.gearhead.ignore_original_pkg"};

    private static final Intent MEDIA_BROWSER_INTENT =
            new Intent(MediaBrowserService.SERVICE_INTERFACE);
    private static MediaManager sInstance;

    private final MediaController.Callback mMediaControllerCallback =
            new MediaManagerCallback(this);
    private final MediaBrowser.ConnectionCallback mMediaBrowserConnectionCallback =
            new MediaManagerConnectionCallback(this);

    public interface Listener {
        void onMediaAppChanged(ComponentName componentName);

        /**
         * Called when we want to show a message on playback screen.
         * @param msg if null, dismiss any previous message and
         *            restore the track title and subtitle.
         */
        void onStatusMessageChanged(String msg);
    }

    /**
     * An adapter interface to abstract the specifics of how media services are queried. This allows
     * for Vanagon to query for allowed media services without the need to connect to carClientApi.
     */
    public interface ServiceAdapter {
        List<ResolveInfo> queryAllowedServices(Intent providerIntent);
    }

    private int mPrimaryColor;
    private int mPrimaryColorDark;
    private int mAccentColor;
    private CharSequence mName;

    private final Context mContext;
    private final List<Listener> mListeners = new ArrayList<>();

    private ServiceAdapter mServiceAdapter;
    private Intent mPendingSearchIntent;

    private MediaController mController;
    private MediaBrowser mBrowser;
    private ComponentName mCurrentComponent;
    private PendingMsg mPendingMsg;

    public synchronized static MediaManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new MediaManager(context.getApplicationContext());
        }
        return sInstance;
    }

    private MediaManager(Context context) {
        mContext = context;

        // Set some sane default values for the attributes
        mName = "";
        int color = context.getResources().getColor(android.R.color.background_dark);
        mPrimaryColor = color;
        mAccentColor = color;
        mPrimaryColorDark = color;
    }

    /**
     * Returns the default component used to load media.
     */
    public ComponentName getDefaultComponent(ServiceAdapter serviceAdapter) {
        SharedPreferences prefs = mContext
                .getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE);
        String packageName = prefs.getString(PREFS_KEY_PACKAGE, null);
        String className = prefs.getString(PREFS_KEY_CLASS, null);
        final Intent providerIntent = new Intent(MediaBrowserService.SERVICE_INTERFACE);
        List<ResolveInfo> mediaApps = serviceAdapter.queryAllowedServices(providerIntent);

        // check if the previous component we connected to is still valid.
        if (packageName != null && className != null) {
            boolean componentValid = false;
            for (ResolveInfo info : mediaApps) {
                if (info.serviceInfo.packageName.equals(packageName)
                        && info.serviceInfo.name.equals(className)) {
                    componentValid = true;
                }
            }
            // if not valid, null it and we will bring up the lens switcher or connect to another
            // app (this may happen when the app has been uninstalled)
            if (!componentValid) {
                packageName = null;
                className = null;
            }
        }

        // If there are no apps used before or previous app is not valid,
        // try to connect to a supported media app.
        if (packageName == null || className == null) {
            // Only one app installed, connect to it.
            if (mediaApps.size() == 1) {
                ResolveInfo info = mediaApps.get(0);
                packageName = info.serviceInfo.packageName;
                className = info.serviceInfo.name;
            } else {
                // there are '0' or >1 media apps installed; don't know what to run
                return null;
            }
        }
        return new ComponentName(packageName, className);
    }

    /**
     * Connects to the most recently used media app if it exists and return true.
     * Otherwise check the number of supported media apps installed,
     * if only one installed, connect to it return true. Otherwise return false.
     */
    public boolean connectToMostRecentMediaComponent(ServiceAdapter serviceAdapter) {
        ComponentName component = getDefaultComponent(serviceAdapter);
        if (component != null) {
            setMediaClientComponent(serviceAdapter, component);
            return true;
        }
        return false;
    }

    public ComponentName getCurrentComponent() {
        return mCurrentComponent;
    }

    public void setMediaClientComponent(ComponentName component) {
        setMediaClientComponent(null, component);
    }

    /**
     * Change the media component. This will connect to a {@link android.media.browse.MediaBrowser} if necessary.
     * All registered listener will be updated with the new component.
     */
    public void setMediaClientComponent(ServiceAdapter serviceAdapter, ComponentName component) {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "setMediaClientComponent(), "
                    + "component: " + (component == null ? "<< NULL >>" : component.toString()));
        }

        if (component == null) {
            return;
        }

        // mController will be set to null if previously connected media session has crashed.
        if (mCurrentComponent != null && mCurrentComponent.equals(component)
                && mController != null) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Already connected to " + component.toString());
            }
            return;
        }

        mCurrentComponent = component;
        mServiceAdapter = serviceAdapter;
        disconnectCurrentBrowser();
        updateClientPackageAttributes(mCurrentComponent);

        if (mController != null) {
            mController.unregisterCallback(mMediaControllerCallback);
            mController = null;
        }
        mBrowser = new MediaBrowser(mContext, component, mMediaBrowserConnectionCallback, null);
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Connecting to " + component.toString());
        }
        mBrowser.connect();

        writeComponentToPrefs(component);

        ArrayList<Listener> temp = new ArrayList<Listener>(mListeners);
        for (Listener listener : temp) {
            listener.onMediaAppChanged(mCurrentComponent);
        }
    }

    /**
     * Processes the search intent using the current media app. If it's not connected yet, store it
     * in the {@code mPendingSearchIntent} and process it when the app is connected.
     *
     * @param intent The intent containing the query and
     *            MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH action
     */
    public void processSearchIntent(Intent intent) {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "processSearchIntent(), query: "
                    + (intent == null ? "<< NULL >>" : intent.getStringExtra(SearchManager.QUERY)));
        }
        if (intent == null) {
            return;
        }
        mPendingSearchIntent = intent;

        String mediaPackageName;
        if (intent.getBooleanExtra(KEY_IGNORE_ORIGINAL_PKG, false)) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Ignoring package from gsa and falling back to default media app");
            }
            mediaPackageName = null;
        } else if (intent.hasExtra(KEY_MEDIA_PACKAGE_FROM_GSA)) {
            // Legacy way of piping through the media app package.
            mediaPackageName = intent.getStringExtra(KEY_MEDIA_PACKAGE_FROM_GSA);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Package from extras: " + mediaPackageName);
            }
        } else {
            mediaPackageName = intent.getPackage();
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Package from getPackage(): " + mediaPackageName);
            }
        }

        if (mediaPackageName != null && mCurrentComponent != null
                && !mediaPackageName.equals(mCurrentComponent.getPackageName())) {
            final ComponentName componentName =
                    getMediaBrowserComponent(mServiceAdapter, mediaPackageName);
            if (componentName == null) {
                Log.w(TAG, "There are no matching media app to handle intent: " + intent);
                return;
            }
            setMediaClientComponent(mServiceAdapter, componentName);
            // It's safe to return here as pending search intent will be processed
            // when newly created media controller for the new media component is connected.
            return;
        }

        String query = mPendingSearchIntent.getStringExtra(SearchManager.QUERY);
        if (mController != null) {
            mController.getTransportControls().pause();
            mPendingMsg = new PendingMsg(PendingMsg.STATUS_UPDATE,
                    mContext.getResources().getString(R.string.loading));
            notifyStatusMessage(mPendingMsg.mMsg);
            Bundle extras = mPendingSearchIntent.getExtras();
            // Remove two extras that are not meant to be seen by external apps.
            if (!GOOGLE_PLAY_MUSIC_PACKAGE.equals(mediaPackageName)) {
                for (String key : INTERNAL_EXTRAS) {
                    extras.remove(key);
                }
            }
            mController.getTransportControls().playFromSearch(query, extras);
            mPendingSearchIntent = null;
        } else {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "No controller for search intent; save it for later");
            }
        }
    }


    private ComponentName getMediaBrowserComponent(ServiceAdapter serviceAdapter,
            final String packageName) {
        List<ResolveInfo> queryResults = serviceAdapter.queryAllowedServices(MEDIA_BROWSER_INTENT);
        if (queryResults != null) {
            for (int i = 0, N = queryResults.size(); i < N; ++i) {
                final ResolveInfo ri = queryResults.get(i);
                if (ri != null && ri.serviceInfo != null
                        && ri.serviceInfo.packageName.equals(packageName)) {
                    return new ComponentName(ri.serviceInfo.packageName, ri.serviceInfo.name);
                }
            }
        }
        return null;
    }

    /**
     * Add a listener to get media app changes.
     * Your listener will be called with the initial values when the listener is added.
     */
    public void addListener(Listener listener) {
        mListeners.add(listener);
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "addListener(); count: " + mListeners.size());
        }

        if (mCurrentComponent != null) {
            listener.onMediaAppChanged(mCurrentComponent);
        }

        if (mPendingMsg != null) {
            listener.onStatusMessageChanged(mPendingMsg.mMsg);
        }
    }

    public void removeListener(Listener listener) {
        mListeners.remove(listener);

        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "removeListener(); count: " + mListeners.size());
        }

        if (mListeners.size() == 0) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "no manager listeners; destroy manager instance");
            }

            synchronized (MediaManager.class) {
                sInstance = null;
            }

            if (mBrowser != null) {
                mBrowser.disconnect();
            }
        }
    }

    public CharSequence getMediaClientName() {
        return mName;
    }

    public int getMediaClientPrimaryColor() {
        return mPrimaryColor;
    }

    public int getMediaClientPrimaryColorDark() {
        return mPrimaryColorDark;
    }

    public int getMediaClientAccentColor() {
        return mAccentColor;
    }

    private void writeComponentToPrefs(ComponentName componentName) {
        // Store selected media service to shared preference.
        SharedPreferences prefs = mContext
                .getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PREFS_KEY_PACKAGE, componentName.getPackageName());
        editor.putString(PREFS_KEY_CLASS, componentName.getClassName());
        editor.apply();
    }

    /**
     * Disconnect from the current media browser service if any, and notify the listeners.
     */
    private void disconnectCurrentBrowser() {
        if (mBrowser != null) {
            mBrowser.disconnect();
            mBrowser = null;
        }
    }

    private void updateClientPackageAttributes(ComponentName componentName) {
        TypedArray ta = null;
        try {
            String packageName = componentName.getPackageName();
            ApplicationInfo applicationInfo =
                    mContext.getPackageManager().getApplicationInfo(packageName,
                            PackageManager.GET_META_DATA);
            ServiceInfo serviceInfo = mContext.getPackageManager().getServiceInfo(
                    componentName, PackageManager.GET_META_DATA);

            // Get the proper app name, check service label, then application label.
            CharSequence name = "";
            if (serviceInfo.labelRes != 0) {
                name = serviceInfo.loadLabel(mContext.getPackageManager());
            } else if (applicationInfo.labelRes != 0) {
                name = applicationInfo.loadLabel(mContext.getPackageManager());
            }
            if (TextUtils.isEmpty(name)) {
                name = mContext.getResources().getString(R.string.unknown_media_provider_name);
            }
            mName = name;

            // Get the proper theme, check theme for service, then application.
            int appTheme = 0;
            if (serviceInfo.metaData != null) {
                appTheme = serviceInfo.metaData.getInt(THEME_META_DATA_NAME);
            }
            if (appTheme == 0 && applicationInfo.metaData != null) {
                appTheme = applicationInfo.metaData.getInt(THEME_META_DATA_NAME);
            }
            if (appTheme == 0) {
                appTheme = applicationInfo.theme;
            }

            Context packageContext = mContext.createPackageContext(packageName, 0);
            packageContext.setTheme(appTheme);
            Resources.Theme theme = packageContext.getTheme();
            ta = theme.obtainStyledAttributes(new int[] {
                    android.R.attr.colorPrimary,
                    android.R.attr.colorAccent,
                    android.R.attr.colorPrimaryDark
            });
            int defaultColor =
                    mContext.getResources().getColor(android.R.color.background_dark);
            mPrimaryColor = ta.getColor(0, defaultColor);
            mAccentColor = ta.getColor(1, defaultColor);
            mPrimaryColorDark = ta.getColor(2, defaultColor);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Unable to update media client package attributes.", e);
        } finally {
            if (ta != null) {
                ta.recycle();
            }
        }
    }

    private void notifyStatusMessage(String str) {
        for (Listener l : mListeners) {
            l.onStatusMessageChanged(str);
        }
    }

    private void doPlaybackStateChanged(PlaybackState playbackState) {
        // Display error message in MediaPlaybackFragment.
        if (mPendingMsg == null) {
            return;
        }
        // Dismiss the error msg if any,
        // and dismiss status update msg if the state is now playing
        if ((mPendingMsg.mType == PendingMsg.ERROR) ||
                (playbackState.getState() == PlaybackState.STATE_PLAYING
                        && mPendingMsg.mType == PendingMsg.STATUS_UPDATE)) {
            mPendingMsg = null;
            notifyStatusMessage(null);
        }
    }

    private void doOnSessionDestroyed() {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "Media session destroyed");
        }
        if (mController != null) {
            mController.unregisterCallback(mMediaControllerCallback);
        }
        mController = null;
        mServiceAdapter = null;
    }

    private void doOnConnected() {
        // existing mController has been disconnected before we call MediaBrowser.connect()
        MediaSession.Token token = mBrowser.getSessionToken();
        if (token == null) {
            Log.e(TAG, "Media session token is null");
            return;
        }
        mController = new MediaController(mContext, token);
        mController.registerCallback(mMediaControllerCallback);
        processSearchIntent(mPendingSearchIntent);
    }

    private void doOnConnectionFailed() {
        Log.w(TAG, "Media browser connection FAILED!");
        // disconnect anyway to make sure we get into a sanity state
        mBrowser.disconnect();
        mBrowser = null;
    }

    private static class PendingMsg {
        public static final int ERROR = 0;
        public static final int STATUS_UPDATE = 1;

        public int mType;
        public String mMsg;
        public PendingMsg(int type, String msg) {
            mType = type;
            mMsg = msg;
        }
    }

    private static class MediaManagerCallback extends MediaController.Callback {
        private final WeakReference<MediaManager> mWeakCallback;

        MediaManagerCallback(MediaManager callback) {
            mWeakCallback = new WeakReference<>(callback);
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState playbackState) {
            MediaManager callback = mWeakCallback.get();
            if (callback == null) {
                return;
            }
            callback.doPlaybackStateChanged(playbackState);
        }

        @Override
        public void onSessionDestroyed() {
            MediaManager callback = mWeakCallback.get();
            if (callback == null) {
                return;
            }
            callback.doOnSessionDestroyed();
        }
    }

    private static class MediaManagerConnectionCallback extends MediaBrowser.ConnectionCallback {
        private final WeakReference<MediaManager> mWeakCallback;

        private MediaManagerConnectionCallback(MediaManager callback) {
            mWeakCallback = new WeakReference<>(callback);
        }

        @Override
        public void onConnected() {
            MediaManager callback = mWeakCallback.get();
            if (callback == null) {
                return;
            }
            callback.doOnConnected();
        }

        @Override
        public void onConnectionSuspended() {}

        @Override
        public void onConnectionFailed() {
            MediaManager callback = mWeakCallback.get();
            if (callback == null) {
                return;
            }
            callback.doOnConnectionFailed();
        }
    }
}
