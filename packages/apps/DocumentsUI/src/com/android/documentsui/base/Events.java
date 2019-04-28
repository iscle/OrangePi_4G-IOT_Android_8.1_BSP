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

package com.android.documentsui.base;

import static com.android.documentsui.base.Shared.DEBUG;

import android.graphics.Point;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.Pools;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import com.android.documentsui.dirlist.DocumentDetails;
import com.android.documentsui.dirlist.DocumentHolder;

import javax.annotation.Nullable;

/**
 * Utility code for dealing with MotionEvents.
 */
public final class Events {

    /**
     * Returns true if event was triggered by a mouse.
     */
    public static boolean isMouseEvent(MotionEvent e) {
        int toolType = e.getToolType(0);
        return toolType == MotionEvent.TOOL_TYPE_MOUSE;
    }

    /**
     * Returns true if event was triggered by a finger or stylus touch.
     */
    public static boolean isActionDown(MotionEvent e) {
        return e.getActionMasked() == MotionEvent.ACTION_DOWN;
    }

    /**
     * Returns true if event was triggered by a finger or stylus touch.
     */
    public static boolean isActionUp(MotionEvent e) {
        return e.getActionMasked() == MotionEvent.ACTION_UP;
    }

    /**
     * Returns true if the shift is pressed.
     */
    public boolean isShiftPressed(MotionEvent e) {
        return hasShiftBit(e.getMetaState());
    }

    /**
     * Returns true if the event is a mouse drag event.
     * @param e
     * @return
     */
    public static boolean isMouseDragEvent(InputEvent e) {
        return e.isMouseEvent()
                && e.isActionMove()
                && e.isPrimaryButtonPressed()
                && e.isOverDragHotspot();
    }

    /**
     * Whether or not the given keyCode represents a navigation keystroke (e.g. up, down, home).
     *
     * @param keyCode
     * @return
     */
    public static boolean isNavigationKeyCode(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MOVE_HOME:
            case KeyEvent.KEYCODE_MOVE_END:
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                return true;
            default:
                return false;
        }
    }


    /**
     * Returns true if the "SHIFT" bit is set.
     */
    public static boolean hasShiftBit(int metaState) {
        return (metaState & KeyEvent.META_SHIFT_ON) != 0;
    }

    public static boolean hasCtrlBit(int metaState) {
        return (metaState & KeyEvent.META_CTRL_ON) != 0;
    }

    public static boolean hasAltBit(int metaState) {
        return (metaState & KeyEvent.META_ALT_ON) != 0;
    }

    /**
     * A facade over MotionEvent primarily designed to permit for unit testing
     * of related code.
     */
    public interface InputEvent extends AutoCloseable {
        boolean isMouseEvent();
        boolean isPrimaryButtonPressed();
        boolean isSecondaryButtonPressed();
        boolean isTertiaryButtonPressed();
        boolean isAltKeyDown();
        boolean isShiftKeyDown();
        boolean isCtrlKeyDown();

        /** Returns true if the action is the initial press of a mouse or touch. */
        boolean isActionDown();

        /** Returns true if the action is the final release of a mouse or touch. */
        boolean isActionUp();

        /**
         * Returns true when the action is the initial press of a non-primary (ex. second finger)
         * pointer.
         * See {@link MotionEvent#ACTION_POINTER_DOWN}.
         */
        boolean isMultiPointerActionDown();

        /**
         * Returns true when the action is the final of a non-primary (ex. second finger)
         * pointer.
         * * See {@link MotionEvent#ACTION_POINTER_UP}.
         */
        boolean isMultiPointerActionUp();

        /** Returns true if the action is neither the initial nor the final release of a mouse
         * or touch. */
        boolean isActionMove();

        /** Returns true if the action is cancel. */
        boolean isActionCancel();

        // Eliminate the checked Exception from Autoclosable.
        @Override
        public void close();

        Point getOrigin();
        float getX();
        float getY();
        float getRawX();
        float getRawY();
        int getPointerCount();

        /** Returns true if there is an item under the finger/cursor. */
        boolean isOverItem();

        /**
         * Returns true if there is a model backed item under the finger/cursor.
         * Resulting calls on the event instance should never return a null
         * DocumentDetails and DocumentDetails#hasModelId should always return true
         */
        boolean isOverModelItem();

        /**
         * Returns true if the event is over an area that can be dragged via touch.
         * List items have a white area that is not draggable.
         */
        boolean isOverDragHotspot();

        /**
         * Returns true if the event is a two/three-finger scroll on touchpad.
         */
        boolean isTouchpadScroll();

        /** Returns the adapter position of the item under the finger/cursor. */
        int getItemPosition();

        boolean isOverDocIcon();

        /** Returns the DocumentDetails for the item under the event, or null. */
        @Nullable DocumentDetails getDocumentDetails();
    }

    public static final class MotionInputEvent implements InputEvent {
        private static final String TAG = "MotionInputEvent";

        private static final int UNSET_POSITION = RecyclerView.NO_POSITION - 1;

        private static final Pools.SimplePool<MotionInputEvent> sPool = new Pools.SimplePool<>(1);

        private MotionEvent mEvent;
        private @Nullable RecyclerView mRecView;

        private int mPosition = UNSET_POSITION;
        private @Nullable DocumentDetails mDocDetails;

        private MotionInputEvent() {
            if (DEBUG) Log.i(TAG, "Created a new instance.");
        }

        public static MotionInputEvent obtain(MotionEvent event, RecyclerView view) {
            Shared.checkMainLoop();

            MotionInputEvent instance = sPool.acquire();
            instance = (instance != null ? instance : new MotionInputEvent());

            instance.mEvent = event;
            instance.mRecView = view;

            return instance;
        }

        public void recycle() {
            Shared.checkMainLoop();

            mEvent = null;
            mRecView = null;
            mPosition = UNSET_POSITION;
            mDocDetails = null;

            boolean released = sPool.release(this);
            // This assert is used to guarantee we won't generate too many instances that can't be
            // held in the pool, which indicates our pool size is too small.
            //
            // Right now one instance is enough because we expect all instances are only used in
            // main thread.
            assert(released);
        }

        @Override
        public void close() {
            recycle();
        }

        @Override
        public boolean isMouseEvent() {
            return Events.isMouseEvent(mEvent);
        }

        @Override
        public boolean isPrimaryButtonPressed() {
            return mEvent.isButtonPressed(MotionEvent.BUTTON_PRIMARY);
        }

        @Override
        public boolean isSecondaryButtonPressed() {
            return mEvent.isButtonPressed(MotionEvent.BUTTON_SECONDARY);
        }

        @Override
        public boolean isTertiaryButtonPressed() {
            return mEvent.isButtonPressed(MotionEvent.BUTTON_TERTIARY);
        }

        @Override
        public boolean isAltKeyDown() {
            return Events.hasAltBit(mEvent.getMetaState());
        }

        @Override
        public boolean isShiftKeyDown() {
            return Events.hasShiftBit(mEvent.getMetaState());
        }

        @Override
        public boolean isCtrlKeyDown() {
            return Events.hasCtrlBit(mEvent.getMetaState());
        }

        @Override
        public boolean isActionDown() {
            return mEvent.getActionMasked() == MotionEvent.ACTION_DOWN;
        }

        @Override
        public boolean isActionUp() {
            return mEvent.getActionMasked() == MotionEvent.ACTION_UP;
        }

        @Override
        public boolean isMultiPointerActionDown() {
            return mEvent.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN;
        }

        @Override
        public boolean isMultiPointerActionUp() {
            return mEvent.getActionMasked() == MotionEvent.ACTION_POINTER_UP;
        }


        @Override
        public boolean isActionMove() {
            return mEvent.getActionMasked() == MotionEvent.ACTION_MOVE;
        }

        @Override
        public boolean isActionCancel() {
            return mEvent.getActionMasked() == MotionEvent.ACTION_CANCEL;
        }

        @Override
        public Point getOrigin() {
            return new Point((int) mEvent.getX(), (int) mEvent.getY());
        }

        @Override
        public float getX() {
            return mEvent.getX();
        }

        @Override
        public float getY() {
            return mEvent.getY();
        }

        @Override
        public float getRawX() {
            return mEvent.getRawX();
        }

        @Override
        public float getRawY() {
            return mEvent.getRawY();
        }

        @Override
        public int getPointerCount() {
            return mEvent.getPointerCount();
        }

        @Override
        public boolean isTouchpadScroll() {
            // Touchpad inputs are treated as mouse inputs, and when scrolling, there are no buttons
            // returned.
            return isMouseEvent() && isActionMove() && mEvent.getButtonState() == 0;
        }

        @Override
        public boolean isOverDragHotspot() {
            return isOverItem() && getDocumentDetails().isInDragHotspot(this);
        }

        @Override
        public boolean isOverItem() {
            return getItemPosition() != RecyclerView.NO_POSITION;
        }

        @Override
        public boolean isOverDocIcon() {
            return isOverItem() && getDocumentDetails().isOverDocIcon(this);
        }

        @Override
        public boolean isOverModelItem() {
            return isOverItem() && getDocumentDetails().hasModelId();
        }

        @Override
        public int getItemPosition() {
            if (mPosition == UNSET_POSITION) {
                View child = mRecView.findChildViewUnder(mEvent.getX(), mEvent.getY());
                mPosition = (child != null)
                        ? mRecView.getChildAdapterPosition(child)
                        : RecyclerView.NO_POSITION;
            }
            return mPosition;
        }

        @Override
        public @Nullable DocumentDetails getDocumentDetails() {
            if (mDocDetails == null) {
                View childView = mRecView.findChildViewUnder(mEvent.getX(), mEvent.getY());
                mDocDetails = (childView != null)
                    ? (DocumentHolder) mRecView.getChildViewHolder(childView)
                    : null;
            }
            if (isOverItem()) {
                assert(mDocDetails != null);
            }
            return mDocDetails;
        }

        @Override
        public String toString() {
            return new StringBuilder()
                    .append("MotionInputEvent {")
                    .append("isMouseEvent=").append(isMouseEvent())
                    .append(" isPrimaryButtonPressed=").append(isPrimaryButtonPressed())
                    .append(" isSecondaryButtonPressed=").append(isSecondaryButtonPressed())
                    .append(" isShiftKeyDown=").append(isShiftKeyDown())
                    .append(" isAltKeyDown=").append(isAltKeyDown())
                    .append(" action(decoded)=").append(
                            MotionEvent.actionToString(mEvent.getActionMasked()))
                    .append(" getOrigin=").append(getOrigin())
                    .append(" isOverItem=").append(isOverItem())
                    .append(" getItemPosition=").append(getItemPosition())
                    .append(" getDocumentDetails=").append(getDocumentDetails())
                    .append(" getPointerCount=").append(getPointerCount())
                    .append("}")
                    .toString();
        }
    }
}
