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

import java.nio.Buffer;
import java.nio.ByteBuffer;

import static android.opengl.GLES30.GL_BUFFER_MAP_POINTER;
import static android.opengl.GLES30.GL_DYNAMIC_READ;
import static android.opengl.GLES30.GL_MAP_READ_BIT;
import static android.opengl.GLES30.GL_UNIFORM_BUFFER;
import static android.opengl.GLES30.glBindBuffer;
import static android.opengl.GLES30.glBufferData;
import static android.opengl.GLES30.glDeleteBuffers;
import static android.opengl.GLES30.glGenBuffers;
import static android.opengl.GLES30.glGetBufferPointerv;
import static android.opengl.GLES30.glMapBufferRange;
import static android.opengl.GLES30.glUnmapBuffer;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for functions that return a ByteBuffer.
 */
@SmallTest
@RunWith(BlockJUnit4ClassRunner.class) // DO NOT USE AndroidJUnit4, it messes up threading
public class ByteBufferTest extends GlTestBase {
    @Test
    public void testMapBufferRange() {
        // Always pass on ES 2.0
        if (Egl14Utils.getMajorVersion() >= 3) {
            int[] buffer = new int[1];
            glGenBuffers(1, buffer, 0);
            glBindBuffer(GL_UNIFORM_BUFFER, buffer[0]);
            glBufferData(GL_UNIFORM_BUFFER, 1024, null, GL_DYNAMIC_READ);

            Buffer mappedBuffer = glMapBufferRange(GL_UNIFORM_BUFFER, 0, 1024, GL_MAP_READ_BIT);

            assertNotNull(mappedBuffer);
            assertTrue(mappedBuffer instanceof ByteBuffer);

            Buffer pointerBuffer = glGetBufferPointerv(GL_UNIFORM_BUFFER, GL_BUFFER_MAP_POINTER);
            assertNotNull(pointerBuffer);
            assertTrue(pointerBuffer instanceof ByteBuffer);

            glUnmapBuffer(GL_UNIFORM_BUFFER);

            glBindBuffer(GL_UNIFORM_BUFFER, 0);
            glDeleteBuffers(1, buffer, 0);
        }
    }
}
