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


import fcntl
import logging
logging.getLogger().setLevel(logging.ERROR)

import os.path
import select
import stat
import struct
import sys
import time
import collections
import socket
import glob
import signal
import serial           # http://pyserial.sourceforge.net/

#Set to True if you want log output to go to screen:
LOG_TO_SCREEN = False

TIMEOUT_SERIAL = 1 #seconds

#ignore SIG CONTINUE signals
for signum in [signal.SIGCONT]:              
  signal.signal(signum, signal.SIG_IGN)

try:
  from . import Abstract_Power_Monitor
except:
  sys.exit("You cannot run 'monsoon.py' directly.  Run 'execut_power_tests.py' instead.")
  
class Power_Monitor(Abstract_Power_Monitor):
  """
  Provides a simple class to use the power meter, e.g.
  mon = monsoon.Power_Monitor()
  mon.SetVoltage(3.7)
  mon.StartDataCollection()
  mydata = []
  while len(mydata) < 1000:
    mydata.extend(mon.CollectData())
  mon.StopDataCollection()
  """
  _do_log = False

  @staticmethod
  def lock( device ):
      tmpname = "/tmp/monsoon.%s.%s" % ( os.uname()[0],
                                         os.path.basename(device))
      lockfile = open(tmpname, "w")
      try:  # use a lockfile to ensure exclusive access
          fcntl.lockf(lockfile, fcntl.LOCK_EX | fcntl.LOCK_NB)
          logging.debug("Locked device %s"%device)
      except IOError as e:
          self.log("device %s is in use" % dev)
          sys.exit('device in use')
      return lockfile
  
  def to_string(self):
      return self._devicename
  
  def __init__(self, device = None, wait = False, log_file_id= None ):
    """
    Establish a connection to a Power_Monitor.
    By default, opens the first available port, waiting if none are ready.
    A particular port can be specified with "device".
    With wait=0, IOError is thrown if a device is not immediately available.
    """
    self._lockfile = None
    self._logfile = None
    self.ser = None
    for signum in [signal.SIGALRM, signal.SIGHUP, signal.SIGINT,
                   signal.SIGILL, signal.SIGQUIT,
                   signal.SIGTRAP,signal.SIGABRT, signal.SIGIOT, signal.SIGBUS,
                   signal.SIGFPE, signal.SIGSEGV, signal.SIGUSR2, signal.SIGPIPE,
                   signal.SIGTERM]:
      signal.signal(signum, self.handle_signal)

    self._coarse_ref = self._fine_ref = self._coarse_zero = self._fine_zero = 0
    self._coarse_scale = self._fine_scale = 0
    self._last_seq = 0
    self.start_voltage = 0
        
    if device:
      if isinstance( device, serial.Serial ):
        self.ser = device
        
    else:
        device_list = None
        while not device_list:
            device_list = Power_Monitor.Discover()
            if not device_list and wait:
                time.sleep(1.0)
                logging.info("No power monitor serial devices found.  Retrying...")
            elif not device_list and not wait:
                logging.error("No power monitor serial devices found.  Exiting")
                self.Close()
                sys.exit("No power monitor serial devices found")
                
        if device_list:
            if len(device_list) > 1:
                logging.error("=======================================")
                logging.error("More than one power monitor discovered!")
                logging.error("Test may not execute properly.Aborting test.")
                logging.error("=======================================")
                sys.exit("More than one power monitor connected.")
            device = device_list[0].to_string() # choose the first one
            if len(device_list) > 1:
                logging.info("More than one device found.  Using %s"%device)
            else:
                logging.info("Power monitor @ %s"%device)
        else: raise IOError("No device found")
          
    self._lockfile = Power_Monitor.lock( device )
    if log_file_id is not None:
        self._logfilename = "/tmp/monsoon_%s_%s.%s.log" % (os.uname()[0], os.path.basename(device),
                                                            log_file_id)
        self._logfile = open(self._logfilename,'a')
    else:
        self._logfile = None
    try:
        self.ser = serial.Serial(device, timeout= TIMEOUT_SERIAL)
    except Exception as e:
      self.log( "error opening device %s: %s" % (dev, e))
      self._lockfile.close()
      raise
    logging.debug("Setting up power monitor...")
    self._devicename = device
    #just in case, stop any active data collection on monsoon
    self._dataCollectionActive = True
    self.StopDataCollection()
    logging.debug("Flushing input...")
    self._FlushInput()  # discard stale input
    logging.debug("Getting status....")
    status = self.GetStatus()
    
    if not status:
      self.log( "no response from device %s" % device)
      self._lockfile.close()
      raise IOError("Failed to get status from device")
    self.start_voltage = status["voltage1"]
    
  def __del__(self):
    self.Close()

  def Close(self):
    if self._logfile:
      print("=============\n"+\
            "Power Monitor log file can be found at '%s'"%self._logfilename +
            "=============\n")
      self._logfile.close()
      self._logfile = None
    if (self.ser):
      #self.StopDataCollection()
      self.ser.flush()
      self.ser.close()
      self.ser = None
    if self._lockfile:
      self._lockfile.close()

  def log(self, msg , debug = False):
    if self._logfile: self._logfile.write( msg + "\n")
    if not debug and LOG_TO_SCREEN:
      logging.error( msg )
    else:
      logging.debug(msg)

  def handle_signal( self, signum, frame):
    if self.ser:
      self.ser.flush()
      self.ser.close()
      self.ser = None
    self.log("Got signal %d"%signum)
    sys.exit("\nGot signal %d\n"%signum)
    
  @staticmethod
  def Discover():
    monsoon_list = []
    elapsed = 0
    logging.info("Discovering power monitor(s)...")
    ser_device_list = glob.glob("/dev/ttyACM*")
    logging.info("Seeking devices %s"%ser_device_list)
    for dev in ser_device_list:
        try:
            lockfile = Power_Monitor.lock( dev )
        except:
            logging.info( "... device %s in use, skipping"%dev)
            continue
        tries = 0
        ser = None
        while ser is None and tries < 100:
             try:  # try to open the device
                ser = serial.Serial( dev, timeout=TIMEOUT_SERIAL)
             except Exception as e:
                logging.error(  "error opening device %s: %s" % (dev, e) )
                tries += 1
                time.sleep(2);
                ser = None
        logging.info("... found device %s"%dev)
        lockfile.close()#will be re-locked once monsoon instance created
        logging.debug("unlocked")
        if not ser:
            continue
        if ser is not None:
            try:
                monsoon = Power_Monitor(device = dev)
                status = monsoon.GetStatus()
                
                if not status:
                    monsoon.log("... no response from device %s, skipping")
                    continue
                else:
                    logging.info("... found power monitor @ %s"%dev)
                    monsoon_list.append( monsoon )
            except:
                import traceback
                traceback.print_exc()
                logging.error("... %s appears to not be a monsoon device"%dev)
    logging.debug("Returning list of %s"%monsoon_list)
    return monsoon_list

  def GetStatus(self):
    """ Requests and waits for status.  Returns status dictionary. """

    # status packet format
    self.log("Getting status...", debug = True)
    STATUS_FORMAT = ">BBBhhhHhhhHBBBxBbHBHHHHBbbHHBBBbbbbbbbbbBH"
    STATUS_FIELDS = [
        "packetType", "firmwareVersion", "protocolVersion",
        "mainFineCurrent", "usbFineCurrent", "auxFineCurrent", "voltage1",
        "mainCoarseCurrent", "usbCoarseCurrent", "auxCoarseCurrent", "voltage2",
        "outputVoltageSetting", "temperature", "status", "leds",
        "mainFineResistor", "serialNumber", "sampleRate",
        "dacCalLow", "dacCalHigh",
        "powerUpCurrentLimit", "runTimeCurrentLimit", "powerUpTime",
        "usbFineResistor", "auxFineResistor",
        "initialUsbVoltage", "initialAuxVoltage",
        "hardwareRevision", "temperatureLimit", "usbPassthroughMode",
        "mainCoarseResistor", "usbCoarseResistor", "auxCoarseResistor",
        "defMainFineResistor", "defUsbFineResistor", "defAuxFineResistor",
        "defMainCoarseResistor", "defUsbCoarseResistor", "defAuxCoarseResistor",
        "eventCode", "eventData", ]

    self._SendStruct("BBB", 0x01, 0x00, 0x00)
    while True:  # Keep reading, discarding non-status packets
      bytes = self._ReadPacket()
      if not bytes: return None
      if len(bytes) != struct.calcsize(STATUS_FORMAT) or bytes[0] != "\x10":
        self.log("wanted status, dropped type=0x%02x, len=%d" % (
                ord(bytes[0]), len(bytes)))
        continue

      status = dict(zip(STATUS_FIELDS, struct.unpack(STATUS_FORMAT, bytes)))
      assert status["packetType"] == 0x10
      for k in status.keys():
        if k.endswith("VoltageSetting"):
          status[k] = 2.0 + status[k] * 0.01
        elif k.endswith("FineCurrent"):
          pass # needs calibration data
        elif k.endswith("CoarseCurrent"):
          pass # needs calibration data
        elif k.startswith("voltage") or k.endswith("Voltage"):
          status[k] = status[k] * 0.000125
        elif k.endswith("Resistor"):
          status[k] = 0.05 + status[k] * 0.0001
          if k.startswith("aux") or k.startswith("defAux"): status[k] += 0.05
        elif k.endswith("CurrentLimit"):
          status[k] = 8 * (1023 - status[k]) / 1023.0
      #self.log( "Returning requested status: \n %s"%(status), debug = True)
      return status

  def RampVoltage(self, start, end):
    v = start
    if v < 3.0: v = 3.0       # protocol doesn't support lower than this
    while (v < end):
      self.SetVoltage(v)
      v += .1
      time.sleep(.1)
    self.SetVoltage(end)

  def SetVoltage(self, v):
    """ Set the output voltage, 0 to disable. """
    self.log("Setting voltage to %s..."%v, debug = True)
    if v == 0:
      self._SendStruct("BBB", 0x01, 0x01, 0x00)
    else:
      self._SendStruct("BBB", 0x01, 0x01, int((v - 2.0) * 100))
    self.log("...Set voltage", debug = True)

  def SetMaxCurrent(self, i):
    """Set the max output current."""
    assert i >= 0 and i <= 8
    self.log("Setting max current to %s..."%i, debug = True)
    val = 1023 - int((i/8)*1023)
    self._SendStruct("BBB", 0x01, 0x0a, val & 0xff)
    self._SendStruct("BBB", 0x01, 0x0b, val >> 8)
    self.log("...Set max current.", debug = True)
    
  def SetUsbPassthrough(self, val):
    """ Set the USB passthrough mode: 0 = off, 1 = on,  2 = auto. """
    self._SendStruct("BBB", 0x01, 0x10, val)

  def StartDataCollection(self):    
    """ Tell the device to start collecting and sending measurement data. """
    self.log("Starting data collection...", debug = True)
    self._SendStruct("BBB", 0x01, 0x1b, 0x01) # Mystery command
    self._SendStruct("BBBBBBB", 0x02, 0xff, 0xff, 0xff, 0xff, 0x03, 0xe8)
    self.log("...started", debug = True)
    self._dataCollectionActive = True
    
  def StopDataCollection(self):
    """ Tell the device to stop collecting measurement data. """
    self._SendStruct("BB", 0x03, 0x00) # stop
    if self._dataCollectionActive:
      while self.CollectData(False) is not None:
        pass
    self._dataCollectionActive = False
    
  def CollectData(self, verbose = True):
    """ Return some current samples.  Call StartDataCollection() first. """
    #self.log("Collecting data ...", debug = True)
    while True:  # loop until we get data or a timeout
      bytes = self._ReadPacket(verbose)
      
      if not bytes: return None
      if len(bytes) < 4 + 8 + 1 or bytes[0] < "\x20" or bytes[0] > "\x2F":
        if verbose: self.log( "wanted data, dropped type=0x%02x, len=%d" % (
          ord(bytes[0]), len(bytes)), debug=verbose)
        continue

      seq, type, x, y = struct.unpack("BBBB", bytes[:4])
      data = [struct.unpack(">hhhh", bytes[x:x+8])
              for x in range(4, len(bytes) - 8, 8)]

      if self._last_seq and seq & 0xF != (self._last_seq + 1) & 0xF:
        self.log( "data sequence skipped, lost packet?" )
      self._last_seq = seq

      if type == 0:
        if not self._coarse_scale or not self._fine_scale:
          self.log("waiting for calibration, dropped data packet")
          continue

        out = []
        for main, usb, aux, voltage in data:
          if main & 1:
            out.append(((main & ~1) - self._coarse_zero) * self._coarse_scale)
          else:
            out.append((main - self._fine_zero) * self._fine_scale)
        #self.log("...Collected %d samples"%(len(out)), debug = True)
        return out

      elif type == 1:
        self._fine_zero = data[0][0]
        self._coarse_zero = data[1][0]

      elif type == 2:
        self._fine_ref = data[0][0]
        self._coarse_ref = data[1][0]

      else:
        self.log( "discarding data packet type=0x%02x" % type)
        continue

      if self._coarse_ref != self._coarse_zero:
        self._coarse_scale = 2.88 / (self._coarse_ref - self._coarse_zero)
      if self._fine_ref != self._fine_zero:
        self._fine_scale = 0.0332 / (self._fine_ref - self._fine_zero)


  def _SendStruct(self, fmt, *args):
    """ Pack a struct (without length or checksum) and send it. """
    data = struct.pack(fmt, *args)
    data_len = len(data) + 1
    checksum = (data_len + sum(struct.unpack("B" * len(data), data))) % 256
    out = struct.pack("B", data_len) + data + struct.pack("B", checksum)
    self.ser.write(out)
    self.ser.flush()

  def _ReadPacket(self, verbose = True):
    """ Read a single data record as a string (without length or checksum). """
    len_char = self.ser.read(1)
    if not len_char:
      if verbose: self.log( "timeout reading from serial port" )
      return None

    data_len = struct.unpack("B", len_char)
    data_len = ord(len_char)
    if not data_len: return ""

    result = self.ser.read(data_len)
    if len(result) != data_len: return None
    body = result[:-1]
    checksum = (data_len + sum(struct.unpack("B" * len(body), body))) % 256
    if result[-1] != struct.pack("B", checksum):
      self.log( "Invalid checksum from serial port" )
      return None
    return result[:-1]

  def _FlushInput(self):
    """ Flush all read data until no more available. """
    self.ser.flushInput()
    flushed = 0
    self.log("Flushing input...", debug = True)
    while True:
      ready_r, ready_w, ready_x = select.select([self.ser], [], [self.ser], 0)
      if len(ready_x) > 0:
        self.log( "exception from serial port" )
        return None
      elif len(ready_r) > 0:
        flushed += 1
        self.ser.read(1)  # This may cause underlying buffering.
        self.ser.flush()  # Flush the underlying buffer too.
      else:
        break
    if flushed > 0:
      self.log( "flushed >%d bytes" % flushed, debug = True )

