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

import static org.junit.Assert.fail;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.support.annotation.FloatRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RawRes;
import android.util.ArrayMap;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Map;

/**
 * Utilities for this package
 */
class Utils {
    private static final String LOG_TAG = "Utils";

    private static Map<Integer, File> sFiles = new ArrayMap<>();
    private static Map<Integer, Bitmap> sRenderedBitmaps = new ArrayMap<>();

    static final int A4_WIDTH_PTS = 595;
    static final int A4_HEIGHT_PTS = 841;
    static final int A4_PORTRAIT = android.graphics.pdf.cts.R.raw.a4_portrait_rgbb;
    static final int A5_PORTRAIT = android.graphics.pdf.cts.R.raw.a5_portrait_rgbb;

    /**
     * Create a {@link PdfRenderer} pointing to a file copied from a resource.
     *
     * @param docRes  The resource to load
     * @param context The context to use for creating the renderer
     *
     * @return the renderer
     *
     * @throws IOException If anything went wrong
     */
    static @NonNull PdfRenderer createRenderer(@RawRes int docRes, @NonNull Context context)
            throws IOException {
        File pdfFile = sFiles.get(docRes);

        if (pdfFile == null) {
            pdfFile = File.createTempFile("pdf", null, context.getCacheDir());

            // Copy resource to file so that we can open it as a ParcelFileDescriptor
            try (OutputStream os = new BufferedOutputStream(new FileOutputStream(pdfFile))) {
                try (InputStream is = new BufferedInputStream(
                        context.getResources().openRawResource(docRes))) {
                    byte buffer[] = new byte[1024];

                    while (true) {
                        int numRead = is.read(buffer, 0, buffer.length);

                        if (numRead == -1) {
                            break;
                        }

                        os.write(Arrays.copyOf(buffer, numRead));
                    }

                    os.flush();
                }
            }

            sFiles.put(docRes, pdfFile);
        }

        return new PdfRenderer(
                ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY));
    }

    /**
     * Render a pdf onto a bitmap <u>while</u> applying the transformation <u>in the</u>
     * PDFRenderer. Hence use PdfRenderer.*'s translation and clipping methods.
     *
     * @param bmWidth        The width of the destination bitmap
     * @param bmHeight       The height of the destination bitmap
     * @param docRes         The resolution of the doc
     * @param clipping       The clipping for the PDF document
     * @param transformation The transformation of the PDF
     * @param renderMode     The render mode to use to render the PDF
     * @param context        The context to use for creating the renderer
     *
     * @return The rendered bitmap
     */
    static @NonNull Bitmap renderWithTransform(int bmWidth, int bmHeight, @RawRes int docRes,
            @Nullable Rect clipping, @Nullable Matrix transformation, int renderMode,
            @NonNull Context context)
            throws IOException {
        try (PdfRenderer renderer = createRenderer(docRes, context)) {
            try (PdfRenderer.Page page = renderer.openPage(0)) {
                Bitmap bm = Bitmap.createBitmap(bmWidth, bmHeight, Bitmap.Config.ARGB_8888);

                page.render(bm, clipping, transformation, renderMode);

                return bm;
            }
        }
    }

    /**
     * Render a pdf onto a bitmap <u>and then</u> apply then render the resulting bitmap onto
     * another bitmap while applying the transformation. Hence use canvas' translation and clipping
     * methods.
     *
     * @param bmWidth        The width of the destination bitmap
     * @param bmHeight       The height of the destination bitmap
     * @param docRes         The resolution of the doc
     * @param clipping       The clipping for the PDF document
     * @param transformation The transformation of the PDF
     * @param renderMode     The render mode to use to render the PDF
     * @param context        The context to use for creating the renderer
     *
     * @return The rendered bitmap
     */
    private static @NonNull Bitmap renderAndThenTransform(int bmWidth, int bmHeight,
            @RawRes int docRes, @Nullable Rect clipping, @Nullable Matrix transformation,
            int renderMode, @NonNull Context context) throws IOException {
        Bitmap renderedBm;

        renderedBm = sRenderedBitmaps.get(docRes);

        if (renderedBm == null) {
            try (PdfRenderer renderer = Utils.createRenderer(docRes, context)) {
                try (PdfRenderer.Page page = renderer.openPage(0)) {
                    renderedBm = Bitmap.createBitmap(page.getWidth(), page.getHeight(),
                            Bitmap.Config.ARGB_8888);
                    page.render(renderedBm, null, null, renderMode);
                }
            }
            sRenderedBitmaps.put(docRes, renderedBm);
        }

        if (transformation == null) {
            // According to PdfRenderer.page#render transformation == null means that the bitmap
            // should be stretched to clipping (if provided) or otherwise destination size
            transformation = new Matrix();

            if (clipping != null) {
                transformation.postScale((float) clipping.width() / renderedBm.getWidth(),
                        (float) clipping.height() / renderedBm.getHeight());
                transformation.postTranslate(clipping.left, clipping.top);
            } else {
                transformation.postScale((float) bmWidth / renderedBm.getWidth(),
                        (float) bmHeight / renderedBm.getHeight());
            }
        }

        Bitmap transformedBm = Bitmap.createBitmap(bmWidth, bmHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(transformedBm);
        canvas.drawBitmap(renderedBm, transformation, null);

        Bitmap clippedBm;
        if (clipping != null) {
            clippedBm = Bitmap.createBitmap(bmWidth, bmHeight, Bitmap.Config.ARGB_8888);
            canvas = new Canvas(clippedBm);
            canvas.drawBitmap(transformedBm, clipping, clipping, null);
            transformedBm.recycle();
        } else {
            clippedBm = transformedBm;
        }

        return clippedBm;
    }

    /**
     * Get the fraction of non-matching pixels of two bitmaps. 1 == no pixels match, 0 == all pixels
     * match.
     *
     * @param a The first bitmap
     * @param b The second bitmap
     *
     * @return The fraction of non-matching pixels.
     */
    private static @FloatRange(from = 0, to = 1) float getNonMatching(@NonNull Bitmap a,
            @NonNull Bitmap b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            return 1;
        }

        int[] aPx = new int[a.getWidth() * a.getHeight()];
        int[] bPx = new int[b.getWidth() * b.getHeight()];
        a.getPixels(aPx, 0, a.getWidth(), 0, 0, a.getWidth(), a.getHeight());
        b.getPixels(bPx, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());

        int badPixels = 0;
        int totalPixels = a.getWidth() * a.getHeight();
        for (int i = 0; i < totalPixels; i++) {
            if (aPx[i] != bPx[i]) {
                badPixels++;
            }
        }

        return ((float) badPixels) / totalPixels;
    }

    /**
     * Render the PDF two times. Once with applying the transformation and clipping in the {@link
     * PdfRenderer}. The other time render the PDF onto a bitmap and then clip and transform that
     * image. The result should be the same beside some minor aliasing.
     *
     * @param width          The width of the resulting bitmap
     * @param height         The height of the resulting bitmap
     * @param docRes         The resource of the PDF document
     * @param clipping       The clipping to apply
     * @param transformation The transformation to apply
     * @param renderMode     The render mode to use
     * @param context        The context to use for creating the renderer
     *
     * @throws IOException
     */
    static void renderAndCompare(int width, int height, @RawRes int docRes,
            @Nullable Rect clipping, @Nullable Matrix transformation, int renderMode,
            @NonNull Context context) throws IOException {
        Bitmap a = renderWithTransform(width, height, docRes, clipping, transformation,
                renderMode, context);
        Bitmap b = renderAndThenTransform(width, height, docRes, clipping, transformation,
                renderMode, context);

        try {
            // We allow 1% aliasing error
            float nonMatching = getNonMatching(a, b);

            if (nonMatching == 0) {
                Log.d(LOG_TAG, "bitmaps match");
            } else if (nonMatching > 0.01) {
                fail("Testing width:" + width + ", height:" + height + ", docRes:" + docRes +
                        ", clipping:" + clipping + ", transform:" + transformation + ". Bitmaps " +
                        "differ by " + Math.ceil(nonMatching * 10000) / 100 +
                        "%. That is too much.");
            } else {
                Log.d(LOG_TAG, "bitmaps differ by " + Math.ceil(nonMatching * 10000) / 100 + "%");
            }
        } finally {
            a.recycle();
            b.recycle();
        }
    }

    /**
     * Run a runnable and expect an exception of a certain type.
     *
     * @param r             The {@link Invokable} to run
     * @param expectedClass The expected exception type
     */
    static void verifyException(@NonNull Invokable r,
            @NonNull Class<? extends Exception> expectedClass) {
        try {
            r.run();
        } catch (Exception e) {
            if (e.getClass().isAssignableFrom(expectedClass)) {
                return;
            } else {
                Log.e(LOG_TAG, "Incorrect exception", e);
                fail("Expected: " + expectedClass.getName() + ", got: " + e.getClass().getName());
            }
        }

        fail("Expected to have " + expectedClass.getName() + " exception thrown");
    }

    /**
     * A runnable that can throw an exception.
     */
    interface Invokable {
        void run() throws Exception;
    }
}
