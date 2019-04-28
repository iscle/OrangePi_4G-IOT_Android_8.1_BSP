/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.tv.settings.name.setup;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.annotation.Nullable;
import android.app.Activity;
import android.os.Bundle;
import android.support.v17.leanback.app.GuidedStepFragment;

import com.android.tv.settings.R;
import com.android.tv.settings.name.DeviceNameSetFragment;

/**
 * Entry point for the device name flow. This will be invoked during the tv setup process.
 * This activity needs to have transparent background to show the background drawable of the
 * setup flow.
 */
public class DeviceNameFlowStartActivity extends Activity {
    private static final String EXTRA_MOVING_FORWARD = "movingForward";
    private boolean mResultOk = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            GuidedStepFragment.addAsRoot(this, DeviceNameSetFragment.newInstance(),
                    android.R.id.content);

            // Because our fragment transition is added as root, the animation is dependent
            // on the Activity transition. We must run the animation at runtime in order to
            // enter from the correct side.
            boolean movingForward = getIntent().getExtras().getBoolean(EXTRA_MOVING_FORWARD, true);
            Animator animator = movingForward
                    ? AnimatorInflater.loadAnimator(this, R.anim.setup_fragment_open_in)
                    : AnimatorInflater.loadAnimator(this, R.anim.setup_fragment_close_in);
            animator.setTarget(getWindow().getDecorView());
            animator.start();
        }
    }

    @Override
    public void finish() {
        Animator animator = mResultOk
                ? AnimatorInflater.loadAnimator(this, R.anim.setup_fragment_open_out)
                : AnimatorInflater.loadAnimator(this, R.anim.setup_fragment_close_out);
        animator.setTarget(getWindow().getDecorView());
        animator.addListener(new Animator.AnimatorListener() {

            @Override
            public void onAnimationStart(Animator animation) {}

            @Override
            public void onAnimationRepeat(Animator animation) {}

            @Override
            public void onAnimationEnd(Animator animation) {
                doFinish();
            }

            @Override
            public void onAnimationCancel(Animator animation) {}
        });
        animator.start();
    }

    private void doFinish() {
        super.finish();
    }

    /**
     * Records activity result is OK so we can finish the activity with the correct animation.
     */
    public void setResultOk(boolean resultOk) {
        mResultOk = resultOk;
    }
}
