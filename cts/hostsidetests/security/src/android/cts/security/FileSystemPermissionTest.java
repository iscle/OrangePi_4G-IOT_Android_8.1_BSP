package android.cts.security;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceTestCase;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class FileSystemPermissionTest extends DeviceTestCase {

   /**
    * A reference to the device under test.
    */
    private ITestDevice mDevice;

    /**
     * Used to build the find command for finding insecure file system components
     */
    private static final String INSECURE_DEVICE_ADB_COMMAND = "find %s -type %s -perm /o=rwx 2>/dev/null";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDevice = getDevice();
    }

    public void testAllBlockDevicesAreSecure() throws Exception {
        Set<String> insecure = getAllInsecureDevicesInDirAndSubdir("/dev", "b");
        assertTrue("Found insecure block devices: " + insecure.toString(),
                insecure.isEmpty());
    }

    /**
     * Searches for all world accessable files, note this may need sepolicy to search the desired
     * location and stat files.
     * @path The path to search, must be a directory.
     * @type The type of file to search for, must be a valid find command argument to the type
     *       option.
     * @returns The set of insecure fs objects found.
     */
    private Set<String> getAllInsecureDevicesInDirAndSubdir(String path, String type) throws DeviceNotAvailableException {

        String cmd = getInsecureDeviceAdbCommand(path, type);
        String output = mDevice.executeShellCommand(cmd);
        // Splitting an empty string results in an array of an empty string.
        String [] found = output.length() > 0 ? output.split("\\s") : new String[0];
        return new HashSet<String>(Arrays.asList(found));
    }

    private static String getInsecureDeviceAdbCommand(String path, String type) {
        return String.format(INSECURE_DEVICE_ADB_COMMAND, path, type);
    }
}
