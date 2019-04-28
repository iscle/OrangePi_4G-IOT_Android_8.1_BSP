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
#ifndef TEXWRAPPER_H
#define TEXWRAPPER_H

#include <GLES2/gl2.h>


class TexWrapper {
public:
    TexWrapper(GLuint textureId, unsigned width, unsigned height);
    virtual ~TexWrapper();

    GLuint glId()       { return id; };
    unsigned width()    { return w; };
    unsigned height()   { return h; };

protected:
    TexWrapper();

    GLuint id;
    unsigned w;
    unsigned h;
};


TexWrapper* createTextureFromPng(const char* filename);

#endif // TEXWRAPPER_H