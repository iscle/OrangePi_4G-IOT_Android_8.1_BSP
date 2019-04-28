/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2016 Mopria Alliance, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.bips.jni;

public final class LocalJobParams {
    public int borderless;
    public int duplex;
    public int pdf_render_resolution;
    public String job_name = null;
    public String job_originating_user_name = null;

    public int media_size;
    public int media_type;
    public int media_tray;

    public int render_flags;
    public int num_copies;
    public int color_space;

    public int print_resolution;
    public int printable_width;
    public int printable_height;

    public float job_margin_top;
    public float job_margin_left;
    public float job_margin_right;
    public float job_margin_bottom;

    public float page_width;
    public float page_height;
    public float page_margin_top;
    public float page_margin_left;
    public float page_margin_right;
    public float page_margin_bottom;

    public boolean fit_to_page;
    public boolean auto_rotate;
    public boolean fill_page;
    public boolean portrait_mode;
    public boolean landscape_mode;

    public String page_range = null;
    public String document_category = null;

    public byte[] nativeData = null;

    public int alignment = 0;
    public boolean document_scaling;

    @Override
    public String toString() {
        return "LocalJobParams{" +
                " borderless=" + borderless +
                " duplex=" + duplex +
                " pdf_render_resolution=" + pdf_render_resolution +
                " job_name=" + job_name +
                " job_originating_user_name=" + job_originating_user_name +
                " media_size=" + media_size +
                " media_type=" + media_type +
                " media_tray=" + media_tray +
                " render_flags=" + render_flags +
                " num_copies=" + num_copies +
                " color_space=" + color_space +
                " print_resolution=" + print_resolution +
                " printable_width=" + printable_width +
                " printable_height=" + printable_height +
                " job_margin_top=" + job_margin_top +
                " job_margin_left=" + job_margin_left +
                " job_margin_right=" + job_margin_right +
                " job_margin_bottom=" + job_margin_bottom +
                " page_width=" + page_width +
                " page_height=" + page_height +
                " page_margin_top=" + page_margin_top +
                " page_margin_left=" + page_margin_left +
                " page_margin_right=" + page_margin_right +
                " page_margin_bottom=" + page_margin_bottom +
                " fit_to_page=" + fit_to_page +
                " auto_rotate=" + auto_rotate +
                " fill_page=" + fill_page +
                " portrait_mode=" + portrait_mode +
                " landscape_mode=" + landscape_mode +
                " page_range=" + page_range +
                " document_category=" + document_category +
                " nativeData=" + !(nativeData == null) +
                " alignment=" + alignment +
                " document_scaling=" + document_scaling +
                "}";
    }
}