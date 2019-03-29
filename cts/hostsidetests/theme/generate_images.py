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
import sys
import tempfile
import threading
import time
import traceback

from android_device import *
from avd import *
from queue import Queue, Empty


# This dict should contain one entry for every density listed in CDD 7.1.1.3.
CTS_THEME_dict = {
    120: "ldpi",
    160: "mdpi",
    213: "tvdpi",
    240: "hdpi",
    260: "260dpi",
    280: "280dpi",
    300: "300dpi",
    320: "xhdpi",
    340: "340dpi",
    360: "360dpi",
    400: "400dpi",
    420: "420dpi",
    480: "xxhdpi",
    560: "560dpi",
    640: "xxxhdpi",
}

OUT_FILE = "/sdcard/cts-theme-assets.zip"


class ParallelExecutor(threading.Thread):
    def __init__(self, tasks, q):
        threading.Thread.__init__(self)
        self._q = q
        self._tasks = tasks
        self._setup = setup
        self._result = 0

    def run(self):
        try:
            while True:
                config = q.get(block=True, timeout=2)
                for t in self._tasks:
                    try:
                        if t(self._setup, config):
                            self._result += 1
                    except KeyboardInterrupt:
                        raise
                    except:
                        print("Failed to execute thread:", sys.exc_info()[0])
                        traceback.print_exc()
                q.task_done()
        except KeyboardInterrupt:
            raise
        except Empty:
            pass

    def get_result(self):
        return self._result


# pass a function with number of instances to be executed in parallel
# each thread continues until config q is empty.
def execute_parallel(tasks, setup, q, num_threads):
    result = 0
    threads = []
    for i in range(num_threads):
        t = ParallelExecutor(tasks, q)
        t.start()
        threads.append(t)
    for t in threads:
        t.join()
        result += t.get_result()
    return result


def print_adb_result(device, out, err):
    print("device: " + device)
    if out is not None:
        print("out:\n" + out)
    if err is not None:
        print("err:\n" + err)


def do_capture(setup, device_serial):
    (themeApkPath, out_path) = setup

    device = AndroidDevice(device_serial)

    version = device.get_version_codename()
    if version == "REL":
        version = str(device.get_version_sdk())

    density = device.get_density()

    if CTS_THEME_dict[density]:
        density_bucket = CTS_THEME_dict[density]
    else:
        density_bucket = str(density) + "dpi"

    out_file = os.path.join(out_path, os.path.join(version, "%s.zip" % density_bucket))

    device.uninstall_package('android.theme.app')

    (out, err, success) = device.install_apk(themeApkPath)
    if not success:
        print("Failed to install APK on " + device_serial)
        print_adb_result(device_serial, out, err)
        return False

    print("Generating images on " + device_serial + "...")
    try:
        (out, err) = device.run_instrumentation_test(
            "android.theme.app/android.support.test.runner.AndroidJUnitRunner")
    except KeyboardInterrupt:
        raise
    except:
        (out, err) = device.run_instrumentation_test(
            "android.theme.app/android.test.InstrumentationTestRunner")

    # Detect test failure and abort.
    if "FAILURES!!!" in out.split():
        print_adb_result(device_serial, out, err)
        return False

    # Make sure that the run is complete by checking the process itself
    print("Waiting for " + device_serial + "...")
    wait_time = 0
    while device.is_process_alive("android.theme.app"):
        time.sleep(1)
        wait_time = wait_time + 1
        if wait_time > 180:
            print("Timed out")
            break

    time.sleep(10)

    print("Pulling images from " + device_serial + " to " + out_file)
    device.run_adb_command("pull " + OUT_FILE + " " + out_file)
    device.run_adb_command("shell rm -rf " + OUT_FILE)
    return True


def get_emulator_path():
    if 'ANDROID_SDK_ROOT' not in os.environ:
        print('Environment variable ANDROID_SDK_ROOT must point to your Android SDK root.')
        sys.exit(1)

    sdk_path = os.environ['ANDROID_SDK_ROOT']
    if not os.path.isdir(sdk_path):
        print("Failed to find Android SDK at ANDROID_SDK_ROOT: %s" % sdk_path)
        sys.exit(1)

    emu_path = os.path.join(os.path.join(sdk_path, 'tools'), 'emulator')
    if not os.path.isfile(emu_path):
        print("Failed to find emulator within ANDROID_SDK_ROOT: %s" % sdk_path)
        sys.exit(1)

    return emu_path


def start_emulator(name, density):
    emu_path = get_emulator_path()

    # Start emulator for 560dpi, normal screen size.
    test_avd = AVD(name, emu_path)
    test_avd.configure_screen(density, 360, 640)
    test_avd.start()
    try:
        test_avd_device = test_avd.get_device()
        test_avd_device.wait_for_device()
        test_avd_device.wait_for_boot_complete()
        return test_avd
    except:
        test_avd.stop()
        return None


def main(argv):
    if 'ANDROID_BUILD_TOP' not in os.environ or 'ANDROID_HOST_OUT' not in os.environ:
        print('Missing environment variables. Did you run build/envsetup.sh and lunch?')
        sys.exit(1)

    theme_apk = os.path.join(os.environ['ANDROID_HOST_OUT'],
                             'cts/android-cts/testcases/CtsThemeDeviceApp.apk')
    if not os.path.isfile(theme_apk):
        print('Couldn\'t find test APK. Did you run make cts?')
        sys.exit(1)

    out_path = os.path.join(os.environ['ANDROID_BUILD_TOP'],
                            'cts/hostsidetests/theme/assets')
    os.system("mkdir -p %s" % out_path)

    if len(argv) is 2:
        for density in CTS_THEME_dict.keys():
            emulator = start_emulator(argv[1], density)
            result = do_capture(setup=(theme_apk, out_path), device_serial=emulator.get_serial())
            emulator.stop()
            if result:
                print("Generated reference images for %ddpi" % density)
            else:
                print("Failed to generate reference images for %ddpi" % density)
                break
    else:
        tasks = [do_capture]
        setup = (theme_apk, out_path)

        devices = enumerate_android_devices('emulator')

        device_queue = Queue()
        for device in devices:
            device_queue.put(device)

        result = execute_parallel(tasks, setup, device_queue, len(devices))

        if result > 0:
            print('Generated reference images for %(count)d devices' % {"count": result})
        else:
            print('Failed to generate reference images')


if __name__ == '__main__':
    main(sys.argv)
