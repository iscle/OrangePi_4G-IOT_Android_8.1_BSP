/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package android.app.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.PictureInPictureParams;
import android.app.PictureInPictureParams.Builder;
import android.graphics.Rect;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.Rational;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

/**
 * Tests the {@link PictureInPictureParams} builder.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class PictureInPictureParamsBuilderTest {

    @Test
    public void testBuildParams() throws Exception {
        // Set the params
        Builder builder = new Builder()
                .setAspectRatio(new Rational(1, 2))
                .setActions(new ArrayList<>())
                .setSourceRectHint(new Rect(0, 0, 100, 100));

        PictureInPictureParams params = builder.build();
        assertTrue(Float.compare(0.5f, params.getAspectRatio()) == 0);
        assertTrue(params.getActions().isEmpty());
        assertEquals(new Rect(0, 0, 100, 100), params.getSourceRectHint());

        // Reset the params
        builder.setAspectRatio(null)
                .setActions(null)
                .setSourceRectHint(null);
        params = builder.build();

        assertTrue(Float.compare(0f, params.getAspectRatio()) == 0);
        assertNull(params.getActions());
        assertNull(params.getSourceRectHint());
    }
}
