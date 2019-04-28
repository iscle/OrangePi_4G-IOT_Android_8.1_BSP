
package com.android.bluetooth.gatt;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.bluetooth.gatt.GattService;

/**
 * Test cases for {@link GattService}.
 */
public class GattServiceTest extends AndroidTestCase {

    @SmallTest
    public void testParseBatchTimestamp() {
        GattService service = new GattService();
        long timestampNanos = service.parseTimestampNanos(new byte[] {
                -54, 7 });
        assertEquals(99700000000L, timestampNanos);
    }

}
