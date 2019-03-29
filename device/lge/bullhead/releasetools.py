#
# Copyright (C) 2015 The Android Open-Source Project
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

import common
import struct


def FindRadio(zipfile):
  try:
    return zipfile.read("RADIO/radio.img")
  except KeyError:
    return None


def FullOTA_InstallEnd(info):
  try:
    bootloader_img = info.input_zip.read("RADIO/bootloader.img")
  except KeyError:
    print "no bootloader.img in target_files; skipping install"
  else:
    WriteBootloader(info, bootloader_img)

  radio_img = FindRadio(info.input_zip)
  if radio_img:
    WriteRadio(info, radio_img)
  else:
    print "no radio.img in target_files; skipping install"


def IncrementalOTA_VerifyEnd(info):
  target_radio_img = FindRadio(info.target_zip)
  if common.OPTIONS.full_radio:
    if not target_radio_img:
      assert False, "full radio option specified but no radio img found"
    else:
      return
  source_radio_img = FindRadio(info.source_zip)
  if not target_radio_img or not source_radio_img:
    return
  if source_radio_img != target_radio_img:
    info.script.CacheFreeSpaceCheck(len(source_radio_img))
    radio_type, radio_device = common.GetTypeAndDevice("/radio", info.info_dict)
    info.script.PatchCheck("%s:%s:%d:%s:%d:%s" % (
        radio_type, radio_device,
        len(source_radio_img), common.sha1(source_radio_img).hexdigest(),
        len(target_radio_img), common.sha1(target_radio_img).hexdigest()))


def IncrementalOTA_InstallEnd(info):
  try:
    target_bootloader_img = info.target_zip.read("RADIO/bootloader.img")
    try:
      source_bootloader_img = info.source_zip.read("RADIO/bootloader.img")
    except KeyError:
      source_bootloader_img = None

    if source_bootloader_img == target_bootloader_img:
      print "bootloader unchanged; skipping"
    else:
      WriteBootloader(info, target_bootloader_img)
  except KeyError:
    print "no bootloader.img in target target_files; skipping install"

  tf = FindRadio(info.target_zip)
  if not tf:
    # failed to read TARGET radio image: don't include any radio in update.
    print "no radio.img in target target_files; skipping install"
    # we have checked the existence of the radio image in
    # IncrementalOTA_VerifyEnd(), so it won't reach here.
    assert common.OPTIONS.full_radio == False
  else:
    tf = common.File("radio.img", tf)

    sf = FindRadio(info.source_zip)
    if not sf or common.OPTIONS.full_radio:
      # failed to read SOURCE radio image or one has specified the option to
      # include the whole target radio image.
      print("no radio image in source target_files or full_radio specified; "
            "installing complete image")
      WriteRadio(info, tf.data)
    else:
      sf = common.File("radio.img", sf)

      if tf.sha1 == sf.sha1:
        print "radio image unchanged; skipping"
      else:
        diff = common.Difference(tf, sf, diff_program="bsdiff")
        common.ComputeDifferences([diff])
        _, _, d = diff.GetPatch()
        if d is None or len(d) > tf.size * common.OPTIONS.patch_threshold:
          # computing difference failed, or difference is nearly as
          # big as the target:  simply send the target.
          WriteRadio(info, tf.data)
        else:
          common.ZipWriteStr(info.output_zip, "radio.img.p", d)
          info.script.Print("Patching radio...")
          radio_type, radio_device = common.GetTypeAndDevice(
              "/radio", info.info_dict)
          info.script.ApplyPatch(
              "%s:%s:%d:%s:%d:%s" % (radio_type, radio_device,
                                     sf.size, sf.sha1, tf.size, tf.sha1),
              "-", tf.size, tf.sha1, sf.sha1, "radio.img.p")


def WriteRadio(info, radio_img):
  info.script.Print("Writing radio...")
  common.ZipWriteStr(info.output_zip, "radio.img", radio_img)
  _, device = common.GetTypeAndDevice("/radio", info.info_dict)
  info.script.AppendExtra(
      'package_extract_file("radio.img", "%s");' % (device,))


# /* msm8992 bootloader.img format */
#
# #define BOOTLDR_MAGIC "BOOTLDR!"
# #define BOOTLDR_MAGIC_SIZE 8
#
# struct bootloader_images_header {
#         char magic[BOOTLDR_MAGIC_SIZE];
#         unsigned int num_images;
#         unsigned int start_offset;
#         unsigned int bootldr_size;
#         struct {
#                 char name[64];
#                 unsigned int size;
#         } img_info[];
# };
#
def ParseBootloaderHeader(bootloader):
  header_fmt = "<8sIII"
  header_size = struct.calcsize(header_fmt)
  magic, num_images, start_offset, bootloader_size = struct.unpack(
      header_fmt, bootloader[:header_size])
  assert magic == "BOOTLDR!", "bootloader.img bad magic value"

  img_info_fmt = "<64sI"
  img_info_size = struct.calcsize(img_info_fmt)

  imgs = [struct.unpack(img_info_fmt,
                        bootloader[header_size+i*img_info_size:
                                   header_size+(i+1)*img_info_size])
          for i in range(num_images)]

  p = start_offset
  img_dict = {}
  for name, size in imgs:
    img_dict[trunc_to_null(name)] = p, size
    p += size
  assert p - start_offset == bootloader_size, "bootloader.img corrupted"

  return img_dict


# bullhead's bootloader.img contains 11 separate images.
# Each goes to its own partition:
#    sbl1, tz, rpm, aboot, sdi, imgdata, pmic, hyp, sec, keymaster, cmnlib
#
# bullhead also has 8 backup partitions:
#    sbl1, tz, rpm, aboot, pmic, hyp, keymaster, cmnlib
#
release_backup_partitions = "sbl1 tz rpm aboot pmic hyp keymaster cmnlib"
debug_backup_partitions = "sbl1 tz rpm aboot pmic hyp keymaster cmnlib"
release_nobackup_partitions = "sdi imgdata sec"
debug_nobackup_partitions = "sdi imgdata sec"


def WriteBootloader(info, bootloader):
  info.script.Print("Writing bootloader...")

  img_dict = ParseBootloaderHeader(bootloader)

  common.ZipWriteStr(info.output_zip, "bootloader-flag.txt",
                     "updating-bootloader" + "\0" * 13)
  common.ZipWriteStr(info.output_zip, "bootloader-flag-clear.txt", "\0" * 32)

  _, misc_device = common.GetTypeAndDevice("/misc", info.info_dict)

  info.script.AppendExtra(
      'package_extract_file("bootloader-flag.txt", "%s");' % (misc_device,))

  # failed sbl updates, may render the handset unusable/unrestorable.
  # Hence adopt below strategy for updates,enabling restore at all times.
  # 1. Flash backup partitions
  # 2. patch secondary pte's to swap primary/backup, and enable secondary gpt
  # 3. Flash psuedo-backup partions, effectively flashing primary partitions
  # 4. restore secondary pte's and restore primary gpt
  # 5. Flash all other non backup partitions
  #
  # Depending on the build fingerprint, we can decide which partitions
  # to update.
  fp = info.info_dict["build.prop"]["ro.build.fingerprint"]
  if "release-keys" in fp:
    to_bkp_flash = release_backup_partitions.split()
    to_flash = release_nobackup_partitions.split()
  else:
    to_bkp_flash = debug_backup_partitions.split()
    to_flash = debug_nobackup_partitions.split()

  # Write the images to separate files in the OTA package
  # and flash backup partitions
  for i in to_bkp_flash:
    try:
      _, device = common.GetTypeAndDevice("/"+i+"bak", info.info_dict)
    except KeyError:
      print "skipping flash of %s; not in recovery.fstab" % (i,)
      continue
    common.ZipWriteStr(info.output_zip, "bootloader.%s.img" % (i,),
                       bootloader[img_dict[i][0]:
                                  img_dict[i][0]+img_dict[i][1]])

    info.script.AppendExtra(
        'package_extract_file("bootloader.%s.img", "%s");' % (i, device))

  target_device = info.info_dict["build.prop"]["ro.product.device"]
  # swap ptes in secondary and force secondary gpt
  info.script.AppendExtra("lge_"+target_device+"_update_gpt();")

  # flash again after swap, effectively flashing primary
  # pte's are not re-read, hence primary is psuedo-secondary
  for i in to_bkp_flash:
    try:
      _, device = common.GetTypeAndDevice("/"+i, info.info_dict)
    except KeyError:
      print "skipping flash of %s; not in recovery.fstab" % (i,)
      continue
    info.script.AppendExtra(
        'package_extract_file("bootloader.%s.img", "%s");' % (i, device))

  # restore secondary gpt for correct mappings and enable primary gpt
  info.script.AppendExtra("lge_"+target_device+"_recover_gpt();")

  # Write the images to separate files in the OTA package
  for i in to_flash:
    try:
      _, device = common.GetTypeAndDevice("/"+i, info.info_dict)
    except KeyError:
      print "skipping flash of %s; not in recovery.fstab" % (i,)
      continue
    common.ZipWriteStr(info.output_zip, "bootloader.%s.img" % (i,),
                       bootloader[img_dict[i][0]:
                                  img_dict[i][0]+img_dict[i][1]])

    info.script.AppendExtra(
        'package_extract_file("bootloader.%s.img", "%s");' % (i, device))

  info.script.AppendExtra(
      'package_extract_file("bootloader-flag-clear.txt", "%s");' %
      (misc_device,))


def trunc_to_null(s):
  if '\0' in s:
    return s[:s.index('\0')]
  else:
    return s
