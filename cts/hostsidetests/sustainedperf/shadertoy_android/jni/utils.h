/*
 * Copyright (C) 2016 The Android Open Source Project
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

#ifndef COMMON_UTILS_H
#define COMMON_UTILS_H

#include <math.h>
#include <string>

#include <android/log.h>

#include <GLES3/gl31.h>
#include <GLES3/gl3ext.h>

#define LOG_TAG "GPUStressTestActivity"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

inline void PrintGLError(...) {
  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG, "GL_ERROR");
}

#ifdef LOG_GL_ERRORS
#define GL_CALL(call)                      \
  {                                        \
    call;                                  \
    if (glGetError() != GL_NO_ERROR) {     \
      PrintGLError();                      \
    }                                      \
  }
#else
#define GL_CALL(call)                      \
  {                                        \
    call;                                  \
  }
#endif

template <size_t size>
std::string StripLambda(const char(&shader)[size]) {
  return std::string(shader + 6, shader + size - 2);
}

#define SHADER0(Src) StripLambda(#Src)

bool CompileShader(GLuint shader, const std::string& shader_string);
bool LinkProgram(GLuint program, GLuint vertex_shader, GLuint fragment_shader);

// Converts from degrees to radians.
float DegToRad(float deg);

GLenum GLCheckError();
GLenum GLCheckErrorStr(std::string str);

#endif // COMMON_UTILS_H
