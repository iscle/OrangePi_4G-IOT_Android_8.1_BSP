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
    """Take long bursts of images and check that they're all identical.

    Assumes a static scene. Can be used to idenfity if there are sporadic
    frames that are processed differently or have artifacts, or if 3A isn't
    stable, since this test converges 3A at the start but doesn't lock 3A
    throughout capture.
    """
    NAME = os.path.basename(__file__).split(".")[0]

    BURST_LEN = 6
    BURSTS = 2
    FRAMES = BURST_LEN * BURSTS

    DELTA_THRESH = 0.1

    with its.device.ItsSession() as cam:

        # Capture at full resolution.
        props = cam.get_camera_properties()
        its.caps.skip_unless(its.caps.manual_sensor(props) and
                             its.caps.awb_lock(props))
        w,h = its.objects.get_available_output_sizes("yuv", props)[0]

        # Converge 3A prior to capture.
        cam.do_3a(lock_ae=True, lock_awb=True)

        # After 3A has converged, lock AE+AWB for the duration of the test.
        req = its.objects.fastest_auto_capture_request(props)
        req["android.blackLevel.lock"] = True
        req["android.control.awbLock"] = True
        req["android.control.aeLock"] = True

        # Capture bursts of YUV shots.
        # Build a 4D array, which is an array of all RGB images after down-
        # scaling them by a factor of 4x4.
        imgs = numpy.empty([FRAMES,h/4,w/4,3])
        for j in range(BURSTS):
            caps = cam.do_capture([req]*BURST_LEN)
            for i,cap in enumerate(caps):
                n = j*BURST_LEN + i
                imgs[n] = its.image.downscale_image(
                        its.image.convert_capture_to_rgb_image(cap), 4)

        # Dump all images.
        print "Dumping images"
        for i in range(FRAMES):
            its.image.write_image(imgs[i], "%s_frame%03d.jpg"%(NAME,i))

        # The mean image.
        img_mean = imgs.mean(0)
        its.image.write_image(img_mean, "%s_mean.jpg"%(NAME))

        # Compute the deltas of each image from the mean image; this test
        # passes if none of the deltas are large.
        print "Computing frame differences"
        delta_maxes = []
        for i in range(FRAMES):
            deltas = (imgs[i] - img_mean).reshape(h*w*3/16)
            delta_max_pos = numpy.max(deltas)
            delta_max_neg = numpy.min(deltas)
            delta_maxes.append(max(abs(delta_max_pos), abs(delta_max_neg)))
        max_delta_max = max(delta_maxes)
        print "Frame %d has largest diff %f" % (
                delta_maxes.index(max_delta_max), max_delta_max)
        assert(max_delta_max < DELTA_THRESH)

if __name__ == '__main__':
    main()

