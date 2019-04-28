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
#include "shader.h"

#include <stdio.h>
#include <memory>


// Given shader source, load and compile it
static GLuint loadShader(GLenum type, const char *shaderSrc, const char *name) {
    // Create the shader object
    GLuint shader = glCreateShader (type);
    if (shader == 0) {
        return 0;
    }

    // Load and compile the shader
    glShaderSource(shader, 1, &shaderSrc, nullptr);
    glCompileShader(shader);

    // Verify the compilation worked as expected
    GLint compiled = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (!compiled) {
        printf("Error compiling %s shader for %s\n", (type==GL_VERTEX_SHADER) ? "vtx":"pxl", name);

        GLint size = 0;
        glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &size);
        if (size > 0)
        {
            // Get and report the error message
            std::unique_ptr<char> infoLog(new char[size]);
            glGetShaderInfoLog(shader, size, NULL, infoLog.get());
            printf("  msg:\n%s\n", infoLog.get());
        }

        glDeleteShader(shader);
        return 0;
    }

    return shader;
}


// Create a program object given vertex and pixels shader source
GLuint buildShaderProgram(const char* vtxSrc, const char* pxlSrc, const char* name) {
    GLuint program = glCreateProgram();
    if (program == 0) {
        printf("Failed to allocate program object\n");
        return 0;
    }

    // Compile the shaders and bind them to this program
    GLuint vertexShader = loadShader(GL_VERTEX_SHADER, vtxSrc, name);
    if (vertexShader == 0) {
        printf("Failed to load vertex shader\n");
        glDeleteProgram(program);
        return 0;
    }
    GLuint pixelShader = loadShader(GL_FRAGMENT_SHADER, pxlSrc, name);
    if (pixelShader == 0) {
        printf("Failed to load pixel shader\n");
        glDeleteProgram(program);
        glDeleteShader(vertexShader);
        return 0;
    }
    glAttachShader(program, vertexShader);
    glAttachShader(program, pixelShader);

    // Link the program
    glLinkProgram(program);
    GLint linked = 0;
    glGetProgramiv(program, GL_LINK_STATUS, &linked);
    if (!linked)
    {
        printf("Error linking program.\n");
        GLint size = 0;
        glGetProgramiv(program, GL_INFO_LOG_LENGTH, &size);
        if (size > 0)
        {
            // Get and report the error message
            std::unique_ptr<char> infoLog(new char[size]);
            glGetProgramInfoLog(program, size, NULL, infoLog.get());
            printf("  msg:  %s\n", infoLog.get());
        }

        glDeleteProgram(program);
        glDeleteShader(vertexShader);
        glDeleteShader(pixelShader);
        return 0;
    }


#if 0 // Debug output to diagnose shader parameters
    GLint numShaderParams;
    GLchar paramName[128];
    GLint paramSize;
    GLenum paramType;
    const char *typeName = "?";
    printf("Shader parameters for %s:\n", name);
    glGetProgramiv(program, GL_ACTIVE_UNIFORMS, &numShaderParams);
    for (GLint i=0; i<numShaderParams; i++) {
        glGetActiveUniform(program,
                           i,
                           sizeof(paramName),
                           nullptr,
                           &paramSize,
                           &paramType,
                           paramName);
        switch (paramType) {
            case GL_FLOAT:      typeName = "GL_FLOAT"; break;
            case GL_FLOAT_VEC4: typeName = "GL_FLOAT_VEC4"; break;
            case GL_FLOAT_MAT4: typeName = "GL_FLOAT_MAT4"; break;
            case GL_SAMPLER_2D: typeName = "GL_SAMPLER_2D"; break;
        }

        printf("  %2d: %s\t (%d) of type %s(%d)\n", i, paramName, paramSize, typeName, paramType);
    }
#endif


    return program;
}
