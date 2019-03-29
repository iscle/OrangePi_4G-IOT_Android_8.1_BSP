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

import math

import its.caps
import its.device
import its.objects
import its.target


def main():
    """Test the validity of some metadata entries.

    Looks at capture results and at the camera characteristics objects.
    """
    global md, props, failed

    with its.device.ItsSession() as cam:
        # Arbitrary capture request exposure values; image content is not
        # important for this test, only the metadata.
        props = cam.get_camera_properties()
        auto_req = its.objects.auto_capture_request()
        cap = cam.do_capture(auto_req)
        md = cap["metadata"]

    print "Hardware level"
    print "  Legacy:", its.caps.legacy(props)
    print "  Limited:", its.caps.limited(props)
    print "  Full or better:", its.caps.full_or_better(props)
    print "Capabilities"
    print "  Manual sensor:", its.caps.manual_sensor(props)
    print "  Manual post-proc:", its.caps.manual_post_proc(props)
    print "  Raw:", its.caps.raw(props)
    print "  Sensor fusion:", its.caps.sensor_fusion(props)

    # Test: hardware level should be a valid value.
    check('props.has_key("android.info.supportedHardwareLevel")')
    check('props["android.info.supportedHardwareLevel"] is not None')
    check('props["android.info.supportedHardwareLevel"] in [0,1,2,3]')
    manual_sensor = its.caps.manual_sensor(props)

    # Test: rollingShutterSkew, and frameDuration tags must all be present,
    # and rollingShutterSkew must be greater than zero and smaller than all
    # of the possible frame durations.
    if manual_sensor:
        check('md.has_key("android.sensor.frameDuration")')
        check('md["android.sensor.frameDuration"] is not None')
    check('md.has_key("android.sensor.rollingShutterSkew")')
    check('md["android.sensor.rollingShutterSkew"] is not None')
    if manual_sensor:
        check('md["android.sensor.rollingShutterSkew"] > 0')
        check('md["android.sensor.frameDuration"] > 0')

    # Test: timestampSource must be a valid value.
    check('props.has_key("android.sensor.info.timestampSource")')
    check('props["android.sensor.info.timestampSource"] is not None')
    check('props["android.sensor.info.timestampSource"] in [0,1]')

    # Test: croppingType must be a valid value, and for full devices, it
    # must be FREEFORM=1.
    check('props.has_key("android.scaler.croppingType")')
    check('props["android.scaler.croppingType"] is not None')
    check('props["android.scaler.croppingType"] in [0,1]')

    # Test: android.sensor.blackLevelPattern exists for RAW and is not None
    if its.caps.raw(props):
        check('props.has_key("android.sensor.blackLevelPattern")')
        check('props["android.sensor.blackLevelPattern"] is not None')

    assert not failed

    if not its.caps.legacy(props):
        # Test: pixel_pitch, FOV, and hyperfocal distance are reasonable
        fmts = props["android.scaler.streamConfigurationMap"]["availableStreamConfigurations"]
        fmts = sorted(fmts, key=lambda k: k["width"]*k["height"], reverse=True)
        sensor_size = props["android.sensor.info.physicalSize"]
        pixel_pitch_h = (sensor_size["height"] / fmts[0]["height"] * 1E3)
        pixel_pitch_w = (sensor_size["width"] / fmts[0]["width"] * 1E3)
        print "Assert pixel_pitch WxH: %.2f um, %.2f um" % (pixel_pitch_w,
                                                            pixel_pitch_h)
        assert 1.0 <= pixel_pitch_w <= 10
        assert 1.0 <= pixel_pitch_h <= 10
        assert 0.333 <= pixel_pitch_w/pixel_pitch_h <= 3.0

        diag = math.sqrt(sensor_size["height"] ** 2 +
                         sensor_size["width"] ** 2)
        fl = md["android.lens.focalLength"]
        fov = 2 * math.degrees(math.atan(diag / (2 * fl)))
        print "Assert field of view: %.1f degrees" % fov
        assert 30 <= fov <= 130

        if its.caps.lens_approx_calibrated(props):
            diopter_hyperfocal = props["android.lens.info.hyperfocalDistance"]
            if diopter_hyperfocal != 0.0:
                hyperfocal = 1.0 / diopter_hyperfocal
                print "Assert hyperfocal distance: %.2f m" % hyperfocal
                assert 0.02 <= hyperfocal


def getval(expr, default=None):
    try:
        return eval(expr)
    except:
        return default

failed = False


def check(expr):
    global md, props, failed
    try:
        if eval(expr):
            print "Passed>", expr
        else:
            print "Failed>>", expr
            failed = True
    except:
        print "Failed>>", expr
        failed = True

if __name__ == '__main__':
    main()

