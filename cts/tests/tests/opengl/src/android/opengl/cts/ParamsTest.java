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

package android.opengl.cts;

import android.support.test.filters.SmallTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;

import java.nio.IntBuffer;

import static android.opengl.GLES20.glDeleteBuffers;
import static android.opengl.GLES30.glGenBuffers;
import static org.junit.Assert.assertTrue;

/**
 * Tests for parameters validation.
 */
@SmallTest
@RunWith(BlockJUnit4ClassRunner.class) // DO NOT USE AndroidJUnit4, it messes up threading
public class ParamsTest extends GlTestBase {
    @Test(expected = IllegalArgumentException.class)
    public void testNullBufferParam() {
        glGenBuffers(1, null);
    }

    @Test
    public void testBufferParam() {
        IntBuffer buffer = IntBuffer.allocate(1);
        glGenBuffers(1, buffer);

        assertTrue(buffer.get() > 0);

        buffer.rewind();
        glDeleteBuffers(1, buffer);
    }
}
