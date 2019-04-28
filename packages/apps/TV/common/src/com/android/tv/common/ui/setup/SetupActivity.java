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

package com.android.tv.common.ui.setup;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.transition.Transition;
import android.transition.TransitionInflater;
import android.view.View;
import android.view.ViewTreeObserver.OnPreDrawListener;

import com.android.tv.common.R;
import com.android.tv.common.WeakHandler;
import com.android.tv.common.ui.setup.animation.SetupAnimationHelper;

/**
 * Setup activity for onboarding screens or TIS.
 *
 * <p>The inherited class should add theme {@code Theme.Setup.GuidedStep} to its definition in
 * AndroidManifest.xml.
 */
public abstract class SetupActivity extends Activity implements OnActionClickListener {
    private static final int MSG_EXECUTE_ACTION = 1;

    private boolean mShowInitialFragment = true;
    private long mFragmentTransitionDuration;
    private final Handler mHandler = new SetupActivityHandler(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SetupAnimationHelper.initialize(this);
        setContentView(R.layout.activity_setup);
        mFragmentTransitionDuration = getResources().getInteger(
                R.integer.setup_fragment_transition_duration);
        // Show initial fragment only when the saved state is not restored, because the last
        // fragment is restored if savesInstanceState is not null.
        if (savedInstanceState == null) {
            // This is the workaround to show the first fragment with delay to show the fragment
            // enter transition. See http://b/26255145
            getWindow().getDecorView().getViewTreeObserver().addOnPreDrawListener(
                    new OnPreDrawListener() {
                        @Override
                        public boolean onPreDraw() {
                            getWindow().getDecorView().getViewTreeObserver()
                                    .removeOnPreDrawListener(this);
                            showInitialFragment();
                            return true;
                        }
                    });
        } else {
            mShowInitialFragment = false;
        }
    }

    /**
     * The inherited class should provide the initial fragment to show.
     *
     * <p>If this method returns {@code null} during {@link #onCreate}, then call
     * {@link #showInitialFragment} explicitly later with non null initial fragment.
     */
    protected abstract Fragment onCreateInitialFragment();

    /**
     * Shows the initial fragment.
     *
     * <p>The inherited class can call this method later explicitly if it doesn't want the initial
     * fragment to be shown in onCreate().
     */
    protected void showInitialFragment() {
        if (!mShowInitialFragment) {
            return;
        }
        Fragment fragment = onCreateInitialFragment();
        if (fragment != null) {
            showFragment(fragment, false);
            mShowInitialFragment = false;
        }
    }

    /**
     * Shows the given fragment.
     */
    protected FragmentTransaction showFragment(Fragment fragment, boolean addToBackStack) {
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        if (fragment instanceof SetupFragment) {
            int[] sharedElements = ((SetupFragment) fragment).getSharedElementIds();
            if (sharedElements != null && sharedElements.length > 0) {
                Transition sharedTransition = TransitionInflater.from(this)
                        .inflateTransition(R.transition.transition_action_background);
                sharedTransition.setDuration(getSharedElementTransitionDuration());
                SetupAnimationHelper.applyAnimationTimeScale(sharedTransition);
                fragment.setSharedElementEnterTransition(sharedTransition);
                fragment.setSharedElementReturnTransition(sharedTransition);
                for (int id : sharedElements) {
                    View sharedView = findViewById(id);
                    if (sharedView != null) {
                        ft.addSharedElement(sharedView, sharedView.getTransitionName());
                    }
                }
            }
        }
        String tag = fragment.getClass().getCanonicalName();
        if (addToBackStack) {
            ft.addToBackStack(tag);
        }
        ft.replace(R.id.fragment_container, fragment, tag).commit();

        return ft;
    }

    @Override
    public boolean onActionClick(String category, int actionId, Bundle params) {
        if (mHandler.hasMessages(MSG_EXECUTE_ACTION)) {
            return false;
        }
        return executeAction(category, actionId, params);
    }

    protected void executeActionWithDelay(Runnable action, int delayMs) {
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_EXECUTE_ACTION, action), delayMs);
    }

    /**
     * Override this method if the inherited class wants to handle the action.
     * <p>
     * The override method should return {@code true} if the action is handled, otherwise
     * {@code false}.
     */
    protected boolean executeAction(String category, int actionId, Bundle params) {
        return false;
    }

    /**
     * Returns the duration of the shared element transition.
     *
     * <p>It's (exit transition) + (delayed animation) + (enter transition).
     */
    private long getSharedElementTransitionDuration() {
        return (mFragmentTransitionDuration + SetupAnimationHelper.DELAY_BETWEEN_SIBLINGS_MS) * 2;
    }

    private static class SetupActivityHandler extends WeakHandler<SetupActivity> {
        SetupActivityHandler(SetupActivity activity) {
            // Should run on main thread because onAc3SupportChanged will be called on main thread.
            super(Looper.getMainLooper(), activity);
        }

        @Override
        protected void handleMessage(Message msg, @NonNull SetupActivity activity) {
            if (msg.what == MSG_EXECUTE_ACTION) {
                ((Runnable) msg.obj).run();
            }
        }
    }
}
