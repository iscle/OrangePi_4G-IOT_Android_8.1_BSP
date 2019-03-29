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

import static com.android.compatibility.common.util.CtsMockitoUtils.within;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.Transition;
import android.transition.TransitionSet;

import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class TransitionSetTest extends BaseTransitionTest {
    @Test
    public void testTransitionTogether() throws Throwable {
        TransitionSet transitionSet = new TransitionSet();
        Fade fade = new Fade();
        final Transition.TransitionListener fadeListener =
                mock(Transition.TransitionListener.class);
        fade.addListener(fadeListener);
        ChangeBounds changeBounds = new ChangeBounds();
        final Transition.TransitionListener changeBoundsListener =
                mock(Transition.TransitionListener.class);
        changeBounds.addListener(changeBoundsListener);
        transitionSet.addTransition(fade);
        transitionSet.addTransition(changeBounds);
        mTransition = transitionSet;
        resetListener();

        assertEquals(TransitionSet.ORDERING_TOGETHER, transitionSet.getOrdering());
        enterScene(R.layout.scene1);
        startTransition(R.layout.scene3);
        mActivityRule.runOnUiThread(() -> {
            verify(fadeListener, times(1)).onTransitionStart(any());
            verify(changeBoundsListener, times(1)).onTransitionStart(any());
        });
    }

    @Test
    public void testTransitionSequentially() throws Throwable {
        TransitionSet transitionSet = new TransitionSet();
        Fade fade = new Fade();
        final Transition.TransitionListener fadeListener =
                mock(Transition.TransitionListener.class);
        fade.addListener(fadeListener);
        ChangeBounds changeBounds = new ChangeBounds();
        final Transition.TransitionListener changeBoundsListener =
                mock(Transition.TransitionListener.class);
        changeBounds.addListener(changeBoundsListener);
        transitionSet.addTransition(fade);
        transitionSet.addTransition(changeBounds);
        mTransition = transitionSet;
        resetListener();

        assertEquals(TransitionSet.ORDERING_TOGETHER, transitionSet.getOrdering());
        transitionSet.setOrdering(TransitionSet.ORDERING_SEQUENTIAL);
        assertEquals(TransitionSet.ORDERING_SEQUENTIAL, transitionSet.getOrdering());

        enterScene(R.layout.scene1);
        startTransition(R.layout.scene3);
        mActivityRule.runOnUiThread(() -> {
            verify(fadeListener, times(1)).onTransitionStart(any());
            verify(changeBoundsListener, never()).onTransitionStart(any());
        });
        verify(fadeListener, within(400)).onTransitionEnd(any());
        mActivityRule.runOnUiThread(
                () -> verify(changeBoundsListener, times(1)).onTransitionStart(any()));
    }

    @Test
    public void testTransitionCount() throws Throwable {
        TransitionSet transitionSet = new TransitionSet();
        assertEquals(0, transitionSet.getTransitionCount());

        Fade fade = new Fade();
        ChangeBounds changeBounds = new ChangeBounds();
        transitionSet.addTransition(fade);
        transitionSet.addTransition(changeBounds);

        assertEquals(2, transitionSet.getTransitionCount());
        assertSame(fade, transitionSet.getTransitionAt(0));
        assertSame(changeBounds, transitionSet.getTransitionAt(1));

        transitionSet.removeTransition(fade);

        assertEquals(1, transitionSet.getTransitionCount());
        assertSame(changeBounds, transitionSet.getTransitionAt(0));

        transitionSet.removeTransition(fade); // remove one that isn't there
        assertEquals(1, transitionSet.getTransitionCount());
        assertSame(changeBounds, transitionSet.getTransitionAt(0));
    }
}

