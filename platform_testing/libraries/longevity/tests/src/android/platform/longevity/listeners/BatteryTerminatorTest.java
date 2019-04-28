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
package android.platform.longevity.listeners;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.JUnit4;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit test the logic for {@link BatteryTerminator}
 */
@RunWith(JUnit4.class)
public class BatteryTerminatorTest {
    private BatteryTerminator mListener;
    @Mock private RunNotifier mNotifier;
    @Mock private Context mContext;
    @Mock private Intent mIntent;

    @Before
    public void setupListener() {
        MockitoAnnotations.initMocks(this);
        when(mContext.registerReceiver(any(), any())).thenReturn(mIntent);
        when(mIntent.getIntExtra("scale", -1)).thenReturn(100);
        Bundle args = new Bundle();
        args.putString(BatteryTerminator.OPTION, String.valueOf(0.5));
        mListener = new BatteryTerminator(mNotifier, args, mContext);
    }

    /**
     * Unit test the listener's stops on low battery.
     */
    @Test
    @SmallTest
    public void testBatteryTerminator_low() throws Exception {
        when(mIntent.getIntExtra("level", -1)).thenReturn(25);
        mListener.testFinished(Description.EMPTY);
        verify(mNotifier).pleaseStop();
    }

    /**
     * Unit test the listener's does not stop on high battery.
     */
    @Test
    @SmallTest
    public void testBatteryTerminator_high() throws Exception {
        when(mIntent.getIntExtra("level", -1)).thenReturn(75);
        mListener.testFinished(Description.EMPTY);
        verify(mNotifier, never()).pleaseStop();
    }
}
