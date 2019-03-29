# Copyright 2016 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the 'License');
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an 'AS IS' BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import os

import its.caps
import its.cv2image
import its.device
import its.image
import its.objects
import numpy as np

NUM_IMGS = 12
FRAME_TIME_TOL = 10  # ms
SHARPNESS_TOL = 0.10  # percentage
POSITION_TOL = 0.10  # percentage
VGA_WIDTH = 640
VGA_HEIGHT = 480
NAME = os.path.basename(__file__).split('.')[0]
CHART_FILE = os.path.join(os.environ['CAMERA_ITS_TOP'], 'pymodules', 'its',
                          'test_images', 'ISO12233.png')
CHART_HEIGHT = 13.5  # cm
CHART_DISTANCE = 30.0  # cm
CHART_SCALE_START = 0.65
CHART_SCALE_STOP = 1.35
CHART_SCALE_STEP = 0.025


def test_lens_movement_reporting(cam, props, fmt, sensitivity, exp, af_fd):
    """Return fd, sharpness, lens state of the output images.

    Args:
        cam: An open device session.
        props: Properties of cam
        fmt: dict; capture format
        sensitivity: Sensitivity for the 3A request as defined in
            android.sensor.sensitivity
        exp: Exposure time for the 3A request as defined in
            android.sensor.exposureTime
        af_fd: Focus distance for the 3A request as defined in
            android.lens.focusDistance

    Returns:
        Object containing reported sharpness of the output image, keyed by
        the following string:
            'sharpness'
    """

    # initialize chart class
    chart = its.cv2image.Chart(CHART_FILE, CHART_HEIGHT, CHART_DISTANCE,
                               CHART_SCALE_START, CHART_SCALE_STOP,
                               CHART_SCALE_STEP)

    # find chart location
    xnorm, ynorm, wnorm, hnorm = chart.locate(cam, props, fmt, sensitivity,
                                              exp, af_fd)

    # initialize variables and take data sets
    data_set = {}
    white_level = int(props['android.sensor.info.whiteLevel'])
    min_fd = props['android.lens.info.minimumFocusDistance']
    fds = [af_fd, min_fd]
    fds = sorted(fds * NUM_IMGS)
    reqs = []
    for i, fd in enumerate(fds):
        reqs.append(its.objects.manual_capture_request(sensitivity, exp))
        reqs[i]['android.lens.focusDistance'] = fd
    caps = cam.do_capture(reqs, fmt)
    for i, cap in enumerate(caps):
        data = {'fd': fds[i]}
        data['loc'] = cap['metadata']['android.lens.focusDistance']
        data['lens_moving'] = (cap['metadata']['android.lens.state']
                               == 1)
        timestamp = cap['metadata']['android.sensor.timestamp']
        if i == 0:
            timestamp_init = timestamp
        timestamp -= timestamp_init
        timestamp *= 1E-6
        data['timestamp'] = timestamp
        print ' focus distance (diopters): %.3f' % data['fd']
        print ' current lens location (diopters): %.3f' % data['loc']
        print ' lens moving %r' % data['lens_moving']
        y, _, _ = its.image.convert_capture_to_planes(cap, props)
        y = its.image.flip_mirror_img_per_argv(y)
        chart = its.image.normalize_img(its.image.get_image_patch(y,
                                                                  xnorm, ynorm,
                                                                  wnorm, hnorm))
        its.image.write_image(chart, '%s_i=%d_chart.jpg' % (NAME, i))
        data['sharpness'] = white_level*its.image.compute_image_sharpness(chart)
        print 'Chart sharpness: %.1f\n' % data['sharpness']
        data_set[i] = data
    return data_set


def main():
    """Test if focus distance is properly reported.

    Capture images at a variety of focus locations.
    """

    print '\nStarting test_lens_movement_reporting.py'
    with its.device.ItsSession() as cam:
        props = cam.get_camera_properties()
        its.caps.skip_unless(not its.caps.fixed_focus(props))
        its.caps.skip_unless(its.caps.lens_approx_calibrated(props))
        min_fd = props['android.lens.info.minimumFocusDistance']
        fmt = {'format': 'yuv', 'width': VGA_WIDTH, 'height': VGA_HEIGHT}

        # Get proper sensitivity, exposure time, and focus distance with 3A.
        s, e, _, _, fd = cam.do_3a(get_results=True)

        # Get sharpness for each focal distance
        d = test_lens_movement_reporting(cam, props, fmt, s, e, fd)
        for k in sorted(d):
            print ('i: %d\tfd: %.3f\tlens location (diopters): %.3f \t'
                   'sharpness: %.1f  \tlens_moving: %r \t'
                   'timestamp: %.1fms' % (k, d[k]['fd'], d[k]['loc'],
                                          d[k]['sharpness'],
                                          d[k]['lens_moving'],
                                          d[k]['timestamp']))

        # assert frames are consecutive
        print 'Asserting frames are consecutive'
        times = [v['timestamp'] for v in d.itervalues()]
        diffs = np.gradient(times)
        assert np.isclose(np.amax(diffs)-np.amax(diffs), 0, atol=FRAME_TIME_TOL)

        # remove data when lens is moving
        for k in sorted(d):
            if d[k]['lens_moving']:
                del d[k]

        # split data into min_fd and af data for processing
        d_min_fd = {}
        d_af_fd = {}
        for k in sorted(d):
            if d[k]['fd'] == min_fd:
                d_min_fd[k] = d[k]
            if d[k]['fd'] == fd:
                d_af_fd[k] = d[k]

        # assert reported locations are close at af_fd
        print 'Asserting lens location of af_fd data'
        min_loc = min([v['loc'] for v in d_af_fd.itervalues()])
        max_loc = max([v['loc'] for v in d_af_fd.itervalues()])
        assert np.isclose(min_loc, max_loc, rtol=POSITION_TOL)
        # assert reported sharpness is close at af_fd
        print 'Asserting sharpness of af_fd data'
        min_sharp = min([v['sharpness'] for v in d_af_fd.itervalues()])
        max_sharp = max([v['sharpness'] for v in d_af_fd.itervalues()])
        assert np.isclose(min_sharp, max_sharp, rtol=SHARPNESS_TOL)
        # assert reported location is close to assign location for af_fd
        print 'Asserting lens location close to assigned fd for af_fd data'
        assert np.isclose(d_af_fd[0]['loc'], d_af_fd[0]['fd'],
                          rtol=POSITION_TOL)

        # assert reported location is close for min_fd captures
        print 'Asserting lens location similar min_fd data'
        min_loc = min([v['loc'] for v in d_min_fd.itervalues()])
        max_loc = max([v['loc'] for v in d_min_fd.itervalues()])
        assert np.isclose(min_loc, max_loc, rtol=POSITION_TOL)
        # assert reported sharpness is close at min_fd
        print 'Asserting sharpness of min_fd data'
        min_sharp = min([v['sharpness'] for v in d_min_fd.itervalues()])
        max_sharp = max([v['sharpness'] for v in d_min_fd.itervalues()])
        assert np.isclose(min_sharp, max_sharp, rtol=SHARPNESS_TOL)
        # assert reported location is close to assign location for min_fd
        print 'Asserting lens location close to assigned fd for min_fd data'
        assert np.isclose(d_min_fd[NUM_IMGS*2-1]['loc'],
                          d_min_fd[NUM_IMGS*2-1]['fd'], rtol=POSITION_TOL)


if __name__ == '__main__':
    main()
