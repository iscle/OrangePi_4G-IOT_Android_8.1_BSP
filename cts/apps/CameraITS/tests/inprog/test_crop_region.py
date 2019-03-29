# Copyright 2014 The Android Open Source Project
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

import os
import its.image
import its.device
import its.objects


def main():
    """Takes shots with different sensor crop regions.
    """
    name = os.path.basename(__file__).split(".")[0]

    # Regions specified here in x,y,w,h normalized form.
    regions = [[0.0, 0.0, 0.5, 0.5], # top left
               [0.0, 0.5, 0.5, 0.5], # bottom left
               [0.1, 0.9, 0.5, 1.0]] # right side (top + bottom)

    with its.device.ItsSession() as cam:
        props = cam.get_camera_properties()
        r = props['android.sensor.info.pixelArraySize']
        w = r['width']
        h = r['height']

        # Capture a full frame first.
        reqs = [its.objects.auto_capture_request()]
        print "Capturing img0 with the full sensor region"

        # Capture a frame for each of the regions.
        for i,region in enumerate(regions):
            req = its.objects.auto_capture_request()
            req['android.scaler.cropRegion'] = {
                    "left": int(region[0] * w),
                    "top": int(region[1] * h),
                    "right": int((region[0]+region[2])*w),
                    "bottom": int((region[1]+region[3])*h)}
            reqs.append(req)
            crop = req['android.scaler.cropRegion']
            print "Capturing img%d with crop: %d,%d %dx%d"%(i+1,
                    crop["left"],crop["top"],
                    crop["right"]-crop["left"],crop["bottom"]-crop["top"])

        cam.do_3a()
        caps = cam.do_capture(reqs)

        for i,cap in enumerate(caps):
            img = its.image.convert_capture_to_rgb_image(cap)
            crop = cap["metadata"]['android.scaler.cropRegion']
            its.image.write_image(img, "%s_img%d.jpg"%(name,i))
            print "Captured img%d with crop: %d,%d %dx%d"%(i,
                    crop["left"],crop["top"],
                    crop["right"]-crop["left"],crop["bottom"]-crop["top"])

if __name__ == '__main__':
    main()
