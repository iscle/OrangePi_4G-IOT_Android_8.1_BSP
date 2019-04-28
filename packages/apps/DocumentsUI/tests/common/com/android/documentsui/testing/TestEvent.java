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

package com.android.documentsui.testing;

import android.annotation.IntDef;
import android.graphics.Point;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.android.documentsui.base.Events.InputEvent;
import com.android.documentsui.dirlist.DocumentDetails;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashSet;
import java.util.Set;

/**
 * Events and DocDetails are closely related. For the pursposes of this test
 * we coalesce the two in a single, handy-dandy test class.
 */
public class TestEvent implements InputEvent {
    private static final int ACTION_UNSET = -1;

    // Add other actions from MotionEvent.ACTION_ as needed.
    @IntDef(flag = true, value = {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE,
            MotionEvent.ACTION_UP
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Action {}

    // Add other types from MotionEvent.TOOL_TYPE_ as needed.
    @IntDef(flag = true, value = {
            MotionEvent.TOOL_TYPE_FINGER,
            MotionEvent.TOOL_TYPE_MOUSE,
            MotionEvent.TOOL_TYPE_STYLUS,
            MotionEvent.TOOL_TYPE_UNKNOWN
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ToolType {}

    @IntDef(flag = true, value = {
            MotionEvent.BUTTON_PRIMARY,
            MotionEvent.BUTTON_SECONDARY
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Button {}

    @IntDef(flag = true, value = {
            KeyEvent.META_SHIFT_ON,
            KeyEvent.META_CTRL_ON
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Key {}

    private @Action int mAction;
    private @ToolType int mToolType;
    private int mPointerCount;
    private Set<Integer> mButtons;
    private Set<Integer> mKeys;
    private Point mLocation;
    private Point mRawLocation;
    private Details mDetails;

    private TestEvent() {
        mAction = ACTION_UNSET;  // somebody has to set this, else we'll barf later.
        mToolType = MotionEvent.TOOL_TYPE_UNKNOWN;
        mButtons = new HashSet<>();
        mKeys = new HashSet<>();
        mLocation = new Point(0, 0);
        mRawLocation = new Point(0, 0);
        mDetails = new Details();
        mPointerCount = 0;
    }

    private TestEvent(TestEvent source) {
        assert(source.mAction != ACTION_UNSET);
        mAction = source.mAction;
        mToolType = source.mToolType;
        mButtons = source.mButtons;
        mKeys = source.mKeys;
        mLocation = source.mLocation;
        mRawLocation = source.mRawLocation;
        mDetails = new Details(source.mDetails);
        mPointerCount = source.mPointerCount;
    }

    @Override
    public Point getOrigin() {
        return mLocation;
    }

    @Override
    public float getX() {
        return mLocation.x;
    }

    @Override
    public float getY() {
        return mLocation.y;
    }

    @Override
    public float getRawX() {
        return mRawLocation.x;
    }

    @Override
    public float getRawY() {
        return mRawLocation.y;
    }

    @Override
    public int getPointerCount() {
        return mPointerCount;
    }

    @Override
    public boolean isMouseEvent() {
        return mToolType == MotionEvent.TOOL_TYPE_MOUSE;
    }

    @Override
    public boolean isPrimaryButtonPressed() {
        return mButtons.contains(MotionEvent.BUTTON_PRIMARY);
    }

    @Override
    public boolean isSecondaryButtonPressed() {
        return mButtons.contains(MotionEvent.BUTTON_SECONDARY);
    }

    @Override
    public boolean isTertiaryButtonPressed() {
        return mButtons.contains(MotionEvent.BUTTON_TERTIARY);
    }

    @Override
    public boolean isShiftKeyDown() {
        return mKeys.contains(KeyEvent.META_SHIFT_ON);
    }

    @Override
    public boolean isCtrlKeyDown() {
        return mKeys.contains(KeyEvent.META_CTRL_ON);
    }

    @Override
    public boolean isAltKeyDown() {
        return mKeys.contains(KeyEvent.META_ALT_ON);
    }

    @Override
    public boolean isActionDown() {
        return mAction == MotionEvent.ACTION_DOWN;
    }

    @Override
    public boolean isActionUp() {
        return mAction == MotionEvent.ACTION_UP;
    }

    @Override
    public boolean isMultiPointerActionDown() {
        return mAction == MotionEvent.ACTION_POINTER_DOWN;
    }

    @Override
    public boolean isMultiPointerActionUp() {
        return mAction == MotionEvent.ACTION_POINTER_UP;
    }

    @Override
    public boolean isActionMove() {
        return mAction == MotionEvent.ACTION_MOVE;
    }

    @Override
    public boolean isActionCancel() {
        return mAction == MotionEvent.ACTION_CANCEL;
    }

    @Override
    public boolean isOverItem() {
        return mDetails.isOverItem();
    }

    @Override
    public boolean isOverDocIcon() {
        return mDetails.isOverDocIcon(this);
    }

    @Override
    public boolean isOverDragHotspot() {
        return isOverItem() && mDetails.isInDragHotspot(this);
    }

    @Override
    public boolean isOverModelItem() {
        if (isOverItem()) {
            DocumentDetails doc = getDocumentDetails();
            return doc != null && doc.hasModelId();
        }
        return false;
    }

    @Override
    public boolean isTouchpadScroll() {
        return isMouseEvent() && mButtons.isEmpty() && isActionMove();
    }

    @Override
    public int getItemPosition() {
        return mDetails.mPosition;
    }

    @Override
    public DocumentDetails getDocumentDetails() {
        return mDetails;
    }

    @Override
    public void close() {}

    @Override
    public int hashCode() {
        return mDetails.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
          return true;
      }

      if (!(o instanceof TestEvent)) {
          return false;
      }

      TestEvent other = (TestEvent) o;
      return mAction == other.mAction
              && mToolType == other.mToolType
              && mButtons.equals(other.mButtons)
              && mKeys.equals(other.mKeys)
              && mLocation.equals(other.mLocation)
              && mRawLocation.equals(other.mRawLocation)
              && mDetails.equals(other.mDetails);
    }

    private static final class Details implements DocumentDetails {

        private int mPosition;
        private String mModelId;
        private boolean mInSelectionHotspot;
        private boolean mInDragHotspot;
        private boolean mOverDocIcon;

        public Details() {
           mPosition = Integer.MIN_VALUE;
        }

        public Details(Details source) {
            mPosition = source.mPosition;
            mModelId = source.mModelId;
            mInSelectionHotspot = source.mInSelectionHotspot;
            mInDragHotspot = source.mInDragHotspot;
            mOverDocIcon = source.mOverDocIcon;
        }


        private boolean isOverItem() {
            return mPosition != Integer.MIN_VALUE && mPosition != RecyclerView.NO_POSITION;
        }

        @Override
        public boolean hasModelId() {
            return !TextUtils.isEmpty(mModelId);
        }

        @Override
        public String getModelId() {
            return mModelId;
        }

        @Override
        public int getAdapterPosition() {
            return mPosition;
        }

        @Override
        public boolean isInSelectionHotspot(InputEvent event) {
            return mInSelectionHotspot;
        }

        @Override
        public boolean isInDragHotspot(InputEvent event) {
            return mInDragHotspot;
        }

        @Override
        public boolean isOverDocIcon(InputEvent event) {
            return mOverDocIcon;
        }

        @Override
        public int hashCode() {
            return mModelId != null ? mModelId.hashCode() : ACTION_UNSET;
        }

        @Override
        public boolean equals(Object o) {
          if (this == o) {
              return true;
          }

          if (!(o instanceof Details)) {
              return false;
          }

          Details other = (Details) o;
          return mPosition == other.mPosition
                  && mModelId == other.mModelId;
        }
    }

    public static final Builder builder() {
        return new Builder();
    }

    /**
     * Test event builder with convenience methods for common event attrs.
     */
    public static final class Builder {

        private TestEvent mState = new TestEvent();

        public Builder() {
        }

        public Builder(TestEvent state) {
            mState = new TestEvent(state);
        }

        /**
         * @param action Any action specified in {@link MotionEvent}.
         * @return
         */
        public Builder action(int action) {
            mState.mAction = action;
            return this;
        }

        public Builder type(@ToolType int type) {
            mState.mToolType = type;
            return this;
        }

        public Builder location(int x, int y) {
            mState.mLocation = new Point(x, y);
            return this;
        }

        public Builder rawLocation(int x, int y) {
            mState.mRawLocation = new Point(x, y);
            return this;
        }

        public Builder pointerCount(int count) {
            mState.mPointerCount = count;
            return this;
        }

        /**
         * Adds one or more button press attributes.
         */
        public Builder pressButton(@Button int... buttons) {
            for (int button : buttons) {
                mState.mButtons.add(button);
            }
            return this;
        }

        /**
         * Removes one or more button press attributes.
         */
        public Builder releaseButton(@Button int... buttons) {
            for (int button : buttons) {
                mState.mButtons.remove(button);
            }
            return this;
        }

        /**
         * Adds one or more key press attributes.
         */
        public Builder pressKey(@Key int... keys) {
            for (int key : keys) {
                mState.mKeys.add(key);
            }
            return this;
        }

        /**
         * Removes one or more key press attributes.
         */
        public Builder releaseKey(@Button int... keys) {
            for (int key : keys) {
                mState.mKeys.remove(key);
            }
            return this;
        }

        public Builder at(int position) {
            mState.mDetails.mPosition = position;  // this is both "adapter position" and "item position".
            mState.mDetails.mModelId = String.valueOf(position);
            return this;
        }

        public Builder inSelectionHotspot() {
            mState.mDetails.mInSelectionHotspot = true;
            return this;
        }

        public Builder inDragHotspot() {
            mState.mDetails.mInDragHotspot = true;
            return this;
        }

        public Builder notInDragHotspot() {
            mState.mDetails.mInDragHotspot = false;
            return this;
        }

        public Builder overDocIcon() {
            mState.mDetails.mOverDocIcon = true;
            return this;
        }

        public Builder notOverDocIcon() {
            mState.mDetails.mOverDocIcon = false;
            return this;
        }

        public Builder touch() {
            type(MotionEvent.TOOL_TYPE_FINGER);
            return this;
        }

        public Builder mouse() {
            type(MotionEvent.TOOL_TYPE_MOUSE);
            return this;
        }

        public Builder shift() {
            pressKey(KeyEvent.META_SHIFT_ON);
            return this;
        }

        /**
         * Use {@link #remove(@Attribute int...)}
         */
        @Deprecated
        public Builder unshift() {
            releaseKey(KeyEvent.META_SHIFT_ON);
            return this;
        }

        public Builder ctrl() {
            pressKey(KeyEvent.META_CTRL_ON);
            return this;
        }

        public Builder alt() {
            pressKey(KeyEvent.META_ALT_ON);
            return this;
        }

        public Builder primary() {
            pressButton(MotionEvent.BUTTON_PRIMARY);
            releaseButton(MotionEvent.BUTTON_SECONDARY);
            releaseButton(MotionEvent.BUTTON_TERTIARY);
            return this;
        }

        public Builder secondary() {
            pressButton(MotionEvent.BUTTON_SECONDARY);
            releaseButton(MotionEvent.BUTTON_PRIMARY);
            releaseButton(MotionEvent.BUTTON_TERTIARY);
            return this;
        }

        public Builder tertiary() {
            pressButton(MotionEvent.BUTTON_TERTIARY);
            releaseButton(MotionEvent.BUTTON_PRIMARY);
            releaseButton(MotionEvent.BUTTON_SECONDARY);
            return this;
        }

        public Builder reset() {
            mState = new TestEvent();
            return this;
        }

        @Override
        public Builder clone() {
            return new Builder(build());
        }

        public TestEvent build() {
            // Return a copy, so nobody can mess w/ our internal state.
            return new TestEvent(mState);
        }
    }
}
