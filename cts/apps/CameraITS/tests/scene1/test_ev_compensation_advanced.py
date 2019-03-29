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
import its.caps
import its.objects
import os.path
from matplotlib import pylab
import matplotlib
import matplotlib.pyplot
import numpy

#AE must converge within this number of auto requests for EV
THREASH_CONVERGE_FOR_EV = 8

def main():
    """Tests that EV compensation is applied.
    """
    LOCKED = 3

    NAME = os.path.basename(__file__).split(".")[0]

    MAX_LUMA_DELTA_THRESH = 0.05

    with its.device.ItsSession() as cam:
        props = cam.get_camera_properties()
        its.caps.skip_unless(its.caps.manual_sensor(props) and
                             its.caps.manual_post_proc(props) and
                             its.caps.per_frame_control(props) and
                             its.caps.ev_compensation(props))

        debug = its.caps.debug_mode()
        largest_yuv = its.objects.get_largest_yuv_format(props)
        if debug:
            fmt = largest_yuv
        else:
            match_ar = (largest_yuv['width'], largest_yuv['height'])
            fmt = its.objects.get_smallest_yuv_format(props, match_ar=match_ar)

        ev_compensation_range = props['android.control.aeCompensationRange']
        range_min = ev_compensation_range[0]
        range_max = ev_compensation_range[1]
        ev_per_step = its.objects.rational_to_float(
                props['android.control.aeCompensationStep'])
        steps_per_ev = int(round(1.0 / ev_per_step))
        ev_steps = range(range_min, range_max + 1, steps_per_ev)
        imid = len(ev_steps) / 2
        ev_shifts = [pow(2, step * ev_per_step) for step in ev_steps]
        lumas = []

        # Converge 3A, and lock AE once converged. skip AF trigger as
        # dark/bright scene could make AF convergence fail and this test
        # doesn't care the image sharpness.
        cam.do_3a(ev_comp=0, lock_ae=True, do_af=False)

        for ev in ev_steps:

            # Capture a single shot with the same EV comp and locked AE.
            req = its.objects.auto_capture_request()
            req['android.control.aeExposureCompensation'] = ev
            req["android.control.aeLock"] = True
            # Use linear tone curve to avoid brightness being impacted
            # by tone curves.
            req["android.tonemap.mode"] = 0
            req["android.tonemap.curveRed"] = [0.0,0.0, 1.0,1.0]
            req["android.tonemap.curveGreen"] = [0.0,0.0, 1.0,1.0]
            req["android.tonemap.curveBlue"] = [0.0,0.0, 1.0,1.0]
            caps = cam.do_capture([req]*THREASH_CONVERGE_FOR_EV, fmt)

            for cap in caps:
                if (cap['metadata']['android.control.aeState'] == LOCKED):
                    y = its.image.convert_capture_to_planes(cap)[0]
                    tile = its.image.get_image_patch(y, 0.45,0.45,0.1,0.1)
                    lumas.append(its.image.compute_image_means(tile)[0])
                    break
            assert(cap['metadata']['android.control.aeState'] == LOCKED)

        print "ev_step_size_in_stops", ev_per_step
        shift_mid = ev_shifts[imid]
        luma_normal = lumas[imid] / shift_mid
        expected_lumas = [min(1.0, luma_normal * ev_shift) for ev_shift in ev_shifts]

        pylab.plot(ev_steps, lumas, 'r')
        pylab.plot(ev_steps, expected_lumas, 'b')
        matplotlib.pyplot.savefig("%s_plot_means.png" % (NAME))

        luma_diffs = [expected_lumas[i] - lumas[i] for i in range(len(ev_steps))]
        max_diff = max(abs(i) for i in luma_diffs)
        avg_diff = abs(numpy.array(luma_diffs)).mean()
        print "Max delta between modeled and measured lumas:", max_diff
        print "Avg delta between modeled and measured lumas:", avg_diff
        assert(max_diff < MAX_LUMA_DELTA_THRESH)

if __name__ == '__main__':
    main()
