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

# A simple GUI to remotely actuate the Vehicle HAL via the eumalator

import sys
from threading import Thread
from PyQt4.QtCore import *
from PyQt4.QtGui import *

import VehicleHalProto_pb2
from vhal_emulator import Vhal
import vhal_consts_2_1 as c


# Define a simple thread that receives messages from a vhal object (v) and prints them
def rxThread(v):
    while(1):
        msg = v.rxMsg()
        if (msg.msg_type == VehicleHalProto_pb2.SET_PROPERTY_RESP):
            if msg.status == 0:
                print "Success ("+str(msg.status)+")"
            else:
                print "Error ("+str(msg.status)+")"
        else:
            print msg;


# Main window setup
def window():
    app = QApplication(sys.argv)
    widget = QWidget()
    widget.setWindowTitle("VHal Driver")
    widget.setGeometry(100,100,200,50)
    topLevelLayout = QHBoxLayout()
    widget.setLayout(topLevelLayout)

    shiftLayout = QVBoxLayout()
    topLevelLayout.addLayout(shiftLayout)

    gearTitle = QLabel(widget)
    gearTitle.setText("Gear Shift")
    shiftLayout.addWidget(gearTitle);

    gearDisplay = QLabel(widget)
    shiftLayout.addWidget(gearDisplay);

    slider = QSlider(Qt.Vertical)
    slider.setMinimum(0)
    slider.setMaximum(2)
    slider.setInvertedAppearance(True)
    slider.valueChanged.connect(lambda:sliderMove(slider, gearDisplay))
    shiftLayout.addWidget(slider)
    sliderMove(slider, gearDisplay)


    buttonLayout = QVBoxLayout()
    topLevelLayout.addLayout(buttonLayout)

    signalButtonGroup = QButtonGroup()

    bNoSignal = QPushButton("None")
    bNoSignal.setCheckable(True)
    bNoSignal.setChecked(True)
    buttonLayout.addWidget(bNoSignal)
    signalButtonGroup.addButton(bNoSignal)

    bHazards = QPushButton("Hazards")
    bHazards.setCheckable(True)
    buttonLayout.addWidget(bHazards)
    signalButtonGroup.addButton(bHazards)

    bLeft = QPushButton("Left")
    bLeft.setCheckable(True)
    buttonLayout.addWidget(bLeft)
    signalButtonGroup.addButton(bLeft)

    bRight = QPushButton("Right")
    bRight.setCheckable(True)
    buttonLayout.addWidget(bRight)
    signalButtonGroup.addButton(bRight)

    signalButtonGroup.buttonClicked.connect(lambda:onSignalClicked(signalButtonGroup))

    widget.show()
    sys.exit(app.exec_())


def onSignalClicked(group):
    print "signal "+group.checkedButton().text()+" is active"
    try:
        vhal.setProperty(c.VEHICLEPROPERTY_TURN_SIGNAL_STATE, 0, group.checkedId())
    except:
        print "Ignoring error setting property 0x{:08X}".format(c.VEHICLEPROPERTY_TURN_SIGNAL_STATE)


def sliderMove(slider, gearDisplay):
    if slider.value() == 0:
        gearName = 'park'
        vhal.setProperty(c.VEHICLEPROPERTY_GEAR_SELECTION, 0, c.VEHICLEGEAR_GEAR_PARK)
    elif slider.value() == 1:
        gearName = 'reverse'
        vhal.setProperty(c.VEHICLEPROPERTY_GEAR_SELECTION, 0, c.VEHICLEGEAR_GEAR_REVERSE)
    elif slider.value() == 2:
        gearName = 'drive'
        vhal.setProperty(c.VEHICLEPROPERTY_GEAR_SELECTION, 0, c.VEHICLEGEAR_GEAR_DRIVE)
    else:
        gearName = "UNK"
    print "slider "+slider.objectName()+" requested "+str(slider.value())+" = "+gearName
    gearDisplay.setText(gearName)


if __name__ == '__main__':
    print "Starting VHal driver GUI"
    vhal = Vhal(c.vhal_types_2_0)

    # Start a receive thread to consume any replies from the vhal
    print "Starting receiver thread"
    rx = Thread(target=rxThread, args=(vhal,))
    rx.setDaemon(True)
    rx.start()

    # Put the car in park so we start in a known state (consistent with the GUI default state)
    vhal.setProperty(c.VEHICLEPROPERTY_GEAR_SELECTION, 0, c.VEHICLEGEAR_GEAR_PARK)

    # Start the main UI -- never returns
    window()
