/*
* Copyright (C) 2011 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
#ifndef _GL_SHARED_GROUP_H_
#define _GL_SHARED_GROUP_H_

#define GL_API
#ifndef ANDROID
#define GL_APIENTRY
#define GL_APIENTRYP
#endif

#include "TextureSharedData.h"

#include <GLES/gl.h>
#include <GLES/glext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

#include <map>
#include <string>
#include <vector>

#include <stdio.h>
#include <stdlib.h>
#include "ErrorLog.h"
#include <utils/KeyedVector.h>
#include <utils/List.h>
#include <utils/String8.h>
#include <utils/threads.h>
#include "FixedBuffer.h"
#include "IndexRangeCache.h"
#include "SmartPtr.h"

struct BufferData {
    BufferData();
    BufferData(GLsizeiptr size, void * data);

    // General buffer state
    GLsizeiptr m_size;
    GLenum m_usage;

    // Mapped buffer state
    bool m_mapped;
    GLbitfield m_mappedAccess;
    GLintptr m_mappedOffset;
    GLsizeiptr m_mappedLength;

    // Internal bookkeeping
    FixedBuffer m_fixedBuffer; // actual buffer is shadowed here
    IndexRangeCache m_indexRangeCache;
};

class ProgramData {
private:
    typedef struct _IndexInfo {
        GLint base;
        GLint size;
        GLenum type;
        GLint appBase;
        GLint hostLocsPerElement;
        GLuint flags;
        GLint samplerValue; // only set for sampler uniforms
    } IndexInfo;

    GLuint m_numIndexes;
    IndexInfo* m_Indexes;
    bool m_initialized;
    bool m_locShiftWAR;

    android::Vector<GLuint> m_shaders;

public:
    enum {
        INDEX_FLAG_SAMPLER_EXTERNAL = 0x00000001,
    };

    ProgramData();
    void initProgramData(GLuint numIndexes);
    bool isInitialized();
    virtual ~ProgramData();
    void setIndexInfo(GLuint index, GLint base, GLint size, GLenum type);
    void setIndexFlags(GLuint index, GLuint flags);
    GLuint getIndexForLocation(GLint location);
    GLenum getTypeForLocation(GLint location);

    bool needUniformLocationWAR() const { return m_locShiftWAR; }
    void setupLocationShiftWAR();
    GLint locationWARHostToApp(GLint hostLoc, GLint arrIndex);
    GLint locationWARAppToHost(GLint appLoc);

    GLint getNextSamplerUniform(GLint index, GLint* val, GLenum* target);
    bool setSamplerUniform(GLint appLoc, GLint val, GLenum* target);

    bool attachShader(GLuint shader);
    bool detachShader(GLuint shader);
    size_t getNumShaders() const { return m_shaders.size(); }
    GLuint getShader(size_t i) const { return m_shaders[i]; }
};

struct ShaderData {
    typedef android::List<android::String8> StringList;
    StringList samplerExternalNames;
    int refcount;
    std::vector<std::string> sources;
};

class ShaderProgramData {
public:
    ShaderProgramData() {
        shaderData = new ShaderData();
        programData = new ProgramData();
    }
    ~ShaderProgramData() {
        delete shaderData;
        delete programData;
    }
    ShaderData* shaderData;
    ProgramData* programData;
};

class GLSharedGroup {
private:
    SharedTextureDataMap m_textureRecs;
    android::DefaultKeyedVector<GLuint, BufferData*> m_buffers;
    android::DefaultKeyedVector<GLuint, ProgramData*> m_programs;
    android::DefaultKeyedVector<GLuint, ShaderData*> m_shaders;
    android::DefaultKeyedVector<uint32_t, ShaderProgramData*> m_shaderPrograms;
    std::map<GLuint, uint32_t> m_shaderProgramIdMap;

    mutable android::Mutex m_lock;

    void refShaderDataLocked(ssize_t shaderIdx);
    void unrefShaderDataLocked(ssize_t shaderIdx);

    uint32_t m_shaderProgramId;

public:
    GLSharedGroup();
    ~GLSharedGroup();
    bool isShaderOrProgramObject(GLuint obj);
    BufferData * getBufferData(GLuint bufferId);
    SharedTextureDataMap* getTextureData();
    void    addBufferData(GLuint bufferId, GLsizeiptr size, void * data);
    void    updateBufferData(GLuint bufferId, GLsizeiptr size, void * data);
    void    setBufferUsage(GLuint bufferId, GLenum usage);
    void    setBufferMapped(GLuint bufferId, bool mapped);
    GLenum    getBufferUsage(GLuint bufferId);
    bool    isBufferMapped(GLuint bufferId);
    GLenum  subUpdateBufferData(GLuint bufferId, GLintptr offset, GLsizeiptr size, void * data);
    void    deleteBufferData(GLuint);

    bool    isProgram(GLuint program);
    bool    isProgramInitialized(GLuint program);
    void    addProgramData(GLuint program); 
    void    initProgramData(GLuint program, GLuint numIndexes);
    void    attachShader(GLuint program, GLuint shader);
    void    detachShader(GLuint program, GLuint shader);
    void    deleteProgramData(GLuint program);
    void    setProgramIndexInfo(GLuint program, GLuint index, GLint base, GLint size, GLenum type, const char* name);
    GLenum  getProgramUniformType(GLuint program, GLint location);
    void    setupLocationShiftWAR(GLuint program);
    GLint   locationWARHostToApp(GLuint program, GLint hostLoc, GLint arrIndex);
    GLint   locationWARAppToHost(GLuint program, GLint appLoc);
    bool    needUniformLocationWAR(GLuint program);
    GLint   getNextSamplerUniform(GLuint program, GLint index, GLint* val, GLenum* target) const;
    bool    setSamplerUniform(GLuint program, GLint appLoc, GLint val, GLenum* target);

    bool    isShader(GLuint shader);
    bool    addShaderData(GLuint shader);
    // caller must hold a reference to the shader as long as it holds the pointer
    ShaderData* getShaderData(GLuint shader);
    void    unrefShaderData(GLuint shader);

    // For separable shader programs.
    uint32_t addNewShaderProgramData();
    void associateGLShaderProgram(GLuint shaderProgramName, uint32_t shaderProgramId);
    ShaderProgramData* getShaderProgramDataById(uint32_t id);
    ShaderProgramData* getShaderProgramData(GLuint shaderProgramName);
    void deleteShaderProgramDataById(uint32_t id);
    void deleteShaderProgramData(GLuint shaderProgramName);
    void initShaderProgramData(GLuint shaderProgram, GLuint numIndices);
    void setShaderProgramIndexInfo(GLuint shaderProgram, GLuint index, GLint base, GLint size, GLenum type, const char* name);
    void setupShaderProgramLocationShiftWAR(GLuint shaderProgram);
};

typedef SmartPtr<GLSharedGroup> GLSharedGroupPtr; 

#endif //_GL_SHARED_GROUP_H_
