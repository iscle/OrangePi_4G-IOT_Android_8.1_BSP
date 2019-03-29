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
import re


class Device(object):
    """Create dict object for relay usb connection.

       This class provides an interface to locate lab equipment without encoding
       knowledge of the USB bus topology in the lab equipment device drivers.
    """

    KEY_VID = 'vendor_id'
    KEY_PID = 'product_id'
    KEY_SN = 'serial_no'
    KEY_INF = 'inf'
    KEY_CFG = 'config'
    KEY_NAME = 'name'
    KEY_TTY = 'tty_path'
    KEY_MFG = 'mfg'
    KEY_PRD = 'product'
    KEY_VER = 'version'

    _instance = None

    _USB_DEVICE_SYS_ROOT = '/sys/bus/usb/devices'
    _DEV_ROOT = '/dev'

    _SYS_VENDOR_ID = 'idVendor'
    _SYS_PRODUCT_ID = 'idProduct'
    _SYS_SERIAL_NO = 'serial'
    _INF_CLASS = 'bInterfaceClass'
    _INF_SUB_CLASS = 'bInterfaceSubClass'
    _INF_PROTOCOL = 'bInterfaceProtocol'
    _MFG_STRING = 'manufacturer'
    _PRODUCT_STRING = 'product'
    _VERSION_STRING = 'version'

    _USB_CDC_ACM_CLASS = 0x02
    _USB_CDC_ACM_SUB_CLASS = 0x02
    _USB_CDC_ACM_PROTOCOL = 0x01

    def __init__(self, name, vid, pid, cfg, inf):
        self._device_list = []

        self._build_device(name, vid, pid, cfg, inf)

        self._walk_usb_tree(self._init_device_list_callback, None)

    def __new__(cls, *args, **kwargs):
        # The Device class should be a singleton.  A lab test procedure may
        # use multiple pieces of lab equipment and we do not want to have to
        # create a new instance of the Device for each device.
        if not cls._instance:
            cls._instance = super(Device, cls).__new__(cls, *args, **kwargs)
        return cls._instance

    def __enter__(self):
        return self

    def __exit__(self, exception_type, exception_value, traceback):
        pass

    def _build_device(self, name, vid, pid, cfg, inf):
        """Build relay device information.

        Args:
            name:   device
            vid:    vendor ID
            pid:    product ID
            cfg:    configuration
            inf:    interface

        Returns:
            Nothing
        """
        entry = {}
        entry[self.KEY_NAME] = name
        entry[self.KEY_VID] = int(vid, 16)
        entry[self.KEY_PID] = int(pid, 16)

        # The serial number string is optional in USB and not all devices
        # use it.  The relay devices do not use it then we specify 'None' in
        # the lab configuration file.
        entry[self.KEY_SN] = None
        entry[self.KEY_CFG] = int(cfg)
        entry[self.KEY_INF] = int(inf)
        entry[self.KEY_TTY] = None

        self._device_list.append(entry)

    def _find_lab_device_entry(self, vendor_id, product_id, serial_no):
        """find a device in the lab device list.

        Args:
            vendor_id: unique vendor id for device
            product_id: unique product id for device
            serial_no: serial string for the device (may be None)

        Returns:
            device entry or None
        """
        for device in self._device_list:
            if device[self.KEY_VID] != vendor_id:
                continue
            if device[self.KEY_PID] != product_id:
                continue
            if device[self.KEY_SN] == serial_no:
                return device

        return None

    def _read_sys_attr(self, root, attr):
        """read a sysfs attribute.

        Args:
            root: path of the sysfs directory
            attr: attribute to read

        Returns:
            attribute value or None
        """
        try:
            path = os.path.join(root, attr)
            with open(path) as f:
                return f.readline().rstrip()
        except IOError:
            return None

    def _read_sys_hex_attr(self, root, attr):
        """read a sysfs hexadecimal integer attribute.

        Args:
            root: path of the sysfs directory
            attr: attribute to read

        Returns:
            attribute value or None
        """
        try:
            path = os.path.join(root, attr)
            with open(path) as f:
                return int(f.readline(), 16)
        except IOError:
            return None

    def _is_cdc_acm(self, inf_path):
        """determine if the interface implements the CDC ACM class.

        Args:
            inf_path: directory entry for the inf under /sys/bus/usb/devices

        Returns:
            True if the inf is CDC ACM, false otherwise
        """
        cls = self._read_sys_hex_attr(inf_path, self._INF_CLASS)
        sub_cls = self._read_sys_hex_attr(inf_path, self._INF_SUB_CLASS)
        proto = self._read_sys_hex_attr(inf_path, self._INF_PROTOCOL)
        if self._USB_CDC_ACM_CLASS != cls:
            return False
        if self._USB_CDC_ACM_SUB_CLASS != sub_cls:
            return False
        if self._USB_CDC_ACM_PROTOCOL != proto:
            return False

        return True

    def _read_tty_name(self, dir_entry, inf, cfg):
        """Get the path to the associated tty device.

        Args:
            dir_entry: directory entry for the device under /sys/bus/usb/devices
            inf: Interface number of the device
            cfg: Configuration number of the device

        Returns:
            Path to a tty device or None
        """
        inf_path = os.path.join(self._USB_DEVICE_SYS_ROOT,
                                '%s:%d.%d' % (dir_entry, cfg, inf))

        # first determine if this is a CDC-ACM or USB Serial device.
        if self._is_cdc_acm(inf_path):
            tty_list = os.listdir(os.path.join(inf_path, 'tty'))

            # Each CDC-ACM interface should only have one tty device associated
            # with it so just return the first item in the list.
            return os.path.join(self._DEV_ROOT, tty_list[0])
        else:
            # USB Serial devices have a link to their ttyUSB* device in the inf
            # directory
            tty_re = re.compile(r'ttyUSB\d+$')

            dir_list = os.listdir(inf_path)
            for entry in dir_list:
                if tty_re.match(entry):
                    return os.path.join(self._DEV_ROOT, entry)

        return None

    def _init_device_list_callback(self, _, dir_entry):
        """Callback function used with _walk_usb_tree for device list init.

        Args:
            _: Callback context (unused)
            dir_entry: Directory entry reported by _walk_usb_tree

        """
        path = os.path.join(self._USB_DEVICE_SYS_ROOT, dir_entry)

        # The combination of vendor id, product id, and serial number
        # should be sufficient to uniquely identify each piece of lab
        # equipment.
        vendor_id = self._read_sys_hex_attr(path, self._SYS_VENDOR_ID)
        product_id = self._read_sys_hex_attr(path, self._SYS_PRODUCT_ID)
        serial_no = self._read_sys_attr(path, self._SYS_SERIAL_NO)

        # For each device try to match it with a device entry in the lab
        # configuration.
        device = self._find_lab_device_entry(vendor_id, product_id, serial_no)
        if device:
            # If the device is in the lab configuration then determine
            # which tty device it associated with.
            device[self.KEY_TTY] = self._read_tty_name(dir_entry,
                                                       device[self.KEY_INF],
                                                       device[self.KEY_CFG])

    def _list_all_tty_devices_callback(self, dev_list, dir_entry):
        """Callback for _walk_usb_tree when listing all USB serial devices.

        Args:
            dev_list: Device list to fill
            dir_entry: Directory entry reported by _walk_usb_tree

        """
        dev_path = os.path.join(self._USB_DEVICE_SYS_ROOT, dir_entry)

        # Determine if there are any interfaces in the sys directory for the
        # USB Device.
        inf_re = re.compile(r'\d+-\d+(\.\d+){0,}:(?P<cfg>\d+)\.(?P<inf>\d+)$')
        inf_dir_list = os.listdir(dev_path)

        for inf_entry in inf_dir_list:
            inf_match = inf_re.match(inf_entry)
            if inf_match is None:
                continue

            inf_dict = inf_match.groupdict()
            inf = int(inf_dict['inf'])
            cfg = int(inf_dict['cfg'])

            # Check to see if there is a tty device associated with this
            # interface.
            tty_path = self._read_tty_name(dir_entry, inf, cfg)
            if tty_path is None:
                continue

            # This is a TTY interface, create a dictionary of the relevant
            # sysfs attributes for this device.
            entry = {}
            entry[self.KEY_TTY] = tty_path
            entry[self.KEY_INF] = inf
            entry[self.KEY_CFG] = cfg
            entry[self.KEY_VID] = self._read_sys_hex_attr(dev_path,
                                                          self._SYS_VENDOR_ID)
            entry[self.KEY_PID] = self._read_sys_hex_attr(dev_path,
                                                          self._SYS_PRODUCT_ID)
            entry[self.KEY_SN] = self._read_sys_attr(dev_path,
                                                     self._SYS_SERIAL_NO)
            entry[self.KEY_MFG] = self._read_sys_attr(dev_path,
                                                      self._MFG_STRING)
            entry[self.KEY_PRD] = self._read_sys_attr(dev_path,
                                                      self._PRODUCT_STRING)
            entry[self.KEY_VER] = self._read_sys_attr(dev_path,
                                                      self._VERSION_STRING)

            # If this device is also in the lab device list then add the
            # friendly name for it.
            lab_device = self._find_lab_device_entry(entry[self.KEY_VID],
                                                     entry[self.KEY_PID],
                                                     entry[self.KEY_SN])
            if lab_device is not None:
                entry[self.KEY_NAME] = lab_device[self.KEY_NAME]

            dev_list.append(entry)

    def _walk_usb_tree(self, callback, context):
        """Walk the USB device and locate lab devices.

           Traverse the USB device tree in /sys/bus/usb/devices and inspect each
           device and see if it matches a device in the lab configuration.  If
           it does then get the path to the associated tty device.

        Args:
            callback: Callback to invoke when a USB device is found.
            context: Context variable for callback.

        Returns:
            Nothing
        """
        # Match only devices, exclude interfaces and root hubs
        file_re = re.compile(r'\d+-\d+(\.\d+){0,}$')
        dir_list = os.listdir(self._USB_DEVICE_SYS_ROOT)

        for dir_entry in dir_list:
            if file_re.match(dir_entry):
                callback(context, dir_entry)

    def get_tty_path(self, name):
        """Get the path to the tty device for a given lab device.

        Args:
            name: lab device identifier, e.g. 'rail', or 'bt_trigger'

        Returns:
            Path to the tty device otherwise None
        """
        for dev in self._device_list:
            if dev[self.KEY_NAME] == name and dev[self.KEY_NAME] is not None:
                return dev[self.KEY_TTY]

        return None

    def get_tty_devices(self):
        """Get a list of all USB based tty devices attached to the machine.

        Returns:
            List of dictionaries where each dictionary contains a description of
            the USB TTY device.
        """
        all_dev_list = []
        self._walk_usb_tree(self._list_all_tty_devices_callback, all_dev_list)

        return all_dev_list

