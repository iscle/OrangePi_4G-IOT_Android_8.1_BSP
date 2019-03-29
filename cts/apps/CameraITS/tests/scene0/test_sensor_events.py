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

import its.device
import its.caps
import time

def main():
    """Basic test to query and print out sensor events.

    Test will only work if the screen is on (i.e.) the device isn't in standby.
    Pass if some of each event are received.
    """

    with its.device.ItsSession() as cam:
        props = cam.get_camera_properties()
        # Only run test if the appropriate caps are claimed.
        its.caps.skip_unless(its.caps.sensor_fusion(props))

        cam.start_sensor_events()
        time.sleep(1)
        events = cam.get_sensor_events()
        print "Events over 1s: %d gyro, %d accel, %d mag"%(
                len(events["gyro"]), len(events["accel"]), len(events["mag"]))
        assert(len(events["gyro"]) > 0)
        assert(len(events["accel"]) > 0)
        assert(len(events["mag"]) > 0)

if __name__ == '__main__':
    main()

