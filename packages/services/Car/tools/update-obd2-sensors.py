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

# This script generates useful Java representations for the OBD2 sensors
# defined in Vehicle HAL. It is meant to be as an easy way to update the
# list of diagnostic sensors and get all downstream users of that information
# updated in a consistent fashion.

import sys
sys.dont_write_bytecode = True

import hidl_parser.parser

class SensorList(object):
    """A list of sensors ordered by a unique identifier."""
    def __init__(self, descriptor):
        self.sensors = []
        self.id = -1
        self.descriptor = descriptor

    def addSensor(self, sensor):
        """Add a new sensor to the list."""
        if not hasattr(sensor, 'id'):
            self.id += 1
            sensor.id = self.id
        self.sensors.append(sensor)

    def finalizeList(self):
        """Complete the list, adding well-known sensor information."""
        self.id -= 1
        vendorStartSensor = self.sensorClass("VENDOR_START_INDEX",
            id="LAST_SYSTEM_INDEX + 1")
        # make calling finalizeList idempotent
        self.finalizeList = lambda: self
        return self

    def __getitem__(self, key):
        return self.sensors.__getitem__(key)

class SensorPolicy(object):
    """A formatter object that does processing on sensor data."""
    @classmethod
    def indentLines(cls, string, numSpaces):
        indent = ' ' * numSpaces
        parts = string.split('\n')
        parts = [indent + part for part in parts]
        return '\n'.join(parts) + "\n"

    def sensor(self, theSensor, theSensors):
        """Produce output for a sensor."""
        pass

    def prefix(self, theSensors):
        """Prefix string before any sensor data is generated."""
        return ""

    def suffix(self, theSensors):
        """Suffix string after all sensor data is generated."""
        return ""

    def indent(self):
        """Indentation level for individual sensor data."""
        return 0

    def separator(self):
        """Separator between individual sensor data entries."""
        return ""

    def description(self):
        """A description of this policy."""
        return "A sensor policy."

    def sensors(self, theSensors):
        """Produce output for all sensors."""
        theSensors = theSensors.finalizeList()
        s = self.prefix(theSensors) + "\n"
        first = True
        for theSensor in theSensors:
            if first:
                first = False
            else:
                s += self.separator()
            sensorLine = SensorPolicy.indentLines(self.sensor(theSensor,
                theSensors), self.indent())
            s += sensorLine
        s += self.suffix(theSensors) + "\n"
        return s

class JavaSensorPolicy(SensorPolicy):
    """The sensor policy that emits Java sensor descriptions."""
    def sensor(self, theSensor, theSensors):
        sensorName = theSensor.name.replace("_INDEX", "")
        sensorId = str(theSensor.id).replace("_INDEX", "")
        return "public static final int " + sensorName + " = " + \
            str(sensorId) + ";"

    def prefix(self, theSensors):
        s = \
"/*\n" + \
" * Copyright (C) 2017 The Android Open Source Project\n" + \
" *\n" + \
" * Licensed under the Apache License, Version 2.0 (the \"License\");\n" + \
" * you may not use this file except in compliance with the License.\n" + \
" * You may obtain a copy of the License at\n" + \
" *\n" + \
" *      http://www.apache.org/licenses/LICENSE-2.0\n" + \
" *\n" + \
" * Unless required by applicable law or agreed to in writing, software\n" + \
" * distributed under the License is distributed on an \"AS IS\" BASIS,\n" + \
" * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" + \
" * See the License for the specific language governing permissions and\n" + \
" * limitations under the License.\n" + \
"*/\n" + \
"\n" + \
"package android.car.diagnostic;\n" + \
"\n" + \
"import android.annotation.IntDef;\n" + \
"import android.annotation.SystemApi;\n" + \
"import java.lang.annotation.Retention;\n" + \
"import java.lang.annotation.RetentionPolicy;\n" + \
"\n" + \
"/**\n" + \
" * This class is a container for the indices of diagnostic sensors. The values are extracted by\n" + \
" * running packages/services/Car/tools/update-obd2-sensors.py against types.hal.\n" + \
" *\n" + \
" * DO NOT EDIT MANUALLY\n" + \
" *\n" + \
" * @hide\n" + \
" */\n" + \
"@SystemApi\n" + \
"public final class %sSensorIndex {\n" % theSensors.descriptor + \
"    private %sSensorIndex() {}\n" % theSensors.descriptor

        return s

    def indent(self):
        return 4

class PythonSensorPolicy(SensorPolicy):
    """The sensor policy that emits Python sensor descriptions."""
    def sensor(self, theSensor, theSensors):
        return "DIAGNOSTIC_SENSOR_%s_%s = %s" % (
            theSensors.descriptor.upper(),
            theSensor.name.upper(),
            self.adjustSensorId(theSensors.descriptor.upper(), str(theSensor.id))
        )

    def adjustSensorId(self, descriptor, sensorId):
        if sensorId.isdigit(): return sensorId
        return "DIAGNOSTIC_SENSOR_%s_%s" % (descriptor, sensorId.upper())

class IntDefSensorPolicy(SensorPolicy):
    """The sensor policy that emits @IntDef sensor descriptions."""
    def sensor(self, theSensor, theSensors):
        sensorName = theSensor.name.replace("_INDEX", "")
        return "%sSensorIndex.%s," % (theSensors.descriptor,sensorName)

    def prefix(self, theSensors):
        return "    /** @hide */\n    @Retention(RetentionPolicy.SOURCE)\n    @IntDef({"

    def indent(self):
        return 8

    def suffix(self, theSensors):
        return "    })\n    public @interface SensorIndex {}"

class SensorMeta(type):
    """Metaclass for sensor classes."""
    def __new__(cls, name, parents, dct):
        sensorList = dct['sensorList']
        class SensorBase(object):
            def __init__(self, name, comment=None, id=None):
                self.name = name
                self.comment = comment if comment else ""
                if id: self.id = id
                sensorList.addSensor(self)
            def __repr__(self):
                s = ""
                if self.comment:
                    s = s + self.comment + "\n"
                s = s + self.name + " = " + str(self.id)
                return s

        newClass = super().__new__(cls, name, (SensorBase,), dct)
        sensorList.sensorClass = newClass
        return newClass

intSensors = SensorList(descriptor="Integer")
floatSensors = SensorList(descriptor="Float")

class intSensor(metaclass=SensorMeta):
    sensorList = intSensors

class floatSensor(metaclass=SensorMeta):
    sensorList = floatSensors

def applyPolicy(policy, destfile):
    """Given a sensor policy, apply it to all known sensor types"""
    applyIntPolicy(policy, destfile)
    applyFloatPolicy(policy, destfile)

def applyIntPolicy(policy, destfile):
    "Given a sensor policy, apply it to integer sensors"
    print(policy.sensors(intSensors), file=destfile)

def applyFloatPolicy(policy, destfile):
    "Given a sensor policy, apply it to float sensors"
    print(policy.sensors(floatSensors), file=destfile)

def java(destfile):
    applyPolicy(JavaSensorPolicy(), destfile)

def intdef(destfile):
    applyPolicy(IntDefSensorPolicy(), destfile)

def python(destfile):
    applyPolicy(PythonSensorPolicy(), destfile)

def generateJava(filepath):
    """Generate Java code for all sensors."""
    intfile = open(os.path.join(filepath, "IntegerSensorIndex.java"), "w")
    floatfile = open(os.path.join(filepath, "FloatSensorIndex.java"), "w")
    javaPolicy = JavaSensorPolicy()
    intdefPolicy = IntDefSensorPolicy()
    applyIntPolicy(javaPolicy, intfile)
    applyIntPolicy(intdefPolicy, intfile)
    applyFloatPolicy(javaPolicy, floatfile)
    applyFloatPolicy(intdefPolicy, floatfile)
    print("}", file=intfile)
    print("}", file=floatfile)
    intfile.close()
    floatfile.close()

def generatePython(filepath):
    """Generate Python code for all sensors."""
    destfile = open(filepath, "w")
    print("#!/usr/bin/env python3", file=destfile)
    print("#", file=destfile)
    print("# Copyright (C) 2017 The Android Open Source Project", file=destfile)
    print("#", file=destfile)
    print("# Licensed under the Apache License, Version 2.0 (the \"License\");", file=destfile)
    print("# you may not use this file except in compliance with the License.", file=destfile)
    print("# You may obtain a copy of the License at", file=destfile)
    print("#", file=destfile)
    print("#      http://www.apache.org/licenses/LICENSE-2.0", file=destfile)
    print("#", file=destfile)
    print("# Unless required by applicable law or agreed to in writing, software", file=destfile)
    print("# distributed under the License is distributed on an \"AS IS\" BASIS,", file=destfile)
    print("# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.", file=destfile)
    print("# See the License for the specific language governing permissions and", file=destfile)
    print("# limitations under the License.", file=destfile)
    print("#", file=destfile)
    print("# This file is generated by types.hal by packages/services/Car/tools/update-obd2-sensors.py", file=destfile)
    print("# DO NOT EDIT MANUALLY", file=destfile)
    python(destfile)

def load(filepath):
    """Load sensor data from Vehicle HAL."""
    ast = hidl_parser.parser.parse(filepath)
    integerSensors = ast['enums']['DiagnosticIntegerSensorIndex']
    floatSensors = ast['enums']['DiagnosticFloatSensorIndex']
    for case in integerSensors.cases:
        intSensor(name=case.name, id=case.value)
    for case in floatSensors.cases:
        floatSensor(name=case.name, id=case.value)

import os

if len(sys.argv) != 4:
    print('syntax: update-obd2-sensors.py <path/to/types.hal> <path/to/android.car.diagnostic> <path/to/diagnostic_sensors.py>')
    print('This script will parse types.hal, and use the resulting', end='')
    print('parse tree to generate Java and Python lists of sensor identifiers.')
    sys.exit(1)
load(sys.argv[1])
generateJava(sys.argv[2])
generatePython(sys.argv[3])
