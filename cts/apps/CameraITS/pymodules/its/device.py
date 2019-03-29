# Copyright 2013 The Android Open Source Project
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

import its.error
import os
import os.path
import sys
import re
import json
import time
import unittest
import socket
import subprocess
import hashlib
import numpy
import string

CMD_DELAY = 1  # seconds


class ItsSession(object):
    """Controls a device over adb to run ITS scripts.

    The script importing this module (on the host machine) prepares JSON
    objects encoding CaptureRequests, specifying sets of parameters to use
    when capturing an image using the Camera2 APIs. This class encapsulates
    sending the requests to the device, monitoring the device's progress, and
    copying the resultant captures back to the host machine when done. TCP
    forwarded over adb is the transport mechanism used.

    The device must have CtsVerifier.apk installed.

    Attributes:
        sock: The open socket.
    """

    # Open a connection to localhost:<host_port>, forwarded to port 6000 on the
    # device. <host_port> is determined at run-time to support multiple
    # connected devices.
    IPADDR = '127.0.0.1'
    REMOTE_PORT = 6000
    BUFFER_SIZE = 4096

    # LOCK_PORT is used as a mutex lock to protect the list of forwarded ports
    # among all processes. The script assumes LOCK_PORT is available and will
    # try to use ports between CLIENT_PORT_START and
    # CLIENT_PORT_START+MAX_NUM_PORTS-1 on host for ITS sessions.
    CLIENT_PORT_START = 6000
    MAX_NUM_PORTS = 100
    LOCK_PORT = CLIENT_PORT_START + MAX_NUM_PORTS

    # Seconds timeout on each socket operation.
    SOCK_TIMEOUT = 20.0
    # Additional timeout in seconds when ITS service is doing more complicated
    # operations, for example: issuing warmup requests before actual capture.
    EXTRA_SOCK_TIMEOUT = 5.0

    SEC_TO_NSEC = 1000*1000*1000.0

    PACKAGE = 'com.android.cts.verifier.camera.its'
    INTENT_START = 'com.android.cts.verifier.camera.its.START'
    ACTION_ITS_RESULT = 'com.android.cts.verifier.camera.its.ACTION_ITS_RESULT'
    EXTRA_VERSION = 'camera.its.extra.VERSION'
    CURRENT_ITS_VERSION = '1.0' # version number to sync with CtsVerifier
    EXTRA_CAMERA_ID = 'camera.its.extra.CAMERA_ID'
    EXTRA_RESULTS = 'camera.its.extra.RESULTS'
    ITS_TEST_ACTIVITY = 'com.android.cts.verifier/.camera.its.ItsTestActivity'

    RESULT_PASS = 'PASS'
    RESULT_FAIL = 'FAIL'
    RESULT_NOT_EXECUTED = 'NOT_EXECUTED'
    RESULT_VALUES = {RESULT_PASS, RESULT_FAIL, RESULT_NOT_EXECUTED}
    RESULT_KEY = 'result'
    SUMMARY_KEY = 'summary'

    adb = "adb -d"
    device_id = ""

    # Definitions for some of the common output format options for do_capture().
    # Each gets images of full resolution for each requested format.
    CAP_RAW = {"format":"raw"}
    CAP_DNG = {"format":"dng"}
    CAP_YUV = {"format":"yuv"}
    CAP_JPEG = {"format":"jpeg"}
    CAP_RAW_YUV = [{"format":"raw"}, {"format":"yuv"}]
    CAP_DNG_YUV = [{"format":"dng"}, {"format":"yuv"}]
    CAP_RAW_JPEG = [{"format":"raw"}, {"format":"jpeg"}]
    CAP_DNG_JPEG = [{"format":"dng"}, {"format":"jpeg"}]
    CAP_YUV_JPEG = [{"format":"yuv"}, {"format":"jpeg"}]
    CAP_RAW_YUV_JPEG = [{"format":"raw"}, {"format":"yuv"}, {"format":"jpeg"}]
    CAP_DNG_YUV_JPEG = [{"format":"dng"}, {"format":"yuv"}, {"format":"jpeg"}]

    # Predefine camera props. Save props extracted from the function,
    # "get_camera_properties".
    props = None

    # Initialize the socket port for the host to forward requests to the device.
    # This method assumes localhost's LOCK_PORT is available and will try to
    # use ports between CLIENT_PORT_START and CLIENT_PORT_START+MAX_NUM_PORTS-1
    def __init_socket_port(self):
        NUM_RETRIES = 100
        RETRY_WAIT_TIME_SEC = 0.05

        # Bind a socket to use as mutex lock
        socket_lock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        for i in range(NUM_RETRIES):
            try:
                socket_lock.bind((ItsSession.IPADDR, ItsSession.LOCK_PORT))
                break
            except socket.error:
                if i == NUM_RETRIES - 1:
                    raise its.error.Error(self.device_id,
                                          "acquiring socket lock timed out")
                else:
                    time.sleep(RETRY_WAIT_TIME_SEC)

        # Check if a port is already assigned to the device.
        command = "adb forward --list"
        proc = subprocess.Popen(command.split(), stdout=subprocess.PIPE)
        output, error = proc.communicate()

        port = None
        used_ports = []
        for line in output.split(os.linesep):
            # each line should be formatted as:
            # "<device_id> tcp:<host_port> tcp:<remote_port>"
            forward_info = line.split()
            if len(forward_info) >= 3 and \
               len(forward_info[1]) > 4 and forward_info[1][:4] == "tcp:" and \
               len(forward_info[2]) > 4 and forward_info[2][:4] == "tcp:":
                local_p = int(forward_info[1][4:])
                remote_p = int(forward_info[2][4:])
                if forward_info[0] == self.device_id and \
                   remote_p == ItsSession.REMOTE_PORT:
                    port = local_p
                    break;
                else:
                    used_ports.append(local_p)

        # Find the first available port if no port is assigned to the device.
        if port is None:
            for p in range(ItsSession.CLIENT_PORT_START,
                           ItsSession.CLIENT_PORT_START +
                           ItsSession.MAX_NUM_PORTS):
                if p not in used_ports:
                    # Try to run "adb forward" with the port
                    command = "%s forward tcp:%d tcp:%d" % \
                              (self.adb, p, self.REMOTE_PORT)
                    proc = subprocess.Popen(command.split(),
                                            stdout=subprocess.PIPE,
                                            stderr=subprocess.PIPE)
                    output, error = proc.communicate()

                    # Check if there is no error
                    if error is None or error.find("error") < 0:
                        port = p
                        break

        if port is None:
            raise its.error.Error(self.device_id, " cannot find an available " +
                                  "port")

        # Release the socket as mutex unlock
        socket_lock.close()

        # Connect to the socket
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.connect((self.IPADDR, port))
        self.sock.settimeout(self.SOCK_TIMEOUT)

    # Reboot the device if needed and wait for the service to be ready for
    # connection.
    def __wait_for_service(self):
        # This also includes the optional reboot handling: if the user
        # provides a "reboot" or "reboot=N" arg, then reboot the device,
        # waiting for N seconds (default 30) before returning.
        for s in sys.argv[1:]:
            if s[:6] == "reboot":
                duration = 30
                if len(s) > 7 and s[6] == "=":
                    duration = int(s[7:])
                print "Rebooting device"
                _run("%s reboot" % (self.adb))
                _run("%s wait-for-device" % (self.adb))
                time.sleep(duration)
                print "Reboot complete"

        # Flush logcat so following code won't be misled by previous
        # 'ItsService ready' log.
        _run('%s logcat -c' % (self.adb))
        time.sleep(1)

        # TODO: Figure out why "--user 0" is needed, and fix the problem.
        _run('%s shell am force-stop --user 0 %s' % (self.adb, self.PACKAGE))
        _run(('%s shell am start --user 0 '
              'com.android.cts.verifier/.camera.its.ItsTestActivity '
              '--activity-brought-to-front') % self.adb)
        time.sleep(CMD_DELAY)
        _run(('%s shell am startservice --user 0 -t text/plain '
              '-a %s') % (self.adb, self.INTENT_START))

        # Wait until the socket is ready to accept a connection.
        proc = subprocess.Popen(
                self.adb.split() + ["logcat"],
                stdout=subprocess.PIPE)
        logcat = proc.stdout
        while True:
            line = logcat.readline().strip()
            if line.find('ItsService ready') >= 0:
                break
        proc.kill()

    def __init__(self):
        # Initialize device id and adb command.
        self.device_id = get_device_id()
        self.adb = "adb -s " + self.device_id

        self.__wait_for_service()
        self.__init_socket_port()

        self.__close_camera()
        self.__open_camera()

    def __del__(self):
        if hasattr(self, 'sock') and self.sock:
            self.__close_camera()
            self.sock.close()

    def __enter__(self):
        return self

    def __exit__(self, type, value, traceback):
        return False

    def __read_response_from_socket(self):
        # Read a line (newline-terminated) string serialization of JSON object.
        chars = []
        while len(chars) == 0 or chars[-1] != '\n':
            ch = self.sock.recv(1)
            if len(ch) == 0:
                # Socket was probably closed; otherwise don't get empty strings
                raise its.error.Error('Problem with socket on device side')
            chars.append(ch)
        line = ''.join(chars)
        jobj = json.loads(line)
        # Optionally read a binary buffer of a fixed size.
        buf = None
        if jobj.has_key("bufValueSize"):
            n = jobj["bufValueSize"]
            buf = bytearray(n)
            view = memoryview(buf)
            while n > 0:
                nbytes = self.sock.recv_into(view, n)
                view = view[nbytes:]
                n -= nbytes
            buf = numpy.frombuffer(buf, dtype=numpy.uint8)
        return jobj, buf

    def __open_camera(self):
        # Get the camera ID to open as an argument.
        camera_id = 0
        for s in sys.argv[1:]:
            if s[:7] == "camera=" and len(s) > 7:
                camera_id = int(s[7:])
        cmd = {"cmdName":"open", "cameraId":camera_id}
        self.sock.send(json.dumps(cmd) + "\n")
        data,_ = self.__read_response_from_socket()
        if data['tag'] != 'cameraOpened':
            raise its.error.Error('Invalid command response')

    def __close_camera(self):
        cmd = {"cmdName":"close"}
        self.sock.send(json.dumps(cmd) + "\n")
        data,_ = self.__read_response_from_socket()
        if data['tag'] != 'cameraClosed':
            raise its.error.Error('Invalid command response')

    def do_vibrate(self, pattern):
        """Cause the device to vibrate to a specific pattern.

        Args:
            pattern: Durations (ms) for which to turn on or off the vibrator.
                The first value indicates the number of milliseconds to wait
                before turning the vibrator on. The next value indicates the
                number of milliseconds for which to keep the vibrator on
                before turning it off. Subsequent values alternate between
                durations in milliseconds to turn the vibrator off or to turn
                the vibrator on.

        Returns:
            Nothing.
        """
        cmd = {}
        cmd["cmdName"] = "doVibrate"
        cmd["pattern"] = pattern
        self.sock.send(json.dumps(cmd) + "\n")
        data,_ = self.__read_response_from_socket()
        if data['tag'] != 'vibrationStarted':
            raise its.error.Error('Invalid command response')

    def start_sensor_events(self):
        """Start collecting sensor events on the device.

        See get_sensor_events for more info.

        Returns:
            Nothing.
        """
        cmd = {}
        cmd["cmdName"] = "startSensorEvents"
        self.sock.send(json.dumps(cmd) + "\n")
        data,_ = self.__read_response_from_socket()
        if data['tag'] != 'sensorEventsStarted':
            raise its.error.Error('Invalid command response')

    def get_sensor_events(self):
        """Get a trace of all sensor events on the device.

        The trace starts when the start_sensor_events function is called. If
        the test runs for a long time after this call, then the device's
        internal memory can fill up. Calling get_sensor_events gets all events
        from the device, and then stops the device from collecting events and
        clears the internal buffer; to start again, the start_sensor_events
        call must be used again.

        Events from the accelerometer, compass, and gyro are returned; each
        has a timestamp and x,y,z values.

        Note that sensor events are only produced if the device isn't in its
        standby mode (i.e.) if the screen is on.

        Returns:
            A Python dictionary with three keys ("accel", "mag", "gyro") each
            of which maps to a list of objects containing "time","x","y","z"
            keys.
        """
        cmd = {}
        cmd["cmdName"] = "getSensorEvents"
        self.sock.send(json.dumps(cmd) + "\n")
        timeout = self.SOCK_TIMEOUT + self.EXTRA_SOCK_TIMEOUT
        self.sock.settimeout(timeout)
        data,_ = self.__read_response_from_socket()
        if data['tag'] != 'sensorEvents':
            raise its.error.Error('Invalid command response')
        self.sock.settimeout(self.SOCK_TIMEOUT)
        return data['objValue']

    def get_camera_ids(self):
        """Get a list of camera device Ids that can be opened.

        Returns:
            a list of camera ID string
        """
        cmd = {}
        cmd["cmdName"] = "getCameraIds"
        self.sock.send(json.dumps(cmd) + "\n")
        data,_ = self.__read_response_from_socket()
        if data['tag'] != 'cameraIds':
            raise its.error.Error('Invalid command response')
        return data['objValue']['cameraIdArray']

    def get_camera_properties(self):
        """Get the camera properties object for the device.

        Returns:
            The Python dictionary object for the CameraProperties object.
        """
        cmd = {}
        cmd["cmdName"] = "getCameraProperties"
        self.sock.send(json.dumps(cmd) + "\n")
        data,_ = self.__read_response_from_socket()
        if data['tag'] != 'cameraProperties':
            raise its.error.Error('Invalid command response')
        self.props = data['objValue']['cameraProperties']
        return data['objValue']['cameraProperties']

    def do_3a(self, regions_ae=[[0,0,1,1,1]],
                    regions_awb=[[0,0,1,1,1]],
                    regions_af=[[0,0,1,1,1]],
                    do_ae=True, do_awb=True, do_af=True,
                    lock_ae=False, lock_awb=False,
                    get_results=False,
                    ev_comp=0):
        """Perform a 3A operation on the device.

        Triggers some or all of AE, AWB, and AF, and returns once they have
        converged. Uses the vendor 3A that is implemented inside the HAL.
        Note: do_awb is always enabled regardless of do_awb flag

        Throws an assertion if 3A fails to converge.

        Args:
            regions_ae: List of weighted AE regions.
            regions_awb: List of weighted AWB regions.
            regions_af: List of weighted AF regions.
            do_ae: Trigger AE and wait for it to converge.
            do_awb: Wait for AWB to converge.
            do_af: Trigger AF and wait for it to converge.
            lock_ae: Request AE lock after convergence, and wait for it.
            lock_awb: Request AWB lock after convergence, and wait for it.
            get_results: Return the 3A results from this function.
            ev_comp: An EV compensation value to use when running AE.

        Region format in args:
            Arguments are lists of weighted regions; each weighted region is a
            list of 5 values, [x,y,w,h, wgt], and each argument is a list of
            these 5-value lists. The coordinates are given as normalized
            rectangles (x,y,w,h) specifying the region. For example:
                [[0.0, 0.0, 1.0, 0.5, 5], [0.0, 0.5, 1.0, 0.5, 10]].
            Weights are non-negative integers.

        Returns:
            Five values are returned if get_results is true::
            * AE sensitivity; None if do_ae is False
            * AE exposure time; None if do_ae is False
            * AWB gains (list);
            * AWB transform (list);
            * AF focus position; None if do_af is false
            Otherwise, it returns five None values.
        """
        print "Running vendor 3A on device"
        cmd = {}
        cmd["cmdName"] = "do3A"
        cmd["regions"] = {"ae": sum(regions_ae, []),
                          "awb": sum(regions_awb, []),
                          "af": sum(regions_af, [])}
        cmd["triggers"] = {"ae": do_ae, "af": do_af}
        if lock_ae:
            cmd["aeLock"] = True
        if lock_awb:
            cmd["awbLock"] = True
        if ev_comp != 0:
            cmd["evComp"] = ev_comp
        self.sock.send(json.dumps(cmd) + "\n")

        # Wait for each specified 3A to converge.
        ae_sens = None
        ae_exp = None
        awb_gains = None
        awb_transform = None
        af_dist = None
        converged = False
        while True:
            data,_ = self.__read_response_from_socket()
            vals = data['strValue'].split()
            if data['tag'] == 'aeResult':
                if do_ae:
                    ae_sens, ae_exp = [int(i) for i in vals]
            elif data['tag'] == 'afResult':
                if do_af:
                    af_dist = float(vals[0])
            elif data['tag'] == 'awbResult':
                awb_gains = [float(f) for f in vals[:4]]
                awb_transform = [float(f) for f in vals[4:]]
            elif data['tag'] == '3aConverged':
                converged = True
            elif data['tag'] == '3aDone':
                break
            else:
                raise its.error.Error('Invalid command response')
        if converged and not get_results:
            return None,None,None,None,None
        if (do_ae and ae_sens == None or do_awb and awb_gains == None
                or do_af and af_dist == None or not converged):
            raise its.error.Error('3A failed to converge')
        return ae_sens, ae_exp, awb_gains, awb_transform, af_dist

    def do_capture(self, cap_request,
            out_surfaces=None, reprocess_format=None, repeat_request=None):
        """Issue capture request(s), and read back the image(s) and metadata.

        The main top-level function for capturing one or more images using the
        device. Captures a single image if cap_request is a single object, and
        captures a burst if it is a list of objects.

        The optional repeat_request field can be used to assign a repeating
        request list ran in background for 3 seconds to warm up the capturing
        pipeline before start capturing. The repeat_requests will be ran on a
        640x480 YUV surface without sending any data back. The caller needs to
        make sure the stream configuration defined by out_surfaces and
        repeat_request are valid or do_capture may fail because device does not
        support such stream configuration.

        The out_surfaces field can specify the width(s), height(s), and
        format(s) of the captured image. The formats may be "yuv", "jpeg",
        "dng", "raw", "raw10", "raw12", or "rawStats". The default is a YUV420
        frame ("yuv") corresponding to a full sensor frame.

        Note that one or more surfaces can be specified, allowing a capture to
        request images back in multiple formats (e.g.) raw+yuv, raw+jpeg,
        yuv+jpeg, raw+yuv+jpeg. If the size is omitted for a surface, the
        default is the largest resolution available for the format of that
        surface. At most one output surface can be specified for a given format,
        and raw+dng, raw10+dng, and raw+raw10 are not supported as combinations.

        If reprocess_format is not None, for each request, an intermediate
        buffer of the given reprocess_format will be captured from camera and
        the intermediate buffer will be reprocessed to the output surfaces. The
        following settings will be turned off when capturing the intermediate
        buffer and will be applied when reprocessing the intermediate buffer.
            1. android.noiseReduction.mode
            2. android.edge.mode
            3. android.reprocess.effectiveExposureFactor

        Supported reprocess format are "yuv" and "private". Supported output
        surface formats when reprocessing is enabled are "yuv" and "jpeg".

        Example of a single capture request:

            {
                "android.sensor.exposureTime": 100*1000*1000,
                "android.sensor.sensitivity": 100
            }

        Example of a list of capture requests:

            [
                {
                    "android.sensor.exposureTime": 100*1000*1000,
                    "android.sensor.sensitivity": 100
                },
                {
                    "android.sensor.exposureTime": 100*1000*1000,
                    "android.sensor.sensitivity": 200
                }
            ]

        Examples of output surface specifications:

            {
                "width": 640,
                "height": 480,
                "format": "yuv"
            }

            [
                {
                    "format": "jpeg"
                },
                {
                    "format": "raw"
                }
            ]

        The following variables defined in this class are shortcuts for
        specifying one or more formats where each output is the full size for
        that format; they can be used as values for the out_surfaces arguments:

            CAP_RAW
            CAP_DNG
            CAP_YUV
            CAP_JPEG
            CAP_RAW_YUV
            CAP_DNG_YUV
            CAP_RAW_JPEG
            CAP_DNG_JPEG
            CAP_YUV_JPEG
            CAP_RAW_YUV_JPEG
            CAP_DNG_YUV_JPEG

        If multiple formats are specified, then this function returns multiple
        capture objects, one for each requested format. If multiple formats and
        multiple captures (i.e. a burst) are specified, then this function
        returns multiple lists of capture objects. In both cases, the order of
        the returned objects matches the order of the requested formats in the
        out_surfaces parameter. For example:

            yuv_cap            = do_capture( req1                           )
            yuv_cap            = do_capture( req1,        yuv_fmt           )
            yuv_cap,  raw_cap  = do_capture( req1,        [yuv_fmt,raw_fmt] )
            yuv_caps           = do_capture( [req1,req2], yuv_fmt           )
            yuv_caps, raw_caps = do_capture( [req1,req2], [yuv_fmt,raw_fmt] )

        The "rawStats" format processes the raw image and returns a new image
        of statistics from the raw image. The format takes additional keys,
        "gridWidth" and "gridHeight" which are size of grid cells in a 2D grid
        of the raw image. For each grid cell, the mean and variance of each raw
        channel is computed, and the do_capture call returns two 4-element float
        images of dimensions (rawWidth / gridWidth, rawHeight / gridHeight),
        concatenated back-to-back, where the first iamge contains the 4-channel
        means and the second contains the 4-channel variances. Note that only
        pixels in the active array crop region are used; pixels outside this
        region (for example optical black rows) are cropped out before the
        gridding and statistics computation is performed.

        For the rawStats format, if the gridWidth is not provided then the raw
        image width is used as the default, and similarly for gridHeight. With
        this, the following is an example of a output description that computes
        the mean and variance across each image row:

            {
                "gridHeight": 1,
                "format": "rawStats"
            }

        Args:
            cap_request: The Python dict/list specifying the capture(s), which
                will be converted to JSON and sent to the device.
            out_surfaces: (Optional) specifications of the output image formats
                and sizes to use for each capture.
            reprocess_format: (Optional) The reprocessing format. If not None,
                reprocessing will be enabled.

        Returns:
            An object, list of objects, or list of lists of objects, where each
            object contains the following fields:
            * data: the image data as a numpy array of bytes.
            * width: the width of the captured image.
            * height: the height of the captured image.
            * format: image the format, in [
                        "yuv","jpeg","raw","raw10","raw12","rawStats","dng"].
            * metadata: the capture result object (Python dictionary).
        """
        cmd = {}
        if reprocess_format != None:
            cmd["cmdName"] = "doReprocessCapture"
            cmd["reprocessFormat"] = reprocess_format
        else:
            cmd["cmdName"] = "doCapture"

        if repeat_request is not None and reprocess_format is not None:
            raise its.error.Error('repeating request + reprocessing is not supported')

        if repeat_request is None:
            cmd["repeatRequests"] = []
        elif not isinstance(repeat_request, list):
            cmd["repeatRequests"] = [repeat_request]
        else:
            cmd["repeatRequests"] = repeat_request

        if not isinstance(cap_request, list):
            cmd["captureRequests"] = [cap_request]
        else:
            cmd["captureRequests"] = cap_request
        if out_surfaces is not None:
            if not isinstance(out_surfaces, list):
                cmd["outputSurfaces"] = [out_surfaces]
            else:
                cmd["outputSurfaces"] = out_surfaces
            formats = [c["format"] if "format" in c else "yuv"
                       for c in cmd["outputSurfaces"]]
            formats = [s if s != "jpg" else "jpeg" for s in formats]
        else:
            max_yuv_size = its.objects.get_available_output_sizes(
                    "yuv", self.props)[0]
            formats = ['yuv']
            cmd["outputSurfaces"] = [{"format": "yuv",
                                      "width" : max_yuv_size[0],
                                      "height": max_yuv_size[1]}]
        ncap = len(cmd["captureRequests"])
        nsurf = 1 if out_surfaces is None else len(cmd["outputSurfaces"])
        # Only allow yuv output to multiple targets
        yuv_surfaces = [s for s in cmd["outputSurfaces"] if s["format"]=="yuv"]
        n_yuv = len(yuv_surfaces)
        # Compute the buffer size of YUV targets
        yuv_maxsize_1d = 0
        for s in yuv_surfaces:
            if not ("width" in s and "height" in s):
                if self.props is None:
                    raise its.error.Error('Camera props are unavailable')
                yuv_maxsize_2d = its.objects.get_available_output_sizes(
                    "yuv", self.props)[0]
                yuv_maxsize_1d = yuv_maxsize_2d[0] * yuv_maxsize_2d[1] * 3 / 2
                break
        yuv_sizes = [c["width"]*c["height"]*3/2
                     if "width" in c and "height" in c
                     else yuv_maxsize_1d
                     for c in yuv_surfaces]
        # Currently we don't pass enough metadta from ItsService to distinguish
        # different yuv stream of same buffer size
        if len(yuv_sizes) != len(set(yuv_sizes)):
            raise its.error.Error(
                    'ITS does not support yuv outputs of same buffer size')
        if len(formats) > len(set(formats)):
            if n_yuv != len(formats) - len(set(formats)) + 1:
                raise its.error.Error('Duplicate format requested')

        raw_formats = 0;
        raw_formats += 1 if "dng" in formats else 0
        raw_formats += 1 if "raw" in formats else 0
        raw_formats += 1 if "raw10" in formats else 0
        raw_formats += 1 if "raw12" in formats else 0
        raw_formats += 1 if "rawStats" in formats else 0
        if raw_formats > 1:
            raise its.error.Error('Different raw formats not supported')

        # Detect long exposure time and set timeout accordingly
        longest_exp_time = 0
        for req in cmd["captureRequests"]:
            if "android.sensor.exposureTime" in req and \
                    req["android.sensor.exposureTime"] > longest_exp_time:
                longest_exp_time = req["android.sensor.exposureTime"]

        extended_timeout = longest_exp_time / self.SEC_TO_NSEC + \
                self.SOCK_TIMEOUT
        if repeat_request:
            extended_timeout += self.EXTRA_SOCK_TIMEOUT
        self.sock.settimeout(extended_timeout)

        print "Capturing %d frame%s with %d format%s [%s]" % (
                  ncap, "s" if ncap>1 else "", nsurf, "s" if nsurf>1 else "",
                  ",".join(formats))
        self.sock.send(json.dumps(cmd) + "\n")

        # Wait for ncap*nsurf images and ncap metadata responses.
        # Assume that captures come out in the same order as requested in
        # the burst, however individual images of different formats can come
        # out in any order for that capture.
        nbufs = 0
        bufs = {"raw":[], "raw10":[], "raw12":[],
                "rawStats":[], "dng":[], "jpeg":[]}
        yuv_bufs = {size:[] for size in yuv_sizes}
        mds = []
        widths = None
        heights = None
        while nbufs < ncap*nsurf or len(mds) < ncap:
            jsonObj,buf = self.__read_response_from_socket()
            if jsonObj['tag'] in ['jpegImage', 'rawImage', \
                    'raw10Image', 'raw12Image', 'rawStatsImage', 'dngImage'] \
                    and buf is not None:
                fmt = jsonObj['tag'][:-5]
                bufs[fmt].append(buf)
                nbufs += 1
            elif jsonObj['tag'] == 'yuvImage':
                buf_size = numpy.product(buf.shape)
                yuv_bufs[buf_size].append(buf)
                nbufs += 1
            elif jsonObj['tag'] == 'captureResults':
                mds.append(jsonObj['objValue']['captureResult'])
                outputs = jsonObj['objValue']['outputs']
                widths = [out['width'] for out in outputs]
                heights = [out['height'] for out in outputs]
            else:
                # Just ignore other tags
                None
        rets = []
        for j,fmt in enumerate(formats):
            objs = []
            for i in range(ncap):
                obj = {}
                obj["width"] = widths[j]
                obj["height"] = heights[j]
                obj["format"] = fmt
                obj["metadata"] = mds[i]
                if fmt == 'yuv':
                    buf_size = widths[j] * heights[j] * 3 / 2
                    obj["data"] = yuv_bufs[buf_size][i]
                else:
                    obj["data"] = bufs[fmt][i]
                objs.append(obj)
            rets.append(objs if ncap>1 else objs[0])
        self.sock.settimeout(self.SOCK_TIMEOUT)
        return rets if len(rets)>1 else rets[0]

def get_device_id():
    """ Return the ID of the device that the test is running on.

    Return the device ID provided in the command line if it's connected. If no
    device ID is provided in the command line and there is only one device
    connected, return the device ID by parsing the result of "adb devices".
    Also, if the environment variable ANDROID_SERIAL is set, use it as device
    id. When both ANDROID_SERIAL and device argument present, device argument
    takes priority.

    Raise an exception if no device is connected; or the device ID provided in
    the command line is not connected; or no device ID is provided in the
    command line or environment variable and there are more than 1 device
    connected.

    Returns:
        Device ID string.
    """
    device_id = None

    # Check if device id is set in env
    if "ANDROID_SERIAL" in os.environ:
        device_id = os.environ["ANDROID_SERIAL"]

    for s in sys.argv[1:]:
        if s[:7] == "device=" and len(s) > 7:
            device_id = str(s[7:])

    # Get a list of connected devices
    devices = []
    command = "adb devices"
    proc = subprocess.Popen(command.split(), stdout=subprocess.PIPE)
    output, error = proc.communicate()
    for line in output.split(os.linesep):
        device_info = line.split()
        if len(device_info) == 2 and device_info[1] == "device":
            devices.append(device_info[0])

    if len(devices) == 0:
        raise its.error.Error("No device is connected!")
    elif device_id is not None and device_id not in devices:
        raise its.error.Error(device_id + " is not connected!")
    elif device_id is None and len(devices) >= 2:
        raise its.error.Error("More than 1 device are connected. " +
                "Use device=<device_id> to specify a device to test.")
    elif len(devices) == 1:
        device_id = devices[0]

    return device_id

def report_result(device_id, camera_id, results):
    """Send a pass/fail result to the device, via an intent.

    Args:
        device_id: The ID string of the device to report the results to.
        camera_id: The ID string of the camera for which to report pass/fail.
        results: a dictionary contains all ITS scenes as key and result/summary
                 of current ITS run. See test_report_result unit test for
                 an example.
    Returns:
        Nothing.
    """
    adb = "adb -s " + device_id

    # Start ItsTestActivity to prevent flaky
    cmd = "%s shell am start %s --activity-brought-to-front" % (adb, ItsSession.ITS_TEST_ACTIVITY)
    _run(cmd)

    # Validate/process results argument
    for scene in results:
        result_key = ItsSession.RESULT_KEY
        summary_key = ItsSession.SUMMARY_KEY
        if result_key not in results[scene]:
            raise its.error.Error('ITS result not found for ' + scene)
        if results[scene][result_key] not in ItsSession.RESULT_VALUES:
            raise its.error.Error('Unknown ITS result for %s: %s' % (
                    scene, results[result_key]))
        if summary_key in results[scene]:
            device_summary_path = "/sdcard/its_camera%s_%s.txt" % (
                    camera_id, scene)
            _run("%s push %s %s" % (
                    adb, results[scene][summary_key], device_summary_path))
            results[scene][summary_key] = device_summary_path

    json_results = json.dumps(results)
    cmd = "%s shell am broadcast -a %s --es %s %s --es %s %s --es %s \'%s\'" % (
            adb, ItsSession.ACTION_ITS_RESULT,
            ItsSession.EXTRA_VERSION, ItsSession.CURRENT_ITS_VERSION,
            ItsSession.EXTRA_CAMERA_ID, camera_id,
            ItsSession.EXTRA_RESULTS, json_results)
    if len(cmd) > 4095:
        print "ITS command string might be too long! len:", len(cmd)
    _run(cmd)

def get_device_fingerprint(device_id):
    """ Return the Build FingerPrint of the device that the test is running on.

    Returns:
        Device Build Fingerprint string.
    """
    device_bfp = None

    # Get a list of connected devices

    com = ('adb -s %s shell getprop | grep ro.build.fingerprint' % device_id)
    proc = subprocess.Popen(com.split(), stdout=subprocess.PIPE)
    output, error = proc.communicate()
    assert error is None

    lst = string.split( \
            string.replace( \
            string.replace( \
            string.replace(output,
            '\n', ''), '[', ''), ']', ''), \
            ' ')

    if lst[0].find('ro.build.fingerprint') != -1:
        device_bfp = lst[1]

    return device_bfp

def _run(cmd):
    """Replacement for os.system, with hiding of stdout+stderr messages.
    """
    with open(os.devnull, 'wb') as devnull:
        subprocess.check_call(
                cmd.split(), stdout=devnull, stderr=subprocess.STDOUT)

class __UnitTest(unittest.TestCase):
    """Run a suite of unit tests on this module.
    """

    """
    # TODO: this test currently needs connected device to pass
    #       Need to remove that dependency before enabling the test
    def test_report_result(self):
        device_id = get_device_id()
        camera_id = "1"
        result_key = ItsSession.RESULT_KEY
        results = {"scene0":{result_key:"PASS"},
                   "scene1":{result_key:"PASS"},
                   "scene2":{result_key:"PASS"},
                   "scene3":{result_key:"PASS"},
                   "sceneNotExist":{result_key:"FAIL"}}
        report_result(device_id, camera_id, results)
    """

if __name__ == '__main__':
    unittest.main()

