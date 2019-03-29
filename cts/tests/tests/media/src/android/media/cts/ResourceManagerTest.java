/*
 * Copyright 2015 The Android Open Source Project
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

package android.media.cts;

import android.os.Bundle;
import android.platform.test.annotations.RequiresDevice;
import android.support.test.filters.SmallTest;
import android.test.ActivityInstrumentationTestCase2;

@SmallTest
@RequiresDevice
public class ResourceManagerTest
        extends ActivityInstrumentationTestCase2<ResourceManagerStubActivity> {

    public ResourceManagerTest() {
        super("android.media.cts", ResourceManagerStubActivity.class);
    }

    private void doTestReclaimResource(int type1, int type2) throws Exception {
        Bundle extras = new Bundle();
        ResourceManagerStubActivity activity = launchActivity(
                "android.media.cts", ResourceManagerStubActivity.class, extras);
        activity.testReclaimResource(type1, type2);
        activity.finish();
    }

    public void testReclaimResourceNonsecureVsNonsecure() throws Exception {
        doTestReclaimResource(ResourceManagerTestActivityBase.TYPE_NONSECURE,
                ResourceManagerTestActivityBase.TYPE_NONSECURE);
    }

    public void testReclaimResourceNonsecureVsSecure() throws Exception {
        doTestReclaimResource(ResourceManagerTestActivityBase.TYPE_NONSECURE,
                ResourceManagerTestActivityBase.TYPE_SECURE);
    }

    public void testReclaimResourceSecureVsNonsecure() throws Exception {
        doTestReclaimResource(ResourceManagerTestActivityBase.TYPE_SECURE,
                ResourceManagerTestActivityBase.TYPE_NONSECURE);
    }

    public void testReclaimResourceSecureVsSecure() throws Exception {
        doTestReclaimResource(ResourceManagerTestActivityBase.TYPE_SECURE,
                ResourceManagerTestActivityBase.TYPE_SECURE);
    }

    public void testReclaimResourceMixVsNonsecure() throws Exception {
        doTestReclaimResource(ResourceManagerTestActivityBase.TYPE_MIX,
                ResourceManagerTestActivityBase.TYPE_NONSECURE);
    }

    public void testReclaimResourceMixVsSecure() throws Exception {
        doTestReclaimResource(ResourceManagerTestActivityBase.TYPE_MIX,
                ResourceManagerTestActivityBase.TYPE_SECURE);
    }
}
