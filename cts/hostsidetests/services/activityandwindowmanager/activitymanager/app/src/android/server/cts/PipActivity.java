/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.server.cts;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.PictureInPictureParams;
import android.content.res.Configuration;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.util.Rational;
import android.view.WindowManager;

public class PipActivity extends AbstractLifecycleLogActivity {

    private static final String TAG = "PipActivity";

    // Intent action that this activity dynamically registers to enter picture-in-picture
    private static final String ACTION_ENTER_PIP = "android.server.cts.PipActivity.enter_pip";
    // Intent action that this activity dynamically registers to move itself to the back
    private static final String ACTION_MOVE_TO_BACK = "android.server.cts.PipActivity.move_to_back";
    // Intent action that this activity dynamically registers to expand itself.
    // If EXTRA_SET_ASPECT_RATIO_WITH_DELAY is set, it will also attempt to apply the aspect ratio
    // after a short delay.
    private static final String ACTION_EXPAND_PIP = "android.server.cts.PipActivity.expand_pip";
    // Intent action that this activity dynamically registers to set requested orientation.
    // Will apply the oriention to the value set in the EXTRA_FIXED_ORIENTATION extra.
    private static final String ACTION_SET_REQUESTED_ORIENTATION =
            "android.server.cts.PipActivity.set_requested_orientation";
    // Intent action that will finish this activity
    private static final String ACTION_FINISH = "android.server.cts.PipActivity.finish";

    // Sets the fixed orientation (can be one of {@link ActivityInfo.ScreenOrientation}
    private static final String EXTRA_FIXED_ORIENTATION = "fixed_orientation";
    // Calls enterPictureInPicture() on creation
    private static final String EXTRA_ENTER_PIP = "enter_pip";
    // Used with EXTRA_AUTO_ENTER_PIP, value specifies the aspect ratio to enter PIP with
    private static final String EXTRA_ENTER_PIP_ASPECT_RATIO_NUMERATOR =
            "enter_pip_aspect_ratio_numerator";
    // Used with EXTRA_AUTO_ENTER_PIP, value specifies the aspect ratio to enter PIP with
    private static final String EXTRA_ENTER_PIP_ASPECT_RATIO_DENOMINATOR =
            "enter_pip_aspect_ratio_denominator";
    // Calls setPictureInPictureAspectRatio with the aspect ratio specified in the value
    private static final String EXTRA_SET_ASPECT_RATIO_NUMERATOR = "set_aspect_ratio_numerator";
    // Calls setPictureInPictureAspectRatio with the aspect ratio specified in the value
    private static final String EXTRA_SET_ASPECT_RATIO_DENOMINATOR = "set_aspect_ratio_denominator";
    // Calls setPictureInPictureAspectRatio with the aspect ratio specified in the value with a
    // fixed delay
    private static final String EXTRA_SET_ASPECT_RATIO_WITH_DELAY_NUMERATOR =
            "set_aspect_ratio_with_delay_numerator";
    // Calls setPictureInPictureAspectRatio with the aspect ratio specified in the value with a
    // fixed delay
    private static final String EXTRA_SET_ASPECT_RATIO_WITH_DELAY_DENOMINATOR =
            "set_aspect_ratio_with_delay_denominator";
    // Adds a click listener to finish this activity when it is clicked
    private static final String EXTRA_TAP_TO_FINISH = "tap_to_finish";
    // Calls requestAutoEnterPictureInPicture() with the value provided
    private static final String EXTRA_ENTER_PIP_ON_PAUSE = "enter_pip_on_pause";
    // Starts the activity (component name) provided by the value at the end of onCreate
    private static final String EXTRA_START_ACTIVITY = "start_activity";
    // Finishes the activity at the end of onResume (after EXTRA_START_ACTIVITY is handled)
    private static final String EXTRA_FINISH_SELF_ON_RESUME = "finish_self_on_resume";
    // Calls enterPictureInPicture() again after onPictureInPictureModeChanged(false) is called
    private static final String EXTRA_REENTER_PIP_ON_EXIT = "reenter_pip_on_exit";
    // Shows this activity over the keyguard
    private static final String EXTRA_SHOW_OVER_KEYGUARD = "show_over_keyguard";
    // Adds an assertion that we do not ever get onStop() before we enter picture in picture
    private static final String EXTRA_ASSERT_NO_ON_STOP_BEFORE_PIP = "assert_no_on_stop_before_pip";
    // The amount to delay to artificially introduce in onPause() (before EXTRA_ENTER_PIP_ON_PAUSE
    // is processed)
    private static final String EXTRA_ON_PAUSE_DELAY = "on_pause_delay";

    private boolean mEnteredPictureInPicture;

    private Handler mHandler = new Handler();
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                switch (intent.getAction()) {
                    case ACTION_ENTER_PIP:
                        enterPictureInPictureMode();
                        break;
                    case ACTION_MOVE_TO_BACK:
                        moveTaskToBack(false /* nonRoot */);
                        break;
                    case ACTION_EXPAND_PIP:
                        // Trigger the activity to expand
                        Intent startIntent = new Intent(PipActivity.this, PipActivity.class);
                        startIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(startIntent);

                        if (intent.hasExtra(EXTRA_SET_ASPECT_RATIO_WITH_DELAY_NUMERATOR)
                                && intent.hasExtra(EXTRA_SET_ASPECT_RATIO_WITH_DELAY_DENOMINATOR)) {
                            // Ugly, but required to wait for the startActivity to actually start
                            // the activity...
                            mHandler.postDelayed(() -> {
                                final PictureInPictureParams.Builder builder =
                                        new PictureInPictureParams.Builder();
                                builder.setAspectRatio(getAspectRatio(intent,
                                        EXTRA_SET_ASPECT_RATIO_WITH_DELAY_NUMERATOR,
                                        EXTRA_SET_ASPECT_RATIO_WITH_DELAY_DENOMINATOR));
                                setPictureInPictureParams(builder.build());
                            }, 100);
                        }
                        break;
                    case ACTION_SET_REQUESTED_ORIENTATION:
                        setRequestedOrientation(Integer.parseInt(intent.getStringExtra(
                                EXTRA_FIXED_ORIENTATION)));
                        break;
                    case ACTION_FINISH:
                        finish();
                        break;
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the fixed orientation if requested
        if (getIntent().hasExtra(EXTRA_FIXED_ORIENTATION)) {
            final int ori = Integer.parseInt(getIntent().getStringExtra(EXTRA_FIXED_ORIENTATION));
            setRequestedOrientation(ori);
        }

        // Set the window flag to show over the keyguard
        if (getIntent().hasExtra(EXTRA_SHOW_OVER_KEYGUARD)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }

        // Enter picture in picture with the given aspect ratio if provided
        if (getIntent().hasExtra(EXTRA_ENTER_PIP)) {
            if (getIntent().hasExtra(EXTRA_ENTER_PIP_ASPECT_RATIO_NUMERATOR)
                    && getIntent().hasExtra(EXTRA_ENTER_PIP_ASPECT_RATIO_DENOMINATOR)) {
                try {
                    final PictureInPictureParams.Builder builder =
                            new PictureInPictureParams.Builder();
                    builder.setAspectRatio(getAspectRatio(getIntent(),
                            EXTRA_ENTER_PIP_ASPECT_RATIO_NUMERATOR,
                            EXTRA_ENTER_PIP_ASPECT_RATIO_DENOMINATOR));
                    enterPictureInPictureMode(builder.build());
                } catch (Exception e) {
                    // This call can fail intentionally if the aspect ratio is too extreme
                }
            } else {
                enterPictureInPictureMode(new PictureInPictureParams.Builder().build());
            }
        }

        // We need to wait for either enterPictureInPicture() or requestAutoEnterPictureInPicture()
        // to be called before setting the aspect ratio
        if (getIntent().hasExtra(EXTRA_SET_ASPECT_RATIO_NUMERATOR)
                && getIntent().hasExtra(EXTRA_SET_ASPECT_RATIO_DENOMINATOR)) {
            final PictureInPictureParams.Builder builder =
                    new PictureInPictureParams.Builder();
            builder.setAspectRatio(getAspectRatio(getIntent(),
                    EXTRA_SET_ASPECT_RATIO_NUMERATOR, EXTRA_SET_ASPECT_RATIO_DENOMINATOR));
            try {
                setPictureInPictureParams(builder.build());
            } catch (Exception e) {
                // This call can fail intentionally if the aspect ratio is too extreme
            }
        }

        // Enable tap to finish if necessary
        if (getIntent().hasExtra(EXTRA_TAP_TO_FINISH)) {
            setContentView(R.layout.tap_to_finish_pip_layout);
            findViewById(R.id.content).setOnClickListener(v -> {
                finish();
            });
        }

        // Launch a new activity if requested
        String launchActivityComponent = getIntent().getStringExtra(EXTRA_START_ACTIVITY);
        if (launchActivityComponent != null) {
            Intent launchIntent = new Intent();
            launchIntent.setComponent(ComponentName.unflattenFromString(launchActivityComponent));
            startActivity(launchIntent);
        }

        // Register the broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_ENTER_PIP);
        filter.addAction(ACTION_MOVE_TO_BACK);
        filter.addAction(ACTION_EXPAND_PIP);
        filter.addAction(ACTION_SET_REQUESTED_ORIENTATION);
        filter.addAction(ACTION_FINISH);
        registerReceiver(mReceiver, filter);

        // Dump applied display metrics.
        Configuration config = getResources().getConfiguration();
        dumpDisplaySize(config);
        dumpConfiguration(config);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Finish self if requested
        if (getIntent().hasExtra(EXTRA_FINISH_SELF_ON_RESUME)) {
            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Pause if requested
        if (getIntent().hasExtra(EXTRA_ON_PAUSE_DELAY)) {
            SystemClock.sleep(Long.valueOf(getIntent().getStringExtra(EXTRA_ON_PAUSE_DELAY)));
        }

        // Enter PIP on move to background
        if (getIntent().hasExtra(EXTRA_ENTER_PIP_ON_PAUSE)) {
            enterPictureInPictureMode(new PictureInPictureParams.Builder().build());
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (getIntent().hasExtra(EXTRA_ASSERT_NO_ON_STOP_BEFORE_PIP) && !mEnteredPictureInPicture) {
            Log.w(TAG, "Unexpected onStop() called before entering picture-in-picture");
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterReceiver(mReceiver);
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);

        // Fail early if the activity state does not match the dispatched state
        if (isInPictureInPictureMode() != isInPictureInPictureMode) {
            Log.w(TAG, "Received onPictureInPictureModeChanged mode=" + isInPictureInPictureMode
                    + " activityState=" + isInPictureInPictureMode());
            finish();
        }

        // Mark that we've entered picture-in-picture so that we can stop checking for
        // EXTRA_ASSERT_NO_ON_STOP_BEFORE_PIP
        if (isInPictureInPictureMode) {
            mEnteredPictureInPicture = true;
        }

        if (!isInPictureInPictureMode && getIntent().hasExtra(EXTRA_REENTER_PIP_ON_EXIT)) {
            // This call to re-enter PIP can happen too quickly (host side tests can have difficulty
            // checking that the stacks ever changed). Therefor, we need to delay here slightly to
            // allow the tests to verify that the stacks have changed before re-entering.
            mHandler.postDelayed(() -> {
                enterPictureInPictureMode(new PictureInPictureParams.Builder().build());
            }, 1000);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        dumpDisplaySize(newConfig);
        dumpConfiguration(newConfig);
    }

    /**
     * Launches a new instance of the PipActivity directly into the pinned stack.
     */
    static void launchActivityIntoPinnedStack(Activity caller, Rect bounds) {
        final Intent intent = new Intent(caller, PipActivity.class);
        intent.setFlags(FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(EXTRA_ASSERT_NO_ON_STOP_BEFORE_PIP, "true");

        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchBounds(bounds);
        options.setLaunchStackId(4 /* ActivityManager.StackId.PINNED_STACK_ID */);
        caller.startActivity(intent, options.toBundle());
    }

    /**
     * Launches a new instance of the PipActivity in the same task that will automatically enter
     * PiP.
     */
    static void launchEnterPipActivity(Activity caller) {
        final Intent intent = new Intent(caller, PipActivity.class);
        intent.putExtra(EXTRA_ENTER_PIP, "true");
        intent.putExtra(EXTRA_ASSERT_NO_ON_STOP_BEFORE_PIP, "true");
        caller.startActivity(intent);
    }

    @Override
    protected String getTag() {
        return TAG;
    }

    /**
     * @return a {@link Rational} aspect ratio from the given intent and extras.
     */
    private Rational getAspectRatio(Intent intent, String extraNum, String extraDenom) {
        return new Rational(
                Integer.valueOf(intent.getStringExtra(extraNum)),
                Integer.valueOf(intent.getStringExtra(extraDenom)));
    }
}
