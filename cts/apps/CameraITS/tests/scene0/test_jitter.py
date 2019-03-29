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
import its.caps
import its.device
import its.objects
import os.path
from matplotlib import pylab
import matplotlib
import matplotlib.pyplot

def main():
    """Measure jitter in camera timestamps.
    """
    NAME = os.path.basename(__file__).split(".")[0]

    # Pass/fail thresholds
    MIN_AVG_FRAME_DELTA = 30 # at least 30ms delta between frames
    MAX_VAR_FRAME_DELTA = 0.01 # variance of frame deltas
    MAX_FRAME_DELTA_JITTER = 0.3 # max ms gap from the average frame delta

    with its.device.ItsSession() as cam:
        props = cam.get_camera_properties()
        its.caps.skip_unless(its.caps.manual_sensor(props) and
                             its.caps.sensor_fusion(props))

        req, fmt = its.objects.get_fastest_manual_capture_settings(props)
        caps = cam.do_capture([req]*50, [fmt])

        # Print out the millisecond delta between the start of each exposure
        tstamps = [c['metadata']['android.sensor.timestamp'] for c in caps]
        deltas = [tstamps[i]-tstamps[i-1] for i in range(1,len(tstamps))]
        deltas_ms = [d/1000000.0 for d in deltas]
        avg = sum(deltas_ms) / len(deltas_ms)
        var = sum([d*d for d in deltas_ms]) / len(deltas_ms) - avg * avg
        range0 = min(deltas_ms) - avg
        range1 = max(deltas_ms) - avg
        print "Average:", avg
        print "Variance:", var
        print "Jitter range:", range0, "to", range1

        # Draw a plot.
        pylab.plot(range(len(deltas_ms)), deltas_ms)
        matplotlib.pyplot.savefig("%s_deltas.png" % (NAME))

        # Test for pass/fail.
        assert(avg > MIN_AVG_FRAME_DELTA)
        assert(var < MAX_VAR_FRAME_DELTA)
        assert(abs(range0) < MAX_FRAME_DELTA_JITTER)
        assert(abs(range1) < MAX_FRAME_DELTA_JITTER)

if __name__ == '__main__':
    main()

