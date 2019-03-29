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

#ifndef SHADERTOY_SHADER_H
#define SHADERTOY_SHADER_H

#include <GLES3/gl31.h>

#include <fstream>
#include <sstream>

struct InputTextures {
  GLuint id;
  GLint uniform_location;
  int width;
  int height;
};

class ShadertoyShader {
 public:
  ShadertoyShader();
  ~ShadertoyShader();

  void CreateShaderFromString(const std::string& shader_string);
  void PrepareForDraw(int width, int height, float global_time, int frame, float time_delta);

 private:
   void CreateShader(const std::string fragment_string);
   void GetUniformLocations();

   GLint uiResolution_;
   GLint uiGlobalTime_;
   GLint uiFrame_;
   GLint uiTimeDelta_;
   GLint uiChannel0_;
   GLint unViewport_;
   GLint unCorners_;

   GLuint shader_program_;

   struct InputTextures input_textures_[4];
};



#endif // SHADERTOY_SHADER_H
