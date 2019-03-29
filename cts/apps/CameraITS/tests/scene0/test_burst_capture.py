# Copyright 2016 The Android Open Source Project
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
import its.device
import its.objects
import os.path

def main():
    """Test capture a burst of full size images is fast enough to not timeout.
       This test verify that entire capture pipeline can keep up the speed
       of fullsize capture + CPU read for at least some time.
    """
    NAME = os.path.basename(__file__).split(".")[0]
    NUM_TEST_FRAMES = 20

    with its.device.ItsSession() as cam:
        props = cam.get_camera_properties()
        req = its.objects.auto_capture_request()
        caps = cam.do_capture([req]*NUM_TEST_FRAMES)

        cap = caps[0]
        img = its.image.convert_capture_to_rgb_image(cap, props=props)
        img_name = "%s.jpg" % (NAME)
        its.image.write_image(img, img_name)

if __name__ == '__main__':
    main()

