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
import its.error
import its.target
import sys
import os

NAME = os.path.basename(__file__).split(".")[0]
STOP_AT_FIRST_FAILURE = False  # change to True to have test break @ 1st FAIL


def main():
    """Test different combinations of output formats."""

    with its.device.ItsSession() as cam:

        props = cam.get_camera_properties()
        its.caps.skip_unless(its.caps.compute_target_exposure(props) and
                             its.caps.raw16(props))

        successes = []
        failures = []
        debug = its.caps.debug_mode()

        # Two different requests: auto, and manual.
        e, s = its.target.get_target_exposure_combos(cam)["midExposureTime"]
        req_aut = its.objects.auto_capture_request()
        req_man = its.objects.manual_capture_request(s, e)
        reqs = [req_aut,  # R0
                req_man]  # R1

        # 10 different combos of output formats; some are single surfaces, and
        # some are multiple surfaces.
        wyuv, hyuv = its.objects.get_available_output_sizes("yuv", props)[-1]
        wjpg, hjpg = its.objects.get_available_output_sizes("jpg", props)[-1]
        fmt_yuv_prev = {"format": "yuv", "width": wyuv, "height": hyuv}
        fmt_yuv_full = {"format": "yuv"}
        fmt_jpg_prev = {"format": "jpeg", "width": wjpg, "height": hjpg}
        fmt_jpg_full = {"format": "jpeg"}
        fmt_raw_full = {"format": "raw"}
        fmt_combos = [
            [fmt_yuv_prev],                              # F0
            [fmt_yuv_full],                              # F1
            [fmt_jpg_prev],                              # F2
            [fmt_jpg_full],                              # F3
            [fmt_raw_full],                              # F4
            [fmt_yuv_prev, fmt_jpg_prev],                # F5
            [fmt_yuv_prev, fmt_jpg_full],                # F6
            [fmt_yuv_prev, fmt_raw_full],                # F7
            [fmt_yuv_prev, fmt_jpg_prev, fmt_raw_full],  # F8
            [fmt_yuv_prev, fmt_jpg_full, fmt_raw_full]]  # F9

        # Two different burst lengths: single frame, and 3 frames.
        burst_lens = [1,  # B0
                      3]  # B1

        # There are 2x10x2=40 different combinations. Run through them all.
        n = 0
        for r,req in enumerate(reqs):
            for f,fmt_combo in enumerate(fmt_combos):
                for b,burst_len in enumerate(burst_lens):
                    try:
                        caps = cam.do_capture([req]*burst_len, fmt_combo)
                        successes.append((n,r,f,b))
                        print "==> Success[%02d]: R%d F%d B%d" % (n,r,f,b)

                        # Dump the captures out to jpegs in debug mode.
                        if debug:
                            if not isinstance(caps, list):
                                caps = [caps]
                            elif isinstance(caps[0], list):
                                caps = sum(caps, [])
                            for c, cap in enumerate(caps):
                                img = its.image.convert_capture_to_rgb_image(cap, props=props)
                                its.image.write_image(img,
                                    "%s_n%02d_r%d_f%d_b%d_c%d.jpg"%(NAME,n,r,f,b,c))

                    except Exception as e:
                        print e
                        print "==> Failure[%02d]: R%d F%d B%d" % (n,r,f,b)
                        failures.append((n,r,f,b))
                        if STOP_AT_FIRST_FAILURE:
                            sys.exit(1)
                    n += 1

        num_fail = len(failures)
        num_success = len(successes)
        num_total = len(reqs)*len(fmt_combos)*len(burst_lens)
        num_not_run = num_total - num_success - num_fail

        print "\nFailures (%d / %d):" % (num_fail, num_total)
        for (n,r,f,b) in failures:
            print "  %02d: R%d F%d B%d" % (n,r,f,b)
        print "\nSuccesses (%d / %d):" % (num_success, num_total)
        for (n,r,f,b) in successes:
            print "  %02d: R%d F%d B%d" % (n,r,f,b)
        if num_not_run > 0:
            print "\nNumber of tests not run: %d / %d" % (num_not_run, num_total)
        print ""

        # The test passes if all the combinations successfully capture.
        assert num_fail == 0
        assert num_success == num_total

if __name__ == '__main__':
    main()

