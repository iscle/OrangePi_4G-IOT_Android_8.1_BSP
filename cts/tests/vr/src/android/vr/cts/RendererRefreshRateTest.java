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
package android.vr.cts;

import java.util.concurrent.CountDownLatch;
import javax.microedition.khronos.opengles.GL10;

public class RendererRefreshRateTest extends RendererBasicTest {

    public RendererRefreshRateTest(CountDownLatch latch) {
        super(latch);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        doOnDrawFrame(gl);
        mLatch.countDown();
    }
}
