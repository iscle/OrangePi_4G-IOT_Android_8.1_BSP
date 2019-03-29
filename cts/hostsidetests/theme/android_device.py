#!/usr/bin/python3
#
# Copyright (C) 2015 The Android Open Source Project
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
#

import os
import re
import subprocess
import sys
import threading
import time

from subprocess import PIPE


# class for running android device from python
# it will fork the device processor
class AndroidDevice(object):
    def __init__(self, serial):
        self._serial = serial

    def run_adb_command(self, cmd, timeout=None):
        adb_cmd = "adb -s %s %s" % (self._serial, cmd)
        print(adb_cmd)

        adb_process = subprocess.Popen(args=adb_cmd.split(), bufsize=-1, stderr=PIPE, stdout=PIPE)
        (out, err) = adb_process.communicate(timeout=timeout)
        return out.decode('utf-8').strip(), err.decode('utf-8').strip()

    def run_shell_command(self, cmd):
        return self.run_adb_command("shell %s" % cmd)

    def wait_for_device(self, timeout=30):
        return self.run_adb_command('wait-for-device', timeout)

    def wait_for_prop(self, key, value, timeout=30):
        boot_complete = False
        attempts = 0
        wait_period = 1
        while not boot_complete and (attempts*wait_period) < timeout:
            (out, err) = self.run_shell_command("getprop %s" % key)
            if out == value:
                boot_complete = True
            else:
                time.sleep(wait_period)
                attempts += 1
        if not boot_complete:
            print("%s not set to %s within timeout!" % (key, value))
        return boot_complete

    def wait_for_service(self, name, timeout=30):
        service_found = False
        attempts = 0
        wait_period = 1
        while not service_found and (attempts*wait_period) < timeout:
            (output, err) = self.run_shell_command("service check %s" % name)
            if 'not found' not in output:
                service_found = True
            else:
                time.sleep(wait_period)
                attempts += 1
        if not service_found:
            print("Service '%s' not found within timeout!" % name)
        return service_found

    def wait_for_boot_complete(self, timeout=60):
        return self.wait_for_prop('dev.bootcomplete', '1', timeout)

    def install_apk(self, apk_path):
        self.wait_for_service('package')
        (out, err) = self.run_adb_command("install -r -d -g %s" % apk_path)
        result = out.split()
        return out, err, "Success" in result

    def uninstall_package(self, package):
        self.wait_for_service('package')
        (out, err) = self.run_adb_command("uninstall %s" % package)
        result = out.split()
        return "Success" in result

    def run_instrumentation_test(self, option):
        self.wait_for_service('activity')
        return self.run_shell_command("am instrument -w --no-window-animation %s" % option)

    def is_process_alive(self, process_name):
        (out, err) = self.run_shell_command("ps")
        names = out.split()
        # very lazy implementation as it does not filter out things like uid
        # should work mostly unless processName is too simple to overlap with
        # uid. So only use name like com.android.xyz
        return process_name in names

    def get_version_sdk(self):
        return int(self.run_shell_command("getprop ro.build.version.sdk")[0])

    def get_version_codename(self):
        return self.run_shell_command("getprop ro.build.version.codename")[0].strip()

    def get_density(self):
        if "emulator" in self._serial:
            return int(self.run_shell_command("getprop qemu.sf.lcd_density")[0])
        else:
            return int(self.run_shell_command("getprop ro.sf.lcd_density")[0])

    def get_orientation(self):
        return int(self.run_shell_command("dumpsys | grep SurfaceOrientation")[0].split()[1])


def enumerate_android_devices(require_prefix=''):
    devices = subprocess.check_output(["adb", "devices"])
    if not devices:
        return []

    devices = devices.decode('UTF-8').split('\n')[1:]
    device_list = []

    for device in devices:
        if device is not "" and device.startswith(require_prefix):
            info = device.split('\t')
            if info[1] == "device":
                device_list.append(info[0])

    return device_list
