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

package com.android.tv.tuner.cc;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.Layout.Alignment;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.SubscriptSpan;
import android.text.style.SuperscriptSpan;
import android.text.style.UnderlineSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.CaptioningManager;
import android.view.accessibility.CaptioningManager.CaptionStyle;
import android.view.accessibility.CaptioningManager.CaptioningChangeListener;
import android.widget.RelativeLayout;

import com.google.android.exoplayer.text.CaptionStyleCompat;
import com.google.android.exoplayer.text.SubtitleView;
import com.android.tv.tuner.data.Cea708Data.CaptionPenAttr;
import com.android.tv.tuner.data.Cea708Data.CaptionPenColor;
import com.android.tv.tuner.data.Cea708Data.CaptionWindow;
import com.android.tv.tuner.data.Cea708Data.CaptionWindowAttr;
import com.android.tv.tuner.layout.ScaledLayout;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Layout which renders a caption window of CEA-708B. It contains a {@link SubtitleView} that
 * takes care of displaying the actual cc text.
 */
public class CaptionWindowLayout extends RelativeLayout implements View.OnLayoutChangeListener {
    private static final String TAG = "CaptionWindowLayout";
    private static final boolean DEBUG = false;

    private static final float PROPORTION_PEN_SIZE_SMALL = .75f;
    private static final float PROPORTION_PEN_SIZE_LARGE = 1.25f;

    // The following values indicates the maximum cell number of a window.
    private static final int ANCHOR_RELATIVE_POSITIONING_MAX = 99;
    private static final int ANCHOR_VERTICAL_MAX = 74;
    private static final int ANCHOR_HORIZONTAL_4_3_MAX = 159;
    private static final int ANCHOR_HORIZONTAL_16_9_MAX = 209;

    // The following values indicates a gravity of a window.
    private static final int ANCHOR_MODE_DIVIDER = 3;
    private static final int ANCHOR_HORIZONTAL_MODE_LEFT = 0;
    private static final int ANCHOR_HORIZONTAL_MODE_CENTER = 1;
    private static final int ANCHOR_HORIZONTAL_MODE_RIGHT = 2;
    private static final int ANCHOR_VERTICAL_MODE_TOP = 0;
    private static final int ANCHOR_VERTICAL_MODE_CENTER = 1;
    private static final int ANCHOR_VERTICAL_MODE_BOTTOM = 2;

    private static final int US_MAX_COLUMN_COUNT_16_9 = 42;
    private static final int US_MAX_COLUMN_COUNT_4_3 = 32;
    private static final int KR_MAX_COLUMN_COUNT_16_9 = 52;
    private static final int KR_MAX_COLUMN_COUNT_4_3 = 40;
    private static final int MAX_ROW_COUNT = 15;

    private static final String KOR_ALPHABET =
            new String("\uAC00".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    private static final float WIDE_SCREEN_ASPECT_RATIO_THRESHOLD = 1.6f;

    private CaptionLayout mCaptionLayout;
    private CaptionStyleCompat mCaptionStyleCompat;

    // TODO: Replace SubtitleView to {@link com.google.android.exoplayer.text.SubtitleLayout}.
    private final SubtitleView mSubtitleView;
    private int mRowLimit = 0;
    private final SpannableStringBuilder mBuilder = new SpannableStringBuilder();
    private final List<CharacterStyle> mCharacterStyles = new ArrayList<>();
    private int mCaptionWindowId;
    private int mCurrentTextRow = -1;
    private float mFontScale;
    private float mTextSize;
    private String mWidestChar;
    private int mLastCaptionLayoutWidth;
    private int mLastCaptionLayoutHeight;
    private int mWindowJustify;
    private int mPrintDirection;

    private class SystemWideCaptioningChangeListener extends CaptioningChangeListener {
        @Override
        public void onUserStyleChanged(CaptionStyle userStyle) {
            mCaptionStyleCompat = CaptionStyleCompat.createFromCaptionStyle(userStyle);
            mSubtitleView.setStyle(mCaptionStyleCompat);
            updateWidestChar();
        }

        @Override
        public void onFontScaleChanged(float fontScale) {
            mFontScale = fontScale;
            updateTextSize();
        }
    }

    public CaptionWindowLayout(Context context) {
        this(context, null);
    }

    public CaptionWindowLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CaptionWindowLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        // Add a subtitle view to the layout.
        mSubtitleView = new SubtitleView(context);
        LayoutParams params = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addView(mSubtitleView, params);

        // Set the system wide cc preferences to the subtitle view.
        CaptioningManager captioningManager =
                (CaptioningManager) context.getSystemService(Context.CAPTIONING_SERVICE);
        mFontScale = captioningManager.getFontScale();
        mCaptionStyleCompat =
                CaptionStyleCompat.createFromCaptionStyle(captioningManager.getUserStyle());
        mSubtitleView.setStyle(mCaptionStyleCompat);
        mSubtitleView.setText("");
        captioningManager.addCaptioningChangeListener(new SystemWideCaptioningChangeListener());
        updateWidestChar();
    }

    public int getCaptionWindowId() {
        return mCaptionWindowId;
    }

    public void setCaptionWindowId(int captionWindowId) {
        mCaptionWindowId = captionWindowId;
    }

    public void clear() {
        clearText();
        hide();
    }

    public void show() {
        setVisibility(View.VISIBLE);
        requestLayout();
    }

    public void hide() {
        setVisibility(View.INVISIBLE);
        requestLayout();
    }

    public void setPenAttr(CaptionPenAttr penAttr) {
        mCharacterStyles.clear();
        if (penAttr.italic) {
            mCharacterStyles.add(new StyleSpan(Typeface.ITALIC));
        }
        if (penAttr.underline) {
            mCharacterStyles.add(new UnderlineSpan());
        }
        switch (penAttr.penSize) {
            case CaptionPenAttr.PEN_SIZE_SMALL:
                mCharacterStyles.add(new RelativeSizeSpan(PROPORTION_PEN_SIZE_SMALL));
                break;
            case CaptionPenAttr.PEN_SIZE_LARGE:
                mCharacterStyles.add(new RelativeSizeSpan(PROPORTION_PEN_SIZE_LARGE));
                break;
        }
        switch (penAttr.penOffset) {
            case CaptionPenAttr.OFFSET_SUBSCRIPT:
                mCharacterStyles.add(new SubscriptSpan());
                break;
            case CaptionPenAttr.OFFSET_SUPERSCRIPT:
                mCharacterStyles.add(new SuperscriptSpan());
                break;
        }
    }

    public void setPenColor(CaptionPenColor penColor) {
        // TODO: apply pen colors or skip this and use the style of system wide cc style as is.
    }

    public void setPenLocation(int row, int column) {
        // TODO: change the location of pen when window's justify isn't left.
        // According to the CEA708B spec 8.7, setPenLocation means set the pen cursor within
        // window's text buffer. When row > mCurrentTextRow, we add "\n" to make the cursor locate
        // at row. Adding white space to make cursor locate at column.
        if (mWindowJustify == CaptionWindowAttr.JUSTIFY_LEFT) {
            if (mCurrentTextRow >= 0) {
                for (int r = mCurrentTextRow; r < row; ++r) {
                    appendText("\n");
                }
                if (mCurrentTextRow <= row) {
                    for (int i = 0; i < column; ++i) {
                        appendText(" ");
                    }
                }
            }
        }
        mCurrentTextRow = row;
    }

    public void setWindowAttr(CaptionWindowAttr windowAttr) {
        // TODO: apply window attrs or skip this and use the style of system wide cc style as is.
        mWindowJustify = windowAttr.justify;
        mPrintDirection = windowAttr.printDirection;
    }

    public void sendBuffer(String buffer) {
        appendText(buffer);
    }

    public void sendControl(char control) {
        // TODO: there are a bunch of ASCII-style control codes.
    }

    /**
     * This method places the window on a given CaptionLayout along with the anchor of the window.
     * <p>
     * According to CEA-708B, the anchor id indicates the gravity of the window as the follows.
     * For example, A value 7 of a anchor id says that a window is align with its parent bottom and
     * is located at the center horizontally of its parent.
     * </p>
     * <h4>Anchor id and the gravity of a window</h4>
     * <table>
     *     <tr>
     *         <th>GRAVITY</th>
     *         <th>LEFT</th>
     *         <th>CENTER_HORIZONTAL</th>
     *         <th>RIGHT</th>
     *     </tr>
     *     <tr>
     *         <th>TOP</th>
     *         <td>0</td>
     *         <td>1</td>
     *         <td>2</td>
     *     </tr>
     *     <tr>
     *         <th>CENTER_VERTICAL</th>
     *         <td>3</td>
     *         <td>4</td>
     *         <td>5</td>
     *     </tr>
     *     <tr>
     *         <th>BOTTOM</th>
     *         <td>6</td>
     *         <td>7</td>
     *         <td>8</td>
     *     </tr>
     * </table>
     * <p>
     * In order to handle the gravity of a window, there are two steps. First, set the size of the
     * window. Since the window will be positioned at {@link ScaledLayout}, the size factors are
     * determined in a ratio. Second, set the gravity of the window. {@link CaptionWindowLayout} is
     * inherited from {@link RelativeLayout}. Hence, we could set the gravity of its child view,
     * {@link SubtitleView}.
     * </p>
     * <p>
     * The gravity of the window is also related to its size. When it should be pushed to a one of
     * the end of the window, like LEFT, RIGHT, TOP or BOTTOM, the anchor point should be a boundary
     * of the window. When it should be pushed in the horizontal/vertical center of its container,
     * the horizontal/vertical center point of the window should be the same as the anchor point.
     * </p>
     *
     * @param captionLayout a given {@link CaptionLayout}, which contains a safe title area
     * @param captionWindow a given {@link CaptionWindow}, which stores the construction info of the
     *                      window
     */
    public void initWindow(CaptionLayout captionLayout, CaptionWindow captionWindow) {
        if (DEBUG) {
            Log.d(TAG, "initWindow with "
                    + (captionLayout != null ? captionLayout.getCaptionTrack() : null));
        }
        if (mCaptionLayout != captionLayout) {
            if (mCaptionLayout != null) {
                mCaptionLayout.removeOnLayoutChangeListener(this);
            }
            mCaptionLayout = captionLayout;
            mCaptionLayout.addOnLayoutChangeListener(this);
            updateWidestChar();
        }

        // Both anchor vertical and horizontal indicates the position cell number of the window.
        float scaleRow = (float) captionWindow.anchorVertical / (captionWindow.relativePositioning
                ? ANCHOR_RELATIVE_POSITIONING_MAX : ANCHOR_VERTICAL_MAX);
        float scaleCol = (float) captionWindow.anchorHorizontal /
                (captionWindow.relativePositioning ? ANCHOR_RELATIVE_POSITIONING_MAX
                        : (isWideAspectRatio()
                                ? ANCHOR_HORIZONTAL_16_9_MAX : ANCHOR_HORIZONTAL_4_3_MAX));

        // The range of scaleRow/Col need to be verified to be in [0, 1].
        // Otherwise a {@link RuntimeException} will be raised in {@link ScaledLayout}.
        if (scaleRow < 0 || scaleRow > 1) {
            Log.i(TAG, "The vertical position of the anchor point should be at the range of 0 and 1"
                    + " but " + scaleRow);
            scaleRow = Math.max(0, Math.min(scaleRow, 1));
        }
        if (scaleCol < 0 || scaleCol > 1) {
            Log.i(TAG, "The horizontal position of the anchor point should be at the range of 0 and"
                    + " 1 but " + scaleCol);
            scaleCol = Math.max(0, Math.min(scaleCol, 1));
        }
        int gravity = Gravity.CENTER;
        int horizontalMode = captionWindow.anchorId % ANCHOR_MODE_DIVIDER;
        int verticalMode = captionWindow.anchorId / ANCHOR_MODE_DIVIDER;
        float scaleStartRow = 0;
        float scaleEndRow = 1;
        float scaleStartCol = 0;
        float scaleEndCol = 1;
        switch (horizontalMode) {
            case ANCHOR_HORIZONTAL_MODE_LEFT:
                gravity = Gravity.LEFT;
                mSubtitleView.setTextAlignment(Alignment.ALIGN_NORMAL);
                scaleStartCol = scaleCol;
                break;
            case ANCHOR_HORIZONTAL_MODE_CENTER:
                float gap = Math.min(1 - scaleCol, scaleCol);

                // Since all TV sets use left text alignment instead of center text alignment
                // for this case, we follow the industry convention if possible.
                int columnCount = captionWindow.columnCount + 1;
                if (isKoreanLanguageTrack()) {
                    columnCount /= 2;
                }
                columnCount = Math.min(getScreenColumnCount(), columnCount);
                StringBuilder widestTextBuilder = new StringBuilder();
                for (int i = 0; i < columnCount; ++i) {
                    widestTextBuilder.append(mWidestChar);
                }
                Paint paint = new Paint();
                paint.setTypeface(mCaptionStyleCompat.typeface);
                paint.setTextSize(mTextSize);
                float maxWindowWidth = paint.measureText(widestTextBuilder.toString());
                float halfMaxWidthScale = mCaptionLayout.getWidth() > 0
                        ? maxWindowWidth / 2.0f / (mCaptionLayout.getWidth() * 0.8f) : 0.0f;
                if (halfMaxWidthScale > 0f && halfMaxWidthScale < scaleCol) {
                    // Calculate the expected max window size based on the column count of the
                    // caption window multiplied by average alphabets char width, then align the
                    // left side of the window with the left side of the expected max window.
                    gravity = Gravity.LEFT;
                    mSubtitleView.setTextAlignment(Alignment.ALIGN_NORMAL);
                    scaleStartCol = scaleCol - halfMaxWidthScale;
                    scaleEndCol = 1.0f;
                } else {
                    // The gap will be the minimum distance value of the distances from both
                    // horizontal end points to the anchor point.
                    // If scaleCol <= 0.5, the range of scaleCol is [0, the anchor point * 2].
                    // If scaleCol > 0.5, the range of scaleCol is [(1 - the anchor point) * 2, 1].
                    // The anchor point is located at the horizontal center of the window in both
                    // cases.
                    gravity = Gravity.CENTER_HORIZONTAL;
                    mSubtitleView.setTextAlignment(Alignment.ALIGN_CENTER);
                    scaleStartCol = scaleCol - gap;
                    scaleEndCol = scaleCol + gap;
                }
                break;
            case ANCHOR_HORIZONTAL_MODE_RIGHT:
                gravity = Gravity.RIGHT;
                mSubtitleView.setTextAlignment(Alignment.ALIGN_OPPOSITE);
                scaleEndCol = scaleCol;
                break;
        }
        switch (verticalMode) {
            case ANCHOR_VERTICAL_MODE_TOP:
                gravity |= Gravity.TOP;
                scaleStartRow = scaleRow;
                break;
            case ANCHOR_VERTICAL_MODE_CENTER:
                gravity |= Gravity.CENTER_VERTICAL;

                // See the above comment.
                float gap = Math.min(1 - scaleRow, scaleRow);
                scaleStartRow = scaleRow - gap;
                scaleEndRow = scaleRow + gap;
                break;
            case ANCHOR_VERTICAL_MODE_BOTTOM:
                gravity |= Gravity.BOTTOM;
                scaleEndRow = scaleRow;
                break;
        }
        mCaptionLayout.addOrUpdateViewToSafeTitleArea(this, new ScaledLayout
                .ScaledLayoutParams(scaleStartRow, scaleEndRow, scaleStartCol, scaleEndCol));
        setCaptionWindowId(captionWindow.id);
        setRowLimit(captionWindow.rowCount);
        setGravity(gravity);
        setWindowStyle(captionWindow.windowStyle);
        if (mWindowJustify == CaptionWindowAttr.JUSTIFY_CENTER) {
            mSubtitleView.setTextAlignment(Alignment.ALIGN_CENTER);
        }
        if (captionWindow.visible) {
            show();
        } else {
            hide();
        }
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
            int oldTop, int oldRight, int oldBottom) {
        int width = right - left;
        int height = bottom - top;
        if (width != mLastCaptionLayoutWidth || height != mLastCaptionLayoutHeight) {
            mLastCaptionLayoutWidth = width;
            mLastCaptionLayoutHeight = height;
            updateTextSize();
        }
    }

    private boolean isKoreanLanguageTrack() {
        return mCaptionLayout != null && mCaptionLayout.getCaptionTrack() != null
                && mCaptionLayout.getCaptionTrack().language != null
                && "KOR".compareToIgnoreCase(mCaptionLayout.getCaptionTrack().language) == 0;
    }

    private boolean isWideAspectRatio() {
        return mCaptionLayout != null && mCaptionLayout.getCaptionTrack() != null
                && mCaptionLayout.getCaptionTrack().wideAspectRatio;
    }

    private void updateWidestChar() {
        if (isKoreanLanguageTrack()) {
            mWidestChar = KOR_ALPHABET;
        } else {
            Paint paint = new Paint();
            paint.setTypeface(mCaptionStyleCompat.typeface);
            Charset latin1 = Charset.forName("ISO-8859-1");
            float widestCharWidth = 0f;
            for (int i = 0; i < 256; ++i) {
                String ch = new String(new byte[]{(byte) i}, latin1);
                float charWidth = paint.measureText(ch);
                if (widestCharWidth < charWidth) {
                    widestCharWidth = charWidth;
                    mWidestChar = ch;
                }
            }
        }
        updateTextSize();
    }

    private void updateTextSize() {
        if (mCaptionLayout == null) return;

        // Calculate text size based on the max window size.
        StringBuilder widestTextBuilder = new StringBuilder();
        int screenColumnCount = getScreenColumnCount();
        for (int i = 0; i < screenColumnCount; ++i) {
            widestTextBuilder.append(mWidestChar);
        }
        String widestText = widestTextBuilder.toString();
        Paint paint = new Paint();
        paint.setTypeface(mCaptionStyleCompat.typeface);
        float startFontSize = 0f;
        float endFontSize = 255f;
        Rect boundRect = new Rect();
        while (startFontSize < endFontSize) {
            float testTextSize = (startFontSize + endFontSize) / 2f;
            paint.setTextSize(testTextSize);
            float width = paint.measureText(widestText);
            paint.getTextBounds(widestText, 0, widestText.length(), boundRect);
            float height = boundRect.height() + width - boundRect.width();
            // According to CEA-708B Section 9.13, the height of standard font size shouldn't taller
            // than 1/15 of the height of the safe-title area, and the width shouldn't wider than
            // 1/{@code getScreenColumnCount()} of the width of the safe-title area.
            if (mCaptionLayout.getWidth() * 0.8f > width
                    && mCaptionLayout.getHeight() * 0.8f / MAX_ROW_COUNT > height) {
                startFontSize = testTextSize + 0.01f;
            } else {
                endFontSize = testTextSize - 0.01f;
            }
        }
        mTextSize = endFontSize * mFontScale;
        paint.setTextSize(mTextSize);
        float whiteSpaceWidth = paint.measureText(" ");
        mSubtitleView.setWhiteSpaceWidth(whiteSpaceWidth);
        mSubtitleView.setTextSize(mTextSize);
    }

    private int getScreenColumnCount() {
        float screenAspectRatio = (float) mCaptionLayout.getWidth() / mCaptionLayout.getHeight();
        boolean isWideAspectRationScreen = screenAspectRatio > WIDE_SCREEN_ASPECT_RATIO_THRESHOLD;
        if (isKoreanLanguageTrack()) {
            // Each korean character consumes two slots.
            if (isWideAspectRationScreen || isWideAspectRatio()) {
                return KR_MAX_COLUMN_COUNT_16_9 / 2;
            } else {
                return KR_MAX_COLUMN_COUNT_4_3 / 2;
            }
        } else {
            if (isWideAspectRationScreen || isWideAspectRatio()) {
                return US_MAX_COLUMN_COUNT_16_9;
            } else {
                return US_MAX_COLUMN_COUNT_4_3;
            }
        }
    }

    public void removeFromCaptionView() {
        if (mCaptionLayout != null) {
            mCaptionLayout.removeViewFromSafeTitleArea(this);
            mCaptionLayout.removeOnLayoutChangeListener(this);
            mCaptionLayout = null;
        }
    }

    public void setText(String text) {
        updateText(text, false);
    }

    public void appendText(String text) {
        updateText(text, true);
    }

    public void clearText() {
        mBuilder.clear();
        mSubtitleView.setText("");
    }

    private void updateText(String text, boolean appended) {
        if (!appended) {
            mBuilder.clear();
        }
        if (text != null && text.length() > 0) {
            int length = mBuilder.length();
            mBuilder.append(text);
            for (CharacterStyle characterStyle : mCharacterStyles) {
                mBuilder.setSpan(characterStyle, length, mBuilder.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        String[] lines = TextUtils.split(mBuilder.toString(), "\n");

        // Truncate text not to exceed the row limit.
        // Plus one here since the range of the rows is [0, mRowLimit].
        int startRow = Math.max(0, lines.length - (mRowLimit + 1));
        String truncatedText = TextUtils.join("\n", Arrays.copyOfRange(
                lines, startRow, lines.length));
        mBuilder.delete(0, mBuilder.length() - truncatedText.length());
        mCurrentTextRow = lines.length - startRow - 1;

        // Trim the buffer first then set text to {@link SubtitleView}.
        int start = 0, last = mBuilder.length() - 1;
        int end = last;
        while ((start <= end) && (mBuilder.charAt(start) <= ' ')) {
            ++start;
        }
        while (start - 1 >= 0 && start <= end && mBuilder.charAt(start - 1) != '\n') {
            --start;
        }
        while ((end >= start) && (mBuilder.charAt(end) <= ' ')) {
            --end;
        }
        if (start == 0 && end == last) {
            mSubtitleView.setPrefixSpaces(getPrefixSpaces(mBuilder));
            mSubtitleView.setText(mBuilder);
        } else {
            SpannableStringBuilder trim = new SpannableStringBuilder();
            trim.append(mBuilder);
            if (end < last) {
                trim.delete(end + 1, last + 1);
            }
            if (start > 0) {
                trim.delete(0, start);
            }
            mSubtitleView.setPrefixSpaces(getPrefixSpaces(trim));
            mSubtitleView.setText(trim);
        }
    }

    private static ArrayList<Integer> getPrefixSpaces(SpannableStringBuilder builder) {
        ArrayList<Integer> prefixSpaces = new ArrayList<>();
        String[] lines = TextUtils.split(builder.toString(), "\n");
        for (String line : lines) {
            int start = 0;
            while (start < line.length() && line.charAt(start) <= ' ') {
                start++;
            }
            prefixSpaces.add(start);
        }
        return prefixSpaces;
    }

    public void setRowLimit(int rowLimit) {
        if (rowLimit < 0) {
            throw new IllegalArgumentException("A rowLimit should have a positive number");
        }
        mRowLimit = rowLimit;
    }

    private void setWindowStyle(int windowStyle) {
        // TODO: Set other attributes of window style. Like fill opacity and fill color.
        switch (windowStyle) {
            case 2:
                mWindowJustify = CaptionWindowAttr.JUSTIFY_LEFT;
                mPrintDirection = CaptionWindowAttr.PRINT_LEFT_TO_RIGHT;
                break;
            case 3:
                mWindowJustify = CaptionWindowAttr.JUSTIFY_CENTER;
                mPrintDirection = CaptionWindowAttr.PRINT_LEFT_TO_RIGHT;
                break;
            case 4:
                mWindowJustify = CaptionWindowAttr.JUSTIFY_LEFT;
                mPrintDirection = CaptionWindowAttr.PRINT_LEFT_TO_RIGHT;
                break;
            case 5:
                mWindowJustify = CaptionWindowAttr.JUSTIFY_LEFT;
                mPrintDirection = CaptionWindowAttr.PRINT_LEFT_TO_RIGHT;
                break;
            case 6:
                mWindowJustify = CaptionWindowAttr.JUSTIFY_CENTER;
                mPrintDirection = CaptionWindowAttr.PRINT_LEFT_TO_RIGHT;
                break;
            case 7:
                mWindowJustify = CaptionWindowAttr.JUSTIFY_LEFT;
                mPrintDirection = CaptionWindowAttr.PRINT_TOP_TO_BOTTOM;
                break;
            default:
                if (windowStyle != 0 && windowStyle != 1) {
                    Log.e(TAG, "Error predefined window style:" + windowStyle);
                }
                mWindowJustify = CaptionWindowAttr.JUSTIFY_LEFT;
                mPrintDirection = CaptionWindowAttr.PRINT_LEFT_TO_RIGHT;
                break;
        }
    }
}
