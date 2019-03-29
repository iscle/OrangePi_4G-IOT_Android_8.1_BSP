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

package android.autofillservice.cts;

import android.os.Bundle;
import android.support.annotation.Nullable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Activity containing an fragment
 */
public class FragmentContainerActivity extends AbstractAutoFillActivity {
    static final String FRAGMENT_TAG =
            FragmentContainerActivity.class.getName() + "#FRAGMENT_TAG";
    private CountDownLatch mResumed = new CountDownLatch(1);
    private CountDownLatch mStopped = new CountDownLatch(0);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.fragment_container);

        // have to manually add fragment as we cannot remove it otherwise
        getFragmentManager().beginTransaction().add(R.id.rootContainer,
                new FragmentWithEditText(), FRAGMENT_TAG).commitNow();
    }

    @Override
    protected void onStart() {
        super.onStart();

        mStopped = new CountDownLatch(1);
    }

    @Override
    protected void onResume() {
        super.onResume();

        mResumed.countDown();
    }

    @Override
    protected void onPause() {
        super.onPause();

        mResumed = new CountDownLatch(1);
    }

    @Override
    protected void onStop() {
        super.onStop();

        mStopped.countDown();
    }

    public boolean waitUntilResumed() throws InterruptedException {
        return mResumed.await(Helper.UI_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    public boolean waitUntilStopped() throws InterruptedException {
        return mStopped.await(Helper.UI_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }
}
