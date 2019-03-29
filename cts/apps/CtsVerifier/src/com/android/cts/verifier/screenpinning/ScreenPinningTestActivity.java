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
 * limitations under the License.
 */
package com.android.cts.verifier.screenpinning;

import android.app.ActivityManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

public class ScreenPinningTestActivity extends PassFailButtons.Activity {

    private static final String TAG = "ScreenPinningTestActivity";
    private static final String KEY_CURRENT_TEST = "keyCurrentTest";
    private static final long TASK_MODE_CHECK_DELAY = 200;
    private static final int MAX_TASK_MODE_CHECK_COUNT = 5;

    private Test[] mTests;
    private int mTestIndex;

    private ActivityManager mActivityManager;
    private Button mNextButton;
    private LinearLayout mInstructions;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_pinning);
        setPassFailButtonClickListeners();

        mTests = new Test[] {
            // Verify not already pinned.
            mCheckStartedUnpinned,
            // Enter pinning, verify pinned, try leaving and have the user exit.
            mCheckStartPinning,
            mCheckIsPinned,
            mCheckTryLeave,
            mCheckUnpin,
            // Enter pinning, verify pinned, and use APIs to exit.
            mCheckStartPinning,
            mCheckIsPinned,
            mCheckUnpinFromCode,
            // All done.
            mDone,
        };

        mActivityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        mInstructions = (LinearLayout) findViewById(R.id.instructions_list);

        mNextButton = (Button) findViewById(R.id.next_button);
        mNextButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if ((mTestIndex >= 0) && (mTestIndex < mTests.length)) {
                    mTests[mTestIndex].onNextClick();
                }
            }
        });

        // Don't allow pass until all tests complete.
        findViewById(R.id.pass_button).setVisibility(View.GONE);

        // Figure out if we are in a test or starting from the beginning.
        if (savedInstanceState != null && savedInstanceState.containsKey(KEY_CURRENT_TEST)) {
            mTestIndex = savedInstanceState.getInt(KEY_CURRENT_TEST);
        } else {
            mTestIndex = 0;
        }
        mTests[mTestIndex].run();
    };

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(KEY_CURRENT_TEST, mTestIndex);
    }

    @Override
    public void onBackPressed() {
        // Block back button so we can test screen pinning exit functionality.
        // Users can still leave by pressing fail (or when done the pass) button.
    }

    private void show(int id) {
        TextView tv = new TextView(this);
        tv.setPadding(10, 10, 10, 30);
        tv.setText(id);
        mInstructions.removeAllViews();
        mInstructions.addView(tv);
    }

    private void succeed() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTestIndex++;
                if (mTestIndex < mTests.length) {
                    mTests[mTestIndex].run();
                }
            }
        });
    }

    private void error(int errorId) {
        error(errorId, new Throwable());
    }

    private void error(final int errorId, final Throwable cause) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String error = getString(errorId);
                Log.d(TAG, error, cause);
                // No more instructions needed.
                findViewById(R.id.instructions_group).setVisibility(View.GONE);

                ((TextView) findViewById(R.id.error_text)).setText(error);
            }
        });
    }

    // Verify we don't start in screen pinning.
    private final Test mCheckStartedUnpinned = new Test(0) {
        public void run() {
            if (mActivityManager.isInLockTaskMode()) {
                error(R.string.error_screen_already_pinned);
            } else {
                succeed();
            }
        }
    };

    // Start screen pinning by having the user click next then confirm it for us.
    private final Test mCheckStartPinning = new Test(R.string.screen_pin_instructions) {
        protected void onNextClick() {
            startLockTask();
            succeed();
        }
    };

    // Click next and check that we got pinned.
    // Wait for the user to click next to verify that they got back from the prompt
    // successfully.
    private final Test mCheckIsPinned = new Test(R.string.screen_pin_check_pinned) {
        protected void onNextClick() {
            if (mActivityManager.isInLockTaskMode()) {
                succeed();
            } else {
                error(R.string.error_screen_pinning_did_not_start);
            }
        }
    };

    // Tell user to try to leave.
    private final Test mCheckTryLeave = new Test(R.string.screen_pin_no_exit) {
        protected void onNextClick() {
            if (mActivityManager.isInLockTaskMode()) {
                succeed();
            } else {
                error(R.string.error_screen_no_longer_pinned);
            }
        }
    };

    // Verify that the user unpinned and it worked.
    private final Test mCheckUnpin = new Test(R.string.screen_pin_exit) {
        protected void onNextClick() {
            if (!mActivityManager.isInLockTaskMode()) {
                succeed();
            } else {
                error(R.string.error_screen_pinning_did_not_exit);
            }
        }
    };

    // Unpin from code and check that it worked.
    private final Test mCheckUnpinFromCode = new Test(0) {
        protected void run() {
            if (!mActivityManager.isInLockTaskMode()) {
                error(R.string.error_screen_pinning_did_not_start);
                return;
            }
            stopLockTask();
            for (int retry = MAX_TASK_MODE_CHECK_COUNT; retry > 0; retry--) {
                try {
                    Thread.sleep(TASK_MODE_CHECK_DELAY);
                } catch (InterruptedException e) {
                }
                Log.d(TAG, "Check unpin ... " + retry);
                if (!mActivityManager.isInLockTaskMode()) {
                    succeed();
                    break;
                } else if (retry == 1) {
                    error(R.string.error_screen_pinning_couldnt_exit);
                }
            }
        };
    };

    private final Test mDone = new Test(R.string.screen_pinning_done) {
        @Override
        protected void run() {
            super.run();
            // On test completion, hide "next" button, and show "pass" button
            // instead.
            mNextButton.setVisibility(View.GONE);
            findViewById(R.id.pass_button).setVisibility(View.VISIBLE);
        };
    };

    private abstract class Test {
        private final int mResId;

        public Test(int showId) {
            mResId = showId;
        }

        protected void run() {
            showText();
        }

        public void showText() {
            if (mResId == 0) {
                return;
            }
            show(mResId);
        }

        protected void onNextClick() {
        }
    }

}
