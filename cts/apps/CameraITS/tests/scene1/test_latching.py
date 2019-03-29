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
from matplotlib import pylab
import os.path
import matplotlib
import matplotlib.pyplot

def main():
    """Test that settings latch on the right frame.

    Takes a bunch of shots using back-to-back requests, varying the capture
    request parameters between shots. Checks that the images that come back
    have the expected properties.
    """
    NAME = os.path.basename(__file__).split(".")[0]

    with its.device.ItsSession() as cam:
        props = cam.get_camera_properties()
        its.caps.skip_unless(its.caps.full_or_better(props))

        _,fmt = its.objects.get_fastest_manual_capture_settings(props)
        e, s = its.target.get_target_exposure_combos(cam)["midExposureTime"]
        e /= 2.0

        r_means = []
        g_means = []
        b_means = []

        reqs = [
            its.objects.manual_capture_request(s,  e,   0.0, True, props),
            its.objects.manual_capture_request(s,  e,   0.0, True, props),
            its.objects.manual_capture_request(s*2,e,   0.0, True, props),
            its.objects.manual_capture_request(s*2,e,   0.0, True, props),
            its.objects.manual_capture_request(s,  e,   0.0, True, props),
            its.objects.manual_capture_request(s,  e,   0.0, True, props),
            its.objects.manual_capture_request(s,  e*2, 0.0, True, props),
            its.objects.manual_capture_request(s,  e,   0.0, True, props),
            its.objects.manual_capture_request(s*2,e,   0.0, True, props),
            its.objects.manual_capture_request(s,  e,   0.0, True, props),
            its.objects.manual_capture_request(s,  e*2, 0.0, True, props),
            its.objects.manual_capture_request(s,  e,   0.0, True, props),
            its.objects.manual_capture_request(s,  e*2, 0.0, True, props),
            its.objects.manual_capture_request(s,  e*2, 0.0, True, props),
            ]

        caps = cam.do_capture(reqs, fmt)
        for i,cap in enumerate(caps):
            img = its.image.convert_capture_to_rgb_image(cap)
            its.image.write_image(img, "%s_i=%02d.jpg" % (NAME, i))
            tile = its.image.get_image_patch(img, 0.45, 0.45, 0.1, 0.1)
            rgb_means = its.image.compute_image_means(tile)
            r_means.append(rgb_means[0])
            g_means.append(rgb_means[1])
            b_means.append(rgb_means[2])

        # Draw a plot.
        idxs = range(len(r_means))
        pylab.plot(idxs, r_means, 'r')
        pylab.plot(idxs, g_means, 'g')
        pylab.plot(idxs, b_means, 'b')
        pylab.ylim([0,1])
        matplotlib.pyplot.savefig("%s_plot_means.png" % (NAME))

        g_avg = sum(g_means) / len(g_means)
        g_ratios = [g / g_avg for g in g_means]
        g_hilo = [g>1.0 for g in g_ratios]
        assert(g_hilo == [False, False, True, True, False, False, True,
                          False, True, False, True, False, True, True])

if __name__ == '__main__':
    main()

