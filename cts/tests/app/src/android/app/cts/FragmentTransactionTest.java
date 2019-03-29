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
package android.app.cts;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

import android.app.Fragment;
import android.app.Instrumentation;
import android.app.stubs.FragmentTestActivity;
import android.app.stubs.R;
import android.os.Bundle;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests usage of the {@link android.app.FragmentTransaction} class.
 */
@RunWith(AndroidJUnit4.class)
public class FragmentTransactionTest {

    @Rule
    public ActivityTestRule<FragmentTestActivity> mActivityRule =
            new ActivityTestRule<>(FragmentTestActivity.class);

    private FragmentTestActivity mActivity;

    @Before
    public void setUp() {
        mActivity = mActivityRule.getActivity();
    }

    @Test
    public void testAddTransactionWithValidFragment() {
        final Fragment fragment = new CorrectFragment();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mActivity.getFragmentManager().beginTransaction()
                        .add(R.id.content, fragment)
                        .addToBackStack(null)
                        .commit();
                mActivity.getFragmentManager().executePendingTransactions();
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertTrue(fragment.isAdded());
    }

    @Test
    public void testAddTransactionWithPrivateFragment() {
        final Fragment fragment = new PrivateFragment();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                boolean exceptionThrown = false;
                try {
                    mActivity.getFragmentManager().beginTransaction()
                            .add(R.id.content, fragment)
                            .addToBackStack(null)
                            .commit();
                    mActivity.getFragmentManager().executePendingTransactions();
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
    public void testAddTransactionWithPackagePrivateFragment() {
        final Fragment fragment = new PackagePrivateFragment();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                boolean exceptionThrown = false;
                try {
                    mActivity.getFragmentManager().beginTransaction()
                            .add(R.id.content, fragment)
                            .addToBackStack(null)
                            .commit();
                    mActivity.getFragmentManager().executePendingTransactions();
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
    public void testAddTransactionWithAnonymousFragment() {
        final Fragment fragment = new Fragment() {};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                boolean exceptionThrown = false;
                try {
                    mActivity.getFragmentManager().beginTransaction()
                            .add(R.id.content, fragment)
                            .addToBackStack(null)
                            .commit();
                    mActivity.getFragmentManager().executePendingTransactions();
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
    public void testAddTransactionWithNonStaticFragment() {
        final Fragment fragment = new NonStaticFragment();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                boolean exceptionThrown = false;
                try {
                    mActivity.getFragmentManager().beginTransaction()
                            .add(R.id.content, fragment)
                            .addToBackStack(null)
                            .commit();
                    mActivity.getFragmentManager().executePendingTransactions();
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

    /**
     * Test to ensure that when onBackPressed() is received that there is no crash.
     */
    @Test
    public void crashOnBackPressed() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
            Bundle outState = new Bundle();
            instrumentation.callActivityOnSaveInstanceState(mActivity, outState);
            mActivity.onBackPressed();
        });
    }

    public static class CorrectFragment extends Fragment {}

    private static class PrivateFragment extends Fragment {}

    private static class PackagePrivateFragment extends Fragment {}

    private class NonStaticFragment extends Fragment {}
}
