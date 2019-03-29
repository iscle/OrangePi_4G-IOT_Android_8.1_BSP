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
package android.transition.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.transition.ChangeClipBounds;
import android.transition.TransitionManager;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class ChangeClipBoundsTest extends BaseTransitionTest {
    private ChangeClipBounds mChangeClipBounds;

    @Override
    @Before
    public void setup() {
        super.setup();
        mChangeClipBounds = new ChangeClipBounds();
        mTransition = mChangeClipBounds;
        resetListener();
    }

    @Test
    public void testChangeClipBounds() throws Throwable {
        enterScene(R.layout.scene1);

        final View redSquare = mActivity.findViewById(R.id.redSquare);
        final Rect newClip = new Rect(redSquare.getLeft() + 10, redSquare.getTop() + 10,
                redSquare.getRight() - 10, redSquare.getBottom() - 10);

        mActivityRule.runOnUiThread(() -> {
            assertNull(redSquare.getClipBounds());
            TransitionManager.beginDelayedTransition(mSceneRoot, mChangeClipBounds);
            redSquare.setClipBounds(newClip);
        });
        waitForStart();
        Thread.sleep(150);
        mActivityRule.runOnUiThread(() -> {
            Rect midClip = redSquare.getClipBounds();
            assertNotNull(midClip);
            assertTrue(midClip.left > 0 && midClip.left < newClip.left);
            assertTrue(midClip.top > 0 && midClip.top < newClip.top);
            assertTrue(midClip.right < redSquare.getRight() && midClip.right > newClip.right);
            assertTrue(midClip.bottom < redSquare.getBottom() &&
                    midClip.bottom > newClip.bottom);
        });
        waitForEnd(400);

        mActivityRule.runOnUiThread(() -> {
            final Rect endRect = redSquare.getClipBounds();
            assertNotNull(endRect);
            assertEquals(newClip, endRect);
        });

        resetListener();
        mActivityRule.runOnUiThread(() -> {
            TransitionManager.beginDelayedTransition(mSceneRoot, mChangeClipBounds);
            redSquare.setClipBounds(null);
        });
        waitForStart();
        Thread.sleep(150);
        mActivityRule.runOnUiThread(() -> {
            Rect midClip = redSquare.getClipBounds();
            assertNotNull(midClip);
            assertTrue(midClip.left > 0 && midClip.left < newClip.left);
            assertTrue(midClip.top > 0 && midClip.top < newClip.top);
            assertTrue(midClip.right < redSquare.getRight() && midClip.right > newClip.right);
            assertTrue(midClip.bottom < redSquare.getBottom() &&
                    midClip.bottom > newClip.bottom);
        });
        waitForEnd(400);

        mActivityRule.runOnUiThread(() -> assertNull(redSquare.getClipBounds()));
    }
}

