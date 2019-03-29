# Copyright 2013 The Android Open Source Project
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

import os
import os.path
import sys
import re
import json
import tempfile
import time
import unittest
import subprocess
import math

def int_to_rational(i):
    """Function to convert Python integers to Camera2 rationals.

    Args:
        i: Python integer or list of integers.

    Returns:
        Python dictionary or list of dictionaries representing the given int(s)
        as rationals with denominator=1.
    """
    if isinstance(i, list):
        return [{"numerator":val, "denominator":1} for val in i]
    else:
        return {"numerator":i, "denominator":1}

def float_to_rational(f, denom=128):
    """Function to convert Python floats to Camera2 rationals.

    Args:
        f: Python float or list of floats.
        denom: (Optonal) the denominator to use in the output rationals.

    Returns:
        Python dictionary or list of dictionaries representing the given
        float(s) as rationals.
    """
    if isinstance(f, list):
        return [{"numerator":math.floor(val*denom+0.5), "denominator":denom}
                for val in f]
    else:
        return {"numerator":math.floor(f*denom+0.5), "denominator":denom}

def rational_to_float(r):
    """Function to convert Camera2 rational objects to Python floats.

    Args:
        r: Rational or list of rationals, as Python dictionaries.

    Returns:
        Float or list of floats.
    """
    if isinstance(r, list):
        return [float(val["numerator"]) / float(val["denominator"])
                for val in r]
    else:
        return float(r["numerator"]) / float(r["denominator"])

def manual_capture_request(
        sensitivity, exp_time, f_distance = 0.0, linear_tonemap=False, props=None):
    """Return a capture request with everything set to manual.

    Uses identity/unit color correction, and the default tonemap curve.
    Optionally, the tonemap can be specified as being linear.

    Args:
        sensitivity: The sensitivity value to populate the request with.
        exp_time: The exposure time, in nanoseconds, to populate the request
            with.
        f_distance: The focus distance to populate the request with.
        linear_tonemap: [Optional] whether a linear tonemap should be used
            in this request.
        props: [Optional] the object returned from
            its.device.get_camera_properties(). Must present when
            linear_tonemap is True.

    Returns:
        The default manual capture request, ready to be passed to the
        its.device.do_capture function.
    """
    req = {
        "android.control.captureIntent": 6,
        "android.control.mode": 0,
        "android.control.aeMode": 0,
        "android.control.awbMode": 0,
        "android.control.afMode": 0,
        "android.control.effectMode": 0,
        "android.sensor.sensitivity": sensitivity,
        "android.sensor.exposureTime": exp_time,
        "android.colorCorrection.mode": 0,
        "android.colorCorrection.transform":
                int_to_rational([1,0,0, 0,1,0, 0,0,1]),
        "android.colorCorrection.gains": [1,1,1,1],
        "android.lens.focusDistance" : f_distance,
        "android.tonemap.mode": 1,
        "android.shading.mode": 1,
        "android.lens.opticalStabilizationMode": 0
        }
    if linear_tonemap:
        assert(props is not None)
        #CONTRAST_CURVE mode
        if 0 in props["android.tonemap.availableToneMapModes"]:
            req["android.tonemap.mode"] = 0
            req["android.tonemap.curveRed"] = [0.0,0.0, 1.0,1.0]
            req["android.tonemap.curveGreen"] = [0.0,0.0, 1.0,1.0]
            req["android.tonemap.curveBlue"] = [0.0,0.0, 1.0,1.0]
        #GAMMA_VALUE mode
        elif 3 in props["android.tonemap.availableToneMapModes"]:
            req["android.tonemap.mode"] = 3
            req["android.tonemap.gamma"] = 1.0
        else:
            print "Linear tonemap is not supported"
            assert(False)
    return req

def auto_capture_request():
    """Return a capture request with everything set to auto.
    """
    return {
        "android.control.mode": 1,
        "android.control.aeMode": 1,
        "android.control.awbMode": 1,
        "android.control.afMode": 1,
        "android.colorCorrection.mode": 1,
        "android.tonemap.mode": 1,
        "android.lens.opticalStabilizationMode": 0
        }

def fastest_auto_capture_request(props):
    """Return an auto capture request for the fastest capture.

    Args:
        props: the object returned from its.device.get_camera_properties().

    Returns:
        A capture request with everything set to auto and all filters that
            may slow down capture set to OFF or FAST if possible
    """
    req = auto_capture_request()
    turn_slow_filters_off(props, req)

    return req

def get_available_output_sizes(fmt, props, max_size=None, match_ar_size=None):
    """Return a sorted list of available output sizes for a given format.

    Args:
        fmt: the output format, as a string in
            ["jpg", "yuv", "raw", "raw10", "raw12"].
        props: the object returned from its.device.get_camera_properties().
        max_size: (Optional) A (w,h) tuple.
            Sizes larger than max_size (either w or h)  will be discarded.
        match_ar_size: (Optional) A (w,h) tuple.
            Sizes not matching the aspect ratio of match_ar_size will be
            discarded.

    Returns:
        A sorted list of (w,h) tuples (sorted large-to-small).
    """
    AR_TOLERANCE = 0.03
    fmt_codes = {"raw":0x20, "raw10":0x25, "raw12":0x26,"yuv":0x23,
                 "jpg":0x100, "jpeg":0x100}
    configs = props['android.scaler.streamConfigurationMap']\
                   ['availableStreamConfigurations']
    fmt_configs = [cfg for cfg in configs if cfg['format'] == fmt_codes[fmt]]
    out_configs = [cfg for cfg in fmt_configs if cfg['input'] == False]
    out_sizes = [(cfg['width'],cfg['height']) for cfg in out_configs]
    if max_size:
        out_sizes = [s for s in out_sizes if
                s[0] <= max_size[0] and s[1] <= max_size[1]]
    if match_ar_size:
        ar = match_ar_size[0] / float(match_ar_size[1])
        out_sizes = [s for s in out_sizes if
                abs(ar - s[0] / float(s[1])) <= AR_TOLERANCE]
    out_sizes.sort(reverse=True, key=lambda s: s[0]) # 1st pass, sort by width
    out_sizes.sort(reverse=True, key=lambda s: s[0]*s[1]) # sort by area
    return out_sizes

def set_filter_off_or_fast_if_possible(props, req, available_modes, filter):
    """Check and set controlKey to off or fast in req.

    Args:
        props: the object returned from its.device.get_camera_properties().
        req: the input request. filter will be set to OFF or FAST if possible.
        available_modes: the key to check available modes.
        filter: the filter key

    Returns:
        Nothing.
    """
    if props.has_key(available_modes):
        if 0 in props[available_modes]:
            req[filter] = 0
        elif 1 in props[available_modes]:
            req[filter] = 1

def turn_slow_filters_off(props, req):
    """Turn filters that may slow FPS down to OFF or FAST in input request.

    This function modifies the request argument, such that filters that may
    reduce the frames-per-second throughput of the camera device will be set to
    OFF or FAST if possible.

    Args:
        props: the object returned from its.device.get_camera_properties().
        req: the input request.

    Returns:
        Nothing.
    """
    set_filter_off_or_fast_if_possible(props, req,
        "android.noiseReduction.availableNoiseReductionModes",
        "android.noiseReduction.mode")
    set_filter_off_or_fast_if_possible(props, req,
        "android.colorCorrection.availableAberrationModes",
        "android.colorCorrection.aberrationMode")
    if props.has_key("android.request.availableCharacteristicsKeys"):
        hot_pixel_modes = 393217 in props["android.request.availableCharacteristicsKeys"]
        edge_modes = 196610 in props["android.request.availableCharacteristicsKeys"]
    if props.has_key("android.request.availableRequestKeys"):
        hot_pixel_mode = 393216 in props["android.request.availableRequestKeys"]
        edge_mode = 196608 in props["android.request.availableRequestKeys"]
    if hot_pixel_modes and hot_pixel_mode:
        set_filter_off_or_fast_if_possible(props, req,
            "android.hotPixel.availableHotPixelModes",
            "android.hotPixel.mode")
    if edge_modes and edge_mode:
        set_filter_off_or_fast_if_possible(props, req,
            "android.edge.availableEdgeModes",
            "android.edge.mode")

def get_fastest_manual_capture_settings(props):
    """Return a capture request and format spec for the fastest capture.

    Args:
        props: the object returned from its.device.get_camera_properties().

    Returns:
        Two values, the first is a capture request, and the second is an output
        format specification, for the fastest possible (legal) capture that
        can be performed on this device (with the smallest output size).
    """
    fmt = "yuv"
    size = get_available_output_sizes(fmt, props)[-1]
    out_spec = {"format":fmt, "width":size[0], "height":size[1]}
    s = min(props['android.sensor.info.sensitivityRange'])
    e = min(props['android.sensor.info.exposureTimeRange'])
    req = manual_capture_request(s,e)

    turn_slow_filters_off(props, req)

    return req, out_spec


def get_smallest_yuv_format(props, match_ar=None):
    """Return a capture request and format spec for the smallest yuv size.

    Args:
        props: the object returned from its.device.get_camera_properties().

    Returns:
        fmt:    an output format specification, for the smallest possible yuv
        format for this device.
    """
    size = get_available_output_sizes("yuv", props, match_ar_size=match_ar)[-1]
    fmt = {"format":"yuv", "width":size[0], "height":size[1]}

    return fmt


def get_largest_yuv_format(props):
    """Return a capture request and format spec for the smallest yuv size.

    Args:
        props: the object returned from its.device.get_camera_properties().

    Returns:
        fmt:    an output format specification, for the smallest possible yuv
        format for this device.
    """
    size = get_available_output_sizes("yuv", props)[0]
    fmt = {"format":"yuv", "width":size[0], "height":size[1]}

    return fmt


def get_max_digital_zoom(props):
    """Returns the maximum amount of zooming possible by the camera device.

    Args:
        props: the object returned from its.device.get_camera_properties().

    Return:
        A float indicating the maximum amount of zooming possible by the
        camera device.
    """

    maxz = 1.0

    if props.has_key("android.scaler.availableMaxDigitalZoom"):
        maxz = props["android.scaler.availableMaxDigitalZoom"]

    return maxz


class __UnitTest(unittest.TestCase):
    """Run a suite of unit tests on this module.
    """

    def test_int_to_rational(self):
        """Unit test for int_to_rational.
        """
        self.assertEqual(int_to_rational(10),
                         {"numerator":10,"denominator":1})
        self.assertEqual(int_to_rational([1,2]),
                         [{"numerator":1,"denominator":1},
                          {"numerator":2,"denominator":1}])

    def test_float_to_rational(self):
        """Unit test for float_to_rational.
        """
        self.assertEqual(float_to_rational(0.5001, 64),
                        {"numerator":32, "denominator":64})

    def test_rational_to_float(self):
        """Unit test for rational_to_float.
        """
        self.assertTrue(
                abs(rational_to_float({"numerator":32,"denominator":64})-0.5)
                < 0.0001)

if __name__ == '__main__':
    unittest.main()

