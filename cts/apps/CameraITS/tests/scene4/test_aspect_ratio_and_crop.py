# Copyright 2015 The Android Open Source Project
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
import cv2
import numpy as np


def main():
    """ Test aspect ratio and check if images are cropped correctly under each
    output size
    Aspect ratio test runs on level3, full and limited devices. Crop test only
    runs on full and level3 devices.
    The test image is a black circle inside a black square. When raw capture is
    available, set the height vs. width ratio of the circle in the full-frame
    raw as ground truth. Then compare with images of request combinations of
    different formats ("jpeg" and "yuv") and sizes.
    If raw capture is unavailable, take a picture of the test image right in
    front to eliminate shooting angle effect. the height vs. width ratio for
    the circle should be close to 1. Considering shooting position error, aspect
    ratio greater than 1.05 or smaller than 0.95 will fail the test.
    """
    NAME = os.path.basename(__file__).split(".")[0]
    LARGE_SIZE = 2000   # Define the size of a large image
    # pass/fail threshold of large size images for aspect ratio test
    THRES_L_AR_TEST = 0.02
    # pass/fail threshold of mini size images for aspect ratio test
    THRES_XS_AR_TEST = 0.05
    # pass/fail threshold of large size images for crop test
    THRES_L_CP_TEST = 0.02
    # pass/fail threshold of mini size images for crop test
    THRES_XS_CP_TEST = 0.05
    # Crop test will allow at least THRES_MIN_PIXEL offset
    THRES_MIN_PIXEL = 4
    PREVIEW_SIZE = (1920, 1080) # preview size
    aspect_ratio_gt = 1  # ground truth
    failed_ar = []  # streams failed the aspect ration test
    failed_crop = [] # streams failed the crop test
    format_list = [] # format list for multiple capture objects.
    # Do multi-capture of "iter" and "cmpr". Iterate through all the
    # available sizes of "iter", and only use the size specified for "cmpr"
    # Do single-capture to cover untouched sizes in multi-capture when needed.
    format_list.append({"iter": "yuv", "iter_max": None,
                        "cmpr": "yuv", "cmpr_size": PREVIEW_SIZE})
    format_list.append({"iter": "yuv", "iter_max": PREVIEW_SIZE,
                        "cmpr": "jpeg", "cmpr_size": None})
    format_list.append({"iter": "yuv", "iter_max": PREVIEW_SIZE,
                        "cmpr": "raw", "cmpr_size": None})
    format_list.append({"iter": "jpeg", "iter_max": None,
                        "cmpr": "raw", "cmpr_size": None})
    format_list.append({"iter": "jpeg", "iter_max": None,
                        "cmpr": "yuv", "cmpr_size": PREVIEW_SIZE})
    with its.device.ItsSession() as cam:
        props = cam.get_camera_properties()
        # Todo: test for radial distortion enabled devices has not yet been
        # implemented
        its.caps.skip_unless(not its.caps.radial_distortion_correction(props))
        its.caps.skip_unless(its.caps.read_3a(props))
        full_device = its.caps.full_or_better(props)
        limited_device = its.caps.limited(props)
        its.caps.skip_unless(full_device or limited_device)
        level3_device = its.caps.level3(props)
        raw_avlb = its.caps.raw16(props)
        run_crop_test = (level3_device or full_device) and raw_avlb
        if not run_crop_test:
            print "Crop test skipped"
        debug = its.caps.debug_mode()
        # Converge 3A and get the estimates.
        sens, exp, gains, xform, focus = cam.do_3a(get_results=True,
                                                   lock_ae=True, lock_awb=True)
        print "AE sensitivity %d, exposure %dms" % (sens, exp / 1000000.0)
        print "AWB gains", gains
        print "AWB transform", xform
        print "AF distance", focus
        req = its.objects.manual_capture_request(
                sens, exp, focus, True, props)
        xform_rat = its.objects.float_to_rational(xform)
        req["android.colorCorrection.gains"] = gains
        req["android.colorCorrection.transform"] = xform_rat

        # If raw capture is available, use it as ground truth.
        if raw_avlb:
            # Capture full-frame raw. Use its aspect ratio and circle center
            # location as ground truth for the other jepg or yuv images.
            out_surface = {"format": "raw"}
            cap_raw = cam.do_capture(req, out_surface)
            print "Captured %s %dx%d" % ("raw", cap_raw["width"],
                                         cap_raw["height"])
            img_raw = its.image.convert_capture_to_rgb_image(cap_raw,
                                                             props=props)
            size_raw = img_raw.shape
            img_name = "%s_%s_w%d_h%d.png" \
                       % (NAME, "raw", size_raw[1], size_raw[0])
            aspect_ratio_gt, cc_ct_gt, circle_size_raw = measure_aspect_ratio(
                                                         img_raw, 1, img_name,
                                                         debug)
            # Normalize the circle size to 1/4 of the image size, so that
            # circle size won"t affect the crop test result
            factor_cp_thres = (min(size_raw[0:1])/4.0) / max(circle_size_raw)
            thres_l_cp_test = THRES_L_CP_TEST * factor_cp_thres
            thres_xs_cp_test = THRES_XS_CP_TEST * factor_cp_thres

        # Take pictures of each settings with all the image sizes available.
        for fmt in format_list:
            fmt_iter = fmt["iter"]
            fmt_cmpr = fmt["cmpr"]
            dual_target = fmt_cmpr is not "none"
            # Get the size of "cmpr"
            if dual_target:
                sizes = its.objects.get_available_output_sizes(
                        fmt_cmpr, props, fmt["cmpr_size"])
                if len(sizes) == 0: # device might not support RAW
                    continue
                size_cmpr = sizes[0]
            for size_iter in its.objects.get_available_output_sizes(
                    fmt_iter, props, fmt["iter_max"]):
                w_iter = size_iter[0]
                h_iter = size_iter[1]
                # Skip testing same format/size combination
                # ITS does not handle that properly now
                if dual_target and \
                        w_iter == size_cmpr[0] and \
                        h_iter == size_cmpr[1] and \
                        fmt_iter == fmt_cmpr:
                    continue
                out_surface = [{"width": w_iter,
                                "height": h_iter,
                                "format": fmt_iter}]
                if dual_target:
                    out_surface.append({"width": size_cmpr[0],
                                        "height": size_cmpr[1],
                                        "format": fmt_cmpr})
                cap = cam.do_capture(req, out_surface)
                if dual_target:
                    frm_iter = cap[0]
                else:
                    frm_iter = cap
                assert (frm_iter["format"] == fmt_iter)
                assert (frm_iter["width"] == w_iter)
                assert (frm_iter["height"] == h_iter)
                print "Captured %s with %s %dx%d" \
                        % (fmt_iter, fmt_cmpr, w_iter, h_iter)
                img = its.image.convert_capture_to_rgb_image(frm_iter)
                img_name = "%s_%s_with_%s_w%d_h%d.png" \
                           % (NAME, fmt_iter, fmt_cmpr, w_iter, h_iter)
                aspect_ratio, cc_ct, (cc_w, cc_h) = \
                        measure_aspect_ratio(img, raw_avlb, img_name,
                                             debug)
                # check pass/fail for aspect ratio
                # image size >= LARGE_SIZE: use THRES_L_AR_TEST
                # image size == 0 (extreme case): THRES_XS_AR_TEST
                # 0 < image size < LARGE_SIZE: scale between THRES_XS_AR_TEST
                # and THRES_L_AR_TEST
                thres_ar_test = max(THRES_L_AR_TEST,
                        THRES_XS_AR_TEST + max(w_iter, h_iter) *
                        (THRES_L_AR_TEST-THRES_XS_AR_TEST)/LARGE_SIZE)
                thres_range_ar = (aspect_ratio_gt-thres_ar_test,
                                  aspect_ratio_gt+thres_ar_test)
                if aspect_ratio < thres_range_ar[0] \
                        or aspect_ratio > thres_range_ar[1]:
                    failed_ar.append({"fmt_iter": fmt_iter,
                                      "fmt_cmpr": fmt_cmpr,
                                      "w": w_iter, "h": h_iter,
                                      "ar": aspect_ratio,
                                      "valid_range": thres_range_ar})

                # check pass/fail for crop
                if run_crop_test:
                    # image size >= LARGE_SIZE: use thres_l_cp_test
                    # image size == 0 (extreme case): thres_xs_cp_test
                    # 0 < image size < LARGE_SIZE: scale between
                    # thres_xs_cp_test and thres_l_cp_test
                    # Also, allow at least THRES_MIN_PIXEL off to
                    # prevent threshold being too tight for very
                    # small circle
                    thres_hori_cp_test = max(thres_l_cp_test,
                            thres_xs_cp_test + w_iter *
                            (thres_l_cp_test-thres_xs_cp_test)/LARGE_SIZE)
                    min_threshold_h = THRES_MIN_PIXEL / cc_w
                    thres_hori_cp_test = max(thres_hori_cp_test,
                            min_threshold_h)
                    thres_range_h_cp = (cc_ct_gt["hori"]-thres_hori_cp_test,
                                        cc_ct_gt["hori"]+thres_hori_cp_test)
                    thres_vert_cp_test = max(thres_l_cp_test,
                            thres_xs_cp_test + h_iter *
                            (thres_l_cp_test-thres_xs_cp_test)/LARGE_SIZE)
                    min_threshold_v = THRES_MIN_PIXEL / cc_h
                    thres_vert_cp_test = max(thres_vert_cp_test,
                            min_threshold_v)
                    thres_range_v_cp = (cc_ct_gt["vert"]-thres_vert_cp_test,
                                        cc_ct_gt["vert"]+thres_vert_cp_test)
                    if cc_ct["hori"] < thres_range_h_cp[0] \
                            or cc_ct["hori"] > thres_range_h_cp[1] \
                            or cc_ct["vert"] < thres_range_v_cp[0] \
                            or cc_ct["vert"] > thres_range_v_cp[1]:
                        failed_crop.append({"fmt_iter": fmt_iter,
                                            "fmt_cmpr": fmt_cmpr,
                                            "w": w_iter, "h": h_iter,
                                            "ct_hori": cc_ct["hori"],
                                            "ct_vert": cc_ct["vert"],
                                            "valid_range_h": thres_range_h_cp,
                                            "valid_range_v": thres_range_v_cp})

        # Print aspect ratio test results
        failed_image_number_for_aspect_ratio_test = len(failed_ar)
        if failed_image_number_for_aspect_ratio_test > 0:
            print "\nAspect ratio test summary"
            print "Images failed in the aspect ratio test:"
            print "Aspect ratio value: width / height"
        for fa in failed_ar:
            print "%s with %s %dx%d: %.3f; valid range: %.3f ~ %.3f" % \
                  (fa["fmt_iter"], fa["fmt_cmpr"], fa["w"], fa["h"], fa["ar"],
                   fa["valid_range"][0], fa["valid_range"][1])

        # Print crop test results
        failed_image_number_for_crop_test = len(failed_crop)
        if failed_image_number_for_crop_test > 0:
            print "\nCrop test summary"
            print "Images failed in the crop test:"
            print "Circle center position, (horizontal x vertical), listed " \
                  "below is relative to the image center."
        for fc in failed_crop:
            print "%s with %s %dx%d: %.3f x %.3f; " \
                    "valid horizontal range: %.3f ~ %.3f; " \
                    "valid vertical range: %.3f ~ %.3f" \
                    % (fc["fmt_iter"], fc["fmt_cmpr"], fc["w"], fc["h"],
                    fc["ct_hori"], fc["ct_vert"], fc["valid_range_h"][0],
                    fc["valid_range_h"][1], fc["valid_range_v"][0],
                    fc["valid_range_v"][1])

        assert (failed_image_number_for_aspect_ratio_test == 0)
        if level3_device:
            assert (failed_image_number_for_crop_test == 0)


def measure_aspect_ratio(img, raw_avlb, img_name, debug):
    """ Measure the aspect ratio of the black circle in the test image.

    Args:
        img: Numpy float image array in RGB, with pixel values in [0,1].
        raw_avlb: True: raw capture is available; False: raw capture is not
             available.
        img_name: string with image info of format and size.
        debug: boolean for whether in debug mode.
    Returns:
        aspect_ratio: aspect ratio number in float.
        cc_ct: circle center position relative to the center of image.
        (circle_w, circle_h): tuple of the circle size
    """
    size = img.shape
    img = img * 255
    # Gray image
    img_gray = 0.299 * img[:,:,2] + 0.587 * img[:,:,1] + 0.114 * img[:,:,0]

    # otsu threshold to binarize the image
    ret3, img_bw = cv2.threshold(np.uint8(img_gray), 0, 255,
            cv2.THRESH_BINARY + cv2.THRESH_OTSU)

    # connected component
    contours, hierarchy = cv2.findContours(255-img_bw, cv2.RETR_TREE,
            cv2.CHAIN_APPROX_SIMPLE)

    # Check each component and find the black circle
    min_cmpt = size[0] * size[1] * 0.005
    max_cmpt = size[0] * size[1] * 0.35
    num_circle = 0
    aspect_ratio = 0
    for ct, hrch in zip(contours, hierarchy[0]):
        # The radius of the circle is 1/3 of the length of the square, meaning
        # around 1/3 of the area of the square
        # Parental component should exist and the area is acceptable.
        # The coutour of a circle should have at least 5 points
        child_area = cv2.contourArea(ct)
        if hrch[3] == -1 or child_area < min_cmpt or child_area > max_cmpt or \
                len(ct) < 15:
            continue
        # Check the shapes of current component and its parent
        child_shape = component_shape(ct)
        parent = hrch[3]
        prt_shape = component_shape(contours[parent])
        prt_area = cv2.contourArea(contours[parent])
        dist_x = abs(child_shape["ctx"]-prt_shape["ctx"])
        dist_y = abs(child_shape["cty"]-prt_shape["cty"])
        # 1. 0.56*Parent"s width < Child"s width < 0.76*Parent"s width.
        # 2. 0.56*Parent"s height < Child"s height < 0.76*Parent"s height.
        # 3. Child"s width > 0.1*Image width
        # 4. Child"s height > 0.1*Image height
        # 5. 0.25*Parent"s area < Child"s area < 0.45*Parent"s area
        # 6. Child is a black, and Parent is white
        # 7. Center of Child and center of parent should overlap
        if prt_shape["width"] * 0.56 < child_shape["width"] \
                < prt_shape["width"] * 0.76 \
                and prt_shape["height"] * 0.56 < child_shape["height"] \
                < prt_shape["height"] * 0.76 \
                and child_shape["width"] > 0.1 * size[1] \
                and child_shape["height"] > 0.1 * size[0] \
                and 0.30 * prt_area < child_area < 0.50 * prt_area \
                and img_bw[child_shape["cty"]][child_shape["ctx"]] == 0 \
                and img_bw[child_shape["top"]][child_shape["left"]] == 255 \
                and dist_x < 0.1 * child_shape["width"] \
                and dist_y < 0.1 * child_shape["height"]:
            # If raw capture is not available, check the camera is placed right
            # in front of the test page:
            # 1. Distances between parent and child horizontally on both side,0
            #    dist_left and dist_right, should be close.
            # 2. Distances between parent and child vertically on both side,
            #    dist_top and dist_bottom, should be close.
            if not raw_avlb:
                dist_left = child_shape["left"] - prt_shape["left"]
                dist_right = prt_shape["right"] - child_shape["right"]
                dist_top = child_shape["top"] - prt_shape["top"]
                dist_bottom = prt_shape["bottom"] - child_shape["bottom"]
                if abs(dist_left-dist_right) > 0.05 * child_shape["width"] or \
                        abs(dist_top-dist_bottom) > \
                        0.05 * child_shape["height"]:
                    continue
            # Calculate aspect ratio
            aspect_ratio = float(child_shape["width"]) / \
                           float(child_shape["height"])
            circle_ctx = child_shape["ctx"]
            circle_cty = child_shape["cty"]
            circle_w = float(child_shape["width"])
            circle_h = float(child_shape["height"])
            cc_ct = {"hori": float(child_shape["ctx"]-size[1]/2) / circle_w,
                     "vert": float(child_shape["cty"]-size[0]/2) / circle_h}
            num_circle += 1
            # If more than one circle found, break
            if num_circle == 2:
                break

    if num_circle == 0:
        its.image.write_image(img/255, img_name, True)
        print "No black circle was detected. Please take pictures according " \
              "to instruction carefully!\n"
        assert (num_circle == 1)

    if num_circle > 1:
        its.image.write_image(img/255, img_name, True)
        print "More than one black circle was detected. Background of scene " \
              "may be too complex.\n"
        assert (num_circle == 1)

    # draw circle center and image center, and save the image
    line_width = max(1, max(size)/500)
    move_text_dist = line_width * 3
    cv2.line(img, (circle_ctx, circle_cty), (size[1]/2, size[0]/2),
             (255, 0, 0), line_width)
    if circle_cty > size[0]/2:
        move_text_down_circle = 4
        move_text_down_image = -1
    else:
        move_text_down_circle = -1
        move_text_down_image = 4
    if circle_ctx > size[1]/2:
        move_text_right_circle = 2
        move_text_right_image = -1
    else:
        move_text_right_circle = -1
        move_text_right_image = 2
    # circle center
    text_circle_x = move_text_dist * move_text_right_circle + circle_ctx
    text_circle_y = move_text_dist * move_text_down_circle + circle_cty
    cv2.circle(img, (circle_ctx, circle_cty), line_width*2, (255, 0, 0), -1)
    cv2.putText(img, "circle center", (text_circle_x, text_circle_y),
                cv2.FONT_HERSHEY_SIMPLEX, line_width/2.0, (255, 0, 0),
                line_width)
    # image center
    text_imgct_x = move_text_dist * move_text_right_image + size[1]/2
    text_imgct_y = move_text_dist * move_text_down_image + size[0]/2
    cv2.circle(img, (size[1]/2, size[0]/2), line_width*2, (255, 0, 0), -1)
    cv2.putText(img, "image center", (text_imgct_x, text_imgct_y),
                cv2.FONT_HERSHEY_SIMPLEX, line_width/2.0, (255, 0, 0),
                line_width)
    if debug:
        its.image.write_image(img/255, img_name, True)

    print "Aspect ratio: %.3f" % aspect_ratio
    print "Circle center position regarding to image center: %.3fx%.3f" % \
            (cc_ct["vert"], cc_ct["hori"])
    return aspect_ratio, cc_ct, (circle_w, circle_h)


def component_shape(contour):
    """ Measure the shape for a connected component in the aspect ratio test

    Args:
        contour: return from cv2.findContours. A list of pixel coordinates of
        the contour.

    Returns:
        The most left, right, top, bottom pixel location, height, width, and
        the center pixel location of the contour.
    """
    shape = {"left": np.inf, "right": 0, "top": np.inf, "bottom": 0,
             "width": 0, "height": 0, "ctx": 0, "cty": 0}
    for pt in contour:
        if pt[0][0] < shape["left"]:
            shape["left"] = pt[0][0]
        if pt[0][0] > shape["right"]:
            shape["right"] = pt[0][0]
        if pt[0][1] < shape["top"]:
            shape["top"] = pt[0][1]
        if pt[0][1] > shape["bottom"]:
            shape["bottom"] = pt[0][1]
    shape["width"] = shape["right"] - shape["left"] + 1
    shape["height"] = shape["bottom"] - shape["top"] + 1
    shape["ctx"] = (shape["left"]+shape["right"])/2
    shape["cty"] = (shape["top"]+shape["bottom"])/2
    return shape


if __name__ == "__main__":
    main()
