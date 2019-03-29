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

import os
import os.path
import re
import subprocess
import sys
import tempfile
import time

import its.device
import numpy

SCENE_NAME = 'sensor_fusion'
SKIP_RET_CODE = 101
TEST_NAME = 'test_sensor_fusion'
TEST_DIR = os.path.join(os.getcwd(), 'tests', SCENE_NAME)
W, H = 640, 480

# For finding best correlation shifts from test output logs.
SHIFT_RE = re.compile('^Best correlation of [0-9.]+ at shift of [-0-9.]+ms$')
# For finding lines that indicate socket issues in failed test runs.
SOCKET_FAIL_RE = re.compile(
        r'.*((socket\.(error|timeout))|(Problem with socket)).*')

FPS = 30
TEST_LENGTH = 7  # seconds


def main():
    """Run test_sensor_fusion NUM_RUNS times.

    Save intermediate files and produce a summary/report of the results.

    Script should be run from the top-level CameraITS directory.

    Command line arguments:
        camera:      Camera(s) to be tested. Use comma to separate multiple
                     camera Ids. Ex: 'camera=0,1' or 'camera=1'
        device:      Device id for adb
        fps:         FPS to capture with during the test
        img_size:    Comma-separated dimensions of captured images (defaults to
                     640x480). Ex: 'img_size=<width>,<height>'
        num_runs:    Number of times to repeat the test
        rotator:     String for rotator id in for vid:pid:ch
        test_length: How long the test should run for (in seconds)
        tmp_dir:     Location of temp directory for output files
    """

    camera_id = '0'
    fps = str(FPS)
    img_size = '%s,%s' % (W, H)
    num_runs = 1
    rotator_ids = 'default'
    test_length = str(TEST_LENGTH)
    tmp_dir = None
    for s in sys.argv[1:]:
        if s[:7] == 'camera=' and len(s) > 7:
            camera_id = s[7:]
        if s[:4] == 'fps=' and len(s) > 4:
            fps = s[4:]
        elif s[:9] == 'img_size=' and len(s) > 9:
            img_size = s[9:]
        elif s[:9] == 'num_runs=' and len(s) > 9:
            num_runs = int(s[9:])
        elif s[:8] == 'rotator=' and len(s) > 8:
            rotator_ids = s[8:]
        elif s[:12] == 'test_length=' and len(s) > 12:
            test_length = s[12:]
        elif s[:8] == 'tmp_dir=' and len(s) > 8:
            tmp_dir = s[8:]

    if camera_id not in ['0', '1']:
        print 'Need to specify camera 0 or 1'
        sys.exit()

    # Make output directories to hold the generated files.
    tmpdir = tempfile.mkdtemp(dir=tmp_dir)
    print 'Saving output files to:', tmpdir, '\n'

    device_id = its.device.get_device_id()
    device_id_arg = 'device=' + device_id
    print 'Testing device ' + device_id

    camera_id_arg = 'camera=' + camera_id
    if rotator_ids:
        rotator_id_arg = 'rotator=' + rotator_ids
    print 'Preparing to run sensor_fusion on camera', camera_id

    img_size_arg = 'img_size=' + img_size
    print 'Image dimensions are ' + 'x'.join(img_size.split(','))

    fps_arg = 'fps=' + fps
    test_length_arg = 'test_length=' + test_length

    os.mkdir(os.path.join(tmpdir, camera_id))

    # Run test "num_runs" times, capturing stdout and stderr.
    num_pass = 0
    num_fail = 0
    num_skip = 0
    num_socket_fails = 0
    num_non_socket_fails = 0
    shift_list = []
    for i in range(num_runs):
        os.mkdir(os.path.join(tmpdir, camera_id, SCENE_NAME+'_'+str(i)))
        cmd = 'python tools/rotation_rig.py rotator=%s' % rotator_ids
        subprocess.Popen(cmd.split())
        cmd = ['python', os.path.join(TEST_DIR, TEST_NAME+'.py'),
               device_id_arg, camera_id_arg, rotator_id_arg, img_size_arg,
               fps_arg, test_length_arg]
        outdir = os.path.join(tmpdir, camera_id, SCENE_NAME+'_'+str(i))
        outpath = os.path.join(outdir, TEST_NAME+'_stdout.txt')
        errpath = os.path.join(outdir, TEST_NAME+'_stderr.txt')
        t0 = time.time()
        with open(outpath, 'w') as fout, open(errpath, 'w') as ferr:
            retcode = subprocess.call(
                    cmd, stderr=ferr, stdout=fout, cwd=outdir)
        t1 = time.time()

        if retcode == 0:
            retstr = 'PASS '
            time_shift = find_time_shift(outpath)
            shift_list.append(time_shift)
            num_pass += 1
        elif retcode == SKIP_RET_CODE:
            retstr = 'SKIP '
            num_skip += 1
        else:
            retstr = 'FAIL '
            time_shift = find_time_shift(outpath)
            if time_shift is None:
                if is_socket_fail(errpath):
                    num_socket_fails += 1
                else:
                    num_non_socket_fails += 1
            else:
                shift_list.append(time_shift)
            num_fail += 1
        msg = '%s %s/%s [%.1fs]' % (retstr, SCENE_NAME, TEST_NAME, t1-t0)
        print msg

    if num_pass == 1:
        print 'Best shift is %sms' % shift_list[0]
    elif num_pass > 1:
        shift_arr = numpy.array(shift_list)
        mean, std = numpy.mean(shift_arr), numpy.std(shift_arr)
        print 'Best shift mean is %sms with std. dev. of %sms' % (mean, std)

    pass_percentage = 100*float(num_pass+num_skip)/num_runs
    print '%d / %d tests passed (%.1f%%)' % (num_pass+num_skip,
                                             num_runs,
                                             pass_percentage)

    if num_socket_fails != 0:
        print '%s failure(s) due to socket issues' % num_socket_fails
    if num_non_socket_fails != 0:
        print '%s non-socket failure(s)' % num_non_socket_fails


def is_socket_fail(err_file_path):
    """Search through a test run's stderr log for any mention of socket issues.

    Args:
        err_file_path: File path for stderr logs to search through

    Returns:
        True if the test run failed and it was due to socket issues. Otherwise,
        False.
    """
    return find_matching_line(err_file_path, SOCKET_FAIL_RE) is not None


def find_time_shift(out_file_path):
    """Search through a test run's stdout log for the best time shift.

    Args:
        out_file_path: File path for stdout logs to search through

    Returns:
        The best time shift, if one is found. Otherwise, returns None.
    """
    line = find_matching_line(out_file_path, SHIFT_RE)
    if line is None:
        return None
    else:
        words = line.split(' ')
        # Get last word and strip off 'ms\n' before converting to a float.
        return float(words[-1][:-3])


def find_matching_line(file_path, regex):
    """Search each line in the file at 'file_path' for a line matching 'regex'.

    Args:
        file_path: File path for file being searched
        regex:     Regex used to match against lines

    Returns:
        The first matching line. If none exists, returns None.
    """
    with open(file_path) as f:
        for line in f:
            if regex.match(line):
                return line
    return None


if __name__ == '__main__':
    main()

