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
import its.device
import its.objects
import os.path

def main():
    """Test sensor test patterns.
    """
    NAME = os.path.basename(__file__).split(".")[0]

    with its.device.ItsSession() as cam:
        caps = []
        for i in range(1,6):
            req = its.objects.manual_capture_request(100, 10*1000*1000)
            req['android.sensor.testPatternData'] = [40, 100, 160, 220]
            req['android.sensor.testPatternMode'] = i

            # Capture the shot twice, and use the second one, so the pattern
            # will have stabilized.
            caps = cam.do_capture([req]*2)

            img = its.image.convert_capture_to_rgb_image(caps[1])
            its.image.write_image(img, "%s_pattern=%d.jpg" % (NAME, i))

if __name__ == '__main__':
    main()

