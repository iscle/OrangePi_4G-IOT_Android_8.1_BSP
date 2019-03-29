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

import matplotlib
matplotlib.use('Agg')

import its.error
from matplotlib import pylab
import sys
from PIL import Image
import numpy
import math
import unittest
import cStringIO
import scipy.stats
import copy
import cv2
import os

def scale_img(img, scale=1.0):
    """Scale and image based on a real number scale factor."""
    dim = (int(img.shape[1]*scale), int(img.shape[0]*scale))
    return cv2.resize(img.copy(), dim, interpolation=cv2.INTER_AREA)

class Chart(object):
    """Definition for chart object.

    Defines PNG reference file, chart size and distance, and scaling range.
    """

    def __init__(self, chart_file, height, distance, scale_start, scale_stop,
                 scale_step):
        """Initial constructor for class.

        Args:
            chart_file:     str; absolute path to png file of chart
            height:         float; height in cm of displayed chart
            distance:       float; distance in cm from camera of displayed chart
            scale_start:    float; start value for scaling for chart search
            scale_stop:     float; stop value for scaling for chart search
            scale_step:     float; step value for scaling for chart search
        """
        self._file = chart_file
        self._height = height
        self._distance = distance
        self._scale_start = scale_start
        self._scale_stop = scale_stop
        self._scale_step = scale_step

    def _calc_scale_factors(self, cam, props, fmt, s, e, fd):
        """Take an image with s, e, & fd to find the chart location.

        Args:
            cam:            An open device session.
            props:          Properties of cam
            fmt:            Image format for the capture
            s:              Sensitivity for the AF request as defined in
                            android.sensor.sensitivity
            e:              Exposure time for the AF request as defined in
                            android.sensor.exposureTime
            fd:             float; autofocus lens position
        Returns:
            template:       numpy array; chart template for locator
            img_3a:         numpy array; RGB image for chart location
            scale_factor:   float; scaling factor for chart search
        """
        req = its.objects.manual_capture_request(s, e)
        req['android.lens.focusDistance'] = fd
        cap_chart = its.image.stationary_lens_cap(cam, req, fmt)
        img_3a = its.image.convert_capture_to_rgb_image(cap_chart, props)
        img_3a = its.image.flip_mirror_img_per_argv(img_3a)
        its.image.write_image(img_3a, 'af_scene.jpg')
        template = cv2.imread(self._file, cv2.IMREAD_ANYDEPTH)
        focal_l = cap_chart['metadata']['android.lens.focalLength']
        pixel_pitch = (props['android.sensor.info.physicalSize']['height'] /
                       img_3a.shape[0])
        print ' Chart distance: %.2fcm' % self._distance
        print ' Chart height: %.2fcm' % self._height
        print ' Focal length: %.2fmm' % focal_l
        print ' Pixel pitch: %.2fum' % (pixel_pitch*1E3)
        print ' Template height: %dpixels' % template.shape[0]
        chart_pixel_h = self._height * focal_l / (self._distance * pixel_pitch)
        scale_factor = template.shape[0] / chart_pixel_h
        print 'Chart/image scale factor = %.2f' % scale_factor
        return template, img_3a, scale_factor

    def locate(self, cam, props, fmt, s, e, fd):
        """Find the chart in the image.

        Args:
            cam:            An open device session
            props:          Properties of cam
            fmt:            Image format for the capture
            s:              Sensitivity for the AF request as defined in
                            android.sensor.sensitivity
            e:              Exposure time for the AF request as defined in
                            android.sensor.exposureTime
            fd:             float; autofocus lens position

        Returns:
            xnorm:          float; [0, 1] left loc of chart in scene
            ynorm:          float; [0, 1] top loc of chart in scene
            wnorm:          float; [0, 1] width of chart in scene
            hnorm:          float; [0, 1] height of chart in scene
        """
        chart, scene, s_factor = self._calc_scale_factors(cam, props, fmt,
                                                          s, e, fd)
        scale_start = self._scale_start * s_factor
        scale_stop = self._scale_stop * s_factor
        scale_step = self._scale_step * s_factor
        max_match = []
        # check for normalized image
        if numpy.amax(scene) <= 1.0:
            scene = (scene * 255.0).astype(numpy.uint8)
        if len(scene.shape) == 2:
            scene_gray = scene.copy()
        elif len(scene.shape) == 3:
            if scene.shape[2] == 1:
                scene_gray = scene[:, :, 0]
            else:
                scene_gray = cv2.cvtColor(scene.copy(), cv2.COLOR_RGB2GRAY)
        print 'Finding chart in scene...'
        for scale in numpy.arange(scale_start, scale_stop, scale_step):
            scene_scaled = scale_img(scene_gray, scale)
            result = cv2.matchTemplate(scene_scaled, chart, cv2.TM_CCOEFF)
            _, opt_val, _, top_left_scaled = cv2.minMaxLoc(result)
            # print out scale and match
            print ' scale factor: %.3f, opt val: %.f' % (scale, opt_val)
            max_match.append((opt_val, top_left_scaled))

        # determine if optimization results are valid
        opt_values = [x[0] for x in max_match]
        if 2.0*min(opt_values) > max(opt_values):
            estring = ('Unable to find chart in scene!\n'
                       'Check camera distance and self-reported '
                       'pixel pitch, focal length and hyperfocal distance.')
            raise its.error.Error(estring)
        # find max and draw bbox
        match_index = max_match.index(max(max_match, key=lambda x: x[0]))
        scale = scale_start + scale_step * match_index
        print 'Optimum scale factor: %.3f' %  scale
        top_left_scaled = max_match[match_index][1]
        h, w = chart.shape
        bottom_right_scaled = (top_left_scaled[0] + w, top_left_scaled[1] + h)
        top_left = (int(top_left_scaled[0]/scale),
                    int(top_left_scaled[1]/scale))
        bottom_right = (int(bottom_right_scaled[0]/scale),
                        int(bottom_right_scaled[1]/scale))
        wnorm = float((bottom_right[0]) - top_left[0]) / scene.shape[1]
        hnorm = float((bottom_right[1]) - top_left[1]) / scene.shape[0]
        xnorm = float(top_left[0]) / scene.shape[1]
        ynorm = float(top_left[1]) / scene.shape[0]
        return xnorm, ynorm, wnorm, hnorm


class __UnitTest(unittest.TestCase):
    """Run a suite of unit tests on this module.
    """

    def test_compute_image_sharpness(self):
        """Unit test for compute_img_sharpness.

        Test by using PNG of ISO12233 chart and blurring intentionally.
        'sharpness' should drop off by sqrt(2) for 2x blur of image.

        We do one level of blur as PNG image is not perfect.
        """
        yuv_full_scale = 1023.0
        chart_file = os.path.join(os.environ['CAMERA_ITS_TOP'], 'pymodules',
                                  'its', 'test_images', 'ISO12233.png')
        chart = cv2.imread(chart_file, cv2.IMREAD_ANYDEPTH)
        white_level = numpy.amax(chart).astype(float)
        sharpness = {}
        for j in [2, 4, 8]:
            blur = cv2.blur(chart, (j, j))
            blur = blur[:, :, numpy.newaxis]
            sharpness[j] = (yuv_full_scale *
                    its.image.compute_image_sharpness(blur / white_level))
        self.assertTrue(numpy.isclose(sharpness[2]/sharpness[4],
                                      numpy.sqrt(2), atol=0.1))
        self.assertTrue(numpy.isclose(sharpness[4]/sharpness[8],
                                      numpy.sqrt(2), atol=0.1))


if __name__ == '__main__':
    unittest.main()
