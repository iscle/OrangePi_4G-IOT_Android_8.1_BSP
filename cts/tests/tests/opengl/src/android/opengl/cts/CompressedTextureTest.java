/*
 * Copyright (C) 2011 The Android Open Source Project
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

import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class CompressedTextureTest {
    @Rule
    public ActivityTestRule<CompressedTextureCtsActivity> mActivityRule =
            new ActivityTestRule<>(CompressedTextureCtsActivity.class, false, false);

    private void launchTest(String format) throws Exception {
        Intent intent = new Intent(InstrumentationRegistry.getTargetContext(),
                CompressedTextureCtsActivity.class);
        intent.putExtra("TextureFormat", format);
        CompressedTextureCtsActivity activity = mActivityRule.launchActivity(intent);
        activity.finish();
        assertTrue(activity.getPassed());
    }

    @Test
    public void testTextureUncompressed() throws Exception {
        launchTest(CompressedTextureLoader.TEXTURE_UNCOMPRESSED);
    }

    @Test
    public void testTextureETC1() throws Exception {
        launchTest(CompressedTextureLoader.TEXTURE_ETC1);
    }

    @Test
    public void testTexturePVRTC() throws Exception {
        launchTest(CompressedTextureLoader.TEXTURE_PVRTC);
    }

    @Test
    public void testTextureS3TC() throws Exception {
        launchTest(CompressedTextureLoader.TEXTURE_S3TC);
    }

    @Ignore
    @Test
    public void testTextureATC() throws Exception {
        launchTest(CompressedTextureLoader.TEXTURE_ATC);
    }
}
