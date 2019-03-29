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

# --------------------------------------------------------------------------- #
# The Google Python style guide should be used for scripts:                   #
# http://google-styleguide.googlecode.com/svn/trunk/pyguide.html              #
# --------------------------------------------------------------------------- #

# The ITS modules that are in the pymodules/its/ directory. To see formatted
# docs, use the "pydoc" command:
#
# > pydoc its.image
#
import its.image
import its.device
import its.objects
import its.target

# Standard Python modules.
import os.path
import pprint
import math

# Modules from the numpy, scipy, and matplotlib libraries. These are used for
# the image processing code, and images are represented as numpy arrays.
from matplotlib import pylab
import numpy
import matplotlib
import matplotlib.pyplot

# Each script has a "main" function.
def main():

    # Each script has a string description of what it does. This is the first
    # entry inside the main function.
    """Tutorial script to show how to use the ITS infrastructure.
    """

    # A convention in each script is to use the filename (without the extension)
    # as the name of the test, when printing results to the screen or dumping
    # files.
    NAME = os.path.basename(__file__).split(".")[0]

    # The standard way to open a session with a connected camera device. This
    # creates a cam object which encapsulates the session and which is active
    # within the scope of the "with" block; when the block exits, the camera
    # session is closed.
    with its.device.ItsSession() as cam:

        # Get the static properties of the camera device. Returns a Python
        # associative array object; print it to the console.
        props = cam.get_camera_properties()
        pprint.pprint(props)

        # Grab a YUV frame with manual exposure of sensitivity = 200, exposure
        # duration = 50ms.
        req = its.objects.manual_capture_request(200, 50*1000*1000)
        cap = cam.do_capture(req)

        # Print the properties of the captured frame; width and height are
        # integers, and the metadata is a Python associative array object.
        print "Captured image width:", cap["width"]
        print "Captured image height:", cap["height"]
        pprint.pprint(cap["metadata"])

        # The captured image is YUV420. Convert to RGB, and save as a file.
        rgbimg = its.image.convert_capture_to_rgb_image(cap)
        its.image.write_image(rgbimg, "%s_rgb_1.jpg" % (NAME))

        # Can also get the Y,U,V planes separately; save these to greyscale
        # files.
        yimg,uimg,vimg = its.image.convert_capture_to_planes(cap)
        its.image.write_image(yimg, "%s_y_plane_1.jpg" % (NAME))
        its.image.write_image(uimg, "%s_u_plane_1.jpg" % (NAME))
        its.image.write_image(vimg, "%s_v_plane_1.jpg" % (NAME))

        # Run 3A on the device. In this case, just use the entire image as the
        # 3A region, and run each of AWB,AE,AF. Can also change the region and
        # specify independently for each of AE,AWB,AF whether it should run.
        #
        # NOTE: This may fail, if the camera isn't pointed at a reasonable
        # target scene. If it fails, the script will end. The logcat messages
        # can be inspected to see the status of 3A running on the device.
        #
        # > adb logcat -s 'ItsService:v'
        #
        # If this keeps on failing, try also rebooting the device before
        # running the test.
        sens, exp, gains, xform, focus = cam.do_3a(get_results=True)
        print "AE: sensitivity %d, exposure %dms" % (sens, exp/1000000.0)
        print "AWB: gains", gains, "transform", xform
        print "AF: distance", focus

        # Grab a new manual frame, using the 3A values, and convert it to RGB
        # and save it to a file too. Note that the "req" object is just a
        # Python dictionary that is pre-populated by the its.objets module
        # functions (in this case a default manual capture), and the key/value
        # pairs in the object can be used to set any field of the capture
        # request. Here, the AWB gains and transform (CCM) are being used.
        # Note that the CCM transform is in a rational format in capture
        # requests, meaning it is an object with integer numerators and
        # denominators. The 3A routine returns simple floats instead, however,
        # so a conversion from float to rational must be performed.
        req = its.objects.manual_capture_request(sens, exp)
        xform_rat = its.objects.float_to_rational(xform)

        req["android.colorCorrection.transform"] = xform_rat
        req["android.colorCorrection.gains"] = gains
        cap = cam.do_capture(req)
        rgbimg = its.image.convert_capture_to_rgb_image(cap)
        its.image.write_image(rgbimg, "%s_rgb_2.jpg" % (NAME))

        # Print out the actual capture request object that was used.
        pprint.pprint(req)

        # Images are numpy arrays. The dimensions are (h,w,3) when indexing,
        # in the case of RGB images. Greyscale images are (h,w,1). Pixels are
        # generally float32 values in the [0,1] range, however some of the
        # helper functions in its.image deal with the packed YUV420 and other
        # formats of images that come from the device (and convert them to
        # float32).
        # Print the dimensions of the image, and the top-left pixel value,
        # which is an array of 3 floats.
        print "RGB image dimensions:", rgbimg.shape
        print "RGB image top-left pixel:", rgbimg[0,0]

        # Grab a center tile from the image; this returns a new image. Save
        # this tile image. In this case, the tile is the middle 10% x 10%
        # rectangle.
        tile = its.image.get_image_patch(rgbimg, 0.45, 0.45, 0.1, 0.1)
        its.image.write_image(tile, "%s_rgb_2_tile.jpg" % (NAME))

        # Compute the mean values of the center tile image.
        rgb_means = its.image.compute_image_means(tile)
        print "RGB means:", rgb_means

        # Apply a lookup table to the image, and save the new version. The LUT
        # is basically a tonemap, and can be used to implement a gamma curve.
        # In this case, the LUT is used to double the value of each pixel.
        lut = numpy.array([2*i for i in xrange(65536)])
        rgbimg_lut = its.image.apply_lut_to_image(rgbimg, lut)
        its.image.write_image(rgbimg_lut, "%s_rgb_2_lut.jpg" % (NAME))

        # Apply a 3x3 matrix to the image, and save the new version. The matrix
        # is a numpy array, in row major order, and the pixel values are right-
        # multiplied to it (when considered as column vectors). The example
        # matrix here just boosts the blue channel by 10%.
        mat = numpy.array([[1, 0, 0  ],
                           [0, 1, 0  ],
                           [0, 0, 1.1]])
        rgbimg_mat = its.image.apply_matrix_to_image(rgbimg, mat)
        its.image.write_image(rgbimg_mat, "%s_rgb_2_mat.jpg" % (NAME))

        # Compute a histogram of the luma image, in 256 buckets.
        yimg,_,_ = its.image.convert_capture_to_planes(cap)
        hist,_ = numpy.histogram(yimg*255, 256, (0,256))

        # Plot the histogram using matplotlib, and save as a PNG image.
        pylab.plot(range(256), hist.tolist())
        pylab.xlabel("Luma DN")
        pylab.ylabel("Pixel count")
        pylab.title("Histogram of luma channel of captured image")
        matplotlib.pyplot.savefig("%s_histogram.png" % (NAME))

        # Capture a frame to be returned as a JPEG. Load it as an RGB image,
        # then save it back as a JPEG.
        cap = cam.do_capture(req, cam.CAP_JPEG)
        rgbimg = its.image.convert_capture_to_rgb_image(cap)
        its.image.write_image(rgbimg, "%s_jpg.jpg" % (NAME))
        r,g,b = its.image.convert_capture_to_planes(cap)
        its.image.write_image(r, "%s_r.jpg" % (NAME))

# This is the standard boilerplate in each test that allows the script to both
# be executed directly and imported as a module.
if __name__ == '__main__':
    main()

