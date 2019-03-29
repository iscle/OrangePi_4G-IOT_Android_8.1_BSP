/*
  * Copyright (C) 2014 The Android Open Source Project
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
package android.uirendering.cts.testinfrastructure;

import android.uirendering.cts.R;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;

/**
 * This will contain the resource-based content for the DisplayModifiers
 */
public class ResourceModifier {
    private static ResourceModifier sInstance = null;

    public final BitmapShader repeatShader;
    public final BitmapShader translatedShader;
    public final BitmapShader scaledShader;
    public final LinearGradient horGradient;
    public final LinearGradient diagGradient;
    public final LinearGradient vertGradient;
    public final RadialGradient radGradient;
    public final SweepGradient sweepGradient;
    public final ComposeShader composeShader;
    public final ComposeShader nestedComposeShader;
    public final ComposeShader doubleGradientComposeShader;
    public final Bitmap bitmap;
    public final float[] bitmapVertices;
    public final int[] bitmapColors;

    public ResourceModifier(Resources resources) {
        bitmap = BitmapFactory.decodeResource(resources, R.drawable.sunset1);
        int texWidth = bitmap.getWidth();
        int texHeight = bitmap.getHeight();

        int drawWidth = ActivityTestBase.TEST_WIDTH;
        int drawHeight = ActivityTestBase.TEST_HEIGHT;

        repeatShader = new BitmapShader(bitmap, Shader.TileMode.REPEAT,
                Shader.TileMode.REPEAT);

        translatedShader = new BitmapShader(bitmap, Shader.TileMode.REPEAT,
                Shader.TileMode.REPEAT);
        Matrix matrix = new Matrix();
        matrix.setTranslate(texWidth / 2.0f, texHeight / 2.0f);
        matrix.postRotate(45, 0, 0);
        translatedShader.setLocalMatrix(matrix);

        scaledShader = new BitmapShader(bitmap, Shader.TileMode.MIRROR,
                Shader.TileMode.MIRROR);
        matrix = new Matrix();
        matrix.setScale(0.5f, 0.5f);
        scaledShader.setLocalMatrix(matrix);

        horGradient = new LinearGradient(0.0f, 0.0f, 1.0f, 0.0f,
                Color.RED, Color.GREEN, Shader.TileMode.CLAMP);
        matrix = new Matrix();
        matrix.setScale(drawHeight, 1.0f);
        matrix.postRotate(-90.0f);
        matrix.postTranslate(0.0f, drawHeight);
        horGradient.setLocalMatrix(matrix);

        diagGradient = new LinearGradient(0.0f, 0.0f, drawWidth / 2.0f, drawHeight / 2.0f,
                Color.BLUE, Color.RED, Shader.TileMode.CLAMP);

        vertGradient = new LinearGradient(0.0f, 0.0f, 0.0f, drawHeight / 2.0f,
                Color.YELLOW, Color.MAGENTA, Shader.TileMode.MIRROR);

        sweepGradient = new SweepGradient(drawWidth / 2.0f, drawHeight / 2.0f,
                Color.YELLOW, Color.MAGENTA);

        composeShader = new ComposeShader(repeatShader, horGradient,
                PorterDuff.Mode.MULTIPLY);

        final float width = bitmap.getWidth() / 8.0f;
        final float height = bitmap.getHeight() / 8.0f;

        bitmapVertices = new float[]{
                0.0f, 0.0f, width, 0.0f, width * 2, 0.0f, width * 3, 0.0f,
                0.0f, height, width, height, width * 2, height, width * 4, height,
                0.0f, height * 2, width, height * 2, width * 2, height * 2, width * 3, height * 2,
                0.0f, height * 4, width, height * 4, width * 2, height * 4, width * 4, height * 4,
        };

        bitmapColors = new int[]{
                0xffff0000, 0xff00ff00, 0xff0000ff, 0xffff0000,
                0xff0000ff, 0xffff0000, 0xff00ff00, 0xff00ff00,
                0xff00ff00, 0xff0000ff, 0xffff0000, 0xff00ff00,
                0x00ff0000, 0x0000ff00, 0x000000ff, 0x00ff0000,
        };

        // Use a repeating gradient with many colors to test the non simple case.
        radGradient = new RadialGradient(drawWidth / 4.0f, drawHeight / 4.0f, 4.0f,
                bitmapColors, null, Shader.TileMode.REPEAT);

        nestedComposeShader = new ComposeShader(radGradient, composeShader,
                PorterDuff.Mode.MULTIPLY);

        doubleGradientComposeShader = new ComposeShader(radGradient, vertGradient,
                PorterDuff.Mode.MULTIPLY);
    }

    public static ResourceModifier instance() {
        return sInstance;
    }

    public static void init(Resources resources) {
        sInstance = new ResourceModifier(resources);
    }
}
