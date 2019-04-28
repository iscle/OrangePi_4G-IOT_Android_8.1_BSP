#!/usr/bin/env python
# -*- coding: utf-8 -*-
#
# Copyright 2017 Google Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

"""
    This module tests the Vehicle HAL using adb socket.

    Protocol Buffer:
        This module relies on VehicleHalProto_pb2.py being in sync with the protobuf in the VHAL.
        If the VehicleHalProto.proto file has changed, re-generate the python version using
        a command of the form:
            protoc -I=<proto_dir> --python_out=<out_dir> <proto_dir>/VehicleHalProto.proto
        For example:
            protoDir=~/android/master/hardware/interfaces/automotive/vehicle/2.0/default/impl/vhal_v2_0/proto
            outDir=~/android/master/packages/services/Car/tools/emulator
            protoc -I=$protoDir --python_out=$outDir $protoDir/VehicleHalProto.proto
"""

from __future__ import print_function

# Suppress .pyc files
import sys
sys.dont_write_bytecode = True

import VehicleHalProto_pb2
import vhal_consts_2_1
import vhal_emulator
import logging

class VhalTest:
    # Global vars
    _badProps = [0, 0x3FFFFFFF]     # List of bad properties to try for negative tests
    _configs = 0                    # List of configs from DUT
    _log = 0                        # Logger module
    _vhal = 0                       # Handle to VHAL object that communicates over socket to DUT
    # TODO: b/38203109 - Fix OBD2 values, implement handling for complex properties
    _skipProps = [
                    vhal_consts_2_1.VEHICLEPROPERTY_OBD2_LIVE_FRAME,
                    vhal_consts_2_1.VEHICLEPROPERTY_OBD2_FREEZE_FRAME,
                    vhal_consts_2_1.VEHICLEPROPERTY_OBD2_FREEZE_FRAME_INFO,
                    vhal_consts_2_1.VEHICLEPROPERTY_OBD2_FREEZE_FRAME_CLEAR,
                    vhal_consts_2_1.VEHICLEPROPERTY_VEHICLE_MAP_SERVICE,
                    vhal_consts_2_1.VEHICLEPROPERTY_WHEEL_TICK,     # Need to support complex properties
                    0x21E00666      # FakeDataControllingProperty - an internal test property
                 ]

    def _getMidpoint(self, minVal, maxVal):
        retVal =  minVal + (maxVal - minVal)/2
        return retVal

    # Generates a test value based on the config
    def _generateTestValue(self, cfg, idx, origValue):
        valType = cfg.value_type
        if valType in self._types.TYPE_STRING:
            testValue = "test string"
        elif valType in self._types.TYPE_BYTES:
            # Generate array of integers counting from 0
            testValue = list(range(len(origValue)))
        elif valType == vhal_consts_2_1.VEHICLEPROPERTYTYPE_BOOLEAN:
            testValue = origValue ^ 1
        elif valType in self._types.TYPE_INT32:
            try:
                testValue = self._getMidpoint(cfg.area_configs[idx].min_int32_value,
                                              cfg.area_configs[idx].max_int32_value)
            except:
                # min/max values aren't set.  Set a hard-coded value
                testValue = 123
        elif valType in self._types.TYPE_INT64:
            try:
                testValue = self._getMidpoint(cfg.area_configs[idx].min_int64_value,
                                              cfg.area_configs[idx].max_int64_value)
            except:
                # min/max values aren't set.  Set a large hard-coded value
                testValue = 1 << 50
        elif valType in self._types.TYPE_FLOAT:
            try:
                testValue = self._getMidpoint(cfg.area_configs[idx].min_float_value,
                                              cfg.area_configs[idx].max_float_value)
            except:
                # min/max values aren't set.  Set a hard-coded value
                testValue = 123.456
            # Truncate float to 5 decimal places
            testValue = "%.5f" % testValue
            testValue = float(testValue)
        else:
            self._log.error("generateTestValue:  valType=0x%X is not handled", valType)
            testValue = None
        return testValue

    # Helper function to extract values array from rxMsg
    def _getValueFromMsg(self, rxMsg):
        # Check to see only one property value is returned
        if len(rxMsg.value) != 1:
            self._log.error("getValueFromMsg:  Received invalid value")
            value = None
        else:
            valType = rxMsg.value[0].value_type
            if valType in self._types.TYPE_STRING:
                value = rxMsg.value[0].string_value
            elif valType in self._types.TYPE_BYTES:
                value = rxMsg.value[0].bytes_value
            elif valType == vhal_consts_2_1.VEHICLEPROPERTYTYPE_BOOLEAN:
                value = rxMsg.value[0].int32_values[0]
            elif valType in self._types.TYPE_INT32:
                value = rxMsg.value[0].int32_values[0]
            elif valType in self._types.TYPE_INT64:
                value = rxMsg.value[0].int64_values[0]
            elif valType in self._types.TYPE_FLOAT:
                value = rxMsg.value[0].float_values[0]
                # Truncate float to 5 decimal places
                value = "%.5f" % value
                value = float(value)
            else:
                self._log.error("getValueFromMsg:  valType=0x%X is not handled", valType)
                value = None
        return value

    # Helper function to receive a message and validate the type and status
    #   retVal = 1 if no errors
    #   retVal = 0 if errors detected
    def _rxMsgAndValidate(self, expectedType, expectedStatus):
        retVal = 1
        rxMsg = self._vhal.rxMsg()
        if rxMsg.msg_type != expectedType:
            self._log.error("rxMsg Type expected: 0x%X, received: 0x%X", expectedType, rxMsg.msg_type)
            retVal = 0
        if rxMsg.status != expectedStatus:
            self._log.error("rxMsg Status expected: 0x%X, received: 0x%X", expectedStatus, rxMsg.status)
            retVal = 0
        return rxMsg, retVal

    # Calls getConfig() on each individual property ID and verifies it matches with the config
    #   received in getConfigAll()
    def testGetConfig(self):
        self._log.info("Starting testGetConfig...")
        for cfg in self._configs:
            self._log.debug("  Getting config for propId=0x%X", cfg.prop)
            self._vhal.getConfig(cfg.prop)
            rxMsg, retVal = self._rxMsgAndValidate(VehicleHalProto_pb2.GET_CONFIG_RESP,
                                                   VehicleHalProto_pb2.RESULT_OK)
            if retVal:
                if rxMsg.config[0] != cfg:
                    self._log.error("testGetConfig failed.  prop=0x%X, expected:\n%s\nreceived:\n%s",
                               cfg.prop, str(cfg), str(rxMsg.config))
        self._log.info("  Finished testGetConfig!")

    # Calls getConfig() on invalid property ID and verifies it generates an error
    def testGetBadConfig(self):
        self._log.info("Starting testGetBadConfig...")
        for prop in self._badProps:
            self._log.debug("  Testing bad propId=0x%X", prop)
            self._vhal.getConfig(prop)
            rxMsg, retVal = self._rxMsgAndValidate(VehicleHalProto_pb2.GET_CONFIG_RESP,
                                                   VehicleHalProto_pb2.ERROR_INVALID_PROPERTY)
            if retVal:
                for cfg in rxMsg.config:
                    self._log.error("testGetBadConfig  prop=0x%X, expected:None, received:\n%s",
                                    cfg.prop, str(rxMsg.config))
        self._log.info("  Finished testGetBadConfig!")

    def testGetPropertyAll(self):
        self._log.info("Starting testGetPropertyAll...")
        self._vhal.getPropertyAll()
        rxMsg, retVal = self._rxMsgAndValidate(VehicleHalProto_pb2.GET_PROPERTY_ALL_RESP,
                                               VehicleHalProto_pb2.RESULT_OK)
        if retVal == 0:
            self._log.error("testGetPropertyAll:  Failed to receive proper rxMsg")

        # TODO: Finish writing this test.  What should we be testing, anyway?

        self._log.info("  Finished testGetPropertyAll!")

    def testGetSet(self):
        self._log.info("Starting testGetSet()...")
        for cfg in self._configs:
            if cfg.prop in self._skipProps:
                # Skip properties that cannot be handled properly by this test.
                self._log.warning("  Skipping propId=0x%X", cfg.prop)
                continue

            areas = cfg.supported_areas
            idx = -1
            while (idx == -1) | (areas != 0):
                idx += 1
                # Get the area to test
                area = areas & (areas -1)
                area ^= areas

                # Remove the area from areas
                areas ^= area

                self._log.debug("  Testing propId=0x%X, area=0x%X", cfg.prop, area)

                # Get the current value
                self._vhal.getProperty(cfg.prop, area)
                rxMsg, retVal = self._rxMsgAndValidate(VehicleHalProto_pb2.GET_PROPERTY_RESP,
                                                       VehicleHalProto_pb2.RESULT_OK)

                # Save the original value
                origValue = self._getValueFromMsg(rxMsg)
                if origValue == None:
                    self._log.error("testGetSet:  Could not get value for prop=0x%X, area=0x%X",
                                    cfg.prop, area)
                    continue

                # Generate the test value
                testValue = self._generateTestValue(cfg, idx, origValue)
                if testValue == None:
                    self._log.error("testGetSet:  Cannot generate test value for prop=0x%X, area=0x%X",
                                    cfg.prop, area)
                    continue

                # Send the new value
                self._vhal.setProperty(cfg.prop, area, testValue)
                rxMsg, retVal = self._rxMsgAndValidate(VehicleHalProto_pb2.SET_PROPERTY_RESP,
                                                        VehicleHalProto_pb2.RESULT_OK)

                # Get the new value and verify it
                self._vhal.getProperty(cfg.prop, area)
                rxMsg, retVal = self._rxMsgAndValidate(VehicleHalProto_pb2.GET_PROPERTY_RESP,
                                                       VehicleHalProto_pb2.RESULT_OK)
                newValue = self._getValueFromMsg(rxMsg)
                if newValue != testValue:
                    self._log.error("testGetSet: set failed for propId=0x%X, area=0x%X", cfg.prop, area)
                    print("testValue= ", testValue, "newValue= ", newValue)
                    continue

                # Reset the value to what it was before
                self._vhal.setProperty(cfg.prop, area, origValue)
                rxMsg, retVal = self._rxMsgAndValidate(VehicleHalProto_pb2.SET_PROPERTY_RESP,
                                                       VehicleHalProto_pb2.RESULT_OK)
        self._log.info("  Finished testGetSet()!")

    def testGetBadProperty(self):
        self._log.info("Starting testGetBadProperty()...")
        for prop in self._badProps:
            self._log.debug("  Testing bad propId=0x%X", prop)
            self._vhal.getProperty(prop, 0)
            rxMsg, retVal = self._rxMsgAndValidate(VehicleHalProto_pb2.GET_PROPERTY_RESP,
                                                   VehicleHalProto_pb2.ERROR_INVALID_PROPERTY)
            if retVal:
                for value in rxMsg.value:
                    self._log.error("testGetBadProperty  prop=0x%X, expected:None, received:\n%s",
                                    prop, str(rxMsg))
        self._log.info("  Finished testGetBadProperty()!")

    def testSetBadProperty(self):
        self._log.info("Starting testSetBadProperty()...")
        area = 1
        value = 100
        for prop in self._badProps:
            self._log.debug("  Testing bad propId=0x%X", prop)
            area = area + 1
            value = value + 1
            try:
                self._vhal.setProperty(prop, area, value)
                self._log.error("testGetBadProperty failed.  prop=0x%X, area=0x%X, value=%d",
                                prop, area, value)
            except ValueError as e:
                # Received expected error
                pass
        self._log.info("  Finished testSetBadProperty()!")

    def runTests(self):
        self.testGetConfig()
        self.testGetBadConfig()
        self.testGetPropertyAll()
        self.testGetSet()
        self.testGetBadProperty()
        self.testSetBadProperty()
        # Add new tests here to be run


    # Valid logLevels:
    #   CRITICAL    50
    #   ERRROR      40
    #   WARNING     30
    #   INFO        20
    #   DEBUG       10
    #   NOTSET      0
    def __init__(self, types, logLevel=20):
        self._types = types
        # Configure the logger
        logging.basicConfig()
        self._log = logging.getLogger('vhal_emulator_test')
        self._log.setLevel(logLevel)
        # Start the VHAL Emulator
        self._vhal = vhal_emulator.Vhal(types)
        # Get the list of configs
        self._vhal.getConfigAll()
        self._configs = self._vhal.rxMsg().config

if __name__ == '__main__':
    v = VhalTest(vhal_consts_2_1.vhal_types_2_0)
    v.runTests()
