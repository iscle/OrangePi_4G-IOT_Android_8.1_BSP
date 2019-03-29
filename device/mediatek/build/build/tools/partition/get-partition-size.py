#!/usr/bin/env python

import os
import sys
import xml.dom.minidom

def main(argv):
	if len(argv) != 3 and len(argv) != 4:
		print "Usage: %s partition.xml partition-name [reserved-bytes]" % argv[0]
		exit(1)
	reserved = len(argv) == 4 and eval(argv[3]) or 0
	root = xml.dom.minidom.parse(argv[1])
	for partition in root.childNodes:
		if partition.nodeName == "partition":
			break
	else:
		raise Exception("partition not found")
	for node in partition.childNodes:
		if node.nodeName != "entry":
			continue
		name = node.getAttribute("name")
		if name != argv[2]:
			continue
		start = node.getAttribute("start")
		start = start and eval(start) or 0
		end = node.getAttribute("end")
		end = end and eval(end) or 0
		size = node.getAttribute("size")
		size = size and eval(size) or 0
		if size != 0:
			result = size * 512 - reserved
		elif end != start:
			result = (end - start + 1) * 512 - reserved
		else:
			result = 0
		if result < 0:
			exit(1)
		print result
		break
	else:
		exit(1)

if __name__ == "__main__":
        main(sys.argv)
