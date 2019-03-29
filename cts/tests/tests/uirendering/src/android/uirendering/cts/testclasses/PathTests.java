package android.uirendering.cts.testclasses;

import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.uirendering.cts.R;
import android.uirendering.cts.bitmapcomparers.MSSIMComparer;
import android.uirendering.cts.bitmapverifiers.GoldenImageVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;

import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class PathTests extends ActivityTestBase {

    @Test
    public void testTextPathWithOffset() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    Paint paint = new Paint();
                    paint.setColor(Color.BLACK);
                    paint.setAntiAlias(true);
                    paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                    paint.setTextSize(26);
                    Path path = new Path();
                    String text = "Abc";
                    paint.getTextPath(text, 0, text.length(), 0, 0, path);
                    path.offset(0, 50);
                    canvas.drawPath(path, paint);
                })
                .runWithVerifier(new GoldenImageVerifier(getActivity(),
                        R.drawable.text_path_with_offset, new MSSIMComparer(0.92)));
    }

    @Test
    public void testPathApproximate_circle() {
        final Path path = new Path();
        path.addCircle(45, 45, 40, Path.Direction.CW);
        verifyPathApproximation(path, R.drawable.pathtest_path_approximate_circle);
    }

    @Test
    public void testPathApproximate_rect() {
        final Path path = new Path();
        path.addRect(5, 5, 85, 85, Path.Direction.CW);
        verifyPathApproximation(path, R.drawable.pathtest_path_approximate_rect);
    }

    @Test
    public void testPathApproximate_quads() {
        final Path path = new Path();
        path.moveTo(5, 5);
        path.quadTo(45, 45, 85, 5);
        path.quadTo(45, 45, 85, 85);
        path.quadTo(45, 45, 5, 85);
        path.quadTo(45, 45, 5, 5);
        verifyPathApproximation(path, R.drawable.pathtest_path_approximate_quads);
    }

    @Test
    public void testPathApproximate_cubics() {
        final Path path = new Path();
        path.moveTo(5, 5);
        path.cubicTo(45, 45, 45, 45, 85, 5);
        path.cubicTo(45, 45, 45, 45, 85, 85);
        path.cubicTo(45, 45, 45, 45, 5, 85);
        path.cubicTo(45, 45, 45, 45, 5, 5);
        verifyPathApproximation(path, R.drawable.pathtest_path_approximate_cubics);
    }

    private void verifyPathApproximation(Path path, int goldenResourceId) {
        float[] approx = path.approximate(0.5f);
        final Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStrokeWidth(2);
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    float lastX = approx[1];
                    float lastY = approx[2];
                    for (int i = 3; i < approx.length; i += 3) {
                        float x = approx[i + 1];
                        float y = approx[i + 2];
                        canvas.drawLine(lastX, lastY, x, y, paint);
                        lastX = x;
                        lastY = y;
                    }
                })
                .runWithVerifier(new GoldenImageVerifier(getActivity(),
                        goldenResourceId, new MSSIMComparer(0.98)));
    }
}
