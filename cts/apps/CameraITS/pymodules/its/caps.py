# Copyright 2014 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import unittest
import its.objects
import sys


def skip_unless(cond):
    """Skips the test if the condition is false.

    If a test is skipped, then it is exited and returns the special code
    of 101 to the calling shell, which can be used by an external test
    harness to differentiate a skip from a pass or fail.

    Args:
        cond: Boolean, which must be true for the test to not skip.

    Returns:
        Nothing.
    """
    SKIP_RET_CODE = 101

    if not cond:
        print "Test skipped"
        sys.exit(SKIP_RET_CODE)

def full_or_better(props):
    """Returns whether a device is a FULL or better camera2 device.

    Args:
        props: Camera properties object.

    Returns:
        Boolean.
    """
    return props.has_key("android.info.supportedHardwareLevel") and \
            props["android.info.supportedHardwareLevel"] != 2 and \
            props["android.info.supportedHardwareLevel"] >= 1

def level3(props):
    """Returns whether a device is a LEVEL3 capability camera2 device.

    Args:
        props: Camera properties object.

    Returns:
        Boolean.
    """
    return props.has_key("android.info.supportedHardwareLevel") and \
           props["android.info.supportedHardwareLevel"] == 3

def full(props):
    """Returns whether a device is a FULL capability camera2 device.

    Args:
        props: Camera properties object.

    Returns:
        Boolean.
    """
    return props.has_key("android.info.supportedHardwareLevel") and \
           props["android.info.supportedHardwareLevel"] == 1

def limited(props):
    """Returns whether a device is a LIMITED capability camera2 device.

    Args:
        props: Camera properties object.

    Returns:
        Boolean.
    """
    return props.has_key("android.info.supportedHardwareLevel") and \
           props["android.info.supportedHardwareLevel"] == 0

def legacy(props):
    """Returns whether a device is a LEGACY capability camera2 device.

    Args:
        props: Camera properties object.

    Returns:
        Boolean.
    """
    return props.has_key("android.info.supportedHardwareLevel") and \
           props["android.info.supportedHardwareLevel"] == 2

def radial_distortion_correction(props):
    """Returns whether a device supports RADIAL_DISTORTION_CORRECTION
    capabilities.

    Args:
        props: Camera properties object.

    Returns:
        Boolean.
    """
    return props.has_key("android.lens.radialDistortion") and \
           props["android.lens.radialDistortion"] is not None

def manual_sensor(props):
    """Returns whether a device supports MANUAL_SENSOR capabilities.

    Args:
        props: Camera properties object.

    Returns:
        Boolean.
    """
    return    props.has_key("android.request.availableCapabilities") and \
              1 in props["android.request.availableCapabilities"]

def manual_post_proc(props):
    """Returns whether a device supports MANUAL_POST_PROCESSING capabilities.

    Args:
        props: Camera properties object.

    Returns:
        Boolean.
    """
    return    props.has_key("android.request.availableCapabilities") and \
              2 in props["android.request.availableCapabilities"]

def raw(props):
    """Returns whether a device supports RAW capabilities.

    Args:
        props: Camera properties object.

    Returns:
        Boolean.
    """
    return props.has_key("android.request.availableCapabilities") and \
           3 in props["android.request.availableCapabilities"]

def raw16(props):
    """Returns whether a device supports RAW16 output.

    Args:
        props: Camera properties object.

    Returns:
        Boolean.
    """
    return len(its.objects.get_available_output_sizes("raw", props)) > 0

def raw10(props):
    """Returns whether a device supports RAW10 output.

    Args:
        props: Camera properties object.

    Returns:
        Boolean.
    """
    return len(its.objects.get_available_output_sizes("raw10", props)) > 0

def raw12(props):
    """Returns whether a device supports RAW12 output.

    Args:
        props: Camera properties object.

    Returns:
        Boolean.
    """
    return len(its.objects.get_available_output_sizes("raw12", props)) > 0

def raw_output(props):
    """Returns whether a device supports any of RAW output format.

    Args:
        props: Camera properties object.

    Returns:
        Boolean.
    """
    return raw16(props) or raw10(props) or raw12(props)

def post_raw_sensitivity_boost(props):
    """Returns whether a device supports post RAW sensitivity boost..

    Args:
        props: Camera properties object.

    Returns:
        Boolean.
    """
    return props.has_key("android.control.postRawSensitivityBoostRange") and \
            props["android.control.postRawSensitivityBoostRange"] != [100, 100]

def sensor_fusion(props):
    """Returns whether the camera and motion sensor timestamps for the device
    are in the same time domain and can be compared directly.

    Args:
        props: Camera properties object.

    Returns:
        Boolean.
    """
    return props.has_key("android.sensor.info.timestampSource") and \
           props["android.sensor.info.timestampSource"] == 1

def read_3a(props):
    """Return whether a device supports reading out the following 3A settings:
        sensitivity
        exposure time
        awb gain
        awb cct
        focus distance

    Args:
        props: Camera properties object.

    Returns:
        Boolean.
    """
    # TODO: check available result keys explicitly
    return manual_sensor(props) and manual_post_proc(props)

def compute_target_exposure(props):
    """Return whether a device supports target exposure computation in its.target module.

    Args:
        props: Camera properties object.

    Returns:
        Boolean.
    """
    return manual_sensor(props) and manual_post_proc(props)

def freeform_crop(props):
    """Returns whether a device supports freefrom cropping.

    Args:
        props: Camera properties object.

    Return:
        Boolean.
    """
    return props.has_key("android.scaler.croppingType") and \
           props["android.scaler.croppingType"] == 1

def flash(props):
    """Returns whether a device supports flash control.

    Args:
        props: Camera properties object.

    Return:
        Boolean.
    """
    return props.has_key("android.flash.info.available") and \
           props["android.flash.info.available"] == 1

def per_frame_control(props):
    """Returns whether a device supports per frame control

    Args:
        props: Camera properties object.

    Return:
        Boolean.
    """
    return props.has_key("android.sync.maxLatency") and \
           props["android.sync.maxLatency"] == 0

def ev_compensation(props):
    """Returns whether a device supports ev compensation

    Args:
        props: Camera properties object.

    Return:
        Boolean.
    """
    return props.has_key("android.control.aeCompensationRange") and \
           props["android.control.aeCompensationRange"] != [0, 0]

def ae_lock(props):
    """Returns whether a device supports AE lock

    Args:
        props: Camera properties object.

    Return:
        Boolean.
    """
    return props.has_key("android.control.aeLockAvailable") and \
           props["android.control.aeLockAvailable"] == 1

def awb_lock(props):
    """Returns whether a device supports AWB lock

    Args:
        props: Camera properties object.

    Return:
        Boolean.
    """
    return props.has_key("android.control.awbLockAvailable") and \
           props["android.control.awbLockAvailable"] == 1

def lsc_map(props):
    """Returns whether a device supports lens shading map output

    Args:
        props: Camera properties object.

    Return:
        Boolean.
    """
    return props.has_key(
            "android.statistics.info.availableLensShadingMapModes") and \
        1 in props["android.statistics.info.availableLensShadingMapModes"]

def lsc_off(props):
    """Returns whether a device supports disabling lens shading correction

    Args:
        props: Camera properties object.

    Return:
        Boolean.
    """
    return props.has_key(
            "android.shading.availableModes") and \
        0 in props["android.shading.availableModes"]

def yuv_reprocess(props):
    """Returns whether a device supports YUV reprocessing.

    Args:
        props: Camera properties object.

    Returns:
        Boolean.
    """
    return props.has_key("android.request.availableCapabilities") and \
           7 in props["android.request.availableCapabilities"]

def private_reprocess(props):
    """Returns whether a device supports PRIVATE reprocessing.

    Args:
        props: Camera properties object.

    Returns:
        Boolean.
    """
    return props.has_key("android.request.availableCapabilities") and \
           4 in props["android.request.availableCapabilities"]

def noise_reduction_mode(props, mode):
    """Returns whether a device supports the noise reduction mode.

    Args:
        props: Camera properties objects.
        mode: Integer, indicating the noise reduction mode to check for
              availability.

    Returns:
        Boolean.
    """
    return props.has_key(
            "android.noiseReduction.availableNoiseReductionModes") and mode \
            in props["android.noiseReduction.availableNoiseReductionModes"];

def edge_mode(props, mode):
    """Returns whether a device supports the edge mode.

    Args:
        props: Camera properties objects.
        mode: Integer, indicating the edge mode to check for availability.

    Returns:
        Boolean.
    """
    return props.has_key(
            "android.edge.availableEdgeModes") and mode \
            in props["android.edge.availableEdgeModes"];


def lens_calibrated(props):
    """Returns whether lens position is calibrated or not.

    android.lens.info.focusDistanceCalibration has 3 modes.
    0: Uncalibrated
    1: Approximate
    2: Calibrated

    Args:
        props: Camera properties objects.

    Returns:
        Boolean.
    """
    return props.has_key("android.lens.info.focusDistanceCalibration") and \
         props["android.lens.info.focusDistanceCalibration"] == 2


def lens_approx_calibrated(props):
    """Returns whether lens position is calibrated or not.

    android.lens.info.focusDistanceCalibration has 3 modes.
    0: Uncalibrated
    1: Approximate
    2: Calibrated

    Args:
        props: Camera properties objects.

    Returns:
        Boolean.
    """
    return props.has_key("android.lens.info.focusDistanceCalibration") and \
        (props["android.lens.info.focusDistanceCalibration"] == 1 or
         props["android.lens.info.focusDistanceCalibration"] == 2)


def fixed_focus(props):
    """Returns whether a device is fixed focus.

    props[android.lens.info.minimumFocusDistance] == 0 is fixed focus

    Args:
        props: Camera properties objects.

    Returns:
        Boolean.
    """
    return props.has_key("android.lens.info.minimumFocusDistance") and \
        props["android.lens.info.minimumFocusDistance"] == 0


def debug_mode():
    """Returns True/False for whether test is run in debug mode.

    Returns:
        Boolean.
    """
    for s in sys.argv[1:]:
        if s[:6] == "debug=" and s[6:] == "True":
            return True
    return False


class __UnitTest(unittest.TestCase):
    """Run a suite of unit tests on this module.
    """
    # TODO: Add more unit tests.

if __name__ == '__main__':
    unittest.main()

