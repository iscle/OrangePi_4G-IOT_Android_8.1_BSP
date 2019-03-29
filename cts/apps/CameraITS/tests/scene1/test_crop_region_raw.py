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

import its.image
import its.caps
import its.device
import its.objects
import its.target
import numpy
import os.path

def main():
    """Test that raw streams are not croppable.
    """
    NAME = os.path.basename(__file__).split(".")[0]

    DIFF_THRESH = 0.05
    CROP_REGION_ERROR_THRESHOLD = 0.01

    with its.device.ItsSession() as cam:
        props = cam.get_camera_properties()
        its.caps.skip_unless(its.caps.compute_target_exposure(props) and
                             its.caps.raw16(props) and
                             its.caps.per_frame_control(props))

        # Calculate the active sensor region for a full (non-cropped) image.
        a = props['android.sensor.info.activeArraySize']
        ax, ay = a["left"], a["top"]
        aw, ah = a["right"] - a["left"], a["bottom"] - a["top"]
        print "Active sensor region: (%d,%d %dx%d)" % (ax, ay, aw, ah)

        full_region = {
            "left": 0,
            "top": 0,
            "right": aw,
            "bottom": ah
        }

        # Calculate a center crop region.
        zoom = min(3.0, its.objects.get_max_digital_zoom(props))
        assert(zoom >= 1)
        cropw = aw / zoom
        croph = ah / zoom

        crop_region = {
            "left": aw / 2 - cropw / 2,
            "top": ah / 2 - croph / 2,
            "right": aw / 2 + cropw / 2,
            "bottom": ah / 2 + croph / 2
        }

        # Capture without a crop region.
        # Use a manual request with a linear tonemap so that the YUV and RAW
        # should look the same (once converted by the its.image module).
        e, s = its.target.get_target_exposure_combos(cam)["minSensitivity"]
        req = its.objects.manual_capture_request(s,e, 0.0, True, props)
        cap1_raw, cap1_yuv = cam.do_capture(req, cam.CAP_RAW_YUV)

        # Capture with a crop region.
        req["android.scaler.cropRegion"] = crop_region
        cap2_raw, cap2_yuv = cam.do_capture(req, cam.CAP_RAW_YUV)

        # Check the metadata related to crop regions.
        # When both YUV and RAW are requested, the crop region that's
        # applied to YUV should be reported.
        # Note that the crop region returned by the cropped captures doesn't
        # need to perfectly match the one that was requested.
        imgs = {}
        for s, cap, cr_expected, err_delta in [
                ("yuv_full",cap1_yuv,full_region,0),
                ("raw_full",cap1_raw,full_region,0),
                ("yuv_crop",cap2_yuv,crop_region,CROP_REGION_ERROR_THRESHOLD),
                ("raw_crop",cap2_raw,crop_region,CROP_REGION_ERROR_THRESHOLD)]:

            # Convert the capture to RGB and dump to a file.
            img = its.image.convert_capture_to_rgb_image(cap, props=props)
            its.image.write_image(img, "%s_%s.jpg" % (NAME, s))
            imgs[s] = img

            # Get the crop region that is reported in the capture result.
            cr_reported = cap["metadata"]["android.scaler.cropRegion"]
            x, y = cr_reported["left"], cr_reported["top"]
            w = cr_reported["right"] - cr_reported["left"]
            h = cr_reported["bottom"] - cr_reported["top"]
            print "Crop reported on %s: (%d,%d %dx%d)" % (s, x, y, w, h)

            # Test that the reported crop region is the same as the expected
            # one, for a non-cropped capture, and is close to the expected one,
            # for a cropped capture.
            ex = aw * err_delta
            ey = ah * err_delta
            assert ((abs(cr_expected["left"] - cr_reported["left"]) <= ex) and
                    (abs(cr_expected["right"] - cr_reported["right"]) <= ex) and
                    (abs(cr_expected["top"] - cr_reported["top"]) <= ey) and
                    (abs(cr_expected["bottom"] - cr_reported["bottom"]) <= ey))

        # Also check the image content; 3 of the 4 shots should match.
        # Note that all the shots are RGB below; the variable names correspond
        # to what was captured.

        # Shrink the YUV images 2x2 -> 1 to account for the size reduction that
        # the raw images went through in the RGB conversion.
        imgs2 = {}
        for s,img in imgs.iteritems():
            h,w,ch = img.shape
            if s in ["yuv_full", "yuv_crop"]:
                img = img.reshape(h/2,2,w/2,2,3).mean(3).mean(1)
                img = img.reshape(h/2,w/2,3)
            imgs2[s] = img

        # Strip any border pixels from the raw shots (since the raw images may
        # be larger than the YUV images). Assume a symmetric padded border.
        xpad = (imgs2["raw_full"].shape[1] - imgs2["yuv_full"].shape[1]) / 2
        ypad = (imgs2["raw_full"].shape[0] - imgs2["yuv_full"].shape[0]) / 2
        wyuv = imgs2["yuv_full"].shape[1]
        hyuv = imgs2["yuv_full"].shape[0]
        imgs2["raw_full"]=imgs2["raw_full"][ypad:ypad+hyuv:,xpad:xpad+wyuv:,::]
        imgs2["raw_crop"]=imgs2["raw_crop"][ypad:ypad+hyuv:,xpad:xpad+wyuv:,::]
        print "Stripping padding before comparison:", xpad, ypad

        for s,img in imgs2.iteritems():
            its.image.write_image(img, "%s_comp_%s.jpg" % (NAME, s))

        # Compute diffs between images of the same type.
        # The raw_crop and raw_full shots should be identical (since the crop
        # doesn't apply to raw images), and the yuv_crop and yuv_full shots
        # should be different.
        diff_yuv = numpy.fabs((imgs2["yuv_full"] - imgs2["yuv_crop"])).mean()
        diff_raw = numpy.fabs((imgs2["raw_full"] - imgs2["raw_crop"])).mean()
        print "YUV diff (crop vs. non-crop):", diff_yuv
        print "RAW diff (crop vs. non-crop):", diff_raw

        assert(diff_yuv > DIFF_THRESH)
        assert(diff_raw < DIFF_THRESH)

if __name__ == '__main__':
    main()

