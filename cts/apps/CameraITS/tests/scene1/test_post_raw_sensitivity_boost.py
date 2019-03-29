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

import its.device
import its.caps
import its.image
import its.objects
import its.target
import os.path
from matplotlib import pylab
import matplotlib
import matplotlib.pyplot

def main():
    """Capture a set of raw/yuv images with different
        sensitivity/post Raw sensitivity boost combination
        and check if the output pixel mean matches request settings
    """
    NAME = os.path.basename(__file__).split(".")[0]

    # Each raw image
    RATIO_THRESHOLD = 0.1
    # Waive the check if raw pixel value is below this level (signal too small
    # that small black level error converts to huge error in percentage)
    RAW_PIXEL_VAL_THRESHOLD = 0.03

    with its.device.ItsSession() as cam:
        props = cam.get_camera_properties()
        its.caps.skip_unless(its.caps.raw_output(props) and
                             its.caps.post_raw_sensitivity_boost(props) and
                             its.caps.compute_target_exposure(props) and
                             its.caps.per_frame_control(props))

        w,h = its.objects.get_available_output_sizes(
                "yuv", props, (1920, 1080))[0]

        if its.caps.raw16(props):
            raw_format = 'raw'
        elif its.caps.raw10(props):
            raw_format = 'raw10'
        elif its.caps.raw12(props):
            raw_format = 'raw12'
        else: # should not reach here
            raise its.error.Error('Cannot find available RAW output format')

        out_surfaces = [{"format": raw_format},
                        {"format": "yuv", "width": w, "height": h}]

        sens_min, sens_max = props['android.sensor.info.sensitivityRange']
        sens_boost_min, sens_boost_max = \
                props['android.control.postRawSensitivityBoostRange']


        e_target, s_target = \
                its.target.get_target_exposure_combos(cam)["midSensitivity"]

        reqs = []
        settings = []
        s_boost = sens_boost_min
        while s_boost <= sens_boost_max:
            s_raw = int(round(s_target * 100.0 / s_boost))
            if s_raw < sens_min or s_raw > sens_max:
                break
            req = its.objects.manual_capture_request(s_raw, e_target)
            req['android.control.postRawSensitivityBoost'] = s_boost
            reqs.append(req)
            settings.append((s_raw, s_boost))
            if s_boost == sens_boost_max:
                break
            s_boost *= 2
            # Always try to test maximum sensitivity boost value
            if s_boost > sens_boost_max:
                s_boost = sens_boost_max

        caps = cam.do_capture(reqs, out_surfaces)

        raw_rgb_means = []
        yuv_rgb_means = []
        raw_caps, yuv_caps = caps
        if not isinstance(raw_caps, list):
            raw_caps = [raw_caps]
        if not isinstance(yuv_caps, list):
            yuv_caps = [yuv_caps]
        for i in xrange(len(reqs)):
            (s, s_boost) = settings[i]
            raw_cap = raw_caps[i]
            yuv_cap = yuv_caps[i]
            raw_rgb = its.image.convert_capture_to_rgb_image(raw_cap, props=props)
            yuv_rgb = its.image.convert_capture_to_rgb_image(yuv_cap)
            raw_tile = its.image.get_image_patch(raw_rgb, 0.45,0.45,0.1,0.1)
            yuv_tile = its.image.get_image_patch(yuv_rgb, 0.45,0.45,0.1,0.1)
            raw_rgb_means.append(its.image.compute_image_means(raw_tile))
            yuv_rgb_means.append(its.image.compute_image_means(yuv_tile))
            its.image.write_image(raw_tile,
                    "%s_raw_s=%04d_boost=%04d.jpg" % (NAME,s,s_boost))
            its.image.write_image(yuv_tile,
                    "%s_yuv_s=%04d_boost=%04d.jpg" % (NAME,s,s_boost))
            print "s=%d, s_boost=%d: raw_means %s, yuv_means %s"%(
                    s,s_boost,raw_rgb_means[-1], yuv_rgb_means[-1])

        xs = range(len(reqs))
        pylab.plot(xs, [rgb[0] for rgb in raw_rgb_means], 'r')
        pylab.plot(xs, [rgb[1] for rgb in raw_rgb_means], 'g')
        pylab.plot(xs, [rgb[2] for rgb in raw_rgb_means], 'b')
        pylab.ylim([0,1])
        matplotlib.pyplot.savefig("%s_raw_plot_means.png" % (NAME))
        pylab.clf()
        pylab.plot(xs, [rgb[0] for rgb in yuv_rgb_means], 'r')
        pylab.plot(xs, [rgb[1] for rgb in yuv_rgb_means], 'g')
        pylab.plot(xs, [rgb[2] for rgb in yuv_rgb_means], 'b')
        pylab.ylim([0,1])
        matplotlib.pyplot.savefig("%s_yuv_plot_means.png" % (NAME))

        rgb_str = ["R", "G", "B"]
        # Test that raw means is about 2x brighter than next step
        for step in range(1, len(reqs)):
            (s_prev, s_boost_prev) = settings[step - 1]
            (s, s_boost) = settings[step]
            expect_raw_ratio = s_prev / float(s)
            raw_thres_min = expect_raw_ratio * (1 - RATIO_THRESHOLD)
            raw_thres_max = expect_raw_ratio * (1 + RATIO_THRESHOLD)
            for rgb in range(3):
                ratio = raw_rgb_means[step - 1][rgb] / raw_rgb_means[step][rgb]
                print ("Step (%d,%d) %s channel: %f, %f, ratio %f," +
                       " threshold_min %f, threshold_max %f") % (
                        step-1, step, rgb_str[rgb],
                        raw_rgb_means[step - 1][rgb],
                        raw_rgb_means[step][rgb],
                        ratio, raw_thres_min, raw_thres_max)
                if (raw_rgb_means[step][rgb] <= RAW_PIXEL_VAL_THRESHOLD):
                    continue
                assert(raw_thres_min < ratio < raw_thres_max)

        # Test that each yuv step is about the same bright as their mean
        yuv_thres_min = 1 - RATIO_THRESHOLD
        yuv_thres_max = 1 + RATIO_THRESHOLD
        for rgb in range(3):
            vals = [val[rgb] for val in yuv_rgb_means]
            for step in range(len(reqs)):
                if (raw_rgb_means[step][rgb] <= RAW_PIXEL_VAL_THRESHOLD):
                    vals = vals[:step]
            mean = sum(vals) / len(vals)
            print "%s channel vals %s mean %f"%(rgb_str[rgb], vals, mean)
            for step in range(len(vals)):
                ratio = vals[step] / mean
                assert(yuv_thres_min < ratio < yuv_thres_max)

if __name__ == '__main__':
    main()
