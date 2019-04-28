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

package com.android.documentsui.dirlist;

import static com.android.documentsui.base.Shared.VERBOSE;

import android.annotation.Nullable;
import android.content.Context;
import android.os.Build;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.OnItemTouchListener;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import com.android.documentsui.base.BooleanConsumer;
import com.android.documentsui.base.EventHandler;
import com.android.documentsui.base.Events;
import com.android.documentsui.base.Events.InputEvent;
import com.android.documentsui.base.Events.MotionInputEvent;
import com.android.documentsui.base.Features;
import com.android.documentsui.selection.BandController;
import com.android.documentsui.selection.GestureSelector;

import java.util.function.Consumer;

//Receives event meant for both directory and empty view, and either pass them to
//{@link UserInputHandler} for simple gestures (Single Tap, Long-Press), or intercept them for
//other types of gestures (drag n' drop)
final class ListeningGestureDetector extends GestureDetector implements OnItemTouchListener {

    private static final String TAG = "ListeningGestureDetector";

    private final Features mFeatures;
    private final GestureSelector mGestureSelector;
    private final EventHandler<InputEvent> mMouseDragListener;
    private final BooleanConsumer mRefreshLayoutEnabler;
    private final BandController mBandController;
    private final MouseDelegate mMouseDelegate = new MouseDelegate();
    private final TouchDelegate mTouchDelegate = new TouchDelegate();

    // Currently only initialized on IS_DEBUGGABLE builds.
    private final @Nullable ScaleGestureDetector mScaleDetector;

    public ListeningGestureDetector(
            Features features,
            Context context,
            RecyclerView recView,
            EventHandler<InputEvent> mouseDragListener,
            BooleanConsumer refreshLayoutEnabler,
            GestureSelector gestureSelector,
            UserInputHandler<? extends InputEvent> handler,
            @Nullable BandController bandController,
            Consumer<Float> scaleHandler) {

        super(context, handler);

        mFeatures = features;
        mMouseDragListener = mouseDragListener;
        mRefreshLayoutEnabler = refreshLayoutEnabler;
        mGestureSelector = gestureSelector;
        mBandController = bandController;
        recView.addOnItemTouchListener(this);

        mScaleDetector = !Build.IS_DEBUGGABLE
                ? null
                : new ScaleGestureDetector(
                        context,
                        new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                            @Override
                            public boolean onScale(ScaleGestureDetector detector) {
                                if (VERBOSE) Log.v(TAG,
                                        "Received scale event: " + detector.getScaleFactor());
                                scaleHandler.accept(detector.getScaleFactor());
                                return true;
                            }
                        });
    }

    @Override
    public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {
        boolean handled = false;

        // TODO: Re-wire event handling so that we're not dispatching
        //     events to to scaledetector's #onTouchEvent from this
        //     #onInterceptTouchEvent touch event.
        if (mFeatures.isGestureScaleEnabled()
                && mScaleDetector != null) {
            mScaleDetector.onTouchEvent(e);
        }

        try (InputEvent event = MotionInputEvent.obtain(e, rv)) {
            if (event.isMouseEvent()) {
                if (event.isActionDown()) {
                    mRefreshLayoutEnabler.accept(false);
                }
                handled |= mMouseDelegate.onInterceptTouchEvent(event);
            } else {
                // If user has started some gesture while RecyclerView is not at the top, disable
                // refresh
                if (event.isActionDown() && rv.computeVerticalScrollOffset() != 0) {
                    mRefreshLayoutEnabler.accept(false);
                }
                handled |= mTouchDelegate.onInterceptTouchEvent(event);
            }


            if (event.isActionUp()) {
                mRefreshLayoutEnabler.accept(true);
            }
        }

        // Forward all events to UserInputHandler.
        // This is necessary since UserInputHandler needs to always see the first DOWN event. Or
        // else all future UP events will be tossed.
        handled |= onTouchEvent(e);

        return handled;
    }

    @Override
    public void onTouchEvent(RecyclerView rv, MotionEvent e) {
        try (InputEvent event = MotionInputEvent.obtain(e, rv)) {
            if (Events.isMouseEvent(e)) {
                mMouseDelegate.onTouchEvent(event);
            } else {
                mTouchDelegate.onTouchEvent(rv, event);
            }

            if (event.isActionUp()) {
                mRefreshLayoutEnabler.accept(true);
            }
        }

        // Note: even though this event is being handled as part of gestures such as drag and band,
        // continue forwarding to the GestureDetector. The detector needs to see the entire cluster
        // of events in order to properly interpret other gestures, such as long press.
        onTouchEvent(e);
    }

    private class MouseDelegate {
        boolean onInterceptTouchEvent(InputEvent e) {
            if (Events.isMouseDragEvent(e)) {
                return mMouseDragListener.accept(e);
            } else if (mBandController != null &&
                    (mBandController.shouldStart(e) || mBandController.shouldStop(e))) {
                return mBandController.onInterceptTouchEvent(e);
            }
            return false;
        }

        void onTouchEvent(InputEvent e) {
            if (mBandController != null) {
                mBandController.onTouchEvent(e);
            }
        }
    }

    private class TouchDelegate {
        boolean onInterceptTouchEvent(InputEvent e) {
            // Gesture Selector needs to be constantly fed events, so that when a long press does
            // happen, we would have the last DOWN event that occurred to keep track of our anchor
            // point
            return mGestureSelector.onInterceptTouchEvent(e);
        }

        // TODO: Make this take just an InputEvent, no RecyclerView
        void onTouchEvent(RecyclerView rv, InputEvent e) {
            mGestureSelector.onTouchEvent(rv, e);
        }
    }

    @Override
    public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {}
}
