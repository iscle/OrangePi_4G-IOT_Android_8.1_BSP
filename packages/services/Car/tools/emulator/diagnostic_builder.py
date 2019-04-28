#!/usr/bin/env python3.4
#
# Copyright (C) 2017 The Android Open Source Project
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
#

# A helper class to generate COMPLEX property values that can be
# set as the value for a diagnostic frame
# Spritually, the same as DiagnosticEventBuilder.java

from diagnostic_sensors import OBD2_SENSOR_INTEGER_LAST_SYSTEM_INDEX
from diagnostic_sensors import OBD2_SENSOR_FLOAT_LAST_SYSTEM_INDEX

class DiagnosticEventBuilder(object):
    class ByteArray(object):
        def __init__(self, numElements):
            self.count = numElements
            if 0 == (numElements % 8):
                self.data = bytearray(numElements/8)
            else:
                # if not a multiple of 8, add one extra byte
                self.data = bytearray(1+numElements/8)

        def _getIndices(self, bit):
            if (bit < 0) or (bit >= self.count):
                raise IndexError("index %d not in range [0,%d)" % (bit, self.count))
            byteIdx = bit / 8
            bitIdx = (bit % 8)
            return byteIdx, bitIdx

        def setBit(self, bit):
            byteIdx, bitIdx = self._getIndices(bit)
            bitValue = pow(2,bitIdx)
            self.data[byteIdx] = self.data[byteIdx] | bitValue

        def getBit(self, bit):
            byteIdx, bitIdx = self._getIndices(bit)
            bitValue = pow(2,bitIdx)
            return 0 != self.data[byteIdx] & bitValue

        def __str__(self):
            return str(self.data)

    def __init__(self, propConfig):
        self.string_value = ""
        self.bytes = ""
        self.numIntSensors = propConfig.config[0].config_array[0] + \
            OBD2_SENSOR_INTEGER_LAST_SYSTEM_INDEX + 1
        self.numFloatSensors = propConfig.config[0].config_array[1] + \
            OBD2_SENSOR_FLOAT_LAST_SYSTEM_INDEX + 1
        self.bitmask = DiagnosticEventBuilder.ByteArray(
            self.numIntSensors+self.numFloatSensors)
        self.int32_values = [0] * self.numIntSensors
        self.float_values = [0.0] * self.numFloatSensors

    def addIntSensor(self, idx, value):
        self.int32_values[idx] = value
        self.bitmask.setBit(idx)
        return self

    def addFloatSensor(self, idx, value):
        self.float_values[idx] = value
        self.bitmask.setBit(len(self.int32_values)+idx)
        return self

    def setStringValue(self, string):
        self.string_value = string
        return self

    def build(self):
        self.bytes_value = str(self.bitmask)
        return self

    def __str__(self):
        s = "diagnostic event {\n"
        for x in ['string_value', 'int32_values', 'float_values']:
            s = s + "\t%s: %s\n" % (x, self.__dict__[x])
        return s  + "}"
