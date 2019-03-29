package android.uirendering.cts.testclasses;

import static org.junit.Assert.assertFalse;

import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Rect;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.uirendering.cts.R;
import android.uirendering.cts.bitmapverifiers.BitmapVerifier;
import android.uirendering.cts.bitmapverifiers.RectVerifier;
import android.uirendering.cts.testclasses.view.UnclippedBlueView;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;
import android.uirendering.cts.testinfrastructure.ViewInitializer;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This tests view clipping by modifying properties of blue_padded_layout, and validating
 * the resulting rect of content.
 *
 * Since the layout is blue on a white background, this is always done with a RectVerifier.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class ViewClippingTests extends ActivityTestBase {
    static final Rect FULL_RECT = new Rect(0, 0, 90, 90);
    static final Rect BOUNDS_RECT = new Rect(0, 0, 80, 80);
    static final Rect PADDED_RECT = new Rect(15, 16, 63, 62);
    static final Rect OUTLINE_RECT = new Rect(1, 2, 78, 79);
    static final Rect CLIP_BOUNDS_RECT = new Rect(10, 20, 50, 60);

    static final ViewInitializer BOUNDS_CLIP_INIT =
            rootView -> ((ViewGroup)rootView).setClipChildren(true);

    static final ViewInitializer PADDING_CLIP_INIT = rootView -> {
        ViewGroup child = (ViewGroup) rootView.findViewById(R.id.child);
        child.setClipToPadding(true);
        child.setWillNotDraw(true);
        child.addView(new UnclippedBlueView(rootView.getContext()));
    };

    static final ViewInitializer OUTLINE_CLIP_INIT = rootView -> {
        View child = rootView.findViewById(R.id.child);
        child.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRect(OUTLINE_RECT);
            }
        });
        child.setClipToOutline(true);
    };

    static final ViewInitializer CLIP_BOUNDS_CLIP_INIT =
            view -> view.setClipBounds(CLIP_BOUNDS_RECT);

    static BitmapVerifier makeClipVerifier(Rect blueBoundsRect) {
        // very high error tolerance, since all these tests care about is clip alignment
        return new RectVerifier(Color.WHITE, Color.BLUE, blueBoundsRect, 75);
    }

    @Test
    public void testSimpleUnclipped() {
        createTest()
                .addLayout(R.layout.blue_padded_layout, null)
                .runWithVerifier(makeClipVerifier(FULL_RECT));
    }

    @Test
    public void testSimpleBoundsClip() {
        createTest()
                .addLayout(R.layout.blue_padded_layout, BOUNDS_CLIP_INIT)
                .runWithVerifier(makeClipVerifier(BOUNDS_RECT));
    }

    @Test
    public void testSimpleClipBoundsClip() {
        createTest()
                .addLayout(R.layout.blue_padded_layout, CLIP_BOUNDS_CLIP_INIT)
                .runWithVerifier(makeClipVerifier(CLIP_BOUNDS_RECT));
    }

    @Test
    public void testSimplePaddingClip() {
        createTest()
                .addLayout(R.layout.blue_padded_layout, PADDING_CLIP_INIT)
                .runWithVerifier(makeClipVerifier(PADDED_RECT));
    }
    // TODO: add tests with clip + scroll, and with interesting combinations of the above

    @Test
    public void testSimpleOutlineClip() {
        // NOTE: Only HW is supported
        createTest()
                .addLayout(R.layout.blue_padded_layout, OUTLINE_CLIP_INIT, true)
                .runWithVerifier(makeClipVerifier(OUTLINE_RECT));

        // SW ignores the outline clip
        createTest()
                .addLayout(R.layout.blue_padded_layout, OUTLINE_CLIP_INIT, false)
                .runWithVerifier(makeClipVerifier(FULL_RECT));
    }

    @Test
    public void testOvalOutlineClip() {
        // In hw this works because clipping to a non-round rect isn't supported, and is no-op'd.
        // In sw this works because Outline clipping isn't supported.
        createTest()
                .addLayout(R.layout.blue_padded_layout, view -> {
                    view.setOutlineProvider(new ViewOutlineProvider() {
                        Path mPath = new Path();
                        @Override
                        public void getOutline(View view, Outline outline) {
                            mPath.reset();
                            mPath.addOval(0, 0, view.getWidth(), view.getHeight(),
                                    Path.Direction.CW);
                            outline.setConvexPath(mPath);
                            assertFalse(outline.canClip()); // NOTE: non-round-rect, so can't clip
                        }
                    });
                    view.setClipToOutline(true); // should do nothing, since non-rect clip
                })
                .runWithVerifier(makeClipVerifier(FULL_RECT));
    }
}
