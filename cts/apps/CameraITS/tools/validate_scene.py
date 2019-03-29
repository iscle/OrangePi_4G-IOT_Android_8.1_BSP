# Copyright 2015 The Android Open Source Project
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

import sys
import its.device
import its.objects
import its.image
import its.caps
import re

def main():
    """capture a yuv image and save it to argv[1]
    """
    camera_id = -1
    out_path = ""
    scene_name = ""
    scene_desc = "No requirement"
    do_af = True
    for s in sys.argv[1:]:
        if s[:7] == "camera=" and len(s) > 7:
            camera_id = s[7:]
        elif s[:4] == "out=" and len(s) > 4:
            out_path = s[4:]
        elif s[:6] == "scene=" and len(s) > 6:
            scene_desc = s[6:]
        elif s[:5] == "doAF=" and len(s) > 5:
            do_af = s[5:] == "True"

    if out_path != "":
        scene_name = re.split("/|\.", out_path)[-2]

    if camera_id == -1:
        print "Error: need to specify which camera to use"
        assert(False)

    with its.device.ItsSession() as cam:
        raw_input("Press Enter after placing camera " + camera_id +
                " to frame the test scene: " + scene_name +
                "\nThe scene setup should be: " + scene_desc )
        # Converge 3A prior to capture.
        cam.do_3a(do_af=do_af, lock_ae=True, lock_awb=True)
        props = cam.get_camera_properties()
        req = its.objects.fastest_auto_capture_request(props)
        if its.caps.ae_lock(props):
            req["android.control.awbLock"] = True
        if its.caps.awb_lock(props):
            req["android.control.aeLock"] = True
        while True:
            print "Capture an image to check the test scene"
            cap = cam.do_capture(req)
            img = its.image.convert_capture_to_rgb_image(cap)
            if out_path != "":
                its.image.write_image(img, out_path)
            print "Please check scene setup in", out_path
            choice = raw_input(
                "Is the image okay for ITS " + scene_name +\
                "? (Y/N)").lower()
            if choice == "y":
                break
            else:
                raw_input("Press Enter after placing camera " + camera_id +
                          " to frame the test scene")

if __name__ == '__main__':
    main()
