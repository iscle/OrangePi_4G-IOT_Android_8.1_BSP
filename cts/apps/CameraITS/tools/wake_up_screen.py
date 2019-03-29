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

import re
import subprocess
import sys
import time

DISPLAY_LEVEL = 96  # [0:255] Depends on tablet model. Adjust for best result.
DISPLAY_CMD_WAIT = 0.5  # seconds. Screen commands take time to have effect
DISPLAY_TIMEOUT = 1800000  # ms


def main():
    """Power up and unlock screen as needed."""
    screen_id = None
    for s in sys.argv[1:]:
        if s[:7] == 'screen=' and len(s) > 7:
            screen_id = s[7:]

    if not screen_id:
        print 'Error: need to specify screen serial'
        assert False

    # turn on screen if necessary and unlock
    cmd = ('adb -s %s shell dumpsys display | egrep "mScreenState"'
           % screen_id)
    process = subprocess.Popen(cmd.split(), stdout=subprocess.PIPE)
    cmd_ret = process.stdout.read()
    screen_state = re.split(r'[s|=]', cmd_ret)[-1]
    power_event = ('adb -s %s shell input keyevent POWER' % screen_id)
    subprocess.Popen(power_event.split())
    time.sleep(DISPLAY_CMD_WAIT)
    if 'ON' in screen_state:
        print 'Screen was ON. Toggling to refresh.'
        subprocess.Popen(power_event.split())
        time.sleep(DISPLAY_CMD_WAIT)
    else:
        print 'Screen was OFF. Powered ON.'
    unlock = ('adb -s %s wait-for-device shell wm dismiss-keyguard'
              % screen_id)
    subprocess.Popen(unlock.split())
    time.sleep(DISPLAY_CMD_WAIT)

    # set brightness
    print 'Tablet display brightness set to %d' % DISPLAY_LEVEL
    bright = ('adb -s %s shell settings put system screen_brightness %d'
              % (screen_id, DISPLAY_LEVEL))
    subprocess.Popen(bright.split())
    time.sleep(DISPLAY_CMD_WAIT)

    # set screen to dim at max time (30min)
    stay_bright = ('adb -s %s shell settings put system screen_off_timeout %d'
                   % (screen_id, DISPLAY_TIMEOUT))
    subprocess.Popen(stay_bright.split())
    time.sleep(DISPLAY_CMD_WAIT)

if __name__ == '__main__':
    main()
