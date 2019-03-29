#!/usr/bin/python3
#
# Copyright (C) 2017 The Android Open Source Project
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

import functools
import math
import socket
import subprocess
import sys
import tempfile

from android_device import *


def find_free_port():
    s = socket.socket()
    s.bind(('', 0))
    return int(s.getsockname()[1])


class AVD(object):
    def __init__(self, name, emu_path):
        self._name = name
        self._emu_path = emu_path
        self._opts = ''
        self._adb_name = None
        self._emu_proc = None

    def start(self):
        if self._emu_proc:
            raise Exception('Emulator already running')

        port_adb = find_free_port()
        port_tty = find_free_port()
        # -no-window might be useful here
        emu_cmd = "%s -avd %s %s-ports %d,%d" \
                  % (self._emu_path, self._name, self._opts, port_adb, port_tty)
        print(emu_cmd)

        emu_proc = subprocess.Popen(emu_cmd.split(" "), bufsize=-1, stdout=subprocess.PIPE,
                                    stderr=subprocess.PIPE)

        # The emulator ought to be starting now.
        self._adb_name = "emulator-%d" % (port_tty - 1)
        self._emu_proc = emu_proc

    def stop(self):
        if not self._emu_proc:
            raise Exception('Emulator not currently running')
        self._emu_proc.kill()
        (out, err) = self._emu_proc.communicate()
        self._emu_proc = None
        return out, err

    def get_serial(self):
        if not self._emu_proc:
            raise Exception('Emulator not currently running')
        return self._adb_name

    def get_device(self):
        if not self._emu_proc:
            raise Exception('Emulator not currently running')
        return AndroidDevice(self._adb_name)

    def configure_screen(self, density, width_dp, height_dp):
        width_px = int(math.ceil(width_dp * density / 1600) * 10)
        height_px = int(math.ceil(height_dp * density / 1600) * 10)
        self._opts = "-prop qemu.sf.lcd_density=%d -skin %dx%d " % (density, width_px, height_px)
