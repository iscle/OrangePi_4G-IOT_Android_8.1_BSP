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

import re
import subprocess
import sys
import time

TURN_OFF_DELAY = 1  # seconds. Needed for back to back runs


def main():
    """Put screen to sleep."""
    screen_id = ''
    for s in sys.argv[1:]:
        if s[:7] == 'screen=' and len(s) > 7:
            screen_id = s[7:]

    if not screen_id:
        print 'Error: need to specify screen serial'
        assert False

    cmd = ('adb -s %s shell dumpsys power | egrep "Display Power"'
           % screen_id)
    process = subprocess.Popen(cmd.split(), stdout=subprocess.PIPE)
    cmd_ret = process.stdout.read()
    screen_state = re.split(r'[s|=]', cmd_ret)[-1]
    if 'OFF' in screen_state:
        print 'Screen already OFF.'
    else:
        pwrdn = ('adb -s %s shell input keyevent POWER' % screen_id)
        subprocess.Popen(pwrdn.split())
        time.sleep(TURN_OFF_DELAY)

if __name__ == '__main__':
    main()
