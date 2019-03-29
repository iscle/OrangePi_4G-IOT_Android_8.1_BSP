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

import its.image
import its.caps
import its.device
import its.objects
import its.target

def main():
    """Test that the android.sensor.sensitivity parameter is applied properly
    within a burst. Inspects the output metadata only (not the image data).
    """

    NUM_STEPS = 3
    ERROR_TOLERANCE = 0.97 # Allow ISO to be rounded down by 3%

    with its.device.ItsSession() as cam:
        props = cam.get_camera_properties()
        its.caps.skip_unless(its.caps.manual_sensor(props) and
                             its.caps.per_frame_control(props))

        sens_range = props['android.sensor.info.sensitivityRange']
        sens_step = (sens_range[1] - sens_range[0]) / NUM_STEPS
        sens_list = range(sens_range[0], sens_range[1], sens_step)
        e = min(props['android.sensor.info.exposureTimeRange'])
        reqs = [its.objects.manual_capture_request(s,e) for s in sens_list]
        _,fmt = its.objects.get_fastest_manual_capture_settings(props)

        caps = cam.do_capture(reqs, fmt)
        for i,cap in enumerate(caps):
            s_req = sens_list[i]
            s_res = cap["metadata"]["android.sensor.sensitivity"]
            assert(s_req >= s_res)
            assert(s_res/float(s_req) > ERROR_TOLERANCE)

if __name__ == '__main__':
    main()

