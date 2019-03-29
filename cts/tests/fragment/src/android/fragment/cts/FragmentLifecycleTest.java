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

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.fail;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotSame;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertSame;
import static junit.framework.TestCase.assertTrue;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentController;
import android.app.FragmentHostCallback;
import android.app.FragmentManager;
import android.app.FragmentManager.FragmentLifecycleCallbacks;
import android.app.FragmentManagerNonConfig;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileDescriptor;
import java.io.PrintWriter;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class FragmentLifecycleTest {

    @Rule
    public ActivityTestRule<FragmentTestActivity> mActivityRule =
            new ActivityTestRule<FragmentTestActivity>(FragmentTestActivity.class);

    @Test
    public void basicLifecycle() throws Throwable {
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictFragment strictFragment = new StrictFragment();

        // Add fragment; StrictFragment will throw if it detects any violation
        // in standard lifecycle method ordering or expected preconditions.
        fm.beginTransaction().add(strictFragment, "EmptyHeadless").commit();
        executePendingTransactions(fm);

        assertTrue("fragment is not added", strictFragment.isAdded());
        assertFalse("fragment is detached", strictFragment.isDetached());
        assertTrue("fragment is not resumed", strictFragment.isResumed());

        // Test removal as well; StrictFragment will throw here too.
        fm.beginTransaction().remove(strictFragment).commit();
        executePendingTransactions(fm);

        assertFalse("fragment is added", strictFragment.isAdded());
        assertFalse("fragment is resumed", strictFragment.isResumed());

        // This one is perhaps counterintuitive; "detached" means specifically detached
        // but still managed by a FragmentManager. The .remove call above
        // should not enter this state.
        assertFalse("fragment is detached", strictFragment.isDetached());
    }

    @Test
    public void detachment() throws Throwable {
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictFragment f1 = new StrictFragment();
        final StrictFragment f2 = new StrictFragment();

        fm.beginTransaction().add(f1, "1").add(f2, "2").commit();
        executePendingTransactions(fm);

        assertTrue("fragment 1 is not added", f1.isAdded());
        assertTrue("fragment 2 is not added", f2.isAdded());

        // Test detaching fragments using StrictFragment to throw on errors.
        fm.beginTransaction().detach(f1).detach(f2).commit();
        executePendingTransactions(fm);

        assertTrue("fragment 1 is not detached", f1.isDetached());
        assertTrue("fragment 2 is not detached", f2.isDetached());
        assertFalse("fragment 1 is added", f1.isAdded());
        assertFalse("fragment 2 is added", f2.isAdded());

        // Only reattach f1; leave v2 detached.
        fm.beginTransaction().attach(f1).commit();
        executePendingTransactions(fm);

        assertTrue("fragment 1 is not added", f1.isAdded());
        assertFalse("fragment 1 is detached", f1.isDetached());
        assertTrue("fragment 2 is not detached", f2.isDetached());

        // Remove both from the FragmentManager.
        fm.beginTransaction().remove(f1).remove(f2).commit();
        executePendingTransactions(fm);

        assertFalse("fragment 1 is added", f1.isAdded());
        assertFalse("fragment 2 is added", f2.isAdded());
        assertFalse("fragment 1 is detached", f1.isDetached());
        assertFalse("fragment 2 is detached", f2.isDetached());
    }

    @Test
    public void basicBackStack() throws Throwable {
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictFragment f1 = new StrictFragment();
        final StrictFragment f2 = new StrictFragment();

        // Add a fragment normally to set up
        fm.beginTransaction().add(f1, "1").commit();
        executePendingTransactions(fm);

        assertTrue("fragment 1 is not added", f1.isAdded());

        // Remove the first one and add a second. We're not using replace() here since
        // these fragments are headless and as of this test writing, replace() only works
        // for fragments with views and a container view id.
        // Add it to the back stack so we can pop it afterwards.
        fm.beginTransaction().remove(f1).add(f2, "2").addToBackStack("stack1").commit();
        executePendingTransactions(fm);

        assertFalse("fragment 1 is added", f1.isAdded());
        assertTrue("fragment 2 is not added", f2.isAdded());

        // Test popping the stack
        fm.popBackStack();
        executePendingTransactions(fm);

        assertFalse("fragment 2 is added", f2.isAdded());
        assertTrue("fragment 1 is not added", f1.isAdded());
    }

    @Test
    public void attachBackStack() throws Throwable {
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictFragment f1 = new StrictFragment();
        final StrictFragment f2 = new StrictFragment();

        // Add a fragment normally to set up
        fm.beginTransaction().add(f1, "1").commit();
        executePendingTransactions(fm);

        assertTrue("fragment 1 is not added", f1.isAdded());

        fm.beginTransaction().detach(f1).add(f2, "2").addToBackStack("stack1").commit();
        executePendingTransactions(fm);

        assertTrue("fragment 1 is not detached", f1.isDetached());
        assertFalse("fragment 2 is detached", f2.isDetached());
        assertFalse("fragment 1 is added", f1.isAdded());
        assertTrue("fragment 2 is not added", f2.isAdded());
    }

    @Test
    public void viewLifecycle() throws Throwable {
        // Test basic lifecycle when the fragment creates a view

        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictViewFragment f1 = new StrictViewFragment();

        fm.beginTransaction().add(android.R.id.content, f1).commit();
        executePendingTransactions(fm);

        assertTrue("fragment 1 is not added", f1.isAdded());
        final View view = f1.getView();
        assertNotNull("fragment 1 returned null from getView", view);
        assertTrue("fragment 1's view is not attached to a window", view.isAttachedToWindow());

        fm.beginTransaction().remove(f1).commit();
        executePendingTransactions(fm);

        assertFalse("fragment 1 is added", f1.isAdded());
        assertNull("fragment 1 returned non-null from getView after removal", f1.getView());
        assertFalse("fragment 1's previous view is still attached to a window",
                view.isAttachedToWindow());
    }

    @Test
    public void viewReplace() throws Throwable {
        // Replace one view with another, then reverse it with the back stack

        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictViewFragment f1 = new StrictViewFragment();
        final StrictViewFragment f2 = new StrictViewFragment();

        fm.beginTransaction().add(android.R.id.content, f1).commit();
        executePendingTransactions(fm);

        assertTrue("fragment 1 is not added", f1.isAdded());

        View origView1 = f1.getView();
        assertNotNull("fragment 1 returned null view", origView1);
        assertTrue("fragment 1's view not attached", origView1.isAttachedToWindow());

        fm.beginTransaction().replace(android.R.id.content, f2).addToBackStack("stack1").commit();
        executePendingTransactions(fm);

        assertFalse("fragment 1 is added", f1.isAdded());
        assertTrue("fragment 2 is added", f2.isAdded());
        assertNull("fragment 1 returned non-null view", f1.getView());
        assertFalse("fragment 1's old view still attached", origView1.isAttachedToWindow());
        View origView2 = f2.getView();
        assertNotNull("fragment 2 returned null view", origView2);
        assertTrue("fragment 2's view not attached", origView2.isAttachedToWindow());

        fm.popBackStack();
        executePendingTransactions(fm);

        assertTrue("fragment 1 is not added", f1.isAdded());
        assertFalse("fragment 2 is added", f2.isAdded());
        assertNull("fragment 2 returned non-null view", f2.getView());
        assertFalse("fragment 2's view still attached", origView2.isAttachedToWindow());
        View newView1 = f1.getView();
        assertNotSame("fragment 1 had same view from last attachment", origView1, newView1);
        assertTrue("fragment 1's view not attached", newView1.isAttachedToWindow());
    }

    @Test
    public void viewReplaceMultiple() throws Throwable {
        // Replace several views with one, then reverse it with the back stack

        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictViewFragment f1 = new StrictViewFragment();
        final StrictViewFragment f2 = new StrictViewFragment();
        final StrictViewFragment f3 = new StrictViewFragment();

        fm.beginTransaction().add(android.R.id.content, f1).commit();
        fm.beginTransaction().add(android.R.id.content, f2).commit();
        executePendingTransactions(fm);

        assertTrue("fragment 1 is not added", f1.isAdded());
        assertTrue("fragment 2 is not added", f2.isAdded());

        View origView1 = f1.getView();
        assertNotNull("fragment 1 returned null view", origView1);
        assertTrue("fragment 1's view not attached", origView1.isAttachedToWindow());
        assertSame(origView1, ((ViewGroup)origView1.getParent()).getChildAt(0));

        View origView2 = f2.getView();
        assertNotNull("fragment 2 returned null view", origView2);
        assertTrue("fragment 2's view not attached", origView2.isAttachedToWindow());
        assertSame(origView2, ((ViewGroup)origView1.getParent()).getChildAt(1));

        fm.beginTransaction().replace(android.R.id.content, f3).addToBackStack("stack1").commit();
        executePendingTransactions(fm);

        assertFalse("fragment 1 is added", f1.isAdded());
        assertFalse("fragment 2 is added", f2.isAdded());
        assertTrue("fragment 3 is added", f3.isAdded());
        assertNull("fragment 1 returned non-null view", f1.getView());
        assertNull("fragment 2 returned non-null view", f2.getView());
        assertFalse("fragment 1's old view still attached", origView1.isAttachedToWindow());
        assertFalse("fragment 2's old view still attached", origView2.isAttachedToWindow());
        View origView3 = f3.getView();
        assertNotNull("fragment 3 returned null view", origView3);
        assertTrue("fragment 3's view not attached", origView3.isAttachedToWindow());

        fm.popBackStack();
        executePendingTransactions(fm);

        assertTrue("fragment 1 is not added", f1.isAdded());
        assertTrue("fragment 2 is not added", f2.isAdded());
        assertFalse("fragment 3 is added", f3.isAdded());
        assertNull("fragment 3 returned non-null view", f3.getView());
        assertFalse("fragment 3's view still attached", origView3.isAttachedToWindow());
        View newView1 = f1.getView();
        View newView2 = f2.getView();
        assertNotSame("fragment 1 had same view from last attachment", origView1, newView1);
        assertNotSame("fragment 2 had same view from last attachment", origView2, newView1);
        assertTrue("fragment 1's view not attached", newView1.isAttachedToWindow());
        assertTrue("fragment 2's view not attached", newView2.isAttachedToWindow());
        assertSame(newView1, ((ViewGroup)newView1.getParent()).getChildAt(0));
        assertSame(newView2, ((ViewGroup)newView1.getParent()).getChildAt(1));
    }

    /**
     * This tests that fragments call onDestroy when the activity finishes.
     */
    @Test
    public void fragmentDestroyedOnFinish() throws Throwable {
        final FragmentController fc = FragmentTestUtil.createController(mActivityRule);
        FragmentTestUtil.resume(mActivityRule, fc, null);
        final StrictViewFragment fragmentA = StrictViewFragment.create(R.layout.text_a);
        final StrictViewFragment fragmentB = StrictViewFragment.create(R.layout.text_b);
        mActivityRule.runOnUiThread(() -> {
            FragmentManager fm = fc.getFragmentManager();

            fm.beginTransaction()
                    .add(android.R.id.content, fragmentA)
                    .commit();
            fm.executePendingTransactions();
            fm.beginTransaction()
                    .replace(android.R.id.content, fragmentB)
                    .addToBackStack(null)
                    .commit();
            fm.executePendingTransactions();
        });
        FragmentTestUtil.destroy(mActivityRule, fc);
        assertTrue(fragmentB.mCalledOnDestroy);
        assertTrue(fragmentA.mCalledOnDestroy);
    }

    /**
     * This test confirms that as long as a parent fragment has called super.onCreate,
     * any child fragments added, committed and with transactions executed will be brought
     * to at least the CREATED state by the time the parent fragment receives onCreateView.
     * This means the child fragment will have received onAttach/onCreate.
     */
    @Test
    @MediumTest
    public void childFragmentManagerAttach() throws Throwable {
        mActivityRule.runOnUiThread(new Runnable() {
            public void run() {
                FragmentController fc = FragmentController.createController(
                        new HostCallbacks(mActivityRule.getActivity()));
                fc.attachHost(null);
                fc.dispatchCreate();

                FragmentLifecycleCallbacks mockLc = mock(FragmentLifecycleCallbacks.class);
                FragmentLifecycleCallbacks mockRecursiveLc = mock(FragmentLifecycleCallbacks.class);

                FragmentManager fm = fc.getFragmentManager();
                fm.registerFragmentLifecycleCallbacks(mockLc, false);
                fm.registerFragmentLifecycleCallbacks(mockRecursiveLc, true);

                ChildFragmentManagerFragment fragment = new ChildFragmentManagerFragment();
                fm.beginTransaction()
                        .add(android.R.id.content, fragment)
                        .commitNow();

                verify(mockLc, times(1)).onFragmentCreated(fm, fragment, null);

                fc.dispatchActivityCreated();

                Fragment childFragment = fragment.getChildFragment();

                verify(mockLc, times(1)).onFragmentActivityCreated(fm, fragment, null);
                verify(mockRecursiveLc, times(1)).onFragmentActivityCreated(fm, fragment, null);
                verify(mockRecursiveLc, times(1)).onFragmentActivityCreated(fm, childFragment, null);

                fc.dispatchStart();

                verify(mockLc, times(1)).onFragmentStarted(fm, fragment);
                verify(mockRecursiveLc, times(1)).onFragmentStarted(fm, fragment);
                verify(mockRecursiveLc, times(1)).onFragmentStarted(fm, childFragment);

                fc.dispatchResume();

                verify(mockLc, times(1)).onFragmentResumed(fm, fragment);
                verify(mockRecursiveLc, times(1)).onFragmentResumed(fm, fragment);
                verify(mockRecursiveLc, times(1)).onFragmentResumed(fm, childFragment);

                // Confirm that the parent fragment received onAttachFragment
                assertTrue("parent fragment did not receive onAttachFragment",
                        fragment.mCalledOnAttachFragment);

                fc.dispatchStop();

                verify(mockLc, times(1)).onFragmentStopped(fm, fragment);
                verify(mockRecursiveLc, times(1)).onFragmentStopped(fm, fragment);
                verify(mockRecursiveLc, times(1)).onFragmentStopped(fm, childFragment);

                fc.dispatchDestroy();

                verify(mockLc, times(1)).onFragmentDestroyed(fm, fragment);
                verify(mockRecursiveLc, times(1)).onFragmentDestroyed(fm, fragment);
                verify(mockRecursiveLc, times(1)).onFragmentDestroyed(fm, childFragment);
            }
        });
    }

    /**
     * Test to ensure that when dispatch* is called that the fragment manager
     * doesn't cause the contained fragment states to change even if no state changes.
     */
    @Test
    public void noPrematureStateChange() throws Throwable {
        final FragmentController fc = FragmentTestUtil.createController(mActivityRule);
        FragmentTestUtil.resume(mActivityRule, fc, null);

        mActivityRule.runOnUiThread(() -> {
            fc.getFragmentManager().beginTransaction()
                    .add(new StrictFragment(), "1")
                    .commitNow();
        });

        Pair<Parcelable, FragmentManagerNonConfig> savedState =
                FragmentTestUtil.destroy(mActivityRule, fc);

        final FragmentController fragmentController = FragmentTestUtil.createController(mActivityRule);

        mActivityRule.runOnUiThread(() -> {
            fragmentController.attachHost(null);
            fragmentController.dispatchCreate();
            fragmentController.dispatchActivityCreated();
            fragmentController.noteStateNotSaved();
            fragmentController.execPendingActions();
            fragmentController.dispatchStart();
            fragmentController.reportLoaderStart();
            fragmentController.dispatchResume();
            fragmentController.restoreAllState(savedState.first, savedState.second);
            fragmentController.dispatchResume();
        });

        FragmentManager fm = fragmentController.getFragmentManager();

        StrictFragment fragment1 = (StrictFragment) fm.findFragmentByTag("1");

        assertNotNull(fragment1);
        assertFalse(fragment1.mCalledOnResume);
    }

    @Test
    public void testIsStateSaved() throws Throwable {
        FragmentController fc = FragmentTestUtil.createController(mActivityRule);
        FragmentTestUtil.resume(mActivityRule, fc, null);
        FragmentManager fm = fc.getFragmentManager();

        mActivityRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Fragment f = new StrictFragment();
                fm.beginTransaction()
                        .add(f, "1")
                        .commitNow();

                assertFalse("fragment reported state saved while resumed",
                        f.isStateSaved());

                fc.dispatchPause();
                fc.saveAllState();

                assertTrue("fragment reported state not saved after saveAllState",
                        f.isStateSaved());

                fc.dispatchStop();

                assertTrue("fragment reported state not saved after stop",
                        f.isStateSaved());

                fc.dispatchDestroy();

                assertFalse("fragment reported state saved after destroy",
                        f.isStateSaved());
            }
        });
    }

    @Test
    public void testSetArgumentsLifecycle() throws Throwable {
        FragmentController fc = FragmentTestUtil.createController(mActivityRule);
        FragmentTestUtil.resume(mActivityRule, fc, null);
        FragmentManager fm = fc.getFragmentManager();

        mActivityRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Fragment f = new StrictFragment();
                f.setArguments(new Bundle());

                fm.beginTransaction()
                        .add(f, "1")
                        .commitNow();

                f.setArguments(new Bundle());

                fc.dispatchPause();
                fc.saveAllState();

                boolean threw = false;
                try {
                    f.setArguments(new Bundle());
                } catch (IllegalStateException ise) {
                    threw = true;
                }
                assertTrue("fragment allowed setArguments after state save", threw);

                fc.dispatchStop();

                threw = false;
                try {
                    f.setArguments(new Bundle());
                } catch (IllegalStateException ise) {
                    threw = true;
                }
                assertTrue("fragment allowed setArguments after stop", threw);

                fc.dispatchDestroy();

                // Fully destroyed, so fragments have been removed.
                f.setArguments(new Bundle());
            }
        });

    }

    /*
     * Test that target fragments are in a useful state when we restore them, even if they're
     * on the back stack.
     */

    @Test
    public void targetFragmentRestoreLifecycleStateBackStack() throws Throwable {
        mActivityRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final FragmentController fc1 = FragmentController.createController(
                        new HostCallbacks(mActivityRule.getActivity()));

                final FragmentManager fm1 = fc1.getFragmentManager();

                fc1.attachHost(null);
                fc1.dispatchCreate();

                final Fragment target = new TargetFragment();
                fm1.beginTransaction().add(target, "target").commitNow();

                final Fragment referrer = new ReferrerFragment();
                referrer.setTargetFragment(target, 0);

                fm1.beginTransaction()
                        .remove(target)
                        .add(referrer, "referrer")
                        .addToBackStack(null)
                        .commit();

                fc1.dispatchActivityCreated();
                fc1.noteStateNotSaved();
                fc1.execPendingActions();
                fc1.doLoaderStart();
                fc1.dispatchStart();
                fc1.reportLoaderStart();
                fc1.dispatchResume();
                fc1.execPendingActions();

                // Bring the state back down to destroyed, simulating an activity restart
                fc1.dispatchPause();
                final Parcelable savedState = fc1.saveAllState();
                final FragmentManagerNonConfig nonconf = fc1.retainNestedNonConfig();
                fc1.dispatchStop();
                fc1.dispatchDestroy();

                final FragmentController fc2 = FragmentController.createController(
                        new HostCallbacks(mActivityRule.getActivity()));

                fc2.attachHost(null);
                fc2.restoreAllState(savedState, nonconf);
                fc2.dispatchCreate();

                fc2.dispatchActivityCreated();
                fc2.noteStateNotSaved();
                fc2.execPendingActions();
                fc2.doLoaderStart();
                fc2.dispatchStart();
                fc2.reportLoaderStart();
                fc2.dispatchResume();
                fc2.execPendingActions();

                // Bring the state back down to destroyed before we finish the test
                fc2.dispatchPause();
                fc2.saveAllState();
                fc2.dispatchStop();
                fc2.dispatchDestroy();
            }
        });
    }

    @Test
    public void targetFragmentRestoreLifecycleStateManagerOrder() throws Throwable {
        mActivityRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final FragmentController fc1 = FragmentController.createController(
                        new HostCallbacks(mActivityRule.getActivity()));

                final FragmentManager fm1 = fc1.getFragmentManager();

                fc1.attachHost(null);
                fc1.dispatchCreate();

                final Fragment target1 = new TargetFragment();
                final Fragment referrer1 = new ReferrerFragment();
                referrer1.setTargetFragment(target1, 0);

                fm1.beginTransaction().add(target1, "target1").add(referrer1, "referrer1").commitNow();

                final Fragment target2 = new TargetFragment();
                final Fragment referrer2 = new ReferrerFragment();
                referrer2.setTargetFragment(target2, 0);

                // Order shouldn't matter.
                fm1.beginTransaction().add(referrer2, "referrer2").add(target2, "target2").commitNow();

                fc1.dispatchActivityCreated();
                fc1.noteStateNotSaved();
                fc1.execPendingActions();
                fc1.doLoaderStart();
                fc1.dispatchStart();
                fc1.reportLoaderStart();
                fc1.dispatchResume();
                fc1.execPendingActions();

                // Bring the state back down to destroyed, simulating an activity restart
                fc1.dispatchPause();
                final Parcelable savedState = fc1.saveAllState();
                final FragmentManagerNonConfig nonconf = fc1.retainNestedNonConfig();
                fc1.dispatchStop();
                fc1.dispatchDestroy();

                final FragmentController fc2 = FragmentController.createController(
                        new HostCallbacks(mActivityRule.getActivity()));

                fc2.attachHost(null);
                fc2.restoreAllState(savedState, nonconf);
                fc2.dispatchCreate();

                fc2.dispatchActivityCreated();
                fc2.noteStateNotSaved();
                fc2.execPendingActions();
                fc2.doLoaderStart();
                fc2.dispatchStart();
                fc2.reportLoaderStart();
                fc2.dispatchResume();
                fc2.execPendingActions();

                // Bring the state back down to destroyed before we finish the test
                fc2.dispatchPause();
                fc2.saveAllState();
                fc2.dispatchStop();
                fc2.dispatchDestroy();
            }
        });
    }

    // Make sure that executing transactions during activity lifecycle events
    // is properly prevented.
    @Test
    public void preventReentrantCalls() throws Throwable {
        testLifecycleTransitionFailure(StrictFragment.ATTACHED, StrictFragment.CREATED);
        testLifecycleTransitionFailure(StrictFragment.CREATED, StrictFragment.ACTIVITY_CREATED);
        testLifecycleTransitionFailure(StrictFragment.ACTIVITY_CREATED, StrictFragment.STARTED);
        testLifecycleTransitionFailure(StrictFragment.STARTED, StrictFragment.RESUMED);

        testLifecycleTransitionFailure(StrictFragment.RESUMED, StrictFragment.STARTED);
        testLifecycleTransitionFailure(StrictFragment.STARTED, StrictFragment.CREATED);
        testLifecycleTransitionFailure(StrictFragment.CREATED, StrictFragment.ATTACHED);
        testLifecycleTransitionFailure(StrictFragment.ATTACHED, StrictFragment.DETACHED);
    }

    private void testLifecycleTransitionFailure(int fromState, int toState) throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            final FragmentController fc1 = FragmentController.createController(
                    new HostCallbacks(mActivityRule.getActivity()));
            FragmentTestUtil.resume(mActivityRule, fc1, null);

            final FragmentManager fm1 = fc1.getFragmentManager();

            final Fragment reentrantFragment = ReentrantFragment.create(fromState, toState);

            fm1.beginTransaction()
                    .add(reentrantFragment, "reentrant")
                    .commit();
            try {
                fm1.executePendingTransactions();
            } catch (IllegalStateException e) {
                fail("An exception shouldn't happen when initially adding the fragment");
            }

            // Now shut down the fragment controller. When fromState > toState, this should
            // result in an exception
            Pair<Parcelable, FragmentManagerNonConfig> savedState = null;
            try {
                savedState = FragmentTestUtil.destroy(mActivityRule, fc1);
                if (fromState > toState) {
                    fail("Expected IllegalStateException when moving from "
                            + StrictFragment.stateToString(fromState) + " to "
                            + StrictFragment.stateToString(toState));
                }
            } catch (IllegalStateException e) {
                if (fromState < toState) {
                    fail("Unexpected IllegalStateException when moving from "
                            + StrictFragment.stateToString(fromState) + " to "
                            + StrictFragment.stateToString(toState));
                }
                return; // test passed!
            }

            // now restore from saved state. This will be reached when
            // fromState < toState. We want to catch the fragment while it
            // is being restored as the fragment controller state is being brought up.

            final FragmentController fc2 = FragmentController.createController(
                    new HostCallbacks(mActivityRule.getActivity()));
            try {
                FragmentTestUtil.resume(mActivityRule, fc2, savedState);

                fail("Expected IllegalStateException when moving from "
                        + StrictFragment.stateToString(fromState) + " to "
                        + StrictFragment.stateToString(toState));
            } catch (IllegalStateException e) {
                // expected, so the test passed!
            }
        });
    }

    @Test
    public void targetFragmentNoCycles() throws Throwable {
        final Fragment one = new Fragment();
        final Fragment two = new Fragment();
        final Fragment three = new Fragment();

        try {
            one.setTargetFragment(two, 0);
            two.setTargetFragment(three, 0);
            three.setTargetFragment(one, 0);
            assertTrue("creating a fragment target cycle did not throw IllegalArgumentException",
                    false);
        } catch (IllegalArgumentException e) {
            // Success!
        }
    }

    @Test
    public void targetFragmentSetClear() throws Throwable {
        final Fragment one = new Fragment();
        final Fragment two = new Fragment();

        one.setTargetFragment(two, 0);
        one.setTargetFragment(null, 0);
    }

    /**
     * When a fragment is saved in non-config, it should be restored to the same index.
     */
    @Test
    public void restoreNonConfig() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            FragmentController fc = FragmentTestUtil.createController(mActivityRule);
            FragmentTestUtil.resume(mActivityRule, fc, null);
            FragmentManager fm = fc.getFragmentManager();

            Fragment fragment1 = new StrictFragment();
            fm.beginTransaction()
                    .add(fragment1, "1")
                    .addToBackStack(null)
                    .commit();
            fm.executePendingTransactions();
            Fragment fragment2 = new StrictFragment();
            fragment2.setRetainInstance(true);
            fragment2.setTargetFragment(fragment1, 0);
            Fragment fragment3 = new StrictFragment();
            fm.beginTransaction()
                    .remove(fragment1)
                    .add(fragment2, "2")
                    .add(fragment3, "3")
                    .addToBackStack(null)
                    .commit();
            fm.executePendingTransactions();

            Pair<Parcelable, FragmentManagerNonConfig> savedState =
                    FragmentTestUtil.destroy(mActivityRule, fc);

            fc = FragmentTestUtil.createController(mActivityRule);
            FragmentTestUtil.resume(mActivityRule, fc, savedState);
            boolean foundFragment2 = false;
            for (Fragment fragment : fc.getFragmentManager().getFragments()) {
                if (fragment == fragment2) {
                    foundFragment2 = true;
                    assertNotNull(fragment.getTargetFragment());
                    assertEquals("1", fragment.getTargetFragment().getTag());
                } else {
                    assertFalse("2".equals(fragment.getTag()));
                }
            }
            assertTrue(foundFragment2);
        });
    }

    /**
     * Check that retained fragments in the backstack correctly restored after two "configChanges"
     */
    @Test
    public void retainedFragmentInBackstack() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            FragmentController fc = FragmentTestUtil.createController(mActivityRule);
            FragmentTestUtil.resume(mActivityRule, fc, null);
            FragmentManager fm = fc.getFragmentManager();

            Fragment fragment1 = new StrictFragment();
            fm.beginTransaction()
                    .add(fragment1, "1")
                    .addToBackStack(null)
                    .commit();
            fm.executePendingTransactions();

            Fragment child = new StrictFragment();
            child.setRetainInstance(true);
            fragment1.getChildFragmentManager().beginTransaction()
                    .add(child, "child").commit();
            fragment1.getChildFragmentManager().executePendingTransactions();

            Fragment fragment2 = new StrictFragment();
            fm.beginTransaction()
                    .remove(fragment1)
                    .add(fragment2, "2")
                    .addToBackStack(null)
                    .commit();
            fm.executePendingTransactions();

            Pair<Parcelable, FragmentManagerNonConfig> savedState =
                    FragmentTestUtil.destroy(mActivityRule, fc);

            fc = FragmentTestUtil.createController(mActivityRule);
            FragmentTestUtil.resume(mActivityRule, fc, savedState);
            savedState = FragmentTestUtil.destroy(mActivityRule, fc);
            fc = FragmentTestUtil.createController(mActivityRule);
            FragmentTestUtil.resume(mActivityRule, fc, savedState);
            fm = fc.getFragmentManager();
            fm.popBackStackImmediate();
            Fragment retainedChild = fm.findFragmentByTag("1")
                    .getChildFragmentManager().findFragmentByTag("child");
            assertEquals(child, retainedChild);
        });
    }

    /**
     * When a fragment has been optimized out, it state should still be saved during
     * save and restore instance state.
     */
    @Test
    public void saveRemovedFragment() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            FragmentController fc = FragmentTestUtil.createController(mActivityRule);
            FragmentTestUtil.resume(mActivityRule, fc, null);
            FragmentManager fm = fc.getFragmentManager();

            SaveStateFragment fragment1 = SaveStateFragment.create(1);
            fm.beginTransaction()
                    .add(android.R.id.content, fragment1, "1")
                    .addToBackStack(null)
                    .commit();
            SaveStateFragment fragment2 = SaveStateFragment.create(2);
            fm.beginTransaction()
                    .replace(android.R.id.content, fragment2, "2")
                    .addToBackStack(null)
                    .commit();
            fm.executePendingTransactions();

            Pair<Parcelable, FragmentManagerNonConfig> savedState =
                    FragmentTestUtil.destroy(mActivityRule, fc);

            fc = FragmentTestUtil.createController(mActivityRule);
            FragmentTestUtil.resume(mActivityRule, fc, savedState);
            fm = fc.getFragmentManager();
            fragment2 = (SaveStateFragment) fm.findFragmentByTag("2");
            assertNotNull(fragment2);
            assertEquals(2, fragment2.getValue());
            fm.popBackStackImmediate();
            fragment1 = (SaveStateFragment) fm.findFragmentByTag("1");
            assertNotNull(fragment1);
            assertEquals(1, fragment1.getValue());
        });
    }

    /**
     * When there are no retained instance fragments, the FragmentManagerNonConfig should be
     * null
     */
    @Test
    public void nullNonConfig() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            FragmentController fc = FragmentTestUtil.createController(mActivityRule);
            FragmentTestUtil.resume(mActivityRule, fc, null);
            FragmentManager fm = fc.getFragmentManager();

            Fragment fragment1 = new StrictFragment();
            fm.beginTransaction()
                    .add(fragment1, "1")
                    .addToBackStack(null)
                    .commit();
            fm.executePendingTransactions();
            Pair<Parcelable, FragmentManagerNonConfig> savedState =
                    FragmentTestUtil.destroy(mActivityRule, fc);
            assertNull(savedState.second);
        });
    }

    /**
     * When the FragmentManager state changes, the pending transactions should execute.
     */
    @Test
    public void runTransactionsOnChange() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            FragmentController fc = FragmentTestUtil.createController(mActivityRule);
            FragmentTestUtil.resume(mActivityRule, fc, null);
            FragmentManager fm = fc.getFragmentManager();

            RemoveHelloInOnResume fragment1 = new RemoveHelloInOnResume();
            StrictFragment fragment2 = new StrictFragment();
            fm.beginTransaction()
                    .add(fragment1, "1")
                    .setReorderingAllowed(false)
                    .commit();
            fm.beginTransaction()
                    .add(fragment2, "Hello")
                    .setReorderingAllowed(false)
                    .commit();
            fm.executePendingTransactions();

            assertEquals(2, fm.getFragments().size());
            assertTrue(fm.getFragments().contains(fragment1));
            assertTrue(fm.getFragments().contains(fragment2));

            Pair<Parcelable, FragmentManagerNonConfig> savedState =
                    FragmentTestUtil.destroy(mActivityRule, fc);
            fc = FragmentTestUtil.createController(mActivityRule);
            FragmentTestUtil.resume(mActivityRule, fc, savedState);
            fm = fc.getFragmentManager();

            assertEquals(1, fm.getFragments().size());
            for (Fragment fragment : fm.getFragments()) {
                assertTrue(fragment instanceof RemoveHelloInOnResume);
            }
        });
    }

    @Test
    public void optionsMenu() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            FragmentController fc = FragmentTestUtil.createController(mActivityRule);
            FragmentTestUtil.resume(mActivityRule, fc, null);
            FragmentManager fm = fc.getFragmentManager();

            InvalidateOptionFragment fragment = new InvalidateOptionFragment();
            fm.beginTransaction()
                    .add(android.R.id.content, fragment)
                    .commit();
            fm.executePendingTransactions();

            Menu menu = mock(Menu.class);
            fc.dispatchPrepareOptionsMenu(menu);
            assertTrue(fragment.onPrepareOptionsMenuCalled);
            fragment.onPrepareOptionsMenuCalled = false;
            FragmentTestUtil.destroy(mActivityRule, fc);
            fc.dispatchPrepareOptionsMenu(menu);
            assertFalse(fragment.onPrepareOptionsMenuCalled);
        });
    }


    /**
     * When a retained instance fragment is saved while in the back stack, it should go
     * through onCreate() when it is popped back.
     */
    @Test
    public void retainInstanceWithOnCreate() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            FragmentController fc = FragmentTestUtil.createController(mActivityRule);
            FragmentTestUtil.resume(mActivityRule, fc, null);
            FragmentManager fm = fc.getFragmentManager();

            OnCreateFragment fragment1 = new OnCreateFragment();

            fm.beginTransaction()
                    .add(fragment1, "1")
                    .commit();
            fm.beginTransaction()
                    .remove(fragment1)
                    .addToBackStack(null)
                    .commit();

            Pair<Parcelable, FragmentManagerNonConfig> savedState =
                    FragmentTestUtil.destroy(mActivityRule, fc);
            Pair<Parcelable, FragmentManagerNonConfig> restartState =
                    Pair.create(savedState.first, null);

            fc = FragmentTestUtil.createController(mActivityRule);
            FragmentTestUtil.resume(mActivityRule, fc, restartState);

            // Save again, but keep the state
            savedState = FragmentTestUtil.destroy(mActivityRule, fc);

            fc = FragmentTestUtil.createController(mActivityRule);
            FragmentTestUtil.resume(mActivityRule, fc, savedState);

            fm = fc.getFragmentManager();

            fm.popBackStackImmediate();
            OnCreateFragment fragment2 = (OnCreateFragment) fm.findFragmentByTag("1");
            assertTrue(fragment2.onCreateCalled);
            fm.popBackStackImmediate();
        });
    }

    /**
     * A retained instance fragment should go through onCreate() once, even through save and
     * restore.
     */
    @Test
    public void retainInstanceOneOnCreate() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            FragmentController fc = FragmentTestUtil.createController(mActivityRule);
            FragmentTestUtil.resume(mActivityRule, fc, null);
            FragmentManager fm = fc.getFragmentManager();

            OnCreateFragment fragment = new OnCreateFragment();

            fm.beginTransaction()
                    .add(fragment, "fragment")
                    .commit();
            fm.executePendingTransactions();

            fm.beginTransaction()
                    .remove(fragment)
                    .addToBackStack(null)
                    .commit();

            assertTrue(fragment.onCreateCalled);
            fragment.onCreateCalled = false;

            Pair<Parcelable, FragmentManagerNonConfig> savedState =
                    FragmentTestUtil.destroy(mActivityRule, fc);

            fc = FragmentTestUtil.createController(mActivityRule);
            FragmentTestUtil.resume(mActivityRule, fc, savedState);
            fm = fc.getFragmentManager();

            fm.popBackStackImmediate();
            assertFalse(fragment.onCreateCalled);
        });
    }

    private void executePendingTransactions(final FragmentManager fm) throws Throwable {
        mActivityRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                fm.executePendingTransactions();
            }
        });
    }

    /**
     * This tests a deliberately odd use of a child fragment, added in onCreateView instead
     * of elsewhere. It simulates creating a UI child fragment added to the view hierarchy
     * created by this fragment.
     */
    public static class ChildFragmentManagerFragment extends StrictFragment {
        private FragmentManager mSavedChildFragmentManager;
        private ChildFragmentManagerChildFragment mChildFragment;

        @Override
        public void onAttach(Context context) {
            super.onAttach(context);
            mSavedChildFragmentManager = getChildFragmentManager();
        }


        @Override
        public View onCreateView(LayoutInflater inflater,  ViewGroup container,
                 Bundle savedInstanceState) {
            assertSame("child FragmentManagers not the same instance", mSavedChildFragmentManager,
                    getChildFragmentManager());
            ChildFragmentManagerChildFragment child =
                    (ChildFragmentManagerChildFragment) mSavedChildFragmentManager
                            .findFragmentByTag("tag");
            if (child == null) {
                child = new ChildFragmentManagerChildFragment("foo");
                mSavedChildFragmentManager.beginTransaction()
                        .add(child, "tag")
                        .commitNow();
                assertEquals("argument strings don't match", "foo", child.getString());
            }
            mChildFragment = child;
            return new TextView(container.getContext());
        }


        public Fragment getChildFragment() {
            return mChildFragment;
        }
    }

    public static class ChildFragmentManagerChildFragment extends StrictFragment {
        private String mString;

        public ChildFragmentManagerChildFragment() {
        }

        public ChildFragmentManagerChildFragment(String arg) {
            final Bundle b = new Bundle();
            b.putString("string", arg);
            setArguments(b);
        }

        @Override
        public void onAttach(Context context) {
            super.onAttach(context);
            mString = getArguments().getString("string", "NO VALUE");
        }

        public String getString() {
            return mString;
        }
    }

    public static class TargetFragment extends Fragment {
        public boolean calledCreate;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            calledCreate = true;
        }
    }

    public static class ReferrerFragment extends Fragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            Fragment target = getTargetFragment();
            assertNotNull("target fragment was null during referrer onCreate", target);

            if (!(target instanceof TargetFragment)) {
                throw new IllegalStateException("target fragment was not a TargetFragment");
            }

            assertTrue("target fragment has not yet been created",
                    ((TargetFragment) target).calledCreate);
        }
    }

    static class HostCallbacks extends FragmentHostCallback<Activity> {
        private final Activity mActivity;

        public HostCallbacks(Activity activity) {
            super(activity, null, 0);
            mActivity = activity;
        }

        @Override
        public void onDump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        }

        @Override
        public boolean onShouldSaveFragmentState(Fragment fragment) {
            return !mActivity.isFinishing();
        }

        @Override
        public LayoutInflater onGetLayoutInflater() {
            return mActivity.getLayoutInflater().cloneInContext(mActivity);
        }

        @Override
        public Activity onGetHost() {
            return mActivity;
        }

        @Override
        public void onStartActivityFromFragment(
                Fragment fragment, Intent intent, int requestCode,  Bundle options) {
            mActivity.startActivityFromFragment(fragment, intent, requestCode, options);
        }

        @Override
        public void onRequestPermissionsFromFragment( Fragment fragment,
                 String[] permissions, int requestCode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean onHasWindowAnimations() {
            return mActivity.getWindow() != null;
        }

        @Override
        public int onGetWindowAnimations() {
            final Window w = mActivity.getWindow();
            return (w == null) ? 0 : w.getAttributes().windowAnimations;
        }

        @Override
        public void onAttachFragment(Fragment fragment) {
            mActivity.onAttachFragment(fragment);
        }

        @Override
        public View onFindViewById(int id) {
            return mActivity.findViewById(id);
        }

        @Override
        public boolean onHasView() {
            final Window w = mActivity.getWindow();
            return (w != null && w.peekDecorView() != null);
        }
    }

    public static class SaveStateFragment extends Fragment {
        private static final String VALUE_KEY = "SaveStateFragment.mValue";
        private int mValue;

        public static SaveStateFragment create(int value) {
            SaveStateFragment saveStateFragment = new SaveStateFragment();
            saveStateFragment.mValue = value;
            return saveStateFragment;
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putInt(VALUE_KEY, mValue);
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (savedInstanceState != null) {
                mValue = savedInstanceState.getInt(VALUE_KEY, mValue);
            }
        }

        public int getValue() {
            return mValue;
        }
    }

    public static class RemoveHelloInOnResume extends Fragment {
        @Override
        public void onResume() {
            super.onResume();
            Fragment fragment = getFragmentManager().findFragmentByTag("Hello");
            if (fragment != null) {
                getFragmentManager().beginTransaction().remove(fragment).commit();
            }
        }
    }

    public static class InvalidateOptionFragment extends Fragment {
        public boolean onPrepareOptionsMenuCalled;

        public InvalidateOptionFragment() {
            setHasOptionsMenu(true);
        }

        @Override
        public void onPrepareOptionsMenu(Menu menu) {
            onPrepareOptionsMenuCalled = true;
            assertNotNull(getContext());
            super.onPrepareOptionsMenu(menu);
        }
    }

    public static class OnCreateFragment extends Fragment {
        public boolean onCreateCalled;

        public OnCreateFragment() {
            setRetainInstance(true);
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            onCreateCalled = true;
        }
    }
}
