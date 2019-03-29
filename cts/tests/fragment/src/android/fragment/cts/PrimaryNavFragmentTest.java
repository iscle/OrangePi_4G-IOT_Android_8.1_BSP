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

import android.app.FragmentManager;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.MediumTest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.fragment.cts.FragmentTestUtil.executePendingTransactions;
import static android.fragment.cts.FragmentTestUtil.popBackStackImmediate;
import static junit.framework.TestCase.*;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class PrimaryNavFragmentTest {
    @Rule
    public ActivityTestRule<FragmentTestActivity> mActivityRule =
            new ActivityTestRule<>(FragmentTestActivity.class);

    @Test
    public void delegateBackToPrimaryNav() throws Throwable {
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictFragment strictFragment = new StrictFragment();

        fm.beginTransaction().add(strictFragment, null).setPrimaryNavigationFragment(strictFragment)
                .commit();
        executePendingTransactions(mActivityRule, fm);

        assertSame("new fragment is not primary nav fragment", strictFragment,
                fm.getPrimaryNavigationFragment());

        final StrictFragment child = new StrictFragment();
        FragmentManager cfm = strictFragment.getChildFragmentManager();
        cfm.beginTransaction().add(child, null).addToBackStack(null).commit();
        executePendingTransactions(mActivityRule, cfm);

        assertEquals("child transaction not on back stack", 1, cfm.getBackStackEntryCount());

        // Should execute the pop for the child fragmentmanager
        assertTrue("popBackStackImmediate returned no action performed",
                popBackStackImmediate(mActivityRule, fm));

        assertEquals("child transaction still on back stack", 0, cfm.getBackStackEntryCount());
    }

    @Test
    public void popPrimaryNav() throws Throwable {
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictFragment strictFragment1 = new StrictFragment();

        fm.beginTransaction().add(strictFragment1, null)
                .setPrimaryNavigationFragment(strictFragment1)
                .commit();
        executePendingTransactions(mActivityRule, fm);

        assertSame("new fragment is not primary nav fragment", strictFragment1,
                fm.getPrimaryNavigationFragment());

        fm.beginTransaction().remove(strictFragment1).addToBackStack(null).commit();
        executePendingTransactions(mActivityRule, fm);

        assertNull("primary nav fragment is not null after remove",
                fm.getPrimaryNavigationFragment());

        popBackStackImmediate(mActivityRule, fm);

        assertSame("primary nav fragment was not restored on pop", strictFragment1,
                fm.getPrimaryNavigationFragment());

        final StrictFragment strictFragment2 = new StrictFragment();
        fm.beginTransaction().remove(strictFragment1).add(strictFragment2, null)
                .setPrimaryNavigationFragment(strictFragment2).addToBackStack(null).commit();
        executePendingTransactions(mActivityRule, fm);

        assertSame("primary nav fragment not updated to new fragment", strictFragment2,
                fm.getPrimaryNavigationFragment());

        popBackStackImmediate(mActivityRule, fm);

        assertSame("primary nav fragment not restored on pop", strictFragment1,
                fm.getPrimaryNavigationFragment());

        fm.beginTransaction().setPrimaryNavigationFragment(strictFragment1)
                .addToBackStack(null).commit();
        executePendingTransactions(mActivityRule, fm);

        assertSame("primary nav fragment not retained when set again in new transaction",
                strictFragment1, fm.getPrimaryNavigationFragment());
        popBackStackImmediate(mActivityRule, fm);

        assertSame("same primary nav fragment not retained when set primary nav transaction popped",
                strictFragment1, fm.getPrimaryNavigationFragment());
    }

    @Test
    public void replacePrimaryNav() throws Throwable {
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictFragment strictFragment1 = new StrictFragment();

        fm.beginTransaction().add(android.R.id.content, strictFragment1)
                .setPrimaryNavigationFragment(strictFragment1).commit();
        executePendingTransactions(mActivityRule, fm);

        assertSame("new fragment is not primary nav fragment", strictFragment1,
                fm.getPrimaryNavigationFragment());

        final StrictFragment strictFragment2 = new StrictFragment();
        fm.beginTransaction().replace(android.R.id.content, strictFragment2)
                .addToBackStack(null).commit();

        executePendingTransactions(mActivityRule, fm);

        assertNull("primary nav fragment not null after replace",
                fm.getPrimaryNavigationFragment());

        popBackStackImmediate(mActivityRule, fm);

        assertSame("primary nav fragment not restored after popping replace", strictFragment1,
                fm.getPrimaryNavigationFragment());

        fm.beginTransaction().setPrimaryNavigationFragment(null).commit();
        executePendingTransactions(mActivityRule, fm);

        assertNull("primary nav fragment not null after explicit set to null",
                fm.getPrimaryNavigationFragment());

        fm.beginTransaction().replace(android.R.id.content, strictFragment2)
                .setPrimaryNavigationFragment(strictFragment2).addToBackStack(null).commit();
        executePendingTransactions(mActivityRule, fm);

        assertSame("primary nav fragment not set correctly after replace", strictFragment2,
                fm.getPrimaryNavigationFragment());

        popBackStackImmediate(mActivityRule, fm);

        assertNull("primary nav fragment not null after popping replace",
                fm.getPrimaryNavigationFragment());
    }
}
