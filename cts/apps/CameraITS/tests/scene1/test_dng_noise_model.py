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

import os.path
import its.caps
import its.device
import its.image
import its.objects
import matplotlib
from matplotlib import pylab

NAME = os.path.basename(__file__).split('.')[0]
BAYER_LIST = ['R', 'GR', 'GB', 'B']
DIFF_THRESH = 0.0005  # absolute variance delta threshold
FRAC_THRESH = 0.2  # relative variance delta threshold
NUM_STEPS = 4
STATS_GRID = 49  # center 2.04% of image for calculations


def main():
    """Verify that the DNG raw model parameters are correct."""

    # Pass if the difference between expected and computed variances is small,
    # defined as being within an absolute variance delta or relative variance
    # delta of the expected variance, whichever is larger. This is to allow the
    # test to pass in the presence of some randomness (since this test is
    # measuring noise of a small patch) and some imperfect scene conditions
    # (since ITS doesn't require a perfectly uniformly lit scene).

    with its.device.ItsSession() as cam:

        props = cam.get_camera_properties()
        its.caps.skip_unless(its.caps.raw(props) and
                             its.caps.raw16(props) and
                             its.caps.manual_sensor(props) and
                             its.caps.read_3a(props) and
                             its.caps.per_frame_control(props))
        debug = its.caps.debug_mode()

        white_level = float(props['android.sensor.info.whiteLevel'])
        cfa_idxs = its.image.get_canonical_cfa_order(props)
        aax = props['android.sensor.info.activeArraySize']['left']
        aay = props['android.sensor.info.activeArraySize']['top']
        aaw = props['android.sensor.info.activeArraySize']['right']-aax
        aah = props['android.sensor.info.activeArraySize']['bottom']-aay

        # Expose for the scene with min sensitivity
        sens_min, sens_max = props['android.sensor.info.sensitivityRange']
        sens_step = (sens_max - sens_min) / NUM_STEPS
        s_ae, e_ae, _, _, f_dist = cam.do_3a(get_results=True)
        s_e_prod = s_ae * e_ae
        sensitivities = range(sens_min, sens_max, sens_step)

        var_expected = [[], [], [], []]
        var_measured = [[], [], [], []]
        x = STATS_GRID/2  # center in H of STATS_GRID
        y = STATS_GRID/2  # center in W of STATS_GRID
        for sens in sensitivities:

            # Capture a raw frame with the desired sensitivity
            exp = int(s_e_prod / float(sens))
            req = its.objects.manual_capture_request(sens, exp, f_dist)
            if debug:
                cap = cam.do_capture(req, cam.CAP_RAW)
                planes = its.image.convert_capture_to_planes(cap, props)
            else:
                cap = cam.do_capture(req, {'format': 'rawStats',
                                           'gridWidth': aaw/STATS_GRID,
                                           'gridHeight': aah/STATS_GRID})
                mean_img, var_img = its.image.unpack_rawstats_capture(cap)

            # Test each raw color channel (R, GR, GB, B)
            noise_profile = cap['metadata']['android.sensor.noiseProfile']
            assert len(noise_profile) == len(BAYER_LIST)
            for i in range(len(BAYER_LIST)):
                # Get the noise model parameters for this channel of this shot.
                ch = cfa_idxs[i]
                s, o = noise_profile[ch]

                # Use a very small patch to ensure gross uniformity (i.e. so
                # non-uniform lighting or vignetting doesn't affect the variance
                # calculation)
                black_level = its.image.get_black_level(i, props,
                                                        cap['metadata'])
                level_range = white_level - black_level
                if debug:
                    plane = ((planes[i] * white_level - black_level) /
                             level_range)
                    tile = its.image.get_image_patch(plane, 0.49, 0.49,
                                                     0.02, 0.02)
                    mean_img_ch = tile.mean()
                    var_measured[i].append(
                            its.image.compute_image_variances(tile)[0])
                else:
                    mean_img_ch = (mean_img[x, y, ch]-black_level)/level_range
                    var_measured[i].append(var_img[x, y, ch]/level_range**2)
                var_expected[i].append(s * mean_img_ch + o)

    for i, ch in enumerate(BAYER_LIST):
        pylab.plot(sensitivities, var_expected[i], 'rgkb'[i],
                   label=ch+' expected')
        pylab.plot(sensitivities, var_measured[i], 'rgkb'[i]+'--',
                   label=ch+' measured')
    pylab.xlabel('Sensitivity')
    pylab.ylabel('Center patch variance')
    pylab.legend(loc=2)
    matplotlib.pyplot.savefig('%s_plot.png' % NAME)

    # PASS/FAIL check
    for i, ch in enumerate(BAYER_LIST):
        diffs = [var_measured[i][j] - var_expected[i][j]
                 for j in range(NUM_STEPS)]
        print 'Diffs (%s):'%(ch), diffs
        for j, diff in enumerate(diffs):
            thresh = max(DIFF_THRESH, FRAC_THRESH*var_expected[i][j])
            assert diff <= thresh

if __name__ == '__main__':
    main()

