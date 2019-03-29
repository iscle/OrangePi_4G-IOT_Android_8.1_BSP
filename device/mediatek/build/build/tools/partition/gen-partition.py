#!/usr/bin/env python

from __future__ import print_function
import binascii
import json
import os
import struct
import sys
import uuid
import xml.dom.minidom

def write(path, data):
	with open(path, "wb") as f:
		f.write(data)

def crc32(data):
        return binascii.crc32(data) & 0xffffffff

def padding(data, size):
	return data + '\0' * (size - len(data))

def gen_gpt(partition):
	pmbr = ('\0' * 446 +
		"\x00\x00\x02\x00\xee\xff\xff\xff\x01\x00\x00\x00\xff\xff\xff\xff" +
		'\0' * 48 + "\x55\xaa")
	entries = ''
	total = 1024
	for node in partition.childNodes:
		if node.nodeName != "entry":
			continue
		type = uuid.UUID(node.getAttribute("type"))
		uniq = node.getAttribute("uuid")
		uniq = uniq and uuid.UUID(uniq) or uuid.uuid4()
		start = node.getAttribute("start")
		start = start and eval(start) or 0
		end = node.getAttribute("end")
		end = end and eval(end) or 0
		size = node.getAttribute("size")
		size = size and eval(size) or 0
		attr = node.getAttribute("attributes")
		attr = attr and eval(attr) or 0
		name = node.getAttribute("name")
		if size != 0:
			start = total
			total += size
			end = total - 1
		entries += struct.pack("<16s16sQQQ72s",
			type.bytes_le,
			uniq.bytes_le,
			start, end, 0,
			name.encode("utf-16le"))
	entries = padding(entries, 32 * 512)
	lba = eval(partition.getAttribute("lba"))
	uniq = partition.getAttribute("uuid")
	uniq = uniq and uuid.UUID(uniq) or uuid.uuid4()
	header = struct.pack("<8sIIIIQQQQ16sQIII",
		"EFI PART", 0x00010000, 92, 0, 0,
		1, # MyLBA
		1, #lba - 1, # AlternateLBA
		34, # FirstUsableLBA
		lba - 34, # LastUsableLBA
		uniq.bytes_le,
		2, 128, 128, crc32(entries))
	header = padding(header[:16] +
		struct.pack('I', crc32(header)) +
		header[20:], 512)
	return pmbr + header + entries

def write_scatter_partition(f, entry):
	if entry.get("file_name", "NONE") != "NONE":
		entry.setdefault("is_download", True)
		entry.setdefault("operation_type", "UPDATE")
		entry.setdefault("type", "NORMAL_ROM")
	f.write(
"""- partition_index: %s
  partition_name: %s
  file_name: %s
  is_download: %s
  type: %s
  linear_start_addr: %s
  physical_start_addr: %s
  partition_size: %s
  region: %s
  storage: %s
  boundary_check: %s
  is_reserved: %s
  operation_type: %s
  reserve: %s

""" % (entry["partition_index"], entry["partition_name"], entry.get("file_name", "NONE") if entry.get("file_name", "NONE") != "tz.img" or os.getenv("MTK_IN_HOUSE_TEE_SUPPORT","yes") == "yes" else "trustzone.bin",
entry.get("is_download", False) and "true" or "false", entry.get("type", "NONE"), hex(entry["linear_start_addr"]),
hex(entry["physical_start_addr"]), hex(entry["partition_size"]), entry.get("region", "EMMC_USER"),
entry.get("storage", "HW_STORAGE_EMMC"), entry.get("boundary_check", True) and "true" or "false",
entry.get("is_reserved", False) and "true" or "false", entry.get("operation_type", "PROTECTED"),
hex(entry.get("reserve", 0))))

def write_scatter(f, partition, d):
	f.write(
"""############################################################################################################
#
#  General Setting
#
############################################################################################################
- general: MTK_PLATFORM_CFG
  info:
    - config_version: V1.1.2
      platform: MT8173
      project:
      storage: EMMC
      boot_channel: MSDC_0
      block_size: 0x20000
      skip_pmt_operate: true
############################################################################################################
#
#  Layout Setting
#
############################################################################################################
""")
	d["preloader"]["partition_index"] = "SYS0"
	d["pgpt"]["partition_index"] = "SYS1"
	write_scatter_partition(f, d["preloader"])
	write_scatter_partition(f, d["pgpt"])
	i = 2
	total = 1024
	for node in partition.childNodes:
		if node.nodeName != "entry":
			continue
		name = node.getAttribute("name")
		if name not in d:
			continue
		start = node.getAttribute("start")
		start = start and eval(start) or 0
		end = node.getAttribute("end")
		end = end and eval(end) or 0
		size = node.getAttribute("size")
		size = size and eval(size) or 0
		if size != 0:
			start = total
			total += size
		elif end != start:
			size = end + 1 - start
		else:
			size = 0
		entry = d[name]
		entry["partition_name"] = name
		entry["partition_index"] = "SYS%d" % i
		i += 1
		entry["linear_start_addr"] = start * 512
		entry["physical_start_addr"] = start * 512
		entry["partition_size"] = size * 512
		write_scatter_partition(f, entry)
	d["sgpt"]["partition_index"] = "SYS%d" % i
	write_scatter_partition(f, d["sgpt"])

def sanity_check(path, partition):
	err = 0
	lba = eval(partition.getAttribute("lba"))
	usable = (34, lba - 34)
	used = {}
	total = 1024
	for node in partition.childNodes:
		if node.nodeName != "entry":
			continue
		name = node.getAttribute("name")
		start = node.getAttribute("start")
		start = start and eval(start) or 0
		end = node.getAttribute("end")
		end = end and eval(end) or 0
		size = node.getAttribute("size")
		size = size and eval(size) or 0
		if size != 0:
			start = total
			total += size
			end = total - 1
		if start > end:
			print("%s: error: partition '%s': start lba (%d) > end lba (%d)" %
				(path, name, start, end), file = sys.stderr)
			err += 1
		if start < usable[0] or end > usable[1]:
			print("%s: error: partition '%s': (%d...%d) out of usable range (%d...%d)" %
				(path, name, start, end, usable[0], usable[1]), file = sys.stderr)
			err += 1
		for i in used:
			if (used[i][0] <= start and start <= used[i][1] or
				used[i][0] <= end and end <= used[i][1]):
				print("%s: error: partition '%s': (%d...%d) overlapped with partition '%s' (%d...%d)" %
					(path, name, start, end, i, used[i][0], used[i][1]), file = sys.stderr)
				err += 1
		used[name] = (start, end)
	return err

def main(argv):
	if len(argv) != 3 and len(argv) != 4:
		print("Usage: %s partition.xml MBR [scatter.txt]" % argv[0])
		exit(1)
	root = xml.dom.minidom.parse(argv[1])
	for partition in root.childNodes:
		if partition.nodeName == "partition":
			break
	else:
		raise Exception("partition not found")
	if sanity_check(argv[1], partition):
		return 1
	write(argv[2], gen_gpt(partition))
	if len(argv) == 4:
		with open(os.path.join(os.path.dirname(__file__), "scatter.json"), "r") as f:
			d = json.load(f)
		with open(argv[3], "w") as f:
			write_scatter(f, partition, d)
	return 0

if __name__ == "__main__":
	sys.exit(main(sys.argv))
