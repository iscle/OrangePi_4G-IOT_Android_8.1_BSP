# Copyright 2013 The Android Open Source Project
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

import matplotlib
matplotlib.use('Agg')

import its.error
import sys
from PIL import Image
import numpy
import math
import unittest
import cStringIO
import copy
import random

DEFAULT_YUV_TO_RGB_CCM = numpy.matrix([
                                [1.000,  0.000,  1.402],
                                [1.000, -0.344, -0.714],
                                [1.000,  1.772,  0.000]])

DEFAULT_YUV_OFFSETS = numpy.array([0, 128, 128])

DEFAULT_GAMMA_LUT = numpy.array(
        [math.floor(65535 * math.pow(i/65535.0, 1/2.2) + 0.5)
         for i in xrange(65536)])

DEFAULT_INVGAMMA_LUT = numpy.array(
        [math.floor(65535 * math.pow(i/65535.0, 2.2) + 0.5)
         for i in xrange(65536)])

MAX_LUT_SIZE = 65536

NUM_TRYS = 2
NUM_FRAMES = 4


def convert_capture_to_rgb_image(cap,
                                 ccm_yuv_to_rgb=DEFAULT_YUV_TO_RGB_CCM,
                                 yuv_off=DEFAULT_YUV_OFFSETS,
                                 props=None):
    """Convert a captured image object to a RGB image.

    Args:
        cap: A capture object as returned by its.device.do_capture.
        ccm_yuv_to_rgb: (Optional) the 3x3 CCM to convert from YUV to RGB.
        yuv_off: (Optional) offsets to subtract from each of Y,U,V values.
        props: (Optional) camera properties object (of static values);
            required for processing raw images.

    Returns:
        RGB float-3 image array, with pixel values in [0.0, 1.0].
    """
    w = cap["width"]
    h = cap["height"]
    if cap["format"] == "raw10":
        assert(props is not None)
        cap = unpack_raw10_capture(cap, props)
    if cap["format"] == "raw12":
        assert(props is not None)
        cap = unpack_raw12_capture(cap, props)
    if cap["format"] == "yuv":
        y = cap["data"][0:w*h]
        u = cap["data"][w*h:w*h*5/4]
        v = cap["data"][w*h*5/4:w*h*6/4]
        return convert_yuv420_planar_to_rgb_image(y, u, v, w, h)
    elif cap["format"] == "jpeg":
        return decompress_jpeg_to_rgb_image(cap["data"])
    elif cap["format"] == "raw" or cap["format"] == "rawStats":
        assert(props is not None)
        r,gr,gb,b = convert_capture_to_planes(cap, props)
        return convert_raw_to_rgb_image(r,gr,gb,b, props, cap["metadata"])
    else:
        raise its.error.Error('Invalid format %s' % (cap["format"]))


def unpack_rawstats_capture(cap):
    """Unpack a rawStats capture to the mean and variance images.

    Args:
        cap: A capture object as returned by its.device.do_capture.

    Returns:
        Tuple (mean_image var_image) of float-4 images, with non-normalized
        pixel values computed from the RAW16 images on the device
    """
    assert(cap["format"] == "rawStats")
    w = cap["width"]
    h = cap["height"]
    img = numpy.ndarray(shape=(2*h*w*4,), dtype='<f', buffer=cap["data"])
    analysis_image = img.reshape(2,h,w,4)
    mean_image = analysis_image[0,:,:,:].reshape(h,w,4)
    var_image = analysis_image[1,:,:,:].reshape(h,w,4)
    return mean_image, var_image


def unpack_raw10_capture(cap, props):
    """Unpack a raw-10 capture to a raw-16 capture.

    Args:
        cap: A raw-10 capture object.
        props: Camera properties object.

    Returns:
        New capture object with raw-16 data.
    """
    # Data is packed as 4x10b pixels in 5 bytes, with the first 4 bytes holding
    # the MSPs of the pixels, and the 5th byte holding 4x2b LSBs.
    w,h = cap["width"], cap["height"]
    if w % 4 != 0:
        raise its.error.Error('Invalid raw-10 buffer width')
    cap = copy.deepcopy(cap)
    cap["data"] = unpack_raw10_image(cap["data"].reshape(h,w*5/4))
    cap["format"] = "raw"
    return cap


def unpack_raw10_image(img):
    """Unpack a raw-10 image to a raw-16 image.

    Output image will have the 10 LSBs filled in each 16b word, and the 6 MSBs
    will be set to zero.

    Args:
        img: A raw-10 image, as a uint8 numpy array.

    Returns:
        Image as a uint16 numpy array, with all row padding stripped.
    """
    if img.shape[1] % 5 != 0:
        raise its.error.Error('Invalid raw-10 buffer width')
    w = img.shape[1]*4/5
    h = img.shape[0]
    # Cut out the 4x8b MSBs and shift to bits [9:2] in 16b words.
    msbs = numpy.delete(img, numpy.s_[4::5], 1)
    msbs = msbs.astype(numpy.uint16)
    msbs = numpy.left_shift(msbs, 2)
    msbs = msbs.reshape(h,w)
    # Cut out the 4x2b LSBs and put each in bits [1:0] of their own 8b words.
    lsbs = img[::, 4::5].reshape(h,w/4)
    lsbs = numpy.right_shift(
            numpy.packbits(numpy.unpackbits(lsbs).reshape(h,w/4,4,2),3), 6)
    # Pair the LSB bits group to pixel 0 instead of pixel 3
    lsbs = lsbs.reshape(h,w/4,4)[:,:,::-1]
    lsbs = lsbs.reshape(h,w)
    # Fuse the MSBs and LSBs back together
    img16 = numpy.bitwise_or(msbs, lsbs).reshape(h,w)
    return img16


def unpack_raw12_capture(cap, props):
    """Unpack a raw-12 capture to a raw-16 capture.

    Args:
        cap: A raw-12 capture object.
        props: Camera properties object.

    Returns:
        New capture object with raw-16 data.
    """
    # Data is packed as 4x10b pixels in 5 bytes, with the first 4 bytes holding
    # the MSBs of the pixels, and the 5th byte holding 4x2b LSBs.
    w,h = cap["width"], cap["height"]
    if w % 2 != 0:
        raise its.error.Error('Invalid raw-12 buffer width')
    cap = copy.deepcopy(cap)
    cap["data"] = unpack_raw12_image(cap["data"].reshape(h,w*3/2))
    cap["format"] = "raw"
    return cap


def unpack_raw12_image(img):
    """Unpack a raw-12 image to a raw-16 image.

    Output image will have the 12 LSBs filled in each 16b word, and the 4 MSBs
    will be set to zero.

    Args:
        img: A raw-12 image, as a uint8 numpy array.

    Returns:
        Image as a uint16 numpy array, with all row padding stripped.
    """
    if img.shape[1] % 3 != 0:
        raise its.error.Error('Invalid raw-12 buffer width')
    w = img.shape[1]*2/3
    h = img.shape[0]
    # Cut out the 2x8b MSBs and shift to bits [11:4] in 16b words.
    msbs = numpy.delete(img, numpy.s_[2::3], 1)
    msbs = msbs.astype(numpy.uint16)
    msbs = numpy.left_shift(msbs, 4)
    msbs = msbs.reshape(h,w)
    # Cut out the 2x4b LSBs and put each in bits [3:0] of their own 8b words.
    lsbs = img[::, 2::3].reshape(h,w/2)
    lsbs = numpy.right_shift(
            numpy.packbits(numpy.unpackbits(lsbs).reshape(h,w/2,2,4),3), 4)
    # Pair the LSB bits group to pixel 0 instead of pixel 1
    lsbs = lsbs.reshape(h,w/2,2)[:,:,::-1]
    lsbs = lsbs.reshape(h,w)
    # Fuse the MSBs and LSBs back together
    img16 = numpy.bitwise_or(msbs, lsbs).reshape(h,w)
    return img16


def convert_capture_to_planes(cap, props=None):
    """Convert a captured image object to separate image planes.

    Decompose an image into multiple images, corresponding to different planes.

    For YUV420 captures ("yuv"):
        Returns Y,U,V planes, where the Y plane is full-res and the U,V planes
        are each 1/2 x 1/2 of the full res.

    For Bayer captures ("raw", "raw10", "raw12", or "rawStats"):
        Returns planes in the order R,Gr,Gb,B, regardless of the Bayer pattern
        layout. For full-res raw images ("raw", "raw10", "raw12"), each plane
        is 1/2 x 1/2 of the full res. For "rawStats" images, the mean image
        is returned.

    For JPEG captures ("jpeg"):
        Returns R,G,B full-res planes.

    Args:
        cap: A capture object as returned by its.device.do_capture.
        props: (Optional) camera properties object (of static values);
            required for processing raw images.

    Returns:
        A tuple of float numpy arrays (one per plane), consisting of pixel
            values in the range [0.0, 1.0].
    """
    w = cap["width"]
    h = cap["height"]
    if cap["format"] == "raw10":
        assert(props is not None)
        cap = unpack_raw10_capture(cap, props)
    if cap["format"] == "raw12":
        assert(props is not None)
        cap = unpack_raw12_capture(cap, props)
    if cap["format"] == "yuv":
        y = cap["data"][0:w*h]
        u = cap["data"][w*h:w*h*5/4]
        v = cap["data"][w*h*5/4:w*h*6/4]
        return ((y.astype(numpy.float32) / 255.0).reshape(h, w, 1),
                (u.astype(numpy.float32) / 255.0).reshape(h/2, w/2, 1),
                (v.astype(numpy.float32) / 255.0).reshape(h/2, w/2, 1))
    elif cap["format"] == "jpeg":
        rgb = decompress_jpeg_to_rgb_image(cap["data"]).reshape(w*h*3)
        return (rgb[::3].reshape(h,w,1),
                rgb[1::3].reshape(h,w,1),
                rgb[2::3].reshape(h,w,1))
    elif cap["format"] == "raw":
        assert(props is not None)
        white_level = float(props['android.sensor.info.whiteLevel'])
        img = numpy.ndarray(shape=(h*w,), dtype='<u2',
                            buffer=cap["data"][0:w*h*2])
        img = img.astype(numpy.float32).reshape(h,w) / white_level
        # Crop the raw image to the active array region.
        if props.has_key("android.sensor.info.activeArraySize") \
                and props["android.sensor.info.activeArraySize"] is not None \
                and props.has_key("android.sensor.info.pixelArraySize") \
                and props["android.sensor.info.pixelArraySize"] is not None:
            # Note that the Rect class is defined such that the left,top values
            # are "inside" while the right,bottom values are "outside"; that is,
            # it's inclusive of the top,left sides only. So, the width is
            # computed as right-left, rather than right-left+1, etc.
            wfull = props["android.sensor.info.pixelArraySize"]["width"]
            hfull = props["android.sensor.info.pixelArraySize"]["height"]
            xcrop = props["android.sensor.info.activeArraySize"]["left"]
            ycrop = props["android.sensor.info.activeArraySize"]["top"]
            wcrop = props["android.sensor.info.activeArraySize"]["right"]-xcrop
            hcrop = props["android.sensor.info.activeArraySize"]["bottom"]-ycrop
            assert(wfull >= wcrop >= 0)
            assert(hfull >= hcrop >= 0)
            assert(wfull - wcrop >= xcrop >= 0)
            assert(hfull - hcrop >= ycrop >= 0)
            if w == wfull and h == hfull:
                # Crop needed; extract the center region.
                img = img[ycrop:ycrop+hcrop,xcrop:xcrop+wcrop]
                w = wcrop
                h = hcrop
            elif w == wcrop and h == hcrop:
                # No crop needed; image is already cropped to the active array.
                None
            else:
                raise its.error.Error('Invalid image size metadata')
        # Separate the image planes.
        imgs = [img[::2].reshape(w*h/2)[::2].reshape(h/2,w/2,1),
                img[::2].reshape(w*h/2)[1::2].reshape(h/2,w/2,1),
                img[1::2].reshape(w*h/2)[::2].reshape(h/2,w/2,1),
                img[1::2].reshape(w*h/2)[1::2].reshape(h/2,w/2,1)]
        idxs = get_canonical_cfa_order(props)
        return [imgs[i] for i in idxs]
    elif cap["format"] == "rawStats":
        assert(props is not None)
        white_level = float(props['android.sensor.info.whiteLevel'])
        mean_image, var_image = its.image.unpack_rawstats_capture(cap)
        idxs = get_canonical_cfa_order(props)
        return [mean_image[:,:,i] / white_level for i in idxs]
    else:
        raise its.error.Error('Invalid format %s' % (cap["format"]))


def get_canonical_cfa_order(props):
    """Returns a mapping from the Bayer 2x2 top-left grid in the CFA to
    the standard order R,Gr,Gb,B.

    Args:
        props: Camera properties object.

    Returns:
        List of 4 integers, corresponding to the positions in the 2x2 top-
            left Bayer grid of R,Gr,Gb,B, where the 2x2 grid is labeled as
            0,1,2,3 in row major order.
    """
    # Note that raw streams aren't croppable, so the cropRegion doesn't need
    # to be considered when determining the top-left pixel color.
    cfa_pat = props['android.sensor.info.colorFilterArrangement']
    if cfa_pat == 0:
        # RGGB
        return [0,1,2,3]
    elif cfa_pat == 1:
        # GRBG
        return [1,0,3,2]
    elif cfa_pat == 2:
        # GBRG
        return [2,3,0,1]
    elif cfa_pat == 3:
        # BGGR
        return [3,2,1,0]
    else:
        raise its.error.Error("Not supported")


def get_gains_in_canonical_order(props, gains):
    """Reorders the gains tuple to the canonical R,Gr,Gb,B order.

    Args:
        props: Camera properties object.
        gains: List of 4 values, in R,G_even,G_odd,B order.

    Returns:
        List of gains values, in R,Gr,Gb,B order.
    """
    cfa_pat = props['android.sensor.info.colorFilterArrangement']
    if cfa_pat in [0,1]:
        # RGGB or GRBG, so G_even is Gr
        return gains
    elif cfa_pat in [2,3]:
        # GBRG or BGGR, so G_even is Gb
        return [gains[0], gains[2], gains[1], gains[3]]
    else:
        raise its.error.Error("Not supported")


def convert_raw_to_rgb_image(r_plane, gr_plane, gb_plane, b_plane,
                             props, cap_res):
    """Convert a Bayer raw-16 image to an RGB image.

    Includes some extremely rudimentary demosaicking and color processing
    operations; the output of this function shouldn't be used for any image
    quality analysis.

    Args:
        r_plane,gr_plane,gb_plane,b_plane: Numpy arrays for each color plane
            in the Bayer image, with pixels in the [0.0, 1.0] range.
        props: Camera properties object.
        cap_res: Capture result (metadata) object.

    Returns:
        RGB float-3 image array, with pixel values in [0.0, 1.0]
    """
    # Values required for the RAW to RGB conversion.
    assert(props is not None)
    white_level = float(props['android.sensor.info.whiteLevel'])
    black_levels = props['android.sensor.blackLevelPattern']
    gains = cap_res['android.colorCorrection.gains']
    ccm = cap_res['android.colorCorrection.transform']

    # Reorder black levels and gains to R,Gr,Gb,B, to match the order
    # of the planes.
    black_levels = [get_black_level(i,props,cap_res) for i in range(4)]
    gains = get_gains_in_canonical_order(props, gains)

    # Convert CCM from rational to float, as numpy arrays.
    ccm = numpy.array(its.objects.rational_to_float(ccm)).reshape(3,3)

    # Need to scale the image back to the full [0,1] range after subtracting
    # the black level from each pixel.
    scale = white_level / (white_level - max(black_levels))

    # Three-channel black levels, normalized to [0,1] by white_level.
    black_levels = numpy.array([b/white_level for b in [
            black_levels[i] for i in [0,1,3]]])

    # Three-channel gains.
    gains = numpy.array([gains[i] for i in [0,1,3]])

    h,w = r_plane.shape[:2]
    img = numpy.dstack([r_plane,(gr_plane+gb_plane)/2.0,b_plane])
    img = (((img.reshape(h,w,3) - black_levels) * scale) * gains).clip(0.0,1.0)
    img = numpy.dot(img.reshape(w*h,3), ccm.T).reshape(h,w,3).clip(0.0,1.0)
    return img


def get_black_level(chan, props, cap_res):
    """Return the black level to use for a given capture.

    Uses a dynamic value from the capture result if available, else falls back
    to the static global value in the camera characteristics.

    Args:
        chan: The channel index, in canonical order (R, Gr, Gb, B).
        props: The camera properties object.
        cap_res: A capture result object.

    Returns:
        The black level value for the specified channel.
    """
    if (cap_res.has_key('android.sensor.dynamicBlackLevel') and
            cap_res['android.sensor.dynamicBlackLevel'] is not None):
        black_levels = cap_res['android.sensor.dynamicBlackLevel']
    else:
        black_levels = props['android.sensor.blackLevelPattern']
    idxs = its.image.get_canonical_cfa_order(props)
    ordered_black_levels = [black_levels[i] for i in idxs]
    return ordered_black_levels[chan]


def convert_yuv420_planar_to_rgb_image(y_plane, u_plane, v_plane,
                                       w, h,
                                       ccm_yuv_to_rgb=DEFAULT_YUV_TO_RGB_CCM,
                                       yuv_off=DEFAULT_YUV_OFFSETS):
    """Convert a YUV420 8-bit planar image to an RGB image.

    Args:
        y_plane: The packed 8-bit Y plane.
        u_plane: The packed 8-bit U plane.
        v_plane: The packed 8-bit V plane.
        w: The width of the image.
        h: The height of the image.
        ccm_yuv_to_rgb: (Optional) the 3x3 CCM to convert from YUV to RGB.
        yuv_off: (Optional) offsets to subtract from each of Y,U,V values.

    Returns:
        RGB float-3 image array, with pixel values in [0.0, 1.0].
    """
    y = numpy.subtract(y_plane, yuv_off[0])
    u = numpy.subtract(u_plane, yuv_off[1]).view(numpy.int8)
    v = numpy.subtract(v_plane, yuv_off[2]).view(numpy.int8)
    u = u.reshape(h/2, w/2).repeat(2, axis=1).repeat(2, axis=0)
    v = v.reshape(h/2, w/2).repeat(2, axis=1).repeat(2, axis=0)
    yuv = numpy.dstack([y, u.reshape(w*h), v.reshape(w*h)])
    flt = numpy.empty([h, w, 3], dtype=numpy.float32)
    flt.reshape(w*h*3)[:] = yuv.reshape(h*w*3)[:]
    flt = numpy.dot(flt.reshape(w*h,3), ccm_yuv_to_rgb.T).clip(0, 255)
    rgb = numpy.empty([h, w, 3], dtype=numpy.uint8)
    rgb.reshape(w*h*3)[:] = flt.reshape(w*h*3)[:]
    return rgb.astype(numpy.float32) / 255.0


def load_rgb_image(fname):
    """Load a standard image file (JPG, PNG, etc.).

    Args:
        fname: The path of the file to load.

    Returns:
        RGB float-3 image array, with pixel values in [0.0, 1.0].
    """
    img = Image.open(fname)
    w = img.size[0]
    h = img.size[1]
    a = numpy.array(img)
    if len(a.shape) == 3 and a.shape[2] == 3:
        # RGB
        return a.reshape(h,w,3) / 255.0
    elif len(a.shape) == 2 or len(a.shape) == 3 and a.shape[2] == 1:
        # Greyscale; convert to RGB
        return a.reshape(h*w).repeat(3).reshape(h,w,3) / 255.0
    else:
        raise its.error.Error('Unsupported image type')


def load_yuv420_to_rgb_image(yuv_fname,
                             w, h,
                             layout="planar",
                             ccm_yuv_to_rgb=DEFAULT_YUV_TO_RGB_CCM,
                             yuv_off=DEFAULT_YUV_OFFSETS):
    """Load a YUV420 image file, and return as an RGB image.

    Supported layouts include "planar" and "nv21". The "yuv" formatted captures
    returned from the device via do_capture are in the "planar" layout; other
    layouts may only be needed for loading files from other sources.

    Args:
        yuv_fname: The path of the YUV420 file.
        w: The width of the image.
        h: The height of the image.
        layout: (Optional) the layout of the YUV data (as a string).
        ccm_yuv_to_rgb: (Optional) the 3x3 CCM to convert from YUV to RGB.
        yuv_off: (Optional) offsets to subtract from each of Y,U,V values.

    Returns:
        RGB float-3 image array, with pixel values in [0.0, 1.0].
    """
    with open(yuv_fname, "rb") as f:
        if layout == "planar":
            # Plane of Y, plane of V, plane of U.
            y = numpy.fromfile(f, numpy.uint8, w*h, "")
            v = numpy.fromfile(f, numpy.uint8, w*h/4, "")
            u = numpy.fromfile(f, numpy.uint8, w*h/4, "")
        elif layout == "nv21":
            # Plane of Y, plane of interleaved VUVUVU...
            y = numpy.fromfile(f, numpy.uint8, w*h, "")
            vu = numpy.fromfile(f, numpy.uint8, w*h/2, "")
            v = vu[0::2]
            u = vu[1::2]
        else:
            raise its.error.Error('Unsupported image layout')
        return convert_yuv420_planar_to_rgb_image(
                y,u,v,w,h,ccm_yuv_to_rgb,yuv_off)


def load_yuv420_planar_to_yuv_planes(yuv_fname, w, h):
    """Load a YUV420 planar image file, and return Y, U, and V plane images.

    Args:
        yuv_fname: The path of the YUV420 file.
        w: The width of the image.
        h: The height of the image.

    Returns:
        Separate Y, U, and V images as float-1 Numpy arrays, pixels in [0,1].
        Note that pixel (0,0,0) is not black, since U,V pixels are centered at
        0.5, and also that the Y and U,V plane images returned are different
        sizes (due to chroma subsampling in the YUV420 format).
    """
    with open(yuv_fname, "rb") as f:
        y = numpy.fromfile(f, numpy.uint8, w*h, "")
        v = numpy.fromfile(f, numpy.uint8, w*h/4, "")
        u = numpy.fromfile(f, numpy.uint8, w*h/4, "")
        return ((y.astype(numpy.float32) / 255.0).reshape(h, w, 1),
                (u.astype(numpy.float32) / 255.0).reshape(h/2, w/2, 1),
                (v.astype(numpy.float32) / 255.0).reshape(h/2, w/2, 1))


def decompress_jpeg_to_rgb_image(jpeg_buffer):
    """Decompress a JPEG-compressed image, returning as an RGB image.

    Args:
        jpeg_buffer: The JPEG stream.

    Returns:
        A numpy array for the RGB image, with pixels in [0,1].
    """
    img = Image.open(cStringIO.StringIO(jpeg_buffer))
    w = img.size[0]
    h = img.size[1]
    return numpy.array(img).reshape(h,w,3) / 255.0


def apply_lut_to_image(img, lut):
    """Applies a LUT to every pixel in a float image array.

    Internally converts to a 16b integer image, since the LUT can work with up
    to 16b->16b mappings (i.e. values in the range [0,65535]). The lut can also
    have fewer than 65536 entries, however it must be sized as a power of 2
    (and for smaller luts, the scale must match the bitdepth).

    For a 16b lut of 65536 entries, the operation performed is:

        lut[r * 65535] / 65535 -> r'
        lut[g * 65535] / 65535 -> g'
        lut[b * 65535] / 65535 -> b'

    For a 10b lut of 1024 entries, the operation becomes:

        lut[r * 1023] / 1023 -> r'
        lut[g * 1023] / 1023 -> g'
        lut[b * 1023] / 1023 -> b'

    Args:
        img: Numpy float image array, with pixel values in [0,1].
        lut: Numpy table encoding a LUT, mapping 16b integer values.

    Returns:
        Float image array after applying LUT to each pixel.
    """
    n = len(lut)
    if n <= 0 or n > MAX_LUT_SIZE or (n & (n - 1)) != 0:
        raise its.error.Error('Invalid arg LUT size: %d' % (n))
    m = float(n-1)
    return (lut[(img * m).astype(numpy.uint16)] / m).astype(numpy.float32)


def apply_matrix_to_image(img, mat):
    """Multiplies a 3x3 matrix with each float-3 image pixel.

    Each pixel is considered a column vector, and is left-multiplied by
    the given matrix:

        [     ]   r    r'
        [ mat ] * g -> g'
        [     ]   b    b'

    Args:
        img: Numpy float image array, with pixel values in [0,1].
        mat: Numpy 3x3 matrix.

    Returns:
        The numpy float-3 image array resulting from the matrix mult.
    """
    h = img.shape[0]
    w = img.shape[1]
    img2 = numpy.empty([h, w, 3], dtype=numpy.float32)
    img2.reshape(w*h*3)[:] = (numpy.dot(img.reshape(h*w, 3), mat.T)
                             ).reshape(w*h*3)[:]
    return img2


def get_image_patch(img, xnorm, ynorm, wnorm, hnorm):
    """Get a patch (tile) of an image.

    Args:
        img: Numpy float image array, with pixel values in [0,1].
        xnorm,ynorm,wnorm,hnorm: Normalized (in [0,1]) coords for the tile.

    Returns:
        Float image array of the patch.
    """
    hfull = img.shape[0]
    wfull = img.shape[1]
    xtile = math.ceil(xnorm * wfull)
    ytile = math.ceil(ynorm * hfull)
    wtile = math.floor(wnorm * wfull)
    htile = math.floor(hnorm * hfull)
    return img[ytile:ytile+htile,xtile:xtile+wtile,:].copy()


def compute_image_means(img):
    """Calculate the mean of each color channel in the image.

    Args:
        img: Numpy float image array, with pixel values in [0,1].

    Returns:
        A list of mean values, one per color channel in the image.
    """
    means = []
    chans = img.shape[2]
    for i in xrange(chans):
        means.append(numpy.mean(img[:,:,i], dtype=numpy.float64))
    return means


def compute_image_variances(img):
    """Calculate the variance of each color channel in the image.

    Args:
        img: Numpy float image array, with pixel values in [0,1].

    Returns:
        A list of mean values, one per color channel in the image.
    """
    variances = []
    chans = img.shape[2]
    for i in xrange(chans):
        variances.append(numpy.var(img[:,:,i], dtype=numpy.float64))
    return variances


def compute_image_snrs(img):
    """Calculate the SNR (db) of each color channel in the image.

    Args:
        img: Numpy float image array, with pixel values in [0,1].

    Returns:
        A list of SNR value, one per color channel in the image.
    """
    means = compute_image_means(img)
    variances = compute_image_variances(img)
    std_devs = [math.sqrt(v) for v in variances]
    snr = [20 * math.log10(m/s) for m,s in zip(means, std_devs)]
    return snr


def write_image(img, fname, apply_gamma=False):
    """Save a float-3 numpy array image to a file.

    Supported formats: PNG, JPEG, and others; see PIL docs for more.

    Image can be 3-channel, which is interpreted as RGB, or can be 1-channel,
    which is greyscale.

    Can optionally specify that the image should be gamma-encoded prior to
    writing it out; this should be done if the image contains linear pixel
    values, to make the image look "normal".

    Args:
        img: Numpy image array data.
        fname: Path of file to save to; the extension specifies the format.
        apply_gamma: (Optional) apply gamma to the image prior to writing it.
    """
    if apply_gamma:
        img = apply_lut_to_image(img, DEFAULT_GAMMA_LUT)
    (h, w, chans) = img.shape
    if chans == 3:
        Image.fromarray((img * 255.0).astype(numpy.uint8), "RGB").save(fname)
    elif chans == 1:
        img3 = (img * 255.0).astype(numpy.uint8).repeat(3).reshape(h,w,3)
        Image.fromarray(img3, "RGB").save(fname)
    else:
        raise its.error.Error('Unsupported image type')


def downscale_image(img, f):
    """Shrink an image by a given integer factor.

    This function computes output pixel values by averaging over rectangular
    regions of the input image; it doesn't skip or sample pixels, and all input
    image pixels are evenly weighted.

    If the downscaling factor doesn't cleanly divide the width and/or height,
    then the remaining pixels on the right or bottom edge are discarded prior
    to the downscaling.

    Args:
        img: The input image as an ndarray.
        f: The downscaling factor, which should be an integer.

    Returns:
        The new (downscaled) image, as an ndarray.
    """
    h,w,chans = img.shape
    f = int(f)
    assert(f >= 1)
    h = (h/f)*f
    w = (w/f)*f
    img = img[0:h:,0:w:,::]
    chs = []
    for i in xrange(chans):
        ch = img.reshape(h*w*chans)[i::chans].reshape(h,w)
        ch = ch.reshape(h,w/f,f).mean(2).reshape(h,w/f)
        ch = ch.T.reshape(w/f,h/f,f).mean(2).T.reshape(h/f,w/f)
        chs.append(ch.reshape(h*w/(f*f)))
    img = numpy.vstack(chs).T.reshape(h/f,w/f,chans)
    return img


def compute_image_sharpness(img):
    """Calculate the sharpness of input image.

    Args:
        img: Numpy float RGB/luma image array, with pixel values in [0,1].

    Returns:
        A sharpness estimation value based on the average of gradient magnitude.
        Larger value means the image is sharper.
    """
    chans = img.shape[2]
    assert(chans == 1 or chans == 3)
    if (chans == 1):
        luma = img[:, :, 0]
    elif (chans == 3):
        luma = 0.299 * img[:,:,0] + 0.587 * img[:,:,1] + 0.114 * img[:,:,2]

    [gy, gx] = numpy.gradient(luma)
    return numpy.average(numpy.sqrt(gy*gy + gx*gx))

def normalize_img(img):
    """Normalize the image values to between 0 and 1.

    Args:
        img: 2-D numpy array of image values
    Returns:
        Normalized image
    """
    return (img - numpy.amin(img))/(numpy.amax(img) - numpy.amin(img))

def flip_mirror_img_per_argv(img):
    """Flip/mirror an image if "flip" or "mirror" is in argv

    Args:
        img: 2-D numpy array of image values
    Returns:
        Flip/mirrored image
    """
    img_out = img
    if "flip" in sys.argv:
        img_out = numpy.flipud(img_out)
    if "mirror" in sys.argv:
        img_out = numpy.fliplr(img_out)
    return img_out

def stationary_lens_cap(cam, req, fmt):
    """Take up to NUM_TRYS caps and save the 1st one with lens stationary.

    Args:
        cam:    open device session
        req:    capture request
        fmt:    format for capture

    Returns:
        capture
    """
    trys = 0
    done = False
    reqs = [req] * NUM_FRAMES
    while not done:
        print 'Waiting for lens to move to correct location...'
        cap = cam.do_capture(reqs, fmt)
        done = (cap[NUM_FRAMES-1]['metadata']['android.lens.state'] == 0)
        print ' status: ', done
        trys += 1
        if trys == NUM_TRYS:
            raise its.error.Error('Cannot settle lens after %d trys!' % trys)
    return cap[NUM_FRAMES-1]

class __UnitTest(unittest.TestCase):
    """Run a suite of unit tests on this module.
    """

    # TODO: Add more unit tests.

    def test_apply_matrix_to_image(self):
        """Unit test for apply_matrix_to_image.

        Test by using a canned set of values on a 1x1 pixel image.

            [ 1 2 3 ]   [ 0.1 ]   [ 1.4 ]
            [ 4 5 6 ] * [ 0.2 ] = [ 3.2 ]
            [ 7 8 9 ]   [ 0.3 ]   [ 5.0 ]
               mat         x         y
        """
        mat = numpy.array([[1,2,3], [4,5,6], [7,8,9]])
        x = numpy.array([0.1,0.2,0.3]).reshape(1,1,3)
        y = apply_matrix_to_image(x, mat).reshape(3).tolist()
        y_ref = [1.4,3.2,5.0]
        passed = all([math.fabs(y[i] - y_ref[i]) < 0.001 for i in xrange(3)])
        self.assertTrue(passed)

    def test_apply_lut_to_image(self):
        """Unit test for apply_lut_to_image.

        Test by using a canned set of values on a 1x1 pixel image. The LUT will
        simply double the value of the index:

            lut[x] = 2*x
        """
        lut = numpy.array([2*i for i in xrange(65536)])
        x = numpy.array([0.1,0.2,0.3]).reshape(1,1,3)
        y = apply_lut_to_image(x, lut).reshape(3).tolist()
        y_ref = [0.2,0.4,0.6]
        passed = all([math.fabs(y[i] - y_ref[i]) < 0.001 for i in xrange(3)])
        self.assertTrue(passed)

    def test_unpack_raw10_image(self):
        """Unit test for unpack_raw10_image.

        RAW10 bit packing format
                bit 7   bit 6   bit 5   bit 4   bit 3   bit 2   bit 1   bit 0
        Byte 0: P0[9]   P0[8]   P0[7]   P0[6]   P0[5]   P0[4]   P0[3]   P0[2]
        Byte 1: P1[9]   P1[8]   P1[7]   P1[6]   P1[5]   P1[4]   P1[3]   P1[2]
        Byte 2: P2[9]   P2[8]   P2[7]   P2[6]   P2[5]   P2[4]   P2[3]   P2[2]
        Byte 3: P3[9]   P3[8]   P3[7]   P3[6]   P3[5]   P3[4]   P3[3]   P3[2]
        Byte 4: P3[1]   P3[0]   P2[1]   P2[0]   P1[1]   P1[0]   P0[1]   P0[0]
        """
        # test by using a random 4x4 10-bit image
        H = 4
        W = 4
        check_list = random.sample(range(0, 1024), H*W)
        img_check = numpy.array(check_list).reshape(H, W)
        # pack bits
        for row_start in range(0, len(check_list), W):
            msbs = []
            lsbs = ""
            for pixel in range(W):
                val = numpy.binary_repr(check_list[row_start+pixel], 10)
                msbs.append(int(val[:8], base=2))
                lsbs = val[8:] + lsbs
            packed = msbs
            packed.append(int(lsbs, base=2))
            chunk_raw10 = numpy.array(packed, dtype="uint8").reshape(1, 5)
            if row_start == 0:
                img_raw10 = chunk_raw10
            else:
                img_raw10 = numpy.vstack((img_raw10, chunk_raw10))
        # unpack and check against original
        self.assertTrue(numpy.array_equal(unpack_raw10_image(img_raw10),
                                          img_check))

if __name__ == "__main__":
    unittest.main()
