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

import os.path
import cv2
import its.caps
import its.device
import its.image
import its.objects

NAME = os.path.basename(__file__).split('.')[0]
NUM_TEST_FRAMES = 20
NUM_FACES = 3
FD_MODE_OFF = 0
FD_MODE_SIMPLE = 1
FD_MODE_FULL = 2
W, H = 640, 480


def main():
    """Test face detection."""
    with its.device.ItsSession() as cam:
        props = cam.get_camera_properties()
        fd_modes = props['android.statistics.info.availableFaceDetectModes']
        a = props['android.sensor.info.activeArraySize']
        aw, ah = a['right'] - a['left'], a['bottom'] - a['top']

        if its.caps.read_3a(props):
            _, _, _, _, _ = cam.do_3a(get_results=True)

        for fd_mode in fd_modes:
            assert FD_MODE_OFF <= fd_mode <= FD_MODE_FULL
            req = its.objects.auto_capture_request()
            req['android.statistics.faceDetectMode'] = fd_mode
            fmt = {'format': 'yuv', 'width': W, 'height': H}
            caps = cam.do_capture([req]*NUM_TEST_FRAMES, fmt)
            for i, cap in enumerate(caps):
                md = cap['metadata']
                assert md['android.statistics.faceDetectMode'] == fd_mode
                faces = md['android.statistics.faces']

                # 0 faces should be returned for OFF mode
                if fd_mode == FD_MODE_OFF:
                    assert not faces
                    continue
                # Face detection could take several frames to warm up,
                # but should detect the correct number of faces in last frame
                if i == NUM_TEST_FRAMES - 1:
                    img = its.image.convert_capture_to_rgb_image(cap,
                                                                 props=props)
                    fnd_faces = len(faces)
                    print 'Found %d face(s), expected %d.' % (fnd_faces,
                                                              NUM_FACES)
                    # draw boxes around faces
                    for rect in [face['bounds'] for face in faces]:
                        top_left = (int(round(rect['left']*W/aw)),
                                    int(round(rect['top']*H/ah)))
                        bot_rght = (int(round(rect['right']*W/aw)),
                                    int(round(rect['bottom']*H/ah)))
                        cv2.rectangle(img, top_left, bot_rght, (0, 1, 0), 2)
                        img_name = '%s_fd_mode_%s.jpg' % (NAME, fd_mode)
                        its.image.write_image(img, img_name)
                    assert fnd_faces == NUM_FACES
                if not faces:
                    continue

                print 'Frame %d face metadata:' % i
                print '  Faces:', faces
                print ''

                # Reasonable scores for faces
                face_scores = [face['score'] for face in faces]
                for score in face_scores:
                    assert score >= 1 and score <= 100
                # Face bounds should be within active array
                face_rectangles = [face['bounds'] for face in faces]
                for rect in face_rectangles:
                    assert rect['top'] < rect['bottom']
                    assert rect['left'] < rect['right']
                    assert 0 <= rect['top'] <= ah
                    assert 0 <= rect['bottom'] <= ah
                    assert 0 <= rect['left'] <= aw
                    assert 0 <= rect['right'] <= aw

if __name__ == '__main__':
    main()
