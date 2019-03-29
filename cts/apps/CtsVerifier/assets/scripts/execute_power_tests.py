#!/usr/bin/python

# Copyright (C) 2014 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import logging
import os.path
import select
import sys
import time
import collections
import socket
import gflags as flags  # http://code.google.com/p/python-gflags/
import pkgutil
import threading
import Queue
import traceback
import math
import bisect
from bisect import bisect_left

"""
scipy, numpy and matplotlib are python packages that can be installed
from: http://www.scipy.org/

"""
import scipy
import matplotlib.pyplot as plt

# let this script know about the power monitor implementations
sys.path = [os.path.basename(__file__)] + sys.path
available_monitors = [
    name
    for _, name, _ in pkgutil.iter_modules(
        [os.path.join(os.path.dirname(__file__), "power_monitors")])
    if not name.startswith("_")]

APK = os.path.join(os.path.dirname(__file__), "..", "CtsVerifier.apk")

FLAGS = flags.FLAGS

# DELAY_SCREEN_OFF is the number of seconds to wait for baseline state
DELAY_SCREEN_OFF = 20.0

# whether to log data collected to a file for each sensor run:
LOG_DATA_TO_FILE = True

logging.getLogger().setLevel(logging.ERROR)


def do_import(name):
    """import a module by name dynamically"""
    mod = __import__(name)
    components = name.split(".")
    for comp in components[1:]:
        mod = getattr(mod, comp)
    return mod

class PowerTestException(Exception):
    """
    Definition of specialized Exception class for CTS power tests
    """
    def __init__(self, message):
        self._error_message = message
    def __str__(self):
        return self._error_message

class PowerTest:
    """Class to run a suite of power tests. This has methods for obtaining
    measurements from the power monitor (through the driver) and then
    processing it to determine baseline and AP suspend state and
    measure ampere draw of various sensors.
    Ctrl+C causes a keyboard interrupt exception which terminates the test."""

    # Thresholds for max allowed power usage per sensor tested
    # TODO: Accel, Mag and Gyro have no maximum power specified in the CDD;
    # the following numbers are bogus and will be replaced soon by what
    # the device reports (from Sensor.getPower())
    MAX_ACCEL_AMPS = 0.08  # Amps
    MAX_MAG_AMPS = 0.08  # Amps
    MAX_GYRO_AMPS = 0.08  # Amps
    MAX_SIGMO_AMPS = 0.08  # Amps

    # TODO: The following numbers for step counter, etc must be replaced by
    # the numbers specified in CDD for low-power sensors. The expected current
    # draw must be computed from the specified power and the voltage used to
    # power the device (specified from a config file).
    MAX_STEP_COUNTER_AMPS = 0.08  # Amps
    MAX_STEP_DETECTOR_AMPS = 0.08  # Amps
    # The variable EXPECTED_AMPS_VARIATION_HALF_RANGE denotes the expected
    # variation of  the ampere measurements
    # around the mean value at baseline state. i.e. we expect most of the
    # ampere measurements at baseline state to vary around the mean by
    # between +/- of the number below
    EXPECTED_AMPS_VARIATION_HALF_RANGE = 0.0005
    # The variable THRESHOLD_BASELINE_SAMPLES_FRACTION denotes the minimum fraction of samples that must
    # be in the range of variation defined by EXPECTED_AMPS_VARIATION_HALF_RANGE
    # around the mean baseline for us to decide that the phone has settled into
    # its baseline state
    THRESHOLD_BASELINE_SAMPLES_FRACTION = 0.86
    # The variable MAX_PERCENTILE_AP_SCREEN_OFF_AMPS denotes the maximum ampere
    # draw that the device can consume when it has gone to suspend state with
    # one or more sensors registered and batching samples (screen and AP are
    # off in this case)
    MAX_PERCENTILE_AP_SCREEN_OFF_AMPS = 0.030  # Amps
    # The variable PERCENTILE_MAX_AP_SCREEN_OFF denotes the fraction of ampere
    # measurements that must be below the specified maximum amperes
    # MAX_PERCENTILE_AP_SCREEN_OFF_AMPS for us to decide that the phone has
    # reached suspend state.
    PERCENTILE_MAX_AP_SCREEN_OFF = 0.95
    DOMAIN_NAME = "/android/cts/powertest"
    # SAMPLE_COUNT_NOMINAL denotes the typical number of measurements of amperes
    # to collect from the power monitor
    SAMPLE_COUNT_NOMINAL = 1000
    # RATE_NOMINAL denotes the nominal frequency at which ampere measurements
    # are taken from the monsoon power monitor
    RATE_NOMINAL = 100
    ENABLE_PLOTTING = False

    REQUEST_EXTERNAL_STORAGE = "EXTERNAL STORAGE?"
    REQUEST_EXIT = "EXIT"
    REQUEST_RAISE = "RAISE %s %s"
    REQUEST_USER_RESPONSE = "USER RESPONSE %s"
    REQUEST_SET_TEST_RESULT = "SET TEST RESULT %s %s %s"
    REQUEST_SENSOR_SWITCH = "SENSOR %s %s"
    REQUEST_SENSOR_AVAILABILITY = "SENSOR? %s"
    REQUEST_SCREEN_OFF = "SCREEN OFF"
    REQUEST_SHOW_MESSAGE = "MESSAGE %s"

    NEGATIVE_AMPERE_ERROR_MESSAGE = (
        "Negative ampere draw measured, possibly due to power "
        "supply from USB cable. Check the setup of device and power "
        "monitor to make sure that the device is not connected "
        "to machine via USB directly. The device should be "
        "connected to the USB slot in the power monitor. It is okay "
        "to change the wiring when the test is in progress.")


    def __init__(self, max_baseline_amps):
        """
        Args:
            max_baseline_amps: The maximum value of baseline amperes
                    that we expect the device to consume at baseline state.
                    This can be different between models of phones.
        """
        power_monitors = do_import("power_monitors.%s" % FLAGS.power_monitor)
        testid = time.strftime("%d_%m_%Y__%H__%M_%S")
        self._power_monitor = power_monitors.Power_Monitor(log_file_id = testid)
        self._tcp_connect_port = 0  # any available port
        print ("Establishing connection to device...")
        self.setUsbEnabled(True)
        status = self._power_monitor.GetStatus()
        self._native_hz = status["sampleRate"] * 1000
        # the following describes power test being run (i.e on what sensor
        # and what type of test. This is used for logging.
        self._current_test = "None"
        self._external_storage = self.executeOnDevice(PowerTest.REQUEST_EXTERNAL_STORAGE)
        self._max_baseline_amps = max_baseline_amps

    def __del__(self):
        self.finalize()

    def finalize(self):
        """To be called upon termination of host connection to device"""
        if self._tcp_connect_port > 0:
            # tell device side to exit connection loop, and remove the forwarding
            # connection
            self.executeOnDevice(PowerTest.REQUEST_EXIT, reportErrors = False)
            self.executeLocal("adb forward --remove tcp:%d" % self._tcp_connect_port)
        self._tcp_connect_port = 0
        if self._power_monitor:
            self._power_monitor.Close()
            self._power_monitor = None

    def _send(self, msg, report_errors = True):
        """Connect to the device, send the given command, and then disconnect"""
        if self._tcp_connect_port == 0:
            # on first attempt to send a command, connect to device via any open port number,
            # forwarding that port to a local socket on the device via adb
            logging.debug("Seeking port for communication...")
            # discover an open port
            dummysocket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            dummysocket.bind(("localhost", 0))
            (_, self._tcp_connect_port) = dummysocket.getsockname()
            dummysocket.close()
            assert(self._tcp_connect_port > 0)

            status = self.executeLocal("adb forward tcp:%d localabstract:%s" %
                                       (self._tcp_connect_port, PowerTest.DOMAIN_NAME))
            # If the status !=0, then the host machine is unable to
            # forward requests to client over adb. Ending the test and logging error message
            # to the console on the host.
            self.endTestIfLostConnection(
                status != 0,
                "Unable to forward requests to client over adb")
            logging.info("Forwarding requests over local port %d",
                         self._tcp_connect_port)

        link = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

        try:
            logging.debug("Connecting to device...")
            link.connect(("localhost", self._tcp_connect_port))
            logging.debug("Connected.")
        except socket.error as serr:
            print "Socket connection error: ", serr
            print "Finalizing and exiting the test"
            self.endTestIfLostConnection(
                report_errors,
                "Unable to communicate with device: connection refused")
        except:
            print "Non socket-related exception at this block in _send(); re-raising now."
            raise
        logging.debug("Sending '%s'", msg)
        link.sendall(msg)
        logging.debug("Getting response...")
        response = link.recv(4096)
        logging.debug("Got response '%s'", response)
        link.close()
        return response

    def queryDevice(self, query):
        """Post a yes/no query to the device, return True upon successful query, False otherwise"""
        logging.info("Querying device with '%s'", query)
        return self._send(query) == "OK"

    # TODO: abstract device communication (and string commands) into its own class
    def executeOnDevice(self, cmd, reportErrors = True):
        """Execute a (string) command on the remote device"""
        return self._send(cmd, reportErrors)

    def executeLocal(self, cmd, check_status = True):
        """execute a shell command locally (on the host)"""
        from subprocess import call
        status = call(cmd.split(" "))
        if status != 0 and check_status:
            logging.error("Failed to execute \"%s\"", cmd)
        else:
            logging.debug("Executed \"%s\"", cmd)
        return status

    def reportErrorRaiseExceptionIf(self, condition, msg):
        """Report an error condition to the device if condition is True.
        Will raise an exception on the device if condition is True.
        Args:
            condition: If true, this reports error
            msg: Message related to exception
        Raises:
            A PowerTestException encapsulating the message provided in msg
        """
        if condition:
            try:
                logging.error("Exiting on error: %s" % msg)
                self.executeOnDevice(PowerTest.REQUEST_RAISE % (self._current_test, msg),
                                     reportErrors = True)
            except:
                logging.error("Unable to communicate with device to report "
                              "error: %s" % msg)
                self.finalize()
                sys.exit(msg)
            raise PowerTestException(msg)

    def endTestIfLostConnection(self, lost_connection, error_message):
        """
        This function ends the test if lost_connection was true,
        which indicates that the connection to the device was lost.
        Args:
            lost_connection: boolean variable, if True it indicates that
                connection to device was lost and the test must be terminated.
            error_message: String to print to the host console before exiting the test
                (if lost_connection is True)
        Returns:
            None.
        """
        if lost_connection:
            logging.error(error_message)
            self.finalize()
            sys.exit(error_message)

    def setUsbEnabled(self, enabled, verbose = True):
        if enabled:
            val = 1
        else:
            val = 0
        self._power_monitor.SetUsbPassthrough(val)
        tries = 0

        # Sometimes command won't go through first time, particularly if immediately after a data
        # collection, so allow for retries
        # TODO: Move this retry mechanism to the power monitor driver.
        status = self._power_monitor.GetStatus()
        while status is None and tries < 5:
            tries += 1
            time.sleep(2.0)
            logging.error("Retrying get status call...")
            self._power_monitor.StopDataCollection()
            self._power_monitor.SetUsbPassthrough(val)
            status = self._power_monitor.GetStatus()

        if enabled:
            if verbose:
                print("...USB enabled, waiting for device")
            self.executeLocal("adb wait-for-device")
            if verbose:
                print("...device online")
        else:
            if verbose:
                logging.info("...USB disabled")
        # re-establish port forwarding
        if enabled and self._tcp_connect_port > 0:
            status = self.executeLocal("adb forward tcp:%d localabstract:%s" %
                                       (self._tcp_connect_port, PowerTest.DOMAIN_NAME))
            self.reportErrorRaiseExceptionIf(status != 0, msg = "Unable to forward requests to client over adb")

    def computeBaselineState(self, measurements):
        """
        Args:
            measurements: List of floats containing ampere draw measurements
                taken from the monsoon power monitor.
                Must be atleast 100 measurements long
        Returns:
            A tuple (isBaseline, mean_current) where isBaseline is a
            boolean that is True only if the baseline state for the phone is
            detected. mean_current is an estimate of the average baseline
            current for the device, which is valid only if baseline state is
            detected (if not, it is set to -1).
        """

        # Looks at the measurements to see if it is in baseline state
        if len(measurements) < 100:
            print(
                "Need at least 100 measurements to determine if baseline state has"
                " been reached")
            return (False, -1)

        # Assumption: At baseline state, the power profile is Gaussian distributed
        # with low-variance around the mean current draw.
        # Ideally we should find the mode from a histogram bin to find an estimated mean.
        # Assuming here that the median is very close to this value; later we check that the
        # variance of the samples is low enough to validate baseline.
        sorted_measurements = sorted(measurements)
        number_measurements = len(measurements)
        if not number_measurements % 2:
            median_measurement = (sorted_measurements[(number_measurements - 1) / 2] +
                                  sorted_measurements[(number_measurements + 1) / 2]) / 2
        else:
            median_measurement = sorted_measurements[number_measurements / 2]

        # Assume that at baseline state, a large fraction of power measurements
        # are within +/- EXPECTED_AMPS_VARIATION_HALF_RANGE milliAmperes of
        # the average baseline current. Find all such measurements in the
        # sorted measurement vector.
        left_index = (
            bisect_left(
                sorted_measurements,
                median_measurement -
                PowerTest.EXPECTED_AMPS_VARIATION_HALF_RANGE))
        right_index = (
            bisect_left(
                sorted_measurements,
                median_measurement +
                PowerTest.EXPECTED_AMPS_VARIATION_HALF_RANGE))

        average_baseline_amps = scipy.mean(
            sorted_measurements[left_index: (right_index - 1)])

        detected_baseline = True
        # We enforce that a fraction of more than 'THRESHOLD_BASELINE_SAMPLES_FRACTION'
        # of samples must be within +/- EXPECTED_AMPS_VARIATION_HALF_RANGE
        # milliAmperes of the mean baseline current, which we have estimated as
        # the median.
        if ((right_index - left_index) < PowerTest.THRESHOLD_BASELINE_SAMPLES_FRACTION * len(
                measurements)):
            detected_baseline = False

        # We check for the maximum limit of the expected baseline
        if median_measurement > self._max_baseline_amps:
            detected_baseline = False
        if average_baseline_amps < 0:
            print PowerTest.NEGATIVE_AMPERE_ERROR_MESSAGE
            detected_baseline = False

        print("%s baseline state" % ("Could detect" if detected_baseline else "Could NOT detect"))
        print(
            "median amps = %f, avg amps = %f, fraction of good samples = %f" %
            (median_measurement, average_baseline_amps,
             float(right_index - left_index) / len(measurements)))
        if PowerTest.ENABLE_PLOTTING:
            plt.plot(measurements)
            plt.show()
            print("To continue test, please close the plot window manually.")
        return (detected_baseline, average_baseline_amps)

    def isApInSuspendState(self, measurements_amps, nominal_max_amps, test_percentile):
        """
        This function detects AP suspend and display off state of phone
        after a sensor has been registered.

        Because the power profile can be very different between sensors and
        even across builds, it is difficult to specify a tight threshold for
        mean current draw or mandate that the power measurements must have low
        variance. We use a criteria that allows for a certain fraction of
        peaks in power spectrum and checks that test_percentile fraction of
        measurements must be below the specified value nominal_max_amps
        Args:
            measurements_amps: amperes draw measurements from power monitor
            test_percentile: the fraction of measurements we require to be below
                             a specified amps value
            nominal_max_amps: the specified value of the max current draw
        Returns:
            returns a boolean which is True if and only if the AP suspend and
            display off state is detected
        """
        count_good = len([m for m in measurements_amps if m < nominal_max_amps])
        count_negative = len([m for m in measurements_amps if m < 0])
        if count_negative > 0:
            print PowerTest.NEGATIVE_AMPERE_ERROR_MESSAGE
            return False;
        return count_good > test_percentile * len(measurements_amps)

    def getBaselineState(self):
        """This function first disables all sensors, then collects measurements
        through the power monitor and continuously evaluates if baseline state
        is reached. Once baseline state is detected, it returns a tuple with
        status information. If baseline is not detected in a preset maximum
        number of trials, it returns as well.

        Returns:
            Returns a tuple (isBaseline, mean_current) where isBaseline is a
            boolean that is True only if the baseline state for the phone is
            detected. mean_current is an estimate of the average baseline current
            for the device, which is valid only if baseline state is detected
            (if not, it is set to -1)
        """
        self.setPowerOn("ALL", False)
        self.setUsbEnabled(False)
        print("Waiting %d seconds for baseline state" % DELAY_SCREEN_OFF)
        time.sleep(DELAY_SCREEN_OFF)

        MEASUREMENT_DURATION_SECONDS_BASELINE_DETECTION = 5  # seconds
        NUMBER_MEASUREMENTS_BASELINE_DETECTION = (
            PowerTest.RATE_NOMINAL *
            MEASUREMENT_DURATION_SECONDS_BASELINE_DETECTION)
        NUMBER_MEASUREMENTS_BASELINE_VERIFICATION = (
            NUMBER_MEASUREMENTS_BASELINE_DETECTION * 5)
        MAX_TRIALS = 50

        collected_baseline_measurements = False

        for tries in xrange(MAX_TRIALS):
            print("Trial number %d of %d..." % (tries, MAX_TRIALS))
            measurements = self.collectMeasurements(
                NUMBER_MEASUREMENTS_BASELINE_DETECTION, PowerTest.RATE_NOMINAL,
                verbose = False)
            if self.computeBaselineState(measurements)[0] is True:
                collected_baseline_measurements = True
                break

        if collected_baseline_measurements:
            print("Verifying baseline state over a longer interval "
                  "in order to double check baseline state")
            measurements = self.collectMeasurements(
                NUMBER_MEASUREMENTS_BASELINE_VERIFICATION, PowerTest.RATE_NOMINAL,
                verbose = False)
            self.reportErrorRaiseExceptionIf(
                not measurements, "No background measurements could be taken")
            retval = self.computeBaselineState(measurements)
            if retval[0]:
                print("Verified baseline.")
                if measurements and LOG_DATA_TO_FILE:
                    with open("/tmp/cts-power-tests-background-data.log", "w") as f:
                        for m in measurements:
                            f.write("%.4f\n" % m)
            return retval
        else:
            return (False, -1)

    def waitForApSuspendMode(self):
        """This function repeatedly collects measurements until AP suspend and display off
        mode is detected. After a maximum number of trials, if this state is not reached, it
        raises an error.
        Returns:
            boolean which is True if device was detected to be in suspend state
        Raises:
            Power monitor-related exception
        """
        print("waitForApSuspendMode(): Sleeping for %d seconds" % DELAY_SCREEN_OFF)
        time.sleep(DELAY_SCREEN_OFF)

        NUMBER_MEASUREMENTS = 200
        # Maximum trials for which to collect measurements to get to Ap suspend
        # state
        MAX_TRIALS = 50

        got_to_suspend_state = False
        for count in xrange(MAX_TRIALS):
            print ("waitForApSuspendMode(): Trial %d of %d" % (count, MAX_TRIALS))
            measurements = self.collectMeasurements(NUMBER_MEASUREMENTS,
                                                    PowerTest.RATE_NOMINAL,
                                                    verbose = False)
            if self.isApInSuspendState(
                    measurements, PowerTest.MAX_PERCENTILE_AP_SCREEN_OFF_AMPS,
                    PowerTest.PERCENTILE_MAX_AP_SCREEN_OFF):
                got_to_suspend_state = True
                break
        self.reportErrorRaiseExceptionIf(
            got_to_suspend_state is False,
            msg = "Unable to determine application processor suspend mode status.")
        print("Got to AP suspend state")
        return got_to_suspend_state

    def collectMeasurements(self, measurementCount, rate, verbose = True):
        """Args:
            measurementCount: Number of measurements to collect from the power
                              monitor
            rate: The integer frequency in Hertz at which to collect measurements from
                  the power monitor
        Returns:
            A list containing measurements from the power monitor; that has the
            requested count of the number of measurements at the specified rate
        """
        assert (measurementCount > 0)
        decimate_by = self._native_hz / rate or 1

        self._power_monitor.StartDataCollection()
        sub_measurements = []
        measurements = []
        tries = 0
        if verbose: print("")
        try:
            while len(measurements) < measurementCount and tries < 5:
                if tries:
                    self._power_monitor.StopDataCollection()
                    self._power_monitor.StartDataCollection()
                    time.sleep(1.0)
                tries += 1
                additional = self._power_monitor.CollectData()
                if additional is not None:
                    tries = 0
                    sub_measurements.extend(additional)
                    while len(sub_measurements) >= decimate_by:
                        sub_avg = sum(sub_measurements[0:decimate_by]) / decimate_by
                        measurements.append(sub_avg)
                        sub_measurements = sub_measurements[decimate_by:]
                        if verbose:
                            # "\33[1A\33[2K" is a special Linux console control
                            # sequence for moving to the previous line, and
                            # erasing it; and reprinting new text on that
                            # erased line.
                            sys.stdout.write("\33[1A\33[2K")
                            print ("MEASURED[%d]: %f" % (len(measurements), measurements[-1]))
        finally:
            self._power_monitor.StopDataCollection()

        self.reportErrorRaiseExceptionIf(measurementCount > len(measurements),
                           "Unable to collect all requested measurements")
        return measurements

    def requestUserAcknowledgment(self, msg):
        """Post message to user on screen and wait for acknowledgment"""
        response = self.executeOnDevice(PowerTest.REQUEST_USER_RESPONSE % msg)
        self.reportErrorRaiseExceptionIf(
            response != "OK", "Unable to request user acknowledgment")

    def setTestResult(self, test_name, test_result, test_message):
        """
        Reports the result of a test to the device
        Args:
            test_name: name of the test
            test_result: Boolean result of the test (True means Pass)
            test_message: Relevant message
        """
        print ("Test %s : %s" % (test_name, test_result))

        response = (
            self.executeOnDevice(
                PowerTest.REQUEST_SET_TEST_RESULT %
                (test_name, test_result, test_message)))
        self.reportErrorRaiseExceptionIf(
            response != "OK", "Unable to send test status to Verifier")

    def setPowerOn(self, sensor, powered_on):
        response = self.executeOnDevice(PowerTest.REQUEST_SENSOR_SWITCH %
            (("ON" if powered_on else "OFF"), sensor))
        self.reportErrorRaiseExceptionIf(
            response == "ERR", "Unable to set sensor %s state" % sensor)
        logging.info("Set %s %s", sensor, ("ON" if powered_on else "OFF"))
        return response

    def runSensorPowerTest(
            self, sensor, max_amperes_allowed, baseline_amps, user_request = None):
        """
        Runs power test for a specific sensor; i.e. measures the amperes draw
        of the phone using monsoon, with the specified sensor mregistered
        and the phone in suspend state; and verifies that the incremental
        consumed amperes is within expected bounds.
        Args:
            sensor: The specified sensor for which to run the power test
            max_amperes_allowed: Maximum ampere draw of the device with the
                    sensor registered and device in suspend state
            baseline_amps: The power draw of the device when it is in baseline
                    state (no sensors registered, display off, AP asleep)
        """
        self._current_test = ("%s_Power_Test_While_%s" % (
            sensor, ("Under_Motion" if user_request is not None else "Still")))
        try:
            print ("\n\n---------------------------------")
            if user_request is not None:
                print ("Running power test on %s under motion." % sensor)
            else:
                print ("Running power test on %s while device is still." % sensor)
            print ("---------------------------------")
            response = self.executeOnDevice(
                PowerTest.REQUEST_SENSOR_AVAILABILITY % sensor)
            if response == "UNAVAILABLE":
                self.setTestResult(
                    self._current_test, test_result = "SKIPPED",
                    test_message = "Sensor %s not available on this platform" % sensor)
            self.setPowerOn("ALL", False)
            if response == "UNAVAILABLE":
                self.setTestResult(
                    self._current_test, test_result = "SKIPPED",
                    test_message = "Sensor %s not available on this device" % sensor)
                return
            self.reportErrorRaiseExceptionIf(response != "OK", "Unable to set all sensor off")
            self.executeOnDevice(PowerTest.REQUEST_SCREEN_OFF)
            self.setUsbEnabled(False)
            self.setUsbEnabled(True)
            self.setPowerOn(sensor, True)
            if user_request is not None:
                print("===========================================\n" +
                      "==> Please follow the instructions presented on the device\n" +
                      "===========================================")
                self.requestUserAcknowledgment(user_request)
            self.executeOnDevice(PowerTest.REQUEST_SCREEN_OFF)
            self.setUsbEnabled(False)
            self.reportErrorRaiseExceptionIf(
                response != "OK", "Unable to set sensor %s ON" % sensor)

            self.waitForApSuspendMode()
            print ("Collecting sensor %s measurements" % sensor)
            measurements = self.collectMeasurements(PowerTest.SAMPLE_COUNT_NOMINAL,
                                                    PowerTest.RATE_NOMINAL)

            if measurements and LOG_DATA_TO_FILE:
                with open("/tmp/cts-power-tests-%s-%s-sensor-data.log" % (sensor,
                    ("Under_Motion" if user_request is not None else "Still")), "w") as f:
                    for m in measurements:
                        f.write("%.4f\n" % m)
                    self.setUsbEnabled(True, verbose = False)
                    print("Saving raw data files to device...")
                    self.executeLocal("adb shell mkdir -p %s" % self._external_storage, False)
                    self.executeLocal("adb push %s %s/." % (f.name, self._external_storage))
                    self.setUsbEnabled(False, verbose = False)
            self.reportErrorRaiseExceptionIf(
                not measurements, "No measurements could be taken for %s" % sensor)
            avg = sum(measurements) / len(measurements)
            squared = [(m - avg) * (m - avg) for m in measurements]

            stddev = math.sqrt(sum(squared) / len(squared))
            current_diff = avg - baseline_amps
            self.setUsbEnabled(True)
            max_power = max(measurements) - avg
            if current_diff <= max_amperes_allowed:
                # TODO: fail the test of background > current
                message = (
                              "Draw is within limits. Sensor delta:%f mAmp   Baseline:%f "
                              "mAmp   Sensor: %f mAmp  Stddev : %f mAmp  Peak: %f mAmp") % (
                              current_diff * 1000.0, baseline_amps * 1000.0, avg * 1000.0,
                              stddev * 1000.0, max_power * 1000.0)
            else:
                message = (
                              "Draw is too high. Current:%f Background:%f   Measured: %f "
                              "Stddev: %f  Peak: %f") % (
                              current_diff * 1000.0, baseline_amps * 1000.0, avg * 1000.0,
                              stddev * 1000.0, max_power * 1000.0)
            self.setTestResult(
                self._current_test,
                ("PASS" if (current_diff <= max_amperes_allowed) else "FAIL"),
                message)
            print("Result: " + message)
        except:
            traceback.print_exc()
            self.setTestResult(self._current_test, test_result = "FAIL",
                               test_message = "Exception occurred during run of test.")
            raise

    @staticmethod
    def runTests(max_baseline_amps):
        testrunner = None
        try:
            GENERIC_MOTION_REQUEST = ("\n===> Please press Next and when the "
                "screen is off, keep the device under motion with only tiny, "
                "slow movements until the screen turns on again.\nPlease "
                "refrain from interacting with the screen or pressing any side "
                "buttons while measurements are taken.")
            USER_STEPS_REQUEST = ("\n===> Please press Next and when the "
                "screen is off, then move the device to simulate step motion "
                "until the screen turns on again.\nPlease refrain from "
                "interacting with the screen or pressing any side buttons "
                "while measurements are taken.")
            testrunner = PowerTest(max_baseline_amps)
            testrunner.executeOnDevice(
                PowerTest.REQUEST_SHOW_MESSAGE % "Connected.  Running tests...")
            is_baseline_success, baseline_amps = testrunner.getBaselineState()

            if is_baseline_success:
                testrunner.setUsbEnabled(True)
                # TODO: Enable testing a single sensor
                testrunner.runSensorPowerTest(
                    "SIGNIFICANT_MOTION", PowerTest.MAX_SIGMO_AMPS, baseline_amps,
                    user_request = GENERIC_MOTION_REQUEST)
                testrunner.runSensorPowerTest(
                    "STEP_DETECTOR", PowerTest.MAX_STEP_DETECTOR_AMPS, baseline_amps,
                    user_request = USER_STEPS_REQUEST)
                testrunner.runSensorPowerTest(
                    "STEP_COUNTER", PowerTest.MAX_STEP_COUNTER_AMPS, baseline_amps,
                    user_request = USER_STEPS_REQUEST)
                testrunner.runSensorPowerTest(
                    "ACCELEROMETER", PowerTest.MAX_ACCEL_AMPS, baseline_amps,
                    user_request = GENERIC_MOTION_REQUEST)
                testrunner.runSensorPowerTest(
                    "MAGNETIC_FIELD", PowerTest.MAX_MAG_AMPS, baseline_amps,
                    user_request = GENERIC_MOTION_REQUEST)
                testrunner.runSensorPowerTest(
                    "GYROSCOPE", PowerTest.MAX_GYRO_AMPS, baseline_amps,
                    user_request = GENERIC_MOTION_REQUEST)
                testrunner.runSensorPowerTest(
                    "ACCELEROMETER", PowerTest.MAX_ACCEL_AMPS, baseline_amps,
                    user_request = None)
                testrunner.runSensorPowerTest(
                    "MAGNETIC_FIELD", PowerTest.MAX_MAG_AMPS, baseline_amps,
                    user_request = None)
                testrunner.runSensorPowerTest(
                    "GYROSCOPE", PowerTest.MAX_GYRO_AMPS, baseline_amps,
                    user_request = None)
                testrunner.runSensorPowerTest(
                    "SIGNIFICANT_MOTION", PowerTest.MAX_SIGMO_AMPS, baseline_amps,
                    user_request = None)
                testrunner.runSensorPowerTest(
                    "STEP_DETECTOR", PowerTest.MAX_STEP_DETECTOR_AMPS, baseline_amps,
                    user_request = None)
                testrunner.runSensorPowerTest(
                    "STEP_COUNTER", PowerTest.MAX_STEP_COUNTER_AMPS, baseline_amps,
                    user_request = None)
            else:
                print("Could not get to baseline state. This is either because "
                      "in several trials, the monitor could not measure a set "
                      "of power measurements that had the specified low "
                      "variance or the mean measurements were below the "
                      "expected value. None of the sensor power measurement "
                      " tests were performed due to not being able to detect "
                      "baseline state. Please re-run the power tests.")
        except KeyboardInterrupt:
            print "Keyboard interrupt from user."
            raise
        except:
            import traceback
            traceback.print_exc()
        finally:
            logging.info("TESTS COMPLETE")
            if testrunner:
                try:
                    testrunner.finalize()
                except socket.error:
                    sys.exit(
                        "===================================================\n"
                        "Unable to connect to device under test. Make sure \n"
                        "the device is connected via the usb pass-through, \n"
                        "the CtsVerifier app is running the SensorPowerTest on \n"
                        "the device, and USB pass-through is enabled.\n"
                        "===================================================")

def main(argv):
    """ Simple command-line interface for a power test application."""
    useful_flags = ["voltage", "status", "usbpassthrough",
                    "samples", "current", "log", "power_monitor"]
    if not [f for f in useful_flags if FLAGS.get(f, None) is not None]:
        print __doc__.strip()
        print FLAGS.MainModuleHelp()
        return

    if FLAGS.avg and FLAGS.avg < 0:
        logging.error("--avg must be greater than 0")
        return

    if FLAGS.voltage is not None:
        if FLAGS.voltage > 5.5:
            print("!!WARNING: Voltage higher than typical values!!!")
        try:
            response = raw_input(
                "Voltage of %.3f requested.  Confirm this is correct (Y/N)" %
                FLAGS.voltage)
            if response.upper() != "Y":
                sys.exit("Aborting")
        except:
            sys.exit("Aborting.")

    if not FLAGS.power_monitor:
        sys.exit(
            "You must specify a '--power_monitor' option to specify which power "
            "monitor type " +
            "you are using.\nOne of:\n  \n  ".join(available_monitors))
    power_monitors = do_import("power_monitors.%s" % FLAGS.power_monitor)
    try:
        mon = power_monitors.Power_Monitor(device = FLAGS.device)
    except:
        import traceback

        traceback.print_exc()
        sys.exit("No power monitors found")

    if FLAGS.voltage is not None:

        if FLAGS.ramp is not None:
            mon.RampVoltage(mon.start_voltage, FLAGS.voltage)
        else:
            mon.SetVoltage(FLAGS.voltage)

    if FLAGS.current is not None:
        mon.SetMaxCurrent(FLAGS.current)

    if FLAGS.status:
        items = sorted(mon.GetStatus().items())
        print "\n".join(["%s: %s" % item for item in items])

    if FLAGS.usbpassthrough:
        if FLAGS.usbpassthrough == "off":
            mon.SetUsbPassthrough(0)
        elif FLAGS.usbpassthrough == "on":
            mon.SetUsbPassthrough(1)
        elif FLAGS.usbpassthrough == "auto":
            mon.SetUsbPassthrough(2)
        else:
            mon.Close()
            sys.exit("bad pass-through flag: %s" % FLAGS.usbpassthrough)

    if FLAGS.samples:
        # Make sure state is normal
        mon.StopDataCollection()
        status = mon.GetStatus()
        native_hz = status["sampleRate"] * 1000

        # Collect and average samples as specified
        mon.StartDataCollection()

        # In case FLAGS.hz doesn't divide native_hz exactly, use this invariant:
        # 'offset' = (consumed samples) * FLAGS.hz - (emitted samples) * native_hz
        # This is the error accumulator in a variation of Bresenham's algorithm.
        emitted = offset = 0
        collected = []
        history_deque = collections.deque()  # past n samples for rolling average

        # TODO: Complicated lines of code below. Refactoring needed
        try:
            last_flush = time.time()
            while emitted < FLAGS.samples or FLAGS.samples == -1:
                # The number of raw samples to consume before emitting the next output
                need = (native_hz - offset + FLAGS.hz - 1) / FLAGS.hz
                if need > len(collected):  # still need more input samples
                    samples = mon.CollectData()
                    if not samples: break
                    collected.extend(samples)
                else:
                    # Have enough data, generate output samples.
                    # Adjust for consuming 'need' input samples.
                    offset += need * FLAGS.hz
                    while offset >= native_hz:  # maybe multiple, if FLAGS.hz > native_hz
                        this_sample = sum(collected[:need]) / need

                        if FLAGS.timestamp: print int(time.time()),

                        if FLAGS.avg:
                            history_deque.appendleft(this_sample)
                            if len(history_deque) > FLAGS.avg: history_deque.pop()
                            print "%f %f" % (this_sample,
                                             sum(history_deque) / len(history_deque))
                        else:
                            print "%f" % this_sample
                        sys.stdout.flush()

                        offset -= native_hz
                        emitted += 1  # adjust for emitting 1 output sample
                    collected = collected[need:]
                    now = time.time()
                    if now - last_flush >= 0.99:  # flush every second
                        sys.stdout.flush()
                        last_flush = now
        except KeyboardInterrupt:
            print("interrupted")
            return 1
        finally:
            mon.Close()
        return 0

    if FLAGS.run:
        if not FLAGS.power_monitor:
            sys.exit(
                "When running power tests, you must specify which type of power "
                "monitor to use" +
                " with '--power_monitor <type of power monitor>'")
        try:
            PowerTest.runTests(FLAGS.max_baseline_amps)
        except KeyboardInterrupt:
            print "Keyboard interrupt from user"

if __name__ == "__main__":
    flags.DEFINE_boolean("status", None, "Print power meter status")
    flags.DEFINE_integer("avg", None,
                         "Also report average over last n data points")
    flags.DEFINE_float("voltage", None, "Set output voltage (0 for off)")
    flags.DEFINE_float("current", None, "Set max output current")
    flags.DEFINE_string("usbpassthrough", None, "USB control (on, off, auto)")
    flags.DEFINE_integer("samples", None, "Collect and print this many samples")
    flags.DEFINE_integer("hz", 5000, "Print this many samples/sec")
    flags.DEFINE_string("device", None,
                        "Path to the device in /dev/... (ex:/dev/ttyACM1)")
    flags.DEFINE_boolean("timestamp", None,
                         "Also print integer (seconds) timestamp on each line")
    flags.DEFINE_boolean("ramp", True, "Gradually increase voltage")
    flags.DEFINE_boolean("log", False, "Log progress to a file or not")
    flags.DEFINE_boolean("run", False, "Run the test suite for power")
    flags.DEFINE_string("power_monitor", None, "Type of power monitor to use")
    flags.DEFINE_float("max_baseline_amps", 0.005,
                       "Set maximum baseline current for device being tested")
    sys.exit(main(FLAGS(sys.argv)))
