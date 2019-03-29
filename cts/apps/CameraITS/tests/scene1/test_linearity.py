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
import numpy
import math
from matplotlib import pylab
import os.path
import matplotlib
import matplotlib.pyplot

NAME = os.path.basename(__file__).split('.')[0]
RESIDUAL_THRESHOLD = 0.0003  # approximately each sample is off by 2/255
# The HAL3.2 spec requires that curves up to 64 control points in length
# must be supported.
L = 64
LM1 = float(L-1)


def main():
    """Test that device processing can be inverted to linear pixels.

    Captures a sequence of shots with the device pointed at a uniform
    target. Attempts to invert all the ISP processing to get back to
    linear R,G,B pixel data.
    """
    gamma_lut = numpy.array(
        sum([[i/LM1, math.pow(i/LM1, 1/2.2)] for i in xrange(L)], []))
    inv_gamma_lut = numpy.array(
        sum([[i/LM1, math.pow(i/LM1, 2.2)] for i in xrange(L)], []))

    with its.device.ItsSession() as cam:
        props = cam.get_camera_properties()
        its.caps.skip_unless(its.caps.compute_target_exposure(props) and
                             its.caps.per_frame_control(props))

        debug = its.caps.debug_mode()
        largest_yuv = its.objects.get_largest_yuv_format(props)
        if debug:
            fmt = largest_yuv
        else:
            match_ar = (largest_yuv['width'], largest_yuv['height'])
            fmt = its.objects.get_smallest_yuv_format(props, match_ar=match_ar)

        e,s = its.target.get_target_exposure_combos(cam)["midSensitivity"]
        s /= 2
        sens_range = props['android.sensor.info.sensitivityRange']
        sensitivities = [s*1.0/3.0, s*2.0/3.0, s, s*4.0/3.0, s*5.0/3.0]
        sensitivities = [s for s in sensitivities
                         if s > sens_range[0] and s < sens_range[1]]

        req = its.objects.manual_capture_request(0, e)
        req['android.blackLevel.lock'] = True
        req['android.tonemap.mode'] = 0
        req['android.tonemap.curveRed'] = gamma_lut.tolist()
        req['android.tonemap.curveGreen'] = gamma_lut.tolist()
        req['android.tonemap.curveBlue'] = gamma_lut.tolist()

        r_means = []
        g_means = []
        b_means = []

        for sens in sensitivities:
            req["android.sensor.sensitivity"] = sens
            cap = cam.do_capture(req, fmt)
            img = its.image.convert_capture_to_rgb_image(cap)
            its.image.write_image(
                img, '%s_sens=%04d.jpg' % (NAME, sens))
            img = its.image.apply_lut_to_image(img, inv_gamma_lut[1::2] * LM1)
            tile = its.image.get_image_patch(img, 0.45, 0.45, 0.1, 0.1)
            rgb_means = its.image.compute_image_means(tile)
            r_means.append(rgb_means[0])
            g_means.append(rgb_means[1])
            b_means.append(rgb_means[2])

        pylab.title(NAME)
        pylab.plot(sensitivities, r_means, '-ro')
        pylab.plot(sensitivities, g_means, '-go')
        pylab.plot(sensitivities, b_means, '-bo')
        pylab.xlim([sens_range[0], sens_range[1]/2])
        pylab.ylim([0, 1])
        pylab.xlabel('sensitivity(ISO)')
        pylab.ylabel('RGB avg [0, 1]')
        matplotlib.pyplot.savefig('%s_plot_means.png' % (NAME))

        # Check that each plot is actually linear.
        for means in [r_means, g_means, b_means]:
            line, residuals, _, _, _ = numpy.polyfit(range(len(sensitivities)),
                                                     means, 1, full=True)
            print 'Line: m=%f, b=%f, resid=%f'%(line[0], line[1], residuals[0])
            assert residuals[0] < RESIDUAL_THRESHOLD

if __name__ == '__main__':
    main()

