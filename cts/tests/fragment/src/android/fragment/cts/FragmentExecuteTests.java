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
package android.fragment.cts;

import static junit.framework.Assert.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.FragmentManager;
import android.app.Instrumentation;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.ViewGroup;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class FragmentExecuteTests {
    @Rule
    public ActivityTestRule<FragmentTestActivity> mActivityRule =
            new ActivityTestRule<FragmentTestActivity>(FragmentTestActivity.class);

    @Before
    public void setupContentView() {
        FragmentTestUtil.setContentView(mActivityRule, R.layout.simple_container);
    }

    // Test that when executePendingBindings is called after something has been
    // committed that it returns true and that the transaction was executed.
    @Test
    public void executeAndPopNormal() throws Throwable {
        ViewGroup container = (ViewGroup)
                mActivityRule.getActivity().findViewById(R.id.fragmentContainer);
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictViewFragment fragment = new StrictViewFragment();

        mActivityRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                fm.beginTransaction()
                        .add(R.id.fragmentContainer, fragment, "1")
                        .addToBackStack(null)
                        .commit();
                assertTrue(fm.executePendingTransactions());
            }
        });

        FragmentTestUtil.assertChildren(container, fragment);
        assertEquals(1, fm.getBackStackEntryCount());
        assertEquals(fragment, fm.findFragmentById(R.id.fragmentContainer));
        assertEquals(fragment, fm.findFragmentByTag("1"));

        assertTrue(FragmentTestUtil.popBackStackImmediate(mActivityRule));

        FragmentTestUtil.assertChildren(container);
        assertEquals(0, fm.getBackStackEntryCount());
        assertNull(fm.findFragmentById(R.id.fragmentContainer));
        assertNull(fm.findFragmentByTag("1"));
    }

    // Test that when executePendingBindings is called when nothing has been
    // committed that it returns false and that the fragment manager is unchanged.
    @Test
    public void executeAndPopNothing() throws Throwable {
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();

        assertEquals(0, fm.getBackStackEntryCount());
        assertFalse(FragmentTestUtil.executePendingTransactions(mActivityRule));
        assertEquals(0, fm.getBackStackEntryCount());
        assertFalse(FragmentTestUtil.popBackStackImmediate(mActivityRule));
        assertEquals(0, fm.getBackStackEntryCount());
    }

    // Test that when popBackStackImmediate is called when something is in the queue and
    // there is a back stack to pop, it will execute both and return true.
    @Test
    public void popBackStackImmediateSomething() throws Throwable {
        ViewGroup container = (ViewGroup)
                mActivityRule.getActivity().findViewById(R.id.fragmentContainer);
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictViewFragment fragment = new StrictViewFragment();

        fm.beginTransaction()
                .add(R.id.fragmentContainer, fragment, "1")
                .addToBackStack(null)
                .commit();

        assertTrue(FragmentTestUtil.popBackStackImmediate(mActivityRule));

        FragmentTestUtil.assertChildren(container);
        assertEquals(0, fm.getBackStackEntryCount());
        assertNull(fm.findFragmentById(R.id.fragmentContainer));
        assertNull(fm.findFragmentByTag("1"));
    }

    // Test that when popBackStackImmediate is called when something is in the queue and
    // there is no back stack to pop, it will execute the thing in the queue and
    // return false.
    @Test
    public void popBackStackImmediateNothing() throws Throwable {
        ViewGroup container = (ViewGroup)
                mActivityRule.getActivity().findViewById(R.id.fragmentContainer);
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictViewFragment fragment = new StrictViewFragment();

        fm.beginTransaction()
                .add(R.id.fragmentContainer, fragment, "1")
                .commit();

        assertFalse(FragmentTestUtil.popBackStackImmediate(mActivityRule));

        FragmentTestUtil.assertChildren(container, fragment);
        assertEquals(0, fm.getBackStackEntryCount());
        assertEquals(fragment, fm.findFragmentById(R.id.fragmentContainer));
        assertEquals(fragment, fm.findFragmentByTag("1"));
    }

    // Test popBackStackImmediate(int, int)
    @Test
    public void popBackStackImmediateInt() throws Throwable {
        ViewGroup container = (ViewGroup)
                mActivityRule.getActivity().findViewById(R.id.fragmentContainer);
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictViewFragment fragment1 = new StrictViewFragment();

        final int commit1 = fm.beginTransaction()
                .add(R.id.fragmentContainer, fragment1, "1")
                .addToBackStack(null)
                .commit();

        final StrictViewFragment fragment2 = new StrictViewFragment();
        fm.beginTransaction()
                .add(R.id.fragmentContainer, fragment2, "2")
                .addToBackStack(null)
                .commit();

        final StrictViewFragment fragment3 = new StrictViewFragment();
        fm.beginTransaction()
                .add(R.id.fragmentContainer, fragment3, "3")
                .addToBackStack(null)
                .commit();

        assertTrue(FragmentTestUtil.popBackStackImmediate(mActivityRule, commit1, 0));
        assertFalse(fragment2.isAdded());
        assertTrue(fragment2.mCalledOnDestroy || !fragment2.mCalledOnCreate);
        assertFalse(fragment3.isAdded());
        assertTrue(fragment3.mCalledOnDestroy || !fragment3.mCalledOnCreate);

        assertFalse(FragmentTestUtil.popBackStackImmediate(mActivityRule, commit1, 0));

        FragmentTestUtil.assertChildren(container, fragment1);
        assertEquals(1, fm.getBackStackEntryCount());
        assertEquals(fragment1, fm.findFragmentById(R.id.fragmentContainer));
        assertEquals(fragment1, fm.findFragmentByTag("1"));

        final StrictViewFragment fragment4 = new StrictViewFragment();
        final int commit4 = fm.beginTransaction()
                .add(R.id.fragmentContainer, fragment4, "4")
                .addToBackStack(null)
                .commit();

        final StrictViewFragment fragment5 = new StrictViewFragment();
        fm.beginTransaction()
                .add(R.id.fragmentContainer, fragment5, "5")
                .addToBackStack(null)
                .commit();

        assertTrue(FragmentTestUtil.popBackStackImmediate(mActivityRule, commit4,
                FragmentManager.POP_BACK_STACK_INCLUSIVE));
        assertFalse(fragment4.isAdded());
        assertTrue(fragment4.mCalledOnDestroy || !fragment4.mCalledOnCreate);
        assertFalse(fragment5.isAdded());
        assertTrue(fragment5.mCalledOnDestroy || !fragment5.mCalledOnCreate);

        assertFalse(FragmentTestUtil.popBackStackImmediate(mActivityRule, commit4,
                FragmentManager.POP_BACK_STACK_INCLUSIVE));

        FragmentTestUtil.assertChildren(container, fragment1);
        assertEquals(1, fm.getBackStackEntryCount());
        assertEquals(fragment1, fm.findFragmentById(R.id.fragmentContainer));
        assertEquals(fragment1, fm.findFragmentByTag("1"));
    }

    // Test popBackStackImmediate(String, int)
    @Test
    public void popBackStackImmediateString() throws Throwable {
        ViewGroup container = (ViewGroup)
                mActivityRule.getActivity().findViewById(R.id.fragmentContainer);
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictViewFragment fragment1 = new StrictViewFragment();

        fm.beginTransaction()
                .add(R.id.fragmentContainer, fragment1, "1")
                .addToBackStack("1")
                .commit();

        final StrictViewFragment fragment2 = new StrictViewFragment();
        fm.beginTransaction()
                .add(R.id.fragmentContainer, fragment2, "2")
                .addToBackStack("2")
                .commit();

        final StrictViewFragment fragment3 = new StrictViewFragment();
        fm.beginTransaction()
                .add(R.id.fragmentContainer, fragment3, "3")
                .addToBackStack("3")
                .commit();

        assertTrue(FragmentTestUtil.popBackStackImmediate(mActivityRule, "1", 0));
        assertFalse(fragment2.isAdded());
        assertTrue(fragment2.mCalledOnDestroy || !fragment2.mCalledOnCreate);
        assertFalse(fragment3.isAdded());
        assertTrue(fragment3.mCalledOnDestroy || !fragment3.mCalledOnCreate);

        assertFalse(FragmentTestUtil.popBackStackImmediate(mActivityRule, "1", 0));

        FragmentTestUtil.assertChildren(container, fragment1);
        assertEquals(1, fm.getBackStackEntryCount());
        assertEquals(fragment1, fm.findFragmentById(R.id.fragmentContainer));
        assertEquals(fragment1, fm.findFragmentByTag("1"));

        final StrictViewFragment fragment4 = new StrictViewFragment();
        fm.beginTransaction()
                .add(R.id.fragmentContainer, fragment4, "4")
                .addToBackStack("4")
                .commit();

        final StrictViewFragment fragment5 = new StrictViewFragment();
        fm.beginTransaction()
                .add(R.id.fragmentContainer, fragment5, "5")
                .addToBackStack("5")
                .commit();

        assertTrue(FragmentTestUtil.popBackStackImmediate(mActivityRule, "4",
                FragmentManager.POP_BACK_STACK_INCLUSIVE));
        assertFalse(fragment4.isAdded());
        assertTrue(fragment4.mCalledOnDestroy || !fragment4.mCalledOnCreate);
        assertFalse(fragment5.isAdded());
        assertTrue(fragment5.mCalledOnDestroy || !fragment5.mCalledOnCreate);

        assertFalse(FragmentTestUtil.popBackStackImmediate(mActivityRule, "4",
                FragmentManager.POP_BACK_STACK_INCLUSIVE));

        FragmentTestUtil.assertChildren(container, fragment1);
        assertEquals(1, fm.getBackStackEntryCount());
        assertEquals(fragment1, fm.findFragmentById(R.id.fragmentContainer));
        assertEquals(fragment1, fm.findFragmentByTag("1"));
    }

}
