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
 * limitations under the License
 */

package android.graphics.drawable.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.NinePatch;
import android.graphics.Picture;
import android.graphics.cts.R;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableContainer;
import android.graphics.drawable.DrawableContainer.DrawableContainerState;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LevelListDrawable;
import android.graphics.drawable.NinePatchDrawable;
import android.graphics.drawable.PaintDrawable;
import android.graphics.drawable.PictureDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.StateSet;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class DefaultFocusHighlightTest {

    // The target context.
    private Context mContext;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();
    }

    private static final int A_COLOR = 0x920424;
    private static final int[] NO_STATE_FOCUSED = new int[] { android.R.attr.state_enabled };
    private static final int[] ONLY_STATE_FOCUSED = new int[] { android.R.attr.state_focused };
    private static final int[] STATE_FOCUSED_WITH_POS =
            new int[] { android.R.attr.state_focused, android.R.attr.state_hovered };
    private static final int[] STATE_FOCUSED_WITH_NEG =
            new int[] { android.R.attr.state_focused,  -android.R.attr.state_hovered };
    private static final int[] STATE_FOCUSED_WITH_ENABLED =
            new int[] { android.R.attr.state_focused, android.R.attr.state_enabled };

    final static int[] FOCUSED_STATE =
            new int[] { android.R.attr.state_focused, android.R.attr.state_enabled };

    @UiThreadTest
    @Test
    public void testStateListDrawable() {
        Drawable d;
        // Empty state spec
        d = DrawableFactory.createStateListDrawable(
                new int[][] {}
            );
        d.setState(FOCUSED_STATE);
        assertFalse(d.hasFocusStateSpecified());

        // Wild card
        d = DrawableFactory.createStateListDrawable(
                new int[][] { StateSet.WILD_CARD }
            );
        d.setState(FOCUSED_STATE);
        assertFalse(d.hasFocusStateSpecified());

        // No state spec of state_focused=true
        d = DrawableFactory.createStateListDrawable(
                new int[][] { NO_STATE_FOCUSED }
            );
        d.setState(FOCUSED_STATE);
        assertFalse(d.hasFocusStateSpecified());

        // One state spec of only state_focused=true
        d = DrawableFactory.createStateListDrawable(
                new int[][] { ONLY_STATE_FOCUSED }
            );
        d.setState(FOCUSED_STATE);
        assertTrue(d.hasFocusStateSpecified());

        // One state spec of state_focused=true and something=true, but no state spec of
        // state_focused=true and something=false (something is not enabled)
        d = DrawableFactory.createStateListDrawable(
                new int[][] { STATE_FOCUSED_WITH_POS }
            );
        d.setState(FOCUSED_STATE);
        assertTrue(d.hasFocusStateSpecified());

        // One state spec of state_focused=true and something=true, and one spec of
        // state_focused=true and something=false (something is not enabled)
        d = DrawableFactory.createStateListDrawable(
            new int[][] { STATE_FOCUSED_WITH_POS, STATE_FOCUSED_WITH_NEG }
        );
        d.setState(FOCUSED_STATE);
        assertTrue(d.hasFocusStateSpecified());

        // One state spec of state_focused=true and enabled=true
        d = DrawableFactory.createStateListDrawable(
            new int[][] { STATE_FOCUSED_WITH_ENABLED }
        );
        d.setState(FOCUSED_STATE);
        assertTrue(d.hasFocusStateSpecified());
    }

    @UiThreadTest
    @Test
    public void testRippleDrawable() {
        Drawable d = DrawableFactory.createRippleDrawable();
        d.setState(FOCUSED_STATE);
        assertTrue(d.hasFocusStateSpecified());
    }

    @UiThreadTest
    @Test
    public void testPictureDrawable() {
        Drawable d = DrawableFactory.createPictureDrawable(null);
        d.setState(FOCUSED_STATE);
        assertFalse(d.hasFocusStateSpecified());

        d = DrawableFactory.createPictureDrawable(new Picture());
        d.setState(FOCUSED_STATE);
        assertFalse(d.hasFocusStateSpecified());
    }

    @UiThreadTest
    @Test
    public void testColorStateListHandledDrawable() {
        final Drawable[] drawables = new Drawable[] {
            DrawableFactory.createShapeDrawable(),
            DrawableFactory.createPaintDrawable(),
            DrawableFactory.createBitmapDrawable(mContext),
            DrawableFactory.createColorDrawable(),
            DrawableFactory.createGradientDrawable(),
            DrawableFactory.createNinePatchDrawable(mContext),
        };
        final ColorStateList[] stateLists = new ColorStateList[] {
            // Empty state spec
            new ColorStateList(
                new int[][] {  },
                new int[] {  }),
            // Wild card
            new ColorStateList(
                new int[][] { StateSet.WILD_CARD },
                new int[] { A_COLOR }),
            // No state spec of state_focused=true
            new ColorStateList(
                new int[][] { NO_STATE_FOCUSED },
                new int[] { A_COLOR }),
            // One state spec of only state_focused=true
            new ColorStateList(
                new int[][] { ONLY_STATE_FOCUSED },
                new int[] { A_COLOR }),
            // One state spec of state_focused=true and something=true,
            // but no state spec of state_focused=true and something=false
            new ColorStateList(
                new int[][] { STATE_FOCUSED_WITH_POS },
                new int[] { A_COLOR }),
            // One state spec of state_focused=true and something=true,
            // and one spec of state_focused=true and something=false
            new ColorStateList(
                new int[][] { STATE_FOCUSED_WITH_POS, STATE_FOCUSED_WITH_NEG },
                new int[] { A_COLOR, A_COLOR }),
        };
        final boolean[] expectedResults = new boolean[] {
            // Empty state spec
            false,
            // Wild card
            false,
            // No state spec of state_focused=true
            false,
            // One state spec of only state_focused=true
            true,
            // One state spec of state_focused=true and something=true,
            // but no state spec of state_focused=true and something=false
            true,
            // One state spec of state_focused=true and something=true,
            // and one spec of state_focused=true and something=false
            true
        };
        assertEquals(stateLists.length, expectedResults.length);
        for (Drawable drawable : drawables) {
            // No ColorStateList set
            String drawableName = drawable.getClass().toString();
            String errorMsg = "[" + drawableName + "] Testing no ColorStateList failed.";
            drawable.setState(FOCUSED_STATE);
            assertFalse(errorMsg, drawable.hasFocusStateSpecified());
            // With ColorStateList set
            for (int i = 0; i < stateLists.length; i++) {
                ColorStateList stateList = stateLists[i];
                boolean expectedResult = expectedResults[i];
                drawable.setTintList(stateList);
                errorMsg = "[" + drawableName + "] Testing ColorStateList No." + i + " failed.";

                drawable.setState(FOCUSED_STATE);
                if (expectedResult) {
                    assertTrue(errorMsg, drawable.hasFocusStateSpecified());
                } else {
                    assertFalse(errorMsg, drawable.hasFocusStateSpecified());
                }
            }
        }
    }

    @UiThreadTest
    @Test
    public void testDrawableContainer() {
        MockDrawableContainer container;
        DrawableContainerState containerState;

        // Empty
        container = new MockDrawableContainer();
        containerState = (DrawableContainerState) new LevelListDrawable().getConstantState();
        assertNotNull(containerState);
        container.setConstantState(containerState);
        container.setState(FOCUSED_STATE);
        assertFalse(container.hasFocusStateSpecified());

        // No drawable of state_focused=true
        container = new MockDrawableContainer();
        containerState = (DrawableContainerState) new LevelListDrawable().getConstantState();
        assertNotNull(containerState);
        container.setConstantState(containerState);
        containerState.addChild(DrawableFactory.createPaintDrawable());
        containerState.addChild(DrawableFactory.createBitmapDrawable(mContext));
        containerState.addChild(DrawableFactory.createColorDrawable());
        container.selectDrawable(0);
        container.setState(FOCUSED_STATE);
        assertFalse(container.hasFocusStateSpecified());
        container.selectDrawable(1);
        container.setState(FOCUSED_STATE);
        assertFalse(container.hasFocusStateSpecified());
        container.selectDrawable(2);
        container.setState(FOCUSED_STATE);
        assertFalse(container.hasFocusStateSpecified());

        // Only drawables of state_focused=true
        container = new MockDrawableContainer();
        containerState = (DrawableContainerState) new LevelListDrawable().getConstantState();
        assertNotNull(containerState);
        container.setConstantState(containerState);
        containerState.addChild(DrawableFactory.createRippleDrawable());
        containerState.addChild(
            DrawableFactory.createStateListDrawable(
                new int[][] { STATE_FOCUSED_WITH_POS, STATE_FOCUSED_WITH_NEG }
            )
        );
        container.selectDrawable(0);
        container.setState(FOCUSED_STATE);
        assertTrue(container.hasFocusStateSpecified());
        container.selectDrawable(1);
        container.setState(FOCUSED_STATE);
        assertTrue(container.hasFocusStateSpecified());

        // Both drawables of state_focused=true and state_focused=false
        containerState.addChild(DrawableFactory.createColorDrawable());
        container.selectDrawable(2);
        container.setState(FOCUSED_STATE);
        assertFalse(container.hasFocusStateSpecified());
        container.selectDrawable(1);
        container.setState(FOCUSED_STATE);
        assertTrue(container.hasFocusStateSpecified());
        container.selectDrawable(0);
        container.setState(FOCUSED_STATE);
        assertTrue(container.hasFocusStateSpecified());
    }

    static class DrawableFactory {
        static ShapeDrawable createShapeDrawable() {
            return new ShapeDrawable(new RectShape());
        }
        static PaintDrawable createPaintDrawable() {
            PaintDrawable paintDrawable = new PaintDrawable();
            paintDrawable.setCornerRadius(1.5f);
            return paintDrawable;
        }
        static BitmapDrawable createBitmapDrawable(Context context) {
            Bitmap bitmap = Bitmap.createBitmap(200, 300, Config.ARGB_8888);
            BitmapDrawable bitmapDrawable = new BitmapDrawable(context.getResources(), bitmap);
            return bitmapDrawable;
        }
        static ColorDrawable createColorDrawable() {
            return new ColorDrawable(A_COLOR);
        }
        static GradientDrawable createGradientDrawable() {
            GradientDrawable gradientDrawable = new GradientDrawable();
            gradientDrawable.setColor(A_COLOR);
            gradientDrawable.setCornerRadius(10f);
            return gradientDrawable;
        }
        static NinePatchDrawable createNinePatchDrawable(Context context) {
            Resources res = context.getResources();
            Bitmap bitmap = BitmapFactory.decodeResource(res, R.drawable.ninepatch_0);
            NinePatch np = new NinePatch(bitmap, bitmap.getNinePatchChunk(), null);
            NinePatchDrawable ninePatchDrawable = new NinePatchDrawable(res, np);
            return ninePatchDrawable;
        }
        static RippleDrawable createRippleDrawable() {
            RippleDrawable rippleDrawable =
                    new RippleDrawable(ColorStateList.valueOf(A_COLOR), null, null);
            return rippleDrawable;
        }
        static PictureDrawable createPictureDrawable(Picture picture) {
            PictureDrawable pictureDrawable = new PictureDrawable(picture);
            return pictureDrawable;
        }
        static StateListDrawable createStateListDrawable(int[][] stateList) {
            StateListDrawable drawable = new StateListDrawable();
            ColorDrawable colorDrawable = DrawableFactory.createColorDrawable();
            for (int i = 0; i < stateList.length; i++) {
                drawable.addState(stateList[i], colorDrawable);
            }
            return drawable;
        }
    }

    // We're calling protected methods in DrawableContainer.
    // So we have to extend it here to make it accessible.
    private class MockDrawableContainer extends DrawableContainer {
        @Override
        protected void setConstantState(DrawableContainerState state) {
            super.setConstantState(state);
        }
    }

}
