/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.wallpaper.livepicker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperColors;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources.NotFoundException;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.service.wallpaper.IWallpaperConnection;
import android.service.wallpaper.IWallpaperEngine;
import android.service.wallpaper.IWallpaperService;
import android.service.wallpaper.WallpaperService;
import android.service.wallpaper.WallpaperSettingsActivity;
import android.support.design.widget.BottomSheetBehavior;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager.LayoutParams;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toolbar;

import java.io.IOException;

public class LiveWallpaperPreview extends Activity {
    static final String EXTRA_LIVE_WALLPAPER_INFO = "android.live_wallpaper.info";

    private static final String LOG_TAG = "LiveWallpaperPreview";

    private static final boolean SHOW_DUMMY_DATA = false;

    private WallpaperManager mWallpaperManager;
    private WallpaperConnection mWallpaperConnection;

    private String mPackageName;
    private Intent mWallpaperIntent;
    private Intent mSettingsIntent;

    private TextView mAttributionTitle;
    private TextView mAttributionSubtitle1;
    private TextView mAttributionSubtitle2;
    private Button mAttributionExploreButton;
    private ImageButton mPreviewPaneArrow;
    private View mBottomSheet;
    private View mSpacer;
    private View mLoading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        init();
    }

    protected void init() {
        Bundle extras = getIntent().getExtras();
        WallpaperInfo info = extras.getParcelable(EXTRA_LIVE_WALLPAPER_INFO);
        if (info == null) {
            setResult(RESULT_CANCELED);
            finish();
        }
        initUI(info);
    }

    protected void initUI(WallpaperInfo info) {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        setContentView(R.layout.live_wallpaper_preview);
        mAttributionTitle = (TextView) findViewById(R.id.preview_attribution_pane_title);
        mAttributionSubtitle1 = (TextView) findViewById(R.id.preview_attribution_pane_subtitle1);
        mAttributionSubtitle2 = (TextView) findViewById(R.id.preview_attribution_pane_subtitle2);
        mAttributionExploreButton = (Button) findViewById(
                R.id.preview_attribution_pane_explore_button);
        mPreviewPaneArrow = (ImageButton) findViewById(R.id.preview_attribution_pane_arrow);
        mBottomSheet = findViewById(R.id.bottom_sheet);
        mSpacer = findViewById(R.id.spacer);
        mLoading = findViewById(R.id.loading);

        mPackageName = info.getPackageName();
        mWallpaperIntent = new Intent(WallpaperService.SERVICE_INTERFACE)
                .setClassName(info.getPackageName(), info.getServiceName());

        final String settingsActivity = info.getSettingsActivity();
        if (settingsActivity != null) {
            mSettingsIntent = new Intent();
            mSettingsIntent.setComponent(new ComponentName(mPackageName, settingsActivity));
            mSettingsIntent.putExtra(WallpaperSettingsActivity.EXTRA_PREVIEW_MODE, true);
            final PackageManager pm = getPackageManager();
            final ActivityInfo activityInfo = mSettingsIntent.resolveActivityInfo(pm, 0);
            if (activityInfo == null) {
                Log.e(LOG_TAG, "Couldn't find settings activity: " + settingsActivity);
                mSettingsIntent = null;
            }
        }

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setActionBar(toolbar);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setDisplayShowTitleEnabled(false);

        Drawable backArrow = getResources().getDrawable(R.drawable.ic_arrow_back_white_24dp);
        backArrow.setAutoMirrored(true);
        toolbar.setNavigationIcon(backArrow);

        mWallpaperManager = WallpaperManager.getInstance(this);
        mWallpaperConnection = new WallpaperConnection(mWallpaperIntent);

        populateAttributionPane(info);
    }

    private void populateAttributionPane(WallpaperInfo info) {
        if (!info.getShowMetadataInPreview() && !SHOW_DUMMY_DATA) {
            mBottomSheet.setVisibility(View.GONE);
            return;
        }
        final BottomSheetBehavior bottomSheetBehavior = BottomSheetBehavior.from(mBottomSheet);

        OnClickListener onClickListener = new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                } else if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                }
            }
        };
        mAttributionTitle.setOnClickListener(onClickListener);
        mPreviewPaneArrow.setOnClickListener(onClickListener);

        bottomSheetBehavior.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    mPreviewPaneArrow.setImageResource(R.drawable.ic_keyboard_arrow_up_white_24dp);
                    mPreviewPaneArrow.setContentDescription(
                            getResources().getString(R.string.expand_attribution_panel));
                } else if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    mPreviewPaneArrow.setImageResource(
                            R.drawable.ic_keyboard_arrow_down_white_24dp);
                    mPreviewPaneArrow.setContentDescription(
                            getResources().getString(R.string.collapse_attribution_panel));
                }
            }

            @Override
            public void onSlide(View bottomSheet, float slideOffset) {
                float alpha;
                if (slideOffset >= 0) {
                    alpha = slideOffset;
                } else {
                    alpha = 1f - slideOffset;
                }
                mAttributionTitle.setAlpha(slideOffset);
                mAttributionSubtitle1.setAlpha(slideOffset);
                mAttributionSubtitle2.setAlpha(slideOffset);
                mAttributionExploreButton.setAlpha(slideOffset);
            }
        });

        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        mPreviewPaneArrow.setImageResource(R.drawable.ic_keyboard_arrow_down_white_24dp);

        if (SHOW_DUMMY_DATA) {
            mAttributionTitle.setText("Diorama, Yosemite");
            mAttributionSubtitle1.setText("Live Earth Collection - Android Earth");
            mAttributionSubtitle2.setText("Lorem ipsum dolor sit amet, consectetur adipiscing elit."
                    + " Sed imperdiet et mauris molestie laoreet. Proin volutpat elit nec magna"
                    + " tempus, ac aliquet lectus volutpat.");
            mAttributionExploreButton.setText("Explore");
        } else {
            PackageManager pm = getPackageManager();

            CharSequence title = info.loadLabel(pm);
            if (!TextUtils.isEmpty(title)) {
                mAttributionTitle.setText(title);
            } else {
                mAttributionTitle.setVisibility(View.GONE);
            }

            try {
                CharSequence author = info.loadAuthor(pm);
                if (TextUtils.isEmpty(author)) {
                    throw new NotFoundException();
                }
                mAttributionSubtitle1.setText(author);
            } catch (NotFoundException e) {
                mAttributionSubtitle1.setVisibility(View.GONE);
            }

            try {
                CharSequence description = info.loadDescription(pm);
                if (TextUtils.isEmpty(description)) {
                    throw new NotFoundException();
                }
                mAttributionSubtitle2.setText(description);
            } catch (NotFoundException e) {
                mAttributionSubtitle2.setVisibility(View.GONE);
            }

            try {
                Uri contextUri = info.loadContextUri(pm);
                CharSequence contextDescription = info.loadContextDescription(pm);
                if (contextUri == null) {
                    throw new NotFoundException();
                }
                mAttributionExploreButton.setText(contextDescription);
                mAttributionExploreButton.setOnClickListener(v -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, contextUri);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        Log.e(LOG_TAG, "Couldn't find activity for context link.", e);
                    }
                });
            } catch (NotFoundException e) {
                mAttributionExploreButton.setVisibility(View.GONE);
                mSpacer.setVisibility(View.VISIBLE);
            }
        }


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_preview, menu);
        menu.findItem(R.id.configure).setVisible(mSettingsIntent != null);
        menu.findItem(R.id.set_wallpaper).getActionView().setOnClickListener(
                this::setLiveWallpaper);
        return super.onCreateOptionsMenu(menu);
    }

    public void setLiveWallpaper(final View v) {
        if (mWallpaperManager.getWallpaperInfo() != null
            && mWallpaperManager.getWallpaperId(WallpaperManager.FLAG_LOCK) < 0) {
            // The lock screen does not have a distinct wallpaper and the current wallpaper is a
            // live wallpaper, so since we cannot preserve any static imagery on the lock screen,
            // set the live wallpaper directly without giving the user a destination option.
            try {
                setLiveWallpaper(v.getRootView().getWindowToken());
                setResult(RESULT_OK);
            } catch (RuntimeException e) {
                Log.w(LOG_TAG, "Failure setting wallpaper", e);
            }
            finish();
        } else {
            // Otherwise, prompt to either set on home or both home and lock screen.
            Context themedContext = new ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Settings);
            new AlertDialog.Builder(themedContext)
                    .setTitle(R.string.set_live_wallpaper)
                    .setAdapter(new WallpaperTargetAdapter(themedContext), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                setLiveWallpaper(v.getRootView().getWindowToken());
                                if (which == 1) {
                                    // "Home screen and lock screen"; clear the lock screen so it
                                    // shows through to the live wallpaper on home.
                                    mWallpaperManager.clear(WallpaperManager.FLAG_LOCK);
                                }
                                setResult(RESULT_OK);
                            } catch (RuntimeException|IOException e) {
                                Log.w(LOG_TAG, "Failure setting wallpaper", e);
                            }
                            finish();
                        }
                    })
                    .show();
        }
    }

    private void setLiveWallpaper(IBinder windowToken) {
        mWallpaperManager.setWallpaperComponent(mWallpaperIntent.getComponent());
        mWallpaperManager.setWallpaperOffsetSteps(0.5f, 0.0f);
        mWallpaperManager.setWallpaperOffsets(windowToken, 0.5f, 0.0f);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.configure) {
            startActivity(mSettingsIntent);
            return true;
        } else if (id == R.id.set_wallpaper) {
            setLiveWallpaper(getWindow().getDecorView());
            return true;
        } else if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mWallpaperConnection != null && mWallpaperConnection.mEngine != null) {
            try {
                mWallpaperConnection.mEngine.setVisibility(true);
            } catch (RemoteException e) {
                // Ignore
            }
        }
    }
    
    @Override
    public void onPause() {
        super.onPause();
        if (mWallpaperConnection != null && mWallpaperConnection.mEngine != null) {
            try {
                mWallpaperConnection.mEngine.setVisibility(false);
            } catch (RemoteException e) {
                // Ignore
            }
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        getWindow().getDecorView().post(new Runnable() {
            public void run() {
                if (!mWallpaperConnection.connect()) {
                    mWallpaperConnection = null;
                }
            }
        });
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        
        if (mWallpaperConnection != null) {
            mWallpaperConnection.disconnect();
        }
        mWallpaperConnection = null;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (mWallpaperConnection != null && mWallpaperConnection.mEngine != null) {
            MotionEvent dup = MotionEvent.obtainNoHistory(ev);
            try {
                mWallpaperConnection.mEngine.dispatchPointer(dup);
            } catch (RemoteException e) {
            }
        }
        
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            onUserInteraction();
        }
        boolean handled = getWindow().superDispatchTouchEvent(ev);
        if (!handled) {
            handled = onTouchEvent(ev);
        }

        if (!handled && mWallpaperConnection != null && mWallpaperConnection.mEngine != null) {
            int action = ev.getActionMasked();
            try {
                if (action == MotionEvent.ACTION_UP) {
                    mWallpaperConnection.mEngine.dispatchWallpaperCommand(
                            WallpaperManager.COMMAND_TAP,
                            (int) ev.getX(), (int) ev.getY(), 0, null);
                } else if (action == MotionEvent.ACTION_POINTER_UP) {
                    int pointerIndex = ev.getActionIndex();
                    mWallpaperConnection.mEngine.dispatchWallpaperCommand(
                            WallpaperManager.COMMAND_SECONDARY_TAP,
                            (int) ev.getX(pointerIndex), (int) ev.getY(pointerIndex), 0, null);
                }
            } catch (RemoteException e) {
            }
        }
        return handled;
    }
    
    class WallpaperConnection extends IWallpaperConnection.Stub implements ServiceConnection {
        final Intent mIntent;
        IWallpaperService mService;
        IWallpaperEngine mEngine;
        boolean mConnected;

        WallpaperConnection(Intent intent) {
            mIntent = intent;
        }

        public boolean connect() {
            synchronized (this) {
                if (!bindService(mIntent, this, Context.BIND_AUTO_CREATE)) {
                    return false;
                }

                mConnected = true;
                return true;
            }
        }

        public void disconnect() {
            synchronized (this) {
                mConnected = false;
                if (mEngine != null) {
                    try {
                        mEngine.destroy();
                    } catch (RemoteException e) {
                        // Ignore
                    }
                    mEngine = null;
                }
                try {
                    unbindService(this);
                } catch (IllegalArgumentException e) {
                    Log.w(LOG_TAG, "Can't unbind wallpaper service. "
                            + "It might have crashed, just ignoring.", e);
                }
                mService = null;
            }
        }
        
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (mWallpaperConnection == this) {
                mService = IWallpaperService.Stub.asInterface(service);
                try {
                    final View root = getWindow().getDecorView();
                    mService.attach(this, root.getWindowToken(),
                            LayoutParams.TYPE_APPLICATION_MEDIA,
                            true, root.getWidth(), root.getHeight(),
                            new Rect(0, 0, 0, 0));
                } catch (RemoteException e) {
                    Log.w(LOG_TAG, "Failed attaching wallpaper; clearing", e);
                }
            }
        }

        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            mEngine = null;
            if (mWallpaperConnection == this) {
                Log.w(LOG_TAG, "Wallpaper service gone: " + name);
            }
        }
        
        public void attachEngine(IWallpaperEngine engine) {
            synchronized (this) {
                if (mConnected) {
                    mEngine = engine;
                    try {
                        engine.setVisibility(true);
                    } catch (RemoteException e) {
                        // Ignore
                    }
                } else {
                    try {
                        engine.destroy();
                    } catch (RemoteException e) {
                        // Ignore
                    }
                }
            }
        }

        public ParcelFileDescriptor setWallpaper(String name) {
            return null;
        }

        @Override
        public void onWallpaperColorsChanged(WallpaperColors colors) throws RemoteException {

        }

        @Override
        public void engineShown(IWallpaperEngine engine) throws RemoteException {
            mLoading.post(() -> {
                mLoading.animate()
                        .alpha(0f)
                        .setDuration(220)
                        .setInterpolator(AnimationUtils.loadInterpolator(LiveWallpaperPreview.this,
                                android.R.interpolator.fast_out_linear_in))
                        .withEndAction(() -> mLoading.setVisibility(View.INVISIBLE));
            });
        }
    }

    private static class WallpaperTargetAdapter extends ArrayAdapter<CharSequence> {

        public WallpaperTargetAdapter(Context context) {
            super(context, R.layout.wallpaper_target_dialog_item,
                    context.getResources().getTextArray(R.array.which_wallpaper_options));
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView tv = (TextView) super.getView(position, convertView, parent);
            tv.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    position == 0 ? R.drawable.ic_home : R.drawable.ic_device, 0, 0, 0);
            return tv;
        }
    }
}
