/*
     * Copyright (C) 2014 The Android Open Source Project
     *
     * Licensed under the Apache License, Version 2.0 (the "License");
     * you may not use this file except in compliance with the License.
     * You may obtain a copy of the License at
     *
     *      http://www.apache.org/licenses/LICENSE2.0
     *
     * Unless required by applicable law or agreed to in riting, software
     * distributed under the License is distributed on an "AS IS" BASIS,
     * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     * See the License for the specific language governing permissions and
     * limitations under the License.
     */
package android.uirendering.cts.testinfrastructure;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Xfermode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Modifies the canvas and paint objects when called.
 */
public abstract class DisplayModifier {
    private static final RectF RECT = new RectF(0, 0, 100, 100);
    private static final float[] POINTS = new float[]{
            0, 40, 40, 0, 40, 80, 80, 40
    };
    private static final float[] TRIANGLE_POINTS = new float[]{
            40, 0, 80, 80, 80, 80, 0, 80, 0, 80, 40, 0
    };
    private static final int NUM_PARALLEL_LINES = 10;
    private static final float[] LINES = new float[NUM_PARALLEL_LINES * 8
            + TRIANGLE_POINTS.length];

    public static final PorterDuff.Mode[] PORTERDUFF_MODES = new PorterDuff.Mode[] {
        PorterDuff.Mode.SRC, PorterDuff.Mode.DST, PorterDuff.Mode.SRC_OVER,
        PorterDuff.Mode.DST_OVER, PorterDuff.Mode.SRC_IN, PorterDuff.Mode.DST_IN,
        PorterDuff.Mode.SRC_OUT, PorterDuff.Mode.DST_OUT, PorterDuff.Mode.SRC_ATOP,
        PorterDuff.Mode.DST_ATOP, PorterDuff.Mode.XOR, PorterDuff.Mode.MULTIPLY,
        PorterDuff.Mode.SCREEN
    };

    static {
        System.arraycopy(TRIANGLE_POINTS, 0, LINES, 0, TRIANGLE_POINTS.length);
        int index = TRIANGLE_POINTS.length;
        float val = 0;
        for (int i = 0; i < NUM_PARALLEL_LINES; i++) {
            LINES[index + 0] = 40;
            LINES[index + 1] = val;
            LINES[index + 2] = 80;
            LINES[index + 3] = val;
            index += 4;
            val += 8 + (2.0f / NUM_PARALLEL_LINES);
        }
        val = 0;
        for (int i = 0; i < NUM_PARALLEL_LINES; i++) {
            LINES[index + 0] = val;
            LINES[index + 1] = 40;
            LINES[index + 2] = val;
            LINES[index + 3] = 80;
            index += 4;
            val += 8 + (2.0f / NUM_PARALLEL_LINES);
        }
    }

    // This linked hash map contains each of the different things that can be done to a canvas and
    // paint object, like anti-aliasing or drawing. Within those LinkedHashMaps are the various
    // options for that specific topic, which contains a displaymodifier which will affect the
    // given canvas and paint objects.
    public static final LinkedHashMap<String, LinkedHashMap<String, DisplayModifier>> MAPS =
            new LinkedHashMap<String, LinkedHashMap<String,DisplayModifier>>() {
                {
                    put("aa", new LinkedHashMap<String, DisplayModifier>() {
                        {
                            put("true", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    paint.setAntiAlias(true);
                                }
                            });
                            put("false", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    paint.setAntiAlias(false);
                                }
                            });
                        }
                    });
                    put("style", new LinkedHashMap<String, DisplayModifier>() {
                        {
                            put("fill", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    paint.setStyle(Paint.Style.FILL);
                                }
                            });
                            put("stroke", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    paint.setStyle(Paint.Style.STROKE);
                                }
                            });
                            put("fillAndStroke", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    paint.setStyle(Paint.Style.FILL_AND_STROKE);
                                }
                            });
                        }
                    });
                    put("strokeWidth", new LinkedHashMap<String, DisplayModifier>() {
                        {
                            put("hair", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    paint.setStrokeWidth(0);
                                }
                            });
                            put("0.3", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    paint.setStrokeWidth(0.3f);
                                }
                            });
                            put("1", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    paint.setStrokeWidth(1);
                                }
                            });
                            put("5", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    paint.setStrokeWidth(5);
                                }
                            });
                            put("30", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    paint.setStrokeWidth(30);
                                }
                            });
                        }
                    });
                    put("strokeCap", new LinkedHashMap<String, DisplayModifier>() {
                        {
                            put("butt", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    paint.setStrokeCap(Paint.Cap.BUTT);
                                }
                            });
                            put("round", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    paint.setStrokeCap(Paint.Cap.ROUND);
                                }
                            });
                            put("square", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    paint.setStrokeCap(Paint.Cap.SQUARE);
                                }
                            });
                        }
                    });
                    put("strokeJoin", new LinkedHashMap<String, DisplayModifier>() {
                        {
                            put("bevel", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    paint.setStrokeJoin(Paint.Join.BEVEL);
                                }
                            });
                            put("round", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    paint.setStrokeJoin(Paint.Join.ROUND);
                                }
                            });
                            put("miter", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    paint.setStrokeJoin(Paint.Join.MITER);
                                }
                            });
                            // TODO: add miter0, miter1 etc to test miter distances
                        }
                    });

                    put("transform", new LinkedHashMap<String, DisplayModifier>() {
                        {
                            put("noTransform", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                }
                            });
                            put("rotate5", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    canvas.rotate(5);
                                }
                            });
                            put("rotate45", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    canvas.rotate(45);
                                }
                            });
                            put("rotate90", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    canvas.rotate(90);
                                    canvas.translate(0, -100);
                                }
                            });
                            put("scale2x2", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    canvas.scale(2, 2);
                                }
                            });
                            put("rot20scl1x4", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    canvas.rotate(20);
                                    canvas.scale(1, 4);
                                }
                            });
                        }
                    });

                    put("shader", new LinkedHashMap<String, DisplayModifier>() {
                        {
                            put("noShader", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                }
                            });
                            put("repeatShader", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    paint.setShader(ResourceModifier.instance().repeatShader);
                                }
                            });
                            put("translatedShader", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    paint.setShader(ResourceModifier.instance().translatedShader);
                                }
                            });
                            put("scaledShader", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    paint.setShader(ResourceModifier.instance().scaledShader);
                                }
                            });
                            put("composeShader", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    paint.setShader(ResourceModifier.instance().composeShader);
                                }
                            });
                            /*
                            put("bad composeShader", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    paint.setShader(ResourceModifier.instance().nestedComposeShader);
                                }
                            });
                            put("bad composeShader 2", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    paint.setShader(
                                            ResourceModifier.instance().doubleGradientComposeShader);
                                }
                            });
                            */
                            put("horGradient", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    paint.setShader(ResourceModifier.instance().horGradient);
                                }
                            });
                            put("diagGradient", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    paint.setShader(ResourceModifier.instance().diagGradient);
                                }
                            });
                            put("vertGradient", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    paint.setShader(ResourceModifier.instance().vertGradient);
                                }
                            });
                            put("radGradient", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    paint.setShader(ResourceModifier.instance().radGradient);
                                }
                            });
                            put("sweepGradient", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    paint.setShader(ResourceModifier.instance().sweepGradient);
                                }
                            });
                        }
                    });

                    put("xfermodes", new LinkedHashMap<String, DisplayModifier>() {
                        {
                            for (int i = 0 ; i < PORTERDUFF_MODES.length ; i++) {
                                put(PORTERDUFF_MODES[i].toString(),
                                        new XfermodeModifier(PORTERDUFF_MODES[i]));
                            }
                            put("lowSaturationColorMatrix", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    ColorMatrix matrix = new ColorMatrix();
                                    matrix.setSaturation(0.1f);
                                    paint.setColorFilter(new ColorMatrixColorFilter(matrix));
                                }
                            });
                            put("highSaturationColorMatrix", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    ColorMatrix matrix = new ColorMatrix();
                                    matrix.setSaturation(10.0f);
                                    paint.setColorFilter(new ColorMatrixColorFilter(matrix));
                                }
                            });
                        }
                    });

                    put("colorfilters", new LinkedHashMap<String, DisplayModifier>() {
                        {
                            for (int i = 0 ; i < PORTERDUFF_MODES.length ; i++) {
                                put(PORTERDUFF_MODES[i].toString(),
                                        new ColorFilterModifier(PORTERDUFF_MODES[i]));
                            }
                        }
                    });

                    // FINAL MAP: DOES ACTUAL DRAWING
                    put("drawing", new LinkedHashMap<String, DisplayModifier>() {
                        {
                            put("roundRect", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    canvas.drawRoundRect(RECT, 20, 20, paint);
                                }
                            });
                            put("rect", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    canvas.drawRect(RECT, paint);
                                }
                            });
                            put("circle", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    canvas.drawCircle(100, 100, 75, paint);
                                }
                            });
                            put("oval", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    canvas.drawOval(RECT, paint);
                                }
                            });
                            put("lines", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    canvas.drawLines(LINES, paint);
                                }
                            });
                            put("plusPoints", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    canvas.drawPoints(POINTS, paint);
                                }
                            });
                            put("text", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    paint.setTextSize(20);
                                    canvas.drawText("TEXTTEST", 0, 50, paint);
                                }
                            });
                            put("shadowtext", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    paint.setTextSize(20);
                                    paint.setShadowLayer(3.0f, 0.0f, 3.0f, 0xffff00ff);
                                    canvas.drawText("TEXTTEST", 0, 50, paint);
                                }
                            });
                            put("bitmapMesh", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    canvas.drawBitmapMesh(ResourceModifier.instance().bitmap, 3, 3,
                                            ResourceModifier.instance().bitmapVertices, 0, null, 0,
                                            null);
                                }
                            });
                            put("arc", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    canvas.drawArc(RECT, 260, 285, false, paint);
                                }
                            });
                            put("arcFromCenter", new DisplayModifier() {
                                @Override
                                public void modifyDrawing(Paint paint, Canvas canvas) {
                                    canvas.drawArc(RECT, 260, 285, true, paint);
                                }
                            });
                        }
                    });
                    // WARNING: DON'T PUT MORE MAPS BELOW THIS
                }
            };

    abstract public void modifyDrawing(Paint paint, Canvas canvas);

    public static class Accessor {
        public final static int AA_MASK =               0x1 << 0;
        public final static int STYLE_MASK =            0x1 << 1;
        public final static int STROKE_WIDTH_MASK =     0x1 << 2;
        public final static int STROKE_CAP_MASK =       0x1 << 3;
        public final static int STROKE_JOIN_MASK =      0x1 << 4;
        public final static int TRANSFORM_MASK =        0x1 << 5;
        public final static int SHADER_MASK =           0x1 << 6;
        public final static int XFERMODE_MASK =         0x1 << 7;
        public final static int COLOR_FILTER_MASK =     0x1 << 8;
        public final static int SHAPES_MASK =           0x1 << 9;
        public final static int ALL_OPTIONS_MASK =      (0x1 << 10) - 1;
        public final static int SHAPES_INDEX = 9;
        public final static int XFERMODE_INDEX = 7;
        private final int mMask;

        private String mDebugString;
        private int[] mIndices;
        private LinkedHashMap<String, LinkedHashMap<String, DisplayModifier>> mDisplayMap;

        public Accessor(int mask) {
            int totalModifiers = Integer.bitCount(mask);
            mIndices = new int[totalModifiers];
            mMask = mask;
            // Create a Display Map of the valid indices
            mDisplayMap = new LinkedHashMap<String, LinkedHashMap<String, DisplayModifier>>();
            int index = 0;
            for (String key : DisplayModifier.MAPS.keySet()) {
                if (validIndex(index)) {
                    mDisplayMap.put(key, DisplayModifier.MAPS.get(key));
                }
                index++;
            }
            mDebugString = "";
        }

        private LinkedHashMap<String, DisplayModifier> getMapAtIndex(int index) {
            int i = 0;
            for (LinkedHashMap<String, DisplayModifier> map : mDisplayMap.values()) {
                if (i == index) {
                    return map;
                }
                i++;
            }
            return null;
        }

        /**
         * This will create the next combination of drawing commands. If we have done every combination,
         * then we will return false.
         * @return true if there is more combinations to do
         */
        public boolean step() {
            int modifierMapIndex = mIndices.length - 1;
            // Start from the last map, and loop until it is at the front
            while (modifierMapIndex >= 0) {
                LinkedHashMap<String, DisplayModifier> map = getMapAtIndex(modifierMapIndex);
                mIndices[modifierMapIndex]++;

                // If we are still at a valid index, then we don't need to update any others
                if (mIndices[modifierMapIndex] < map.size()) {
                    break;
                }

                // If we updated and it was outside the boundary, and it was the last index then
                // we are done
                if (modifierMapIndex == 0) {
                    return false;
                }
                // If we ran off the end of the map, we need to update one more down the list
                mIndices[modifierMapIndex] = 0;

                modifierMapIndex--;
            }
            getModifierList(); // Just to update mDebugString
            return true;
        }

        /**
         * Modifies the canvas and paint given for the particular combination currently
         */
        public void modifyDrawing(Canvas canvas, Paint paint) {
            final ArrayList<DisplayModifier> modifierArrayList = getModifierList();
            for (DisplayModifier modifier : modifierArrayList) {
                modifier.modifyDrawing(paint, canvas);
            }
        }

        /**
         * Gets a list of all the current modifications to be used.
         */
        private ArrayList<DisplayModifier> getModifierList() {
            ArrayList<DisplayModifier> modifierArrayList = new ArrayList<DisplayModifier>();
            int mapIndex = 0;
            mDebugString = "";

            // Through each possible category of modification
            for (Map.Entry<String, LinkedHashMap<String, DisplayModifier>> entry :
                    mDisplayMap.entrySet()) {
                int displayModifierIndex = mIndices[mapIndex];
                if (!mDebugString.isEmpty()) {
                    mDebugString += ", ";
                }
                mDebugString += entry.getKey();
                // Loop until we find the modification we are going to use
                for (Map.Entry<String, DisplayModifier> modifierEntry :
                        entry.getValue().entrySet()) {
                    // Once we find the modification we want, then we will add it to the list,
                    // and the last applied modifications
                    if (displayModifierIndex == 0) {
                        mDebugString += ": " + modifierEntry.getKey();
                        modifierArrayList.add(modifierEntry.getValue());
                        break;
                    }
                    displayModifierIndex--;
                }
                mapIndex++;
            }
            return modifierArrayList;
        }

        public String getDebugString() {
            return mDebugString;
        }

        /**
         * Using the given masks, it tells if the map at the given index should be used, or not.
         */
        private boolean validIndex(int index) {
            return (mMask & (0x1 << index)) != 0;
        }
    }

    private static class XfermodeModifier extends DisplayModifier {
        private Xfermode mXfermode;

        public XfermodeModifier(PorterDuff.Mode mode) {
            mXfermode = new PorterDuffXfermode(mode);
        }

        @Override
        public void modifyDrawing(Paint paint, Canvas canvas) {
            paint.setXfermode(mXfermode);
        }
    }

    private static class ColorFilterModifier extends DisplayModifier {
        private static final int FILTER_COLOR = 0xFFBB0000;
        private ColorFilter mColorFilter;

        public ColorFilterModifier(PorterDuff.Mode mode) {
            mColorFilter = new PorterDuffColorFilter(FILTER_COLOR, mode);
        }

        @Override
        public void modifyDrawing(Paint paint, Canvas canvas) {
            paint.setColorFilter(mColorFilter);
        }
    }
}
