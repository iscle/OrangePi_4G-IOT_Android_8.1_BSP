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

package android.autofillservice.cts;

import static android.autofillservice.cts.Helper.FILL_TIMEOUT_MS;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.assist.AssistStructure.ViewNode;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewStructure;
import android.view.ViewStructure.HtmlInfo;
import android.view.WindowManager;
import android.view.autofill.AutofillManager;
import android.view.autofill.AutofillValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class VirtualContainerView extends View {

    private static final String TAG = "VirtualContainerView";

    static final String LABEL_CLASS = "my.readonly.view";
    static final String TEXT_CLASS = "my.editable.view";


    private final ArrayList<Line> mLines = new ArrayList<>();
    private final SparseArray<Item> mItems = new SparseArray<>();
    private final AutofillManager mAfm;

    private Line mFocusedLine;

    private Paint mTextPaint;
    private int mTextHeight;
    private int mTopMargin;
    private int mLeftMargin;
    private int mVerticalGap;
    private int mLineLength;
    private int mFocusedColor;
    private int mUnfocusedColor;
    private boolean mSync = true;
    private boolean mOverrideDispatchProvideAutofillStructure = false;

    public VirtualContainerView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mAfm = context.getSystemService(AutofillManager.class);

        mTextPaint = new Paint();

        mUnfocusedColor = Color.BLACK;
        mFocusedColor = Color.RED;
        mTextPaint.setStyle(Style.FILL);
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getMetrics(metrics);
        mTopMargin = metrics.heightPixels * 5 / 100;
        mLeftMargin = metrics.widthPixels * 5 / 100;
        mTextHeight = metrics.widthPixels * 5 / 100; // adjust text size with display width
        mVerticalGap = metrics.heightPixels / 100;

        mLineLength = mTextHeight + mVerticalGap;
        mTextPaint.setTextSize(mTextHeight);
        Log.d(TAG, "Text height: " + mTextHeight);
    }

    @Override
    public void autofill(SparseArray<AutofillValue> values) {
        Log.d(TAG, "autofill: " + values);
        for (int i = 0; i < values.size(); i++) {
            final int id = values.keyAt(i);
            final AutofillValue value = values.valueAt(i);
            final Item item = mItems.get(id);
            if (item == null) {
                Log.w(TAG, "No item for id " + id);
                return;
            }
            if (!item.editable) {
                Log.w(TAG, "Item for id " + id + " is not editable: " + item);
                return;
            }
            item.text = value.getTextValue();
            if (item.listener != null) {
                Log.d(TAG, "Notify listener: " + item.text);
                item.listener.onTextChanged(item.text, 0, 0, 0);
            }
        }
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        Log.d(TAG, "onDraw: " + mLines.size() + " lines; canvas:" + canvas);
        float x;
        float y = mTopMargin + mLineLength;
        for (int i = 0; i < mLines.size(); i++) {
            x = mLeftMargin;
            final Line line = mLines.get(i);
            if (!line.visible) {
                continue;
            }
            Log.v(TAG, "Drawing '" + line + "' at " + x + "x" + y);
            mTextPaint.setColor(line.focused ? mFocusedColor : mUnfocusedColor);
            final String readOnlyText = line.label.text + ":  [";
            final String writeText = line.text.text + "]";
            // Paints the label first...
            canvas.drawText(readOnlyText, x, y, mTextPaint);
            // ...then paints the edit text and sets the proper boundary
            final float deltaX = mTextPaint.measureText(readOnlyText);
            x += deltaX;
            line.bounds.set((int) x, (int) (y - mLineLength),
                    (int) (x + mTextPaint.measureText(writeText)), (int) y);
            Log.d(TAG, "setBounds(" + x + ", " + y + "): " + line.bounds);
            canvas.drawText(writeText, x, y, mTextPaint);
            y += mLineLength;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int y = (int) event.getY();
        Log.d(TAG, "You can touch this: y=" + y + ", range=" + mLineLength + ", top=" + mTopMargin);
        int lowerY = mTopMargin;
        int upperY = -1;
        for (int i = 0; i < mLines.size(); i++) {
            upperY = lowerY + mLineLength;
            final Line line = mLines.get(i);
            Log.d(TAG, "Line " + i + " ranges from " + lowerY + " to " + upperY);
            if (lowerY <= y && y <= upperY) {
                if (mFocusedLine != null) {
                    Log.d(TAG, "Removing focus from " + mFocusedLine);
                    mFocusedLine.changeFocus(false);
                }
                Log.d(TAG, "Changing focus to " + line);
                mFocusedLine = line;
                mFocusedLine.changeFocus(true);
                invalidate();
                break;
            }
            lowerY += mLineLength;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public void dispatchProvideAutofillStructure(ViewStructure structure, int flags) {
        if (mOverrideDispatchProvideAutofillStructure) {
            Log.d(TAG, "Overriding dispatchProvideAutofillStructure()");
            structure.setAutofillId(getAutofillId());
            onProvideAutofillVirtualStructure(structure, flags);
        } else {
            super.dispatchProvideAutofillStructure(structure, flags);
        }
    }

    @Override
    public void onProvideAutofillVirtualStructure(ViewStructure structure, int flags) {
        Log.d(TAG, "onProvideAutofillVirtualStructure(): flags = " + flags);
        super.onProvideAutofillVirtualStructure(structure, flags);

        final String packageName = getContext().getPackageName();
        structure.setClassName(getClass().getName());
        final int childrenSize = mItems.size();
        int index = structure.addChildCount(childrenSize);
        final String syncMsg = mSync ? "" : " (async)";
        for (int i = 0; i < childrenSize; i++) {
            final Item item = mItems.valueAt(i);
            Log.d(TAG, "Adding new child" + syncMsg + " at index " + index + ": " + item);
            final ViewStructure child = mSync
                    ? structure.newChild(index)
                    : structure.asyncNewChild(index);
            child.setAutofillId(structure.getAutofillId(), item.id);
            child.setDataIsSensitive(item.sensitive);
            index++;
            final String className = item.editable ? TEXT_CLASS : LABEL_CLASS;
            child.setClassName(className);
            // Must set "fake" idEntry because that's what the test cases use to find nodes.
            child.setId(1000 + index, packageName, "id", item.resourceId);
            child.setText(item.text);
            if (TextUtils.getTrimmedLength(item.text) > 0) {
                child.setAutofillValue(AutofillValue.forText(item.text));
            }
            child.setFocused(item.line.focused);
            child.setHtmlInfo(child.newHtmlInfoBuilder("TAGGY")
                    .addAttribute("a1", "v1")
                    .addAttribute("a2", "v2")
                    .addAttribute("a1", "v2")
                    .build());
            child.setAutofillHints(new String[] {"c", "a", "a", "b", "a", "a"});

            if (!mSync) {
                Log.d(TAG, "Commiting virtual child");
                child.asyncCommit();
            }
        }
    }

    static void assertHtmlInfo(ViewNode node) {
        final String name = node.getText().toString();
        final HtmlInfo info = node.getHtmlInfo();
        assertWithMessage("no HTML info on %s", name).that(info).isNotNull();
        assertWithMessage("wrong HTML tag on %s", name).that(info.getTag()).isEqualTo("TAGGY");
        assertWithMessage("wrong attributes on %s", name).that(info.getAttributes())
                .containsExactly(
                        new Pair<>("a1", "v1"),
                        new Pair<>("a2", "v2"),
                        new Pair<>("a1", "v2"));
    }

    Line addLine(String labelId, String label, String textId, String text) {
        final Line line = new Line(labelId, label, textId, text);
        Log.d(TAG, "addLine: " + line);
        mLines.add(line);
        mItems.put(line.label.id, line.label);
        mItems.put(line.text.id, line.text);
        return line;
    }

    void setSync(boolean sync) {
        mSync = sync;
    }

    void setOverrideDispatchProvideAutofillStructure(boolean flag) {
        mOverrideDispatchProvideAutofillStructure = flag;
    }

    private static int nextId;

    final class Line {

        final Item label;
        final Item text;
        // Boundaries of the text field, relative to the CustomView
        final Rect bounds = new Rect();

        private boolean focused;
        private boolean visible = true;

        private Line(String labelId, String label, String textId, String text) {
            this.label = new Item(this, ++nextId, labelId, label, false, false);
            this.text = new Item(this, ++nextId, textId, text, true, true);
        }

        void changeFocus(boolean focused) {
            this.focused = focused;
            if (focused) {
                final Rect absBounds = getAbsCoordinates();
                Log.d(TAG, "focus gained on " + text.id + "; absBounds=" + absBounds);
                mAfm.notifyViewEntered(VirtualContainerView.this, text.id, absBounds);
            } else {
                Log.d(TAG, "focus lost on " + text.id);
                mAfm.notifyViewExited(VirtualContainerView.this, text.id);
            }
        }

        void changeVisibility(boolean visible) {
            if (this.visible == visible) {
                return;
            }
            this.visible = visible;
            mAfm.notifyViewVisibilityChanged(VirtualContainerView.this, text.id, visible);
            Log.d(TAG, "visibility changed view: " + text.id + "; visible:" + visible);
            invalidate();
        }

        Rect getAbsCoordinates() {
            // Must offset the boundaries so they're relative to the CustomView.
            final int offset[] = new int[2];
            getLocationOnScreen(offset);
            final Rect absBounds = new Rect(bounds.left + offset[0],
                    bounds.top + offset[1],
                    bounds.right + offset[0], bounds.bottom + offset[1]);
            Log.v(TAG, "getAbsCoordinates() for " + text.id + ": bounds=" + bounds
                    + " offset: " + Arrays.toString(offset) + " absBounds: " + absBounds);
            return absBounds;
        }

        void setText(String value) {
            text.text = value;
            final AutofillManager autofillManager =
                    getContext().getSystemService(AutofillManager.class);
            if (autofillManager != null) {
                autofillManager.notifyValueChanged(VirtualContainerView.this, text.id,
                        AutofillValue.forText(text.text));
            }
            invalidate();
        }

        void setTextChangedListener(TextWatcher listener) {
            text.listener = listener;
        }

        @Override
        public String toString() {
            return "Label: " + label + " Text: " + text + " Focused: " + focused
                    + " Visible: " + visible;
        }

        final class OneTimeLineWatcher implements TextWatcher {
            private final CountDownLatch latch;
            private final CharSequence expected;

            OneTimeLineWatcher(CharSequence expectedValue) {
                this.expected = expectedValue;
                this.latch = new CountDownLatch(1);
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                latch.countDown();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }

            void assertAutoFilled() throws Exception {
                final boolean set = latch.await(FILL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                assertWithMessage("Timeout (%s ms) on Line %s", FILL_TIMEOUT_MS, label)
                        .that(set).isTrue();
                final String actual = text.text.toString();
                assertWithMessage("Wrong auto-fill value on Line %s", label)
                        .that(actual).isEqualTo(expected.toString());
            }
        }
    }

    static final class Item {
        private final Line line;
        final int id;
        private final String resourceId;
        private CharSequence text;
        private final boolean editable;
        private final boolean sensitive;
        private TextWatcher listener;

        Item(Line line, int id, String resourceId, CharSequence text, boolean editable,
                boolean sensitive) {
            this.line = line;
            this.id = id;
            this.resourceId = resourceId;
            this.text = text;
            this.editable = editable;
            this.sensitive = sensitive;
        }

        @Override
        public String toString() {
            return id + "/" + resourceId + ": " + text + (editable ? " (editable)" : " (read-only)"
                    + (sensitive ? " (sensitive)" : " (sanitized"));
        }
    }
}
