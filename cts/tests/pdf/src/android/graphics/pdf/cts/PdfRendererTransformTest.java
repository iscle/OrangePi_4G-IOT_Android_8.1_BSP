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

package android.graphics.pdf.cts;

import static android.graphics.pdf.cts.Utils.A4_HEIGHT_PTS;
import static android.graphics.pdf.cts.Utils.A4_PORTRAIT;
import static android.graphics.pdf.cts.Utils.A4_WIDTH_PTS;
import static android.graphics.pdf.cts.Utils.renderAndCompare;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.pdf.PdfRenderer;
import android.graphics.pdf.PdfRenderer.Page;
import android.support.annotation.Nullable;
import android.support.annotation.RawRes;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Test for the {@link PdfRenderer}
 */
@RunWith(Parameterized.class)
public class PdfRendererTransformTest {
    private Context mContext;
    private int mWidth;
    private int mHeight;
    private int mDocRes;
    private @Nullable Rect mClipping;
    private @Nullable Matrix mTransformation;
    private int mRenderMode;

    public PdfRendererTransformTest(int width, int height, @RawRes int docRes,
            @Nullable Rect clipping, @Nullable Matrix transformation, int renderMode) {
        mWidth = width;
        mHeight = height;
        mDocRes = docRes;
        mClipping = clipping;
        mTransformation = transformation;
        mRenderMode = renderMode;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> getParameters() {
        int[] widths = new int[] { A4_WIDTH_PTS * 3 / 4, A4_WIDTH_PTS, A4_WIDTH_PTS * 4 / 3
        };
        int[] heights = new int[] { A4_HEIGHT_PTS * 3 / 4, A4_HEIGHT_PTS, A4_HEIGHT_PTS * 4 / 3
        };
        int[] rotations = new int[] { 0, 15, 90, 180 };
        int[] translations = new int[] { -A4_HEIGHT_PTS / 2, 0, A4_HEIGHT_PTS / 2 };
        float[] scales = { -0.5f, 0, 1, 1.5f };

        Collection<Object[]> params = new ArrayList<>();

        for (int rotation : rotations) {
            for (float scaleX : scales) {
                for (float scaleY : scales) {
                    for (int translateX : translations) {
                        for (int translateY : translations) {
                            Matrix transformation = new Matrix();
                            if (rotation != 0 || translateX != 0 || translateY != 0
                                    || scaleX != 0 || scaleY != 0) {
                                if (rotation != 0) {
                                    transformation.postRotate(rotation);
                                }

                                if (scaleX != 0 || scaleY != 0) {
                                    transformation.postScale(scaleX, scaleY);
                                }

                                if (translateX != 0 || translateY != 0) {
                                    transformation.postTranslate(translateX,
                                            translateY);
                                }
                            }

                            for (int width : widths) {
                                for (int height : heights) {
                                    params.add(
                                            new Object[] { width, height, A4_PORTRAIT, null,
                                                    transformation, Page.RENDER_MODE_FOR_DISPLAY
                                            });
                                }
                            }
                        }
                    }
                }
            }
        }

        return params;
    }

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();
    }

    // Note that the size annotation refers to the "size" of each individual parameterized run,
    // and not the "full" run.
    @SmallTest
    @Test
    public void test() throws Exception {
        renderAndCompare(mWidth, mHeight, mDocRes, mClipping, mTransformation, mRenderMode,
                mContext);
    }
}
