package com.android.bluetooth.hdp;

import android.test.InstrumentationTestCase;

import junit.framework.Assert;

public class HealthServiceTest extends InstrumentationTestCase {

    public void testRegisterAppConfiguration() {
        HealthService testService = new HealthService();

        // Attach a context to the Health Service for permission checks
        testService.attach(getInstrumentation().getContext(), null, null, null, null, null);

        // Test registering a null config
        assertEquals(false, testService.registerAppConfiguration(null, null));
    }

}
