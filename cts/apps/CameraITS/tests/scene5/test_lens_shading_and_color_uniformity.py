# Copyright 2016 The Android Open Source Project
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
import numpy
import cv2
import math


def main():
    """ Test that the lens shading correction is applied appropriately, and
    color of a monochrome uniform scene is evenly distributed, for example,
    when a diffuser is placed in front of the camera.
    Perform this test on a yuv frame with auto 3a. Lens shading is evaluated
    based on the y channel. Measure the average y value for each sample block
    specified, and then determine pass/fail by comparing with the center y
    value.
    The color uniformity test is evaluated in r/g and b/g space. At specified
    radius of the image, the variance of r/g and b/g value need to be less than
    a threshold in order to pass the test.
    """
    NAME = os.path.basename(__file__).split(".")[0]
    # Sample block center location and length
    Num_radius = 8
    spb_r = 1/2./(Num_radius*2-1)
    SPB_CT_LIST = numpy.arange(spb_r, 1/2., spb_r*2)

    # Threshold for pass/fail
    THRES_LS_CT = 0.9    # len shading allowance for center
    THRES_LS_CN = 0.6    # len shading allowance for corner
    THRES_LS_HIGH = 0.2  # max allowed percentage for a patch to be brighter
                         # than center
    THRES_UFMT = 0.1     # uniformity allowance
    # Drawing color
    RED = (1, 0, 0)   # blocks failed the test
    GREEN = (0, 0.7, 0.3)   # blocks passed the test

    with its.device.ItsSession() as cam:
        props = cam.get_camera_properties()
        if its.caps.read_3a(props):
            # Converge 3A and get the estimates.
            sens, exp, gains, xform, focus = cam.do_3a(get_results=True,
                                                       do_af=False,
                                                       lock_ae=True,
                                                       lock_awb=True)
            print "AE sensitivity %d, exposure %dms" % (sens, exp / 1000000.0)
            print "AWB gains", gains
            print "AWB transform", xform
            print "AF distance", focus
        req = its.objects.auto_capture_request()
        img_size = its.objects.get_available_output_sizes("yuv", props)
        w = img_size[0][0]
        h = img_size[0][1]
        out_surface = {"format": "yuv"}
        cap = cam.do_capture(req, out_surface)
        print "Captured yuv %dx%d" % (w, h)
        # rgb image
        img_rgb = its.image.convert_capture_to_rgb_image(cap)
        img_g_pos = img_rgb[:, :, 1] + 0.001  # in case g channel is zero.
        r_g = img_rgb[:, :, 0] / img_g_pos
        b_g = img_rgb[:, :, 2] / img_g_pos
        # y channel
        img_y = its.image.convert_capture_to_planes(cap)[0]
        its.image.write_image(img_y, "%s_y_plane.png" % NAME, True)

        # Evaluation begins
        # image with legend
        img_legend_ls = numpy.copy(img_rgb)
        img_legend_ufmt = numpy.copy(img_rgb)
        line_width = max(2, int(max(h, w)/500))  # line width of legend
        font_scale = line_width / 7.0   # font scale of the basic font size
        text_height = cv2.getTextSize('gf', cv2.FONT_HERSHEY_SIMPLEX,
                                      font_scale, line_width)[0][1]
        text_offset = int(text_height*1.5)

        # center block average Y value, r/g, and b/g
        top = int((0.5-spb_r)*h)
        bottom = int((0.5+spb_r)*h)
        left = int((0.5-spb_r)*w)
        right = int((0.5+spb_r)*w)
        center_y = numpy.mean(img_y[top:bottom, left:right])
        center_r_g = numpy.mean(r_g[top:bottom, left:right])
        center_b_g = numpy.mean(b_g[top:bottom, left:right])
        # add legend to lens Shading figure
        cv2.rectangle(img_legend_ls, (left, top), (right, bottom), GREEN,
                      line_width)
        draw_legend(img_legend_ls, ["Y: %.2f" % center_y],
                    [left+text_offset, bottom-text_offset],
                    font_scale, text_offset, GREEN, int(line_width/2))
        # add legend to color uniformity figure
        cv2.rectangle(img_legend_ufmt, (left, top), (right, bottom), GREEN,
                      line_width)
        texts = ["r/g: %.2f" % center_r_g,
                 "b/g: %.2f" % center_b_g]
        draw_legend(img_legend_ufmt, texts,
                    [left+text_offset, bottom-text_offset*2],
                    font_scale, text_offset, GREEN, int(line_width/2))

        # evaluate y and r/g, b/g for each block
        ls_test_failed = []
        cu_test_failed = []
        ls_thres_h = center_y * (1 + THRES_LS_HIGH)
        dist_max = math.sqrt(pow(w, 2)+pow(h, 2))/2
        for spb_ct in SPB_CT_LIST:
            # list sample block center location
            num_sample = int(numpy.asscalar((1-spb_ct*2)/spb_r/2 + 1))
            ct_cord_x = numpy.concatenate(
                        (numpy.arange(spb_ct, 1-spb_ct+spb_r, spb_r*2),
                         spb_ct*numpy.ones((num_sample-1)),
                         (1-spb_ct)*numpy.ones((num_sample-1)),
                         numpy.arange(spb_ct, 1-spb_ct+spb_r, spb_r*2)))
            ct_cord_y = numpy.concatenate(
                        (spb_ct*numpy.ones(num_sample+1),
                         numpy.arange(spb_ct+spb_r*2, 1-spb_ct, spb_r*2),
                         numpy.arange(spb_ct+spb_r*2, 1-spb_ct, spb_r*2),
                         (1-spb_ct)*numpy.ones(num_sample+1)))

            blocks_info = []
            max_r_g = 0
            min_r_g = float("inf")
            max_b_g = 0
            min_b_g = float("inf")
            for spb_ctx, spb_cty in zip(ct_cord_x, ct_cord_y):
                top = int((spb_cty-spb_r)*h)
                bottom = int((spb_cty+spb_r)*h)
                left = int((spb_ctx-spb_r)*w)
                right = int((spb_ctx+spb_r)*w)
                dist_to_img_center = math.sqrt(pow(abs(spb_ctx-0.5)*w, 2)
                                     + pow(abs(spb_cty-0.5)*h, 2))
                ls_thres_l = ((THRES_LS_CT-THRES_LS_CN)*(1-dist_to_img_center
                              /dist_max)+THRES_LS_CN) * center_y

                # compute block average value
                block_y = numpy.mean(img_y[top:bottom, left:right])
                block_r_g = numpy.mean(r_g[top:bottom, left:right])
                block_b_g = numpy.mean(b_g[top:bottom, left:right])
                max_r_g = max(max_r_g, block_r_g)
                min_r_g = min(min_r_g, block_r_g)
                max_b_g = max(max_b_g, block_b_g)
                min_b_g = min(min_b_g, block_b_g)
                blocks_info.append({"pos": [top, bottom, left, right],
                                    "block_r_g": block_r_g,
                                    "block_b_g": block_b_g})
                # check lens shading and draw legend
                if block_y > ls_thres_h or block_y < ls_thres_l:
                    ls_test_failed.append({"pos": [top, bottom, left,
                                                   right],
                                           "val": block_y,
                                           "thres_l": ls_thres_l})
                    legend_color = RED
                else:
                    legend_color = GREEN
                text_bottom = bottom - text_offset
                cv2.rectangle(img_legend_ls, (left, top), (right, bottom),
                              legend_color, line_width)
                draw_legend(img_legend_ls, ["Y: %.2f" % block_y],
                            [left+text_offset, text_bottom], font_scale,
                            text_offset, legend_color, int(line_width/2))

            # check color uniformity and draw legend
            ufmt_r_g = (max_r_g-min_r_g) / center_r_g
            ufmt_b_g = (max_b_g-min_b_g) / center_b_g
            if ufmt_r_g > THRES_UFMT or ufmt_b_g > THRES_UFMT:
                cu_test_failed.append({"pos": spb_ct,
                                       "ufmt_r_g": ufmt_r_g,
                                       "ufmt_b_g": ufmt_b_g})
                legend_color = RED
            else:
                legend_color = GREEN
            for block in blocks_info:
                top, bottom, left, right = block["pos"]
                cv2.rectangle(img_legend_ufmt, (left, top), (right, bottom),
                              legend_color, line_width)
                texts = ["r/g: %.2f" % block["block_r_g"],
                         "b/g: %.2f" % block["block_b_g"]]
                text_bottom = bottom - text_offset * 2
                draw_legend(img_legend_ufmt, texts,
                            [left+text_offset, text_bottom], font_scale,
                            text_offset, legend_color, int(line_width/2))

        # Save images
        its.image.write_image(img_legend_ufmt,
                              "%s_color_uniformity_result.png" % NAME, True)
        its.image.write_image(img_legend_ls,
                              "%s_lens_shading_result.png" % NAME, True)

        # print results
        lens_shading_test_passed = True
        color_uniformity_test_passed = True
        if len(ls_test_failed) > 0:
            lens_shading_test_passed = False
            print "\nLens shading test summary"
            print "Center block average Y value: %.3f" % center_y
            print "Blocks failed in the lens shading test:"
            for block in ls_test_failed:
                top, bottom, left, right = block["pos"]
                print "Block position: [top: %d, bottom: %d, left: %d, right: "\
                      "%d]; average Y value: %.3f; valid value range: %.3f ~ " \
                      "%.3f" % (top, bottom, left, right, block["val"],
                      block["thres_l"], ls_thres_h)
        if len(cu_test_failed) > 0:
            color_uniformity_test_passed = False
            print "\nColor uniformity test summary"
            print "Valid color uniformity value range: 0 ~ ", THRES_UFMT
            print "Areas that failed the color uniformity test:"
            for rd in cu_test_failed:
                print "Radius position: %.3f; r/g uniformity: %.3f; b/g " \
                      "uniformity: %.3f" % (rd["pos"], rd["ufmt_r_g"],
                      rd["ufmt_b_g"])
        assert lens_shading_test_passed
        assert color_uniformity_test_passed


def draw_legend(img, texts, text_org, font_scale, text_offset, legend_color,
                line_width):
    """ Draw legend on an image.

    Args:
        img: Numpy float image array in RGB, with pixel values in [0,1].
        texts: list of legends. Each element in the list is a line of legend.
        text_org: tuple of the bottom left corner of the text position in
            pixels, horizontal and vertical.
        font_scale: float number. Font scale of the basic font size.
        text_offset: text line width in pixels.
        legend_color: text color in rgb value.
        line_width: strokes width in pixels.
    """
    for text in texts:
        cv2.putText(img, text, (text_org[0], text_org[1]),
                    cv2.FONT_HERSHEY_SIMPLEX, font_scale,
                    legend_color, line_width)
        text_org[1] += text_offset


if __name__ == '__main__':
    main()
