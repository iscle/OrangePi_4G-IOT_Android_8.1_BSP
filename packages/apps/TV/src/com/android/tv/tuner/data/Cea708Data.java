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

package com.android.tv.tuner.data;

import com.android.tv.tuner.cc.Cea708Parser;

import android.graphics.Color;
import android.support.annotation.NonNull;

/**
 * Collection of CEA-708 structures.
 */
public class Cea708Data {

    private Cea708Data() {
    }

    // According to CEA-708B, the range of valid service number is between 1 and 63.
    public static final int EMPTY_SERVICE_NUMBER = 0;

    // For the details of the ranges of DTVCC code groups, see CEA-708B Table 6.
    public static final int CODE_C0_RANGE_START = 0x00;
    public static final int CODE_C0_RANGE_END = 0x1f;
    public static final int CODE_C1_RANGE_START = 0x80;
    public static final int CODE_C1_RANGE_END = 0x9f;
    public static final int CODE_G0_RANGE_START = 0x20;
    public static final int CODE_G0_RANGE_END = 0x7f;
    public static final int CODE_G1_RANGE_START = 0xa0;
    public static final int CODE_G1_RANGE_END = 0xff;
    public static final int CODE_C2_RANGE_START = 0x00;
    public static final int CODE_C2_RANGE_END = 0x1f;
    public static final int CODE_C3_RANGE_START = 0x80;
    public static final int CODE_C3_RANGE_END = 0x9f;
    public static final int CODE_G2_RANGE_START = 0x20;
    public static final int CODE_G2_RANGE_END = 0x7f;
    public static final int CODE_G3_RANGE_START = 0xa0;
    public static final int CODE_G3_RANGE_END = 0xff;

    // The following ranges are defined in CEA-708B Section 7.4.1.
    public static final int CODE_C0_SKIP2_RANGE_START = 0x18;
    public static final int CODE_C0_SKIP2_RANGE_END = 0x1f;
    public static final int CODE_C0_SKIP1_RANGE_START = 0x10;
    public static final int CODE_C0_SKIP1_RANGE_END = 0x17;

    // The following ranges are defined in CEA-708B Section 7.4.7.
    public static final int CODE_C2_SKIP0_RANGE_START = 0x00;
    public static final int CODE_C2_SKIP0_RANGE_END = 0x07;
    public static final int CODE_C2_SKIP1_RANGE_START = 0x08;
    public static final int CODE_C2_SKIP1_RANGE_END = 0x0f;
    public static final int CODE_C2_SKIP2_RANGE_START = 0x10;
    public static final int CODE_C2_SKIP2_RANGE_END = 0x17;
    public static final int CODE_C2_SKIP3_RANGE_START = 0x18;
    public static final int CODE_C2_SKIP3_RANGE_END = 0x1f;

    // The following ranges are defined in CEA-708B Section 7.4.8.
    public static final int CODE_C3_SKIP4_RANGE_START = 0x80;
    public static final int CODE_C3_SKIP4_RANGE_END = 0x87;
    public static final int CODE_C3_SKIP5_RANGE_START = 0x88;
    public static final int CODE_C3_SKIP5_RANGE_END = 0x8f;

    // The following values are the special characters of CEA-708 spec.
    public static final int CODE_C0_NUL = 0x00;
    public static final int CODE_C0_ETX = 0x03;
    public static final int CODE_C0_BS = 0x08;
    public static final int CODE_C0_FF = 0x0c;
    public static final int CODE_C0_CR = 0x0d;
    public static final int CODE_C0_HCR = 0x0e;
    public static final int CODE_C0_EXT1 = 0x10;
    public static final int CODE_C0_P16 = 0x18;
    public static final int CODE_G0_MUSICNOTE = 0x7f;
    public static final int CODE_G2_TSP = 0x20;
    public static final int CODE_G2_NBTSP = 0x21;
    public static final int CODE_G2_BLK = 0x30;
    public static final int CODE_G3_CC = 0xa0;

    // The following values are the command bits of CEA-708 spec.
    public static final int CODE_C1_CW0 = 0x80;
    public static final int CODE_C1_CW1 = 0x81;
    public static final int CODE_C1_CW2 = 0x82;
    public static final int CODE_C1_CW3 = 0x83;
    public static final int CODE_C1_CW4 = 0x84;
    public static final int CODE_C1_CW5 = 0x85;
    public static final int CODE_C1_CW6 = 0x86;
    public static final int CODE_C1_CW7 = 0x87;
    public static final int CODE_C1_CLW = 0x88;
    public static final int CODE_C1_DSW = 0x89;
    public static final int CODE_C1_HDW = 0x8a;
    public static final int CODE_C1_TGW = 0x8b;
    public static final int CODE_C1_DLW = 0x8c;
    public static final int CODE_C1_DLY = 0x8d;
    public static final int CODE_C1_DLC = 0x8e;
    public static final int CODE_C1_RST = 0x8f;
    public static final int CODE_C1_SPA = 0x90;
    public static final int CODE_C1_SPC = 0x91;
    public static final int CODE_C1_SPL = 0x92;
    public static final int CODE_C1_SWA = 0x97;
    public static final int CODE_C1_DF0 = 0x98;
    public static final int CODE_C1_DF1 = 0x99;
    public static final int CODE_C1_DF2 = 0x9a;
    public static final int CODE_C1_DF3 = 0x9b;
    public static final int CODE_C1_DF4 = 0x9c;
    public static final int CODE_C1_DF5 = 0x9d;
    public static final int CODE_C1_DF6 = 0x9e;
    public static final int CODE_C1_DF7 = 0x9f;

    public static class CcPacket implements Comparable<CcPacket> {
        public final byte[] bytes;
        public final int ccCount;
        public final long pts;

        public CcPacket(byte[] bytes, int ccCount, long pts) {
            this.bytes = bytes;
            this.ccCount = ccCount;
            this.pts = pts;
        }

        @Override
        public int compareTo(@NonNull CcPacket another) {
            return Long.compare(pts, another.pts);
        }
    }

    /**
     * CEA-708B-specific color.
     */
    public static class CaptionColor {
        public static final int OPACITY_SOLID = 0;
        public static final int OPACITY_FLASH = 1;
        public static final int OPACITY_TRANSLUCENT = 2;
        public static final int OPACITY_TRANSPARENT = 3;

        private static final int[] COLOR_MAP = new int[] { 0x00, 0x0f, 0xf0, 0xff };
        private static final int[] OPACITY_MAP = new int[] { 0xff, 0xfe, 0x80, 0x00 };

        public final int opacity;
        public final int red;
        public final int green;
        public final int blue;

        public CaptionColor(int opacity, int red, int green, int blue) {
            this.opacity = opacity;
            this.red = red;
            this.green = green;
            this.blue = blue;
        }

        public int getArgbValue() {
            return Color.argb(
                    OPACITY_MAP[opacity], COLOR_MAP[red], COLOR_MAP[green], COLOR_MAP[blue]);
        }
    }

    /**
     * Caption event generated by {@link Cea708Parser}.
     */
    public static class CaptionEvent {
        @Cea708Parser.CaptionEmitType public final int type;
        public final Object obj;

        public CaptionEvent(int type, Object obj) {
            this.type = type;
            this.obj = obj;
        }
    }

    /**
     * Pen style information.
     */
    public static class CaptionPenAttr {
        // Pen sizes
        public static final int PEN_SIZE_SMALL = 0;
        public static final int PEN_SIZE_STANDARD = 1;
        public static final int PEN_SIZE_LARGE = 2;

        // Offsets
        public static final int OFFSET_SUBSCRIPT = 0;
        public static final int OFFSET_NORMAL = 1;
        public static final int OFFSET_SUPERSCRIPT = 2;

        public final int penSize;
        public final int penOffset;
        public final int textTag;
        public final int fontTag;
        public final int edgeType;
        public final boolean underline;
        public final boolean italic;

        public CaptionPenAttr(int penSize, int penOffset, int textTag, int fontTag, int edgeType,
                boolean underline, boolean italic) {
            this.penSize = penSize;
            this.penOffset = penOffset;
            this.textTag = textTag;
            this.fontTag = fontTag;
            this.edgeType = edgeType;
            this.underline = underline;
            this.italic = italic;
        }
    }

    /**
     * {@link CaptionColor} objects that indicate the foreground, background, and edge color of a
     * pen.
     */
    public static class CaptionPenColor {
        public final CaptionColor foregroundColor;
        public final CaptionColor backgroundColor;
        public final CaptionColor edgeColor;

        public CaptionPenColor(CaptionColor foregroundColor, CaptionColor backgroundColor,
                CaptionColor edgeColor) {
            this.foregroundColor = foregroundColor;
            this.backgroundColor = backgroundColor;
            this.edgeColor = edgeColor;
        }
    }

    /**
     * Location information of a pen.
     */
    public static class CaptionPenLocation {
        public final int row;
        public final int column;

        public CaptionPenLocation(int row, int column) {
            this.row = row;
            this.column = column;
        }
    }

    /**
     * Attributes of a caption window, which is defined in CEA-708B.
     */
    public static class CaptionWindowAttr {
        public static final int JUSTIFY_LEFT = 0;
        public static final int JUSTIFY_CENTER = 2;
        public static final int PRINT_LEFT_TO_RIGHT = 0;
        public static final int PRINT_RIGHT_TO_LEFT = 1;
        public static final int PRINT_TOP_TO_BOTTOM = 2;
        public static final int PRINT_BOTTOM_TO_TOP = 3;

        public final CaptionColor fillColor;
        public final CaptionColor borderColor;
        public final int borderType;
        public final boolean wordWrap;
        public final int printDirection;
        public final int scrollDirection;
        public final int justify;
        public final int effectDirection;
        public final int effectSpeed;
        public final int displayEffect;

        public CaptionWindowAttr(CaptionColor fillColor, CaptionColor borderColor, int borderType,
                boolean wordWrap, int printDirection, int scrollDirection, int justify,
                int effectDirection,
                int effectSpeed, int displayEffect) {
            this.fillColor = fillColor;
            this.borderColor = borderColor;
            this.borderType = borderType;
            this.wordWrap = wordWrap;
            this.printDirection = printDirection;
            this.scrollDirection = scrollDirection;
            this.justify = justify;
            this.effectDirection = effectDirection;
            this.effectSpeed = effectSpeed;
            this.displayEffect = displayEffect;
        }
    }

    /**
     * Construction information of the caption window of CEA-708B.
     */
    public static class CaptionWindow {
        public final int id;
        public final boolean visible;
        public final boolean rowLock;
        public final boolean columnLock;
        public final int priority;
        public final boolean relativePositioning;
        public final int anchorVertical;
        public final int anchorHorizontal;
        public final int anchorId;
        public final int rowCount;
        public final int columnCount;
        public final int penStyle;
        public final int windowStyle;

        public CaptionWindow(int id, boolean visible,
                boolean rowLock, boolean columnLock, int priority, boolean relativePositioning,
                int anchorVertical, int anchorHorizontal, int anchorId,
                int rowCount, int columnCount, int penStyle, int windowStyle) {
            this.id = id;
            this.visible = visible;
            this.rowLock = rowLock;
            this.columnLock = columnLock;
            this.priority = priority;
            this.relativePositioning = relativePositioning;
            this.anchorVertical = anchorVertical;
            this.anchorHorizontal = anchorHorizontal;
            this.anchorId = anchorId;
            this.rowCount = rowCount;
            this.columnCount = columnCount;
            this.penStyle = penStyle;
            this.windowStyle = windowStyle;
        }
    }
}
