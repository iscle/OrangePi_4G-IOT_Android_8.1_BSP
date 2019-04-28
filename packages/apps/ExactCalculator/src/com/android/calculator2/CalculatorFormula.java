/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.calculator2;

import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.os.Build;
import android.text.Layout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

/**
 * TextView adapted for displaying the formula and allowing pasting.
 */
public class CalculatorFormula extends AlignedTextView implements MenuItem.OnMenuItemClickListener,
        ClipboardManager.OnPrimaryClipChangedListener {

    public static final String TAG_ACTION_MODE = "ACTION_MODE";

    // Temporary paint for use in layout methods.
    private final TextPaint mTempPaint = new TextPaint();

    private final float mMaximumTextSize;
    private final float mMinimumTextSize;
    private final float mStepTextSize;

    private final ClipboardManager mClipboardManager;

    private int mWidthConstraint = -1;
    private ActionMode mActionMode;
    private ActionMode.Callback mPasteActionModeCallback;
    private ContextMenu mContextMenu;
    private OnTextSizeChangeListener mOnTextSizeChangeListener;
    private OnFormulaContextMenuClickListener mOnContextMenuClickListener;
    private Calculator.OnDisplayMemoryOperationsListener mOnDisplayMemoryOperationsListener;

    public CalculatorFormula(Context context) {
        this(context, null /* attrs */);
    }

    public CalculatorFormula(Context context, AttributeSet attrs) {
        this(context, attrs, 0 /* defStyleAttr */);
    }

    public CalculatorFormula(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mClipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);

        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.CalculatorFormula, defStyleAttr, 0);
        mMaximumTextSize = a.getDimension(
                R.styleable.CalculatorFormula_maxTextSize, getTextSize());
        mMinimumTextSize = a.getDimension(
                R.styleable.CalculatorFormula_minTextSize, getTextSize());
        mStepTextSize = a.getDimension(R.styleable.CalculatorFormula_stepTextSize,
                (mMaximumTextSize - mMinimumTextSize) / 3);
        a.recycle();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            setupActionMode();
        } else {
            setupContextMenu();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (!isLaidOut()) {
            // Prevent shrinking/resizing with our variable textSize.
            setTextSizeInternal(TypedValue.COMPLEX_UNIT_PX, mMaximumTextSize,
                    false /* notifyListener */);
            setMinimumHeight(getLineHeight() + getCompoundPaddingBottom()
                    + getCompoundPaddingTop());
        }

        // Ensure we are at least as big as our parent.
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        if (getMinimumWidth() != width) {
            setMinimumWidth(width);
        }

        // Re-calculate our textSize based on new width.
        mWidthConstraint = MeasureSpec.getSize(widthMeasureSpec)
                - getPaddingLeft() - getPaddingRight();
        final float textSize = getVariableTextSize(getText());
        if (getTextSize() != textSize) {
            setTextSizeInternal(TypedValue.COMPLEX_UNIT_PX, textSize, false /* notifyListener */);
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mClipboardManager.addPrimaryClipChangedListener(this);
        onPrimaryClipChanged();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        mClipboardManager.removePrimaryClipChangedListener(this);
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);

        setTextSize(TypedValue.COMPLEX_UNIT_PX, getVariableTextSize(text.toString()));
    }

    private void setTextSizeInternal(int unit, float size, boolean notifyListener) {
        final float oldTextSize = getTextSize();
        super.setTextSize(unit, size);
        if (notifyListener && mOnTextSizeChangeListener != null && getTextSize() != oldTextSize) {
            mOnTextSizeChangeListener.onTextSizeChanged(this, oldTextSize);
        }
    }

    @Override
    public void setTextSize(int unit, float size) {
        setTextSizeInternal(unit, size, true);
    }

    public float getMinimumTextSize() {
        return mMinimumTextSize;
    }

    public float getMaximumTextSize() {
        return mMaximumTextSize;
    }

    public float getVariableTextSize(CharSequence text) {
        if (mWidthConstraint < 0 || mMaximumTextSize <= mMinimumTextSize) {
            // Not measured, bail early.
            return getTextSize();
        }

        // Capture current paint state.
        mTempPaint.set(getPaint());

        // Step through increasing text sizes until the text would no longer fit.
        float lastFitTextSize = mMinimumTextSize;
        while (lastFitTextSize < mMaximumTextSize) {
            mTempPaint.setTextSize(Math.min(lastFitTextSize + mStepTextSize, mMaximumTextSize));
            if (Layout.getDesiredWidth(text, mTempPaint) > mWidthConstraint) {
                break;
            }
            lastFitTextSize = mTempPaint.getTextSize();
        }

        return lastFitTextSize;
    }

    /**
     * Functionally equivalent to setText(), but explicitly announce changes.
     * If the new text is an extension of the old one, announce the addition.
     * Otherwise, e.g. after deletion, announce the entire new text.
     */
    public void changeTextTo(CharSequence newText) {
        final CharSequence oldText = getText();
        final char separator = KeyMaps.translateResult(",").charAt(0);
        final CharSequence added = StringUtils.getExtensionIgnoring(newText, oldText, separator);
        if (added != null) {
            if (added.length() == 1) {
                // The algorithm for pronouncing a single character doesn't seem
                // to respect our hints.  Don't give it the choice.
                final char c = added.charAt(0);
                final int id = KeyMaps.keyForChar(c);
                final String descr = KeyMaps.toDescriptiveString(getContext(), id);
                if (descr != null) {
                    announceForAccessibility(descr);
                } else {
                    announceForAccessibility(String.valueOf(c));
                }
            } else if (added.length() != 0) {
                announceForAccessibility(added);
            }
        } else {
            announceForAccessibility(newText);
        }
        setText(newText, BufferType.SPANNABLE);
    }

    public boolean stopActionModeOrContextMenu() {
        if (mActionMode != null) {
            mActionMode.finish();
            return true;
        }
        if (mContextMenu != null) {
            mContextMenu.close();
            return true;
        }
        return false;
    }

    public void setOnTextSizeChangeListener(OnTextSizeChangeListener listener) {
        mOnTextSizeChangeListener = listener;
    }

    public void setOnContextMenuClickListener(OnFormulaContextMenuClickListener listener) {
        mOnContextMenuClickListener = listener;
    }

    public void setOnDisplayMemoryOperationsListener(
            Calculator.OnDisplayMemoryOperationsListener listener) {
        mOnDisplayMemoryOperationsListener = listener;
    }

    /**
     * Use ActionMode for paste support on M and higher.
     */
    @TargetApi(Build.VERSION_CODES.M)
    private void setupActionMode() {
        mPasteActionModeCallback = new ActionMode.Callback2() {

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                if (onMenuItemClick(item)) {
                    mode.finish();
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mode.setTag(TAG_ACTION_MODE);
                final MenuInflater inflater = mode.getMenuInflater();
                return createContextMenu(inflater, menu);
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                mActionMode = null;
            }

            @Override
            public void onGetContentRect(ActionMode mode, View view, Rect outRect) {
                super.onGetContentRect(mode, view, outRect);
                outRect.top += getTotalPaddingTop();
                outRect.right -= getTotalPaddingRight();
                outRect.bottom -= getTotalPaddingBottom();
                // Encourage menu positioning over the rightmost 10% of the screen.
                outRect.left = (int) (outRect.right * 0.9f);
            }
        };
        setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                mActionMode = startActionMode(mPasteActionModeCallback, ActionMode.TYPE_FLOATING);
                return true;
            }
        });
    }

    /**
     * Use ContextMenu for paste support on L and lower.
     */
    private void setupContextMenu() {
        setOnCreateContextMenuListener(new OnCreateContextMenuListener() {
            @Override
            public void onCreateContextMenu(ContextMenu contextMenu, View view,
                    ContextMenu.ContextMenuInfo contextMenuInfo) {
                final MenuInflater inflater = new MenuInflater(getContext());
                createContextMenu(inflater, contextMenu);
                mContextMenu = contextMenu;
                for (int i = 0; i < contextMenu.size(); i++) {
                    contextMenu.getItem(i).setOnMenuItemClickListener(CalculatorFormula.this);
                }
            }
        });
        setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return showContextMenu();
            }
        });
    }

    private boolean createContextMenu(MenuInflater inflater, Menu menu) {
        final boolean isPasteEnabled = isPasteEnabled();
        final boolean isMemoryEnabled = isMemoryEnabled();
        if (!isPasteEnabled && !isMemoryEnabled) {
            return false;
        }

        bringPointIntoView(length());
        inflater.inflate(R.menu.menu_formula, menu);
        final MenuItem pasteItem = menu.findItem(R.id.menu_paste);
        final MenuItem memoryRecallItem = menu.findItem(R.id.memory_recall);
        pasteItem.setEnabled(isPasteEnabled);
        memoryRecallItem.setEnabled(isMemoryEnabled);
        return true;
    }

    private void paste() {
        final ClipData primaryClip = mClipboardManager.getPrimaryClip();
        if (primaryClip != null && mOnContextMenuClickListener != null) {
            mOnContextMenuClickListener.onPaste(primaryClip);
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.memory_recall:
                mOnContextMenuClickListener.onMemoryRecall();
                return true;
            case R.id.menu_paste:
                paste();
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onPrimaryClipChanged() {
        setLongClickable(isPasteEnabled() || isMemoryEnabled());
    }

    public void onMemoryStateChanged() {
        setLongClickable(isPasteEnabled() || isMemoryEnabled());
    }

    private boolean isMemoryEnabled() {
        return mOnDisplayMemoryOperationsListener != null
                && mOnDisplayMemoryOperationsListener.shouldDisplayMemory();
    }

    private boolean isPasteEnabled() {
        final ClipData clip = mClipboardManager.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            return false;
        }
        CharSequence clipText = null;
        try {
            clipText = clip.getItemAt(0).coerceToText(getContext());
        } catch (Exception e) {
            Log.i("Calculator", "Error reading clipboard:", e);
        }
        return !TextUtils.isEmpty(clipText);
    }

    public interface OnTextSizeChangeListener {
        void onTextSizeChanged(TextView textView, float oldSize);
    }

    public interface OnFormulaContextMenuClickListener {
        boolean onPaste(ClipData clip);
        void onMemoryRecall();
    }
}
