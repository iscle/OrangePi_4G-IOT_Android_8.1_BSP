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

#define _USE_MATH_DEFINES
#include <math.h>
#include "utils.h"

float DegToRad(float deg) {
  return deg * (float)M_PI / 180.0f;
}

bool CompileShader(GLuint shader, const std::string& shader_string) {
  std::string prefix = "#version 300 es\n";
  std::string string_with_prefix = prefix + shader_string;
  const char* shader_str[] = { string_with_prefix.data() };
  glShaderSource(shader, 1, shader_str, NULL);
  glCompileShader(shader);

  GLint success;
  GLchar infoLog[512];
  glGetShaderiv(shader, GL_COMPILE_STATUS, &success);
  if (!success)
  {
    glGetShaderInfoLog(shader, 512, NULL, infoLog);
    LOGI("Shader Failed to compile: %s -- %s\n", *shader_str, infoLog);
    return false;
  }
  return true;
}

bool LinkProgram(GLuint program, GLuint vertex_shader, GLuint fragment_shader) {
  glAttachShader(program, vertex_shader);
  glAttachShader(program, fragment_shader);
  glLinkProgram(program);

  // Check for linking errors
  GLint success;
  GLchar infoLog[512];
  glGetProgramiv(program, GL_LINK_STATUS, &success);
  if (!success) {
    glGetProgramInfoLog(program, 512, NULL, infoLog);
    LOGE("Shader failed to link: %s\n", infoLog);
    return false;
  }

  return true;
}

GLenum GLCheckError() {
  return GLCheckErrorStr("");
}

GLenum GLCheckErrorStr(std::string msg) {
  GLenum e = glGetError();
  std::string str;
  if (e != GL_NO_ERROR) {
    switch (e) {
    case GL_INVALID_ENUM:
      str = "GL_INVALID_ENUM";
      break;
    case GL_INVALID_OPERATION:
      str = "GL_INVALID_OPERATION";
      break;
    case GL_INVALID_VALUE:
      str = "GL_INVALID_VALUE";
      break;
    case GL_OUT_OF_MEMORY:
      str = "GL_OUT_OF_MEMORY";
      break;
    case GL_INVALID_FRAMEBUFFER_OPERATION:
      str = "GL_INVALID_FRAMEBUFFER_OPERATION";
      break;
    }
    LOGE("OpenGL error : %s : %s (%#08x)\n", msg.c_str(), str.c_str(), e);
  }
  return e;
}
