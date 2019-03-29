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

import its.caps
import its.image
import its.device
import its.objects
import its.target
import pprint

def main():
    """Test that a capture result is returned from a manual capture; dump it.
    """

    with its.device.ItsSession() as cam:
        # Arbitrary capture request exposure values; image content is not
        # important for this test, only the metadata.
        props = cam.get_camera_properties()
        its.caps.skip_unless(its.caps.manual_sensor(props))

        req,fmt = its.objects.get_fastest_manual_capture_settings(props)
        cap = cam.do_capture(req, fmt)
        pprint.pprint(cap["metadata"])

        # No pass/fail check; test passes if it completes.

if __name__ == '__main__':
    main()

