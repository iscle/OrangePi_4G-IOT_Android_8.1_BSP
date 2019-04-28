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
 * limitations under the License
 */

package com.android.tv.settings;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.transition.Scene;
import android.transition.Slide;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

public abstract class TvSettingsActivity extends Activity {
    private static final String TAG = "TvSettingsActivity";

    private static final String SETTINGS_FRAGMENT_TAG =
            "com.android.tv.settings.MainSettings.SETTINGS_FRAGMENT";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {

            final ViewGroup root = findViewById(android.R.id.content);
            root.getViewTreeObserver().addOnPreDrawListener(
                    new ViewTreeObserver.OnPreDrawListener() {
                        @Override
                        public boolean onPreDraw() {
                            root.getViewTreeObserver().removeOnPreDrawListener(this);
                            final Scene scene = new Scene(root);
                            scene.setEnterAction(() -> {
                                if (getFragmentManager().isStateSaved()
                                        || getFragmentManager().isDestroyed()) {
                                    Log.d(TAG, "Got torn down before adding fragment");
                                    return;
                                }
                                final Fragment fragment = createSettingsFragment();
                                if (fragment != null) {
                                    getFragmentManager().beginTransaction()
                                            .add(android.R.id.content, fragment,
                                                    SETTINGS_FRAGMENT_TAG)
                                            .commitNow();
                                }
                            });

                            final Slide slide = new Slide(Gravity.END);
                            slide.setSlideFraction(
                                    getResources().getDimension(R.dimen.lb_settings_pane_width)
                                            / root.getWidth());
                            TransitionManager.go(scene, slide);

                            // Skip the current draw, there's nothing in it
                            return false;
                        }
                    });
        }
    }

    @Override
    public void finish() {
        final Fragment fragment = getFragmentManager().findFragmentByTag(SETTINGS_FRAGMENT_TAG);
        if (isResumed() && fragment != null) {
            final ViewGroup root = findViewById(android.R.id.content);
            final Scene scene = new Scene(root);
            scene.setEnterAction(() -> getFragmentManager().beginTransaction()
                    .remove(fragment)
                    .commitNow());
            final Slide slide = new Slide(Gravity.END);
            slide.setSlideFraction(
                    getResources().getDimension(R.dimen.lb_settings_pane_width) / root.getWidth());
            slide.addListener(new Transition.TransitionListener() {
                @Override
                public void onTransitionStart(Transition transition) {
                    getWindow().setDimAmount(0);
                }

                @Override
                public void onTransitionEnd(Transition transition) {
                    transition.removeListener(this);
                    TvSettingsActivity.super.finish();
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
            TransitionManager.go(scene, slide);
        } else {
            super.finish();
        }
    }

    protected abstract Fragment createSettingsFragment();
}
