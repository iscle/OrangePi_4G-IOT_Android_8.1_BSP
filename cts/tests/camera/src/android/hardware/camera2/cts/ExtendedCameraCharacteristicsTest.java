/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.hardware.camera2.cts;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraCharacteristics.Key;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.cts.helpers.CameraErrorCollector;
import android.hardware.camera2.params.BlackLevelPattern;
import android.hardware.camera2.params.ColorSpaceTransform;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.ImageReader;
import android.test.AndroidTestCase;
import android.util.Log;
import android.util.Rational;
import android.util.Range;
import android.util.Size;
import android.util.Patterns;
import android.view.Surface;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static android.hardware.camera2.cts.helpers.AssertHelpers.*;

/**
 * Extended tests for static camera characteristics.
 */
public class ExtendedCameraCharacteristicsTest extends AndroidTestCase {
    private static final String TAG = "ExChrsTest"; // must be short so next line doesn't throw
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    private static final String PREFIX_ANDROID = "android";

    /*
     * Constants for static RAW metadata.
     */
    private static final int MIN_ALLOWABLE_WHITELEVEL = 32; // must have sensor bit depth > 5

    private CameraManager mCameraManager;
    private List<CameraCharacteristics> mCharacteristics;
    private String[] mIds;
    private CameraErrorCollector mCollector;

    private static final Size FULLHD = new Size(1920, 1080);
    private static final Size FULLHD_ALT = new Size(1920, 1088);
    private static final Size HD = new Size(1280, 720);
    private static final Size VGA = new Size(640, 480);
    private static final Size QVGA = new Size(320, 240);

    /*
     * HW Levels short hand
     */
    private static final int LEGACY = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY;
    private static final int LIMITED = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED;
    private static final int FULL = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL;
    private static final int LEVEL_3 = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3;
    private static final int OPT = Integer.MAX_VALUE;  // For keys that are optional on all hardware levels.

    /*
     * Capabilities short hand
     */
    private static final int NONE = -1;
    private static final int BC =
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE;
    private static final int MANUAL_SENSOR =
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR;
    private static final int MANUAL_POSTPROC =
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING;
    private static final int RAW =
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW;
    private static final int YUV_REPROCESS =
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING;
    private static final int OPAQUE_REPROCESS =
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING;
    private static final int CONSTRAINED_HIGH_SPEED =
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO;
    private static final int HIGH_SPEED_FPS_LOWER_MIN = 30;
    private static final int HIGH_SPEED_FPS_UPPER_MIN = 120;

    @Override
    public void setContext(Context context) {
        super.setContext(context);
        mCameraManager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
        assertNotNull("Can't connect to camera manager", mCameraManager);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mIds = mCameraManager.getCameraIdList();
        mCharacteristics = new ArrayList<>();
        mCollector = new CameraErrorCollector();
        for (int i = 0; i < mIds.length; i++) {
            CameraCharacteristics props = mCameraManager.getCameraCharacteristics(mIds[i]);
            assertNotNull(String.format("Can't get camera characteristics from: ID %s", mIds[i]),
                    props);
            mCharacteristics.add(props);
        }
    }

    @Override
    protected void tearDown() throws Exception {
        mCharacteristics = null;

        try {
            mCollector.verify();
        } catch (Throwable e) {
            // When new Exception(e) is used, exception info will be printed twice.
            throw new Exception(e.getMessage());
        } finally {
            super.tearDown();
        }
    }

    /**
     * Test that the available stream configurations contain a few required formats and sizes.
     */
    public void testAvailableStreamConfigs() {
        int counter = 0;
        for (CameraCharacteristics c : mCharacteristics) {
            StreamConfigurationMap config =
                    c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assertNotNull(String.format("No stream configuration map found for: ID %s",
                    mIds[counter]), config);
            int[] outputFormats = config.getOutputFormats();

            int[] actualCapabilities = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            assertNotNull("android.request.availableCapabilities must never be null",
                    actualCapabilities);

            // Check required formats exist (JPEG, and YUV_420_888).
            if (!arrayContains(actualCapabilities, BC)) {
                Log.i(TAG, "Camera " + mIds[counter] +
                    ": BACKWARD_COMPATIBLE capability not supported, skipping test");
                continue;
            }

            assertArrayContains(
                    String.format("No valid YUV_420_888 preview formats found for: ID %s",
                            mIds[counter]), outputFormats, ImageFormat.YUV_420_888);
            assertArrayContains(String.format("No JPEG image format for: ID %s",
                    mIds[counter]), outputFormats, ImageFormat.JPEG);

            Size[] yuvSizes = config.getOutputSizes(ImageFormat.YUV_420_888);
            Size[] jpegSizes = config.getOutputSizes(ImageFormat.JPEG);
            Size[] privateSizes = config.getOutputSizes(ImageFormat.PRIVATE);

            CameraTestUtils.assertArrayNotEmpty(yuvSizes,
                    String.format("No sizes for preview format %x for: ID %s",
                            ImageFormat.YUV_420_888, mIds[counter]));

            Rect activeRect = CameraTestUtils.getValueNotNull(
                    c, CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            Size activeArraySize = new Size(activeRect.width(), activeRect.height());
            Integer hwLevel = c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);

            if (activeArraySize.getWidth() >= FULLHD.getWidth() &&
                    activeArraySize.getHeight() >= FULLHD.getHeight()) {
                assertArrayContainsAnyOf(String.format(
                        "Required FULLHD size not found for format %x for: ID %s",
                        ImageFormat.JPEG, mIds[counter]), jpegSizes,
                        new Size[] {FULLHD, FULLHD_ALT});
            }

            if (activeArraySize.getWidth() >= HD.getWidth() &&
                    activeArraySize.getHeight() >= HD.getHeight()) {
                assertArrayContains(String.format(
                        "Required HD size not found for format %x for: ID %s",
                        ImageFormat.JPEG, mIds[counter]), jpegSizes, HD);
            }

            if (activeArraySize.getWidth() >= VGA.getWidth() &&
                    activeArraySize.getHeight() >= VGA.getHeight()) {
                assertArrayContains(String.format(
                        "Required VGA size not found for format %x for: ID %s",
                        ImageFormat.JPEG, mIds[counter]), jpegSizes, VGA);
            }

            if (activeArraySize.getWidth() >= QVGA.getWidth() &&
                    activeArraySize.getHeight() >= QVGA.getHeight()) {
                assertArrayContains(String.format(
                        "Required QVGA size not found for format %x for: ID %s",
                        ImageFormat.JPEG, mIds[counter]), jpegSizes, QVGA);
            }

            ArrayList<Size> jpegSizesList = new ArrayList<>(Arrays.asList(jpegSizes));
            ArrayList<Size> yuvSizesList = new ArrayList<>(Arrays.asList(yuvSizes));
            ArrayList<Size> privateSizesList = new ArrayList<>(Arrays.asList(privateSizes));

            int cameraId = Integer.valueOf(mIds[counter]);
            CamcorderProfile maxVideoProfile = CamcorderProfile.get(
                    cameraId, CamcorderProfile.QUALITY_HIGH);
            Size maxVideoSize = new Size(
                    maxVideoProfile.videoFrameWidth, maxVideoProfile.videoFrameHeight);

            // Handle FullHD special case first
            if (jpegSizesList.contains(FULLHD)) {
                if (hwLevel >= LEVEL_3 || hwLevel == FULL || (hwLevel == LIMITED &&
                        maxVideoSize.getWidth() >= FULLHD.getWidth() &&
                        maxVideoSize.getHeight() >= FULLHD.getHeight())) {
                    boolean yuvSupportFullHD = yuvSizesList.contains(FULLHD) ||
                            yuvSizesList.contains(FULLHD_ALT);
                    boolean privateSupportFullHD = privateSizesList.contains(FULLHD) ||
                            privateSizesList.contains(FULLHD_ALT);
                    assertTrue("Full device FullHD YUV size not found", yuvSupportFullHD);
                    assertTrue("Full device FullHD PRIVATE size not found", privateSupportFullHD);
                }
                // remove all FullHD or FullHD_Alt sizes for the remaining of the test
                jpegSizesList.remove(FULLHD);
                jpegSizesList.remove(FULLHD_ALT);
            }

            // Check all sizes other than FullHD
            if (hwLevel == LIMITED) {
                // Remove all jpeg sizes larger than max video size
                ArrayList<Size> toBeRemoved = new ArrayList<>();
                for (Size size : jpegSizesList) {
                    if (size.getWidth() >= maxVideoSize.getWidth() &&
                            size.getHeight() >= maxVideoSize.getHeight()) {
                        toBeRemoved.add(size);
                    }
                }
                jpegSizesList.removeAll(toBeRemoved);
            }

            if (hwLevel >= LEVEL_3 || hwLevel == FULL || hwLevel == LIMITED) {
                if (!yuvSizesList.containsAll(jpegSizesList)) {
                    for (Size s : jpegSizesList) {
                        if (!yuvSizesList.contains(s)) {
                            fail("Size " + s + " not found in YUV format");
                        }
                    }
                }
            }

            if (!privateSizesList.containsAll(yuvSizesList)) {
                for (Size s : yuvSizesList) {
                    if (!privateSizesList.contains(s)) {
                        fail("Size " + s + " not found in PRIVATE format");
                    }
                }
            }

            counter++;
        }
    }

    /**
     * Test {@link CameraCharacteristics#getKeys}
     */
    public void testKeys() {
        int counter = 0;
        for (CameraCharacteristics c : mCharacteristics) {
            mCollector.setCameraId(mIds[counter]);

            if (VERBOSE) {
                Log.v(TAG, "testKeys - testing characteristics for camera " + mIds[counter]);
            }

            List<CameraCharacteristics.Key<?>> allKeys = c.getKeys();
            assertNotNull("Camera characteristics keys must not be null", allKeys);
            assertFalse("Camera characteristics keys must have at least 1 key",
                    allKeys.isEmpty());

            for (CameraCharacteristics.Key<?> key : allKeys) {
                assertKeyPrefixValid(key.getName());

                // All characteristics keys listed must never be null
                mCollector.expectKeyValueNotNull(c, key);

                // TODO: add a check that key must not be @hide
            }

            /*
             * List of keys that must be present in camera characteristics (not null).
             *
             * Keys for LIMITED, FULL devices might be available despite lacking either
             * the hardware level or the capability. This is *OK*. This only lists the
             * *minimal* requirements for a key to be listed.
             *
             * LEGACY devices are a bit special since they map to api1 devices, so we know
             * for a fact most keys are going to be illegal there so they should never be
             * available.
             *
             * For LIMITED-level keys, if the level is >= LIMITED, then the capabilities are used to
             * do the actual checking.
             */
            {
                //                                           (Key Name)                                     (HW Level)  (Capabilities <Var-Arg>)
                expectKeyAvailable(c, CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES     , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.CONTROL_AVAILABLE_MODES                         , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.CONTROL_AE_AVAILABLE_ANTIBANDING_MODES          , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES                      , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES          , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE                   , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP                    , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE                       , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES                      , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS                       , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES                   , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES     , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES                     , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE                      , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.CONTROL_MAX_REGIONS_AE                          , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.CONTROL_MAX_REGIONS_AF                          , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.CONTROL_MAX_REGIONS_AWB                         , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES                       , FULL     ,   NONE                 );
                expectKeyAvailable(c, CameraCharacteristics.FLASH_INFO_AVAILABLE                            , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES             , OPT      ,   RAW                  );
                expectKeyAvailable(c, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL                   , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.JPEG_AVAILABLE_THUMBNAIL_SIZES                  , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.LENS_FACING                                     , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES                   , FULL     ,   MANUAL_SENSOR        );
                expectKeyAvailable(c, CameraCharacteristics.LENS_INFO_AVAILABLE_FILTER_DENSITIES            , FULL     ,   MANUAL_SENSOR        );
                expectKeyAvailable(c, CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS               , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION       , LIMITED  ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION            , LIMITED  ,   MANUAL_SENSOR        );
                expectKeyAvailable(c, CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE                   , LIMITED  ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE                , LIMITED  ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES                  , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.REQUEST_MAX_NUM_INPUT_STREAMS                   , OPT      ,   YUV_REPROCESS, OPAQUE_REPROCESS);
                expectKeyAvailable(c, CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP                 , OPT      ,   CONSTRAINED_HIGH_SPEED);
                expectKeyAvailable(c, CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_PROC                     , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_PROC_STALLING            , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_RAW                      , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.REQUEST_PARTIAL_RESULT_COUNT                    , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.REQUEST_PIPELINE_MAX_DEPTH                      , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM               , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP                 , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.SCALER_CROPPING_TYPE                            , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.SENSOR_AVAILABLE_TEST_PATTERN_MODES             , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN                      , FULL     ,   MANUAL_SENSOR, RAW   );
                expectKeyAvailable(c, CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM1                   , OPT      ,   RAW                  );
                expectKeyAvailable(c, CameraCharacteristics.SENSOR_COLOR_TRANSFORM1                         , OPT      ,   RAW                  );
                expectKeyAvailable(c, CameraCharacteristics.SENSOR_FORWARD_MATRIX1                          , OPT      ,   RAW                  );
                expectKeyAvailable(c, CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE                   , OPT      ,   BC, RAW              );
                expectKeyAvailable(c, CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT            , FULL     ,   RAW                  );
                expectKeyAvailable(c, CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE                 , FULL     ,   MANUAL_SENSOR        );
                expectKeyAvailable(c, CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION                  , FULL     ,   MANUAL_SENSOR        );
                expectKeyAvailable(c, CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE                       , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE                    , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE                   , FULL     ,   MANUAL_SENSOR        );
                expectKeyAvailable(c, CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL                         , OPT      ,   RAW                  );
                expectKeyAvailable(c, CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE                    , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY                   , FULL     ,   MANUAL_SENSOR        );
                expectKeyAvailable(c, CameraCharacteristics.SENSOR_ORIENTATION                              , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1                    , OPT      ,   RAW                  );
                expectKeyAvailable(c, CameraCharacteristics.SHADING_AVAILABLE_MODES                         , LIMITED  ,   MANUAL_POSTPROC, RAW );
                expectKeyAvailable(c, CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES     , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES   , OPT      ,   RAW                  );
                expectKeyAvailable(c, CameraCharacteristics.STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES, LIMITED  ,   RAW                  );
                expectKeyAvailable(c, CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT                  , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.SYNC_MAX_LATENCY                                , OPT      ,   BC                   );
                expectKeyAvailable(c, CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES                , FULL     ,   MANUAL_POSTPROC      );
                expectKeyAvailable(c, CameraCharacteristics.TONEMAP_MAX_CURVE_POINTS                        , FULL     ,   MANUAL_POSTPROC      );

                // Future: Use column editors for modifying above, ignore line length to keep 1 key per line

                // TODO: check that no other 'android' keys are listed in #getKeys if they aren't in the above list
            }

            // Only check for these if the second reference illuminant is included
            if (allKeys.contains(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2)) {
                expectKeyAvailable(c, CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2                    , OPT      ,   RAW                  );
                expectKeyAvailable(c, CameraCharacteristics.SENSOR_COLOR_TRANSFORM2                         , OPT      ,   RAW                  );
                expectKeyAvailable(c, CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM2                   , OPT      ,   RAW                  );
                expectKeyAvailable(c, CameraCharacteristics.SENSOR_FORWARD_MATRIX2                          , OPT      ,   RAW                  );
            }

            // Required key if any of RAW format output is supported
            StreamConfigurationMap config =
                    c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assertNotNull(String.format("No stream configuration map found for: ID %s",
                    mIds[counter]), config);
            if (config.isOutputSupportedFor(ImageFormat.RAW_SENSOR) ||
                    config.isOutputSupportedFor(ImageFormat.RAW10)  ||
                    config.isOutputSupportedFor(ImageFormat.RAW12)  ||
                    config.isOutputSupportedFor(ImageFormat.RAW_PRIVATE)) {
                expectKeyAvailable(c,
                        CameraCharacteristics.CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE, OPT, BC);
            }
            counter++;
        }
    }

    /**
     * Test values for static metadata used by the RAW capability.
     */
    public void testStaticRawCharacteristics() {
        int counter = 0;
        for (CameraCharacteristics c : mCharacteristics) {
            int[] actualCapabilities = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            assertNotNull("android.request.availableCapabilities must never be null",
                    actualCapabilities);
            if (!arrayContains(actualCapabilities,
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)) {
                Log.i(TAG, "RAW capability is not supported in camera " + counter++ +
                        ". Skip the test.");
                continue;
            }

            Integer actualHwLevel = c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
            if (actualHwLevel != null && actualHwLevel == FULL) {
                mCollector.expectKeyValueContains(c,
                        CameraCharacteristics.HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES,
                        CameraCharacteristics.HOT_PIXEL_MODE_FAST);
            }
            mCollector.expectKeyValueContains(c,
                    CameraCharacteristics.STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES, false);
            mCollector.expectKeyValueGreaterThan(c, CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL,
                    MIN_ALLOWABLE_WHITELEVEL);

            mCollector.expectKeyValueIsIn(c,
                    CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT,
                    CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB,
                    CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG,
                    CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG,
                    CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR);
            // TODO: SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGB isn't supported yet.

            mCollector.expectKeyValueInRange(c, CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1,
                    CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT,
                    CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1_ISO_STUDIO_TUNGSTEN);
            // Only check the range if the second reference illuminant is avaliable
            if (c.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2) != null) {
                mCollector.expectKeyValueInRange(c, CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2,
                        (byte) CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT,
                        (byte) CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1_ISO_STUDIO_TUNGSTEN);
            }

            Rational[] zeroes = new Rational[9];
            Arrays.fill(zeroes, Rational.ZERO);

            ColorSpaceTransform zeroed = new ColorSpaceTransform(zeroes);
            mCollector.expectNotEquals("Forward Matrix1 should not contain all zeroes.", zeroed,
                    c.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX1));
            mCollector.expectNotEquals("Forward Matrix2 should not contain all zeroes.", zeroed,
                    c.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX2));
            mCollector.expectNotEquals("Calibration Transform1 should not contain all zeroes.",
                    zeroed, c.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM1));
            mCollector.expectNotEquals("Calibration Transform2 should not contain all zeroes.",
                    zeroed, c.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM2));
            mCollector.expectNotEquals("Color Transform1 should not contain all zeroes.",
                    zeroed, c.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1));
            mCollector.expectNotEquals("Color Transform2 should not contain all zeroes.",
                    zeroed, c.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM2));

            BlackLevelPattern blackLevel = mCollector.expectKeyValueNotNull(c,
                    CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN);
            if (blackLevel != null) {
                String blackLevelPatternString = blackLevel.toString();
                if (VERBOSE) {
                    Log.v(TAG, "Black level pattern: " + blackLevelPatternString);
                }
                int[] blackLevelPattern = new int[BlackLevelPattern.COUNT];
                blackLevel.copyTo(blackLevelPattern, /*offset*/0);
                Integer whitelevel = c.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL);
                if (whitelevel != null) {
                    mCollector.expectValuesInRange("BlackLevelPattern", blackLevelPattern, 0,
                            whitelevel);
                } else {
                    mCollector.addMessage(
                            "No WhiteLevel available, cannot check BlackLevelPattern range.");
                }
            }

            // TODO: profileHueSatMap, and profileToneCurve aren't supported yet.
            counter++;
        }
    }

    /**
     * Test values for static metadata used by the BURST capability.
     */
    public void testStaticBurstCharacteristics() throws Exception {
        int counter = 0;
        final float SIZE_ERROR_MARGIN = 0.03f;
        for (CameraCharacteristics c : mCharacteristics) {
            int[] actualCapabilities = CameraTestUtils.getValueNotNull(
                    c, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);

            // Check if the burst capability is defined
            boolean haveBurstCapability = arrayContains(actualCapabilities,
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE);
            boolean haveBC = arrayContains(actualCapabilities,
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE);

            if(haveBurstCapability && !haveBC) {
                fail("Must have BACKWARD_COMPATIBLE capability if BURST_CAPTURE capability is defined");
            }

            if (!haveBC) continue;

            StreamConfigurationMap config =
                    c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assertNotNull(String.format("No stream configuration map found for: ID %s",
                    mIds[counter]), config);
            Rect activeRect = CameraTestUtils.getValueNotNull(
                    c, CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            Size sensorSize = new Size(activeRect.width(), activeRect.height());

            // Ensure that max YUV size matches max JPEG size
            Size maxYuvSize = CameraTestUtils.getMaxSize(
                    config.getOutputSizes(ImageFormat.YUV_420_888));
            Size maxFastYuvSize = maxYuvSize;

            Size[] slowYuvSizes = config.getHighResolutionOutputSizes(ImageFormat.YUV_420_888);
            if (haveBurstCapability && slowYuvSizes != null && slowYuvSizes.length > 0) {
                Size maxSlowYuvSize = CameraTestUtils.getMaxSize(slowYuvSizes);
                maxYuvSize = CameraTestUtils.getMaxSize(new Size[]{maxYuvSize, maxSlowYuvSize});
            }

            Size maxJpegSize = CameraTestUtils.getMaxSize(CameraTestUtils.getSupportedSizeForFormat(
                    ImageFormat.JPEG, mIds[counter], mCameraManager));

            boolean haveMaxYuv = maxYuvSize != null ?
                (maxJpegSize.getWidth() <= maxYuvSize.getWidth() &&
                        maxJpegSize.getHeight() <= maxYuvSize.getHeight()) : false;

            float croppedWidth = (float)sensorSize.getWidth();
            float croppedHeight = (float)sensorSize.getHeight();
            float sensorAspectRatio = (float)sensorSize.getWidth() / (float)sensorSize.getHeight();
            float maxYuvAspectRatio = (float)maxYuvSize.getWidth() / (float)maxYuvSize.getHeight();
            if (sensorAspectRatio < maxYuvAspectRatio) {
                croppedHeight = (float)sensorSize.getWidth() / maxYuvAspectRatio;
            } else if (sensorAspectRatio > maxYuvAspectRatio) {
                croppedWidth = (float)sensorSize.getHeight() * maxYuvAspectRatio;
            }
            Size croppedSensorSize = new Size((int)croppedWidth, (int)croppedHeight);

            boolean maxYuvMatchSensor =
                    (maxYuvSize.getWidth() <= croppedSensorSize.getWidth() * (1.0 + SIZE_ERROR_MARGIN) &&
                     maxYuvSize.getWidth() >= croppedSensorSize.getWidth() * (1.0 - SIZE_ERROR_MARGIN) &&
                     maxYuvSize.getHeight() <= croppedSensorSize.getHeight() * (1.0 + SIZE_ERROR_MARGIN) &&
                     maxYuvSize.getHeight() >= croppedSensorSize.getHeight() * (1.0 - SIZE_ERROR_MARGIN));

            // No need to do null check since framework will generate the key if HAL don't supply
            boolean haveAeLock = CameraTestUtils.getValueNotNull(
                    c, CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE);
            boolean haveAwbLock = CameraTestUtils.getValueNotNull(
                    c, CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE);

            // Ensure that max YUV output is fast enough - needs to be at least 10 fps

            long maxYuvRate =
                config.getOutputMinFrameDuration(ImageFormat.YUV_420_888, maxYuvSize);
            final long MIN_MAXSIZE_DURATION_BOUND_NS = 100000000; // 100 ms, 10 fps
            boolean haveMaxYuvRate = maxYuvRate <= MIN_MAXSIZE_DURATION_BOUND_NS;

            // Ensure that some >=8MP YUV output is fast enough - needs to be at least 20 fps

            long maxFastYuvRate =
                    config.getOutputMinFrameDuration(ImageFormat.YUV_420_888, maxFastYuvSize);
            final long MIN_8MP_DURATION_BOUND_NS = 50000000; // 50 ms, 20 fps
            boolean haveFastYuvRate = maxFastYuvRate <= MIN_8MP_DURATION_BOUND_NS;

            final int SIZE_8MP_BOUND = 8000000;
            boolean havefast8MPYuv = (maxFastYuvSize.getWidth() * maxFastYuvSize.getHeight()) >
                    SIZE_8MP_BOUND;

            // Ensure that there's an FPS range that's fast enough to capture at above
            // minFrameDuration, for full-auto bursts at the fast resolutions
            Range[] fpsRanges = CameraTestUtils.getValueNotNull(
                    c, CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            float minYuvFps = 1.f / maxFastYuvRate;

            boolean haveFastAeTargetFps = false;
            for (Range<Integer> r : fpsRanges) {
                if (r.getLower() >= minYuvFps) {
                    haveFastAeTargetFps = true;
                    break;
                }
            }

            // Ensure that maximum sync latency is small enough for fast setting changes, even if
            // it's not quite per-frame

            Integer maxSyncLatencyValue = c.get(CameraCharacteristics.SYNC_MAX_LATENCY);
            assertNotNull(String.format("No sync latency declared for ID %s", mIds[counter]),
                    maxSyncLatencyValue);

            int maxSyncLatency = maxSyncLatencyValue;
            final long MAX_LATENCY_BOUND = 4;
            boolean haveFastSyncLatency =
                (maxSyncLatency <= MAX_LATENCY_BOUND) && (maxSyncLatency >= 0);

            if (haveBurstCapability) {
                assertTrue("Must have slow YUV size array when BURST_CAPTURE capability is defined!",
                        slowYuvSizes != null);
                assertTrue(
                        String.format("BURST-capable camera device %s does not have maximum YUV " +
                                "size that is at least max JPEG size",
                                mIds[counter]),
                        haveMaxYuv);
                assertTrue(
                        String.format("BURST-capable camera device %s max-resolution " +
                                "YUV frame rate is too slow" +
                                "(%d ns min frame duration reported, less than %d ns expected)",
                                mIds[counter], maxYuvRate, MIN_MAXSIZE_DURATION_BOUND_NS),
                        haveMaxYuvRate);
                assertTrue(
                        String.format("BURST-capable camera device %s >= 8MP YUV output " +
                                "frame rate is too slow" +
                                "(%d ns min frame duration reported, less than %d ns expected)",
                                mIds[counter], maxYuvRate, MIN_8MP_DURATION_BOUND_NS),
                        haveFastYuvRate);
                assertTrue(
                        String.format("BURST-capable camera device %s does not list an AE target " +
                                " FPS range with min FPS >= %f, for full-AUTO bursts",
                                mIds[counter], minYuvFps),
                        haveFastAeTargetFps);
                assertTrue(
                        String.format("BURST-capable camera device %s YUV sync latency is too long" +
                                "(%d frames reported, [0, %d] frames expected)",
                                mIds[counter], maxSyncLatency, MAX_LATENCY_BOUND),
                        haveFastSyncLatency);
                assertTrue(
                        String.format("BURST-capable camera device %s max YUV size %s should be" +
                                "close to active array size %s or cropped active array size %s",
                                mIds[counter], maxYuvSize.toString(), sensorSize.toString(),
                                croppedSensorSize.toString()),
                        maxYuvMatchSensor);
                assertTrue(
                        String.format("BURST-capable camera device %s does not support AE lock",
                                mIds[counter]),
                        haveAeLock);
                assertTrue(
                        String.format("BURST-capable camera device %s does not support AWB lock",
                                mIds[counter]),
                        haveAwbLock);
            } else {
                assertTrue("Must have null slow YUV size array when no BURST_CAPTURE capability!",
                        slowYuvSizes == null);
                assertTrue(
                        String.format("Camera device %s has all the requirements for BURST" +
                                " capability but does not report it!", mIds[counter]),
                        !(haveMaxYuv && haveMaxYuvRate && haveFastYuvRate && haveFastAeTargetFps &&
                                haveFastSyncLatency && maxYuvMatchSensor &&
                                haveAeLock && haveAwbLock));
            }

            counter++;
        }
    }

    /**
     * Check reprocessing capabilities.
     */
    public void testReprocessingCharacteristics() {
        int counter = 0;

        for (CameraCharacteristics c : mCharacteristics) {
            Log.i(TAG, "testReprocessingCharacteristics: Testing camera ID " + mIds[counter]);

            int[] capabilities = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            assertNotNull("android.request.availableCapabilities must never be null",
                    capabilities);
            boolean supportYUV = arrayContains(capabilities,
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING);
            boolean supportOpaque = arrayContains(capabilities,
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING);
            StreamConfigurationMap configs =
                    c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Integer maxNumInputStreams =
                    c.get(CameraCharacteristics.REQUEST_MAX_NUM_INPUT_STREAMS);
            int[] availableEdgeModes = c.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES);
            int[] availableNoiseReductionModes = c.get(
                    CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES);

            int[] inputFormats = configs.getInputFormats();

            boolean supportZslEdgeMode = false;
            boolean supportZslNoiseReductionMode = false;
            boolean supportHiQNoiseReductionMode = false;
            boolean supportHiQEdgeMode = false;

            if (availableEdgeModes != null) {
                supportZslEdgeMode = Arrays.asList(CameraTestUtils.toObject(availableEdgeModes)).
                        contains(CaptureRequest.EDGE_MODE_ZERO_SHUTTER_LAG);
                supportHiQEdgeMode = Arrays.asList(CameraTestUtils.toObject(availableEdgeModes)).
                        contains(CaptureRequest.EDGE_MODE_HIGH_QUALITY);
            }

            if (availableNoiseReductionModes != null) {
                supportZslNoiseReductionMode = Arrays.asList(
                        CameraTestUtils.toObject(availableNoiseReductionModes)).contains(
                        CaptureRequest.NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG);
                supportHiQNoiseReductionMode = Arrays.asList(
                        CameraTestUtils.toObject(availableNoiseReductionModes)).contains(
                        CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY);
            }

            if (supportYUV || supportOpaque) {
                mCollector.expectTrue("Support reprocessing but max number of input stream is " +
                        maxNumInputStreams, maxNumInputStreams != null && maxNumInputStreams > 0);
                mCollector.expectTrue("Support reprocessing but EDGE_MODE_ZERO_SHUTTER_LAG is " +
                        "not supported", supportZslEdgeMode);
                mCollector.expectTrue("Support reprocessing but " +
                        "NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG is not supported",
                        supportZslNoiseReductionMode);

                // For reprocessing, if we only require OFF and ZSL mode, it will be just like jpeg
                // encoding. We implicitly require FAST to make reprocessing meaningful, which means
                // that we also require HIGH_QUALITY.
                mCollector.expectTrue("Support reprocessing but EDGE_MODE_HIGH_QUALITY is " +
                        "not supported", supportHiQEdgeMode);
                mCollector.expectTrue("Support reprocessing but " +
                        "NOISE_REDUCTION_MODE_HIGH_QUALITY is not supported",
                        supportHiQNoiseReductionMode);

                // Verify mandatory input formats are supported
                mCollector.expectTrue("YUV_420_888 input must be supported for YUV reprocessing",
                        !supportYUV || arrayContains(inputFormats, ImageFormat.YUV_420_888));
                mCollector.expectTrue("PRIVATE input must be supported for OPAQUE reprocessing",
                        !supportOpaque || arrayContains(inputFormats, ImageFormat.PRIVATE));

                // max capture stall must be reported if one of the reprocessing is supported.
                final int MAX_ALLOWED_STALL_FRAMES = 4;
                Integer maxCaptureStall = c.get(CameraCharacteristics.REPROCESS_MAX_CAPTURE_STALL);
                mCollector.expectTrue("max capture stall must be non-null and no larger than "
                        + MAX_ALLOWED_STALL_FRAMES,
                        maxCaptureStall != null && maxCaptureStall <= MAX_ALLOWED_STALL_FRAMES);

                for (int input : inputFormats) {
                    // Verify mandatory output formats are supported
                    int[] outputFormats = configs.getValidOutputFormatsForInput(input);
                    mCollector.expectTrue("YUV_420_888 output must be supported for reprocessing",
                            arrayContains(outputFormats, ImageFormat.YUV_420_888));
                    mCollector.expectTrue("JPEG output must be supported for reprocessing",
                            arrayContains(outputFormats, ImageFormat.JPEG));

                    // Verify camera can output the reprocess input formats and sizes.
                    Size[] inputSizes = configs.getInputSizes(input);
                    Size[] outputSizes = configs.getOutputSizes(input);
                    Size[] highResOutputSizes = configs.getHighResolutionOutputSizes(input);
                    mCollector.expectTrue("no input size supported for format " + input,
                            inputSizes.length > 0);
                    mCollector.expectTrue("no output size supported for format " + input,
                            outputSizes.length > 0);

                    for (Size inputSize : inputSizes) {
                        mCollector.expectTrue("Camera must be able to output the supported " +
                                "reprocessing input size",
                                arrayContains(outputSizes, inputSize) ||
                                arrayContains(highResOutputSizes, inputSize));
                    }
                }
            } else {
                mCollector.expectTrue("Doesn't support reprocessing but report input format: " +
                        Arrays.toString(inputFormats), inputFormats.length == 0);
                mCollector.expectTrue("Doesn't support reprocessing but max number of input " +
                        "stream is " + maxNumInputStreams,
                        maxNumInputStreams == null || maxNumInputStreams == 0);
                mCollector.expectTrue("Doesn't support reprocessing but " +
                        "EDGE_MODE_ZERO_SHUTTER_LAG is supported", !supportZslEdgeMode);
                mCollector.expectTrue("Doesn't support reprocessing but " +
                        "NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG is supported",
                        !supportZslNoiseReductionMode);
            }
            counter++;
        }
    }

    /**
     * Check depth output capability
     */
    public void testDepthOutputCharacteristics() {
        int counter = 0;

        for (CameraCharacteristics c : mCharacteristics) {
            Log.i(TAG, "testDepthOutputCharacteristics: Testing camera ID " + mIds[counter]);

            int[] capabilities = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            assertNotNull("android.request.availableCapabilities must never be null",
                    capabilities);
            boolean supportDepth = arrayContains(capabilities,
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT);
            StreamConfigurationMap configs =
                    c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            int[] outputFormats = configs.getOutputFormats();
            boolean hasDepth16 = arrayContains(outputFormats, ImageFormat.DEPTH16);

            Boolean depthIsExclusive = c.get(CameraCharacteristics.DEPTH_DEPTH_IS_EXCLUSIVE);

            float[] poseRotation = c.get(CameraCharacteristics.LENS_POSE_ROTATION);
            float[] poseTranslation = c.get(CameraCharacteristics.LENS_POSE_TRANSLATION);
            float[] cameraIntrinsics = c.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION);
            float[] radialDistortion = c.get(CameraCharacteristics.LENS_RADIAL_DISTORTION);

            if (supportDepth) {
                mCollector.expectTrue("Supports DEPTH_OUTPUT but does not support DEPTH16",
                        hasDepth16);
                if (hasDepth16) {
                    Size[] depthSizes = configs.getOutputSizes(ImageFormat.DEPTH16);
                    mCollector.expectTrue("Supports DEPTH_OUTPUT but no sizes for DEPTH16 supported!",
                            depthSizes != null && depthSizes.length > 0);
                    if (depthSizes != null) {
                        for (Size depthSize : depthSizes) {
                            mCollector.expectTrue("All depth16 sizes must be positive",
                                    depthSize.getWidth() > 0 && depthSize.getHeight() > 0);
                            long minFrameDuration = configs.getOutputMinFrameDuration(
                                    ImageFormat.DEPTH16, depthSize);
                            mCollector.expectTrue("Non-negative min frame duration for depth size "
                                    + depthSize + " expected, got " + minFrameDuration,
                                    minFrameDuration >= 0);
                            long stallDuration = configs.getOutputStallDuration(
                                    ImageFormat.DEPTH16, depthSize);
                            mCollector.expectTrue("Non-negative stall duration for depth size "
                                    + depthSize + " expected, got " + stallDuration,
                                    stallDuration >= 0);
                        }
                    }
                }
                if (arrayContains(outputFormats, ImageFormat.DEPTH_POINT_CLOUD)) {
                    Size[] depthCloudSizes = configs.getOutputSizes(ImageFormat.DEPTH_POINT_CLOUD);
                    mCollector.expectTrue("Supports DEPTH_POINT_CLOUD " +
                            "but no sizes for DEPTH_POINT_CLOUD supported!",
                            depthCloudSizes != null && depthCloudSizes.length > 0);
                    if (depthCloudSizes != null) {
                        for (Size depthCloudSize : depthCloudSizes) {
                            mCollector.expectTrue("All depth point cloud sizes must be nonzero",
                                    depthCloudSize.getWidth() > 0);
                            mCollector.expectTrue("All depth point cloud sizes must be N x 1",
                                    depthCloudSize.getHeight() == 1);
                            long minFrameDuration = configs.getOutputMinFrameDuration(
                                    ImageFormat.DEPTH_POINT_CLOUD, depthCloudSize);
                            mCollector.expectTrue("Non-negative min frame duration for depth size "
                                    + depthCloudSize + " expected, got " + minFrameDuration,
                                    minFrameDuration >= 0);
                            long stallDuration = configs.getOutputStallDuration(
                                    ImageFormat.DEPTH_POINT_CLOUD, depthCloudSize);
                            mCollector.expectTrue("Non-negative stall duration for depth size "
                                    + depthCloudSize + " expected, got " + stallDuration,
                                    stallDuration >= 0);
                        }
                    }
                }

                mCollector.expectTrue("Supports DEPTH_OUTPUT but DEPTH_IS_EXCLUSIVE is not defined",
                        depthIsExclusive != null);

                mCollector.expectTrue(
                        "Supports DEPTH_OUTPUT but LENS_POSE_ROTATION not right size",
                        poseRotation != null && poseRotation.length == 4);
                mCollector.expectTrue(
                        "Supports DEPTH_OUTPUT but LENS_POSE_TRANSLATION not right size",
                        poseTranslation != null && poseTranslation.length == 3);
                mCollector.expectTrue(
                        "Supports DEPTH_OUTPUT but LENS_INTRINSIC_CALIBRATION not right size",
                        cameraIntrinsics != null && cameraIntrinsics.length == 5);
                mCollector.expectTrue(
                        "Supports DEPTH_OUTPUT but LENS_RADIAL_DISTORTION not right size",
                        radialDistortion != null && radialDistortion.length == 6);

                if (poseRotation != null && poseRotation.length == 4) {
                    float normSq =
                        poseRotation[0] * poseRotation[0] +
                        poseRotation[1] * poseRotation[1] +
                        poseRotation[2] * poseRotation[2] +
                        poseRotation[3] * poseRotation[3];
                    mCollector.expectTrue(
                            "LENS_POSE_ROTATION quarternion must be unit-length",
                            0.9999f < normSq && normSq < 1.0001f);

                    // TODO: Cross-validate orientation/facing and poseRotation
                    Integer orientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION);
                    Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                }

                if (poseTranslation != null && poseTranslation.length == 3) {
                    float normSq =
                        poseTranslation[0] * poseTranslation[0] +
                        poseTranslation[1] * poseTranslation[1] +
                        poseTranslation[2] * poseTranslation[2];
                    mCollector.expectTrue("Pose translation is larger than 1 m",
                            normSq < 1.f);
                }

                Rect precorrectionArray =
                    c.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE);
                mCollector.expectTrue("Supports DEPTH_OUTPUT but does not have " +
                        "precorrection active array defined", precorrectionArray != null);

                if (cameraIntrinsics != null && precorrectionArray != null) {
                    float fx = cameraIntrinsics[0];
                    float fy = cameraIntrinsics[1];
                    float cx = cameraIntrinsics[2];
                    float cy = cameraIntrinsics[3];
                    float s = cameraIntrinsics[4];
                    mCollector.expectTrue("Optical center expected to be within precorrection array",
                            0 <= cx && cx < precorrectionArray.width() &&
                            0 <= cy && cy < precorrectionArray.height());

                    // TODO: Verify focal lengths and skew are reasonable
                }

                if (radialDistortion != null) {
                    // TODO: Verify radial distortion
                }

            } else {
                boolean hasFields =
                    hasDepth16 && (poseTranslation != null) &&
                    (poseRotation != null) && (cameraIntrinsics != null) &&
                    (radialDistortion != null) && (depthIsExclusive != null);

                mCollector.expectTrue(
                        "All necessary depth fields defined, but DEPTH_OUTPUT capability is not listed",
                        !hasFields);
            }
            counter++;
        }
    }

    /**
     * Cross-check StreamConfigurationMap output
     */
    public void testStreamConfigurationMap() throws Exception {
        int counter = 0;
        for (CameraCharacteristics c : mCharacteristics) {
            Log.i(TAG, "testStreamConfigurationMap: Testing camera ID " + mIds[counter]);
            StreamConfigurationMap config =
                    c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assertNotNull(String.format("No stream configuration map found for: ID %s",
                            mIds[counter]), config);

            int[] actualCapabilities = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            assertNotNull("android.request.availableCapabilities must never be null",
                    actualCapabilities);

            if (arrayContains(actualCapabilities, BC)) {
                assertTrue("ImageReader must be supported",
                    config.isOutputSupportedFor(android.media.ImageReader.class));
                assertTrue("MediaRecorder must be supported",
                    config.isOutputSupportedFor(android.media.MediaRecorder.class));
                assertTrue("MediaCodec must be supported",
                    config.isOutputSupportedFor(android.media.MediaCodec.class));
                assertTrue("Allocation must be supported",
                    config.isOutputSupportedFor(android.renderscript.Allocation.class));
                assertTrue("SurfaceHolder must be supported",
                    config.isOutputSupportedFor(android.view.SurfaceHolder.class));
                assertTrue("SurfaceTexture must be supported",
                    config.isOutputSupportedFor(android.graphics.SurfaceTexture.class));

                assertTrue("YUV_420_888 must be supported",
                    config.isOutputSupportedFor(ImageFormat.YUV_420_888));
                assertTrue("JPEG must be supported",
                    config.isOutputSupportedFor(ImageFormat.JPEG));
            } else {
                assertTrue("YUV_420_88 may not be supported if BACKWARD_COMPATIBLE capability is not listed",
                    !config.isOutputSupportedFor(ImageFormat.YUV_420_888));
                assertTrue("JPEG may not be supported if BACKWARD_COMPATIBLE capability is not listed",
                    !config.isOutputSupportedFor(ImageFormat.JPEG));
            }

            // Check RAW

            if (arrayContains(actualCapabilities,
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)) {
                assertTrue("RAW_SENSOR must be supported if RAW capability is advertised",
                    config.isOutputSupportedFor(ImageFormat.RAW_SENSOR));
            }

            // Cross check public formats and sizes

            int[] supportedFormats = config.getOutputFormats();
            for (int format : supportedFormats) {
                assertTrue("Format " + format + " fails cross check",
                        config.isOutputSupportedFor(format));
                List<Size> supportedSizes = CameraTestUtils.getAscendingOrderSizes(
                        Arrays.asList(config.getOutputSizes(format)), /*ascending*/true);
                if (arrayContains(actualCapabilities,
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE)) {
                    supportedSizes.addAll(
                        Arrays.asList(config.getHighResolutionOutputSizes(format)));
                    supportedSizes = CameraTestUtils.getAscendingOrderSizes(
                        supportedSizes, /*ascending*/true);
                }
                assertTrue("Supported format " + format + " has no sizes listed",
                        supportedSizes.size() > 0);
                for (int i = 0; i < supportedSizes.size(); i++) {
                    Size size = supportedSizes.get(i);
                    if (VERBOSE) {
                        Log.v(TAG,
                                String.format("Testing camera %s, format %d, size %s",
                                        mIds[counter], format, size.toString()));
                    }

                    long stallDuration = config.getOutputStallDuration(format, size);
                    switch(format) {
                        case ImageFormat.YUV_420_888:
                            assertTrue("YUV_420_888 may not have a non-zero stall duration",
                                    stallDuration == 0);
                            break;
                        case ImageFormat.JPEG:
                        case ImageFormat.RAW_SENSOR:
                            final float TOLERANCE_FACTOR = 2.0f;
                            long prevDuration = 0;
                            if (i > 0) {
                                prevDuration = config.getOutputStallDuration(
                                        format, supportedSizes.get(i - 1));
                            }
                            long nextDuration = Long.MAX_VALUE;
                            if (i < (supportedSizes.size() - 1)) {
                                nextDuration = config.getOutputStallDuration(
                                        format, supportedSizes.get(i + 1));
                            }
                            long curStallDuration = config.getOutputStallDuration(format, size);
                            // Stall duration should be in a reasonable range: larger size should
                            // normally have larger stall duration.
                            mCollector.expectInRange("Stall duration (format " + format +
                                    " and size " + size + ") is not in the right range",
                                    curStallDuration,
                                    (long) (prevDuration / TOLERANCE_FACTOR),
                                    (long) (nextDuration * TOLERANCE_FACTOR));
                            break;
                        default:
                            assertTrue("Negative stall duration for format " + format,
                                    stallDuration >= 0);
                            break;
                    }
                    long minDuration = config.getOutputMinFrameDuration(format, size);
                    if (arrayContains(actualCapabilities,
                            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)) {
                        assertTrue("MANUAL_SENSOR capability, need positive min frame duration for"
                                + "format " + format + " for size " + size + " minDuration " +
                                minDuration,
                                minDuration > 0);
                    } else {
                        assertTrue("Need non-negative min frame duration for format " + format,
                                minDuration >= 0);
                    }

                    // todo: test opaque image reader when it's supported.
                    if (format != ImageFormat.PRIVATE) {
                        ImageReader testReader = ImageReader.newInstance(
                            size.getWidth(),
                            size.getHeight(),
                            format,
                            1);
                        Surface testSurface = testReader.getSurface();

                        assertTrue(
                            String.format("isOutputSupportedFor fails for config %s, format %d",
                                    size.toString(), format),
                            config.isOutputSupportedFor(testSurface));

                        testReader.close();
                    }
                } // sizes

                // Try an invalid size in this format, should round
                Size invalidSize = findInvalidSize(supportedSizes);
                int MAX_ROUNDING_WIDTH = 1920;
                // todo: test opaque image reader when it's supported.
                if (format != ImageFormat.PRIVATE &&
                        invalidSize.getWidth() <= MAX_ROUNDING_WIDTH) {
                    ImageReader testReader = ImageReader.newInstance(
                                                                     invalidSize.getWidth(),
                                                                     invalidSize.getHeight(),
                                                                     format,
                                                                     1);
                    Surface testSurface = testReader.getSurface();

                    assertTrue(
                               String.format("isOutputSupportedFor fails for config %s, %d",
                                       invalidSize.toString(), format),
                               config.isOutputSupportedFor(testSurface));

                    testReader.close();
                }
            } // formats

            // Cross-check opaque format and sizes
            if (arrayContains(actualCapabilities, BC)) {
                SurfaceTexture st = new SurfaceTexture(1);
                Surface surf = new Surface(st);

                Size[] opaqueSizes = CameraTestUtils.getSupportedSizeForClass(SurfaceTexture.class,
                        mIds[counter], mCameraManager);
                assertTrue("Opaque format has no sizes listed",
                        opaqueSizes.length > 0);
                for (Size size : opaqueSizes) {
                    long stallDuration = config.getOutputStallDuration(SurfaceTexture.class, size);
                    assertTrue("Opaque output may not have a non-zero stall duration",
                            stallDuration == 0);

                    long minDuration = config.getOutputMinFrameDuration(SurfaceTexture.class, size);
                    if (arrayContains(actualCapabilities,
                                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)) {
                        assertTrue("MANUAL_SENSOR capability, need positive min frame duration for"
                                + "opaque format",
                                minDuration > 0);
                    } else {
                        assertTrue("Need non-negative min frame duration for opaque format ",
                                minDuration >= 0);
                    }
                    st.setDefaultBufferSize(size.getWidth(), size.getHeight());

                    assertTrue(
                            String.format("isOutputSupportedFor fails for SurfaceTexture config %s",
                                    size.toString()),
                            config.isOutputSupportedFor(surf));

                } // opaque sizes

                // Try invalid opaque size, should get rounded
                Size invalidSize = findInvalidSize(opaqueSizes);
                st.setDefaultBufferSize(invalidSize.getWidth(), invalidSize.getHeight());
                assertTrue(
                        String.format("isOutputSupportedFor fails for SurfaceTexture config %s",
                                invalidSize.toString()),
                        config.isOutputSupportedFor(surf));

            }
            counter++;
        } // mCharacteristics
    }

    /**
     * Test high speed capability and cross-check the high speed sizes and fps ranges from
     * the StreamConfigurationMap.
     */
    public void testConstrainedHighSpeedCapability() throws Exception {
        int counter = 0;
        for (CameraCharacteristics c : mCharacteristics) {
            int[] capabilities = CameraTestUtils.getValueNotNull(
                    c, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            boolean supportHighSpeed = arrayContains(capabilities, CONSTRAINED_HIGH_SPEED);
            if (supportHighSpeed) {
                StreamConfigurationMap config =
                        CameraTestUtils.getValueNotNull(
                                c, CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                List<Size> highSpeedSizes = Arrays.asList(config.getHighSpeedVideoSizes());
                assertTrue("High speed sizes shouldn't be empty", highSpeedSizes.size() > 0);
                Size[] allSizes = CameraTestUtils.getSupportedSizeForFormat(ImageFormat.PRIVATE,
                        mIds[counter], mCameraManager);
                assertTrue("Normal size for PRIVATE format shouldn't be null or empty",
                        allSizes != null && allSizes.length > 0);
                for (Size size: highSpeedSizes) {
                    // The sizes must be a subset of the normal sizes
                    assertTrue("High speed size " + size +
                            " must be part of normal sizes " + Arrays.toString(allSizes),
                            Arrays.asList(allSizes).contains(size));

                    // Sanitize the high speed FPS ranges for each size
                    List<Range<Integer>> ranges =
                            Arrays.asList(config.getHighSpeedVideoFpsRangesFor(size));
                    for (Range<Integer> range : ranges) {
                        assertTrue("The range " + range + " doesn't satisfy the"
                                + " min/max boundary requirements.",
                                range.getLower() >= HIGH_SPEED_FPS_LOWER_MIN &&
                                range.getUpper() >= HIGH_SPEED_FPS_UPPER_MIN);
                        assertTrue("The range " + range + " should be multiple of 30fps",
                                range.getLower() % 30 == 0 && range.getUpper() % 30 == 0);
                        // If the range is fixed high speed range, it should contain the
                        // [30, fps_max] in the high speed range list; if it's variable FPS range,
                        // the corresponding fixed FPS Range must be included in the range list.
                        if (range.getLower() == range.getUpper()) {
                            Range<Integer> variableRange = new Range<Integer>(30, range.getUpper());
                            assertTrue("The variable FPS range " + variableRange +
                                    " shoould be included in the high speed ranges for size " +
                                    size, ranges.contains(variableRange));
                        } else {
                            Range<Integer> fixedRange =
                                    new Range<Integer>(range.getUpper(), range.getUpper());
                            assertTrue("The fixed FPS range " + fixedRange +
                                    " shoould be included in the high speed ranges for size " +
                                    size, ranges.contains(fixedRange));
                        }
                    }
                }
                // If the device advertise some high speed profiles, the sizes and FPS ranges
                // should be advertise by the camera.
                for (int quality = CamcorderProfile.QUALITY_HIGH_SPEED_480P;
                        quality <= CamcorderProfile.QUALITY_HIGH_SPEED_2160P; quality++) {
                    int cameraId = Integer.valueOf(mIds[counter]);
                    if (CamcorderProfile.hasProfile(cameraId, quality)) {
                        CamcorderProfile profile = CamcorderProfile.get(cameraId, quality);
                        Size camcorderProfileSize =
                                new Size(profile.videoFrameWidth, profile.videoFrameHeight);
                        assertTrue("CamcorderPrfile size " + camcorderProfileSize +
                                " must be included in the high speed sizes " +
                                Arrays.toString(highSpeedSizes.toArray()),
                                highSpeedSizes.contains(camcorderProfileSize));
                        Range<Integer> camcorderFpsRange =
                                new Range<Integer>(profile.videoFrameRate, profile.videoFrameRate);
                        List<Range<Integer>> allRanges =
                                Arrays.asList(config.getHighSpeedVideoFpsRangesFor(
                                        camcorderProfileSize));
                        assertTrue("Camcorder fps range " + camcorderFpsRange +
                                " should be included by high speed fps ranges " +
                                Arrays.toString(allRanges.toArray()),
                                allRanges.contains(camcorderFpsRange));
                    }
                }
            }
            counter++;
        }
    }

    /**
     * Sanity check of optical black regions.
     */
    public void testOpticalBlackRegions() {
        int counter = 0;
        for (CameraCharacteristics c : mCharacteristics) {
            List<CaptureResult.Key<?>> resultKeys = c.getAvailableCaptureResultKeys();
            boolean hasDynamicBlackLevel =
                    resultKeys.contains(CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL);
            boolean hasDynamicWhiteLevel =
                    resultKeys.contains(CaptureResult.SENSOR_DYNAMIC_WHITE_LEVEL);
            boolean hasFixedBlackLevel =
                    c.getKeys().contains(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN);
            boolean hasFixedWhiteLevel =
                    c.getKeys().contains(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL);
            // The black and white levels should be either all supported or none of them is
            // supported.
            mCollector.expectTrue("Dynamic black and white level should be all or none of them"
                    + " be supported", hasDynamicWhiteLevel == hasDynamicBlackLevel);
            mCollector.expectTrue("Fixed black and white level should be all or none of them"
                    + " be supported", hasFixedBlackLevel == hasFixedWhiteLevel);
            mCollector.expectTrue("Fixed black level should be supported if dynamic black"
                    + " level is supported", !hasDynamicBlackLevel || hasFixedBlackLevel);

            if (c.getKeys().contains(CameraCharacteristics.SENSOR_OPTICAL_BLACK_REGIONS)) {
                // Regions shouldn't be null or empty.
                Rect[] regions = CameraTestUtils.getValueNotNull(c,
                        CameraCharacteristics.SENSOR_OPTICAL_BLACK_REGIONS);
                CameraTestUtils.assertArrayNotEmpty(regions, "Optical back region arrays must not"
                        + " be empty");

                // Dynamic black level should be supported if the optical black region is
                // advertised.
                mCollector.expectTrue("Dynamic black and white level keys should be advertised in "
                        + "available capture result key list", hasDynamicWhiteLevel);

                // Range check.
                for (Rect region : regions) {
                    mCollector.expectTrue("Camera " + mIds[counter] + ": optical black region" +
                            " shouldn't be empty!", !region.isEmpty());
                    mCollector.expectGreaterOrEqual("Optical black region left", 0/*expected*/,
                            region.left/*actual*/);
                    mCollector.expectGreaterOrEqual("Optical black region top", 0/*expected*/,
                            region.top/*actual*/);
                    mCollector.expectTrue("Optical black region left/right/width/height must be"
                            + " even number, otherwise, the bayer CFA pattern in this region will"
                            + " be messed up",
                            region.left % 2 == 0 && region.top % 2 == 0 &&
                            region.width() % 2 == 0 && region.height() % 2 == 0);
                    mCollector.expectGreaterOrEqual("Optical black region top", 0/*expected*/,
                            region.top/*actual*/);
                    Size size = CameraTestUtils.getValueNotNull(c,
                            CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
                    mCollector.expectLessOrEqual("Optical black region width",
                            size.getWidth()/*expected*/, region.width());
                    mCollector.expectLessOrEqual("Optical black region height",
                            size.getHeight()/*expected*/, region.height());
                    Rect activeArray = CameraTestUtils.getValueNotNull(c,
                            CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE);
                    mCollector.expectTrue("Optical black region" + region + " should be outside of"
                            + " active array " + activeArray,
                            !region.intersect(activeArray));
                    // Region need to be disjoint:
                    for (Rect region2 : regions) {
                        mCollector.expectTrue("Optical black region" + region + " should have no "
                                + "overlap with " + region2,
                                region == region2 || !region.intersect(region2));
                    }
                }
            } else {
                Log.i(TAG, "Camera " + mIds[counter] + " doesn't support optical black regions,"
                        + " skip the region test");
            }
            counter++;
        }
    }
    /**
     * Create an invalid size that's close to one of the good sizes in the list, but not one of them
     */
    private Size findInvalidSize(Size[] goodSizes) {
        return findInvalidSize(Arrays.asList(goodSizes));
    }

    /**
     * Create an invalid size that's close to one of the good sizes in the list, but not one of them
     */
    private Size findInvalidSize(List<Size> goodSizes) {
        Size invalidSize = new Size(goodSizes.get(0).getWidth() + 1, goodSizes.get(0).getHeight());
        while(goodSizes.contains(invalidSize)) {
            invalidSize = new Size(invalidSize.getWidth() + 1, invalidSize.getHeight());
        }
        return invalidSize;
    }

    /**
     * Check key is present in characteristics if the hardware level is at least {@code hwLevel};
     * check that the key is present if the actual capabilities are one of {@code capabilities}.
     *
     * @return value of the {@code key} from {@code c}
     */
    private <T> T expectKeyAvailable(CameraCharacteristics c, CameraCharacteristics.Key<T> key,
            int hwLevel, int... capabilities) {

        Integer actualHwLevel = c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        assertNotNull("android.info.supportedHardwareLevel must never be null", actualHwLevel);

        int[] actualCapabilities = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        assertNotNull("android.request.availableCapabilities must never be null",
                actualCapabilities);

        List<Key<?>> allKeys = c.getKeys();

        T value = c.get(key);

        // For LIMITED-level targeted keys, rely on capability check, not level
        if ((compareHardwareLevel(actualHwLevel, hwLevel) >= 0) && (hwLevel != LIMITED)) {
            mCollector.expectTrue(
                    String.format("Key (%s) must be in characteristics for this hardware level " +
                            "(required minimal HW level %s, actual HW level %s)",
                            key.getName(), toStringHardwareLevel(hwLevel),
                            toStringHardwareLevel(actualHwLevel)),
                    value != null);
            mCollector.expectTrue(
                    String.format("Key (%s) must be in characteristics list of keys for this " +
                            "hardware level (required minimal HW level %s, actual HW level %s)",
                            key.getName(), toStringHardwareLevel(hwLevel),
                            toStringHardwareLevel(actualHwLevel)),
                    allKeys.contains(key));
        } else if (arrayContainsAnyOf(actualCapabilities, capabilities)) {
            if (!(hwLevel == LIMITED && compareHardwareLevel(actualHwLevel, hwLevel) < 0)) {
                // Don't enforce LIMITED-starting keys on LEGACY level, even if cap is defined
                mCollector.expectTrue(
                    String.format("Key (%s) must be in characteristics for these capabilities " +
                            "(required capabilities %s, actual capabilities %s)",
                            key.getName(), Arrays.toString(capabilities),
                            Arrays.toString(actualCapabilities)),
                    value != null);
                mCollector.expectTrue(
                    String.format("Key (%s) must be in characteristics list of keys for " +
                            "these capabilities (required capabilities %s, actual capabilities %s)",
                            key.getName(), Arrays.toString(capabilities),
                            Arrays.toString(actualCapabilities)),
                    allKeys.contains(key));
            }
        } else {
            if (actualHwLevel == LEGACY && hwLevel != OPT) {
                if (value != null || allKeys.contains(key)) {
                    Log.w(TAG, String.format(
                            "Key (%s) is not required for LEGACY devices but still appears",
                            key.getName()));
                }
            }
            // OK: Key may or may not be present.
        }
        return value;
    }

    private static boolean arrayContains(int[] arr, int needle) {
        if (arr == null) {
            return false;
        }

        for (int elem : arr) {
            if (elem == needle) {
                return true;
            }
        }

        return false;
    }

    private static <T> boolean arrayContains(T[] arr, T needle) {
        if (arr == null) {
            return false;
        }

        for (T elem : arr) {
            if (elem.equals(needle)) {
                return true;
            }
        }

        return false;
    }

    private static boolean arrayContainsAnyOf(int[] arr, int[] needles) {
        for (int needle : needles) {
            if (arrayContains(arr, needle)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The key name has a prefix of either "android." or a valid TLD; other prefixes are not valid.
     */
    private static void assertKeyPrefixValid(String keyName) {
        assertStartsWithAndroidOrTLD(
                "All metadata keys must start with 'android.' (built-in keys) " +
                "or valid TLD (vendor-extended keys)", keyName);
    }

    private static void assertTrueForKey(String msg, CameraCharacteristics.Key<?> key,
            boolean actual) {
        assertTrue(msg + " (key = '" + key.getName() + "')", actual);
    }

    private static <T> void assertOneOf(String msg, T[] expected, T actual) {
        for (int i = 0; i < expected.length; ++i) {
            if (Objects.equals(expected[i], actual)) {
                return;
            }
        }

        fail(String.format("%s: (expected one of %s, actual %s)",
                msg, Arrays.toString(expected), actual));
    }

    private static <T> void assertStartsWithAndroidOrTLD(String msg, String keyName) {
        String delimiter = ".";
        if (keyName.startsWith(PREFIX_ANDROID + delimiter)) {
            return;
        }
        Pattern tldPattern = Pattern.compile(Patterns.TOP_LEVEL_DOMAIN_STR);
        Matcher match = tldPattern.matcher(keyName);
        if (match.find(0) && (0 == match.start()) && (!match.hitEnd())) {
            if (keyName.regionMatches(match.end(), delimiter, 0, delimiter.length())) {
                return;
            }
        }

        fail(String.format("%s: (expected to start with %s or valid TLD, but value was %s)",
                msg, PREFIX_ANDROID + delimiter, keyName));
    }

    /** Return a positive int if left > right, 0 if left==right, negative int if left < right */
    private static int compareHardwareLevel(int left, int right) {
        return remapHardwareLevel(left) - remapHardwareLevel(right);
    }

    /** Remap HW levels worst<->best, 0 = LEGACY, 1 = LIMITED, 2 = FULL, ..., N = LEVEL_N */
    private static int remapHardwareLevel(int level) {
        switch (level) {
            case OPT:
                return Integer.MAX_VALUE;
            case LEGACY:
                return 0; // lowest
            case LIMITED:
                return 1; // second lowest
            case FULL:
                return 2; // good
            default:
                if (level >= LEVEL_3) {
                    return level; // higher levels map directly
                }
        }

        fail("Unknown HW level: " + level);
        return -1;
    }

    private static String toStringHardwareLevel(int level) {
        switch (level) {
            case LEGACY:
                return "LEGACY";
            case LIMITED:
                return "LIMITED";
            case FULL:
                return "FULL";
            default:
                if (level >= LEVEL_3) {
                    return String.format("LEVEL_%d", level);
                }
        }

        // unknown
        Log.w(TAG, "Unknown hardware level " + level);
        return Integer.toString(level);
    }
}
