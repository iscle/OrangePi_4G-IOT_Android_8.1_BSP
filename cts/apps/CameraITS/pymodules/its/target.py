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

import its.device
import its.image
import its.objects
import os
import os.path
import sys
import json
import unittest
import json

CACHE_FILENAME = "its.target.cfg"

def __do_target_exposure_measurement(its_session):
    """Use device 3A and captured shots to determine scene exposure.

    Creates a new ITS device session (so this function should not be called
    while another session to the device is open).

    Assumes that the camera is pointed at a scene that is reasonably uniform
    and reasonably lit -- that is, an appropriate target for running the ITS
    tests that assume such uniformity.

    Measures the scene using device 3A and then by taking a shot to hone in on
    the exact exposure level that will result in a center 10% by 10% patch of
    the scene having a intensity level of 0.5 (in the pixel range of [0,1])
    when a linear tonemap is used. That is, the pixels coming off the sensor
    should be at approximately 50% intensity (however note that it's actually
    the luma value in the YUV image that is being targeted to 50%).

    The computed exposure value is the product of the sensitivity (ISO) and
    exposure time (ns) to achieve that sensor exposure level.

    Args:
        its_session: Holds an open device session.

    Returns:
        The measured product of sensitivity and exposure time that results in
            the luma channel of captured shots having an intensity of 0.5.
    """
    print "Measuring target exposure"

    # Get AE+AWB lock first, so the auto values in the capture result are
    # populated properly.
    r = [[0.45, 0.45, 0.1, 0.1, 1]]
    sens, exp_time, gains, xform, _ \
            = its_session.do_3a(r,r,r,do_af=False,get_results=True)

    # Convert the transform to rational.
    xform_rat = [{"numerator":int(100*x),"denominator":100} for x in xform]

    # Linear tonemap
    tmap = sum([[i/63.0,i/63.0] for i in range(64)], [])

    # Capture a manual shot with this exposure, using a linear tonemap.
    # Use the gains+transform returned by the AWB pass.
    req = its.objects.manual_capture_request(sens, exp_time)
    req["android.tonemap.mode"] = 0
    req["android.tonemap.curveRed"] = tmap
    req["android.tonemap.curveGreen"] = tmap
    req["android.tonemap.curveBlue"] = tmap
    req["android.colorCorrection.transform"] = xform_rat
    req["android.colorCorrection.gains"] = gains
    cap = its_session.do_capture(req)

    # Compute the mean luma of a center patch.
    yimg,uimg,vimg = its.image.convert_capture_to_planes(cap)
    tile = its.image.get_image_patch(yimg, 0.45, 0.45, 0.1, 0.1)
    luma_mean = its.image.compute_image_means(tile)

    # Compute the exposure value that would result in a luma of 0.5.
    return sens * exp_time * 0.5 / luma_mean[0]

def __set_cached_target_exposure(exposure):
    """Saves the given exposure value to a cached location.

    Once a value is cached, a call to __get_cached_target_exposure will return
    the value, even from a subsequent test/script run. That is, the value is
    persisted.

    The value is persisted in a JSON file in the current directory (from which
    the script calling this function is run).

    Args:
        exposure: The value to cache.
    """
    print "Setting cached target exposure"
    with open(CACHE_FILENAME, "w") as f:
        f.write(json.dumps({"exposure":exposure}))

def __get_cached_target_exposure():
    """Get the cached exposure value.

    Returns:
        The cached exposure value, or None if there is no valid cached value.
    """
    try:
        with open(CACHE_FILENAME, "r") as f:
            o = json.load(f)
            return o["exposure"]
    except:
        return None

def clear_cached_target_exposure():
    """If there is a cached exposure value, clear it.
    """
    if os.path.isfile(CACHE_FILENAME):
        os.remove(CACHE_FILENAME)

def set_hardcoded_exposure(exposure):
    """Set a hard-coded exposure value, rather than relying on measurements.

    The exposure value is the product of sensitivity (ISO) and eposure time
    (ns) that will result in a center-patch luma value of 0.5 (using a linear
    tonemap) for the scene that the camera is pointing at.

    If bringing up a new HAL implementation and the ability use the device to
    measure the scene isn't there yet (e.g. device 3A doesn't work), then a
    cache file of the appropriate name can be manually created and populated
    with a hard-coded value using this function.

    Args:
        exposure: The hard-coded exposure value to set.
    """
    __set_cached_target_exposure(exposure)

def get_target_exposure(its_session=None):
    """Get the target exposure to use.

    If there is a cached value and if the "target" command line parameter is
    present, then return the cached value. Otherwise, measure a new value from
    the scene, cache it, then return it.

    Args:
        its_session: Optional, holding an open device session.

    Returns:
        The target exposure value.
    """
    cached_exposure = None
    for s in sys.argv[1:]:
        if s == "target":
            cached_exposure = __get_cached_target_exposure()
    if cached_exposure is not None:
        print "Using cached target exposure"
        return cached_exposure
    if its_session is None:
        with its.device.ItsSession() as cam:
            measured_exposure = __do_target_exposure_measurement(cam)
    else:
        measured_exposure = __do_target_exposure_measurement(its_session)
    __set_cached_target_exposure(measured_exposure)
    return measured_exposure

def get_target_exposure_combos(its_session=None):
    """Get a set of legal combinations of target (exposure time, sensitivity).

    Gets the target exposure value, which is a product of sensitivity (ISO) and
    exposure time, and returns equivalent tuples of (exposure time,sensitivity)
    that are all legal and that correspond to the four extrema in this 2D param
    space, as well as to two "middle" points.

    Will open a device session if its_session is None.

    Args:
        its_session: Optional, holding an open device session.

    Returns:
        Object containing six legal (exposure time, sensitivity) tuples, keyed
        by the following strings:
            "minExposureTime"
            "midExposureTime"
            "maxExposureTime"
            "minSensitivity"
            "midSensitivity"
            "maxSensitivity
    """
    if its_session is None:
        with its.device.ItsSession() as cam:
            exposure = get_target_exposure(cam)
            props = cam.get_camera_properties()
    else:
        exposure = get_target_exposure(its_session)
        props = its_session.get_camera_properties()

    sens_range = props['android.sensor.info.sensitivityRange']
    exp_time_range = props['android.sensor.info.exposureTimeRange']

    # Combo 1: smallest legal exposure time.
    e1_expt = exp_time_range[0]
    e1_sens = exposure / e1_expt
    if e1_sens > sens_range[1]:
        e1_sens = sens_range[1]
        e1_expt = exposure / e1_sens

    # Combo 2: largest legal exposure time.
    e2_expt = exp_time_range[1]
    e2_sens = exposure / e2_expt
    if e2_sens < sens_range[0]:
        e2_sens = sens_range[0]
        e2_expt = exposure / e2_sens

    # Combo 3: smallest legal sensitivity.
    e3_sens = sens_range[0]
    e3_expt = exposure / e3_sens
    if e3_expt > exp_time_range[1]:
        e3_expt = exp_time_range[1]
        e3_sens = exposure / e3_expt

    # Combo 4: largest legal sensitivity.
    e4_sens = sens_range[1]
    e4_expt = exposure / e4_sens
    if e4_expt < exp_time_range[0]:
        e4_expt = exp_time_range[0]
        e4_sens = exposure / e4_expt

    # Combo 5: middle exposure time.
    e5_expt = (exp_time_range[0] + exp_time_range[1]) / 2.0
    e5_sens = exposure / e5_expt
    if e5_sens > sens_range[1]:
        e5_sens = sens_range[1]
        e5_expt = exposure / e5_sens
    if e5_sens < sens_range[0]:
        e5_sens = sens_range[0]
        e5_expt = exposure / e5_sens

    # Combo 6: middle sensitivity.
    e6_sens = (sens_range[0] + sens_range[1]) / 2.0
    e6_expt = exposure / e6_sens
    if e6_expt > exp_time_range[1]:
        e6_expt = exp_time_range[1]
        e6_sens = exposure / e6_expt
    if e6_expt < exp_time_range[0]:
        e6_expt = exp_time_range[0]
        e6_sens = exposure / e6_expt

    return {
        "minExposureTime" : (int(e1_expt), int(e1_sens)),
        "maxExposureTime" : (int(e2_expt), int(e2_sens)),
        "minSensitivity" : (int(e3_expt), int(e3_sens)),
        "maxSensitivity" : (int(e4_expt), int(e4_sens)),
        "midExposureTime" : (int(e5_expt), int(e5_sens)),
        "midSensitivity" : (int(e6_expt), int(e6_sens))
        }

class __UnitTest(unittest.TestCase):
    """Run a suite of unit tests on this module.
    """
    # TODO: Add some unit tests.

if __name__ == '__main__':
    unittest.main()

