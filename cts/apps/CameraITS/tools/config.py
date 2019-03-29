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

import its.device
import its.target
import sys

def main():
    """Set the target exposure.

    This program is just a wrapper around the its.target module, to allow the
    functions in it to be invoked from the command line.

    Usage:
        python config.py        - Measure the target exposure, and cache it.
        python config.py EXP    - Hard-code (and cache) the target exposure.

    The "reboot" or "reboot=<N>" and "camera=<N>" arguments may also be
    provided, just as with all the test scripts. The "target" argument is
    may also be provided but it has no effect on this script since the cached
    exposure value is cleared regardless.

    If no exposure value is provided, the camera will be used to measure
    the scene and set a level that will result in the luma (with linear
    tonemap) being at the 0.5 level. This requires camera 3A and capture
    to be functioning.

    For bring-up purposes, the exposure value may be manually set to a hard-
    coded value, without the camera having to be able to perform 3A (or even
    capture a shot reliably).
    """

    # Command line args, ignoring any args that will be passed down to the
    # ItsSession constructor.
    args = [s for s in sys.argv if s[:6] not in \
            ["reboot", "camera", "target", "device"]]

    if len(args) == 1:
        with its.device.ItsSession() as cam:
            # Automatically measure target exposure.
            its.target.clear_cached_target_exposure()
            exposure = its.target.get_target_exposure(cam)
    elif len(args) == 2:
        # Hard-code the target exposure.
        exposure = int(args[1])
        its.target.set_hardcoded_exposure(exposure)
    else:
        print "Usage: python %s [EXPOSURE]"
        sys.exit(0)
    print "New target exposure set to", exposure
    print "This corresponds to %dms at ISO 100" % int(exposure/100/1000000.0)

if __name__ == '__main__':
    main()

