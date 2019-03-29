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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.transition.ArcMotion;
import android.transition.AutoTransition;
import android.transition.ChangeBounds;
import android.transition.ChangeClipBounds;
import android.transition.ChangeImageTransform;
import android.transition.ChangeScroll;
import android.transition.ChangeTransform;
import android.transition.Explode;
import android.transition.Fade;
import android.transition.PathMotion;
import android.transition.PatternPathMotion;
import android.transition.Scene;
import android.transition.Slide;
import android.transition.Transition;
import android.transition.TransitionInflater;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.transition.TransitionValues;
import android.transition.Visibility;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class TransitionInflaterTest extends BaseTransitionTest {
    @Test
    public void testInflationConstructors() throws Throwable {
        TransitionInflater inflater = TransitionInflater.from(mActivity);
        Transition transition = inflater.inflateTransition(R.transition.transition_constructors);
        assertTrue(transition instanceof TransitionSet);
        TransitionSet set = (TransitionSet) transition;
        assertEquals(10, set.getTransitionCount());
    }

    @Test
    public void testInflation() {
        TransitionInflater inflater = TransitionInflater.from(mActivity);
        verifyFadeProperties(inflater.inflateTransition(R.transition.fade));
        verifyChangeBoundsProperties(inflater.inflateTransition(R.transition.change_bounds));
        verifySlideProperties(inflater.inflateTransition(R.transition.slide));
        verifyExplodeProperties(inflater.inflateTransition(R.transition.explode));
        verifyChangeImageTransformProperties(
                inflater.inflateTransition(R.transition.change_image_transform));
        verifyChangeTransformProperties(inflater.inflateTransition(R.transition.change_transform));
        verifyChangeClipBoundsProperties(
                inflater.inflateTransition(R.transition.change_clip_bounds));
        verifyAutoTransitionProperties(inflater.inflateTransition(R.transition.auto_transition));
        verifyChangeScrollProperties(inflater.inflateTransition(R.transition.change_scroll));
        verifyTransitionSetProperties(inflater.inflateTransition(R.transition.transition_set));
        verifyCustomTransitionProperties(
                inflater.inflateTransition(R.transition.custom_transition));
        verifyTargetIds(inflater.inflateTransition(R.transition.target_ids));
        verifyTargetNames(inflater.inflateTransition(R.transition.target_names));
        verifyTargetClass(inflater.inflateTransition(R.transition.target_classes));
        verifyArcMotion(inflater.inflateTransition(R.transition.arc_motion));
        verifyCustomPathMotion(inflater.inflateTransition(R.transition.custom_path_motion));
        verifyPatternPathMotion(inflater.inflateTransition(R.transition.pattern_path_motion));
    }

    @Test
    public void testInflateTransitionManager() throws Throwable {
        TransitionInflater inflater = TransitionInflater.from(mActivity);
        TransitionManager transitionManager =
                inflater.inflateTransitionManager(R.transition.transition_manager, mSceneRoot);
        assertNotNull(transitionManager);

        Scene scene1 = Scene.getSceneForLayout(mSceneRoot, R.layout.scene1, mActivity);
        Transition transition = transitionManager.getTransition(scene1);
        assertNotNull(transition);
        assertTrue(transition instanceof Fade);
        enterScene(scene1);

        Scene scene2 = Scene.getSceneForLayout(mSceneRoot, R.layout.scene2, mActivity);
        transition = transitionManager.getTransition(scene2);
        assertNotNull(transition);
        assertTrue(transition instanceof ChangeBounds);
    }

    private void verifyFadeProperties(Transition transition) {
        assertTrue(transition instanceof Fade);
        Fade fade = (Fade) transition;
        assertEquals(Fade.OUT, fade.getMode());
    }

    private void verifyChangeBoundsProperties(Transition transition) {
        assertTrue(transition instanceof ChangeBounds);
        ChangeBounds changeBounds = (ChangeBounds) transition;
        assertTrue(changeBounds.getResizeClip());
    }

    private void verifySlideProperties(Transition transition) {
        assertTrue(transition instanceof Slide);
        Slide slide = (Slide) transition;
        assertEquals(Gravity.TOP, slide.getSlideEdge());
    }

    private void verifyExplodeProperties(Transition transition) {
        assertTrue(transition instanceof Explode);
        Visibility visibility = (Visibility) transition;
        assertEquals(Visibility.MODE_IN, visibility.getMode());
    }

    private void verifyChangeImageTransformProperties(Transition transition) {
        assertTrue(transition instanceof ChangeImageTransform);
    }

    private void verifyChangeTransformProperties(Transition transition) {
        assertTrue(transition instanceof ChangeTransform);
        ChangeTransform changeTransform = (ChangeTransform) transition;
        assertFalse(changeTransform.getReparent());
        assertFalse(changeTransform.getReparentWithOverlay());
    }

    private void verifyChangeClipBoundsProperties(Transition transition) {
        assertTrue(transition instanceof ChangeClipBounds);
    }

    private void verifyAutoTransitionProperties(Transition transition) {
        assertTrue(transition instanceof AutoTransition);
    }

    private void verifyChangeScrollProperties(Transition transition) {
        assertTrue(transition instanceof ChangeScroll);
    }

    private void verifyTransitionSetProperties(Transition transition) {
        assertTrue(transition instanceof TransitionSet);
        TransitionSet set = (TransitionSet) transition;
        assertEquals(TransitionSet.ORDERING_SEQUENTIAL, set.getOrdering());
        assertEquals(2, set.getTransitionCount());
        assertTrue(set.getTransitionAt(0) instanceof ChangeBounds);
        assertTrue(set.getTransitionAt(1) instanceof Fade);
    }

    private void verifyCustomTransitionProperties(Transition transition) {
        assertTrue(transition instanceof CustomTransition);
    }

    private void verifyTargetIds(Transition transition) {
        List<Integer> targets = transition.getTargetIds();
        assertNotNull(targets);
        assertEquals(2, targets.size());
        assertEquals(R.id.hello, (int) targets.get(0));
        assertEquals(R.id.world, (int) targets.get(1));
    }

    private void verifyTargetNames(Transition transition) {
        List<String> targets = transition.getTargetNames();
        assertNotNull(targets);
        assertEquals(2, targets.size());
        assertEquals("hello", targets.get(0));
        assertEquals("world", targets.get(1));
    }

    private void verifyTargetClass(Transition transition) {
        List<Class> targets = transition.getTargetTypes();
        assertNotNull(targets);
        assertEquals(2, targets.size());
        assertEquals(TextView.class, targets.get(0));
        assertEquals(ImageView.class, targets.get(1));
    }

    private void verifyArcMotion(Transition transition) {
        assertNotNull(transition);
        PathMotion motion = transition.getPathMotion();
        assertNotNull(motion);
        assertTrue(motion instanceof ArcMotion);
        ArcMotion arcMotion = (ArcMotion) motion;
        assertEquals(1f, arcMotion.getMinimumVerticalAngle(), 0.01f);
        assertEquals(2f, arcMotion.getMinimumHorizontalAngle(), 0.01f);
        assertEquals(53f, arcMotion.getMaximumAngle(), 0.01f);
    }

    private void verifyCustomPathMotion(Transition transition) {
        assertNotNull(transition);
        PathMotion motion = transition.getPathMotion();
        assertNotNull(motion);
        assertTrue(motion instanceof CustomPathMotion);
    }

    private void verifyPatternPathMotion(Transition transition) {
        assertNotNull(transition);
        PathMotion motion = transition.getPathMotion();
        assertNotNull(motion);
        assertTrue(motion instanceof PatternPathMotion);
        PatternPathMotion pattern = (PatternPathMotion) motion;
        Path path = pattern.getPatternPath();
        PathMeasure measure = new PathMeasure(path, false);
        assertEquals(200f, measure.getLength(), 0.1f);
    }

    public static class CustomTransition extends Transition {
        public CustomTransition() {
            fail("Default constructor was not expected");
        }

        public CustomTransition(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        public void captureStartValues(TransitionValues transitionValues) {
        }

        @Override
        public void captureEndValues(TransitionValues transitionValues) {
        }
    }

    public static class CustomPathMotion extends PathMotion {
        public CustomPathMotion() {
            fail("default constructor shouldn't be called.");
        }

        public CustomPathMotion(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        public Path getPath(float startX, float startY, float endX, float endY) {
            return null;
        }
    }

    public static class InflationFade extends Fade {
        public InflationFade(Context context, AttributeSet attrs) {
            super(context, attrs);
        }
    }

    public static class InflationChangeBounds extends ChangeBounds {
        public InflationChangeBounds(Context context, AttributeSet attrs) {
            super(context, attrs);
        }
    }

    public static class InflationSlide extends Slide {
        public InflationSlide(Context context, AttributeSet attrs) {
            super(context, attrs);
        }
    }

    public static class InflationTransitionSet extends TransitionSet {
        public InflationTransitionSet(Context context, AttributeSet attrs) {
            super(context, attrs);
        }
    }

    public static class InflationChangeImageTransform extends ChangeImageTransform {
        public InflationChangeImageTransform(Context context, AttributeSet attrs) {
            super(context, attrs);
        }
    }

    public static class InflationChangeTransform extends ChangeTransform {
        public InflationChangeTransform(Context context, AttributeSet attrs) {
            super(context, attrs);
        }
    }

    public static class InflationAutoTransition extends AutoTransition {
        public InflationAutoTransition(Context context, AttributeSet attrs) {
            super(context, attrs);
        }
    }

    public static class InflationChangeClipBounds extends ChangeClipBounds {
        public InflationChangeClipBounds(Context context, AttributeSet attrs) {
            super(context, attrs);
        }
    }

    public static class InflationChangeScroll extends ChangeScroll {
        public InflationChangeScroll(Context context, AttributeSet attrs) {
            super(context, attrs);
        }
    }

    public static class InflationExplode extends Explode {
        public InflationExplode(Context context, AttributeSet attrs) {
            super(context, attrs);
        }
    }
}
