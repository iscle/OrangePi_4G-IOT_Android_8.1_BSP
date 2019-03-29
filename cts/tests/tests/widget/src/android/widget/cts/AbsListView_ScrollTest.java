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

package android.widget.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.filters.Suppress;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.android.compatibility.common.util.CtsTouchUtils;
import com.android.compatibility.common.util.CtsTouchUtils.EventInjectionListener;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class AbsListView_ScrollTest {
    private static final String[] COUNTRY_LIST = new String[] {
            "Argentina", "Armenia", "Aruba", "Australia", "Belarus", "Belgium", "Belize", "Benin",
            "Botswana", "Brazil", "Cameroon", "China", "Colombia", "Costa Rica", "Cyprus",
            "Denmark", "Djibouti", "Ethiopia", "Fiji", "Finland", "France", "Gabon", "Germany",
            "Ghana", "Haiti", "Honduras", "Iceland", "India", "Indonesia", "Ireland", "Italy",
            "Japan", "Kiribati", "Laos", "Lesotho", "Liberia", "Malaysia", "Mongolia", "Myanmar",
            "Nauru", "Norway", "Oman", "Pakistan", "Philippines", "Portugal", "Romania", "Russia",
            "Rwanda", "Singapore", "Slovakia", "Slovenia", "Somalia", "Swaziland", "Togo", "Tuvalu",
            "Uganda", "Ukraine", "United States", "Vanuatu", "Venezuela", "Zimbabwe"
    };

    @Rule
    public ActivityTestRule<ListViewFixedCtsActivity> mActivityRule =
            new ActivityTestRule<>(ListViewFixedCtsActivity.class);

    private Instrumentation mInstrumentation;
    private AbsListView mListView;
    private Context mContext;
    private ArrayAdapter<String> mCountriesAdapter;
    private int mRowHeightPx;

    private static class ListScrollPosition {
        public int mFirstVisiblePosition;
        public int mFirstViewVerticalOffset;
    }

    @Before
    public void setup() throws Throwable {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getTargetContext();

        final Activity activity = mActivityRule.getActivity();

        PollingCheck.waitFor(() -> activity.hasWindowFocus());

        mCountriesAdapter = new ArrayAdapter<>(mContext,
                R.layout.listitemfixed_layout, COUNTRY_LIST);

        mListView = (ListView) activity.findViewById(R.id.listview_default);
        mActivityRule.runOnUiThread(() -> mListView.setAdapter(mCountriesAdapter));
        mInstrumentation.waitForIdleSync();

        mRowHeightPx = mContext.getResources().getDimensionPixelSize(R.dimen.listrow_height);
    }

    /**
     * Listener that allows waiting for the end of a scroll. When the tracked
     * {@link AbsListView} transitions to idle state, the passed {@link CountDownLatch}
     * is notified.
     */
    private class ScrollIdleListListener implements OnScrollListener {
        private CountDownLatch mLatchToNotify;

        public ScrollIdleListListener(CountDownLatch latchToNotify) {
            mLatchToNotify = latchToNotify;
        }

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
            if (scrollState == OnScrollListener.SCROLL_STATE_IDLE) {
                mListView.setOnScrollListener(null);
                mLatchToNotify.countDown();
            }
        }

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                int totalItemCount) {
        }
    }

    /**
     * Listener that allows waiting until a specific position in the list becomes visible.
     * When the tracked position in the {@link AbsListView} becomes visible, the passed
     * {@link CountDownLatch} is notified.
     */
    private class ScrollPositionListListener implements AbsListView.OnScrollListener {
        private CountDownLatch mLatchToNotify;
        private int mTargetPosition;

        public ScrollPositionListListener(CountDownLatch latchToNotify, int targetPosition) {
            mLatchToNotify = latchToNotify;
            mTargetPosition = targetPosition;
        }

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
        }

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                int totalItemCount) {
            // Is our target in current visible range?
            int lastVisibleItem = firstVisibleItem + visibleItemCount - 1;
            boolean isInRange = (mTargetPosition >= firstVisibleItem) &&
                    (mTargetPosition <= lastVisibleItem);
            if (!isInRange) {
                return;
            }

            // Is our target also fully visible?
            int visibleIndexOfTarget = mTargetPosition - firstVisibleItem;
            View targetChild = mListView.getChildAt(visibleIndexOfTarget);
            boolean isTargetFullyVisible = (targetChild.getTop() >= 0) &&
                    (targetChild.getBottom() <= mListView.getHeight());
            if (isTargetFullyVisible) {
                mListView.setOnScrollListener(null);
                mLatchToNotify.countDown();
            }
        }
    }

    private boolean isItemVisible(int position) {
        return (position >= mListView.getFirstVisiblePosition()) &&
                (position <= mListView.getLastVisiblePosition());
    }

    private void verifyScrollToPosition(int positionToScrollTo) throws Throwable {
        final int firstVisiblePosition = mListView.getFirstVisiblePosition();
        final int lastVisiblePosition = mListView.getLastVisiblePosition();

        if ((positionToScrollTo >= firstVisiblePosition) &&
                (positionToScrollTo <= lastVisiblePosition)) {
            // If it's already on the screen, we won't have any real scrolling taking
            // place, so our tracking based on scroll state change will time out. This
            // is why we're returning here early.
            return;
        }

        // Register a scroll listener on our ListView. The listener will notify our latch
        // when the scroll state changes to IDLE. If that never happens, the latch will
        // time out and fail the test.
        final CountDownLatch latch = new CountDownLatch(1);
        mListView.setOnScrollListener(new ScrollIdleListListener(latch));
        mActivityRule.runOnUiThread(() -> mListView.smoothScrollToPosition(
                positionToScrollTo));

        assertTrue("Timed out while waiting for the scroll to complete",
                latch.await(2, TimeUnit.SECONDS));

        // Verify that the position we've been asked to scroll to is visible
        assertTrue("Asked to scroll to " + positionToScrollTo + ", first visible is "
                + mListView.getFirstVisiblePosition() + ", last visible is "
                + mListView.getLastVisiblePosition(), isItemVisible(positionToScrollTo));
    }

    @Test
    public void testSmoothScrollToPositionDownUpDown() throws Throwable {
        final int itemCount = COUNTRY_LIST.length;

        // Scroll closer to the end of the list
        verifyScrollToPosition(itemCount - 10);
        // Scroll back towards the beginning of the list
        verifyScrollToPosition(5);
        // And then towards the end of the list again
        verifyScrollToPosition(itemCount - 1);
        // And back up to the middle of the list
        verifyScrollToPosition(itemCount / 2);
    }

    @Test
    public void testSmoothScrollToPositionEveryRow() throws Throwable {
        final int itemCount = COUNTRY_LIST.length;

        for (int i = 0; i < itemCount; i++) {
            // Scroll one row down
            verifyScrollToPosition(i);
        }

        for (int i = itemCount - 1; i >= 0; i--) {
            // Scroll one row up
            verifyScrollToPosition(i);
        }
    }

    private void verifyScrollToPositionWithBound(int positionToScrollTo, int boundPosition,
            boolean expectTargetPositionToBeVisibleAtEnd) throws Throwable {
        final int firstVisiblePosition = mListView.getFirstVisiblePosition();
        final int lastVisiblePosition = mListView.getLastVisiblePosition();

        if ((positionToScrollTo >= firstVisiblePosition) &&
                (positionToScrollTo <= lastVisiblePosition)) {
            // If it's already on the screen, we won't have any real scrolling taking
            // place, so our tracking based on scroll state change will time out. This
            // is why we're returning here early.
            return;
        }

        // Register a scroll listener on our ListView. The listener will notify our latch
        // when the scroll state changes to IDLE. If that never happens, the latch will
        // time out and fail the test.
        final CountDownLatch latch = new CountDownLatch(1);
        mListView.setOnScrollListener(new ScrollIdleListListener(latch));
        mActivityRule.runOnUiThread(() -> mListView.smoothScrollToPosition(
                positionToScrollTo, boundPosition));

        assertTrue("Timed out while waiting for the scroll to complete",
                latch.await(2, TimeUnit.SECONDS));

        // Verify that the bound position is visible
        assertTrue("Asked to scroll to " + positionToScrollTo + " with bound " + boundPosition
                + ", first visible is " + mListView.getFirstVisiblePosition()
                + ", last visible is " + mListView.getLastVisiblePosition(),
                isItemVisible(boundPosition));

        assertEquals("Asked to scroll to " + positionToScrollTo + " with bound " + boundPosition
                + ", first visible is " + mListView.getFirstVisiblePosition()
                + ", last visible is " + mListView.getLastVisiblePosition(),
                expectTargetPositionToBeVisibleAtEnd, isItemVisible(positionToScrollTo));
    }

    @Test
    public void testSmoothScrollToPositionWithBound() throws Throwable {
        // Our list is 300dp high and each row is 40dp high. Without being too precise,
        // the logic in this method relies on at least 8 and at most 10 items on the screen
        // at any time.

        // Scroll to 20 with bound at 6. This should result in the scroll stopping before it
        // gets to 20 so that 6 is still visible
        verifyScrollToPositionWithBound(20, 6, false);

        // Scroll to 40 with bound at 35. This should result in the scroll getting to 40 becoming
        // visible with 35 visible as well
        verifyScrollToPositionWithBound(40, 35, true);

        // Scroll to 10 with bound at 25. This should result in the scroll stopping before it
        // gets to 10 so that 25 is still visible
        verifyScrollToPositionWithBound(10, 25, false);

        // Scroll to 5 with bound at 8. This should result in the scroll getting to 5 becoming
        // visible with 8 visible as well
        verifyScrollToPositionWithBound(5, 8, true);
    }

    private void verifyScrollToPositionFromTop(int positionToScrollTo, int offset,
            int durationMs) throws Throwable {
        final int startTopPositionInListCoordinates =
                mListView.getFirstVisiblePosition() * mRowHeightPx -
                        mListView.getChildAt(0).getTop();
        int targetTopPositionInListCoordinates = positionToScrollTo * mRowHeightPx - offset;
        // Need to clamp it to account for requests that would scroll the content outside
        // of the available bounds
        targetTopPositionInListCoordinates = Math.max(0, targetTopPositionInListCoordinates);
        targetTopPositionInListCoordinates = Math.min(
                COUNTRY_LIST.length * mRowHeightPx - mListView.getHeight(),
                targetTopPositionInListCoordinates);

        if (targetTopPositionInListCoordinates == startTopPositionInListCoordinates) {
            // If it's already at the target state, we won't have any real scrolling taking
            // place, so our tracking based on scroll state change will time out. This
            // is why we're returning here early.
            return;
        }

        // Register a scroll listener on our ListView. The listener will notify our latch
        // when the scroll state changes to IDLE. If that never happens, the latch will
        // time out and fail the test.
        final CountDownLatch latch = new CountDownLatch(1);
        mListView.setOnScrollListener(new ScrollIdleListListener(latch));
        if (durationMs > 0) {
            mActivityRule.runOnUiThread(() -> mListView.smoothScrollToPositionFromTop(
                    positionToScrollTo, offset, durationMs));
        } else {
            mActivityRule.runOnUiThread(() -> mListView.smoothScrollToPositionFromTop(
                    positionToScrollTo, offset));
        }

        // Since position-based scroll is emulated as a series of mini-flings, scrolling
        // might take considerable time.
        int timeoutMs = durationMs > 0 ? 5000 + durationMs : 5000;
        assertTrue("Timed out while waiting for the scroll to complete",
                latch.await(timeoutMs, TimeUnit.MILLISECONDS));

        final int endTopPositionInListCoordinates =
                mListView.getFirstVisiblePosition() * mRowHeightPx -
                        mListView.getChildAt(0).getTop();

        assertEquals(targetTopPositionInListCoordinates, endTopPositionInListCoordinates);
    }

    @Test
    public void testSmoothScrollToPositionFromTop() throws Throwable {
        // Ask to scroll so that the top of position 5 is 10 pixels below the top edge of the list
        verifyScrollToPositionFromTop(5, 10, -1);

        // Ask to scroll so that the top of position 10 is right at the top edge of the list
        verifyScrollToPositionFromTop(10, 0, -1);

        // Ask to scroll so that the top of position 5 is 80 dps below the top edge of the list
        // (which means that since row height is 40 dps high, the top item should be 3
        verifyScrollToPositionFromTop(5, 2 * mRowHeightPx, -1);

        // Ask to scroll so that the top of position 20 is 20 pixels above the top edge of the list
        verifyScrollToPositionFromTop(20, 20, -1);

        // Ask to scroll so that the top of position 20 is right at the top edge of the list
        verifyScrollToPositionFromTop(20, 0, -1);

        // Ask to scroll so that the top of position 20 is 20 pixels below the top edge of the list
        verifyScrollToPositionFromTop(20, 20, -1);

        // Ask to scroll beyond the top of the content
        verifyScrollToPositionFromTop(0, -20, -1);
        verifyScrollToPositionFromTop(0, -60, -1);

        // Ask to scroll beyond the bottom of the content
        final int itemCount = COUNTRY_LIST.length;
        verifyScrollToPositionFromTop(itemCount - 1, 0, -1);
        verifyScrollToPositionFromTop(itemCount - 1, mListView.getHeight(), -1);
    }

    @Test
    public void testSmoothScrollToPositionFromTopWithTime() throws Throwable {
        // Ask to scroll so that the top of position 5 is 20 pixels below the top edge of the list
        verifyScrollToPositionFromTop(5, 10, 200);

        // Ask to scroll so that the top of position 10 is right at the top edge of the list
        verifyScrollToPositionFromTop(10, 0, 1000);

        // Ask to scroll so that the top of position 5 is 80 dps below the top edge of the list
        // (which means that since row height is 40 dps high, the top item should be 3
        verifyScrollToPositionFromTop(5, 2 * mRowHeightPx, 500);

        // Ask to scroll so that the top of position 20 is 20 pixels above the top edge of the list
        verifyScrollToPositionFromTop(20, 20, 100);

        // Ask to scroll so that the top of position 20 is right at the top edge of the list
        verifyScrollToPositionFromTop(20, 0, 700);

        // Ask to scroll so that the top of position 20 is 20 pixels below the top edge of the list
        verifyScrollToPositionFromTop(20, 20, 600);

        // Ask to scroll beyond the top of the content
        verifyScrollToPositionFromTop(0, -20, 2000);
        verifyScrollToPositionFromTop(0, -60, 300);

        // Ask to scroll beyond the bottom of the content
        final int itemCount = COUNTRY_LIST.length;
        verifyScrollToPositionFromTop(itemCount - 1, 0, 600);
        verifyScrollToPositionFromTop(itemCount - 1, mListView.getHeight(), 200);
    }

    @Test
    public void testCanScrollList() throws Throwable {
        final int itemCount = COUNTRY_LIST.length;

        assertEquals(0, mListView.getFirstVisiblePosition());

        // Verify that when we're at the top of the list, we can't scroll up but we can scroll
        // down.
        assertFalse(mListView.canScrollList(-1));
        assertTrue(mListView.canScrollList(1));

        // Scroll down to the very end of the list
        verifyScrollToPosition(itemCount - 1);
        assertEquals(itemCount - 1, mListView.getLastVisiblePosition());

        // Verify that when we're at the bottom of the list, we can't scroll down but we can scroll
        // up.
        assertFalse(mListView.canScrollList(1));
        assertTrue(mListView.canScrollList(-1));

        // Scroll up to the middle of the list
        final int itemInTheMiddle = itemCount / 2;
        verifyScrollToPosition(itemInTheMiddle);

        // Verify that when we're in the middle of the list, we can scroll both up and down.
        assertTrue(mListView.canScrollList(-1));
        assertTrue(mListView.canScrollList(1));
    }

    private void verifyScrollBy(int y) throws Throwable {
        // Here we rely on knowing the fixed pixel height of each row
        final int startTopPositionInListCoordinates =
                mListView.getFirstVisiblePosition() * mRowHeightPx -
                        mListView.getChildAt(0).getTop();

        // Since scrollListBy is a synchronous operation, we do not need to wait
        // until we can proceed to test the result
        mActivityRule.runOnUiThread(() -> mListView.scrollListBy(y));

        final int endTopPositionInListCoordinates =
                mListView.getFirstVisiblePosition() * mRowHeightPx -
                        mListView.getChildAt(0).getTop();

        // As specified in the Javadocs of AbsListView.scrollListBy, the actual scroll amount
        // will be capped by the list height minus one pixel
        final int listHeight = mListView.getHeight();
        final int expectedScrollAmount = (y > 0) ? Math.min(y, listHeight - 1)
                : Math.max(y, -(listHeight - 1));
        int expectedTopPositionInListCoordinates =
                startTopPositionInListCoordinates + expectedScrollAmount;
        // Need to clamp it to account for requests that would scroll the content outside
        // of the available bounds
        expectedTopPositionInListCoordinates = Math.max(0, expectedTopPositionInListCoordinates);
        expectedTopPositionInListCoordinates = Math.min(
                COUNTRY_LIST.length * mRowHeightPx - mListView.getHeight(),
                expectedTopPositionInListCoordinates);

        assertEquals(expectedTopPositionInListCoordinates, endTopPositionInListCoordinates);
    }

    @Test
    public void testScrollListBy() throws Throwable {
        final int listHeight = mListView.getHeight();
        final int itemCount = COUNTRY_LIST.length;

        // Scroll down by half row height
        verifyScrollBy(mRowHeightPx / 2);

        // Scroll up by full row height - verifying that we're going to stop at the top of the first
        // row
        verifyScrollBy(-mRowHeightPx);

        // Scroll down by slightly more than a screenful of rows - we expect it to be capped
        // by the list height minus one pixel.
        verifyScrollBy(listHeight + mRowHeightPx);

        // Scroll down by another half row
        verifyScrollBy(mRowHeightPx / 2);

        // Scroll up by full row height
        verifyScrollBy(-mRowHeightPx);

        // Now scroll all the way down (using position-based scrolling)
        verifyScrollToPosition(itemCount - 1);
        assertEquals(itemCount - 1, mListView.getLastVisiblePosition());

        // Scroll up by half row height
        verifyScrollBy(-mRowHeightPx / 2);

        // Scroll down by full row height - verifying that we're going to stop at the bottom of the
        // last row
        verifyScrollBy(mRowHeightPx);

        // Scroll up halfway into the list - we expect it to be capped by the list height minus
        // one pixel.
        verifyScrollBy(-itemCount * mRowHeightPx / 2);
    }

    @Test
    public void testListScrollAndTap() throws Throwable {
        // Start a programmatic scroll to position 30. We register a scroll listener on the list
        // to notify us when position 15 becomes visible.
        final CountDownLatch scrollLatch = new CountDownLatch(1);
        mListView.setOnScrollListener(new ScrollPositionListListener(scrollLatch, 15));
        mActivityRule.runOnUiThread(() -> mListView.smoothScrollToPosition(30));

        // Since position-based scroll is emulated as a series of mini-flings, scrolling
        // might take considerable time.
        assertTrue("Timed out while waiting for the scroll to complete",
                scrollLatch.await(5, TimeUnit.SECONDS));

        // Verify that we're here in the middle of the programmatic scroll
        assertTrue(mListView.getLastVisiblePosition() < 30);

        // Emulate tap in the middle of the list - this should stop our programmatic scroll.
        // Note that due to asynchronous nature of the moving pieces, we might still get one
        // more scroll frame as the injected motion events that constitute an emulated tap
        // are being processed by our list view.
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mListView);

        // Sleep for a second
        SystemClock.sleep(1000);

        // and verify that we're still haven't scrolled down to position 30
        assertTrue(mListView.getLastVisiblePosition() < 30);
    }

    private void verifyListScrollAndEmulateFlingGesture(boolean isDownwardsFlingGesture)
            throws Throwable {
        // Start a programmatic scroll to position 30. We register a scroll listener on the list
        // to notify us when position 15 becomes visible.
        final CountDownLatch scrollLatch = new CountDownLatch(1);
        mListView.setOnScrollListener(new ScrollPositionListListener(scrollLatch, 15));
        mActivityRule.runOnUiThread(() -> mListView.smoothScrollToPosition(30));

        // Since position-based scroll is emulated as a series of mini-flings, scrolling
        // might take considerable time.
        assertTrue("Timed out while waiting for the scroll to complete",
                scrollLatch.await(5, TimeUnit.SECONDS));

        // Verify that we're here in the middle of the programmatic scroll
        assertTrue(mListView.getLastVisiblePosition() < 30);

        final int firstVisiblePositionBeforeFling = mListView.getFirstVisiblePosition();

        // At this point the programmatic scroll is still going. Now emulate a fling
        // gesture and verify that we're going to get to the IDLE state
        final CountDownLatch flingLatch = new CountDownLatch(1);
        mListView.setOnScrollListener(new ScrollIdleListListener(flingLatch));
        CtsTouchUtils.emulateFlingGesture(mInstrumentation, mListView, isDownwardsFlingGesture);

        assertTrue("Timed out while waiting for the fling to complete",
                flingLatch.await(5, TimeUnit.SECONDS));

        // Note that the actual position in the list at the end of the fling depends on
        // the processing of the injected sequence of motion events that might differ at milli/micro
        // second level from run to run
        if (isDownwardsFlingGesture) {
            // Verify that the fling gesture has been processed, getting us closer to the
            // beginning of the list.
            assertTrue(mListView.getFirstVisiblePosition() < firstVisiblePositionBeforeFling);
        } else {
            // Verify that the fling gesture has been processed, getting us closer to the
            // end of the list.
            assertTrue(mListView.getFirstVisiblePosition() > firstVisiblePositionBeforeFling);
        }
    }

    @Test
    public void testListScrollAndEmulateDownwardsFlingGesture() throws Throwable {
        verifyListScrollAndEmulateFlingGesture(true);
    }

    @Test
    public void testListScrollAndEmulateUpwardsFlingGesture() throws Throwable {
        verifyListScrollAndEmulateFlingGesture(false);
    }

    private ListScrollPosition getCurrentScrollPosition() {
        ListScrollPosition result = new ListScrollPosition();
        result.mFirstVisiblePosition = mListView.getFirstVisiblePosition();
        result.mFirstViewVerticalOffset = mListView.getChildAt(0).getTop();
        return result;
    }

    @Test
    public void testListFlingWithZeroVelocity() throws Throwable {
        mListView.setVelocityScale(0.0f);

        final CountDownLatch flingLatch = new CountDownLatch(2);
        mListView.setOnScrollListener(new ScrollIdleListListener(flingLatch));

        final ListScrollPosition[] scrollPositionAfterUpEvent =
                new ListScrollPosition[1];
        final EventInjectionListener eventInjectionListener =
                new EventInjectionListener() {
                    @Override
                    public void onDownInjected(int xOnScreen, int yOnScreen) {
                    }

                    @Override
                    public void onMoveInjected(int[] xOnScreen, int[] yOnScreen) {
                    }

                    @Override
                    public void onUpInjected(int xOnScreen, int yOnScreen) {
                        scrollPositionAfterUpEvent[0] = getCurrentScrollPosition();
                        flingLatch.countDown();
                    }
                };
        CtsTouchUtils.emulateFlingGesture(mInstrumentation, mListView, false,
                eventInjectionListener);

        assertTrue("Timed out while waiting for the fling to complete",
                flingLatch.await(5, TimeUnit.SECONDS));

        // Since our velocity scale is 0, we expect that the emulated fling gesture didn't
        // result in any fling, but just a simple scroll that stopped at the ACTION_UP
        // event.
        final ListScrollPosition scrollPositionAtRest = getCurrentScrollPosition();

        assertEquals("First visible position", scrollPositionAtRest.mFirstVisiblePosition,
                scrollPositionAfterUpEvent[0].mFirstVisiblePosition);
        assertEquals("First view offset", scrollPositionAtRest.mFirstViewVerticalOffset,
                scrollPositionAfterUpEvent[0].mFirstViewVerticalOffset);
    }

    private static class LargeContentAdapter extends BaseAdapter {
        private final Context mContext;
        private final int mCount;
        private final LayoutInflater mLayoutInflater;

        public LargeContentAdapter(Context context, int count) {
            mContext = context;
            mCount = count;
            mLayoutInflater = LayoutInflater.from(mContext);
        }

        @Override
        public int getCount() {
            return mCount;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final TextView textView = (convertView != null) ? (TextView) convertView
                    : (TextView) mLayoutInflater.inflate(R.layout.listitemfixed_layout,
                            parent, false);
            textView.setText("Item " + position);
            return textView;
        }
    }

    @Suppress
    @Test
    // Disabled due to flakiness. CtsTouchUtils cannot guarantee the amount of scroll since it is
    // using sleeps and it is unreliable on cloud devices.
    public void testFriction() throws Throwable {
        // Set an adapter with 100K items so that no matter how fast our fling is, we won't
        // get to the bottom of the list in one fling
        mActivityRule.runOnUiThread(
                () -> mListView.setAdapter(new LargeContentAdapter(mContext, 100000)));
        mInstrumentation.waitForIdleSync();

        final CountDownLatch initialFlingLatch = new CountDownLatch(1);
        mListView.setOnScrollListener(new ScrollIdleListListener(initialFlingLatch));
        CtsTouchUtils.emulateFlingGesture(mInstrumentation, mListView, false);
        assertTrue("Timed out while waiting for the fling to complete",
                initialFlingLatch.await(5, TimeUnit.SECONDS));

        final int lastVisiblePositionAfterDefaultFling = mListView.getLastVisiblePosition();

        // Scroll back to the top of the list
        verifyScrollToPosition(0);
        // configure the fling to have less friction
        mListView.setFriction(ViewConfiguration.getScrollFriction() / 4.0f);
        // and do the fling again
        final CountDownLatch fastFlingLatch = new CountDownLatch(1);
        mListView.setOnScrollListener(new ScrollIdleListListener(fastFlingLatch));
        CtsTouchUtils.emulateFlingGesture(mInstrumentation, mListView, false);
        assertTrue("Timed out while waiting for the fling to complete",
                fastFlingLatch.await(5, TimeUnit.SECONDS));

        final int lastVisiblePositionAfterFastFling = mListView.getLastVisiblePosition();

        // We expect a fast fling (with lower scroll friction) to end up scrolling more
        // of our content
        assertTrue("Default fling ended at " + lastVisiblePositionAfterDefaultFling
                        + ", while fast fling ended at " + lastVisiblePositionAfterFastFling,
                lastVisiblePositionAfterFastFling > lastVisiblePositionAfterDefaultFling);

        // Scroll back to the top of the list
        verifyScrollToPosition(0);
        // configure the fling to have more friction
        mListView.setFriction(ViewConfiguration.getScrollFriction() * 4.0f);
        // and do the fling again
        final CountDownLatch slowFlingLatch = new CountDownLatch(1);
        mListView.setOnScrollListener(new ScrollIdleListListener(slowFlingLatch));
        CtsTouchUtils.emulateFlingGesture(mInstrumentation, mListView, false);
        assertTrue("Timed out while waiting for the fling to complete",
                slowFlingLatch.await(5, TimeUnit.SECONDS));

        final int lastVisiblePositionAfterSlowFling = mListView.getLastVisiblePosition();

        // We expect a slow fling (with higher scroll friction) to end up scrolling less
        // of our content
        assertTrue("Default fling ended at " + lastVisiblePositionAfterDefaultFling
                        + ", while slow fling ended at " + lastVisiblePositionAfterSlowFling,
                lastVisiblePositionAfterSlowFling < lastVisiblePositionAfterDefaultFling);
    }
}
