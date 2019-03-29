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

import its.image
import its.device
import its.objects
import its.caps
import os.path
import numpy
from matplotlib import pylab
import matplotlib
import matplotlib.pyplot

def main():
    """Test 3A lock + YUV burst (using auto settings).

    This is a test that is designed to pass even on limited devices that
    don't have MANUAL_SENSOR or PER_FRAME_CONTROLS. The test checks
    YUV image consistency while the frame rate check is in CTS.
    """
    NAME = os.path.basename(__file__).split(".")[0]

    BURST_LEN = 8
    SPREAD_THRESH_MANUAL_SENSOR = 0.01
    SPREAD_THRESH = 0.03
    FPS_MAX_DIFF = 2.0

    with its.device.ItsSession() as cam:
        props = cam.get_camera_properties()
        its.caps.skip_unless(its.caps.ae_lock(props) and
                             its.caps.awb_lock(props))

        # Converge 3A prior to capture.
        cam.do_3a(do_af=True, lock_ae=True, lock_awb=True)

        fmt = its.objects.get_largest_yuv_format(props)

        # After 3A has converged, lock AE+AWB for the duration of the test.
        req = its.objects.fastest_auto_capture_request(props)
        req["android.control.awbLock"] = True
        req["android.control.aeLock"] = True

        # Capture bursts of YUV shots.
        # Get the mean values of a center patch for each.
        r_means = []
        g_means = []
        b_means = []
        caps = cam.do_capture([req]*BURST_LEN, fmt)
        for i,cap in enumerate(caps):
            img = its.image.convert_capture_to_rgb_image(cap)
            its.image.write_image(img, "%s_frame%d.jpg"%(NAME,i))
            tile = its.image.get_image_patch(img, 0.45, 0.45, 0.1, 0.1)
            means = its.image.compute_image_means(tile)
            r_means.append(means[0])
            g_means.append(means[1])
            b_means.append(means[2])

        # Pass/fail based on center patch similarity.
        for means in [r_means, g_means, b_means]:
            spread = max(means) - min(means)
            print "Patch mean spread", spread, \
                    " (min/max: ",  min(means), "/", max(means), ")"
            threshold = SPREAD_THRESH_MANUAL_SENSOR \
                    if its.caps.manual_sensor(props) else SPREAD_THRESH
            assert(spread < threshold)

if __name__ == '__main__':
    main()

