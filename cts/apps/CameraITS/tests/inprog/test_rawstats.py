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
import its.caps
import its.device
import its.objects
import its.target
import os.path
import math

def main():
    """Test capturing some rawstats data.
    """
    NAME = os.path.basename(__file__).split(".")[0]

    with its.device.ItsSession() as cam:

        cam.do_3a(do_af=False);
        req = its.objects.auto_capture_request()

        for (gw,gh) in [(16,16)]:#,(4080,1)]:
            cap = cam.do_capture(req,
                {"format":"rawStats","gridWidth":gw,"gridHeight":gh})
            mean_image, var_image = its.image.unpack_rawstats_capture(cap)

            if gw > 1 and gh > 1:
                h,w,_ = mean_image.shape
                for ch in range(4):
                    m = mean_image[:,:,ch].reshape(h,w,1)/1023.0
                    v = var_image[:,:,ch].reshape(h,w,1)
                    its.image.write_image(m, "%s_mean_ch%d.jpg" % (NAME,ch), True)
                    its.image.write_image(v, "%s_var_ch%d.jpg" % (NAME,ch), True)

if __name__ == '__main__':
    main()

