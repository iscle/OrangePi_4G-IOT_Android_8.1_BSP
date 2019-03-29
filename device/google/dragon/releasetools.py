# Copyright (C) 2015 The Android Open Source Project
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

"""Add extra commands needed for firmwares update during OTA."""

import common

def FullOTA_InstallEnd(info):
  # copy the data into the package.
  try:
    bootloader_img = info.input_zip.read("RADIO/bootloader.img")
    ec_img = info.input_zip.read("RADIO/ec.bin")
  except KeyError:
    print "no firmware images in target_files; skipping install"
    return
  # copy the data into the package.
  common.ZipWriteStr(info.output_zip, "bootloader.img", bootloader_img)
  common.ZipWriteStr(info.output_zip, "ec.bin", ec_img)

  # emit the script code to trigger the firmware updater on the device
  info.script.AppendExtra(
    """dragon.firmware_update(package_extract_file("bootloader.img"), package_extract_file("ec.bin"));""")

def IncrementalOTA_InstallEnd(info):
  try:
    target_bootloader_img = info.target_zip.read("RADIO/bootloader.img")
    target_ec_img = info.target_zip.read("RADIO/ec.bin")
  except KeyError:
    print "no firmware images in target target_files; skipping install"
    return

  # copy the data into the package irrespective of source and target versions.
  # If previous OTA missed a firmware update, we can try that again on the next
  # OTA.
  common.ZipWriteStr(info.output_zip, "bootloader.img", target_bootloader_img)
  common.ZipWriteStr(info.output_zip, "ec.bin", target_ec_img)

  # emit the script code to trigger the firmware updater on the device
  info.script.AppendExtra(
    """dragon.firmware_update(package_extract_file("bootloader.img"), package_extract_file("ec.bin"));""")
