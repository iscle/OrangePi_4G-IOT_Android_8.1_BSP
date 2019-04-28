#!/usr/bin/env python3
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

# OBD2 standard sensor indices are different from those used by the
# Android Auto Diagnostics API. This script maps from OBD2 sensors to
# those expected by the Diagnostics API.
# To use:
# ./obd2_to_diagjson.py --src file1.json --dst file2.json
# It is acceptable and supported to point --src and --dst to the same file

import collections
import json
import os, os.path, sys

class Json(object):
    @classmethod
    def load(cls, file):
        return Json(json.load(file))

    @classmethod
    def wrapIfNeeded(cls, item):
        if isinstance(item, list) or isinstance(item, dict):
            return Json(item)
        return item

    def __init__(self, doc):
        self.doc = doc

    def __str__(self):
        return str(self.doc)

    def __repr__(self):
        return self.__str__()

    def __getattr__(self, attr):
        return Json.wrapIfNeeded(self.doc.get(attr))

    def __iter__(self):
        class Iter(object):
            def __init__(self, doc):
                self.doc = doc.__iter__()

            def __next__(self):
                return Json.wrapIfNeeded(self.doc.__next__())

        return Iter(self.doc)

class OrderedStore(object):
    def __init__(self):
        self.__dict__['store'] = collections.OrderedDict()

    def __setattr__(self, name, value):
        self.__dict__['store'][name] = value

    def __getattr__(self, name):
        return self.__dict__['store'][name]

    def get(self, name, default=None):
        return self.__dict__['store'].get(name, default)

    def getStore(self):
        return self.__dict__['store']

    def __iter__(self):
        return iter(self.__dict__['store'])

    def __delattr__(self, name):
        del self.__dict__['store'][name]

    def __str__(self):
        return str(self.__dict__['store'])

    def toJSON(self):
        return json.dumps(self.store)

class Event(object):
    def __init__(self):
        self.store = OrderedStore()

    def setTimestamp(self, timestamp):
        self.store.timestamp = timestamp
        return self

    def getTimestamp(self):
        return self.store.timestamp

    def setType(self, type):
        self.store.type = type
        return self

    def getType(self):
        return self.store.type

    def setStringValue(self, string):
        if string:
            self.store.stringValue = string
        return self

    def getStringValue(self):
        return self.store.get('stringValue')

    def setIntValue(self, id, value):
        if 'intValues' not in self.store:
            self.store.intValues = []
        d = collections.OrderedDict()
        d['id'] = id
        d['value'] = value
        self.store.intValues.append(d)
        return self

    def intValues(self):
        if 'intValues' not in self.store:
            return []
        for value in self.store.intValues:
            yield (value['id'], value['value'])

    def setFloatValue(self, id, value):
        if 'floatValues' not in self.store:
            self.store.floatValues = []
        d = collections.OrderedDict()
        d['id'] = id
        d['value'] = value
        self.store.floatValues.append(d)
        return self

    def floatValues(self):
        if 'floatValues' not in self.store:
            return []
        for value in self.store.floatValues:
            yield (value['id'], value['value'])

    @classmethod
    def fromJson(cls, json):
        event = Event()
        event.setTimestamp(json.timestamp)
        event.setType(json.type)
        for intValue in json.intValues:
            event.setIntValue(intValue.id, intValue.value)
        for floatValue in json.floatValues:
            event.setFloatValue(floatValue.id, floatValue.value)
        event.setStringValue(json.stringValue)
        return event

    def transform(self, intMapping, floatMapping):
        event = Event()
        event.setTimestamp(self.getTimestamp())
        event.setType(self.getType())
        for id, value in self.intValues():
            if id in intMapping:
                intMapping[id](event, value)
            else:
                print('warning: integer id 0x%x not found in mapping. dropped.' % id)
        for id, value in self.floatValues():
            if id in floatMapping:
                floatMapping[id](event, value)
            else:
                print('warning: float id 0x%x not found in mapping. dropped.' % id)
        event.setStringValue(self.getStringValue())
        return event

    def getStore(self):
        return self.store.getStore()

class EventEncoder(json.JSONEncoder):
    def default(self, o):
        if isinstance(o, Event):
            return o.getStore()

# Mappings between standard OBD2 sensors and the indices
# used by Vehicle HAL
intSensorsMapping = {
    0x03 : lambda event,value: event.setIntValue(0, value),
    0x05 : lambda event,value: event.setFloatValue(1, value),
    0x0A : lambda event,value: event.setIntValue(22, value),
    0x0C : lambda event,value: event.setFloatValue(8, value),
    0x0D : lambda event,value: event.setFloatValue(9, value),
    0x1F : lambda event,value: event.setIntValue(7, value),
    0x5C : lambda event,value: event.setIntValue(23, value),
}

floatSensorsMapping = {
    0x04 : lambda event, value: event.setFloatValue(0, value),
    0x06 : lambda event, value: event.setFloatValue(2, value),
    0x07 : lambda event, value: event.setFloatValue(3, value),
    0x08 : lambda event, value: event.setFloatValue(4, value),
    0x09 : lambda event, value: event.setFloatValue(5, value),
    0x11 : lambda event, value: event.setFloatValue(12, value),
    0x2F : lambda event, value: event.setFloatValue(42, value),
    0x46 : lambda event, value: event.setIntValue(13, int(value)),
}

def parseOptions():
    from argparse import ArgumentParser
    parser = ArgumentParser(description='OBD2 to Diagnostics JSON Converter')
    parser.add_argument('--src', '-S', dest='source_file',
        help='The source file to convert from', required=True)
    parser.add_argument('--dst', '-D', dest='destination_file',
        help='The destination file to convert to', required=True)
    return parser.parse_args()

args = parseOptions()
if not os.path.exists(args.source_file):
    print('source file %s does not exist' % args.source_file)
    sys.exit(1)

source_json = Json.load(open(args.source_file))
dest_events = []

for source_json_event in source_json:
    source_event = Event.fromJson(source_json_event)
    destination_event = source_event.transform(intSensorsMapping, floatSensorsMapping)
    dest_events.append(destination_event)

json.dump(dest_events, open(args.destination_file, 'w'), cls=EventEncoder)
