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
import os.path

def main():
    """Test face detection.
    """
    NAME = os.path.basename(__file__).split(".")[0]
    NUM_TEST_FRAMES = 20
    FD_MODE_OFF = 0
    FD_MODE_SIMPLE = 1
    FD_MODE_FULL = 2
    W, H = 640, 480

    with its.device.ItsSession() as cam:
        props = cam.get_camera_properties()
        fd_modes = props['android.statistics.info.availableFaceDetectModes']
        a = props['android.sensor.info.activeArraySize']
        aw, ah = a['right'] - a['left'], a['bottom'] - a['top']
        if its.caps.read_3a(props):
            gain, exp, _, _, focus = cam.do_3a(get_results=True)
            print 'iso = %d' % gain
            print 'exp = %.2fms' % (exp*1.0E-6)
            if focus == 0.0:
                print 'fd = infinity'
            else:
                print 'fd = %.2fcm' % (1.0E2/focus)
        for fd_mode in fd_modes:
            assert(FD_MODE_OFF <= fd_mode <= FD_MODE_FULL)
            req = its.objects.auto_capture_request()
            req['android.statistics.faceDetectMode'] = fd_mode
            fmt = {"format":"yuv", "width":W, "height":H}
            caps = cam.do_capture([req]*NUM_TEST_FRAMES, fmt)
            for i,cap in enumerate(caps):
                md = cap['metadata']
                assert(md['android.statistics.faceDetectMode'] == fd_mode)
                faces = md['android.statistics.faces']

                # 0 faces should be returned for OFF mode
                if fd_mode == FD_MODE_OFF:
                    assert(len(faces) == 0)
                    continue
                # Face detection could take several frames to warm up,
                # but it should detect at least one face in last frame
                if i == NUM_TEST_FRAMES - 1:
                    img = its.image.convert_capture_to_rgb_image(cap, props=props)
                    img = its.image.flip_mirror_img_per_argv(img)
                    img_name = "%s_fd_mode_%s.jpg" % (NAME, fd_mode)
                    its.image.write_image(img, img_name)
                    if len(faces) == 0:
                        print "Error: no face detected in mode", fd_mode
                        assert(0)
                if len(faces) == 0:
                    continue

                print "Frame %d face metadata:" % i
                print "  Faces:", faces
                print ""

                face_scores = [face['score'] for face in faces]
                face_rectangles = [face['bounds'] for face in faces]
                for score in face_scores:
                    assert(score >= 1 and score <= 100)
                # Face bounds should be within active array
                for rect in face_rectangles:
                    assert(rect['top'] < rect['bottom'])
                    assert(rect['left'] < rect['right'])
                    assert(0 <= rect['top'] <= ah)
                    assert(0 <= rect['bottom'] <= ah)
                    assert(0 <= rect['left'] <= aw)
                    assert(0 <= rect['right'] <= aw)

                # Face landmarks are reported if and only if fd_mode is FULL
                # Face ID should be -1 for SIMPLE and unique for FULL
                if fd_mode == FD_MODE_SIMPLE:
                    for face in faces:
                        assert('leftEye' not in face)
                        assert('rightEye' not in face)
                        assert('mouth' not in face)
                        assert(face['id'] == -1)
                elif fd_mode == FD_MODE_FULL:
                    face_ids = [face['id'] for face in faces]
                    assert(len(face_ids) == len(set(face_ids)))
                    # Face landmarks should be within face bounds
                    for face in faces:
                        left_eye = face['leftEye']
                        right_eye = face['rightEye']
                        mouth = face['mouth']
                        l, r = face['bounds']['left'], face['bounds']['right']
                        t, b = face['bounds']['top'], face['bounds']['bottom']
                        assert(l <= left_eye['x'] <= r)
                        assert(t <= left_eye['y'] <= b)
                        assert(l <= right_eye['x'] <= r)
                        assert(t <= right_eye['y'] <= b)
                        assert(l <= mouth['x'] <= r)
                        assert(t <= mouth['y'] <= b)

if __name__ == '__main__':
    main()

