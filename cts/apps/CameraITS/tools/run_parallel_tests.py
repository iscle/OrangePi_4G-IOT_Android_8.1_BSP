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

from multiprocessing import Process
import os
import os.path
import tempfile
import subprocess
import time
import string
import sys
import textwrap
import its.device

def main():
    """
        device0: device serial number for camera 0 testing
        device1: device serial number for camera 1 testing
        chart: [Experimental] another android device served as test chart
               display. When this argument presents, change of test scene will
               be handled automatically. Note that this argument requires
               special physical/hardware setup to work and may not work on
               all android devices.
    """
    auto_scenes = ["0", "1", "2", "3", "4"]

    device0_id = None
    device1_id = None
    chart_host_id = None
    scenes = None

    for s in sys.argv[1:]:
        if s[:8] == "device0=" and len(s) > 8:
            device0_id = s[8:]
        elif s[:8] == "device1=" and len(s) > 8:
            device1_id = s[8:]
        elif s[:7] == "scenes=" and len(s) > 7:
            scenes = s[7:].split(',')
        elif s[:6] == 'chart=' and len(s) > 6:
            chart_host_id = s[6:]

    #Sanity Check for camera 0 & 1 parallel testing
    device0_bfp = its.device.get_device_fingerprint(device0_id)
    device1_bfp = its.device.get_device_fingerprint(device1_id)
    chart_host_bfp = its.device.get_device_fingerprint(chart_host_id)

    assert device0_bfp is not None, "Can not connect to the device0"
    assert device0_bfp == device1_bfp, \
        "Not the same build: %s vs %s" % (device0_bfp, device1_bfp)
    assert chart_host_bfp is not None, "Can not connect to the chart device"

    if scenes is None:
        scenes = auto_scenes

    print ">>> Start the at %s" % time.strftime('%Y/%m/%d %H:%M:%S')
    for scene in scenes:
        cmds = []
        cmds.append(build_cmd(device0_id, chart_host_id, device1_id, 0, scene))
        cmds.append(build_cmd(device1_id, chart_host_id, device0_id, 1, scene))

        procs = []
        for cmd in cmds:
            print "running: ", cmd
            proc = Process(target=run_cmd, args=(cmd,))
            procs.append(proc)
            proc.start()

        for proc in procs:
            proc.join()

    shut_down_device_screen(device0_id)
    shut_down_device_screen(device1_id)
    shut_down_device_screen(chart_host_id)

    print ">>> End the test at %s" % time.strftime('%Y/%m/%d %H:%M:%S')

def build_cmd(device_id, chart_host_id, result_device_id, camera_id, scene_id):
    """ Create a cmd list for run_all_tests.py
    Return a list of cmd & parameters
    """
    cmd = ['python',
            os.path.join(os.getcwd(),'tools/run_all_tests.py'),
            'device=%s' % device_id,
            'result=%s' % result_device_id,
            'camera=%i' % camera_id,
            'scenes=%s' % scene_id]

    # scene 5 is not automated and no chart is needed
    if scene_id != '5':
        cmd.append('chart=%s' % chart_host_id)
    else:
        cmd.append('skip_scene_validation')

    return cmd

def run_cmd(cmd):
    """ Run shell command on a subprocess
    """
    proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    output, error = proc.communicate()
    print output, error

def shut_down_device_screen(device_id):
    """ Shut Down Device Screen

    Returns:
        None
    """

    print 'Shutting down chart screen: ', device_id
    screen_id_arg = ('screen=%s' % device_id)
    cmd = ['python', os.path.join(os.environ['CAMERA_ITS_TOP'], 'tools',
                                  'turn_off_screen.py'), screen_id_arg]
    retcode = subprocess.call(cmd)
    assert retcode == 0

if __name__ == '__main__':
    main()
