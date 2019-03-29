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

import its.image
import its.caps
import its.device
import its.objects
import os.path
import numpy
import matplotlib.pyplot

# Required for 3d plot to work
import mpl_toolkits.mplot3d

def main():
    """Test that valid data comes back in CaptureResult objects.
    """
    global NAME, auto_req, manual_req, w_map, h_map
    global manual_tonemap, manual_transform, manual_gains, manual_region
    global manual_exp_time, manual_sensitivity, manual_gains_ok

    NAME = os.path.basename(__file__).split(".")[0]

    with its.device.ItsSession() as cam:
        props = cam.get_camera_properties()
        its.caps.skip_unless(its.caps.manual_sensor(props) and
                             its.caps.manual_post_proc(props) and
                             its.caps.per_frame_control(props))

        manual_tonemap = [0,0, 1,1] # Linear
        manual_transform = its.objects.float_to_rational(
                [-1.5,-1.0,-0.5, 0.0,0.5,1.0, 1.5,2.0,3.0])
        manual_gains = [1,1.5,2.0,3.0]
        manual_region = [{"x":8,"y":8,"width":128,"height":128,"weight":1}]
        manual_exp_time = min(props['android.sensor.info.exposureTimeRange'])
        manual_sensitivity = min(props['android.sensor.info.sensitivityRange'])

        # The camera HAL may not support different gains for two G channels.
        manual_gains_ok = [[1,1.5,2.0,3.0],[1,1.5,1.5,3.0],[1,2.0,2.0,3.0]]

        auto_req = its.objects.auto_capture_request()
        auto_req["android.statistics.lensShadingMapMode"] = 1

        manual_req = {
            "android.control.mode": 0,
            "android.control.aeMode": 0,
            "android.control.awbMode": 0,
            "android.control.afMode": 0,
            "android.sensor.sensitivity": manual_sensitivity,
            "android.sensor.exposureTime": manual_exp_time,
            "android.colorCorrection.mode": 0,
            "android.colorCorrection.transform": manual_transform,
            "android.colorCorrection.gains": manual_gains,
            "android.tonemap.mode": 0,
            "android.tonemap.curveRed": manual_tonemap,
            "android.tonemap.curveGreen": manual_tonemap,
            "android.tonemap.curveBlue": manual_tonemap,
            "android.control.aeRegions": manual_region,
            "android.control.afRegions": manual_region,
            "android.control.awbRegions": manual_region,
            "android.statistics.lensShadingMapMode":1
            }

        w_map = props["android.lens.info.shadingMapSize"]["width"]
        h_map = props["android.lens.info.shadingMapSize"]["height"]

        print "Testing auto capture results"
        lsc_map_auto = test_auto(cam, w_map, h_map, props)
        print "Testing manual capture results"
        test_manual(cam, w_map, h_map, lsc_map_auto, props)
        print "Testing auto capture results again"
        test_auto(cam, w_map, h_map, props)

# A very loose definition for two floats being close to each other;
# there may be different interpolation and rounding used to get the
# two values, and all this test is looking at is whether there is
# something obviously broken; it's not looking for a perfect match.
def is_close_float(n1, n2):
    return abs(n1 - n2) < 0.05

def is_close_rational(n1, n2):
    return is_close_float(its.objects.rational_to_float(n1),
                          its.objects.rational_to_float(n2))

def draw_lsc_plot(w_map, h_map, lsc_map, name):
    for ch in range(4):
        fig = matplotlib.pyplot.figure()
        ax = fig.gca(projection='3d')
        xs = numpy.array([range(w_map)] * h_map).reshape(h_map, w_map)
        ys = numpy.array([[i]*w_map for i in range(h_map)]).reshape(
                h_map, w_map)
        zs = numpy.array(lsc_map[ch::4]).reshape(h_map, w_map)
        ax.plot_wireframe(xs, ys, zs)
        matplotlib.pyplot.savefig("%s_plot_lsc_%s_ch%d.png"%(NAME,name,ch))

def test_auto(cam, w_map, h_map, props):
    # Get 3A lock first, so the auto values in the capture result are
    # populated properly.
    rect = [[0,0,1,1,1]]
    cam.do_3a(rect, rect, rect, do_af=False)

    cap = cam.do_capture(auto_req)
    cap_res = cap["metadata"]

    gains = cap_res["android.colorCorrection.gains"]
    transform = cap_res["android.colorCorrection.transform"]
    exp_time = cap_res['android.sensor.exposureTime']
    lsc_map = cap_res["android.statistics.lensShadingMap"]
    ctrl_mode = cap_res["android.control.mode"]

    print "Control mode:", ctrl_mode
    print "Gains:", gains
    print "Transform:", [its.objects.rational_to_float(t)
                         for t in transform]
    if props["android.control.maxRegionsAe"] > 0:
        print "AE region:", cap_res['android.control.aeRegions']
    if props["android.control.maxRegionsAf"] > 0:
        print "AF region:", cap_res['android.control.afRegions']
    if props["android.control.maxRegionsAwb"] > 0:
        print "AWB region:", cap_res['android.control.awbRegions']
    print "LSC map:", w_map, h_map, lsc_map[:8]

    assert(ctrl_mode == 1)

    # Color correction gain and transform must be valid.
    assert(len(gains) == 4)
    assert(len(transform) == 9)
    assert(all([g > 0 for g in gains]))
    assert(all([t["denominator"] != 0 for t in transform]))

    # Color correction should not match the manual settings.
    assert(any([not is_close_float(gains[i], manual_gains[i])
                for i in xrange(4)]))
    assert(any([not is_close_rational(transform[i], manual_transform[i])
                for i in xrange(9)]))

    # Exposure time must be valid.
    assert(exp_time > 0)

    # Lens shading map must be valid.
    assert(w_map > 0 and h_map > 0 and w_map * h_map * 4 == len(lsc_map))
    assert(all([m >= 1 for m in lsc_map]))

    draw_lsc_plot(w_map, h_map, lsc_map, "auto")

    return lsc_map

def test_manual(cam, w_map, h_map, lsc_map_auto, props):
    cap = cam.do_capture(manual_req)
    cap_res = cap["metadata"]

    gains = cap_res["android.colorCorrection.gains"]
    transform = cap_res["android.colorCorrection.transform"]
    curves = [cap_res["android.tonemap.curveRed"],
              cap_res["android.tonemap.curveGreen"],
              cap_res["android.tonemap.curveBlue"]]
    exp_time = cap_res['android.sensor.exposureTime']
    lsc_map = cap_res["android.statistics.lensShadingMap"]
    ctrl_mode = cap_res["android.control.mode"]

    print "Control mode:", ctrl_mode
    print "Gains:", gains
    print "Transform:", [its.objects.rational_to_float(t)
                         for t in transform]
    print "Tonemap:", curves[0][1::16]
    if props["android.control.maxRegionsAe"] > 0:
        print "AE region:", cap_res['android.control.aeRegions']
    if props["android.control.maxRegionsAf"] > 0:
        print "AF region:", cap_res['android.control.afRegions']
    if props["android.control.maxRegionsAwb"] > 0:
        print "AWB region:", cap_res['android.control.awbRegions']
    print "LSC map:", w_map, h_map, lsc_map[:8]

    assert(ctrl_mode == 0)

    # Color correction gain and transform must be valid.
    # Color correction gains and transform should be the same size and
    # values as the manually set values.
    assert(len(gains) == 4)
    assert(len(transform) == 9)
    assert( all([is_close_float(gains[i], manual_gains_ok[0][i])
                 for i in xrange(4)]) or
            all([is_close_float(gains[i], manual_gains_ok[1][i])
                 for i in xrange(4)]) or
            all([is_close_float(gains[i], manual_gains_ok[2][i])
                 for i in xrange(4)]))
    assert(all([is_close_rational(transform[i], manual_transform[i])
                for i in xrange(9)]))

    # Tonemap must be valid.
    # The returned tonemap must be linear.
    for c in curves:
        assert(len(c) > 0)
        assert(all([is_close_float(c[i], c[i+1])
                    for i in xrange(0,len(c),2)]))

    # Exposure time must be close to the requested exposure time.
    assert(is_close_float(exp_time/1000000.0, manual_exp_time/1000000.0))

    # Lens shading map must be valid.
    assert(w_map > 0 and h_map > 0 and w_map * h_map * 4 == len(lsc_map))
    assert(all([m >= 1 for m in lsc_map]))

    draw_lsc_plot(w_map, h_map, lsc_map, "manual")

if __name__ == '__main__':
    main()

