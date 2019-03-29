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

package com.android.compatibility.common.util;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.graphics.Point;
import android.os.SystemClock;
import android.util.SparseArray;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

/**
 * Test utilities for touch emulation.
 */
public final class CtsTouchUtils {
    /**
     * Interface definition for a callback to be invoked when an event has been injected.
     */
    public interface EventInjectionListener {
        /**
         * Callback method to be invoked when a {MotionEvent#ACTION_DOWN} has been injected.
         * @param xOnScreen X coordinate of the injected event.
         * @param yOnScreen Y coordinate of the injected event.
         */
        public void onDownInjected(int xOnScreen, int yOnScreen);

        /**
         * Callback method to be invoked when a {MotionEvent#ACTION_MOVE} has been injected.
         * @param xOnScreen X coordinates of the injected event.
         * @param yOnScreen Y coordinates of the injected event.
         */
        public void onMoveInjected(int[] xOnScreen, int[] yOnScreen);

        /**
         * Callback method to be invoked when a {MotionEvent#ACTION_UP} has been injected.
         * @param xOnScreen X coordinate of the injected event.
         * @param yOnScreen Y coordinate of the injected event.
         */
        public void onUpInjected(int xOnScreen, int yOnScreen);
    }

    private CtsTouchUtils() {}

    /**
     * Emulates a tap in the center of the passed {@link View}.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param view the view to "tap"
     */
    public static void emulateTapOnViewCenter(Instrumentation instrumentation, View view) {
        emulateTapOnView(instrumentation, view, view.getWidth() / 2, view.getHeight() / 2);
    }

    /**
     * Emulates a tap on a point relative to the top-left corner of the passed {@link View}. Offset
     * parameters are used to compute the final screen coordinates of the tap point.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param anchorView the anchor view to determine the tap location on the screen
     * @param offsetX extra X offset for the tap
     * @param offsetY extra Y offset for the tap
     */
    public static void emulateTapOnView(Instrumentation instrumentation, View anchorView,
            int offsetX, int offsetY) {
        final int touchSlop = ViewConfiguration.get(anchorView.getContext()).getScaledTouchSlop();
        // Get anchor coordinates on the screen
        final int[] viewOnScreenXY = new int[2];
        anchorView.getLocationOnScreen(viewOnScreenXY);
        int xOnScreen = viewOnScreenXY[0] + offsetX;
        int yOnScreen = viewOnScreenXY[1] + offsetY;
        final UiAutomation uiAutomation = instrumentation.getUiAutomation();
        final long downTime = SystemClock.uptimeMillis();

        injectDownEvent(uiAutomation, downTime, xOnScreen, yOnScreen, null);
        injectMoveEventForTap(uiAutomation, downTime, touchSlop, xOnScreen, yOnScreen);
        injectUpEvent(uiAutomation, downTime, false, xOnScreen, yOnScreen, null);

        // Wait for the system to process all events in the queue
        instrumentation.waitForIdleSync();
    }

    /**
     * Emulates a double tap in the center of the passed {@link View}.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param view the view to "double tap"
     */
    public static void emulateDoubleTapOnViewCenter(Instrumentation instrumentation, View view) {
        emulateDoubleTapOnView(instrumentation, view, view.getWidth() / 2, view.getHeight() / 2);
    }

    /**
     * Emulates a double tap on a point relative to the top-left corner of the passed {@link View}.
     * Offset parameters are used to compute the final screen coordinates of the tap points.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param anchorView the anchor view to determine the tap location on the screen
     * @param offsetX extra X offset for the taps
     * @param offsetY extra Y offset for the taps
     */
    public static void emulateDoubleTapOnView(Instrumentation instrumentation, View anchorView,
            int offsetX, int offsetY) {
        final int touchSlop = ViewConfiguration.get(anchorView.getContext()).getScaledTouchSlop();
        // Get anchor coordinates on the screen
        final int[] viewOnScreenXY = new int[2];
        anchorView.getLocationOnScreen(viewOnScreenXY);
        int xOnScreen = viewOnScreenXY[0] + offsetX;
        int yOnScreen = viewOnScreenXY[1] + offsetY;
        final UiAutomation uiAutomation = instrumentation.getUiAutomation();
        final long downTime = SystemClock.uptimeMillis();

        injectDownEvent(uiAutomation, downTime, xOnScreen, yOnScreen, null);
        injectMoveEventForTap(uiAutomation, downTime, touchSlop, xOnScreen, yOnScreen);
        injectUpEvent(uiAutomation, downTime, false, xOnScreen, yOnScreen, null);
        injectDownEvent(uiAutomation, downTime, xOnScreen, yOnScreen, null);
        injectMoveEventForTap(uiAutomation, downTime, touchSlop, xOnScreen, yOnScreen);
        injectUpEvent(uiAutomation, downTime, false, xOnScreen, yOnScreen, null);

        // Wait for the system to process all events in the queue
        instrumentation.waitForIdleSync();
    }

    /**
     * Emulates a linear drag gesture between 2 points across the screen.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param dragStartX Start X of the emulated drag gesture
     * @param dragStartY Start Y of the emulated drag gesture
     * @param dragAmountX X amount of the emulated drag gesture
     * @param dragAmountY Y amount of the emulated drag gesture
     */
    public static void emulateDragGesture(Instrumentation instrumentation,
            int dragStartX, int dragStartY, int dragAmountX, int dragAmountY) {
        emulateDragGesture(instrumentation, dragStartX, dragStartY, dragAmountX, dragAmountY,
                2000, 20, null);
    }

    private static void emulateDragGesture(Instrumentation instrumentation,
            int dragStartX, int dragStartY, int dragAmountX, int dragAmountY,
            int dragDurationMs, int moveEventCount) {
        emulateDragGesture(instrumentation, dragStartX, dragStartY, dragAmountX, dragAmountY,
                dragDurationMs, moveEventCount, null);
    }

    private static void emulateDragGesture(Instrumentation instrumentation,
            int dragStartX, int dragStartY, int dragAmountX, int dragAmountY,
            int dragDurationMs, int moveEventCount,
            EventInjectionListener eventInjectionListener) {
        // We are using the UiAutomation object to inject events so that drag works
        // across view / window boundaries (such as for the emulated drag and drop
        // sequences)
        final UiAutomation uiAutomation = instrumentation.getUiAutomation();
        final long downTime = SystemClock.uptimeMillis();

        injectDownEvent(uiAutomation, downTime, dragStartX, dragStartY, eventInjectionListener);

        // Inject a sequence of MOVE events that emulate the "move" part of the gesture
        injectMoveEventsForDrag(uiAutomation, downTime, true, dragStartX, dragStartY,
                dragStartX + dragAmountX, dragStartY + dragAmountY, moveEventCount, dragDurationMs,
            eventInjectionListener);

        injectUpEvent(uiAutomation, downTime, true, dragStartX + dragAmountX,
                dragStartY + dragAmountY, eventInjectionListener);

        // Wait for the system to process all events in the queue
        instrumentation.waitForIdleSync();
    }

    /**
     * Emulates a series of linear drag gestures across the screen between multiple points without
     * lifting the finger. Note that this function does not support curve movements between the
     * points.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param coordinates the ordered list of points for the drag gesture
     */
    public static void emulateDragGesture(Instrumentation instrumentation,
            SparseArray<Point> coordinates) {
        emulateDragGesture(instrumentation, coordinates, 2000, 20);
    }

    private static void emulateDragGesture(Instrumentation instrumentation,
            SparseArray<Point> coordinates, int dragDurationMs, int moveEventCount) {
        final int coordinatesSize = coordinates.size();
        if (coordinatesSize < 2) {
            throw new IllegalArgumentException("Need at least 2 points for emulating drag");
        }
        // We are using the UiAutomation object to inject events so that drag works
        // across view / window boundaries (such as for the emulated drag and drop
        // sequences)
        final UiAutomation uiAutomation = instrumentation.getUiAutomation();
        final long downTime = SystemClock.uptimeMillis();

        injectDownEvent(uiAutomation, downTime, coordinates.get(0).x, coordinates.get(0).y, null);

        // Move to each coordinate.
        for (int i = 0; i < coordinatesSize - 1; i++) {
            // Inject a sequence of MOVE events that emulate the "move" part of the gesture.
            injectMoveEventsForDrag(uiAutomation,
                    downTime,
                    true,
                    coordinates.get(i).x,
                    coordinates.get(i).y,
                    coordinates.get(i + 1).x,
                    coordinates.get(i + 1).y,
                    moveEventCount,
                    dragDurationMs,
                    null);
        }

        injectUpEvent(uiAutomation,
                downTime,
                true,
                coordinates.get(coordinatesSize - 1).x,
                coordinates.get(coordinatesSize - 1).y,
                null);

        // Wait for the system to process all events in the queue
        instrumentation.waitForIdleSync();
    }

    private static long injectDownEvent(UiAutomation uiAutomation, long downTime, int xOnScreen,
            int yOnScreen, EventInjectionListener eventInjectionListener) {
        MotionEvent eventDown = MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN, xOnScreen, yOnScreen, 1);
        eventDown.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        uiAutomation.injectInputEvent(eventDown, true);
        if (eventInjectionListener != null) {
            eventInjectionListener.onDownInjected(xOnScreen, yOnScreen);
        }
        eventDown.recycle();
        return downTime;
    }

    private static void injectMoveEventForTap(UiAutomation uiAutomation, long downTime,
            int touchSlop, int xOnScreen, int yOnScreen) {
        MotionEvent eventMove = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_MOVE,
                xOnScreen + (touchSlop / 2.0f), yOnScreen + (touchSlop / 2.0f), 1);
        eventMove.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        uiAutomation.injectInputEvent(eventMove, true);
        eventMove.recycle();
    }

    private static void injectMoveEventsForDrag(UiAutomation uiAutomation, long downTime,
            boolean useCurrentEventTime, int dragStartX, int dragStartY, int dragEndX, int dragEndY,
            int moveEventCount, int dragDurationMs, EventInjectionListener eventInjectionListener) {
        final int dragAmountX = dragEndX - dragStartX;
        final int dragAmountY = dragEndY - dragStartY;
        final int sleepTime = dragDurationMs / moveEventCount;

        // sleep for a bit to emulate the overall drag gesture.
        long prevEventTime = downTime;
        SystemClock.sleep(sleepTime);
        for (int i = 0; i < moveEventCount; i++) {
            // Note that the first MOVE event is generated "away" from the coordinates
            // of the start / DOWN event, and the last MOVE event is generated
            // at the same coordinates as the subsequent UP event.
            final int moveX = dragStartX + dragAmountX * (i  + 1) / moveEventCount;
            final int moveY = dragStartY + dragAmountY * (i  + 1) / moveEventCount;
            long eventTime = useCurrentEventTime ? SystemClock.uptimeMillis() : downTime;

            // If necessary, generate history for our next MOVE event. The history is generated
            // to be spaced at 10 millisecond intervals, interpolating the coordinates from the
            // last generated MOVE event to our current one.
            int historyEventCount = (int) ((eventTime - prevEventTime) / 10);
            int[] xCoordsForListener = (eventInjectionListener == null) ? null :
                    new int[Math.max(1, historyEventCount)];
            int[] yCoordsForListener = (eventInjectionListener == null) ? null :
                    new int[Math.max(1, historyEventCount)];
            MotionEvent eventMove = null;
            if (historyEventCount == 0) {
                eventMove = MotionEvent.obtain(
                        downTime, eventTime, MotionEvent.ACTION_MOVE, moveX, moveY, 1);
                if (eventInjectionListener != null) {
                    xCoordsForListener[0] = moveX;
                    yCoordsForListener[0] = moveY;
                }
            } else {
                final int prevMoveX = dragStartX + dragAmountX * i / moveEventCount;
                final int prevMoveY = dragStartY + dragAmountY * i / moveEventCount;
                final int deltaMoveX = moveX - prevMoveX;
                final int deltaMoveY = moveY - prevMoveY;
                final long deltaTime = (eventTime - prevEventTime);
                for (int historyIndex = 0; historyIndex < historyEventCount; historyIndex++) {
                    int stepMoveX = prevMoveX + deltaMoveX * (historyIndex + 1) / historyEventCount;
                    int stepMoveY = prevMoveY + deltaMoveY * (historyIndex + 1) / historyEventCount;
                    long stepEventTime = useCurrentEventTime
                            ? prevEventTime + deltaTime * (historyIndex + 1) / historyEventCount
                            : downTime;
                    if (historyIndex == 0) {
                        // Generate the first event in our sequence
                        eventMove = MotionEvent.obtain(downTime, stepEventTime,
                                MotionEvent.ACTION_MOVE, stepMoveX, stepMoveY, 1);
                    } else {
                        // and then add to it
                        eventMove.addBatch(stepEventTime, stepMoveX, stepMoveY, 1.0f, 1.0f, 1);
                    }
                    if (eventInjectionListener != null) {
                        xCoordsForListener[historyIndex] = stepMoveX;
                        yCoordsForListener[historyIndex] = stepMoveY;
                    }
                }
            }

            eventMove.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            uiAutomation.injectInputEvent(eventMove, true);
            if (eventInjectionListener != null) {
                eventInjectionListener.onMoveInjected(xCoordsForListener, yCoordsForListener);
            }
            eventMove.recycle();
            prevEventTime = eventTime;

            // sleep for a bit to emulate the overall drag gesture.
            SystemClock.sleep(sleepTime);
        }
    }

    private static void injectUpEvent(UiAutomation uiAutomation, long downTime,
            boolean useCurrentEventTime, int xOnScreen, int yOnScreen,
            EventInjectionListener eventInjectionListener) {
        long eventTime = useCurrentEventTime ? SystemClock.uptimeMillis() : downTime;
        MotionEvent eventUp = MotionEvent.obtain(
                downTime, eventTime, MotionEvent.ACTION_UP, xOnScreen, yOnScreen, 1);
        eventUp.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        uiAutomation.injectInputEvent(eventUp, true);
        if (eventInjectionListener != null) {
            eventInjectionListener.onUpInjected(xOnScreen, yOnScreen);
        }
        eventUp.recycle();
    }

    /**
     * Emulates a fling gesture across the horizontal center of the passed view.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param view the view to fling
     * @param isDownwardsFlingGesture if <code>true</code>, the emulated fling will
     *      be a downwards gesture
     * @return The vertical amount of emulated fling in pixels
     */
    public static int emulateFlingGesture(Instrumentation instrumentation,
            View view, boolean isDownwardsFlingGesture) {
        return emulateFlingGesture(instrumentation, view, isDownwardsFlingGesture, null);
    }

    /**
     * Emulates a fling gesture across the horizontal center of the passed view.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param view the view to fling
     * @param isDownwardsFlingGesture if <code>true</code>, the emulated fling will
     *      be a downwards gesture
     * @param eventInjectionListener optional listener to notify about the injected events
     * @return The vertical amount of emulated fling in pixels
     */
    public static int emulateFlingGesture(Instrumentation instrumentation,
            View view, boolean isDownwardsFlingGesture,
            EventInjectionListener eventInjectionListener) {
        final ViewConfiguration configuration = ViewConfiguration.get(view.getContext());
        final int flingVelocity = (configuration.getScaledMinimumFlingVelocity() +
                configuration.getScaledMaximumFlingVelocity()) / 2;
        // Get view coordinates on the screen
        final int[] viewOnScreenXY = new int[2];
        view.getLocationOnScreen(viewOnScreenXY);

        // Our fling gesture will be from 25% height of the view to 75% height of the view
        // for downwards fling gesture, and the other way around for upwards fling gesture
        final int viewHeight = view.getHeight();
        final int x = viewOnScreenXY[0] + view.getWidth() / 2;
        final int startY = isDownwardsFlingGesture ? viewOnScreenXY[1] + viewHeight / 4
                : viewOnScreenXY[1] + 3 * viewHeight / 4;
        final int amountY = isDownwardsFlingGesture ? viewHeight / 2 : -viewHeight / 2;

        // Compute fling gesture duration based on the distance (50% height of the view) and
        // fling velocity
        final int durationMs = (1000 * viewHeight) / (2 * flingVelocity);

        // And do the same event injection sequence as our generic drag gesture
        emulateDragGesture(instrumentation, x, startY, 0, amountY, durationMs, durationMs / 16,
            eventInjectionListener);

        return amountY;
    }

    private static class ViewStateSnapshot {
        final View mFirst;
        final View mLast;
        final int mFirstTop;
        final int mLastBottom;
        final int mChildCount;
        private ViewStateSnapshot(ViewGroup viewGroup) {
            mChildCount = viewGroup.getChildCount();
            if (mChildCount == 0) {
                mFirst = mLast = null;
                mFirstTop = mLastBottom = Integer.MIN_VALUE;
            } else {
                mFirst = viewGroup.getChildAt(0);
                mLast = viewGroup.getChildAt(mChildCount - 1);
                mFirstTop = mFirst.getTop();
                mLastBottom = mLast.getBottom();
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final ViewStateSnapshot that = (ViewStateSnapshot) o;
            return mFirstTop == that.mFirstTop &&
                    mLastBottom == that.mLastBottom &&
                    mFirst == that.mFirst &&
                    mLast == that.mLast &&
                    mChildCount == that.mChildCount;
        }

        @Override
        public int hashCode() {
            int result = mFirst != null ? mFirst.hashCode() : 0;
            result = 31 * result + (mLast != null ? mLast.hashCode() : 0);
            result = 31 * result + mFirstTop;
            result = 31 * result + mLastBottom;
            result = 31 * result + mChildCount;
            return result;
        }
    }

    /**
     * Emulates a scroll to the bottom of the specified {@link ViewGroup}.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param viewGroup View group
     */
    public static void emulateScrollToBottom(Instrumentation instrumentation, ViewGroup viewGroup) {
        final int[] viewGroupOnScreenXY = new int[2];
        viewGroup.getLocationOnScreen(viewGroupOnScreenXY);

        final int emulatedX = viewGroupOnScreenXY[0] + viewGroup.getWidth() / 2;
        final int emulatedStartY = viewGroupOnScreenXY[1] + 3 * viewGroup.getHeight() / 4;
        final int swipeAmount = viewGroup.getHeight() / 2;

        ViewStateSnapshot prev;
        ViewStateSnapshot next = new ViewStateSnapshot(viewGroup);
        do {
            prev = next;
            emulateDragGesture(instrumentation, emulatedX, emulatedStartY, 0, -swipeAmount,
                    300, 10);
            next = new ViewStateSnapshot(viewGroup);
        } while (!prev.equals(next));
    }

    /**
     * Emulates a long press in the center of the passed {@link View}.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param view the view to "long press"
     */
    public static void emulateLongPressOnViewCenter(Instrumentation instrumentation, View view) {
        emulateLongPressOnViewCenter(instrumentation, view, 0);
    }

    /**
     * Emulates a long press in the center of the passed {@link View}.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param view the view to "long press"
     * @param extraWaitMs the duration of emulated "long press" in milliseconds starting
     *      after system-level long press timeout.
     */
    public static void emulateLongPressOnViewCenter(Instrumentation instrumentation, View view,
            long extraWaitMs) {
        final int touchSlop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
        // Use instrumentation to emulate a tap on the spinner to bring down its popup
        final int[] viewOnScreenXY = new int[2];
        view.getLocationOnScreen(viewOnScreenXY);
        int xOnScreen = viewOnScreenXY[0] + view.getWidth() / 2;
        int yOnScreen = viewOnScreenXY[1] + view.getHeight() / 2;

        emulateLongPressOnScreen(
                instrumentation, xOnScreen, yOnScreen, touchSlop, extraWaitMs, true);
    }

    /**
     * Emulates a long press confirmed on a point relative to the top-left corner of the passed
     * {@link View}. Offset parameters are used to compute the final screen coordinates of the
     * press point.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param view the view to "long press"
     * @param offsetX extra X offset for the tap
     * @param offsetY extra Y offset for the tap
     */
    public static void emulateLongPressOnView(Instrumentation instrumentation, View view,
            int offsetX, int offsetY) {
        final int touchSlop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
        final int[] viewOnScreenXY = new int[2];
        view.getLocationOnScreen(viewOnScreenXY);
        int xOnScreen = viewOnScreenXY[0] + offsetX;
        int yOnScreen = viewOnScreenXY[1] + offsetY;

        emulateLongPressOnScreen(instrumentation, xOnScreen, yOnScreen, touchSlop, 0, true);
    }

    /**
     * Emulates a long press then a linear drag gesture between 2 points across the screen.
     * This is used for drag selection.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param dragStartX Start X of the emulated drag gesture
     * @param dragStartY Start Y of the emulated drag gesture
     * @param dragAmountX X amount of the emulated drag gesture
     * @param dragAmountY Y amount of the emulated drag gesture
     */
    public static void emulateLongPressAndDragGesture(Instrumentation instrumentation,
            int dragStartX, int dragStartY, int dragAmountX, int dragAmountY) {
        emulateLongPressOnScreen(instrumentation, dragStartX, dragStartY,
                0 /* touchSlop */, 0 /* extraWaitMs */, false /* upGesture */);
        emulateDragGesture(instrumentation, dragStartX, dragStartY, dragAmountX, dragAmountY);
    }

    /**
     * Emulates a long press on the screen.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param xOnScreen X position on screen for the "long press"
     * @param yOnScreen Y position on screen for the "long press"
     * @param extraWaitMs extra duration of emulated long press in milliseconds added
     *        after the system-level "long press" timeout.
     * @param upGesture whether to include an up event.
     */
    private static void emulateLongPressOnScreen(Instrumentation instrumentation,
            int xOnScreen, int yOnScreen, int touchSlop, long extraWaitMs, boolean upGesture) {
        final UiAutomation uiAutomation = instrumentation.getUiAutomation();
        final long downTime = SystemClock.uptimeMillis();

        injectDownEvent(uiAutomation, downTime, xOnScreen, yOnScreen, null);
        injectMoveEventForTap(uiAutomation, downTime, touchSlop, xOnScreen, yOnScreen);
        SystemClock.sleep((long) (ViewConfiguration.getLongPressTimeout() * 1.5f) + extraWaitMs);
        if (upGesture) {
            injectUpEvent(uiAutomation, downTime, false, xOnScreen, yOnScreen, null);
        }

        // Wait for the system to process all events in the queue
        instrumentation.waitForIdleSync();
    }
}
