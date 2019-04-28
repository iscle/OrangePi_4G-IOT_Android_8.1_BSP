/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.managedprovisioning.common;

import static android.support.test.InstrumentationRegistry.getTargetContext;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.number.OrderingComparison.greaterThanOrEqualTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.graphics.Rect;
import android.support.test.filters.SmallTest;
import android.view.TouchDelegate;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
public class TouchTargetEnforcerTest {
    private static final float SAMPLE_DENSITY = 2.625f;
    private static final int TOLERANCE_PX = 2;
    private static final int OFFSET_DEFAULT = 500;

    @Mock private View mViewAncestor;
    @Captor private ArgumentCaptor<Runnable> mArgumentCaptor;
    private TouchTargetEnforcer mEnforcer;
    private View mView;
    private int mEdgeValue;

    private Rect mCapturedBounds;
    private View mCapturedTargetView;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mView = new View(getTargetContext());
        mEdgeValue = Math.round(TouchTargetEnforcer.MIN_TARGET_DP * SAMPLE_DENSITY);

        mEnforcer = new TouchTargetEnforcer(SAMPLE_DENSITY, (bounds, target) -> {
            mCapturedBounds = bounds;
            mCapturedTargetView = target;
            return new TouchDelegate(mCapturedBounds, mCapturedTargetView);
        });

        mCapturedBounds = null;
        mCapturedTargetView = null;
    }

    @Test
    public void expansionNeededWidth() {
        testExpansionNeeded(mEdgeValue - 1, mEdgeValue + 1);
    }

    @Test
    public void expansionNeededHeight() {
        testExpansionNeeded(mEdgeValue + 1, mEdgeValue - 1);
    }

    @Test
    public void expansionNeededWidthHeight() {
        testExpansionNeeded(mEdgeValue - 1, mEdgeValue - 1);
    }

    private void testExpansionNeeded(int width, int height) {
        setViewDimen(width, height);
        assertExpansionNeeded(width, height);
    }

    private void assertExpansionNeeded(int width, int height) {
        // when
        mEnforcer.enforce(mView, mViewAncestor);
        mView.getViewTreeObserver().dispatchOnGlobalLayout(); // force try register Touch Delegate

        // then
        verify(mViewAncestor).post(mArgumentCaptor.capture()); // prep capture TouchDelegate args
        mArgumentCaptor.getValue().run(); // capture args under mCapturedBounds
        //noinspection ResultOfMethodCallIgnored
        verify(mViewAncestor).getTouchDelegate();
        verify(mViewAncestor).setTouchDelegate(any());
        verifyNoMoreInteractions(mViewAncestor);

        // verify end state correct
        assertBoundsCorrect(width, mCapturedBounds.width());
        assertBoundsCorrect(height, mCapturedBounds.height());
    }

    @Test
    public void expansionNotNeeded() {
        // given
        setViewDimen(mEdgeValue + 1, mEdgeValue + 1);
        assertThat(mView.getWidth(), greaterThanOrEqualTo(mEdgeValue));
        assertThat(mView.getHeight(), greaterThanOrEqualTo(mEdgeValue));

        // when
        mEnforcer.enforce(mView, mViewAncestor);
        mView.getViewTreeObserver().dispatchOnGlobalLayout(); // force UI queue to add a Runnable

        // then
        verifyZeroInteractions(mViewAncestor);
    }

    @Test
    public void doesNotCrashOnEdges() {
        setViewDimen(0, 0, 0);
        assertExpansionNeeded(0, 0);
    }

    private void assertBoundsCorrect(int oldValue, int newValue) {
        assertThat(newValue, greaterThanOrEqualTo(mEdgeValue));
        if (oldValue >= mEdgeValue) {
            assertThat(newValue, equalTo(oldValue));
        } else {
            assertThat(mCapturedBounds.width(), lessThanOrEqualTo(mEdgeValue + TOLERANCE_PX));
        }
    }

    private void setViewDimen(int width, int height) {
        setViewDimen(width, height, OFFSET_DEFAULT);
    }

    private void setViewDimen(int width, int height, int offset) {
        mView.setLeft(offset);
        mView.setRight(offset + width);
        mView.setTop(offset);
        mView.setBottom(offset + height);
    }
}