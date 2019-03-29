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
import math

def main():
    """Test that the reported sizes and formats for image capture work.
    """
    NAME = os.path.basename(__file__).split(".")[0]

    THRESHOLD_MAX_RMS_DIFF = 0.03

    with its.device.ItsSession() as cam:
        props = cam.get_camera_properties()
        its.caps.skip_unless(its.caps.compute_target_exposure(props) and
                             its.caps.per_frame_control(props))

        # Use a manual request with a linear tonemap so that the YUV and JPEG
        # should look the same (once converted by the its.image module).
        e, s = its.target.get_target_exposure_combos(cam)["midExposureTime"]
        req = its.objects.manual_capture_request(s, e, 0.0, True, props)

        rgbs = []

        for size in its.objects.get_available_output_sizes("yuv", props):
            out_surface = {"width":size[0], "height":size[1], "format":"yuv"}
            cap = cam.do_capture(req, out_surface)
            assert(cap["format"] == "yuv")
            assert(cap["width"] == size[0])
            assert(cap["height"] == size[1])
            print "Captured YUV %dx%d" % (cap["width"], cap["height"])
            img = its.image.convert_capture_to_rgb_image(cap)
            its.image.write_image(img, "%s_yuv_w%d_h%d.jpg"%(
                    NAME,size[0],size[1]))
            tile = its.image.get_image_patch(img, 0.45, 0.45, 0.1, 0.1)
            rgb = its.image.compute_image_means(tile)
            rgbs.append(rgb)

        for size in its.objects.get_available_output_sizes("jpg", props):
            out_surface = {"width":size[0], "height":size[1], "format":"jpg"}
            cap = cam.do_capture(req, out_surface)
            assert(cap["format"] == "jpeg")
            assert(cap["width"] == size[0])
            assert(cap["height"] == size[1])
            img = its.image.decompress_jpeg_to_rgb_image(cap["data"])
            its.image.write_image(img, "%s_jpg_w%d_h%d.jpg"%(
                    NAME,size[0], size[1]))
            assert(img.shape[0] == size[1])
            assert(img.shape[1] == size[0])
            assert(img.shape[2] == 3)
            print "Captured JPEG %dx%d" % (cap["width"], cap["height"])
            tile = its.image.get_image_patch(img, 0.45, 0.45, 0.1, 0.1)
            rgb = its.image.compute_image_means(tile)
            rgbs.append(rgb)

        max_diff = 0
        rgb0 = rgbs[0]
        for rgb1 in rgbs[1:]:
            rms_diff = math.sqrt(
                    sum([pow(rgb0[i] - rgb1[i], 2.0) for i in range(3)]) / 3.0)
            max_diff = max(max_diff, rms_diff)
        print "Max RMS difference:", max_diff
        assert(rms_diff < THRESHOLD_MAX_RMS_DIFF)

if __name__ == '__main__':
    main()

