#!/usr/bin/env python

import sys, os, argparse, ConfigParser

default_filename='platforms.config'

def list_platforms():
    for p in platforms: print p

def shortlist_platforms():
    for p in platforms: print p,

def get_images():
    if args.platform:
        try:
            value = config.get(args.platform, "EXTRA_FILES")
            print value,
        except:
            pass
        try:
            value = config.get(args.platform, "BUILD_ATF")
            if value == "yes":
                print "bl1.bin fip.bin"
                return True
        except:
            try:
                value = config.get(args.platform, "UEFI_BIN")
                print value
                return True
            except:
                print "No images found!"
    else:
        print "No platform specified!"

    return False

def get_option():
    if args.platform:
        if args.option:
            try:
                value = config.get(args.platform, args.option)
                if value:
                    print value
                    return True
            except:
                return True   # Option not found, return True, and no output
        else:
            print "No option specified!"
    else:
        print "No platform specified!"
    return False

parser = argparse.ArgumentParser(description='Parses platform configuration for Linaro UEFI build scripts.')
parser.add_argument('-c', '--config-file', help='Specify a non-default platform config file.', required=False)
parser.add_argument('-p', '--platform', help='Read configuration for PLATFORM only.', required=False)
parser.add_argument('command', action="store", help='Action to perform')
parser.add_argument('-o', '--option', help='Option to retreive')

args = parser.parse_args()
if args.config_file:
    config_filename = args.config_file
else:
    config_filename = os.path.dirname(os.path.realpath(sys.argv[0])) + '/' + default_filename

config = ConfigParser.ConfigParser()
config.read(config_filename)

platforms = config.sections()

commands = {"shortlist": shortlist_platforms,
            "list": list_platforms,
            "images": get_images,
            "get": get_option}

try:
    retval = commands[args.command]()
except:
    print ("Unrecognized command '%s'" % args.command)

if retval != True:
    sys.exit(1)
