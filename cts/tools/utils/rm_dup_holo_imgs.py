#!/usr/bin/env python
#
# Copyright (C) 2014 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the 'License');
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an 'AS IS' BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
import hashlib
import os
import subprocess
import sys

densities = [
    "ldpi",
    "mdpi",
    "tvdpi",
    "hdpi",
    "xhdpi",
    "400dpi",
    "xxhdpi",
    "xxxhdpi"
]

# A script to find holo images which are duplicated in the landscape and
# portrait folder. The landscape images will then be deleted as Android will
# look up landscape resources in the portrait folder if it doesn't exist in the
# landscape folder. This will reduce the size of the Holo test case.
def main(argv):
  run(True)
  run(False)

def run(sw):
  for density in densities:
    portDir = getDirName(density, sw, False)
    landDir = getDirName(density, sw, True)
    portrait = getAllHashes(portDir)
    landscape = getAllHashes(landDir)
    for f in portrait:
      if f in landscape and landscape[f] == portrait[f]:
        subprocess.call(["rm", landDir + "/" + f])

def getAllHashes(dirName):
  files = {}
  for f in os.listdir(dirName):
    if f.endswith(".png"):
      files[f] = getHash(open(dirName + "/" + f, 'rb'))
  return files

def getHash(f):
  return hashlib.sha1(f.read()).hexdigest()

def getDirName(density, sw, land):
  name = "drawable-"
  if sw:
    name += "sw600dp-"
  if land:
    name += "land-"
  return name + density

if __name__ == '__main__':
  main(sys.argv)
