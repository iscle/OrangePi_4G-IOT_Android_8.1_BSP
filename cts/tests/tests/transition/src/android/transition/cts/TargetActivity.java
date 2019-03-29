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
package android.transition.cts;

import static org.mockito.Mockito.mock;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.transition.Transition;
import android.transition.Transition.TransitionListener;
import android.transition.TransitionListenerAdapter;
import android.view.View;

import com.android.compatibility.common.util.transition.TrackingTransition;
import com.android.compatibility.common.util.transition.TrackingVisibility;

import java.util.concurrent.CountDownLatch;

public class TargetActivity extends Activity {
    public static final String EXTRA_LAYOUT_ID = "layoutId";
    public static final String EXTRA_USE_ANIMATOR = "useAnimator";
    public static final String EXTRA_EXCLUDE_ID = "excludeId";

    public TrackingVisibility enterTransition = new TrackingVisibility();
    public TrackingVisibility returnTransition = new TrackingVisibility();
    final TrackingTransition sharedElementEnterTransition = new TrackingTransition();
    final TrackingTransition sharedElementReturnTransition = new TrackingTransition();

    final TransitionListener enterListener = mock(TransitionListener.class);
    final TransitionListener returnListener = mock(TransitionListener.class);

    public static TargetActivity sLastCreated;

    public int startVisibility = -1;
    public int endVisibility = -1;
    public CountDownLatch transitionComplete;

    @Override
    public void onCreate(Bundle bundle){
        super.onCreate(bundle);
        Intent intent = getIntent();
        int layoutId = R.layout.transition_main;
        boolean useAnimator = false;
        int excludeId = 0;
        if (intent != null) {
            layoutId = intent.getIntExtra(EXTRA_LAYOUT_ID, layoutId);
            useAnimator = intent.getBooleanExtra(EXTRA_USE_ANIMATOR, false);
            excludeId = intent.getIntExtra(EXTRA_EXCLUDE_ID, 0);
        }

        setContentView(layoutId);

        if (useAnimator) {
            enterTransition = new TrackingVisibilityWithAnimator();
            returnTransition = new TrackingVisibilityWithAnimator();
        }

        if (excludeId != 0) {
            enterTransition.excludeTarget(excludeId, true);
            returnTransition.excludeTarget(excludeId, true);

            final View excludedView = findViewById(excludeId);
            transitionComplete = new CountDownLatch(1);

            TransitionListener excludeVisibilityCheck = new TransitionListenerAdapter() {
                @Override
                public void onTransitionStart(Transition transition) {
                    startVisibility = excludedView.getVisibility();
                }

                @Override
                public void onTransitionEnd(Transition transition) {
                    endVisibility = excludedView.getVisibility();
                    transitionComplete.countDown();
                }
            };
            enterTransition.addListener(excludeVisibilityCheck);
            returnTransition.addListener(excludeVisibilityCheck);
        }

        getWindow().setEnterTransition(enterTransition);
        getWindow().setReturnTransition(returnTransition);
        getWindow().setSharedElementEnterTransition(sharedElementEnterTransition);
        getWindow().setSharedElementReturnTransition(sharedElementReturnTransition);
        enterTransition.addListener(enterListener);
        returnTransition.addListener(returnListener);

        sLastCreated = this;
    }
}
