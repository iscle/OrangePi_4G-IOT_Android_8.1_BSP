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

import os
import re
import subprocess
import sys
import time


def main():
    """Load charts on device and display."""
    camera_id = -1
    scene = None
    for s in sys.argv[1:]:
        if s[:6] == 'scene=' and len(s) > 6:
            scene = s[6:]
        elif s[:7] == 'screen=' and len(s) > 7:
            screen_id = s[7:]

    cmd = ('adb -s %s shell am force-stop com.google.android.apps.docs' %
           screen_id)
    subprocess.Popen(cmd.split())

    if not scene:
        print 'Error: need to specify which scene to load'
        assert False

    if not screen_id:
        print 'Error: need to specify screen serial'
        assert False

    remote_scene_file = '/sdcard/Download/%s.pdf' % scene
    local_scene_file = os.path.join(os.environ['CAMERA_ITS_TOP'], 'tests',
                                    scene, scene+'.pdf')
    print 'Loading %s on %s' % (remote_scene_file, screen_id)
    cmd = 'adb -s %s push %s /mnt%s' % (screen_id, local_scene_file,
                                        remote_scene_file)
    subprocess.Popen(cmd.split())
    time.sleep(1)  # wait-for-device doesn't always seem to work...
    # The intent require PDF viewing app be installed on device.
    # Also the first time such app is opened it might request some permission,
    # so it's  better to grant those permissions before using this script
    cmd = ("adb -s %s wait-for-device shell am start -d 'file://%s'"
           " -a android.intent.action.VIEW" % (screen_id,
                                               remote_scene_file))
    subprocess.Popen(cmd.split())

if __name__ == '__main__':
    main()
