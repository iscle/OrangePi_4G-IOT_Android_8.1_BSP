"""Experimentally determines a camera's rolling shutter skew.

See the accompanying PDF for instructions on how to use this test.
"""
from __future__ import division
from __future__ import print_function

import argparse
import glob
import math
import os
import sys
import tempfile

import cv2
import its.caps
import its.device
import its.image
import its.objects
import numpy as np

DEBUG = False

# Constants for which direction the camera is facing.
FACING_FRONT = 0
FACING_BACK = 1
FACING_EXTERNAL = 2

# Camera capture defaults.
FPS = 30
WIDTH = 640
HEIGHT = 480
TEST_LENGTH = 1

# Each circle in a cluster must be within this many pixels of some other circle
# in the cluster.
CLUSTER_DISTANCE = 50.0 / HEIGHT
# A cluster must consist of at least this percentage of the total contours for
# it to be allowed into the computation.
MAJORITY_THRESHOLD = 0.7

# Constants to make sure the slope of the fitted line is reasonable.
SLOPE_MIN_THRESHOLD = 0.5
SLOPE_MAX_THRESHOLD = 1.5

# To improve readability of unit conversions.
SEC_TO_NSEC = float(10**9)
MSEC_TO_NSEC = float(10**6)
NSEC_TO_MSEC = 1.0 / float(10**6)


class RollingShutterArgumentParser(object):
    """Parses command line arguments for the rolling shutter test."""

    def __init__(self):
        self.__parser = argparse.ArgumentParser(
                description='Run rolling shutter test')
        self.__parser.add_argument(
                '-d', '--debug',
                action='store_true',
                help='print and write data useful for debugging')
        self.__parser.add_argument(
                '-f', '--fps',
                type=int,
                help='FPS to capture with during the test (defaults to 30)')
        self.__parser.add_argument(
                '-i', '--img_size',
                help=('comma-separated dimensions of captured images (defaults '
                      'to 640x480). Example: --img_size=<width>,<height>'))
        self.__parser.add_argument(
                '-l', '--led_time',
                type=float,
                required=True,
                help=('how many milliseconds each column of the LED array is '
                      'lit for'))
        self.__parser.add_argument(
                '-p', '--panel_distance',
                type=float,
                help='how far the LED panel is from the camera (in meters)')
        self.__parser.add_argument(
                '-r', '--read_dir',
                help=('read existing test data from specified directory.  If '
                      'not specified, new test data is collected from the '
                      'device\'s camera)'))
        self.__parser.add_argument(
                '--device_id',
                help=('device ID for device being tested (can also use '
                      '\'device=<DEVICE ID>\')'))
        self.__parser.add_argument(
                '-t', '--test_length',
                type=int,
                help=('how many seconds the test should run for (defaults to 1 '
                      'second)'))
        self.__parser.add_argument(
                '-o', '--debug_dir',
                help=('write debugging information in a folder in the '
                      'specified directory.  Otherwise, the system\'s default '
                      'location for temporary folders is used.  --debug must '
                      'be specified along with this argument.'))

    def parse_args(self):
        """Returns object containing parsed values from the command line."""
        # Don't show argparse the 'device' flag, since it's in a different
        # format than the others (to maintain CameraITS conventions) and it will
        # complain.
        filtered_args = [arg for arg in sys.argv[1:] if 'device=' not in arg]
        args = self.__parser.parse_args(filtered_args)
        if args.device_id:
            # If argparse format is used, convert it to a format its.device can
            # use later on.
            sys.argv.append('device=%s' % args.device_id)
        return args


def main():
    global DEBUG
    global CLUSTER_DISTANCE

    parser = RollingShutterArgumentParser()
    args = parser.parse_args()

    DEBUG = args.debug
    if not DEBUG and args.debug_dir:
        print('argument --debug_dir requires --debug', file=sys.stderr)
        sys.exit()

    if args.read_dir is None:
        # Collect new data.
        raw_caps, reported_skew = collect_data(args)
        frames = [its.image.convert_capture_to_rgb_image(c) for c in raw_caps]
    else:
        # Load existing data.
        frames, reported_skew = load_data(args.read_dir)

    # Make the cluster distance relative to the height of the image.
    (frame_h, _, _) = frames[0].shape
    CLUSTER_DISTANCE = frame_h * CLUSTER_DISTANCE
    debug_print('Setting cluster distance to %spx.' % CLUSTER_DISTANCE)

    if DEBUG:
        debug_dir = setup_debug_dir(args.debug_dir)
        # Write raw frames.
        for i, img in enumerate(frames):
            its.image.write_image(img, '%s/raw/%03d.png' % (debug_dir, i))
    else:
        debug_dir = None

    avg_shutter_skew, num_frames_used = find_average_shutter_skew(
            frames, args.led_time, debug_dir)
    if debug_dir:
        # Write the reported skew with the raw images, so the directory can also
        # be used to read from.
        with open(debug_dir + '/raw/reported_skew.txt', 'w') as f:
            f.write('%sms\n' % reported_skew)

    if avg_shutter_skew is None:
        print('Could not find usable frames.')
    else:
        print('Device reported shutter skew of %sms.' % reported_skew)
        print('Measured shutter skew is %sms (averaged over %s frames).' %
              (avg_shutter_skew, num_frames_used))


def collect_data(args):
    """Capture a new set of frames from the device's camera.

    Args:
        args: Parsed command line arguments.

    Returns:
        A list of RGB images as numpy arrays.
    """
    fps = args.fps if args.fps else FPS
    if args.img_size:
        w, h = map(int, args.img_size.split(','))
    else:
        w, h = WIDTH, HEIGHT
    test_length = args.test_length if args.test_length else TEST_LENGTH

    with its.device.ItsSession() as cam:
        props = cam.get_camera_properties()
        its.caps.skip_unless(its.caps.manual_sensor(props))
        facing = props['android.lens.facing']
        if facing != FACING_FRONT and facing != FACING_BACK:
            print('Unknown lens facing %s' % facing)
            assert 0

        fmt = {'format': 'yuv', 'width': w, 'height': h}
        s, e, _, _, _ = cam.do_3a(get_results=True, do_af=False)
        req = its.objects.manual_capture_request(s, e)
        req['android.control.aeTargetFpsRange'] = [fps, fps]

        # Convert from milliseconds to nanoseconds.  We only want enough
        # exposure time to saturate approximately one column.
        exposure_time = (args.led_time / 2.0) * MSEC_TO_NSEC
        print('Using exposure time of %sns.' % exposure_time)
        req['android.sensor.exposureTime'] = exposure_time
        req["android.sensor.frameDuration"] = int(SEC_TO_NSEC / fps);

        if args.panel_distance is not None:
            # Convert meters to diopters and use that for the focus distance.
            req['android.lens.focusDistance'] = 1 / args.panel_distance
        print('Starting capture')
        raw_caps = cam.do_capture([req]*fps*test_length, fmt)
        print('Finished capture')

        # Convert from nanoseconds to milliseconds.
        shutter_skews = {c['metadata']['android.sensor.rollingShutterSkew'] *
                          NSEC_TO_MSEC for c in raw_caps}
        # All frames should have same rolling shutter skew.
        assert len(shutter_skews) == 1
        shutter_skew = list(shutter_skews)[0]

        return raw_caps, shutter_skew


def load_data(dir_name):
    """Reads camera frame data from an existing directory.

    Args:
        dir_name: Name of the directory to read data from.

    Returns:
        A list of RGB images as numpy arrays.
    """
    frame_files = glob.glob('%s/*.png' % dir_name)
    frames = []
    for frame_file in sorted(frame_files):
        frames.append(its.image.load_rgb_image(frame_file))
    with open('%s/reported_skew.txt' % dir_name, 'r') as f:
        reported_skew = f.readline()[:-2]  # Strip off 'ms' suffix
    return frames, reported_skew


def find_average_shutter_skew(frames, led_time, debug_dir=None):
    """Finds the average shutter skew using the given frames.

    Frames without enough information will be discarded from the average to
    improve overall accuracy.

    Args:
        frames:    List of RGB images from the camera being tested.
        led_time:  How long a single LED column is lit for (in milliseconds).
        debug_dir: (optional) Directory to write debugging information to.

    Returns:
        The average calculated shutter skew and the number of frames used to
        calculate the average.
    """
    avg_shutter_skew = 0.0
    avg_slope = 0.0
    weight = 0.0
    num_frames_used = 0

    for i, frame in enumerate(frames):
        debug_print('------------------------')
        debug_print('| PROCESSING FRAME %03d |' % i)
        debug_print('------------------------')
        shutter_skew, confidence, slope = calculate_shutter_skew(
                frame, led_time, i, debug_dir=debug_dir)
        if shutter_skew is None:
            debug_print('Skipped frame.')
        else:
            debug_print('Shutter skew is %sms (confidence: %s).' %
                        (shutter_skew, confidence))
            # Use the confidence to weight the average.
            avg_shutter_skew += shutter_skew * confidence
            avg_slope += slope * confidence
            weight += confidence
            num_frames_used += 1

    debug_print('\n')
    if num_frames_used == 0:
        return None, None
    else:
        avg_shutter_skew /= weight
        avg_slope /= weight
        slope_err_str = ('The average slope of the fitted line was too %s '
                         'to get an accurate measurement (slope was %s).  '
                         'Try making the LED panel %s.')
        if avg_slope < SLOPE_MIN_THRESHOLD:
            print(slope_err_str % ('flat', avg_slope, 'slower'),
                  file=sys.stderr)
        elif avg_slope > SLOPE_MAX_THRESHOLD:
            print(slope_err_str % ('steep', avg_slope, 'faster'),
                  file=sys.stderr)
        return avg_shutter_skew, num_frames_used


def calculate_shutter_skew(frame, led_time, frame_num=None, debug_dir=None):
    """Calculates the shutter skew of the camera being used for this test.

    Args:
        frame:     A single RGB image captured by the camera being tested.
        led_time:  How long a single LED column is lit for (in milliseconds).
        frame_num: (optional) Number of the given frame.
        debug_dir: (optional) Directory to write debugging information to.

    Returns:
        The shutter skew (in milliseconds), the confidence in the accuracy of
        the measurement (useful for weighting averages), and the slope of the
        fitted line.
    """
    contours, scratch_img, contour_img, mono_img = find_contours(frame.copy())
    if debug_dir is not None:
        cv2.imwrite('%s/contour/%03d.png' % (debug_dir, frame_num), contour_img)
        cv2.imwrite('%s/mono/%03d.png' % (debug_dir, frame_num), mono_img)

    largest_cluster, cluster_percentage = find_largest_cluster(contours,
                                                               scratch_img)
    if largest_cluster is None:
        debug_print('No majority cluster found.')
        return None, None, None
    elif len(largest_cluster) <= 1:
        debug_print('Majority cluster was too small.')
        return None, None, None
    debug_print('%s points in the largest cluster.' % len(largest_cluster))

    np_cluster = np.array([[c.x, c.y] for c in largest_cluster])
    [vx], [vy], [x0], [y0] = cv2.fitLine(np_cluster, cv2.cv.CV_DIST_L2,
                                         0, 0.01, 0.01)
    slope = vy / vx
    debug_print('Slope is %s.' % slope)
    (frame_h, frame_w, _) = frame.shape
    # Draw line onto scratch frame.
    pt1 = tuple(map(int, (x0 - vx * 1000, y0 - vy * 1000)))
    pt2 = tuple(map(int, (x0 + vx * 1000, y0 + vy * 1000)))
    cv2.line(scratch_img, pt1, pt2, (0, 255, 255), thickness=3)

    # We only need the width of the cluster.
    _, _, cluster_w, _ = find_cluster_bounding_rect(largest_cluster,
                                                    scratch_img)

    num_columns = find_num_columns_spanned(largest_cluster)
    debug_print('%s columns spanned by cluster.' % num_columns)
    # How long it takes for a column to move from the left of the bounding
    # rectangle to the right.
    left_to_right_time = led_time * num_columns
    milliseconds_per_x_pixel = left_to_right_time / cluster_w
    # The distance between the line's intersection at the top of the frame and
    # the intersection at the bottom.
    x_range = frame_h / slope
    shutter_skew = milliseconds_per_x_pixel * x_range
    # If the aspect ratio is different from 4:3 (the aspect ratio of the actual
    # sensor), we need to correct, because it will be cropped.
    shutter_skew *= (float(frame_w) / float(frame_h)) / (4.0 / 3.0)

    if debug_dir is not None:
        cv2.imwrite('%s/scratch/%03d.png' % (debug_dir, frame_num),
                    scratch_img)

    return shutter_skew, cluster_percentage, slope


def find_contours(img):
    """Finds contours in the given image.

    Args:
        img: Image in Android camera RGB format.

    Returns:
        OpenCV-formatted contours, the original image in OpenCV format, a
        thresholded image with the contours drawn on, and a grayscale version of
        the image.
    """
    # Convert to format OpenCV can work with (BGR ordering with byte-ranged
    # values).
    img *= 255
    img = img.astype(np.uint8)
    img = cv2.cvtColor(img, cv2.COLOR_RGB2BGR)

    # Since the LED colors for the panel we're using are red, we can get better
    # contours for the LEDs if we ignore the green and blue channels.  This also
    # makes it so we don't pick up the blue control screen of the LED panel.
    red_img = img[:, :, 2]
    _, thresh = cv2.threshold(red_img, 0, 255, cv2.THRESH_BINARY +
                              cv2.THRESH_OTSU)

    # Remove noise before finding contours by eroding the thresholded image and
    # then re-dilating it.  The size of the kernel represents how many
    # neighboring pixels to consider for the result of a single pixel.
    kernel = np.ones((3, 3), np.uint8)
    opening = cv2.morphologyEx(thresh, cv2.MORPH_OPEN, kernel, iterations=2)

    if DEBUG:
        # Need to convert it back to BGR if we want to draw colored contours.
        contour_img = cv2.cvtColor(opening, cv2.COLOR_GRAY2BGR)
    else:
        contour_img = None
    contours, _ = cv2.findContours(opening,
                                   cv2.cv.CV_RETR_EXTERNAL,
                                   cv2.cv.CV_CHAIN_APPROX_NONE)
    if DEBUG:
        cv2.drawContours(contour_img, contours, -1, (0, 0, 255), thickness=2)
    return contours, img, contour_img, red_img


def convert_to_circles(contours):
    """Converts given contours into circle objects.

    Args:
        contours: Contours generated by OpenCV.

    Returns:
        A list of circles.
    """

    class Circle(object):
        """Holds data to uniquely define a circle."""

        def __init__(self, contour):
            self.x = int(np.mean(contour[:, 0, 0]))
            self.y = int(np.mean(contour[:, 0, 1]))
            # Get diameters of each axis then half it.
            x_r = (np.max(contour[:, 0, 0]) - np.min(contour[:, 0, 0])) / 2.0
            y_r = (np.max(contour[:, 0, 1]) - np.min(contour[:, 0, 1])) / 2.0
            # Average x radius and y radius to get the approximate radius for
            # the given contour.
            self.r = (x_r + y_r) / 2.0
            assert self.r > 0.0

        def distance_to(self, other):
            return (math.sqrt((other.x - self.x)**2 + (other.y - self.y)**2) -
                    self.r - other.r)

        def intersects(self, other):
            return self.distance_to(other) <= 0.0

    return list(map(Circle, contours))


def find_largest_cluster(contours, frame):
    """Finds the largest cluster in the given contours.

    Args:
        contours: Contours generated by OpenCV.
        frame:    For drawing debugging information onto.

    Returns:
        The cluster with the most contours in it and the percentage of all
        contours that the cluster contains.
    """
    clusters = proximity_clusters(contours)

    if not clusters:
        return None, None  # No clusters found.

    largest_cluster = max(clusters, key=len)
    cluster_percentage = len(largest_cluster) / len(contours)

    if cluster_percentage < MAJORITY_THRESHOLD:
        return None, None

    if DEBUG:
        # Draw largest cluster on scratch frame.
        for circle in largest_cluster:
            cv2.circle(frame, (int(circle.x), int(circle.y)), int(circle.r),
                       (0, 255, 0), thickness=2)

    return largest_cluster, cluster_percentage


def proximity_clusters(contours):
    """Sorts the given contours into groups by distance.

    Converts every given contour to a circle and clusters by adding a circle to
    a cluster only if it is close to at least one other circle in the cluster.

    TODO: Make algorithm faster (currently O(n**2)).

    Args:
        contours: Contours generated by OpenCV.

    Returns:
        A list of clusters, where each cluster is a list of the circles
        contained in the cluster.
    """
    circles = convert_to_circles(contours)

    # Use disjoint-set data structure to store assignments.  Start every point
    # in their own cluster.
    cluster_assignments = [-1 for i in range(len(circles))]

    def get_canonical_index(i):
        if cluster_assignments[i] >= 0:
            index = get_canonical_index(cluster_assignments[i])
            # Collapse tree for better runtime.
            cluster_assignments[i] = index
            return index
        else:
            return i

    def get_cluster_size(i):
        return -cluster_assignments[get_canonical_index(i)]

    for i, curr in enumerate(circles):
        close_circles = [j for j, p in enumerate(circles) if i != j and
                         curr.distance_to(p) < CLUSTER_DISTANCE]
        if close_circles:
            # Note: largest_cluster is an index into cluster_assignments.
            largest_cluster = min(close_circles, key=get_cluster_size)
            largest_size = get_cluster_size(largest_cluster)
            curr_index = get_canonical_index(i)
            curr_size = get_cluster_size(i)
            if largest_size > curr_size:
                # largest_cluster is larger than us.
                target_index = get_canonical_index(largest_cluster)
                # Add our cluster size to the bigger one.
                cluster_assignments[target_index] -= curr_size
                # Reroute our group to the bigger one.
                cluster_assignments[curr_index] = target_index
            else:
                # We're the largest (or equal to the largest) cluster.  Reroute
                # all groups to us.
                for j in close_circles:
                    smaller_size = get_cluster_size(j)
                    smaller_index = get_canonical_index(j)
                    if smaller_index != curr_index:
                        # We only want to modify clusters that aren't already in
                        # the current one.

                        # Add the smaller cluster's size to ours.
                        cluster_assignments[curr_index] -= smaller_size
                        # Reroute their group to us.
                        cluster_assignments[smaller_index] = curr_index

    # Convert assignments list into list of clusters.
    clusters_dict = {}
    for i in range(len(cluster_assignments)):
        canonical_index = get_canonical_index(i)
        if canonical_index not in clusters_dict:
            clusters_dict[canonical_index] = []
        clusters_dict[canonical_index].append(circles[i])
    return clusters_dict.values()


def find_cluster_bounding_rect(cluster, scratch_frame):
    """Finds the minimum rectangle that bounds the given cluster.

    The bounding rectangle will always be axis-aligned.

    Args:
        cluster:       Cluster being used to find the bounding rectangle.
        scratch_frame: Image that rectangle is drawn onto for debugging
                       purposes.

    Returns:
        The leftmost and topmost x and y coordinates, respectively, along with
        the width and height of the rectangle.
    """
    avg_distance = find_average_neighbor_distance(cluster)
    debug_print('Average distance between points in largest cluster is %s '
                'pixels.' % avg_distance)

    c_x = min(cluster, key=lambda c: c.x - c.r)
    c_y = min(cluster, key=lambda c: c.y - c.r)
    c_w = max(cluster, key=lambda c: c.x + c.r)
    c_h = max(cluster, key=lambda c: c.y + c.r)

    x = c_x.x - c_x.r - avg_distance
    y = c_y.y - c_y.r - avg_distance
    w = (c_w.x + c_w.r + avg_distance) - x
    h = (c_h.y + c_h.r + avg_distance) - y

    if DEBUG:
        points = np.array([[x, y], [x + w, y], [x + w, y + h], [x, y + h]],
                          np.int32)
        cv2.polylines(scratch_frame, [points], True, (255, 0, 0), thickness=2)

    return x, y, w, h


def find_average_neighbor_distance(cluster):
    """Finds the average distance between every circle and its closest neighbor.

    Args:
        cluster: List of circles

    Returns:
        The average distance.
    """
    avg_distance = 0.0
    for a in cluster:
        closest_point = None
        closest_dist = None
        for b in cluster:
            if a is b:
                continue
            curr_dist = a.distance_to(b)
            if closest_point is None or curr_dist < closest_dist:
                closest_point = b
                closest_dist = curr_dist
        avg_distance += closest_dist
    avg_distance /= len(cluster)
    return avg_distance


def find_num_columns_spanned(circles):
    """Finds how many columns of the LED panel are spanned by the given circles.

    Args:
        circles: List of circles (assumed to be from the LED panel).

    Returns:
        The number of columns spanned.
    """
    if not circles:
        return 0

    def x_intersects(c_a, c_b):
        return abs(c_a.x - c_b.x) < (c_a.r + c_b.r)

    circles = sorted(circles, key=lambda c: c.x)
    last_circle = circles[0]
    num_columns = 1
    for circle in circles[1:]:
        if not x_intersects(circle, last_circle):
            last_circle = circle
            num_columns += 1

    return num_columns


def setup_debug_dir(dir_name=None):
    """Creates a debug directory and required subdirectories.

    Each subdirectory contains images from a different step in the process.

    Args:
        dir_name: The directory to create.  If none is specified, a temp
        directory is created.

    Returns:
        The name of the directory that is used.
    """
    if dir_name is None:
        dir_name = tempfile.mkdtemp()
    else:
        force_mkdir(dir_name)
    print('Saving debugging files to "%s"' % dir_name)
    # For original captured images.
    force_mkdir(dir_name + '/raw', clean=True)
    # For monochrome images.
    force_mkdir(dir_name + '/mono', clean=True)
    # For contours generated from monochrome images.
    force_mkdir(dir_name + '/contour', clean=True)
    # For post-contour debugging information.
    force_mkdir(dir_name + '/scratch', clean=True)
    return dir_name


def force_mkdir(dir_name, clean=False):
    """Creates a directory if it doesn't already exist.

    Args:
        dir_name: Name of the directory to create.
        clean:    (optional) If set to true, cleans image files from the
                  directory (if it already exists).
    """
    if os.path.exists(dir_name):
        if clean:
            for image in glob.glob('%s/*.png' % dir_name):
                os.remove(image)
    else:
        os.makedirs(dir_name)


def debug_print(s, *args, **kwargs):
    """Only prints if the test is running in debug mode."""
    if DEBUG:
        print(s, *args, **kwargs)


if __name__ == '__main__':
    main()
