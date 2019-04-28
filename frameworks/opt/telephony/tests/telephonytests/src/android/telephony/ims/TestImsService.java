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

package android.telephony.ims;

import android.content.Context;
import android.telephony.ims.feature.MMTelFeature;
import android.telephony.ims.feature.RcsFeature;

import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import static org.mockito.Mockito.spy;

/**
 * Test ImsService used by mockito to verify functionality.
 */

public class TestImsService extends ImsService {

    public TestMMTelFeature mSpyMMTelFeature;
    private TestMMTelFeature mTestMMTelFeature;

    public TestImsService(Context context) {
        attachBaseContext(context);
        MockitoAnnotations.initMocks(this);
        // Must create real MMTelFeature to initialize ImsFeature objects.
        mTestMMTelFeature = new TestMMTelFeature();
        mSpyMMTelFeature = spy(mTestMMTelFeature);
    }

    @Override
    public MMTelFeature onCreateEmergencyMMTelImsFeature(int slotId) {
        return null;
    }

    @Override
    public MMTelFeature onCreateMMTelImsFeature(int slotId) {
        return mSpyMMTelFeature;
    }

    @Override
    public RcsFeature onCreateRcsFeature(int slotId) {
        return null;
    }
}
