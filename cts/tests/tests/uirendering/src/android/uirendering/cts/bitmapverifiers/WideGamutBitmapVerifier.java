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

package android.uirendering.cts.bitmapverifiers;

import android.graphics.Bitmap;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public abstract class WideGamutBitmapVerifier extends BitmapVerifier {
    @Override
    public boolean verify(Bitmap bitmap) {
        ByteBuffer dst = ByteBuffer.allocateDirect(bitmap.getAllocationByteCount());
        bitmap.copyPixelsToBuffer(dst);
        dst.rewind();
        dst.order(ByteOrder.LITTLE_ENDIAN);

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        return verify(dst, 0, bitmap.getRowBytes(), width, height);
    }

    public abstract boolean verify(ByteBuffer bitmap, int offset, int stride,
            int width, int height);

    @Override
    public boolean verify(int[] bitmap, int offset, int stride, int width, int height) {
        // This method is never called, we use
        // verify(ByteBuffer bitmap, int offset, int stride, int width, int height) instead
        return false;
    }
}
