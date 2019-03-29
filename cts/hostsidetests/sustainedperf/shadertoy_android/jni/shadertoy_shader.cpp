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

#include "shadertoy_shader.h"
#include "utils.h"

#define _USE_MATH_DEFINES
#include <math.h>

namespace {
bool CompileShader10(GLuint shader, const std::string& shader_string) {
  std::string prefix = "#version 100\n";
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
};  // namespace


ShadertoyShader::ShadertoyShader() :
 uiResolution_(-1), uiGlobalTime_(-1), uiFrame_(-1), uiTimeDelta_(-1),
 uiChannel0_(-1), unViewport_(-1), unCorners_(-1), shader_program_(0) {

  GLuint tex_ids[4];
  glGenTextures(4, &tex_ids[0]);
  for (int ii = 0; ii < 4; ii++) {
    input_textures_[ii].width = 0;
    input_textures_[ii].height = 0;
    input_textures_[ii].id = tex_ids[ii];
  }
}

ShadertoyShader::~ShadertoyShader() {
  GLuint ids[] = { input_textures_[0].id, input_textures_[1].id, input_textures_[2].id, input_textures_[3].id, };
  glDeleteTextures(4, ids);
}

void ShadertoyShader::CreateShaderFromString(const std::string& shader_string) {
  CreateShader(shader_string);
}

void ShadertoyShader::GetUniformLocations() {
  glUseProgram(shader_program_);
  uiResolution_ = glGetUniformLocation(shader_program_, "iResolution");
  uiGlobalTime_ = glGetUniformLocation(shader_program_, "iGlobalTime");
  uiFrame_ = glGetUniformLocation(shader_program_, "iFrame");
  uiTimeDelta_ = glGetUniformLocation(shader_program_, "iTimeDelta");
  uiChannel0_ = glGetUniformLocation(shader_program_, "iChannel0");

  if (uiChannel0_ != -1)
    glUniform1i(uiChannel0_, 0);

  unViewport_ = glGetUniformLocation(shader_program_, "unViewport");
  unCorners_ = glGetUniformLocation(shader_program_, "unCorners");

  glUseProgram(0);
}

void ShadertoyShader::CreateShader(const std::string fragment_string) {
  std::string vertex_string = SHADER0([]() {
    attribute vec2 pos;
    void main() {
      gl_Position = vec4(pos.xy, 0.0, 1.0);
    }
  });

  std::string shader_toy_fragment_header = SHADER0([]() {
    precision highp float;
    uniform vec3 iResolution;
    uniform float iGlobalTime;
    uniform vec4 iMouse;
    uniform int iFrame;
    uniform float iTimeDelta;
    uniform vec3 iChannelResolution[4];
    uniform sampler2D iChannel0;
    vec4 texture2DGrad(sampler2D s, in vec2 uv, vec2 gx, vec2 gy) { return texture2D(s, uv); }
    vec4 texture2DLod(sampler2D s, in vec2 uv, in float lod) { return texture2D(s, uv); }
    void mainImage(out vec4 c, in vec2 f);
  });

  std::string shader_toy_fragment_footer = SHADER0([]() {
    void main(void) {
      vec4 shader_color = vec4(0, 0, 0, 1);
      mainImage(shader_color, gl_FragCoord.xy);
      shader_color.w = 1.0;
      gl_FragColor = shader_color;
    }
  });

  std::string complete_fragment_string = shader_toy_fragment_header + fragment_string + shader_toy_fragment_footer;

  GLuint vertex_shader = glCreateShader(GL_VERTEX_SHADER);
  CompileShader10(vertex_shader, vertex_string);

  GLuint fragment_shader = glCreateShader(GL_FRAGMENT_SHADER);
  CompileShader10(fragment_shader, complete_fragment_string);

  // Link shaders
  shader_program_ = glCreateProgram();
  LinkProgram(shader_program_, vertex_shader, fragment_shader);

  GetUniformLocations();

  glDeleteShader(vertex_shader);
  glDeleteShader(fragment_shader);
}

void ShadertoyShader::PrepareForDraw(int width, int height, float global_time, int frame, float time_delta) {
  glUseProgram(shader_program_);

  // Set the uniforms
  if (uiResolution_ != -1)
    glUniform3f(uiResolution_, (float)width, (float)height, 1);
  if (uiGlobalTime_ != -1)
    glUniform1f(uiGlobalTime_, global_time);
  if (uiFrame_ != -1)
    glUniform1f(uiFrame_, (float)frame);
  if (uiTimeDelta_ != -1)
    glUniform1f(uiTimeDelta_, time_delta);

  glActiveTexture(GL_TEXTURE0);
  glBindTexture(GL_TEXTURE_2D, input_textures_[0].id);
  glActiveTexture(GL_TEXTURE1);
  glBindTexture(GL_TEXTURE_2D, input_textures_[1].id);
  glActiveTexture(GL_TEXTURE2);
  glBindTexture(GL_TEXTURE_2D, input_textures_[2].id);
  glActiveTexture(GL_TEXTURE3);
  glBindTexture(GL_TEXTURE_2D, input_textures_[3].id);

  if (unViewport_ != -1)
    glUniform4f(unViewport_, 0, 0, (float)width, (float)height);
}
