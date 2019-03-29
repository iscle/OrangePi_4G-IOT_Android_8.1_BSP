# Copyright 2015 The Android Open Source Project
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

import its.caps
import its.device
import its.image
import its.objects
import matplotlib
import numpy
import os
import os.path
from matplotlib import pylab

def main():
    """Test that the android.shading.mode param is applied.

    Switching shading modes and checks that the lens shading maps are
    modified as expected.
    """
    NAME = os.path.basename(__file__).split(".")[0]

    NUM_SHADING_MODE_SWITCH_LOOPS = 3
    THRESHOLD_DIFF_RATIO = 0.15

    with its.device.ItsSession() as cam:
        props = cam.get_camera_properties()

        its.caps.skip_unless(its.caps.per_frame_control(props) and
                             its.caps.lsc_map(props) and
                             its.caps.lsc_off(props))

        assert(props.has_key("android.lens.info.shadingMapSize") and
               props["android.lens.info.shadingMapSize"] != None)

        # lsc_off devices should always support OFF(0), FAST(1), and HQ(2)
        assert(props.has_key("android.shading.availableModes") and
               set(props["android.shading.availableModes"]) == set([0, 1, 2]))

        num_map_gains = props["android.lens.info.shadingMapSize"]["width"] * \
                        props["android.lens.info.shadingMapSize"]["height"] * 4

        # Test 1: Switching shading modes several times and verify:
        #   1. Lens shading maps with mode OFF are all 1.0
        #   2. Lens shading maps with mode FAST are similar after switching
        #      shading modes.
        #   3. Lens shading maps with mode HIGH_QUALITY are similar after
        #      switching shading modes.
        cam.do_3a();

        # Get the reference lens shading maps for OFF, FAST, and HIGH_QUALITY
        # in different sessions.
        # reference_maps[mode]
        reference_maps = [[] for mode in range(3)]
        reference_maps[0] = [1.0] * num_map_gains
        for mode in range(1, 3):
            req = its.objects.auto_capture_request();
            req["android.statistics.lensShadingMapMode"] = 1
            req["android.shading.mode"] = mode
            reference_maps[mode] = cam.do_capture(req)["metadata"] \
                    ["android.statistics.lensShadingMap"]

        # Get the lens shading maps while switching modes in one session.
        reqs = []
        for i in range(NUM_SHADING_MODE_SWITCH_LOOPS):
            for mode in range(3):
                req = its.objects.auto_capture_request();
                req["android.statistics.lensShadingMapMode"] = 1
                req["android.shading.mode"] = mode
                reqs.append(req);

        caps = cam.do_capture(reqs)

        # shading_maps[mode][loop]
        shading_maps = [[[] for loop in range(NUM_SHADING_MODE_SWITCH_LOOPS)]
                for mode in range(3)]

        # Get the shading maps out of capture results
        for i in range(len(caps)):
            shading_maps[i % 3][i / 3] = \
                    caps[i]["metadata"]["android.statistics.lensShadingMap"]

        # Draw the maps
        for mode in range(3):
            for i in range(NUM_SHADING_MODE_SWITCH_LOOPS):
                pylab.clf()
                pylab.plot(range(num_map_gains), shading_maps[mode][i], 'r')
                pylab.plot(range(num_map_gains), reference_maps[mode], 'g')
                pylab.xlim([0, num_map_gains])
                pylab.ylim([0.9, 4.0])
                matplotlib.pyplot.savefig("%s_ls_maps_mode_%d_loop_%d.png" %
                                          (NAME, mode, i))

        print "Verifying lens shading maps with mode OFF are all 1.0"
        for i in range(NUM_SHADING_MODE_SWITCH_LOOPS):
            assert(numpy.allclose(shading_maps[0][i], reference_maps[0]))

        for mode in range(1, 3):
            print "Verifying lens shading maps with mode", mode, "are similar"
            for i in range(NUM_SHADING_MODE_SWITCH_LOOPS):
                assert(numpy.allclose(shading_maps[mode][i],
                                      reference_maps[mode],
                                      THRESHOLD_DIFF_RATIO))

if __name__ == '__main__':
    main()
