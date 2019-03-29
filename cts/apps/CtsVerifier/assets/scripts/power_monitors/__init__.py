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
#

from abc import abstractmethod

class Abstract_Power_Monitor(object):
  """
  Provides a simple interface to use the power meter.
  The implementer should establish communications with the power monitor
  on __init__:

  monitor = <<Implementing Class>>(...)

  Afterward, data can be collected multiple times via the
  series of calls, for example:

      montior.StartDataCollection()
      data = True
      while data is not None:
          data = monitor.CollectData()
          <<do-something-with-data>>
      monitor.StopDataCollection()

  On exit, Close should be called:

      monitor.Close()

  The method GetStatus() can be used to check voltage setting as well as
  ensure status of the USB passthrough connection.
  """

  
  
  def __init__(self ):
      pass  

  @abstractmethod
  def Close(self):
      """Close the connection to the device, preventing
      further interactions.  This should be called only
      when the power monitor is no longer needed
      (e.g. on exit)
      """
      pass

  @abstractmethod
  def GetStatus(self):
    """ Requests and waits for status.  Returns status dictionary.
    This should at a minimum contain entries for "usbPassthroughMode"
    and "outputVoltageSetting" """
    pass

  @abstractmethod
  def SetVoltage(self, v):
    """ Set the output voltage, 0 to disable. """
    pass

  @abstractmethod
  def SetMaxCurrent(self, i):
    """Set the max output current."""
    pass
    
  @abstractmethod
  def SetUsbPassthrough(self, val):
    """ Set the USB passthrough mode: 0 = off, 1 = on,  2 = auto. """
    pass

  @abstractmethod
  def StartDataCollection(self):    
    """ Tell the device to start collecting and sending measurement data. """
    pass
    
  @abstractmethod
  def StopDataCollection(self):
    """ Tell the device to stop collecting measurement data. """
    pass
    
  @abstractmethod
  def CollectData(self, verbose = True):
    """ Return some current samples.  Call StartDataCollection() first.
    Returns None if no data available"""
    pass
