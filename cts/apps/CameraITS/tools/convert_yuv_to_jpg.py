# Copyright 2015 The Android Open Source Project
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

import its.image
import sys

def main():
    """Open a YUV420 file and save it as a JPEG.

    Command line args:
        filename.yuv: The YUV420 file to open.
        w: The width of the image.
        h: The height of the image.
        layout: The layout of the data, in ["planar", "nv21"].
    """
    if len(sys.argv) != 5:
        print "Usage: python %s <filename.yuv> <w> <h> <layout>"%(sys.argv[0])
    else:
        fname, w,h = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
        layout = sys.argv[4]
        img = its.image.load_yuv420_to_rgb_image(fname, w,h, layout=layout)
        its.image.write_image(img, fname.replace(".yuv",".jpg"), False)

if __name__ == '__main__':
    main()

