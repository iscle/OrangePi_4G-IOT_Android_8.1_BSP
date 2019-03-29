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
import its.image
import os.path
from matplotlib import pylab
import matplotlib

GR_PLANE = 1  # GR plane index in RGGB data
IMG_STATS_GRID = 9  # find used to find the center 11.11%
NAME = os.path.basename(__file__).split(".")[0]
NUM_STEPS = 5
VAR_THRESH = 1.01  # each shot must be 1% noisier than previous


def main():
    """Capture a set of raw images with increasing gains and measure the noise.

    Capture raw-only, in a burst.
    """

    with its.device.ItsSession() as cam:

        props = cam.get_camera_properties()
        its.caps.skip_unless(its.caps.raw16(props) and
                             its.caps.manual_sensor(props) and
                             its.caps.read_3a(props) and
                             its.caps.per_frame_control(props))
        debug = its.caps.debug_mode()

        # Expose for the scene with min sensitivity
        sens_min, _ = props["android.sensor.info.sensitivityRange"]
        # Digital gains might not be visible on RAW data
        sens_max = props["android.sensor.maxAnalogSensitivity"]
        sens_step = (sens_max - sens_min) / NUM_STEPS
        s_ae, e_ae, _, _, f_dist = cam.do_3a(get_results=True)
        s_e_prod = s_ae * e_ae

        reqs = []
        settings = []
        for s in range(sens_min, sens_max, sens_step):
            e = int(s_e_prod / float(s))
            req = its.objects.manual_capture_request(s, e, f_dist)
            reqs.append(req)
            settings.append((s, e))

        if debug:
            caps = cam.do_capture(reqs, cam.CAP_RAW)
        else:
            # Get the active array width and height.
            aax = props["android.sensor.info.activeArraySize"]["left"]
            aay = props["android.sensor.info.activeArraySize"]["top"]
            aaw = props["android.sensor.info.activeArraySize"]["right"]-aax
            aah = props["android.sensor.info.activeArraySize"]["bottom"]-aay
            # Compute stats on a grid across each image.
            caps = cam.do_capture(reqs,
                                  {"format": "rawStats",
                                   "gridWidth": aaw/IMG_STATS_GRID,
                                   "gridHeight": aah/IMG_STATS_GRID})

        variances = []
        for i, cap in enumerate(caps):
            (s, e) = settings[i]

            # Each shot should be noisier than the previous shot (as the gain
            # is increasing). Use the variance of the center stats grid cell.
            if debug:
                gr = its.image.convert_capture_to_planes(cap, props)[1]
                tile = its.image.get_image_patch(gr, 0.445, 0.445, 0.11, 0.11)
                var = its.image.compute_image_variances(tile)[0]
                img = its.image.convert_capture_to_rgb_image(cap, props=props)
                its.image.write_image(img,
                                      "%s_s=%05d_var=%f.jpg" % (NAME, s, var))
            else:
                # find white level
                white_level = float(props["android.sensor.info.whiteLevel"])
                _, var_image = its.image.unpack_rawstats_capture(cap)
                cfa_idxs = its.image.get_canonical_cfa_order(props)
                var = var_image[IMG_STATS_GRID/2, IMG_STATS_GRID/2,
                                cfa_idxs[GR_PLANE]]/white_level**2
            variances.append(var)
            print "s=%d, e=%d, var=%e" % (s, e, var)

        x = range(len(variances))
        pylab.plot(x, variances, "-ro")
        pylab.xticks(x)
        pylab.xlabel("Setting Combination")
        pylab.ylabel("Image Center Patch Variance")
        matplotlib.pyplot.savefig("%s_variances.png" % NAME)

        # Test that each shot is noisier than the previous one.
        x.pop()  # remove last element in x index
        for i in x:
            assert variances[i] < variances[i+1] / VAR_THRESH

if __name__ == "__main__":
    main()

