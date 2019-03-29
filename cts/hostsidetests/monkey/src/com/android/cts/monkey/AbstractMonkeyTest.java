package com.android.cts.monkey;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.AbiUtils;

import java.io.File;

abstract class AbstractMonkeyTest extends DeviceTestCase implements IAbiReceiver, IBuildReceiver {

    static final String[] PKGS = {"com.android.cts.monkey", "com.android.cts.monkey2"};
    static final String[] APKS = {"CtsMonkeyApp.apk", "CtsMonkeyApp2.apk"};

    /**
     * Base monkey command with flags to avoid side effects like airplane mode.
     */
    static final String MONKEY_CMD = "monkey --pct-motion 0 --pct-majornav 0 --pct-syskeys 0 --pct-anyevent 0 --pct-rotation 0";

    IAbi mAbi;
    IBuildInfo mBuild;
    ITestDevice mDevice;

    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuild = buildInfo;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDevice = getDevice();
        String[] options = {AbiUtils.createAbiFlag(mAbi.getName())};
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mBuild);
        for (int i = 0; i < PKGS.length; i++) {
            mDevice.uninstallPackage(PKGS[i]);
            File app = buildHelper.getTestFile(APKS[i]);
            mDevice.installPackage(app, false, options);
        }
        clearLogCat();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        for (int i = 0; i < PKGS.length; i++) {
            mDevice.uninstallPackage(PKGS[i]);
        }
    }

    private void clearLogCat() throws DeviceNotAvailableException {
        mDevice.executeAdbCommand("logcat", "-c");
    }
}
