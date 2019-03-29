#!/usr/bin/env python

from __future__ import print_function

import os
import sys

# Find the best reverse path to reference the current directory from another
# directory. We use this to find relative paths to and from the source and build
# directories.
#
# If the directory is given as an absolute path, return an absolute path to the
# current directory.
#
# If there's a symlink involved, and the same relative path would not work if
# the symlink was replace with a regular directory, then return an absolute
# path. This handles paths like out -> /mnt/ssd/out
#
# For symlinks that can use the same relative path (out -> out.1), just return
# the relative path. That way out.1 can be renamed as long as the symlink is
# updated.
#
# For everything else, just return the relative path. That allows the source and
# output directories to be moved as long as they stay in the same position
# relative to each other.
def reverse_path(path):
    if path.startswith("/"):
        return os.path.abspath('.')

    realpath = os.path.relpath(os.path.realpath('.'), os.path.realpath(path))
    relpath = os.path.relpath('.', path)

    if realpath != relpath:
        return os.path.abspath('.')

    return relpath


if __name__ == '__main__':
    print(reverse_path(sys.argv[1]))
