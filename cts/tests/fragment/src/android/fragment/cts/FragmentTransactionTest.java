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
 * limitations under the License.
 */

package android.fragment.cts;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * Tests usage of the {@link FragmentTransaction} class.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class FragmentTransactionTest {

    @Rule
    public ActivityTestRule<FragmentTestActivity> mActivityRule =
            new ActivityTestRule<>(FragmentTestActivity.class);

    private FragmentTestActivity mActivity;
    private int mOnBackStackChangedTimes;
    private FragmentManager.OnBackStackChangedListener mOnBackStackChangedListener;

    @Before
    public void setUp() {
        mActivity = mActivityRule.getActivity();
        mOnBackStackChangedTimes = 0;
        mOnBackStackChangedListener = new FragmentManager.OnBackStackChangedListener() {
            @Override
            public void onBackStackChanged() {
                mOnBackStackChangedTimes++;
            }
        };
        mActivity.getFragmentManager().addOnBackStackChangedListener(mOnBackStackChangedListener);
    }

    @After
    public void tearDown() {
        mActivity.getFragmentManager()
                .removeOnBackStackChangedListener(mOnBackStackChangedListener);
        mOnBackStackChangedListener = null;
    }

    @Test
    public void testAddTransactionWithValidFragment() throws Throwable {
        final Fragment fragment = new CorrectFragment();
        mActivityRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mActivity.getFragmentManager().beginTransaction()
                        .add(android.R.id.content, fragment)
                        .addToBackStack(null)
                        .commit();
                mActivity.getFragmentManager().executePendingTransactions();
                assertEquals(1, mOnBackStackChangedTimes);
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertTrue(fragment.isAdded());
    }

    @Test
    public void testAddTransactionWithPrivateFragment() throws Throwable {
        final Fragment fragment = new PrivateFragment();
        mActivityRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean exceptionThrown = false;
                try {
                    mActivity.getFragmentManager().beginTransaction()
                            .add(android.R.id.content, fragment)
                            .addToBackStack(null)
                            .commit();
                    mActivity.getFragmentManager().executePendingTransactions();
                    assertEquals(1, mOnBackStackChangedTimes);
                } catch (IllegalStateException e) {
                    exceptionThrown = true;
                } finally {
                    assertTrue("Exception should be thrown", exceptionThrown);
                    assertFalse("Fragment shouldn't be added", fragment.isAdded());
                }
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    @Test
    public void testAddTransactionWithPackagePrivateFragment() throws Throwable {
        final Fragment fragment = new PackagePrivateFragment();
        mActivityRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean exceptionThrown = false;
                try {
                    mActivity.getFragmentManager().beginTransaction()
                            .add(android.R.id.content, fragment)
                            .addToBackStack(null)
                            .commit();
                    mActivity.getFragmentManager().executePendingTransactions();
                    assertEquals(1, mOnBackStackChangedTimes);
                } catch (IllegalStateException e) {
                    exceptionThrown = true;
                } finally {
                    assertTrue("Exception should be thrown", exceptionThrown);
                    assertFalse("Fragment shouldn't be added", fragment.isAdded());
                }
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    @Test
    public void testAddTransactionWithAnonymousFragment() throws Throwable {
        final Fragment fragment = new Fragment() {};
        mActivityRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean exceptionThrown = false;
                try {
                    mActivity.getFragmentManager().beginTransaction()
                            .add(android.R.id.content, fragment)
                            .addToBackStack(null)
                            .commit();
                    mActivity.getFragmentManager().executePendingTransactions();
                    assertEquals(1, mOnBackStackChangedTimes);
                } catch (IllegalStateException e) {
                    exceptionThrown = true;
                } finally {
                    assertTrue("Exception should be thrown", exceptionThrown);
                    assertFalse("Fragment shouldn't be added", fragment.isAdded());
                }
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    @Test
    public void testAddTransactionWithNonStaticFragment() throws Throwable {
        final Fragment fragment = new NonStaticFragment();
        mActivityRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean exceptionThrown = false;
                try {
                    mActivity.getFragmentManager().beginTransaction()
                            .add(android.R.id.content, fragment)
                            .addToBackStack(null)
                            .commit();
                    mActivity.getFragmentManager().executePendingTransactions();
                    assertEquals(1, mOnBackStackChangedTimes);
                } catch (IllegalStateException e) {
                    exceptionThrown = true;
                } finally {
                    assertTrue("Exception should be thrown", exceptionThrown);
                    assertFalse("Fragment shouldn't be added", fragment.isAdded());
                }
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    @Test
    public void testRunOnCommit() throws Throwable {
        mActivityRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final boolean[] ran = new boolean[1];
                FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
                fm.beginTransaction().runOnCommit(new Runnable() {
                    @Override
                    public void run() {
                        ran[0] = true;
                    }
                }).commit();
                fm.executePendingTransactions();
                assertEquals(0, mOnBackStackChangedTimes);

                assertTrue("runOnCommit runnable never ran", ran[0]);

                ran[0] = false;

                boolean threw = false;
                try {
                    fm.beginTransaction().runOnCommit(new Runnable() {
                        @Override
                        public void run() {
                            ran[0] = true;
                        }
                    }).addToBackStack(null).commit();
                } catch (IllegalStateException ise) {
                    threw = true;
                }

                fm.executePendingTransactions();
                assertEquals(0, mOnBackStackChangedTimes);

                assertTrue("runOnCommit was allowed to be called for back stack transaction",
                        threw);
                assertFalse("runOnCommit runnable for back stack transaction was run", ran[0]);
            }
        });
    }

    // Ensure that getFragments() works during transactions, even if it is run off thread
    @Test
    public void getFragmentsOffThread() throws Throwable {
        final FragmentManager fm = mActivity.getFragmentManager();

        // Make sure that adding a fragment works
        Fragment fragment = new FragmentWithView();
        fm.beginTransaction()
                .add(android.R.id.content, fragment)
                .addToBackStack(null)
                .commit();

        FragmentTestUtil.executePendingTransactions(mActivityRule);
        Collection<Fragment> fragments = fm.getFragments();
        assertEquals(1, fragments.size());
        assertTrue(fragments.contains(fragment));
        assertEquals(1, mOnBackStackChangedTimes);

        // Removed fragments shouldn't show
        fm.beginTransaction()
                .remove(fragment)
                .addToBackStack(null)
                .commit();
        FragmentTestUtil.executePendingTransactions(mActivityRule);
        assertTrue(fm.getFragments().isEmpty());
        assertEquals(2, mOnBackStackChangedTimes);

        // Now try detached fragments
        FragmentTestUtil.popBackStackImmediate(mActivityRule);
        assertEquals(3, mOnBackStackChangedTimes);
        fm.beginTransaction()
                .detach(fragment)
                .addToBackStack(null)
                .commit();
        FragmentTestUtil.executePendingTransactions(mActivityRule);
        assertTrue(fm.getFragments().isEmpty());
        assertEquals(4, mOnBackStackChangedTimes);

        // Now try hidden fragments
        FragmentTestUtil.popBackStackImmediate(mActivityRule);
        assertEquals(5, mOnBackStackChangedTimes);
        fm.beginTransaction()
                .hide(fragment)
                .addToBackStack(null)
                .commit();
        FragmentTestUtil.executePendingTransactions(mActivityRule);
        fragments = fm.getFragments();
        assertEquals(1, fragments.size());
        assertTrue(fragments.contains(fragment));
        assertEquals(6, mOnBackStackChangedTimes);

        // And showing it again shouldn't change anything:
        FragmentTestUtil.popBackStackImmediate(mActivityRule);
        fragments = fm.getFragments();
        assertEquals(1, fragments.size());
        assertTrue(fragments.contains(fragment));
        assertEquals(7, mOnBackStackChangedTimes);

        // Now pop back to the start state
        FragmentTestUtil.popBackStackImmediate(mActivityRule);
        assertEquals(8, mOnBackStackChangedTimes);

        // We can't force concurrency, but we can do it lots of times and hope that
        // we hit it.
        for (int i = 0; i < 100; i++) {
            Fragment fragment2 = new FragmentWithView();
            fm.beginTransaction()
                    .add(android.R.id.content, fragment2)
                    .addToBackStack(null)
                    .commit();
            getFragmentsUntilSize(1);

            fm.popBackStack();
            getFragmentsUntilSize(0);
        }
    }

    /**
     * When a FragmentManager is detached, it should allow commitAllowingStateLoss()
     * and commitNowAllowingStateLoss() by just dropping the transaction.
     */
    @Test
    public void commitAllowStateLossDetached() throws Throwable {
        Fragment fragment1 = new CorrectFragment();
        mActivity.getFragmentManager()
                .beginTransaction()
                .add(fragment1, "1")
                .commit();
        FragmentTestUtil.executePendingTransactions(mActivityRule);
        assertEquals(0, mOnBackStackChangedTimes);
        final FragmentManager fm = fragment1.getChildFragmentManager();
        mActivity.getFragmentManager()
                .beginTransaction()
                .remove(fragment1)
                .commit();
        FragmentTestUtil.executePendingTransactions(mActivityRule);
        assertEquals(0, mActivity.getFragmentManager().getFragments().size());
        assertEquals(0, fm.getFragments().size());
        assertEquals(0, mOnBackStackChangedTimes);

        // Now the fragment1's fragment manager should allow commitAllowingStateLoss
        // by doing nothing since it has been detached.
        Fragment fragment2 = new CorrectFragment();
        fm.beginTransaction()
                .add(fragment2, "2")
                .commitAllowingStateLoss();
        FragmentTestUtil.executePendingTransactions(mActivityRule);
        assertEquals(0, fm.getFragments().size());
        assertEquals(0, mOnBackStackChangedTimes);

        // It should also allow commitNowAllowingStateLoss by doing nothing
        mActivityRule.runOnUiThread(() -> {
            Fragment fragment3 = new CorrectFragment();
            fm.beginTransaction()
                    .add(fragment3, "3")
                    .commitNowAllowingStateLoss();
            assertEquals(0, fm.getFragments().size());
        });
        assertEquals(0, mOnBackStackChangedTimes);
    }

    /**
     * onNewIntent() should note that the state is not saved so that child fragment
     * managers can execute transactions.
     */
    @Test
    public void newIntentUnlocks() throws Throwable {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent1 = new Intent(mActivity, NewIntentActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        NewIntentActivity newIntentActivity =
                (NewIntentActivity) instrumentation.startActivitySync(intent1);
        FragmentTestUtil.waitForExecution(mActivityRule);

        Intent intent2 = new Intent(mActivity, FragmentTestActivity.class);
        intent2.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity coveringActivity = instrumentation.startActivitySync(intent2);
        FragmentTestUtil.waitForExecution(mActivityRule);

        Intent intent3 = new Intent(mActivity, NewIntentActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mActivity.startActivity(intent3);
        assertTrue(newIntentActivity.newIntent.await(1, TimeUnit.SECONDS));
        FragmentTestUtil.waitForExecution(mActivityRule);

        for (Fragment fragment : newIntentActivity.getFragmentManager().getFragments()) {
            // There really should only be one fragment in newIntentActivity.
            assertEquals(1, fragment.getChildFragmentManager().getFragments().size());
        }
    }

    @Test
    public void testGetLayoutInflater() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            final OnGetLayoutInflaterFragment fragment1 = new OnGetLayoutInflaterFragment();
            assertEquals(0, fragment1.onGetLayoutInflaterCalls);
            mActivity.getFragmentManager().beginTransaction()
                    .add(android.R.id.content, fragment1)
                    .addToBackStack(null)
                    .commit();
            mActivity.getFragmentManager().executePendingTransactions();
            assertEquals(1, fragment1.onGetLayoutInflaterCalls);
            assertEquals(fragment1.layoutInflater, fragment1.getLayoutInflater());
            // getLayoutInflater() didn't force onGetLayoutInflater()
            assertEquals(1, fragment1.onGetLayoutInflaterCalls);

            LayoutInflater layoutInflater = fragment1.layoutInflater;
            // Replacing fragment1 won't detach it, so the value won't be cleared
            final OnGetLayoutInflaterFragment fragment2 = new OnGetLayoutInflaterFragment();
            mActivity.getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, fragment2)
                    .addToBackStack(null)
                    .commit();
            mActivity.getFragmentManager().executePendingTransactions();

            assertSame(layoutInflater, fragment1.getLayoutInflater());
            assertEquals(1, fragment1.onGetLayoutInflaterCalls);

            // Popping it should cause onCreateView again, so a new LayoutInflater...
            mActivity.getFragmentManager().popBackStackImmediate();
            assertNotSame(layoutInflater, fragment1.getLayoutInflater());
            assertEquals(2, fragment1.onGetLayoutInflaterCalls);
            layoutInflater = fragment1.layoutInflater;
            assertSame(layoutInflater, fragment1.getLayoutInflater());

            // Popping it should detach it, clearing the cached value again
            mActivity.getFragmentManager().popBackStackImmediate();

            // once it is detached, the getLayoutInflater() will default to throw
            // an exception, but we've made it return null instead.
            assertEquals(2, fragment1.onGetLayoutInflaterCalls);
            assertNull(fragment1.getLayoutInflater());
            assertEquals(3, fragment1.onGetLayoutInflaterCalls);
        });
    }

    private void getFragmentsUntilSize(int expectedSize) {
        final long endTime = SystemClock.uptimeMillis() + 3000;

        do {
            assertTrue(SystemClock.uptimeMillis() < endTime);
        } while (mActivity.getFragmentManager().getFragments().size() != expectedSize);
    }

    public static class CorrectFragment extends Fragment {}

    private static class PrivateFragment extends Fragment {}

    static class PackagePrivateFragment extends Fragment {}

    private class NonStaticFragment extends Fragment {}

    public static class FragmentWithView extends Fragment {
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            return inflater.inflate(R.layout.text_a, container, false);
        }
    }

    public static class OnGetLayoutInflaterFragment extends Fragment {
        public int onGetLayoutInflaterCalls = 0;
        public LayoutInflater layoutInflater;

        @Override
        public LayoutInflater onGetLayoutInflater(Bundle savedInstanceState) {
            onGetLayoutInflaterCalls++;
            try {
                layoutInflater = super.onGetLayoutInflater(savedInstanceState);
            } catch (Exception e) {
                return null;
            }
            return layoutInflater;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            return inflater.inflate(R.layout.text_a, container, false);
        }
    }
}
