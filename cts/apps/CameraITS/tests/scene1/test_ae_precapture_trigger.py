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
import its.objects
import its.target

AE_FRAMES_PER_ITERATION = 8
AE_CONVERGE_ITERATIONS = 3
# AE must converge within this number of auto requests under scene1
THRESH_AE_CONVERGE = AE_FRAMES_PER_ITERATION * AE_CONVERGE_ITERATIONS

def main():
    """Test the AE state machine when using the precapture trigger.
    """

    INACTIVE = 0
    SEARCHING = 1
    CONVERGED = 2
    LOCKED = 3
    FLASHREQUIRED = 4
    PRECAPTURE = 5

    with its.device.ItsSession() as cam:
        props = cam.get_camera_properties()
        its.caps.skip_unless(its.caps.compute_target_exposure(props) and
                             its.caps.per_frame_control(props))

        _,fmt = its.objects.get_fastest_manual_capture_settings(props)

        # Capture 5 manual requests, with AE disabled, and the last request
        # has an AE precapture trigger (which should be ignored since AE is
        # disabled).
        manual_reqs = []
        e, s = its.target.get_target_exposure_combos(cam)["midExposureTime"]
        manual_req = its.objects.manual_capture_request(s,e)
        manual_req['android.control.aeMode'] = 0 # Off
        manual_reqs += [manual_req]*4
        precap_req = its.objects.manual_capture_request(s,e)
        precap_req['android.control.aeMode'] = 0 # Off
        precap_req['android.control.aePrecaptureTrigger'] = 1 # Start
        manual_reqs.append(precap_req)
        caps = cam.do_capture(manual_reqs, fmt)
        for cap in caps:
            assert(cap['metadata']['android.control.aeState'] == INACTIVE)

        # Capture an auto request and verify the AE state; no trigger.
        auto_req = its.objects.auto_capture_request()
        auto_req['android.control.aeMode'] = 1  # On
        cap = cam.do_capture(auto_req, fmt)
        state = cap['metadata']['android.control.aeState']
        print "AE state after auto request:", state
        assert(state in [SEARCHING, CONVERGED])

        # Capture with auto request with a precapture trigger.
        auto_req['android.control.aePrecaptureTrigger'] = 1  # Start
        cap = cam.do_capture(auto_req, fmt)
        state = cap['metadata']['android.control.aeState']
        print "AE state after auto request with precapture trigger:", state
        assert(state in [SEARCHING, CONVERGED, PRECAPTURE])

        # Capture some more auto requests, and AE should converge.
        auto_req['android.control.aePrecaptureTrigger'] = 0
        for i in range(AE_CONVERGE_ITERATIONS):
            caps = cam.do_capture([auto_req] * AE_FRAMES_PER_ITERATION, fmt)
            state = caps[-1]['metadata']['android.control.aeState']
            print "AE state after auto request:", state
            if state == CONVERGED:
                return
        assert(state == CONVERGED)

if __name__ == '__main__':
    main()
