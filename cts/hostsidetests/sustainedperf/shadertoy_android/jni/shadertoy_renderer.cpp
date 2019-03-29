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

#include <fstream>
#include <sstream>
#include <android/log.h>

int g_framebuffer_width = 0;
int g_framebuffer_height = 0;
GLuint g_quad_vao = 0;

ShadertoyShader shader;

#define  LOG_TAG    "GPUStressTestActivity"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

double NowInMs() {
  timespec timeval;
  clock_gettime(CLOCK_REALTIME, &timeval);
  double time = 1000.0 * timeval.tv_sec + (double) timeval.tv_nsec / 1e6;
  return time;
}


GLuint CreateFullscreenQuad() {
  GLfloat quadVertices[] = {
    // Positions
    -1.0f, 1.0f,
    -1.0f, -1.0f,
    1.0f, -1.0f,

    -1.0f, 1.0f,
    1.0f, -1.0f,
    1.0f, 1.0f,
  };

  // Setup screen VAO
  GLuint quadVAO, quadVBO;
  glGenVertexArrays(1, &quadVAO);
  glGenBuffers(1, &quadVBO);
  glBindVertexArray(quadVAO);
  glBindBuffer(GL_ARRAY_BUFFER, quadVBO);
  glBufferData(GL_ARRAY_BUFFER, sizeof(quadVertices), &quadVertices, GL_STATIC_DRAW);
  glEnableVertexAttribArray(0);
  glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 2 * sizeof(GLfloat), (GLvoid*)0);
  glBindVertexArray(0);

  return quadVAO;
}

void CreateShader() {
  extern std::string g_shader;
  shader.CreateShaderFromString(g_shader);
}

void Init(int width, int height) {
  GLint num_extensions = 0;
  glGetIntegerv(GL_NUM_EXTENSIONS, &num_extensions);
  for (GLint i = 0; i < num_extensions; ++i) {
    const char* extension = (char*)(
      glGetStringi(GL_EXTENSIONS, i));
  }

  g_framebuffer_width = width;
  g_framebuffer_height = height;


  CreateShader();
  g_quad_vao = CreateFullscreenQuad();
}

void DrawFrame() {
  static double previous_time = 0;
  static float angle = 0.0f;
  static double elapsed_time_sum = 0;
  static double gpu_timer_elapsed_sum = 0;
  static double start_time = NowInMs();

  // After how many frames to report the avg frame time.
  int kFrameReportInterval = 1;
  static int frame_count = 0;

  frame_count++;
  if (frame_count == kFrameReportInterval) {
    LOGI("%f\n", elapsed_time_sum / (double)kFrameReportInterval);

    frame_count = 0;
    elapsed_time_sum = 0;
    gpu_timer_elapsed_sum = 0;
  }

  double current_time = NowInMs();
  double elapsed_time = current_time - previous_time;
  previous_time = current_time;
  elapsed_time_sum += elapsed_time;
  float global_time = (float)(NowInMs() - start_time);

  glViewport(0, 0, g_framebuffer_width, g_framebuffer_height);

  glClearColor(0.2f, 0.3f, 0.3f, 1.0f);
  glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

  float fov = 45;

  shader.PrepareForDraw(g_framebuffer_width, g_framebuffer_height, global_time, frame_count, (float)elapsed_time);

  glBindVertexArray(g_quad_vao);

  glDrawArrays(GL_TRIANGLES, 0, 6);
}

void Cleanup() {

}
