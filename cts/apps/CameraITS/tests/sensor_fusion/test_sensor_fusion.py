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

import bisect
import json
import math
import os.path
import sys
import time

import cv2
import its.caps
import its.device
import its.image
import its.objects
import matplotlib
from matplotlib import pylab
import matplotlib.pyplot
import numpy
from PIL import Image
import scipy.spatial

NAME = os.path.basename(__file__).split(".")[0]

W, H = 640, 480
FPS = 30
TEST_LENGTH = 7  # seconds
FEATURE_MARGIN = 0.20  # Only take feature points from the center 20%
                       # so that the rotation measured have much less of rolling
                       # shutter effect

MIN_FEATURE_PTS = 30          # Minimum number of feature points required to
                              # perform rotation analysis

MAX_CAM_FRM_RANGE_SEC = 9.0   # Maximum allowed camera frame range. When this
                              # number is significantly larger than 7 seconds,
                              # usually system is in some busy/bad states.

MIN_GYRO_SMP_RATE = 100.0     # Minimum gyro sample rate

FEATURE_PARAMS = dict(maxCorners=240,
                      qualityLevel=0.3,
                      minDistance=7,
                      blockSize=7)

LK_PARAMS = dict(winSize=(15, 15),
                 maxLevel=2,
                 criteria=(cv2.TERM_CRITERIA_EPS | cv2.TERM_CRITERIA_COUNT,
                           10, 0.03))

# Constants to convert between different units (for clarity).
SEC_TO_NSEC = 1000*1000*1000.0
SEC_TO_MSEC = 1000.0
MSEC_TO_NSEC = 1000*1000.0
MSEC_TO_SEC = 1/1000.0
NSEC_TO_SEC = 1/(1000*1000*1000.0)
NSEC_TO_MSEC = 1/(1000*1000.0)
CM_TO_M = 1/100.0

# PASS/FAIL thresholds.
THRESH_MAX_CORR_DIST = 0.005
THRESH_MAX_SHIFT_MS = 1
THRESH_MIN_ROT = 0.001

# lens facing
FACING_FRONT = 0
FACING_BACK = 1
FACING_EXTERNAL = 2

# Chart distance
CHART_DISTANCE = 25  # cm


def main():
    """Test if image and motion sensor events are well synchronized.

    The instructions for running this test are in the SensorFusion.pdf file in
    the same directory as this test.

    Note that if fps*test_length is too large, write speeds may become a
    bottleneck and camera capture will slow down or stop.

    Command line arguments:
        fps:         FPS to capture with during the test
        img_size:    Comma-separated dimensions of captured images (defaults to
                     640x480). Ex: "img_size=<width>,<height>"
        replay:      Without this argument, the test will collect a new set of
                     camera+gyro data from the device and then analyze it (and
                     it will also dump this data to files in the current
                     directory).  If the "replay" argument is provided, then the
                     script will instead load the dumped data from a previous
                     run and analyze that instead. This can be helpful for
                     developers who are digging for additional information on
                     their measurements.
        test_length: How long the test should run for (in seconds)
    """

    fps = FPS
    w, h = W, H
    test_length = TEST_LENGTH
    for s in sys.argv[1:]:
        if s[:4] == "fps=" and len(s) > 4:
            fps = int(s[4:])
        elif s[:9] == "img_size=" and len(s) > 9:
            # Split by comma and convert each dimension to int.
            [w, h] = map(int, s[9:].split(","))
        elif s[:12] == "test_length=" and len(s) > 12:
            test_length = int(s[12:])

    # Collect or load the camera+gyro data. All gyro events as well as camera
    # timestamps are in the "events" dictionary, and "frames" is a list of
    # RGB images as numpy arrays.
    if "replay" not in sys.argv:
        if w * h > 640 * 480 or fps * test_length > 300:
            warning_str = (
                "Warning: Your test parameters may require fast write speeds "
                "to run smoothly.  If you run into problems, consider smaller "
                "values of \'w\', \'h\', \'fps\', or \'test_length\'."
            )
            print warning_str
        events, frames = collect_data(fps, w, h, test_length)
    else:
        events, frames, _, h = load_data()

    # Sanity check camera timestamps are enclosed by sensor timestamps
    # This will catch bugs where camera and gyro timestamps go completely out
    # of sync
    cam_times = get_cam_times(events["cam"])
    min_cam_time = min(cam_times) * NSEC_TO_SEC
    max_cam_time = max(cam_times) * NSEC_TO_SEC
    gyro_times = [e["time"] for e in events["gyro"]]
    min_gyro_time = min(gyro_times) * NSEC_TO_SEC
    max_gyro_time = max(gyro_times) * NSEC_TO_SEC
    if not (min_cam_time > min_gyro_time and max_cam_time < max_gyro_time):
        fail_str = ("Test failed: "
                    "camera timestamps [%f,%f] "
                    "are not enclosed by "
                    "gyro timestamps [%f, %f]"
                   ) % (min_cam_time, max_cam_time,
                        min_gyro_time, max_gyro_time)
        print fail_str
        assert 0

    cam_frame_range = max_cam_time - min_cam_time
    gyro_time_range = max_gyro_time - min_gyro_time
    gyro_smp_per_sec = len(gyro_times) / gyro_time_range
    print "Camera frame range", max_cam_time - min_cam_time
    print "Gyro samples per second", gyro_smp_per_sec
    assert cam_frame_range < MAX_CAM_FRM_RANGE_SEC
    assert gyro_smp_per_sec > MIN_GYRO_SMP_RATE

    # Compute the camera rotation displacements (rad) between each pair of
    # adjacent frames.
    cam_rots = get_cam_rotations(frames, events["facing"], h)
    if max(abs(cam_rots)) < THRESH_MIN_ROT:
        print "Device wasn't moved enough"
        assert 0

    # Find the best offset (time-shift) to align the gyro and camera motion
    # traces; this function integrates the shifted gyro data between camera
    # samples for a range of candidate shift values, and returns the shift that
    # result in the best correlation.
    offset = get_best_alignment_offset(cam_times, cam_rots, events["gyro"])

    # Plot the camera and gyro traces after applying the best shift.
    cam_times += offset*SEC_TO_NSEC
    gyro_rots = get_gyro_rotations(events["gyro"], cam_times)
    plot_rotations(cam_rots, gyro_rots)

    # Pass/fail based on the offset and also the correlation distance.
    dist = scipy.spatial.distance.correlation(cam_rots, gyro_rots)
    print "Best correlation of %f at shift of %.2fms"%(dist, offset*SEC_TO_MSEC)
    assert dist < THRESH_MAX_CORR_DIST
    assert abs(offset) < THRESH_MAX_SHIFT_MS*MSEC_TO_SEC


def get_best_alignment_offset(cam_times, cam_rots, gyro_events):
    """Find the best offset to align the camera and gyro traces.

    Uses a correlation distance metric between the curves, where a smaller
    value means that the curves are better-correlated.

    Args:
        cam_times: Array of N camera times, one for each frame.
        cam_rots: Array of N-1 camera rotation displacements (rad).
        gyro_events: List of gyro event objects.

    Returns:
        Offset (seconds) of the best alignment.
    """
    # Measure the corr. dist. over a shift of up to +/- 50ms (0.5ms step size).
    # Get the shift corresponding to the best (lowest) score.
    candidates = numpy.arange(-50, 50.5, 0.5).tolist()
    dists = []
    for shift in candidates:
        times = cam_times + shift*MSEC_TO_NSEC
        gyro_rots = get_gyro_rotations(gyro_events, times)
        dists.append(scipy.spatial.distance.correlation(cam_rots, gyro_rots))
    best_corr_dist = min(dists)
    best_shift = candidates[dists.index(best_corr_dist)]

    print "Best shift without fitting is ", best_shift, "ms"

    # Fit a curve to the corr. dist. data to measure the minima more
    # accurately, by looking at the correlation distances within a range of
    # +/- 10ms from the measured best score; note that this will use fewer
    # than the full +/- 10 range for the curve fit if the measured score
    # (which is used as the center of the fit) is within 10ms of the edge of
    # the +/- 50ms candidate range.
    i = dists.index(best_corr_dist)
    candidates = candidates[i-20:i+21]
    dists = dists[i-20:i+21]
    a, b, c = numpy.polyfit(candidates, dists, 2)
    exact_best_shift = -b/(2*a)
    if abs(best_shift - exact_best_shift) > 2.0 or a <= 0 or c <= 0:
        print "Test failed; bad fit to time-shift curve"
        print "best_shift %f, exact_best_shift %f, a %f, c %f" % (
            best_shift, exact_best_shift, a, c)
        assert 0

    xfit = numpy.arange(candidates[0], candidates[-1], 0.05).tolist()
    yfit = [a*x*x+b*x+c for x in xfit]
    matplotlib.pyplot.figure()
    pylab.plot(candidates, dists, "r", label="data")
    pylab.plot(xfit, yfit, "", label="fit")
    pylab.plot([exact_best_shift+x for x in [-0.1, 0, 0.1]], [0, 0.01, 0], "b")
    pylab.xlabel("Relative horizontal shift between curves (ms)")
    pylab.ylabel("Correlation distance")
    pylab.legend()
    matplotlib.pyplot.savefig("%s_plot_shifts.png" % (NAME))

    return exact_best_shift * MSEC_TO_SEC


def plot_rotations(cam_rots, gyro_rots):
    """Save a plot of the camera vs. gyro rotational measurements.

    Args:
        cam_rots: Array of N-1 camera rotation measurements (rad).
        gyro_rots: Array of N-1 gyro rotation measurements (rad).
    """
    # For the plot, scale the rotations to be in degrees.
    scale = 360/(2*math.pi)
    matplotlib.pyplot.figure()
    cam_rots *= scale
    gyro_rots *= scale
    pylab.plot(range(len(cam_rots)), cam_rots, "r", label="camera")
    pylab.plot(range(len(gyro_rots)), gyro_rots, "b", label="gyro")
    pylab.legend()
    pylab.xlabel("Camera frame number")
    pylab.ylabel("Angular displacement between adjacent camera frames (deg)")
    pylab.xlim([0, len(cam_rots)])
    matplotlib.pyplot.savefig("%s_plot.png" % (NAME))


def get_gyro_rotations(gyro_events, cam_times):
    """Get the rotation values of the gyro.

    Integrates the gyro data between each camera frame to compute an angular
    displacement.

    Args:
        gyro_events: List of gyro event objects.
        cam_times: Array of N camera times, one for each frame.

    Returns:
        Array of N-1 gyro rotation measurements (rad).
    """
    all_times = numpy.array([e["time"] for e in gyro_events])
    all_rots = numpy.array([e["z"] for e in gyro_events])
    gyro_rots = []
    # Integrate the gyro data between each pair of camera frame times.
    for icam in range(len(cam_times)-1):
        # Get the window of gyro samples within the current pair of frames.
        tcam0 = cam_times[icam]
        tcam1 = cam_times[icam+1]
        igyrowindow0 = bisect.bisect(all_times, tcam0)
        igyrowindow1 = bisect.bisect(all_times, tcam1)
        sgyro = 0
        # Integrate samples within the window.
        for igyro in range(igyrowindow0, igyrowindow1):
            vgyro = all_rots[igyro+1]
            tgyro0 = all_times[igyro]
            tgyro1 = all_times[igyro+1]
            deltatgyro = (tgyro1 - tgyro0) * NSEC_TO_SEC
            sgyro += vgyro * deltatgyro
        # Handle the fractional intervals at the sides of the window.
        for side, igyro in enumerate([igyrowindow0-1, igyrowindow1]):
            vgyro = all_rots[igyro+1]
            tgyro0 = all_times[igyro]
            tgyro1 = all_times[igyro+1]
            deltatgyro = (tgyro1 - tgyro0) * NSEC_TO_SEC
            if side == 0:
                f = (tcam0 - tgyro0) / (tgyro1 - tgyro0)
                sgyro += vgyro * deltatgyro * (1.0 - f)
            else:
                f = (tcam1 - tgyro0) / (tgyro1 - tgyro0)
                sgyro += vgyro * deltatgyro * f
        gyro_rots.append(sgyro)
    gyro_rots = numpy.array(gyro_rots)
    return gyro_rots


def get_cam_rotations(frames, facing, h):
    """Get the rotations of the camera between each pair of frames.

    Takes N frames and returns N-1 angular displacements corresponding to the
    rotations between adjacent pairs of frames, in radians.

    Args:
        frames: List of N images (as RGB numpy arrays).
        facing: Direction camera is facing
        h:      Pixel height of each frame

    Returns:
        Array of N-1 camera rotation measurements (rad).
    """
    gframes = []
    for frame in frames:
        frame = (frame * 255.0).astype(numpy.uint8)
        gframes.append(cv2.cvtColor(frame, cv2.COLOR_RGB2GRAY))
    rots = []

    ymin = h*(1-FEATURE_MARGIN)/2
    ymax = h*(1+FEATURE_MARGIN)/2
    for i in range(1, len(gframes)):
        gframe0 = gframes[i-1]
        gframe1 = gframes[i]
        p0 = cv2.goodFeaturesToTrack(gframe0, mask=None, **FEATURE_PARAMS)
        # p0's shape is N * 1 * 2
        mask = (p0[:, 0, 1] >= ymin) & (p0[:, 0, 1] <= ymax)
        p0_filtered = p0[mask]
        num_features = len(p0_filtered)
        if num_features < MIN_FEATURE_PTS:
            print "Not enough feature points in frame", i
            print "Need at least %d features, got %d" % (
                    MIN_FEATURE_PTS, num_features)
            assert 0
        else:
            print "Number of features in frame %d is %d" % (i, num_features)
        p1, st, _ = cv2.calcOpticalFlowPyrLK(gframe0, gframe1, p0_filtered,
                                             None, **LK_PARAMS)
        tform = procrustes_rotation(p0_filtered[st == 1], p1[st == 1])
        if facing == FACING_BACK:
            rot = -math.atan2(tform[0, 1], tform[0, 0])
        elif facing == FACING_FRONT:
            rot = math.atan2(tform[0, 1], tform[0, 0])
        else:
            print "Unknown lens facing", facing
            assert 0
        rots.append(rot)
        if i == 1:
            # Save a debug visualization of the features that are being
            # tracked in the first frame.
            frame = frames[i]
            for x, y in p0_filtered[st == 1]:
                cv2.circle(frame, (x, y), 3, (100, 100, 255), -1)
            its.image.write_image(frame, "%s_features.png" % NAME)
    return numpy.array(rots)


def get_cam_times(cam_events):
    """Get the camera frame times.

    Args:
        cam_events: List of (start_exposure, exposure_time, readout_duration)
            tuples, one per captured frame, with times in nanoseconds.

    Returns:
        frame_times: Array of N times, one corresponding to the "middle" of
            the exposure of each frame.
    """
    # Assign a time to each frame that assumes that the image is instantly
    # captured in the middle of its exposure.
    starts = numpy.array([start for start, exptime, readout in cam_events])
    exptimes = numpy.array([exptime for start, exptime, readout in cam_events])
    readouts = numpy.array([readout for start, exptime, readout in cam_events])
    frame_times = starts + (exptimes + readouts) / 2.0
    return frame_times


def load_data():
    """Load a set of previously captured data.

    Returns:
        events: Dictionary containing all gyro events and cam timestamps.
        frames: List of RGB images as numpy arrays.
        w:      Pixel width of frames
        h:      Pixel height of frames
    """
    with open("%s_events.txt" % NAME, "r") as f:
        events = json.loads(f.read())
    n = len(events["cam"])
    frames = []
    for i in range(n):
        img = Image.open("%s_frame%03d.png" % (NAME, i))
        w, h = img.size[0:2]
        frames.append(numpy.array(img).reshape(h, w, 3) / 255.0)
    return events, frames, w, h


def collect_data(fps, w, h, test_length):
    """Capture a new set of data from the device.

    Captures both motion data and camera frames, while the user is moving
    the device in a proscribed manner.

    Args:
        fps:         FPS to capture with
        w:           Pixel width of frames
        h:           Pixel height of frames
        test_length: How long the test should run for (in seconds)

    Returns:
        events: Dictionary containing all gyro events and cam timestamps.
        frames: List of RGB images as numpy arrays.
    """
    with its.device.ItsSession() as cam:
        props = cam.get_camera_properties()
        its.caps.skip_unless(its.caps.sensor_fusion(props) and
                             its.caps.manual_sensor(props) and
                             props["android.lens.facing"] != FACING_EXTERNAL)

        print "Starting sensor event collection"
        cam.start_sensor_events()

        # Sleep a while for gyro events to stabilize.
        time.sleep(0.5)

        # Capture the frames. OIS is disabled for manual captures.
        facing = props["android.lens.facing"]
        if facing != FACING_FRONT and facing != FACING_BACK:
            print "Unknown lens facing", facing
            assert 0

        fmt = {"format": "yuv", "width": w, "height": h}
        s, e, _, _, _ = cam.do_3a(get_results=True, do_af=False)
        req = its.objects.manual_capture_request(s, e)
        fps = 30
        req["android.lens.focusDistance"] = 1 / (CHART_DISTANCE * CM_TO_M)
        req["android.control.aeTargetFpsRange"] = [fps, fps]
        req["android.sensor.frameDuration"] = int(1000.0/fps * MSEC_TO_NSEC)
        print "Capturing %dx%d with sens. %d, exp. time %.1fms" % (
            w, h, s, e*NSEC_TO_MSEC)
        caps = cam.do_capture([req]*fps*test_length, fmt)

        # Get the gyro events.
        print "Reading out sensor events"
        gyro = cam.get_sensor_events()["gyro"]
        print "Number of gyro samples", len(gyro)

        # Combine the events into a single structure.
        print "Dumping event data"
        starts = [c["metadata"]["android.sensor.timestamp"] for c in caps]
        exptimes = [c["metadata"]["android.sensor.exposureTime"] for c in caps]
        readouts = [c["metadata"]["android.sensor.rollingShutterSkew"]
                    for c in caps]
        events = {"gyro": gyro, "cam": zip(starts, exptimes, readouts),
                  "facing": facing}
        with open("%s_events.txt" % NAME, "w") as f:
            f.write(json.dumps(events))

        # Convert the frames to RGB.
        print "Dumping frames"
        frames = []
        for i, c in enumerate(caps):
            img = its.image.convert_capture_to_rgb_image(c)
            frames.append(img)
            its.image.write_image(img, "%s_frame%03d.png" % (NAME, i))

        return events, frames


def procrustes_rotation(X, Y):
    """Performs a Procrustes analysis to conform points in X to Y.

    Procrustes analysis determines a linear transformation (translation,
    reflection, orthogonal rotation and scaling) of the points in Y to best
    conform them to the points in matrix X, using the sum of squared errors
    as the goodness of fit criterion.

    Args:
        X: Target coordinate matrix
        Y: Input coordinate matrix

    Returns:
        The rotation component of the transformation that maps X to Y.
    """
    X0 = (X-X.mean(0)) / numpy.sqrt(((X-X.mean(0))**2.0).sum())
    Y0 = (Y-Y.mean(0)) / numpy.sqrt(((Y-Y.mean(0))**2.0).sum())
    U, _, Vt = numpy.linalg.svd(numpy.dot(X0.T, Y0), full_matrices=False)
    return numpy.dot(Vt.T, U.T)


if __name__ == "__main__":
    main()
