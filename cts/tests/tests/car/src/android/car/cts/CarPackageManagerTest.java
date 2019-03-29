/*
 * Copyright (C) 2016 The Android Open Source Project
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
package android.car.cts;

import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.content.pm.CarPackageManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.platform.test.annotations.RequiresDevice;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.List;

@SmallTest
@RequiresDevice
public class CarPackageManagerTest extends CarApiTestBase {

    private CarPackageManager mCarPm;
    private static String TAG = CarPackageManagerTest.class.getSimpleName();

    /** Name of the meta-data attribute for the automotive application XML resource */
    private static final String METADATA_ATTRIBUTE = "android.car.application";


    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mCarPm = (CarPackageManager) getCar().getCarManager(Car.PACKAGE_SERVICE);
    }

   public void testActivityAllowedWhileDriving() throws Exception {
       assertFalse(mCarPm.isActivityAllowedWhileDriving("com.basic.package", "DummyActivity"));
       // Real system activity is not allowed as well.
       assertFalse(mCarPm.isActivityAllowedWhileDriving("com.android.phone", "CallActivity"));

       try {
           mCarPm.isActivityAllowedWhileDriving("com.android.settings", null);
           fail();
       } catch (IllegalArgumentException expected) {
           // Expected.
       }
       try {
           mCarPm.isActivityAllowedWhileDriving(null, "Any");
           fail();
       } catch (IllegalArgumentException expected) {
           // Expected.
       }
       try {
           mCarPm.isActivityAllowedWhileDriving(null, null);
           fail();
       } catch (IllegalArgumentException expected) {
           // Expected.
       }
   }

    public void testSystemActivitiesAllowed() throws CarNotConnectedException {
        List<PackageInfo> packages = getContext().getPackageManager().getInstalledPackages(
                PackageManager.GET_ACTIVITIES | PackageManager.GET_META_DATA);

        for (PackageInfo info : packages) {
            if (info.applicationInfo == null) {
                continue;
            }
            if ((info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    ((info.applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)) {

                Bundle metaData = info.applicationInfo.metaData;
                if (metaData == null || metaData.getInt(METADATA_ATTRIBUTE, 0) == 0) {
                    continue;  // No car metadata, ignoring this app.
                }

                if (info.activities != null && info.activities.length > 0) {
                    String activity = info.activities[0].name;
                    String packageName = info.packageName;
                    assertTrue("Failed for package: " + packageName + ", activity: " + activity,
                            mCarPm.isActivityAllowedWhileDriving(packageName, activity));
                }
            }
        }
    }

    public void testServiceAllowedWhileDriving() throws Exception {
        assertFalse(mCarPm.isServiceAllowedWhileDriving("com.basic.package", ""));
        assertTrue(mCarPm.isServiceAllowedWhileDriving("com.android.settings", "Any"));
        assertTrue(mCarPm.isServiceAllowedWhileDriving("com.android.settings", ""));
        assertTrue(mCarPm.isServiceAllowedWhileDriving("com.android.settings", null));

        try {
            mCarPm.isServiceAllowedWhileDriving(null, "Any");
            fail();
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

}
