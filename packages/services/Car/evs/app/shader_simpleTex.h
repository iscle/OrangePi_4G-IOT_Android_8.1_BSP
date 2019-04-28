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

#ifndef SHADER_SIMPLE_TEX_H
#define SHADER_SIMPLE_TEX_H

const char vtxShader_simpleTexture[] = ""
        "#version 300 es                    \n"
        "layout(location = 0) in vec4 pos;  \n"
        "layout(location = 1) in vec2 tex;  \n"
        "uniform mat4 cameraMat;            \n"
        "out vec2 uv;                       \n"
        "void main()                        \n"
        "{                                  \n"
        "   gl_Position = cameraMat * pos;  \n"
        "   uv = tex;                       \n"
        "}                                  \n";

const char pixShader_simpleTexture[] =
        "#version 300 es                            \n"
        "precision mediump float;                   \n"
        "uniform sampler2D tex;                     \n"
        "in vec2 uv;                                \n"
        "out vec4 color;                            \n"
        "void main()                                \n"
        "{                                          \n"
        "    vec4 texel = texture(tex, uv);         \n"
        "    color = texel;                         \n"
        "}                                          \n";

#endif // SHADER_SIMPLE_TEX_H