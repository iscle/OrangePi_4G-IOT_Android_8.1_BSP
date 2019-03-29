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

NUM_TRYS = 2
NUM_STEPS = 6
SHARPNESS_TOL = 10  # percentage
POSITION_TOL = 10  # percentage
FRAME_TIME_TOL = 10  # ms
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


def test_lens_position(cam, props, fmt, sensitivity, exp, af_fd):
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
        Dictionary of results for different focal distance captures
        with static lens positions and moving lens positions
        d_static, d_moving
    """

    # initialize chart class
    chart = its.cv2image.Chart(CHART_FILE, CHART_HEIGHT, CHART_DISTANCE,
                                CHART_SCALE_START, CHART_SCALE_STOP,
                                CHART_SCALE_STEP)

    # find chart location
    xnorm, ynorm, wnorm, hnorm = chart.locate(cam, props, fmt, sensitivity,
                                              exp, af_fd)

    # initialize variables and take data sets
    data_static = {}
    data_moving = {}
    white_level = int(props['android.sensor.info.whiteLevel'])
    min_fd = props['android.lens.info.minimumFocusDistance']
    hyperfocal = props['android.lens.info.hyperfocalDistance']
    fds_f = np.arange(hyperfocal, min_fd, (min_fd-hyperfocal)/(NUM_STEPS-1))
    fds_f = np.append(fds_f, min_fd)
    fds_f = fds_f.tolist()
    fds_b = list(reversed(fds_f))
    fds_fb = list(fds_f)
    fds_fb.extend(fds_b)  # forward and back
    # take static data set
    for i, fd in enumerate(fds_fb):
        req = its.objects.manual_capture_request(sensitivity, exp)
        req['android.lens.focusDistance'] = fd
        cap = its.image.stationary_lens_cap(cam, req, fmt)
        data = {'fd': fds_fb[i]}
        data['loc'] = cap['metadata']['android.lens.focusDistance']
        print ' focus distance (diopters): %.3f' % data['fd']
        print ' current lens location (diopters): %.3f' % data['loc']
        y, _, _ = its.image.convert_capture_to_planes(cap, props)
        chart = its.image.normalize_img(its.image.get_image_patch(y,
                                                                  xnorm, ynorm,
                                                                  wnorm, hnorm))
        its.image.write_image(chart, '%s_stat_i=%d_chart.jpg' % (NAME, i))
        data['sharpness'] = white_level*its.image.compute_image_sharpness(chart)
        print 'Chart sharpness: %.1f\n' % data['sharpness']
        data_static[i] = data
    # take moving data set
    reqs = []
    for i, fd in enumerate(fds_f):
        reqs.append(its.objects.manual_capture_request(sensitivity, exp))
        reqs[i]['android.lens.focusDistance'] = fd
    caps = cam.do_capture(reqs, fmt)
    for i, cap in enumerate(caps):
        data = {'fd': fds_f[i]}
        data['loc'] = cap['metadata']['android.lens.focusDistance']
        data['lens_moving'] = (cap['metadata']['android.lens.state']
                               == 1)
        timestamp = cap['metadata']['android.sensor.timestamp'] * 1E-6
        if i == 0:
            timestamp_init = timestamp
        timestamp -= timestamp_init
        data['timestamp'] = timestamp
        print ' focus distance (diopters): %.3f' % data['fd']
        print ' current lens location (diopters): %.3f' % data['loc']
        y, _, _ = its.image.convert_capture_to_planes(cap, props)
        y = its.image.flip_mirror_img_per_argv(y)
        chart = its.image.normalize_img(its.image.get_image_patch(y,
                                                                  xnorm, ynorm,
                                                                  wnorm, hnorm))
        its.image.write_image(chart, '%s_move_i=%d_chart.jpg' % (NAME, i))
        data['sharpness'] = white_level*its.image.compute_image_sharpness(chart)
        print 'Chart sharpness: %.1f\n' % data['sharpness']
        data_moving[i] = data
    return data_static, data_moving


def main():
    """Test if focus position is properly reported for moving lenses."""

    print '\nStarting test_lens_position.py'
    with its.device.ItsSession() as cam:
        props = cam.get_camera_properties()
        its.caps.skip_unless(not its.caps.fixed_focus(props))
        its.caps.skip_unless(its.caps.lens_calibrated(props))
        fmt = {'format': 'yuv', 'width': VGA_WIDTH, 'height': VGA_HEIGHT}

        # Get proper sensitivity, exposure time, and focus distance with 3A.
        s, e, _, _, fd = cam.do_3a(get_results=True)

        # Get sharpness for each focal distance
        d_stat, d_move = test_lens_position(cam, props, fmt, s, e, fd)
        print 'Lens stationary'
        for k in sorted(d_stat):
            print ('i: %d\tfd: %.3f\tlens location (diopters): %.3f \t'
                   'sharpness: %.1f' % (k, d_stat[k]['fd'],
                                        d_stat[k]['loc'],
                                        d_stat[k]['sharpness']))
        print 'Lens moving'
        for k in sorted(d_move):
            print ('i: %d\tfd: %.3f\tlens location (diopters): %.3f \t'
                   'sharpness: %.1f  \tlens_moving: %r \t'
                   'timestamp: %.1fms' % (k, d_move[k]['fd'],
                                          d_move[k]['loc'],
                                          d_move[k]['sharpness'],
                                          d_move[k]['lens_moving'],
                                          d_move[k]['timestamp']))

        # assert static reported location/sharpness is close
        print 'Asserting static lens locations/sharpness are similar'
        for i in range(len(d_stat)/2):
            j = 2 * NUM_STEPS - 1 - i
            print (' lens position: %.3f'
                   % d_stat[i]['fd'])
            assert np.isclose(d_stat[i]['loc'], d_stat[i]['fd'],
                              rtol=POSITION_TOL/100.0)
            assert np.isclose(d_stat[i]['loc'], d_stat[j]['loc'],
                              rtol=POSITION_TOL/100.0)
            assert np.isclose(d_stat[i]['sharpness'], d_stat[j]['sharpness'],
                              rtol=SHARPNESS_TOL/100.0)
        # assert moving frames approximately consecutive with even distribution
        print 'Asserting moving frames are consecutive'
        times = [v['timestamp'] for v in d_move.itervalues()]
        diffs = np.gradient(times)
        assert np.isclose(np.amin(diffs), np.amax(diffs), atol=FRAME_TIME_TOL)
        # assert reported location/sharpness is correct in moving frames
        print 'Asserting moving lens locations/sharpness are similar'
        for i in range(len(d_move)):
            print ' lens position: %.3f' % d_stat[i]['fd']
            assert np.isclose(d_stat[i]['loc'], d_move[i]['loc'],
                              rtol=POSITION_TOL)
            if d_move[i]['lens_moving'] and i > 0:
                if d_stat[i]['sharpness'] > d_stat[i-1]['sharpness']:
                    assert (d_stat[i]['sharpness']*(1.0+SHARPNESS_TOL) >
                            d_move[i]['sharpness'] >
                            d_stat[i-1]['sharpness']*(1.0-SHARPNESS_TOL))
                else:
                    assert (d_stat[i-1]['sharpness']*(1.0+SHARPNESS_TOL) >
                            d_move[i]['sharpness'] >
                            d_stat[i]['sharpness']*(1.0-SHARPNESS_TOL))
            elif not d_move[i]['lens_moving']:
                assert np.isclose(d_stat[i]['sharpness'],
                                  d_move[i]['sharpness'], rtol=SHARPNESS_TOL)
            else:
                raise its.error.Error('Lens is moving at frame 0!')

if __name__ == '__main__':
    main()

