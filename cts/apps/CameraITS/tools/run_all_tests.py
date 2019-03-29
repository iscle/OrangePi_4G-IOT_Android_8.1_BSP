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

import copy
import os
import os.path
import tempfile
import subprocess
import time
import sys

import its.caps
import its.device
from its.device import ItsSession

CHART_DELAY = 1  # seconds
FACING_EXTERNAL = 2
NUM_TRYS = 2
SKIP_RET_CODE = 101  # note this must be same as tests/scene*/test_*


def evaluate_socket_failure(err_file_path):
    """Determine if test fails due to socket FAIL."""
    socket_fail = False
    with open(err_file_path, 'r') as ferr:
        for line in ferr:
            if (line.find('socket.error') != -1 or
                line.find('Problem with socket') != -1):
                socket_fail = True
    return socket_fail


def skip_sensor_fusion():
    """Determine if sensor fusion test is skipped for this camera."""

    skip_code = SKIP_RET_CODE
    with ItsSession() as cam:
        props = cam.get_camera_properties()
        if (its.caps.sensor_fusion(props) and its.caps.manual_sensor(props) and
                props['android.lens.facing'] is not FACING_EXTERNAL):
            skip_code = None
    return skip_code


def main():
    """Run all the automated tests, saving intermediate files, and producing
    a summary/report of the results.

    Script should be run from the top-level CameraITS directory.

    Command line arguments:
        camera:  the camera(s) to be tested. Use comma to separate multiple
                 camera Ids. Ex: "camera=0,1" or "camera=1"
        device:  device id for adb
        scenes:  the test scene(s) to be executed. Use comma to separate
                 multiple scenes. Ex: "scenes=scene0,scene1" or
                 "scenes=0,1,sensor_fusion" (sceneX can be abbreviated by X
                 where X is a integer)
        chart:   [Experimental] another android device served as test chart
                 display. When this argument presents, change of test scene
                 will be handled automatically. Note that this argument
                 requires special physical/hardware setup to work and may not
                 work on all android devices.
        result:  Device ID to forward results to (in addition to the device
                 that the tests are running on).
        rot_rig: [Experimental] ID of the rotation rig being used (formatted as
                 "<vendor ID>:<product ID>:<channel #>" or "default")
        tmp_dir: location of temp directory for output files
        skip_scene_validation: force skip scene validation. Used when test scene
                 is setup up front and don't require tester validation.
    """

    # Not yet mandated tests
    NOT_YET_MANDATED = {
        "scene0": [
            "test_jitter",
            "test_burst_capture"
            ],
        "scene1": [
            "test_ae_af",
            "test_ae_precapture_trigger",
            "test_crop_region_raw",
            "test_ev_compensation_advanced",
            "test_ev_compensation_basic",
            "test_yuv_plus_jpeg"
            ],
        "scene2": [
            "test_num_faces",
            ],
        "scene3": [
            "test_3a_consistency",
            "test_lens_movement_reporting",
            "test_lens_position"
            ],
        "scene4": [],
        "scene5": [],
        "sensor_fusion": []
    }

    all_scenes = ["scene0", "scene1", "scene2", "scene3", "scene4", "scene5",
                  "sensor_fusion"]

    auto_scenes = ["scene0", "scene1", "scene2", "scene3", "scene4"]

    scene_req = {
        "scene0": None,
        "scene1": "A grey card covering at least the middle 30% of the scene",
        "scene2": "A picture containing human faces",
        "scene3": "The ISO 12233 chart",
        "scene4": "A specific test page of a circle covering at least the "
                  "middle 50% of the scene. See CameraITS.pdf section 2.3.4 "
                  "for more details",
        "scene5": "Capture images with a diffuser attached to the camera. See "
                  "CameraITS.pdf section 2.3.4 for more details",
        "sensor_fusion": "Rotating checkboard pattern. See "
                         "sensor_fusion/SensorFusion.pdf for detailed "
                         "instructions.\nNote that this test will be skipped "
                         "on devices not supporting REALTIME camera timestamp."
    }
    scene_extra_args = {
        "scene5": ["doAF=False"]
    }

    camera_ids = []
    scenes = []
    chart_host_id = None
    result_device_id = None
    rot_rig_id = None
    tmp_dir = None
    skip_scene_validation = False
    for s in sys.argv[1:]:
        if s[:7] == "camera=" and len(s) > 7:
            camera_ids = s[7:].split(',')
        elif s[:7] == "scenes=" and len(s) > 7:
            scenes = s[7:].split(',')
        elif s[:6] == 'chart=' and len(s) > 6:
            chart_host_id = s[6:]
        elif s[:7] == 'result=' and len(s) > 7:
            result_device_id = s[7:]
        elif s[:8] == 'rot_rig=' and len(s) > 8:
            rot_rig_id = s[8:]  # valid values: 'default' or '$VID:$PID:$CH'
            # The default '$VID:$PID:$CH' is '04d8:fc73:1'
        elif s[:8] == 'tmp_dir=' and len(s) > 8:
            tmp_dir = s[8:]
        elif s == 'skip_scene_validation':
            skip_scene_validation = True

    auto_scene_switch = chart_host_id is not None
    merge_result_switch = result_device_id is not None

    # Run through all scenes if user does not supply one
    possible_scenes = auto_scenes if auto_scene_switch else all_scenes
    if not scenes:
        scenes = possible_scenes
    else:
        # Validate user input scene names
        valid_scenes = True
        temp_scenes = []
        for s in scenes:
            if s in possible_scenes:
                temp_scenes.append(s)
            else:
                try:
                    # Try replace "X" to "sceneX"
                    scene_str = "scene" + s
                    if scene_str not in possible_scenes:
                        valid_scenes = False
                        break
                    temp_scenes.append(scene_str)
                except ValueError:
                    valid_scenes = False
                    break

        if not valid_scenes:
            print "Unknown scene specifiied:", s
            assert False
        scenes = temp_scenes

    # Initialize test results
    results = {}
    result_key = ItsSession.RESULT_KEY
    for s in all_scenes:
        results[s] = {result_key: ItsSession.RESULT_NOT_EXECUTED}

    # Make output directories to hold the generated files.
    topdir = tempfile.mkdtemp(dir=tmp_dir)
    subprocess.call(['chmod', 'g+rx', topdir])
    print "Saving output files to:", topdir, "\n"

    device_id = its.device.get_device_id()
    device_id_arg = "device=" + device_id
    print "Testing device " + device_id

    # Sanity Check for devices
    device_bfp = its.device.get_device_fingerprint(device_id)
    assert device_bfp is not None

    if auto_scene_switch:
        chart_host_bfp = its.device.get_device_fingerprint(chart_host_id)
        assert chart_host_bfp is not None

    if merge_result_switch:
        result_device_bfp = its.device.get_device_fingerprint(result_device_id)
        assert_err_msg = ('Cannot merge result to a different build, from '
                          '%s to %s' % (device_bfp, result_device_bfp))
        assert device_bfp == result_device_bfp, assert_err_msg

    # user doesn't specify camera id, run through all cameras
    if not camera_ids:
        camera_ids_path = os.path.join(topdir, "camera_ids.txt")
        out_arg = "out=" + camera_ids_path
        cmd = ['python',
               os.path.join(os.getcwd(), "tools/get_camera_ids.py"), out_arg,
               device_id_arg]
        cam_code = subprocess.call(cmd, cwd=topdir)
        assert cam_code == 0
        with open(camera_ids_path, "r") as f:
            for line in f:
                camera_ids.append(line.replace('\n', ''))

    print "Running ITS on camera: %s, scene %s" % (camera_ids, scenes)

    if auto_scene_switch:
        # merge_result only supports run_parallel_tests
        if merge_result_switch and camera_ids[0] == '1':
            print 'Skip chart screen'
            time.sleep(1)
        else:
            print 'Waking up chart screen: ', chart_host_id
            screen_id_arg = ('screen=%s' % chart_host_id)
            cmd = ['python', os.path.join(os.environ['CAMERA_ITS_TOP'], 'tools',
                                          'wake_up_screen.py'), screen_id_arg]
            wake_code = subprocess.call(cmd)
            assert wake_code == 0

    for camera_id in camera_ids:
        # Loop capturing images until user confirm test scene is correct
        camera_id_arg = "camera=" + camera_id
        print "Preparing to run ITS on camera", camera_id

        os.mkdir(os.path.join(topdir, camera_id))
        for d in scenes:
            os.mkdir(os.path.join(topdir, camera_id, d))

        for scene in scenes:
            skip_code = None
            tests = [(s[:-3], os.path.join("tests", scene, s))
                     for s in os.listdir(os.path.join("tests", scene))
                     if s[-3:] == ".py" and s[:4] == "test"]
            tests.sort()

            summary = "Cam" + camera_id + " " + scene + "\n"
            numpass = 0
            numskip = 0
            num_not_mandated_fail = 0
            numfail = 0
            validate_switch = True
            if scene_req[scene] is not None:
                out_path = os.path.join(topdir, camera_id, scene+".jpg")
                out_arg = "out=" + out_path
                if scene == 'sensor_fusion':
                    skip_code = skip_sensor_fusion()
                    if rot_rig_id or skip_code == SKIP_RET_CODE:
                        validate_switch = False
                if skip_scene_validation:
                    validate_switch = False
                cmd = None
                if auto_scene_switch:
                    if (not merge_result_switch or
                            (merge_result_switch and camera_ids[0] == '0')):
                        scene_arg = 'scene=' + scene
                        cmd = ['python',
                               os.path.join(os.getcwd(), 'tools/load_scene.py'),
                               scene_arg, screen_id_arg]
                    else:
                        time.sleep(CHART_DELAY)
                else:
                    # Skip scene validation under certain conditions
                    if validate_switch and not merge_result_switch:
                        scene_arg = 'scene=' + scene_req[scene]
                        extra_args = scene_extra_args.get(scene, [])
                        cmd = ['python',
                               os.path.join(os.getcwd(),
                                            'tools/validate_scene.py'),
                               camera_id_arg, out_arg,
                               scene_arg, device_id_arg] + extra_args
                if cmd is not None:
                    valid_scene_code = subprocess.call(cmd, cwd=topdir)
                    assert valid_scene_code == 0
            print "Start running ITS on camera %s, %s" % (camera_id, scene)
            # Run each test, capturing stdout and stderr.
            for (testname, testpath) in tests:
                if auto_scene_switch:
                    if merge_result_switch and camera_ids[0] == '0':
                        # Send an input event to keep the screen not dimmed.
                        # Since we are not using camera of chart screen, FOCUS event
                        # should do nothing but keep the screen from dimming.
                        # The "sleep after x minutes of inactivity" display setting
                        # determines how long this command can keep screen bright.
                        # Setting it to something like 30 minutes should be enough.
                        cmd = ('adb -s %s shell input keyevent FOCUS'
                               % chart_host_id)
                        subprocess.call(cmd.split())
                t0 = time.time()
                for num_try in range(NUM_TRYS):
                    outdir = os.path.join(topdir, camera_id, scene)
                    outpath = os.path.join(outdir, testname+'_stdout.txt')
                    errpath = os.path.join(outdir, testname+'_stderr.txt')
                    if scene == 'sensor_fusion':
                        if skip_code is not SKIP_RET_CODE:
                            if rot_rig_id:
                                print 'Rotating phone w/ rig %s' % rot_rig_id
                                rig = ('python tools/rotation_rig.py rotator=%s' %
                                       rot_rig_id)
                                subprocess.Popen(rig.split())
                            else:
                                print 'Rotate phone 15s as shown in SensorFusion.pdf'
                        else:
                            test_code = skip_code
                    if skip_code is not SKIP_RET_CODE:
                        cmd = ['python', os.path.join(os.getcwd(), testpath)]
                        cmd += sys.argv[1:] + [camera_id_arg]
                        with open(outpath, 'w') as fout, open(errpath, 'w') as ferr:
                            test_code = subprocess.call(
                                cmd, stderr=ferr, stdout=fout, cwd=outdir)
                    if test_code == 0 or test_code == SKIP_RET_CODE:
                        break
                    else:
                        socket_fail = evaluate_socket_failure(errpath)
                        if socket_fail:
                            if num_try != NUM_TRYS-1:
                                print ' Retry %s/%s' % (scene, testname)
                            else:
                                break
                        else:
                            break
                t1 = time.time()

                test_failed = False
                if test_code == 0:
                    retstr = "PASS "
                    numpass += 1
                elif test_code == SKIP_RET_CODE:
                    retstr = "SKIP "
                    numskip += 1
                elif test_code != 0 and testname in NOT_YET_MANDATED[scene]:
                    retstr = "FAIL*"
                    num_not_mandated_fail += 1
                else:
                    retstr = "FAIL "
                    numfail += 1
                    test_failed = True

                msg = "%s %s/%s [%.1fs]" % (retstr, scene, testname, t1-t0)
                print msg
                msg_short = "%s %s [%.1fs]" % (retstr, testname, t1-t0)
                if test_failed:
                    summary += msg_short + "\n"

            if numskip > 0:
                skipstr = ", %d test%s skipped" % (
                    numskip, "s" if numskip > 1 else "")
            else:
                skipstr = ""

            test_result = "\n%d / %d tests passed (%.1f%%)%s" % (
                numpass + num_not_mandated_fail, len(tests) - numskip,
                100.0 * float(numpass + num_not_mandated_fail) /
                (len(tests) - numskip)
                if len(tests) != numskip else 100.0, skipstr)
            print test_result

            if num_not_mandated_fail > 0:
                msg = "(*) tests are not yet mandated"
                print msg

            summary_path = os.path.join(topdir, camera_id, scene, "summary.txt")
            with open(summary_path, "w") as f:
                f.write(summary)

            passed = numfail == 0
            results[scene][result_key] = (ItsSession.RESULT_PASS if passed
                                          else ItsSession.RESULT_FAIL)
            results[scene][ItsSession.SUMMARY_KEY] = summary_path

        print "Reporting ITS result to CtsVerifier"
        if merge_result_switch:
            # results are modified by report_result
            results_backup = copy.deepcopy(results)
            its.device.report_result(result_device_id, camera_id, results_backup)

        its.device.report_result(device_id, camera_id, results)

    if auto_scene_switch:
        if merge_result_switch:
            print 'Skip shutting down chart screen'
        else:
            print 'Shutting down chart screen: ', chart_host_id
            screen_id_arg = ('screen=%s' % chart_host_id)
            cmd = ['python', os.path.join(os.environ['CAMERA_ITS_TOP'], 'tools',
                                          'turn_off_screen.py'), screen_id_arg]
            screen_off_code = subprocess.call(cmd)
            assert screen_off_code == 0

            print 'Shutting down DUT screen: ', device_id
            screen_id_arg = ('screen=%s' % device_id)
            cmd = ['python', os.path.join(os.environ['CAMERA_ITS_TOP'], 'tools',
                                          'turn_off_screen.py'), screen_id_arg]
            screen_off_code = subprocess.call(cmd)
            assert screen_off_code == 0

    print "ITS tests finished. Please go back to CtsVerifier and proceed"

if __name__ == '__main__':
    main()
