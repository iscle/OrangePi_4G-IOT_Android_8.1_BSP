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

import numpy
import numpy.linalg
import unittest

# Illuminant IDs
A = 0
D65 = 1

def compute_cm_fm(illuminant, gains, ccm, cal):
    """Compute the ColorMatrix (CM) and ForwardMatrix (FM).

    Given a captured shot of a grey chart illuminated by either a D65 or a
    standard A illuminant, the HAL will produce the WB gains and transform,
    in the android.colorCorrection.gains and android.colorCorrection.transform
    tags respectively. These values have both golden module and per-unit
    calibration baked in.

    This function is used to take the per-unit gains, ccm, and calibration
    matrix, and compute the values that the DNG ColorMatrix and ForwardMatrix
    for the specified illuminant should be. These CM and FM values should be
    the same for all DNG files captured by all units of the same model (e.g.
    all Nexus 5 units). The calibration matrix should be the same for all DNGs
    saved by the same unit, but will differ unit-to-unit.

    Args:
        illuminant: 0 (A) or 1 (D65).
        gains: White balance gains, as a list of 4 floats.
        ccm: White balance transform matrix, as a list of 9 floats.
        cal: Per-unit calibration matrix, as a list of 9 floats.

    Returns:
        CM: The 3x3 ColorMatrix for the specified illuminant, as a numpy array
        FM: The 3x3 ForwardMatrix for the specified illuminant, as a numpy array
    """

    ###########################################################################
    # Standard matrices.

    # W is the matrix that maps sRGB to XYZ.
    # See: http://www.brucelindbloom.com/
    W = numpy.array([
        [ 0.4124564,  0.3575761,  0.1804375],
        [ 0.2126729,  0.7151522,  0.0721750],
        [ 0.0193339,  0.1191920,  0.9503041]])

    # HH is the chromatic adaptation matrix from D65 (since sRGB's ref white is
    # D65) to D50 (since CIE XYZ's ref white is D50).
    HH = numpy.array([
        [ 1.0478112,  0.0228866, -0.0501270],
        [ 0.0295424,  0.9904844, -0.0170491],
        [-0.0092345,  0.0150436,  0.7521316]])

    # H is a chromatic adaptation matrix from D65 (because sRGB's reference
    # white is D65) to the calibration illuminant (which is a standard matrix
    # depending on the illuminant). For a D65 illuminant, the matrix is the
    # identity. For the A illuminant, the matrix uses the linear Bradford
    # adaptation method to map from D65 to A.
    # See: http://www.brucelindbloom.com/
    H_D65 = numpy.array([
        [ 1.0,        0.0,        0.0],
        [ 0.0,        1.0,        0.0],
        [ 0.0,        0.0,        1.0]])
    H_A = numpy.array([
        [ 1.2164557,  0.1109905, -0.1549325],
        [ 0.1533326,  0.9152313, -0.0559953],
        [-0.0239469,  0.0358984,  0.3147529]])
    H = [H_A, H_D65][illuminant]

    ###########################################################################
    # Per-model matrices (that should be the same for all units of a particular
    # phone/camera. These are statics in the HAL camera properties.

    # G is formed by taking the r,g,b gains and putting them into a
    # diagonal matrix.
    G = numpy.array([[gains[0],0,0], [0,gains[1],0], [0,0,gains[3]]])

    # S is just the CCM.
    S = numpy.array([ccm[0:3], ccm[3:6], ccm[6:9]])

    ###########################################################################
    # Per-unit matrices.

    # The per-unit calibration matrix for the given illuminant.
    CC = numpy.array([cal[0:3],cal[3:6],cal[6:9]])

    ###########################################################################
    # Derived matrices. These should match up with DNG-related matrices
    # provided by the HAL.

    # The color matrix and forward matrix are computed as follows:
    #   CM = inv(H * W * S * G * CC)
    #   FM = HH * W * S
    CM = numpy.linalg.inv(
            numpy.dot(numpy.dot(numpy.dot(numpy.dot(H, W), S), G), CC))
    FM = numpy.dot(numpy.dot(HH, W), S)

    # The color matrix is normalized so that it maps the D50 (PCS) white
    # point to a maximum component value of 1.
    CM = CM / max(numpy.dot(CM, (0.9642957, 1.0, 0.8251046)))

    return CM, FM

def compute_asn(illuminant, cal, CM):
    """Compute the AsShotNeutral DNG value.

    This value is the only dynamic DNG value; the ForwardMatrix, ColorMatrix,
    and CalibrationMatrix values should be the same for every DNG saved by
    a given unit. The AsShotNeutral depends on the scene white balance
    estimate.

    This function computes what the DNG AsShotNeutral values should be, for
    a given ColorMatrix (which is computed from the WB gains and CCM for a
    shot taken of a grey chart under either A or D65 illuminants) and the
    per-unit calibration matrix.

    Args:
        illuminant: 0 (A) or 1 (D65).
        cal: Per-unit calibration matrix, as a list of 9 floats.
        CM: The computed 3x3 ColorMatrix for the illuminant, as a numpy array.

    Returns:
        ASN: The AsShotNeutral value, as a length-3 numpy array.
    """

    ###########################################################################
    # Standard matrices.

    # XYZCAL is the  XYZ coordinate of calibration illuminant (so A or D65).
    # See: Wyszecki & Stiles, "Color Science", second edition.
    XYZCAL_A = numpy.array([1.098675, 1.0, 0.355916])
    XYZCAL_D65 = numpy.array([0.950456, 1.0, 1.089058])
    XYZCAL = [XYZCAL_A, XYZCAL_D65][illuminant]

    ###########################################################################
    # Per-unit matrices.

    # The per-unit calibration matrix for the given illuminant.
    CC = numpy.array([cal[0:3],cal[3:6],cal[6:9]])

    ###########################################################################
    # Derived matrices.

    # The AsShotNeutral value is then the product of this final color matrix
    # with the XYZ coordinate of calibration illuminant.
    #   ASN = CC * CM * XYZCAL
    ASN = numpy.dot(numpy.dot(CC, CM), XYZCAL)

    # Normalize so the max vector element is 1.0.
    ASN = ASN / max(ASN)

    return ASN

class __UnitTest(unittest.TestCase):
    """Run a suite of unit tests on this module.
    """
    # TODO: Add more unit tests.

if __name__ == '__main__':
    unittest.main()

