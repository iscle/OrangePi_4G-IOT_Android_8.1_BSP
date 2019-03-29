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

package com.android.compatibility.common.util;

import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

import org.mockito.InOrder;
import org.mockito.compat.ArgumentMatcher;

public final class CtsMouseUtil {

    private CtsMouseUtil() {}

    public static View.OnHoverListener installHoverListener(View view) {
        return installHoverListener(view, true);
    }

    public static View.OnHoverListener installHoverListener(View view, boolean result) {
        final View.OnHoverListener mockListener = mock(View.OnHoverListener.class);
        view.setOnHoverListener((v, event) -> {
            // Clone the event to work around event instance reuse in the framework.
            mockListener.onHover(v, MotionEvent.obtain(event));
            return result;
        });
        return mockListener;
    }

    public static void clearHoverListener(View view) {
        view.setOnHoverListener(null);
    }

    public static MotionEvent obtainMouseEvent(int action, View anchor, int offsetX, int offsetY) {
        final long eventTime = SystemClock.uptimeMillis();
        final int[] screenPos = new int[2];
        anchor.getLocationOnScreen(screenPos);
        final int x = screenPos[0] + offsetX;
        final int y = screenPos[1] + offsetY;
        MotionEvent event = MotionEvent.obtain(eventTime, eventTime, action, x, y, 0);
        event.setSource(InputDevice.SOURCE_MOUSE);
        return event;
    }

    public static class ActionMatcher extends ArgumentMatcher<MotionEvent> {
        private final int mAction;

        public ActionMatcher(int action) {
            mAction = action;
        }

        @Override
        public boolean matchesObject(Object actual) {
            return (actual instanceof MotionEvent) && ((MotionEvent) actual).getAction() == mAction;
        }

        @Override
        public String toString() {
            return "action=" + MotionEvent.actionToString(mAction);
        }
    }

    public static class PositionMatcher extends ActionMatcher {
        private final int mX;
        private final int mY;

        public PositionMatcher(int action, int x, int y) {
            super(action);
            mX = x;
            mY = y;
        }

        @Override
        public boolean matchesObject(Object actual) {
            return super.matchesObject(actual)
                    && ((int) ((MotionEvent) actual).getX()) == mX
                    && ((int) ((MotionEvent) actual).getY()) == mY;
        }

        @Override
        public String toString() {
            return super.toString() + "@(" + mX + "," + mY + ")";
        }
    }

    public static void verifyEnterMove(View.OnHoverListener listener, View view, int moveCount) {
        final InOrder inOrder = inOrder(listener);
        verifyEnterMoveInternal(listener, view, moveCount, inOrder);
        inOrder.verifyNoMoreInteractions();
    }

    public static void verifyEnterMoveExit(
            View.OnHoverListener listener, View view, int moveCount) {
        final InOrder inOrder = inOrder(listener);
        verifyEnterMoveInternal(listener, view, moveCount, inOrder);
        inOrder.verify(listener, times(1)).onHover(eq(view),
                argThat(new ActionMatcher(MotionEvent.ACTION_HOVER_EXIT)));
        inOrder.verifyNoMoreInteractions();
    }

    private static void verifyEnterMoveInternal(
            View.OnHoverListener listener, View view, int moveCount, InOrder inOrder) {
        inOrder.verify(listener, times(1)).onHover(eq(view),
                argThat(new ActionMatcher(MotionEvent.ACTION_HOVER_ENTER)));
        inOrder.verify(listener, times(moveCount)).onHover(eq(view),
                argThat(new ActionMatcher(MotionEvent.ACTION_HOVER_MOVE)));
    }
}

