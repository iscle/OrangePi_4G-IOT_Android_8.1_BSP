/*
 * Copyright 2017 The Android Open Source Project
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

package android.camera.cts.api25test;

import android.content.Context;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.cts.CameraTestUtils;
import android.hardware.camera2.cts.helpers.CameraErrorCollector;
import android.hardware.camera2.cts.helpers.StaticMetadata;
import android.hardware.camera2.cts.helpers.StaticMetadata.CheckLevel;
import android.os.Handler;
import android.os.HandlerThread;
import android.test.AndroidTestCase;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Set;
import java.util.HashSet;

public class EnableZslTest extends AndroidTestCase {
    private static final String TAG = "EnableZslTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    private CameraManager mCameraManager;
    private String[] mCameraIds;
    private Handler mHandler;
    private HandlerThread mHandlerThread;
    private StaticMetadata mStaticInfo;
    private CameraErrorCollector mCollector;

    private static int[] sTemplates = new int[] {
            CameraDevice.TEMPLATE_MANUAL,
            CameraDevice.TEMPLATE_PREVIEW,
            CameraDevice.TEMPLATE_RECORD,
            CameraDevice.TEMPLATE_STILL_CAPTURE,
            CameraDevice.TEMPLATE_VIDEO_SNAPSHOT,
            CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG,
    };

    // Request templates that are unsupported by LEGACY mode.
    private static Set<Integer> sLegacySkipTemplates = new HashSet<>();
    static {
        sLegacySkipTemplates.add(CameraDevice.TEMPLATE_VIDEO_SNAPSHOT);
        sLegacySkipTemplates.add(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);
        sLegacySkipTemplates.add(CameraDevice.TEMPLATE_MANUAL);
    }

    @Override
    public void setContext(Context context) {
        super.setContext(context);
        mCameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        assertNotNull("Can't connect to camera manager!", mCameraManager);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mCameraIds = mCameraManager.getCameraIdList();
        assertNotNull("Camera ids shouldn't be null", mCameraIds);

        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        mCollector = new CameraErrorCollector();
    }

    @Override
    protected void tearDown() throws Exception {
        mHandlerThread.quitSafely();
        mHandler = null;

        try {
            mCollector.verify();
        } catch (Throwable e) {
            // When new Exception(e) is used, exception info will be printed twice.
            throw new Exception(e.getMessage());
        } finally {
            super.tearDown();
        }
    }

    // Get android.control.enableZsl value from the request. Note that android.control.enableZsl
    // is not a public key in API 25 so reflect is used to get the value.
    private Boolean getEnableZslValue(CaptureRequest.Builder request) throws Exception {
        Field[] allFields = CaptureRequest.class.getDeclaredFields();
        for (Field field : allFields) {
            if (Modifier.isStatic(field.getModifiers()) &&
                    field.getType() == CaptureRequest.Key.class &&
                    field.getGenericType() instanceof ParameterizedType) {
                ParameterizedType paramType = (ParameterizedType)field.getGenericType();
                Type[] argTypes = paramType.getActualTypeArguments();
                if (argTypes.length > 0) {
                    CaptureRequest.Key key = (CaptureRequest.Key)field.get(request);
                    if (key.getName().equals("android.control.enableZsl")) {
                        return (Boolean)request.get(key);
                    }
                }
            }
        }

        return null;
    }

    // Verify CaptureRequest.CONTROL_ENABLE_ZSL values for a camera.
    private void testEnableZslValueByCamera(String cameraId) throws Exception {
        CameraDevice camera = CameraTestUtils.openCamera(mCameraManager, cameraId,
                /*listener*/null, mHandler);

        StaticMetadata staticInfo = new StaticMetadata(
                mCameraManager.getCameraCharacteristics(cameraId), CheckLevel.ASSERT,
                mCollector);

        for (int i = 0; i < sTemplates.length; i++) {
            try {
                CaptureRequest.Builder request = camera.createCaptureRequest(sTemplates[i]);
                Boolean enableZsl = getEnableZslValue(request);
                if (enableZsl != null) {
                    if (VERBOSE) {
                        Log.v(TAG, "enableZsl is " + enableZsl + " for template " + sTemplates[i]);
                    }

                    mCollector.expectTrue("CaptureRequest.CONTROL_ENABLE_ZSL should be false.",
                            !enableZsl);
                }
            } catch (IllegalArgumentException e) {
                if (sTemplates[i] == CameraDevice.TEMPLATE_MANUAL &&
                        !staticInfo.isCapabilitySupported(CameraCharacteristics.
                                REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)) {
                    // OK
                } else if (sTemplates[i] == CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG &&
                        !staticInfo.isCapabilitySupported(CameraCharacteristics.
                                REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING) &&
                        !staticInfo.isCapabilitySupported(CameraCharacteristics.
                                REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING)) {
                   // OK.
                } else if (sLegacySkipTemplates.contains(sTemplates[i]) &&
                        staticInfo.isHardwareLevelLegacy()) {
                   // OK
                } else if (sTemplates[i] != CameraDevice.TEMPLATE_PREVIEW &&
                        !staticInfo.isColorOutputSupported()) {
                   // OK, depth-only devices need only support PREVIEW template
                } else {
                   throw e; // rethrow
                }
            }
        }
        camera.close();
    }

    /**
     * Verify CaptureRequest.CONTROL_ENABLE_ZSL values.
     * <p>Verify CaptureRequest.CONTROL_ENABLE_ZSL is false if available in all templates.</p>
     */
    public void testEnableZslValue() throws Exception {
        for (int i = 0; i < mCameraIds.length; i++) {
            testEnableZslValueByCamera(mCameraIds[i]);
        }
    }
}
