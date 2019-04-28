/*
 * Copyright (C) 2017 The Android Open Source Project
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

#ifndef SHADER_PROJECTED_TEX_H
#define SHADER_PROJECTED_TEX_H

// This shader is used to project a sensors image onto wold space geometry
// as if it were projected from the original sensor's point of view in the world.

const char vtxShader_projectedTexture[] = ""
        "#version 300 es                            \n"
        "layout(location = 0) in vec4 pos;          \n"
        "uniform mat4 cameraMat;                    \n"
        "uniform mat4 projectionMat;                \n"
        "out vec4 projectionSpace;                  \n"
        "void main()                                \n"
        "{                                          \n"
        "   gl_Position = cameraMat * pos;          \n"
        "   projectionSpace = projectionMat * pos;  \n"
        "}                                          \n";

const char pixShader_projectedTexture[] =
        "#version 300 es                                        \n"
        "precision mediump float;                               \n"
        "uniform sampler2D tex;                                 \n"
        "in vec4 projectionSpace;                               \n"
        "out vec4 color;                                        \n"
        "void main()                                            \n"
        "{                                                      \n"
        "    const vec2 zero = vec2(0.0f, 0.0f);                \n"
        "    const vec2 one  = vec2(1.0f, 1.0f);                \n"
        "                                                       \n"
        "    // Compute perspective correct texture coordinates \n"
        "    // in the sensor map                               \n"
        "    vec2 cs = projectionSpace.xy / projectionSpace.w;  \n"
        "                                                       \n"
        "    // flip the texture!                               \n"
        "    cs.y = -cs.y;                                      \n"
        "                                                       \n"
        "    // scale from -1/1 clip space to 0/1 uv space      \n"
        "    vec2 uv = (cs + 1.0f) * 0.5f;                      \n"
        "                                                       \n"
        "    // Bail if we don't have a valid projection        \n"
        "    if ((projectionSpace.w <= 0.0f) ||                 \n"
        "        any(greaterThan(uv, one)) ||                   \n"
        "        any(lessThan(uv, zero))) {                     \n"
        "        discard;                                       \n"
        "    }                                                  \n"
        "    color = texture(tex, uv);                          \n"
        "}                                                      \n";

#endif // SHADER_PROJECTED_TEX_H