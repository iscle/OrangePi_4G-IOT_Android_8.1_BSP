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

package com.android.cts.verifier;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.widget.ProgressBar;

/**
 * Can be used to show time outs for events. A progress bar will be displayed to the user.
 * On calling start, it will start filling up.
 */
public class TimerProgressBar extends ProgressBar {
  public TimerProgressBar(Context context) {
    super(context);
    setHandler(context);
  }

  public TimerProgressBar(Context context, AttributeSet attrs) {
    super(context, attrs);
    setHandler(context);
  }

  public TimerProgressBar(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    setHandler(context);
  }

  @TargetApi(21)
  public TimerProgressBar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    setHandler(context);
  }

  private void setHandler(Context context) {
    mHandler = new Handler(context.getMainLooper());
  }

  private Handler mHandler;
  private TimerExpiredCallback mTimerExpiredCallback;
  private long mStartTime;
  private long mDuration;
  private long mStepSize;
  private boolean mForceComplete;

  private Runnable mProgressCallback = new Runnable() {
    @Override
    public void run() {
      if (mForceComplete) {
        TimerProgressBar.this.setProgress(TimerProgressBar.this.getMax());
        return;
      }

      long currentTime = SystemClock.elapsedRealtime();
      int progress = (int) ((currentTime - mStartTime) / mStepSize);
      progress = Math.min(progress, TimerProgressBar.this.getMax());
      TimerProgressBar.this.setProgress(progress);

      if (mStartTime + mDuration > currentTime) {
        mHandler.postDelayed(this, mStepSize);
      } else {
        if (mTimerExpiredCallback != null) {
          mTimerExpiredCallback.onTimerExpired();
        }
      }
    }
  };

  public void start(long duration, long stepSize) {
    start(duration, stepSize, null);
  }

  /**
   * Start filling up the progress bar.
   *
   * @param duration Time in milliseconds the progress bar takes to fill up completely
   * @param stepSize Time in milliseconds between consecutive updates to progress bar's progress
   * @param callback Callback that should be executed after the progress bar is filled completely (i.e. timer expires)
   */
  public void start(long duration, long stepSize, TimerExpiredCallback callback) {
    mDuration = duration;
    mStepSize = stepSize;
    mStartTime = SystemClock.elapsedRealtime();
    mForceComplete = false;
    mTimerExpiredCallback = callback;
    this.setMax((int) (duration / stepSize));
    this.setProgress(0);
    mHandler.post(mProgressCallback);
  }

  /**
   * Fill the progress bar completely. Timer expired callback won't be executed.
   */
  public void forceComplete() {
    mForceComplete = true;
  }

  public interface TimerExpiredCallback {
    void onTimerExpired();
  }

}
