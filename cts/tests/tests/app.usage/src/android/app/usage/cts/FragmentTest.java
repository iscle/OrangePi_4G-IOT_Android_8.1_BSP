/*
 * Copyright (C) 2015 The Android Open Source Project
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
package android.app.usage.cts;

import android.annotation.TargetApi;
import android.app.Fragment;
import android.app.usage.cts.R;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.concurrent.atomic.AtomicInteger;

public class FragmentTest extends ActivityInstrumentationTestCase2<FragmentTestActivity> {
    protected FragmentTestActivity mActivity;

    public FragmentTest() {
        super(FragmentTestActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        setActivityInitialTouchMode(false);
        mActivity = getActivity();
    }

    @UiThreadTest
    public void testOnCreateOrder() throws Throwable {
        TestFragment fragment1 = new TestFragment();
        TestFragment fragment2 = new TestFragment();
        mActivity.getFragmentManager()
                .beginTransaction()
                .add(R.id.container, fragment1)
                .add(R.id.container, fragment2)
                .commitNow();
        assertEquals(0, fragment1.createOrder);
        assertEquals(1, fragment2.createOrder);
    }

    public void testChildFragmentManagerGone() throws Throwable {
        final FragmentA fragmentA = new FragmentA();
        final FragmentB fragmentB = new FragmentB();
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mActivity.getFragmentManager().beginTransaction()
                        .add(R.id.container, fragmentA)
                        .commitNow();
            }
        });
        getInstrumentation().waitForIdleSync();
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mActivity.getFragmentManager().beginTransaction()
                        .setCustomAnimations(R.animator.long_fade_in, R.animator.long_fade_out,
                                R.animator.long_fade_in, R.animator.long_fade_out)
                        .replace(R.id.container, fragmentB)
                        .addToBackStack(null)
                        .commit();
            }
        });
        // Wait for the middle of the animation
        Thread.sleep(150);
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mActivity.getFragmentManager().beginTransaction()
                        .setCustomAnimations(R.animator.long_fade_in, R.animator.long_fade_out,
                                R.animator.long_fade_in, R.animator.long_fade_out)
                        .replace(R.id.container, fragmentA)
                        .addToBackStack(null)
                        .commit();
            }
        });
        // Wait for the middle of the animation
        Thread.sleep(150);
        getInstrumentation().waitForIdleSync();
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mActivity.getFragmentManager().popBackStack();
            }
        });
        // Wait for the middle of the animation
        Thread.sleep(150);
        getInstrumentation().waitForIdleSync();
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mActivity.getFragmentManager().popBackStack();
            }
        });
    }

    @TargetApi(VERSION_CODES.HONEYCOMB)
    public static class TestFragment extends Fragment {
        private static AtomicInteger sOrder = new AtomicInteger();
        public int createOrder = -1;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            createOrder = sOrder.getAndIncrement();
            super.onCreate(savedInstanceState);
        }
    }

    public static class FragmentA extends Fragment {
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_a, container, false);
        }
    }

    public static class FragmentB extends Fragment {
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_b, container, false);
        }
    }
}
