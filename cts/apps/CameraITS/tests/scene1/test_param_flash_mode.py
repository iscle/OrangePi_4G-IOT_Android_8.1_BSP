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
import its.target
import os.path

def main():
    """Test that the android.flash.mode parameter is applied.
    """
    NAME = os.path.basename(__file__).split(".")[0]

    with its.device.ItsSession() as cam:
        props = cam.get_camera_properties()
        its.caps.skip_unless(its.caps.compute_target_exposure(props) and
                             its.caps.flash(props) and
                             its.caps.per_frame_control(props))

        flash_modes_reported = []
        flash_states_reported = []
        g_means = []

        # Manually set the exposure to be a little on the dark side, so that
        # it should be obvious whether the flash fired or not, and use a
        # linear tonemap.
        debug = its.caps.debug_mode()
        largest_yuv = its.objects.get_largest_yuv_format(props)
        if debug:
            fmt = largest_yuv
        else:
            match_ar = (largest_yuv['width'], largest_yuv['height'])
            fmt = its.objects.get_smallest_yuv_format(props, match_ar=match_ar)

        e, s = its.target.get_target_exposure_combos(cam)["midExposureTime"]
        e /= 4
        req = its.objects.manual_capture_request(s, e, 0.0, True, props)

        for f in [0,1,2]:
            req["android.flash.mode"] = f
            cap = cam.do_capture(req, fmt)
            flash_modes_reported.append(cap["metadata"]["android.flash.mode"])
            flash_states_reported.append(cap["metadata"]["android.flash.state"])
            img = its.image.convert_capture_to_rgb_image(cap)
            its.image.write_image(img, "%s_mode=%d.jpg" % (NAME, f))
            tile = its.image.get_image_patch(img, 0.45, 0.45, 0.1, 0.1)
            rgb = its.image.compute_image_means(tile)
            g_means.append(rgb[1])

        assert(flash_modes_reported == [0,1,2])
        assert(flash_states_reported[0] not in [3,4])
        assert(flash_states_reported[1] in [3,4])
        assert(flash_states_reported[2] in [3,4])

        print "G brightnesses:", g_means
        assert(g_means[1] > g_means[0])
        assert(g_means[2] > g_means[0])

if __name__ == '__main__':
    main()

