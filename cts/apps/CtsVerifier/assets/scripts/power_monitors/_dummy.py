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

from . import Abstract_Power_Monitor

class Power_Monitor(Abstract_Power_Monitor):
  """
  Dummy implementation for internal use only to test host-to-device
  interactions without need for a real power monitor.  This is not
  to be used in any way as part of CtsVerifier or CTS verification
  activities in general.
  """

  
  def __init__(self, device = None, wait = False, log_file_id = None ):
      self._device = device
      self._usbpassthroughmode = 1
      self._voltage = 0.0
      self._data_active = False
      self._sequence = 0
      
  def __del__(self):
      self.Close()

  def Close(self):
      pass
    
  @staticmethod
  def Discover():
      return ["dummy_monitor"]

  def GetStatus(self):
    """ Requests and waits for status.  Returns status dictionary. """
    return {"usbPassthroughMode": self._usbpassthroughmode,
            "sampleRate":1}


  def RampVoltage(self, start, end):
      self._voltage = end   

  def SetVoltage(self, v):
    """ Set the output voltage, 0 to disable. """
    self._voltage = v

  def SetMaxCurrent(self, i):
    """Set the max output current."""
    self._max_current = i
    
  def SetUsbPassthrough(self, val):
    """ Set the USB passthrough mode: 0 = off, 1 = on,  2 = auto. """
    self._usbpassthroughmode = val

  def StartDataCollection(self):    
    """ Tell the device to start collecting and sending measurement data. """
    self._data_active = True
    
  def StopDataCollection(self):
    """ Tell the device to stop collecting measurement data. """
    self._data_active = False
    
  def CollectData(self, verbose = True):
    """ Return some current samples.  Call StartDataCollection() first. """
    #self.log("Collecting data ...", debug = True)
    import random
    if self._data_active:
        base = [0.003, 0.003, 0.003, (self._sequence%4)*0.0005]
        self._sequence += 1
        values = [ random.gauss(base[(self._sequence-1)%4], 0.0005) for _ in range(100)]
    else:
        values = None
    return values
