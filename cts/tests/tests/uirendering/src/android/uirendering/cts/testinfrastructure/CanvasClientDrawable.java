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

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;

public class CanvasClientDrawable extends Drawable {
    private final CanvasClient mCanvasClient;

    public CanvasClientDrawable(CanvasClient canvasClient) {
        mCanvasClient = canvasClient;
    }

    @Override
    public void draw(Canvas canvas) {
        int saveCount = canvas.save();
        canvas.clipRect(0, 0, ActivityTestBase.TEST_WIDTH, ActivityTestBase.TEST_HEIGHT);
        mCanvasClient.draw(canvas, ActivityTestBase.TEST_WIDTH, ActivityTestBase.TEST_HEIGHT);
        canvas.restoreToCount(saveCount);
    }

    @Override
    public void setAlpha(int alpha) {}

    @Override
    public void setColorFilter(ColorFilter colorFilter) {}

    @Override
    public int getOpacity() {
        return 0;
    }
}
