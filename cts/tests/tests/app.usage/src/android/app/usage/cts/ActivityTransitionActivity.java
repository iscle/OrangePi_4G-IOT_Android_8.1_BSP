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

package android.app.usage.cts;

import android.app.Activity;
import android.app.SharedElementCallback;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.transition.ChangeBounds;
import android.transition.Explode;
import android.transition.Fade;
import android.transition.Transition;
import android.transition.Transition.TransitionListener;
import android.transition.TransitionListenerAdapter;
import android.view.View;

import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * A simple activity containing the start state for an Activity Transition
 */
public class ActivityTransitionActivity extends Activity {
    private static final long DURATION = 50;
    private static final long SHARED_ELEMENT_READY_DELAY = 50;
    public static final String LAYOUT_ID = "layoutId";
    public static final String TEST = "test";
    public static final String RESULT_RECEIVER = "resultReceiver";
    public static final String PAUSE_ON_RESTART = "pauseOnRestart";
    public static final String QUICK_FINISH = "quickFinish";
    public static final String ALLOW_OVERLAP = "allowOverlap";
    public static final String NO_RETURN_TRANSITION = "noReturnTransition";

    public static final int NO_TEST = 0;
    public static final int TEST_ARRIVE = 1;

    public static final String ARRIVE_COUNT = "numArrived";
    public static final String ARRIVE_ENTER_START_VISIBILITY = "arriveEnterStartVisibility";
    public static final String ARRIVE_ENTER_DELAY_VISIBILITY = "arriveEnterDelayVisibility";
    public static final String ARRIVE_ENTER_TIME_READY = "arriveEnterTimeReady";
    public static final String ARRIVE_ENTER_TIME = "arriveEnterTime";
    public static final String ARRIVE_RETURN_TIME_READY = "arriveReturnTimeReady";
    public static final String ARRIVE_RETURN_TIME = "arriveReturnTime";

    public static final String ENTER_VISIBILITY = "enterVisibility";
    public static final String RETURN_VISIBILITY = "returnVisibility";

    private int mLayoutId;
    private int mTest;
    private ResultReceiver mResultReceiver;
    private int mNumSharedElementsArrivedCalled = 0;
    private boolean mEntering = true;

    public int resultCode = 0;
    public Bundle result = new Bundle();
    public final OptionalVisibility exitVisibility = new OptionalVisibility();
    public final OptionalVisibility reenterVisibility = new OptionalVisibility();

    public boolean mPauseOnRestart;
    public boolean mQuickFinish;
    public boolean mAllowOverlap;
    public boolean mNoReturnTransition;

    public CountDownLatch returnLatch = new CountDownLatch(1);
    public CountDownLatch reenterLatch = new CountDownLatch(1);

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mLayoutId = 0;
        if (icicle != null) {
            mLayoutId =  icicle.getInt(LAYOUT_ID);
            mTest = icicle.getInt(TEST);
            mResultReceiver = icicle.getParcelable(RESULT_RECEIVER);
            mPauseOnRestart = icicle.getBoolean(PAUSE_ON_RESTART);
            mQuickFinish = icicle.getBoolean(QUICK_FINISH);
            mAllowOverlap = icicle.getBoolean(ALLOW_OVERLAP, true);
            mNoReturnTransition = icicle.getBoolean(NO_RETURN_TRANSITION);
        }

        if (mLayoutId == 0) {
            Intent intent = getIntent();
            mLayoutId = intent.getIntExtra(LAYOUT_ID, R.layout.start);
            mTest = intent.getIntExtra(TEST, 0);
            mResultReceiver = intent.getParcelableExtra(RESULT_RECEIVER);
            mPauseOnRestart = intent.getBooleanExtra(PAUSE_ON_RESTART, false);
            mQuickFinish = intent.getBooleanExtra(QUICK_FINISH, false);
            mAllowOverlap = intent.getBooleanExtra(ALLOW_OVERLAP, true);
            mNoReturnTransition = intent.getBooleanExtra(NO_RETURN_TRANSITION, false);
        }

        getWindow().setSharedElementEnterTransition(new ChangeBounds().setDuration(DURATION));
        getWindow().setSharedElementReturnTransition(new ChangeBounds().setDuration(DURATION));
        getWindow().setEnterTransition(createVisibilityTransition(true, ENTER_VISIBILITY));
        getWindow().setReturnTransition(createVisibilityTransition(true, RETURN_VISIBILITY));
        getWindow().setExitTransition(createVisibilityTransition(false, exitVisibility));
        getWindow().setReenterTransition(createVisibilityTransition(false, reenterVisibility));
        getWindow().getReenterTransition().addListener(new TransitionListenerAdapter() {
            @Override
            public void onTransitionEnd(Transition transition) {
                reenterLatch.countDown();
            }
        });

        setContentView(mLayoutId);
        getWindow().setAllowReturnTransitionOverlap(mAllowOverlap);
        if (mNoReturnTransition) {
            getWindow().setReturnTransition(null);
            getWindow().setSharedElementReturnTransition(null);
        }

        startTest();
    }

    private Transition createVisibilityTransition(boolean isExplode, OptionalVisibility visibility) {
        final Transition transition = isExplode ? new Explode() : new Fade();
        transition.setDuration(DURATION);
        transition.addListener(new VisibilityCheck(R.id.redSquare, visibility));
        return transition;
    }

    private Transition createVisibilityTransition(boolean isExplode, String propertyName) {
        final Transition transition = isExplode ? new Explode() : new Fade();
        transition.setDuration(DURATION);
        transition.addListener(new VisibilityCheck(R.id.redSquare, propertyName));
        return transition;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(LAYOUT_ID, mLayoutId);
        outState.putInt(TEST, mTest);
        outState.putParcelable(RESULT_RECEIVER, mResultReceiver);
        outState.putBoolean(PAUSE_ON_RESTART, mPauseOnRestart);
    }

    private void startTest() {
        if (mTest == TEST_ARRIVE) {
            setEnterSharedElementCallback(new SharedElementCallback() {
                @Override
                public void onSharedElementsArrived(List<String> sharedElementNames,
                        final List<View> sharedElements,
                        final OnSharedElementsReadyListener listener) {
                    mNumSharedElementsArrivedCalled++;
                    result.putInt(ARRIVE_COUNT, mNumSharedElementsArrivedCalled);
                    if (mEntering) {
                        result.putInt(ARRIVE_ENTER_START_VISIBILITY, sharedElements.get(0).getVisibility());
                        result.putLong(ARRIVE_ENTER_TIME, SystemClock.uptimeMillis());
                    } else {
                        result.putLong(ARRIVE_RETURN_TIME, SystemClock.uptimeMillis());
                    }

                    getWindow().getDecorView().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (mEntering) {
                                result.putInt(ARRIVE_ENTER_DELAY_VISIBILITY,
                                        sharedElements.get(0).getVisibility());
                                result.putLong(ARRIVE_ENTER_TIME_READY, SystemClock.uptimeMillis());
                            } else {
                                result.putLong(ARRIVE_RETURN_TIME_READY,
                                        SystemClock.uptimeMillis());
                                mResultReceiver.send(RESULT_OK, result);
                            }
                            listener.onSharedElementsReady();
                        }
                    }, SHARED_ELEMENT_READY_DELAY);
                }
            });
        }
        getWindow().getEnterTransition().addListener(new TransitionListener() {
            @Override
            public void onTransitionStart(Transition transition) {
            }

            @Override
            public void onTransitionEnd(Transition transition) {
                mEntering = false;
                Intent intent = new Intent();
                intent.putExtra("Extra", new ExtraData("result"));
                setResult(RESULT_OK, intent);
                getWindow().getDecorView().post(new Runnable() {
                    @Override
                    public void run() {
                        finishAfterTransition();
                        if (mQuickFinish) {
                            finish();
                        }
                    }
                });
            }

            @Override
            public void onTransitionCancel(Transition transition) {
            }

            @Override
            public void onTransitionPause(Transition transition) {
            }

            @Override
            public void onTransitionResume(Transition transition) {
            }
        });
    }

    @Override
    public void onActivityReenter(int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            ExtraData extraData = data.getParcelableExtra("Extra");
            if (!"result".equals(extraData.data)) {
                throw new RuntimeException("Incorrect returned intent data in onActivityReenter");
            }
        }
        super.onActivityReenter(resultCode, data);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        this.resultCode = resultCode;
        returnLatch.countDown();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (mPauseOnRestart) {
            postponeEnterTransition();
            getWindow().getDecorView().postDelayed(new Runnable() {
                @Override
                public void run() {
                    startPostponedEnterTransition();
                }
            }, 500);
        }
    }

    private class VisibilityCheck extends TransitionListenerAdapter {
        private final int mViewId;
        private final OptionalVisibility mVisibility;
        private final String mPropertyName;

        public VisibilityCheck(int viewId, OptionalVisibility visibility) {
            mViewId = viewId;
            mVisibility = visibility;
            mPropertyName = null;
        }

        public VisibilityCheck(int viewId, String propertyName) {
            mViewId = viewId;
            mVisibility = null;
            mPropertyName = propertyName;
        }

        @Override
        public void onTransitionEnd(Transition transition) {
            final View view = findViewById(mViewId);
            if (mPropertyName != null) {
                if (mResultReceiver != null) {
                    Bundle data = new Bundle();
                    data.putInt(mPropertyName, view.getVisibility());
                    mResultReceiver.send(RESULT_OK, data);
                }
            } else {
                mVisibility.set(view.getVisibility());
            }
        }
    }

    public static class OptionalVisibility {
        private int mVisibility = -1;

        public void set(int visibility) {
            mVisibility = visibility;
        }

        public int get() {
            return mVisibility;
        }

        public boolean isSet() {
            return mVisibility != -1;
        }
    }

    /**
     * Used to test the class loader of the returned intent.
     */
    public static class ExtraData implements Parcelable {
        public final String data;

        public ExtraData(String data) {
            this.data = data;
        }

        protected ExtraData(Parcel in) {
            this.data = in.readString();
        }

        public static final Creator<ExtraData> CREATOR = new Creator<ExtraData>() {
            @Override
            public ExtraData createFromParcel(Parcel in) {
                return new ExtraData(in);
            }

            @Override
            public ExtraData[] newArray(int size) {
                return new ExtraData[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(data);
        }
    }
}
