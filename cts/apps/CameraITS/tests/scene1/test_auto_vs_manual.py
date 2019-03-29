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

import its.image
import its.caps
import its.device
import its.objects
import os.path
import math

def main():
    """Capture auto and manual shots that should look the same.

    Manual shots taken with just manual WB, and also with manual WB+tonemap.

    In all cases, the general color/look of the shots should be the same,
    however there can be variations in brightness/contrast due to different
    "auto" ISP blocks that may be disabled in the manual flows.
    """
    NAME = os.path.basename(__file__).split(".")[0]

    with its.device.ItsSession() as cam:
        props = cam.get_camera_properties()
        its.caps.skip_unless(its.caps.manual_sensor(props) and
                             its.caps.manual_post_proc(props) and
                             its.caps.per_frame_control(props))

        # Converge 3A and get the estimates.
        debug = its.caps.debug_mode()
        largest_yuv = its.objects.get_largest_yuv_format(props)
        if debug:
            fmt = largest_yuv
        else:
            match_ar = (largest_yuv['width'], largest_yuv['height'])
            fmt = its.objects.get_smallest_yuv_format(props, match_ar=match_ar)
        sens, exp, gains, xform, focus = cam.do_3a(get_results=True)
        xform_rat = its.objects.float_to_rational(xform)
        print "AE sensitivity %d, exposure %dms" % (sens, exp/1000000.0)
        print "AWB gains", gains
        print "AWB transform", xform
        print "AF distance", focus

        # Auto capture.
        req = its.objects.auto_capture_request()
        cap_auto = cam.do_capture(req, fmt)
        img_auto = its.image.convert_capture_to_rgb_image(cap_auto)
        its.image.write_image(img_auto, "%s_auto.jpg" % (NAME))
        xform_a = its.objects.rational_to_float(
                cap_auto["metadata"]["android.colorCorrection.transform"])
        gains_a = cap_auto["metadata"]["android.colorCorrection.gains"]
        print "Auto gains:", gains_a
        print "Auto transform:", xform_a

        # Manual capture 1: WB
        req = its.objects.manual_capture_request(sens, exp, focus)
        req["android.colorCorrection.transform"] = xform_rat
        req["android.colorCorrection.gains"] = gains
        cap_man1 = cam.do_capture(req, fmt)
        img_man1 = its.image.convert_capture_to_rgb_image(cap_man1)
        its.image.write_image(img_man1, "%s_manual_wb.jpg" % (NAME))
        xform_m1 = its.objects.rational_to_float(
                cap_man1["metadata"]["android.colorCorrection.transform"])
        gains_m1 = cap_man1["metadata"]["android.colorCorrection.gains"]
        print "Manual wb gains:", gains_m1
        print "Manual wb transform:", xform_m1

        # Manual capture 2: WB + tonemap
        gamma = sum([[i/63.0,math.pow(i/63.0,1/2.2)] for i in xrange(64)],[])
        req["android.tonemap.mode"] = 0
        req["android.tonemap.curveRed"] = gamma
        req["android.tonemap.curveGreen"] = gamma
        req["android.tonemap.curveBlue"] = gamma
        cap_man2 = cam.do_capture(req, fmt)
        img_man2 = its.image.convert_capture_to_rgb_image(cap_man2)
        its.image.write_image(img_man2, "%s_manual_wb_tm.jpg" % (NAME))
        xform_m2 = its.objects.rational_to_float(
                cap_man2["metadata"]["android.colorCorrection.transform"])
        gains_m2 = cap_man2["metadata"]["android.colorCorrection.gains"]
        print "Manual wb+tm gains:", gains_m2
        print "Manual wb+tm transform:", xform_m2

        # Check that the WB gains and transform reported in each capture
        # result match with the original AWB estimate from do_3a.
        for g,x in [(gains_a,xform_a),(gains_m1,xform_m1),(gains_m2,xform_m2)]:
            assert(all([abs(xform[i] - x[i]) < 0.05 for i in range(9)]))
            assert(all([abs(gains[i] - g[i]) < 0.05 for i in range(4)]))

if __name__ == '__main__':
    main()

