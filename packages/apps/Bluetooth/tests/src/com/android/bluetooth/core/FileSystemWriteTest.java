package com.android.bluetooth;

import android.test.AndroidTestCase;
import java.io.IOException;
import java.io.File;

// Test Bluetooth's ability to write to the different directories that it
// is supposed to own
public class FileSystemWriteTest extends AndroidTestCase {
    public void testBluetoothDirWrite() {
        try {
            File file = new File("/data/misc/bluetooth/test.file");
            assertTrue("File not created", file.createNewFile());
            file.delete();
        } catch (IOException e) {
            fail("Exception creating file /data/misc/bluetooth/test.file: " + e);
        }
    }

    public void testBluedroidDirWrite() {
        try {
            File file = new File("/data/misc/bluedroid/test.file");
            assertTrue("File not created", file.createNewFile());
            file.delete();
        } catch (IOException e) {
            fail("Exception creating file /data/misc/bluedroid/test.file: " + e);
        }
    }

    public void testBluetoothLogsDirWrite() {
        try {
            File file = new File("/data/misc/bluetooth/logs/test.file");
            assertTrue("File not created", file.createNewFile());
            file.delete();
        } catch (IOException e) {
            fail("Exception creating file /data/misc/bluetooth/logs/test.file: " + e);
        }
    }
}
