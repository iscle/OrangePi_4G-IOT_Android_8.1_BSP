/*
 * Copyright 2017, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.managedprovisioning.tools.anim;

import android.graphics.Color;

import com.android.managedprovisioning.preprovisioning.anim.ColorMatcher;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashSet;
import java.util.Set;

/** Generates swiper themes (styles_swiper.xml) to allow for color customization. */
public class SwiperThemeGenerator {
    /**
     * @param args Specify output file path as the first argument
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        String outFilePath = args[0];
        ColorMatcher colorMatcher = new ColorMatcher();

        Set<Integer> seen = new HashSet<>();
        try (OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(outFilePath))) {

            for (int r = 0; r <= 0xff; r++) {
                for (int g = 0; g <= 0xff; g++) {
                    for (int b = 0; b <= 0xff; b++) {
                        int color = Color.argb(0xff, r, g, b);
                        int candidate = colorMatcher.findClosestColor(color);
                        if (seen.add(candidate)) {
                            String colorHex = String.format("%02x%02x%02x", Color.red(candidate),
                                    Color.green(candidate), Color.blue(candidate));
                            out.append("<style name=\"Swiper")
                                    .append(colorHex)
                                    .append("\" parent=\"Provisioning2Theme\">")
                                    .append("<item name=\"swiper_color\">#")
                                    .append(colorHex)
                                    .append("</item></style>\n");
                        }
                    }
                }
            }
        }
    }
}