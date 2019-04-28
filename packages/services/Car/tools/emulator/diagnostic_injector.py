#!/usr/bin/env python
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

# A tool that can read diagnostic events from a Diagnostic JSON document
# and forward them to Vehicle HAL via vhal_emulator
# Use thusly:
# $ ./diagnostic_injector.py <path/to/diagnostic.json>

import argparse
import json
import sys
import time

import vhal_consts_2_1 as c

# vhal_emulator depends on a custom Python package that requires installation
# give user guidance should the import fail
try:
    from vhal_emulator import Vhal
except ImportError as e:
    isProtobuf = False
    pipTool = "pip%s" % ("3" if sys.version_info > (3,0) else "")
    if hasattr(e, 'name'):
        if e.name == 'google': isProtobuf = True
    elif hasattr(e, 'message'):
        if e.message.endswith('symbol_database'):
            isProtobuf = True
    if isProtobuf:
        print('could not find protobuf.')
        print('protobuf can be installed via "sudo %s install --upgrade protobuf"' % pipTool)
        sys.exit(1)
    else:
        raise e

from diagnostic_builder import DiagnosticEventBuilder

class DiagnosticHalWrapper(object):
    def __init__(self, device):
        self.vhal = Vhal(c.vhal_types_2_0, device)
        self.liveFrameConfig = self.chat(
            lambda hal: hal.getConfig(c.VEHICLEPROPERTY_OBD2_LIVE_FRAME))
        self.freezeFrameConfig = self.chat(
            lambda hal: hal.getConfig(c.VEHICLEPROPERTY_OBD2_FREEZE_FRAME))
        self.eventTypeData = {
            'live' : {
                'builder'  : lambda: DiagnosticEventBuilder(self.liveFrameConfig),
                'property' :  c.VEHICLEPROPERTY_OBD2_LIVE_FRAME
            },
            'freeze' : {
                'builder'  : lambda: DiagnosticEventBuilder(self.freezeFrameConfig),
                'property' :  c.VEHICLEPROPERTY_OBD2_FREEZE_FRAME
            },
        }

    def chat(self, request):
        request(self.vhal)
        return self.vhal.rxMsg()

    def inject(self, file):
        data = json.load(open(file))
        lastTimestamp = 0
        for event in data:
            currentTimestamp = event['timestamp']
            # time travel isn't supported (yet)
            assert currentTimestamp >= lastTimestamp
            # wait the delta between this event and the previous one
            # before sending it out; but on the first event, send now
            # or we'd wait for a long long long time
            if lastTimestamp != 0:
                # also, timestamps are in nanoseconds, but sleep() uses seconds
                time.sleep((currentTimestamp-lastTimestamp)/1000000000)
            lastTimestamp = currentTimestamp
            # now build the event
            eventType = event['type'].encode('utf-8')
            eventTypeData = self.eventTypeData[eventType]
            builder = eventTypeData['builder']()
            builder.setStringValue(event.get('stringValue', ''))
            for intValue in event['intValues']:
                builder.addIntSensor(intValue['id'], intValue['value'])
            for floatValue in event['floatValues']:
                builder.addFloatSensor(floatValue['id'], floatValue['value'])
            builtEvent = builder.build()
            print ("Sending %s %s..." % (eventType, builtEvent)),
        # and send it
            status = self.chat(
                lambda hal:
                    hal.setProperty(eventTypeData['property'],
                        0,
                        builtEvent)).status
            if status == 0:
                print("ok!")
            else:
                print("fail: %s" % status)

parser = argparse.ArgumentParser(description='Diagnostic Events Injector')
parser.add_argument('jsondoc', nargs='+')
parser.add_argument('-s', action='store', dest='deviceid', default=None)

args = parser.parse_args()

halWrapper = DiagnosticHalWrapper(device=args.deviceid)

for arg in args.jsondoc:
    print("Injecting %s" % arg)
    halWrapper.inject(arg)
