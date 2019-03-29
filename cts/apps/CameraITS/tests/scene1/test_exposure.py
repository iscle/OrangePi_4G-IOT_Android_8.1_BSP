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

import os.path

import its.caps
import its.device
import its.image
import its.objects
import its.target
import matplotlib
from matplotlib import pylab
import numpy

IMG_STATS_GRID = 9  # find used to find the center 11.11%
NAME = os.path.basename(__file__).split('.')[0]
THRESHOLD_MAX_OUTLIER_DIFF = 0.1
THRESHOLD_MIN_LEVEL = 0.1
THRESHOLD_MAX_LEVEL = 0.9
THRESHOLD_MAX_LEVEL_DIFF = 0.045
THRESHOLD_MAX_LEVEL_DIFF_WIDE_RANGE = 0.06
THRESHOLD_ROUND_DOWN_GAIN = 0.1
THRESHOLD_ROUND_DOWN_EXP = 0.05


def get_raw_active_array_size(props):
    """Return the active array w, h from props."""
    aaw = (props['android.sensor.info.activeArraySize']['right'] -
           props['android.sensor.info.activeArraySize']['left'])
    aah = (props['android.sensor.info.activeArraySize']['bottom'] -
           props['android.sensor.info.activeArraySize']['top'])
    return aaw, aah


def main():
    """Test that a constant exposure is seen as ISO and exposure time vary.

    Take a series of shots that have ISO and exposure time chosen to balance
    each other; result should be the same brightness, but over the sequence
    the images should get noisier.
    """
    mults = []
    r_means = []
    g_means = []
    b_means = []
    raw_r_means = []
    raw_gr_means = []
    raw_gb_means = []
    raw_b_means = []
    threshold_max_level_diff = THRESHOLD_MAX_LEVEL_DIFF

    with its.device.ItsSession() as cam:
        props = cam.get_camera_properties()
        its.caps.skip_unless(its.caps.compute_target_exposure(props) and
                             its.caps.per_frame_control(props))

        process_raw = (its.caps.compute_target_exposure(props) and
                       its.caps.per_frame_control(props) and
                       its.caps.raw16(props) and
                       its.caps.manual_sensor(props))

        debug = its.caps.debug_mode()
        largest_yuv = its.objects.get_largest_yuv_format(props)
        if debug:
            fmt = largest_yuv
        else:
            match_ar = (largest_yuv['width'], largest_yuv['height'])
            fmt = its.objects.get_smallest_yuv_format(props, match_ar=match_ar)

        e, s = its.target.get_target_exposure_combos(cam)['minSensitivity']
        s_e_product = s*e
        expt_range = props['android.sensor.info.exposureTimeRange']
        sens_range = props['android.sensor.info.sensitivityRange']

        m = 1.0
        while s*m < sens_range[1] and e/m > expt_range[0]:
            mults.append(m)
            s_test = round(s*m)
            e_test = s_e_product / s_test
            print 'Testing s:', s_test, 'e:', e_test
            req = its.objects.manual_capture_request(
                s_test, e_test, 0.0, True, props)
            cap = cam.do_capture(req, fmt)
            s_res = cap['metadata']['android.sensor.sensitivity']
            e_res = cap['metadata']['android.sensor.exposureTime']
            assert 0 <= s_test - s_res < s_test * THRESHOLD_ROUND_DOWN_GAIN
            assert 0 <= e_test - e_res < e_test * THRESHOLD_ROUND_DOWN_EXP
            s_e_product_res = s_res * e_res
            request_result_ratio = s_e_product / s_e_product_res
            print 'Capture result s:', s_test, 'e:', e_test
            img = its.image.convert_capture_to_rgb_image(cap)
            its.image.write_image(img, '%s_mult=%3.2f.jpg' % (NAME, m))
            tile = its.image.get_image_patch(img, 0.45, 0.45, 0.1, 0.1)
            rgb_means = its.image.compute_image_means(tile)
            # Adjust for the difference between request and result
            r_means.append(rgb_means[0] * request_result_ratio)
            g_means.append(rgb_means[1] * request_result_ratio)
            b_means.append(rgb_means[2] * request_result_ratio)
            # do same in RAW space if possible
            if process_raw and debug:
                aaw, aah = get_raw_active_array_size(props)
                raw_cap = cam.do_capture(req,
                                         {'format': 'rawStats',
                                          'gridWidth': aaw/IMG_STATS_GRID,
                                          'gridHeight': aah/IMG_STATS_GRID})
                r, gr, gb, b = its.image.convert_capture_to_planes(raw_cap,
                                                                   props)
                raw_r_means.append(r[IMG_STATS_GRID/2, IMG_STATS_GRID/2]
                                   * request_result_ratio)
                raw_gr_means.append(gr[IMG_STATS_GRID/2, IMG_STATS_GRID/2]
                                    * request_result_ratio)
                raw_gb_means.append(gb[IMG_STATS_GRID/2, IMG_STATS_GRID/2]
                                    * request_result_ratio)
                raw_b_means.append(b[IMG_STATS_GRID/2, IMG_STATS_GRID/2]
                                   * request_result_ratio)
            # Test 3 steps per 2x gain
            m *= pow(2, 1.0 / 3)

        # Allow more threshold for devices with wider exposure range
        if m >= 64.0:
            threshold_max_level_diff = THRESHOLD_MAX_LEVEL_DIFF_WIDE_RANGE

    # Draw plots
    pylab.figure('rgb data')
    pylab.plot(mults, r_means, 'ro-')
    pylab.plot(mults, g_means, 'go-')
    pylab.plot(mults, b_means, 'bo-')
    pylab.title(NAME + 'RGB Data')
    pylab.xlabel('Gain Multiplier')
    pylab.ylabel('Normalized RGB Plane Avg')
    pylab.ylim([0, 1])
    matplotlib.pyplot.savefig('%s_plot_means.png' % (NAME))

    if process_raw and debug:
        pylab.figure('raw data')
        pylab.plot(mults, raw_r_means, 'ro-', label='R')
        pylab.plot(mults, raw_gr_means, 'go-', label='GR')
        pylab.plot(mults, raw_gb_means, 'ko-', label='GB')
        pylab.plot(mults, raw_b_means, 'bo-', label='B')
        pylab.title(NAME + 'RAW Data')
        pylab.xlabel('Gain Multiplier')
        pylab.ylabel('Normalized RAW Plane Avg')
        pylab.ylim([0, 1])
        pylab.legend(numpoints=1)
        matplotlib.pyplot.savefig('%s_plot_raw_means.png' % (NAME))

    # Check for linearity. Verify sample pixel mean values are close to each
    # other. Also ensure that the images aren't clamped to 0 or 1
    # (which would make them look like flat lines).
    for chan in xrange(3):
        values = [r_means, g_means, b_means][chan]
        m, b = numpy.polyfit(mults, values, 1).tolist()
        max_val = max(values)
        min_val = min(values)
        max_diff = max_val - min_val
        print 'Channel %d line fit (y = mx+b): m = %f, b = %f' % (chan, m, b)
        print 'Channel max %f min %f diff %f' % (max_val, min_val, max_diff)
        assert max_diff < threshold_max_level_diff
        assert b > THRESHOLD_MIN_LEVEL and b < THRESHOLD_MAX_LEVEL
        for v in values:
            assert v > THRESHOLD_MIN_LEVEL and v < THRESHOLD_MAX_LEVEL
            assert abs(v - b) < THRESHOLD_MAX_OUTLIER_DIFF
    if process_raw and debug:
        for chan in xrange(4):
            values = [raw_r_means, raw_gr_means, raw_gb_means,
                      raw_b_means][chan]
            m, b = numpy.polyfit(mults, values, 1).tolist()
            max_val = max(values)
            min_val = min(values)
            max_diff = max_val - min_val
            print 'Channel %d line fit (y = mx+b): m = %f, b = %f' % (chan,
                                                                      m, b)
            print 'Channel max %f min %f diff %f' % (max_val, min_val, max_diff)
            assert max_diff < threshold_max_level_diff
            assert b > THRESHOLD_MIN_LEVEL and b < THRESHOLD_MAX_LEVEL
            for v in values:
                assert v > THRESHOLD_MIN_LEVEL and v < THRESHOLD_MAX_LEVEL
                assert abs(v - b) < THRESHOLD_MAX_OUTLIER_DIFF

if __name__ == '__main__':
    main()
