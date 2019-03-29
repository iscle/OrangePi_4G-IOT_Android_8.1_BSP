#!/usr/bin/python
# Copyright (C) 2016 The Android Open Source Project
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

import argparse, json, sys

class MetricsParser(object):
  """Executor of this utility"""

  def __init__(self):
    self._parser = argparse.ArgumentParser('Parse CTS Test metrics jsons')
    self._parser.add_argument('filenames', metavar='filenames', nargs='+',
                              help='filenames of metrics jsons to be parsed')
    self._metrics = []

  def _ParseArgs(self):
    self._args = self._parser.parse_args()

  def _Parse(self, filename):
    json_file = open(filename)
    json_data = json.load(json_file)
    self._metrics.append(json_data)
    self._PrintJson(filename, json_data)

  def _PrintJson(self, filename, json_data):
    print "\nFilename: %s" % filename
    stream_names = json_data.keys()
    for stream_name in stream_names:
      metrics_list = json_data.get(stream_name)
      for metrics in metrics_list:
        print "\nStream Name: %s" % stream_name
        for key in metrics.keys():
          print "Key: %s \t Value: %s" % (key, str(metrics.get(key)))

  def Run(self):
    self._ParseArgs()
    try:
      for filename in self._args.filenames:
        self._Parse(filename)
    except (IOError, ValueError) as e:
      print >> sys.stderr, e
      raise KeyboardInterrupt

if __name__ == '__main__':
  MetricsParser().Run()

