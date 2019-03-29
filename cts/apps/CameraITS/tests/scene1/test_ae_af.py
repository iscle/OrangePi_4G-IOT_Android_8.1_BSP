# Copyright 2017 The Android Open Source Project
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

import its.caps
import its.device
import its.target

import numpy

GAIN_LENGTH = 4
TRANSFORM_LENGTH = 9
GREEN_GAIN = 1.0
GREEN_GAIN_TOL = 0.05
SINGLE_A = {'ae': [True, False, True], 'af': [False, True, True],
            'full_3a': [True, True, True]}  # note no AWB solo


def main():
    """Basic test for bring-up of 3A.

    To pass, 3A must converge. Check that the returned 3A values are legal.
    """

    with its.device.ItsSession() as cam:
        props = cam.get_camera_properties()
        its.caps.skip_unless(its.caps.read_3a(props))

        for k, v in sorted(SINGLE_A.items()):
            print k
            try:
                s, e, g, xform, fd = cam.do_3a(get_results=True,
                                               do_ae=v[0],
                                               do_af=v[1],
                                               do_awb=v[2])
                print ' sensitivity', s, 'exposure', e
                print ' gains', g, 'transform', xform
                print ' fd', fd
                print ''
            except its.error.Error:
                print ' FAIL\n'
            if k == 'full_3a':
                assert s > 0
                assert e > 0
                assert len(g) == 4
                assert len(xform) == 9
                assert fd >= 0
                assert numpy.isclose(g[2], GREEN_GAIN, GREEN_GAIN_TOL)

if __name__ == '__main__':
    main()

