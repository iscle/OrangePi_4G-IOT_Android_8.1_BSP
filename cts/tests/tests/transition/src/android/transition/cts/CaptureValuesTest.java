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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.transition.AutoTransition;
import android.transition.ChangeBounds;
import android.transition.ChangeClipBounds;
import android.transition.ChangeImageTransform;
import android.transition.ChangeScroll;
import android.transition.ChangeTransform;
import android.transition.Explode;
import android.transition.Fade;
import android.transition.Slide;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.transition.TransitionValues;
import android.util.ArrayMap;

import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class CaptureValuesTest extends BaseTransitionTest {
    private ArrayMap<Class<?>, Boolean> mStartCaptured = new ArrayMap<>();
    private ArrayMap<Class<?>, Boolean> mEndCaptured = new ArrayMap<>();

    /**
     * Ensures captureValues is called on all Transitions and the proper values are captured.
     */
    @Test
    public void testCaptureValues() throws Throwable {
        final TransitionSetCaptureValues set = new TransitionSetCaptureValues();
        set.addTransition(new FadeCaptureValues());
        set.addTransition(new ChangeBoundsCaptureValues());
        set.addTransition(new ChangeImageTransformCaptureValues());
        set.addTransition(new ChangeTransformCaptureValues());
        set.addTransition(new AutoTransitionCaptureValues());
        set.addTransition(new ChangeClipBoundsCaptureValues());
        set.addTransition(new ChangeScrollCaptureValues());
        set.addTransition(new ExplodeCaptureValues());
        set.addTransition(new SlideCaptureValues());

        enterScene(R.layout.scene11);
        set.addTarget(mActivity.findViewById(R.id.redSquare));
        mTransition = set;
        resetListener();
        mActivityRule.runOnUiThread(() -> {
            TransitionManager.beginDelayedTransition(mSceneRoot, set);
            mSceneRoot.invalidate();
        });
        waitForStart();
        // no transition needs to run, but they should have all captured values.

        for (int i = 0; i < set.getTransitionCount(); i++) {
            Transition transition = set.getTransitionAt(i);
            String className = transition.getClass().getSuperclass().getSimpleName().toString();
            assertNotNull("captureStartValues not called for " + className,
                    mStartCaptured.get(transition.getClass()));
            assertNotNull("captureEndValues not called for " + className,
                    mEndCaptured.get(transition.getClass()));
        }
        assertNotNull(mStartCaptured.get(set.getClass()));
        assertNotNull(mEndCaptured.get(set.getClass()));
    }

    private void verifyCapturedValues(Transition transition, TransitionValues values,
            boolean isStart) {
        String[] properties = transition.getTransitionProperties();
        if (transition instanceof TransitionSet) {
            assertNull(properties);
        } else {
            String className = transition.getClass().getSuperclass().getSimpleName().toString();
            assertNotNull(className + " should have non-null transition properties", properties);
            assertTrue(properties.length > 0);

            for (String property : properties) {
                assertTrue(className + " should have written to property " + property,
                        values.values.keySet().contains(property));
            }
        }
        if (isStart) {
            mStartCaptured.put(transition.getClass(), true);
        } else {
            mEndCaptured.put(transition.getClass(), true);
        }
    }

    public class FadeCaptureValues extends Fade {
        @Override
        public void captureStartValues(TransitionValues transitionValues) {
            super.captureStartValues(transitionValues);
            verifyCapturedValues(this, transitionValues, true);
        }

        @Override
        public void captureEndValues(TransitionValues transitionValues) {
            super.captureEndValues(transitionValues);
            verifyCapturedValues(this, transitionValues, false);
        }
    }

    public class ChangeBoundsCaptureValues extends ChangeBounds {
        public ChangeBoundsCaptureValues() {
            super();
            setResizeClip(true);
            setReparent(true);
        }
        @Override
        public void captureStartValues(TransitionValues transitionValues) {
            super.captureStartValues(transitionValues);
            verifyCapturedValues(this, transitionValues, true);
        }

        @Override
        public void captureEndValues(TransitionValues transitionValues) {
            super.captureEndValues(transitionValues);
            verifyCapturedValues(this, transitionValues, false);
        }
    }

    public class ChangeImageTransformCaptureValues extends ChangeImageTransform {
        @Override
        public void captureStartValues(TransitionValues transitionValues) {
            super.captureStartValues(transitionValues);
            verifyCapturedValues(this, transitionValues, true);
        }

        @Override
        public void captureEndValues(TransitionValues transitionValues) {
            super.captureEndValues(transitionValues);
            verifyCapturedValues(this, transitionValues, false);
        }
    }

    public class ChangeTransformCaptureValues extends ChangeTransform {
        @Override
        public void captureStartValues(TransitionValues transitionValues) {
            super.captureStartValues(transitionValues);
            verifyCapturedValues(this, transitionValues, true);
        }

        @Override
        public void captureEndValues(TransitionValues transitionValues) {
            super.captureEndValues(transitionValues);
            verifyCapturedValues(this, transitionValues, false);
        }
    }

    public class AutoTransitionCaptureValues extends AutoTransition {
        @Override
        public void captureStartValues(TransitionValues transitionValues) {
            super.captureStartValues(transitionValues);
            verifyCapturedValues(this, transitionValues, true);
        }

        @Override
        public void captureEndValues(TransitionValues transitionValues) {
            super.captureEndValues(transitionValues);
            verifyCapturedValues(this, transitionValues, false);
        }
    }

    public class ChangeClipBoundsCaptureValues extends ChangeClipBounds {
        @Override
        public void captureStartValues(TransitionValues transitionValues) {
            super.captureStartValues(transitionValues);
            verifyCapturedValues(this, transitionValues, true);
        }

        @Override
        public void captureEndValues(TransitionValues transitionValues) {
            super.captureEndValues(transitionValues);
            verifyCapturedValues(this, transitionValues, false);
        }
    }

    public class ChangeScrollCaptureValues extends ChangeScroll {
        @Override
        public void captureStartValues(TransitionValues transitionValues) {
            super.captureStartValues(transitionValues);
            verifyCapturedValues(this, transitionValues, true);
        }

        @Override
        public void captureEndValues(TransitionValues transitionValues) {
            super.captureEndValues(transitionValues);
            verifyCapturedValues(this, transitionValues, false);
        }
    }

    public class ExplodeCaptureValues extends Explode {
        @Override
        public void captureStartValues(TransitionValues transitionValues) {
            super.captureStartValues(transitionValues);
            verifyCapturedValues(this, transitionValues, true);
        }

        @Override
        public void captureEndValues(TransitionValues transitionValues) {
            super.captureEndValues(transitionValues);
            verifyCapturedValues(this, transitionValues, false);
        }
    }

    public class SlideCaptureValues extends Slide {
        @Override
        public void captureStartValues(TransitionValues transitionValues) {
            super.captureStartValues(transitionValues);
            verifyCapturedValues(this, transitionValues, true);
        }

        @Override
        public void captureEndValues(TransitionValues transitionValues) {
            super.captureEndValues(transitionValues);
            verifyCapturedValues(this, transitionValues, false);
        }
    }
    public class TransitionSetCaptureValues extends TransitionSet {
        @Override
        public void captureStartValues(TransitionValues transitionValues) {
            super.captureStartValues(transitionValues);
            verifyCapturedValues(this, transitionValues, true);
        }

        @Override
        public void captureEndValues(TransitionValues transitionValues) {
            super.captureEndValues(transitionValues);
            verifyCapturedValues(this, transitionValues, false);
        }
    }
}
