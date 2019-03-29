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

def main():
    """get camera ids and save it to disk.
    """
    out_path = ""
    for s in sys.argv[1:]:
        if s[:4] == "out=" and len(s) > 4:
            out_path = s[4:]
    # kind of weird we need to open a camera to get camera ids, but
    # this is how ITS is working now.
    with its.device.ItsSession() as cam:
        camera_ids = cam.get_camera_ids()
        if out_path != "":
            with open(out_path, "w") as f:
                for camera_id in camera_ids:
                    f.write(camera_id + "\n")

if __name__ == '__main__':
    main()
