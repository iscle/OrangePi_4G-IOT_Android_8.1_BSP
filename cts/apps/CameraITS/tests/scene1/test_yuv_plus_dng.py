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

def main():
    """Test capturing a single frame as both DNG and YUV outputs.
    """
    NAME = os.path.basename(__file__).split(".")[0]

    with its.device.ItsSession() as cam:
        props = cam.get_camera_properties()
        its.caps.skip_unless(its.caps.raw(props) and
                             its.caps.read_3a(props))

        cam.do_3a()

        req = its.objects.auto_capture_request()
        max_dng_size = \
                its.objects.get_available_output_sizes("raw", props)[0]
        w,h = its.objects.get_available_output_sizes(
                "yuv", props, (1920, 1080), max_dng_size)[0]
        out_surfaces = [{"format":"dng"},
                        {"format":"yuv", "width":w, "height":h}]
        cap_dng, cap_yuv = cam.do_capture(req, cam.CAP_DNG_YUV)

        img = its.image.convert_capture_to_rgb_image(cap_yuv)
        its.image.write_image(img, "%s.jpg" % (NAME))

        with open("%s.dng"%(NAME), "wb") as f:
            f.write(cap_dng["data"])

        # No specific pass/fail check; test is assumed to have succeeded if
        # it completes.

if __name__ == '__main__':
    main()

