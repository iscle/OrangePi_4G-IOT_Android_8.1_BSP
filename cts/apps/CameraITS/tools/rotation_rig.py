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

import sys
import time
import hardware as hw
import serial

NUM_ROTATIONS = 10
SLEEP_TIME = 2  # seconds

DATA_DELIMITER = '\r\n'
ROT_RIG_DEVICE = 'relay'
ROT_RIG_VID = '04d8'
ROT_RIG_PID = 'fc73'
ROT_RIG_CHAN = '1'
RELAY_BAUDRATE = 115200
RELAY_COM_SLEEP = 0.05
RELAY_SET_CMD = 'REL'
VALID_RELAY_COMMANDS = ['ON', 'OFF']
VALID_RELAY_CHANNELS = ['1', '2', '3', '4']
SERIAL_SEND_TIMEOUT = 0.02


def cmd_send(vid, pid, cmd_str):
    """Wrapper for sending serial command.

    Args:
        vid:     str; vendor ID
        pid:     str; product ID
        cmd_str: str; value to send to device.
    """
    hw_list = hw.Device(ROT_RIG_DEVICE, vid, pid, '1', '0')
    relay_port = hw_list.get_tty_path('relay')
    relay_ser = serial.Serial(relay_port, RELAY_BAUDRATE,
                              timeout=SERIAL_SEND_TIMEOUT,
                              parity=serial.PARITY_EVEN,
                              stopbits=serial.STOPBITS_ONE,
                              bytesize=serial.EIGHTBITS)
    try:
        relay_ser.write(DATA_DELIMITER)
        time.sleep(RELAY_COM_SLEEP)  # This is critcal for relay.
        relay_ser.write(cmd_str)
        relay_ser.close()
    except ValueError:
        print 'Port %s:%s is not open' % (vid, pid)
        sys.exit()


def set_relay_channel_state(vid, pid, channel, relay_state):
    """Set relay channel and state.

    Args:
        vid:          str; vendor ID
        pid:          str; product ID
        channel:      str; channel number of relay to set. '1', '2', '3', or '4'
        relay_state:  str; either 'ON' or 'OFF'
    Returns:
        None
    """
    if channel in VALID_RELAY_CHANNELS and relay_state in VALID_RELAY_COMMANDS:
        cmd_send(vid, pid, RELAY_SET_CMD + channel + '.' + relay_state + '\r\n')
    else:
        print 'Invalid channel or command, no command sent.'


def main():
    """Main function.

    expected rotator string is vid:pid:ch.
    vid:pid can be found through lsusb on the host.
    ch is hard wired and must be determined from the box.
    """
    for s in sys.argv[1:]:
        if s[:8] == 'rotator=':
            if len(s) > 8:
                rotator_ids = s[8:].split(':')
                if len(rotator_ids) == 3:
                    vid = '0x' + rotator_ids[0]
                    pid = '0x' + rotator_ids[1]
                    ch = rotator_ids[2]
                elif len(rotator_ids) == 1:
                    if rotator_ids[0] in VALID_RELAY_CHANNELS:
                        print ('Using default values %s:%s for VID:PID '
                               'of rotator' % (ROT_RIG_VID, ROT_RIG_PID))
                        vid = '0x' + ROT_RIG_VID
                        pid = '0x' + ROT_RIG_PID
                        ch = rotator_ids[0]
                    elif rotator_ids[0] == 'default':
                        print ('Using default values %s:%s:%s for VID:PID:CH '
                               'of rotator' % (ROT_RIG_VID, ROT_RIG_PID,
                                               ROT_RIG_CHAN))
                        vid = '0x' + ROT_RIG_VID
                        pid = '0x' + ROT_RIG_PID
                        ch = ROT_RIG_CHAN
                    else:
                        print 'Invalid channel: %s' % rotator_ids[0]
                        sys.exit()
                else:
                    err_string = 'Rotator ID (if entered) must be of form: '
                    err_string += 'rotator=VID:PID:CH or rotator=CH'
                    print err_string
                    sys.exit()

    print 'Rotating phone %dx' % NUM_ROTATIONS
    for _ in xrange(NUM_ROTATIONS):
        set_relay_channel_state(vid, pid, ch, 'ON')
        time.sleep(SLEEP_TIME)
        set_relay_channel_state(vid, pid, ch, 'OFF')
        time.sleep(SLEEP_TIME)


if __name__ == '__main__':
    main()
