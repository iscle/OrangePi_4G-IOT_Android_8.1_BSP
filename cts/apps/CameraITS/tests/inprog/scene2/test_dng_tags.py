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
import its.device
import its.dng
import its.objects
import numpy
import os.path

def main():
    """Test that the DNG tags are internally self-consistent.
    """
    NAME = os.path.basename(__file__).split(".")[0]

    with its.device.ItsSession() as cam:
        props = cam.get_camera_properties()

        # Assumes that illuminant 1 is D65, and illuminant 2 is standard A.
        # TODO: Generalize DNG tags check for any provided illuminants.
        illum_code = [21, 17] # D65, A
        illum_str = ['D65', 'A']
        ref_str = ['android.sensor.referenceIlluminant%d'%(i) for i in [1,2]]
        cm_str = ['android.sensor.colorTransform%d'%(i) for i in [1,2]]
        fm_str = ['android.sensor.forwardMatrix%d'%(i) for i in [1,2]]
        cal_str = ['android.sensor.calibrationTransform%d'%(i) for i in [1,2]]
        dng_illum = [its.dng.D65, its.dng.A]

        for i in [0,1]:
            assert(props[ref_str[i]] == illum_code[i])
            raw_input("\n[Point camera at grey card under %s and press ENTER]"%(
                    illum_str[i]))

            cam.do_3a(do_af=False)
            cap = cam.do_capture(its.objects.auto_capture_request())
            gains = cap["metadata"]["android.colorCorrection.gains"]
            ccm = its.objects.rational_to_float(
                    cap["metadata"]["android.colorCorrection.transform"])
            cal = its.objects.rational_to_float(props[cal_str[i]])
            print "HAL reported gains:\n", numpy.array(gains)
            print "HAL reported ccm:\n", numpy.array(ccm).reshape(3,3)
            print "HAL reported cal:\n", numpy.array(cal).reshape(3,3)

            # Dump the image.
            img = its.image.convert_capture_to_rgb_image(cap)
            its.image.write_image(img, "%s_%s.jpg" % (NAME, illum_str[i]))

            # Compute the matrices that are expected under this illuminant from
            # the HAL-reported WB gains, CCM, and calibration matrix.
            cm, fm = its.dng.compute_cm_fm(dng_illum[i], gains, ccm, cal)
            asn = its.dng.compute_asn(dng_illum[i], cal, cm)
            print "Expected ColorMatrix:\n", cm
            print "Expected ForwardMatrix:\n", fm
            print "Expected AsShotNeutral:\n", asn

            # Get the matrices that are reported by the HAL for this
            # illuminant.
            cm_ref = numpy.array(its.objects.rational_to_float(
                    props[cm_str[i]])).reshape(3,3)
            fm_ref = numpy.array(its.objects.rational_to_float(
                    props[fm_str[i]])).reshape(3,3)
            asn_ref = numpy.array(its.objects.rational_to_float(
                    cap['metadata']['android.sensor.neutralColorPoint']))
            print "Reported ColorMatrix:\n", cm_ref
            print "Reported ForwardMatrix:\n", fm_ref
            print "Reported AsShotNeutral:\n", asn_ref

            # The color matrix may be scaled (between the reported and
            # expected values).
            cm_scale = cm.mean(1).mean(0) / cm_ref.mean(1).mean(0)
            print "ColorMatrix scale factor:", cm_scale

            # Compute the deltas between reported and expected.
            print "Ratios in ColorMatrix:\n", cm / cm_ref
            print "Deltas in ColorMatrix (after normalizing):\n", cm/cm_scale - cm_ref
            print "Deltas in ForwardMatrix:\n", fm - fm_ref
            print "Deltas in AsShotNeutral:\n", asn - asn_ref

            # TODO: Add pass/fail test on DNG matrices.

if __name__ == '__main__':
    main()

