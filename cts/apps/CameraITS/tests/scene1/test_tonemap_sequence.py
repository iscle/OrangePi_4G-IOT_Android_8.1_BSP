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
import numpy

def main():
    """Test a sequence of shots with different tonemap curves.
    """
    NAME = os.path.basename(__file__).split(".")[0]

    # There should be 3 identical frames followed by a different set of
    # 3 identical frames.
    MAX_SAME_DELTA = 0.03  # match number in test_burst_sameness_manual
    MIN_DIFF_DELTA = 0.10

    with its.device.ItsSession() as cam:
        props = cam.get_camera_properties()
        its.caps.skip_unless(its.caps.manual_sensor(props) and
                             its.caps.manual_post_proc(props) and
                             its.caps.per_frame_control(props))

        debug = its.caps.debug_mode()
        largest_yuv = its.objects.get_largest_yuv_format(props)
        if debug:
            fmt = largest_yuv
        else:
            match_ar = (largest_yuv['width'], largest_yuv['height'])
            fmt = its.objects.get_smallest_yuv_format(props, match_ar=match_ar)

        sens, exp_time, _,_,f_dist = cam.do_3a(do_af=True,get_results=True)

        means = []

        # Capture 3 manual shots with a linear tonemap.
        req = its.objects.manual_capture_request(sens, exp_time, f_dist, True, props)
        for i in [0,1,2]:
            cap = cam.do_capture(req, fmt)
            img = its.image.convert_capture_to_rgb_image(cap)
            its.image.write_image(img, "%s_i=%d.jpg" % (NAME, i))
            tile = its.image.get_image_patch(img, 0.45, 0.45, 0.1, 0.1)
            means.append(tile.mean(0).mean(0))

        # Capture 3 manual shots with the default tonemap.
        req = its.objects.manual_capture_request(sens, exp_time, f_dist, False)
        for i in [3,4,5]:
            cap = cam.do_capture(req, fmt)
            img = its.image.convert_capture_to_rgb_image(cap)
            its.image.write_image(img, "%s_i=%d.jpg" % (NAME, i))
            tile = its.image.get_image_patch(img, 0.45, 0.45, 0.1, 0.1)
            means.append(tile.mean(0).mean(0))

        # Compute the delta between each consecutive frame pair.
        deltas = [numpy.max(numpy.fabs(means[i+1]-means[i])) \
                  for i in range(len(means)-1)]
        print "Deltas between consecutive frames:", deltas

        assert(all([abs(deltas[i]) < MAX_SAME_DELTA for i in [0,1,3,4]]))
        assert(abs(deltas[2]) > MIN_DIFF_DELTA)

if __name__ == '__main__':
    main()

