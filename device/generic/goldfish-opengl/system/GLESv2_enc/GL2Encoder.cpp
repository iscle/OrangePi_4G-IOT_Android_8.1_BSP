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

#include "GL2Encoder.h"
#include "GLESv2Validation.h"

#include <string>
#include <map>

#include <assert.h>
#include <ctype.h>

#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <GLES2/gl2platform.h>

#include <GLES3/gl3.h>
#include <GLES3/gl31.h>

#ifndef MIN
#define MIN(a, b) ((a) < (b) ? (a) : (b))
#endif

static GLubyte *gVendorString= (GLubyte *) "Android";
static GLubyte *gRendererString= (GLubyte *) "Android HW-GLES 3.0";
static GLubyte *gVersionString= (GLubyte *) "OpenGL ES 3.0";
static GLubyte *gExtensionsString= (GLubyte *) "GL_OES_EGL_image_external ";

#define SET_ERROR_IF(condition, err) if((condition)) { \
        ALOGE("%s:%s:%d GL error 0x%x\n", __FILE__, __FUNCTION__, __LINE__, err); \
        ctx->setError(err); \
        return; \
    }

#define SET_ERROR_WITH_MESSAGE_IF(condition, err, generator, genargs) if ((condition)) { \
        std::string msg = generator genargs; \
        ALOGE("%s:%s:%d GL error 0x%x\n" \
              "Info: %s\n", __FILE__, __FUNCTION__, __LINE__, err, msg.c_str()); \
        ctx->setError(err); \
        return; \
    } \

#define RET_AND_SET_ERROR_IF(condition, err, ret) if((condition)) { \
        ALOGE("%s:%s:%d GL error 0x%x\n", __FILE__, __FUNCTION__, __LINE__, err); \
        ctx->setError(err);  \
        return ret; \
    } \

#define RET_AND_SET_ERROR_WITH_MESSAGE_IF(condition, err, ret, generator, genargs) if((condition)) { \
        std::string msg = generator genargs; \
        ALOGE("%s:%s:%d GL error 0x%x\n" \
              "Info: %s\n", __FILE__, __FUNCTION__, __LINE__, err, msg.c_str()); \
        ctx->setError(err);   \
        return ret; \
    } \

GL2Encoder::GL2Encoder(IOStream *stream, ChecksumCalculator *protocol)
        : gl2_encoder_context_t(stream, protocol)
{
    m_currMajorVersion = 2;
    m_currMinorVersion = 0;
    m_initialized = false;
    m_state = NULL;
    m_error = GL_NO_ERROR;
    m_num_compressedTextureFormats = 0;
    m_max_cubeMapTextureSize = 0;
    m_max_renderBufferSize = 0;
    m_max_textureSize = 0;
    m_max_3d_textureSize = 0;
    m_max_vertexAttribStride = 0;
    m_compressedTextureFormats = NULL;

    m_ssbo_offset_align = 0;
    m_ubo_offset_align = 0;

    m_drawCallFlushCount = 0;
    m_primitiveRestartEnabled = false;
    m_primitiveRestartIndex = 0;

    // overrides
#define OVERRIDE(name)  m_##name##_enc = this-> name ; this-> name = &s_##name
#define OVERRIDE_CUSTOM(name)  this-> name = &s_##name
#define OVERRIDEWITH(name, target)  do { \
    m_##target##_enc = this-> target; \
    this-> target = &s_##name; \
} while(0)
#define OVERRIDEOES(name) OVERRIDEWITH(name, name##OES)

    OVERRIDE(glFlush);
    OVERRIDE(glPixelStorei);
    OVERRIDE(glGetString);
    OVERRIDE(glBindBuffer);
    OVERRIDE(glBufferData);
    OVERRIDE(glBufferSubData);
    OVERRIDE(glDeleteBuffers);
    OVERRIDE(glDrawArrays);
    OVERRIDE(glDrawElements);
    OVERRIDE(glGetIntegerv);
    OVERRIDE(glGetFloatv);
    OVERRIDE(glGetBooleanv);
    OVERRIDE(glVertexAttribPointer);
    OVERRIDE(glEnableVertexAttribArray);
    OVERRIDE(glDisableVertexAttribArray);
    OVERRIDE(glGetVertexAttribiv);
    OVERRIDE(glGetVertexAttribfv);
    OVERRIDE(glGetVertexAttribPointerv);

    this->glShaderBinary = &s_glShaderBinary;
    this->glShaderSource = &s_glShaderSource;
    this->glFinish = &s_glFinish;

    OVERRIDE(glGetError);
    OVERRIDE(glLinkProgram);
    OVERRIDE(glDeleteProgram);
    OVERRIDE(glGetUniformiv);
    OVERRIDE(glGetUniformfv);
    OVERRIDE(glCreateProgram);
    OVERRIDE(glCreateShader);
    OVERRIDE(glDeleteShader);
    OVERRIDE(glAttachShader);
    OVERRIDE(glDetachShader);
    OVERRIDE(glGetAttachedShaders);
    OVERRIDE(glGetShaderSource);
    OVERRIDE(glGetShaderInfoLog);
    OVERRIDE(glGetProgramInfoLog);

    OVERRIDE(glGetUniformLocation);
    OVERRIDE(glUseProgram);

    OVERRIDE(glUniform1f);
    OVERRIDE(glUniform1fv);
    OVERRIDE(glUniform1i);
    OVERRIDE(glUniform1iv);
    OVERRIDE(glUniform2f);
    OVERRIDE(glUniform2fv);
    OVERRIDE(glUniform2i);
    OVERRIDE(glUniform2iv);
    OVERRIDE(glUniform3f);
    OVERRIDE(glUniform3fv);
    OVERRIDE(glUniform3i);
    OVERRIDE(glUniform3iv);
    OVERRIDE(glUniform4f);
    OVERRIDE(glUniform4fv);
    OVERRIDE(glUniform4i);
    OVERRIDE(glUniform4iv);
    OVERRIDE(glUniformMatrix2fv);
    OVERRIDE(glUniformMatrix3fv);
    OVERRIDE(glUniformMatrix4fv);

    OVERRIDE(glActiveTexture);
    OVERRIDE(glBindTexture);
    OVERRIDE(glDeleteTextures);
    OVERRIDE(glGetTexParameterfv);
    OVERRIDE(glGetTexParameteriv);
    OVERRIDE(glTexParameterf);
    OVERRIDE(glTexParameterfv);
    OVERRIDE(glTexParameteri);
    OVERRIDE(glTexParameteriv);
    OVERRIDE(glTexImage2D);
    OVERRIDE(glTexSubImage2D);
    OVERRIDE(glCopyTexImage2D);

    OVERRIDE(glGenRenderbuffers);
    OVERRIDE(glDeleteRenderbuffers);
    OVERRIDE(glBindRenderbuffer);
    OVERRIDE(glRenderbufferStorage);
    OVERRIDE(glFramebufferRenderbuffer);

    OVERRIDE(glGenFramebuffers);
    OVERRIDE(glDeleteFramebuffers);
    OVERRIDE(glBindFramebuffer);
    OVERRIDE(glFramebufferTexture2D);
    OVERRIDE(glFramebufferTexture3DOES);
    OVERRIDE(glGetFramebufferAttachmentParameteriv);

    OVERRIDE(glCheckFramebufferStatus);

    OVERRIDE(glGenVertexArrays);
    OVERRIDE(glDeleteVertexArrays);
    OVERRIDE(glBindVertexArray);
    OVERRIDEOES(glGenVertexArrays);
    OVERRIDEOES(glDeleteVertexArrays);
    OVERRIDEOES(glBindVertexArray);

    OVERRIDE_CUSTOM(glMapBufferRange);
    OVERRIDE_CUSTOM(glUnmapBuffer);
    OVERRIDE_CUSTOM(glFlushMappedBufferRange);

    OVERRIDE(glCompressedTexImage2D);
    OVERRIDE(glCompressedTexSubImage2D);

    OVERRIDE(glBindBufferRange);
    OVERRIDE(glBindBufferBase);

    OVERRIDE(glCopyBufferSubData);

    OVERRIDE(glGetBufferParameteriv);
    OVERRIDE(glGetBufferParameteri64v);
    OVERRIDE(glGetBufferPointerv);

    OVERRIDE_CUSTOM(glGetUniformIndices);

    OVERRIDE(glUniform1ui);
    OVERRIDE(glUniform2ui);
    OVERRIDE(glUniform3ui);
    OVERRIDE(glUniform4ui);
    OVERRIDE(glUniform1uiv);
    OVERRIDE(glUniform2uiv);
    OVERRIDE(glUniform3uiv);
    OVERRIDE(glUniform4uiv);
    OVERRIDE(glUniformMatrix2x3fv);
    OVERRIDE(glUniformMatrix3x2fv);
    OVERRIDE(glUniformMatrix2x4fv);
    OVERRIDE(glUniformMatrix4x2fv);
    OVERRIDE(glUniformMatrix3x4fv);
    OVERRIDE(glUniformMatrix4x3fv);

    OVERRIDE(glGetUniformuiv);
    OVERRIDE(glGetActiveUniformBlockiv);

    OVERRIDE(glGetVertexAttribIiv);
    OVERRIDE(glGetVertexAttribIuiv);

    OVERRIDE_CUSTOM(glVertexAttribIPointer);

    OVERRIDE(glVertexAttribDivisor);

    OVERRIDE(glRenderbufferStorageMultisample);
    OVERRIDE(glDrawBuffers);
    OVERRIDE(glReadBuffer);
    OVERRIDE(glFramebufferTextureLayer);
    OVERRIDE(glTexStorage2D);

    OVERRIDE_CUSTOM(glTransformFeedbackVaryings);
    OVERRIDE(glBeginTransformFeedback);
    OVERRIDE(glEndTransformFeedback);
    OVERRIDE(glPauseTransformFeedback);
    OVERRIDE(glResumeTransformFeedback);

    OVERRIDE(glTexImage3D);
    OVERRIDE(glTexSubImage3D);
    OVERRIDE(glTexStorage3D);
    OVERRIDE(glCompressedTexImage3D);
    OVERRIDE(glCompressedTexSubImage3D);

    OVERRIDE(glDrawArraysInstanced);
    OVERRIDE_CUSTOM(glDrawElementsInstanced);
    OVERRIDE_CUSTOM(glDrawRangeElements);

    OVERRIDE_CUSTOM(glGetStringi);
    OVERRIDE(glGetProgramBinary);
    OVERRIDE(glReadPixels);

    OVERRIDE(glEnable);
    OVERRIDE(glDisable);
    OVERRIDE(glClearBufferiv);
    OVERRIDE(glClearBufferuiv);
    OVERRIDE(glClearBufferfv);
    OVERRIDE(glBlitFramebuffer);
    OVERRIDE_CUSTOM(glGetInternalformativ);

    OVERRIDE(glGenerateMipmap);

    OVERRIDE(glBindSampler);

    OVERRIDE_CUSTOM(glFenceSync);
    OVERRIDE_CUSTOM(glClientWaitSync);
    OVERRIDE_CUSTOM(glWaitSync);
    OVERRIDE_CUSTOM(glDeleteSync);
    OVERRIDE_CUSTOM(glIsSync);
    OVERRIDE_CUSTOM(glGetSynciv);

    OVERRIDE(glGetIntegeri_v);
    OVERRIDE(glGetInteger64i_v);
    OVERRIDE(glGetInteger64v);
    OVERRIDE(glGetBooleani_v);

    OVERRIDE(glGetShaderiv);

    OVERRIDE(glActiveShaderProgram);
    OVERRIDE_CUSTOM(glCreateShaderProgramv);
    OVERRIDE(glProgramUniform1f);
    OVERRIDE(glProgramUniform1fv);
    OVERRIDE(glProgramUniform1i);
    OVERRIDE(glProgramUniform1iv);
    OVERRIDE(glProgramUniform1ui);
    OVERRIDE(glProgramUniform1uiv);
    OVERRIDE(glProgramUniform2f);
    OVERRIDE(glProgramUniform2fv);
    OVERRIDE(glProgramUniform2i);
    OVERRIDE(glProgramUniform2iv);
    OVERRIDE(glProgramUniform2ui);
    OVERRIDE(glProgramUniform2uiv);
    OVERRIDE(glProgramUniform3f);
    OVERRIDE(glProgramUniform3fv);
    OVERRIDE(glProgramUniform3i);
    OVERRIDE(glProgramUniform3iv);
    OVERRIDE(glProgramUniform3ui);
    OVERRIDE(glProgramUniform3uiv);
    OVERRIDE(glProgramUniform4f);
    OVERRIDE(glProgramUniform4fv);
    OVERRIDE(glProgramUniform4i);
    OVERRIDE(glProgramUniform4iv);
    OVERRIDE(glProgramUniform4ui);
    OVERRIDE(glProgramUniform4uiv);
    OVERRIDE(glProgramUniformMatrix2fv);
    OVERRIDE(glProgramUniformMatrix2x3fv);
    OVERRIDE(glProgramUniformMatrix2x4fv);
    OVERRIDE(glProgramUniformMatrix3fv);
    OVERRIDE(glProgramUniformMatrix3x2fv);
    OVERRIDE(glProgramUniformMatrix3x4fv);
    OVERRIDE(glProgramUniformMatrix4fv);
    OVERRIDE(glProgramUniformMatrix4x2fv);
    OVERRIDE(glProgramUniformMatrix4x3fv);

    OVERRIDE(glProgramParameteri);
    OVERRIDE(glUseProgramStages);
    OVERRIDE(glBindProgramPipeline);

    OVERRIDE(glGetProgramResourceiv);
    OVERRIDE(glGetProgramResourceIndex);
    OVERRIDE(glGetProgramResourceLocation);
    OVERRIDE(glGetProgramResourceName);
    OVERRIDE(glGetProgramPipelineInfoLog);

    OVERRIDE(glVertexAttribFormat);
    OVERRIDE(glVertexAttribIFormat);
    OVERRIDE(glVertexBindingDivisor);
    OVERRIDE(glVertexAttribBinding);
    OVERRIDE(glBindVertexBuffer);

    OVERRIDE_CUSTOM(glDrawArraysIndirect);
    OVERRIDE_CUSTOM(glDrawElementsIndirect);

    OVERRIDE(glTexStorage2DMultisample);
}

GL2Encoder::~GL2Encoder()
{
    delete m_compressedTextureFormats;
}

GLenum GL2Encoder::s_glGetError(void * self)
{
    GL2Encoder *ctx = (GL2Encoder *)self;
    GLenum err = ctx->getError();
    if(err != GL_NO_ERROR) {
        ctx->m_glGetError_enc(ctx); // also clear host error
        ctx->setError(GL_NO_ERROR);
        return err;
    }

    return ctx->m_glGetError_enc(self);
}

class GL2Encoder::ErrorUpdater {
public:
    ErrorUpdater(GL2Encoder* ctx) :
        mCtx(ctx),
        guest_error(ctx->getError()),
        host_error(ctx->m_glGetError_enc(ctx)) {
            // Preserve any existing GL error in the guest:
            // OpenGL ES 3.0.5 spec:
            // The command enum GetError( void ); is used to obtain error information.
            // Each detectable error is assigned a numeric code. When an error is
            // detected, a flag is set and the code is recorded. Further errors, if
            // they occur, do not affect this recorded code. When GetError is called,
            // the code is returned and the flag is cleared, so that a further error
            // will again record its code. If a call to GetError returns NO_ERROR, then
            // there has been no detectable error since the last call to GetError (or
            // since the GL was initialized).
            if (guest_error == GL_NO_ERROR) {
                guest_error = host_error;
            }
        }

    GLenum getHostErrorAndUpdate() {
        host_error = mCtx->m_glGetError_enc(mCtx);
        if (guest_error == GL_NO_ERROR) {
            guest_error = host_error;
        }
        return host_error;
    }

    void updateGuestErrorState() {
        mCtx->setError(guest_error);
    }

private:
    GL2Encoder* mCtx;
    GLenum guest_error;
    GLenum host_error;
};

template<class T>
class GL2Encoder::ScopedQueryUpdate {
public:
    ScopedQueryUpdate(GL2Encoder* ctx, uint32_t bytes, T* target) :
        mCtx(ctx),
        mBuf(bytes, 0),
        mTarget(target),
        mErrorUpdater(ctx) {
    }
    T* hostStagingBuffer() {
        return (T*)&mBuf[0];
    }
    ~ScopedQueryUpdate() {
        GLint hostError = mErrorUpdater.getHostErrorAndUpdate();
        if (hostError == GL_NO_ERROR) {
            memcpy(mTarget, &mBuf[0], mBuf.size());
        }
        mErrorUpdater.updateGuestErrorState();
    }
private:
    GL2Encoder* mCtx;
    std::vector<char> mBuf;
    T* mTarget;
    ErrorUpdater mErrorUpdater;
};

void GL2Encoder::safe_glGetBooleanv(GLenum param, GLboolean* val) {
    ScopedQueryUpdate<GLboolean> query(this, glUtilsParamSize(param) * sizeof(GLboolean), val);
    m_glGetBooleanv_enc(this, param, query.hostStagingBuffer());
}

void GL2Encoder::safe_glGetFloatv(GLenum param, GLfloat* val) {
    ScopedQueryUpdate<GLfloat> query(this, glUtilsParamSize(param) * sizeof(GLfloat), val);
    m_glGetFloatv_enc(this, param, query.hostStagingBuffer());
}

void GL2Encoder::safe_glGetIntegerv(GLenum param, GLint* val) {
    ScopedQueryUpdate<GLint> query(this, glUtilsParamSize(param) * sizeof(GLint), val);
    m_glGetIntegerv_enc(this, param, query.hostStagingBuffer());
}

void GL2Encoder::safe_glGetInteger64v(GLenum param, GLint64* val) {
    ScopedQueryUpdate<GLint64> query(this, glUtilsParamSize(param) * sizeof(GLint64), val);
    m_glGetInteger64v_enc(this, param, query.hostStagingBuffer());
}

void GL2Encoder::safe_glGetIntegeri_v(GLenum param, GLuint index, GLint* val) {
    ScopedQueryUpdate<GLint> query(this, sizeof(GLint), val);
    m_glGetIntegeri_v_enc(this, param, index, query.hostStagingBuffer());
}

void GL2Encoder::safe_glGetInteger64i_v(GLenum param, GLuint index, GLint64* val) {
    ScopedQueryUpdate<GLint64> query(this, sizeof(GLint64), val);
    m_glGetInteger64i_v_enc(this, param, index, query.hostStagingBuffer());
}

void GL2Encoder::safe_glGetBooleani_v(GLenum param, GLuint index, GLboolean* val) {
    ScopedQueryUpdate<GLboolean> query(this, sizeof(GLboolean), val);
    m_glGetBooleani_v_enc(this, param, index, query.hostStagingBuffer());
}

void GL2Encoder::s_glFlush(void *self)
{
    GL2Encoder *ctx = (GL2Encoder *) self;
    ctx->m_glFlush_enc(self);
    ctx->m_stream->flush();
}

const GLubyte *GL2Encoder::s_glGetString(void *self, GLenum name)
{
    GL2Encoder *ctx = (GL2Encoder *)self;

    GLubyte *retval =  (GLubyte *) "";
    RET_AND_SET_ERROR_IF(
        name != GL_VENDOR &&
        name != GL_RENDERER &&
        name != GL_VERSION &&
        name != GL_EXTENSIONS,
        GL_INVALID_ENUM,
        retval);
    switch(name) {
    case GL_VENDOR:
        retval = gVendorString;
        break;
    case GL_RENDERER:
        retval = gRendererString;
        break;
    case GL_VERSION:
        retval = gVersionString;
        break;
    case GL_EXTENSIONS:
        retval = gExtensionsString;
        break;
    }
    return retval;
}

void GL2Encoder::s_glPixelStorei(void *self, GLenum param, GLint value)
{
    GL2Encoder *ctx = (GL2Encoder *)self;
    SET_ERROR_IF(!GLESv2Validation::pixelStoreParam(ctx, param), GL_INVALID_ENUM);
    SET_ERROR_IF(!GLESv2Validation::pixelStoreValue(param, value), GL_INVALID_VALUE);
    ctx->m_glPixelStorei_enc(ctx, param, value);
    assert(ctx->m_state != NULL);
    ctx->m_state->setPixelStore(param, value);
}
void GL2Encoder::s_glBindBuffer(void *self, GLenum target, GLuint id)
{
    GL2Encoder *ctx = (GL2Encoder *) self;
    assert(ctx->m_state != NULL);
    ctx->m_state->bindBuffer(target, id);
    ctx->m_state->addBuffer(id);
    // TODO set error state if needed;
    ctx->m_glBindBuffer_enc(self, target, id);
}

void GL2Encoder::s_glBufferData(void * self, GLenum target, GLsizeiptr size, const GLvoid * data, GLenum usage)
{
    GL2Encoder *ctx = (GL2Encoder *) self;
    SET_ERROR_IF(!GLESv2Validation::bufferTarget(ctx, target), GL_INVALID_ENUM);
    GLuint bufferId = ctx->m_state->getBuffer(target);
    SET_ERROR_IF(bufferId==0, GL_INVALID_OPERATION);
    SET_ERROR_IF(size<0, GL_INVALID_VALUE);

    ctx->m_shared->updateBufferData(bufferId, size, (void*)data);
    ctx->m_shared->setBufferUsage(bufferId, usage);
    ctx->m_glBufferData_enc(self, target, size, data, usage);
}

void GL2Encoder::s_glBufferSubData(void * self, GLenum target, GLintptr offset, GLsizeiptr size, const GLvoid * data)
{
    GL2Encoder *ctx = (GL2Encoder *) self;
    SET_ERROR_IF(!GLESv2Validation::bufferTarget(ctx, target), GL_INVALID_ENUM);
    GLuint bufferId = ctx->m_state->getBuffer(target);
    SET_ERROR_IF(bufferId==0, GL_INVALID_OPERATION);
    SET_ERROR_IF(ctx->isBufferTargetMapped(target), GL_INVALID_OPERATION);

    GLenum res = ctx->m_shared->subUpdateBufferData(bufferId, offset, size, (void*)data);
    SET_ERROR_IF(res, res);

    ctx->m_glBufferSubData_enc(self, target, offset, size, data);
}

void GL2Encoder::s_glGenBuffers(void* self, GLsizei n, GLuint* buffers) {
    GL2Encoder *ctx = (GL2Encoder *) self;
    SET_ERROR_IF(n<0, GL_INVALID_VALUE);
    ctx->m_glGenBuffers_enc(self, n, buffers);
    for (int i = 0; i < n; i++) {
        ctx->m_state->addBuffer(buffers[i]);
    }
}

void GL2Encoder::s_glDeleteBuffers(void * self, GLsizei n, const GLuint * buffers)
{
    GL2Encoder *ctx = (GL2Encoder *) self;
    SET_ERROR_IF(n<0, GL_INVALID_VALUE);
    for (int i=0; i<n; i++) {
        // Technically if the buffer is mapped, we should unmap it, but we won't
        // use it anymore after this :)
        ctx->m_shared->deleteBufferData(buffers[i]);
        ctx->m_state->unBindBuffer(buffers[i]);
        ctx->m_state->removeBuffer(buffers[i]);
        ctx->m_glDeleteBuffers_enc(self,1,&buffers[i]);
    }
}

static bool isValidVertexAttribIndex(void *self, GLuint indx)
{
    GL2Encoder *ctx = (GL2Encoder *)self;
    GLint maxIndex;
    ctx->glGetIntegerv(self, GL_MAX_VERTEX_ATTRIBS, &maxIndex);
    return indx < maxIndex;
}

#define VALIDATE_VERTEX_ATTRIB_INDEX(index) \
    SET_ERROR_WITH_MESSAGE_IF( \
            !isValidVertexAttribIndex(self, index), GL_INVALID_VALUE, \
            GLESv2Validation::vertexAttribIndexRangeErrorMsg, (ctx, index)); \

void GL2Encoder::s_glVertexAttribPointer(void *self, GLuint indx, GLint size, GLenum type, GLboolean normalized, GLsizei stride, const GLvoid * ptr)
{
    GL2Encoder *ctx = (GL2Encoder *)self;
    assert(ctx->m_state != NULL);
    VALIDATE_VERTEX_ATTRIB_INDEX(indx);
    SET_ERROR_IF((size < 1 || size > 4), GL_INVALID_VALUE);
    SET_ERROR_IF(!GLESv2Validation::vertexAttribType(ctx, type), GL_INVALID_ENUM);
    SET_ERROR_IF(stride < 0, GL_INVALID_VALUE);
    SET_ERROR_IF((type == GL_INT_2_10_10_10_REV ||
                  type == GL_UNSIGNED_INT_2_10_10_10_REV) &&
                 size != 4,
                 GL_INVALID_OPERATION);
    ctx->m_state->setVertexAttribBinding(indx, indx);
    ctx->m_state->setVertexAttribFormat(indx, size, type, normalized, 0, false);

    GLsizei effectiveStride = stride;
    if (stride == 0) {
        effectiveStride = glSizeof(type) * size; 
        switch (type) {
            case GL_INT_2_10_10_10_REV:
            case GL_UNSIGNED_INT_2_10_10_10_REV:
                effectiveStride /= 4;
                break;
            default:
                break;
        }
    }

    ctx->m_state->bindIndexedBuffer(0, indx, ctx->m_state->currentArrayVbo(), (uintptr_t)ptr, 0, stride, effectiveStride);

    if (ctx->m_state->currentArrayVbo() != 0) {
        ctx->glVertexAttribPointerOffset(ctx, indx, size, type, normalized, stride, (uintptr_t)ptr);
    } else {
        SET_ERROR_IF(ctx->m_state->currentVertexArrayObject() != 0 && ptr, GL_INVALID_OPERATION);
        // wait for client-array handler
    }
}

void GL2Encoder::s_glGetIntegerv(void *self, GLenum param, GLint *ptr)
{
    GL2Encoder *ctx = (GL2Encoder *) self;
    assert(ctx->m_state != NULL);
    GLClientState* state = ctx->m_state;

    switch (param) {
    case GL_MAJOR_VERSION:
        *ptr = ctx->m_deviceMajorVersion;
        break;
    case GL_MINOR_VERSION:
        *ptr = ctx->m_deviceMinorVersion;
        break;
    case GL_NUM_SHADER_BINARY_FORMATS:
        *ptr = 0;
        break;
    case GL_SHADER_BINARY_FORMATS:
        // do nothing
        break;

    case GL_COMPRESSED_TEXTURE_FORMATS: {
        GLint *compressedTextureFormats = ctx->getCompressedTextureFormats();
        if (ctx->m_num_compressedTextureFormats > 0 &&
                compressedTextureFormats != NULL) {
            memcpy(ptr, compressedTextureFormats,
                    ctx->m_num_compressedTextureFormats * sizeof(GLint));
        }
        break;
    }

    case GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS:
    case GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS:
    case GL_MAX_TEXTURE_IMAGE_UNITS:
        ctx->safe_glGetIntegerv(param, ptr);
        *ptr = MIN(*ptr, GLClientState::MAX_TEXTURE_UNITS);
        break;

    case GL_TEXTURE_BINDING_2D:
        *ptr = state->getBoundTexture(GL_TEXTURE_2D);
        break;
    case GL_TEXTURE_BINDING_EXTERNAL_OES:
        *ptr = state->getBoundTexture(GL_TEXTURE_EXTERNAL_OES);
        break;

    case GL_MAX_VERTEX_ATTRIBS:
        if (!ctx->m_state->getClientStateParameter<GLint>(param, ptr)) {
            ctx->safe_glGetIntegerv(param, ptr);
            ctx->m_state->setMaxVertexAttribs(*ptr);
        }
        break;
    case GL_MAX_VERTEX_ATTRIB_STRIDE:
        if (ctx->m_max_vertexAttribStride != 0) {
            *ptr = ctx->m_max_vertexAttribStride;
        } else {
            ctx->safe_glGetIntegerv(param, ptr);
            ctx->m_max_vertexAttribStride = *ptr;
        }
        break;
    case GL_MAX_CUBE_MAP_TEXTURE_SIZE:
        if (ctx->m_max_cubeMapTextureSize != 0) {
            *ptr = ctx->m_max_cubeMapTextureSize;
        } else {
            ctx->safe_glGetIntegerv(param, ptr);
            ctx->m_max_cubeMapTextureSize = *ptr;
        }
        break;
    case GL_MAX_RENDERBUFFER_SIZE:
        if (ctx->m_max_renderBufferSize != 0) {
            *ptr = ctx->m_max_renderBufferSize;
        } else {
            ctx->safe_glGetIntegerv(param, ptr);
            ctx->m_max_renderBufferSize = *ptr;
        }
        break;
    case GL_MAX_TEXTURE_SIZE:
        if (ctx->m_max_textureSize != 0) {
            *ptr = ctx->m_max_textureSize;
        } else {
            ctx->safe_glGetIntegerv(param, ptr);
            ctx->m_max_textureSize = *ptr;
        }
        break;
    case GL_MAX_3D_TEXTURE_SIZE:
        if (ctx->m_max_3d_textureSize != 0) {
            *ptr = ctx->m_max_3d_textureSize;
        } else {
            ctx->safe_glGetIntegerv(param, ptr);
            ctx->m_max_3d_textureSize = *ptr;
        }
        break;
    case GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT:
        if (ctx->m_ssbo_offset_align != 0) {
            *ptr = ctx->m_ssbo_offset_align;
        } else {
            ctx->safe_glGetIntegerv(param, ptr);
            ctx->m_ssbo_offset_align = *ptr;
        }
        break;
    case GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT:
        if (ctx->m_ubo_offset_align != 0) {
            *ptr = ctx->m_ubo_offset_align;
        } else {
            ctx->safe_glGetIntegerv(param, ptr);
            ctx->m_ubo_offset_align = *ptr;
        }
        break;
    // Desktop OpenGL can allow a mindboggling # samples per pixel (such as 64).
    // Limit to 4 (spec minimum) to keep dEQP tests from timing out.
    case GL_MAX_SAMPLES:
    case GL_MAX_COLOR_TEXTURE_SAMPLES:
    case GL_MAX_INTEGER_SAMPLES:
    case GL_MAX_DEPTH_TEXTURE_SAMPLES:
        *ptr = 4;
        break;
    // Checks for version-incompatible enums.
    // Not allowed in vanilla ES 2.0.
    case GL_MAX_TRANSFORM_FEEDBACK_SEPARATE_ATTRIBS:
    case GL_MAX_UNIFORM_BUFFER_BINDINGS:
        SET_ERROR_IF(ctx->majorVersion() < 3, GL_INVALID_ENUM);
        ctx->safe_glGetIntegerv(param, ptr);
        break;
    case GL_MAX_COLOR_ATTACHMENTS:
    case GL_MAX_DRAW_BUFFERS:
        SET_ERROR_IF(ctx->majorVersion() < 3 &&
                     !ctx->hasExtension("GL_EXT_draw_buffers"), GL_INVALID_ENUM);
        ctx->safe_glGetIntegerv(param, ptr);
        break;
    // Not allowed in ES 3.0.
    case GL_MAX_ATOMIC_COUNTER_BUFFER_BINDINGS:
    case GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS:
    case GL_MAX_VERTEX_ATTRIB_BINDINGS:
        SET_ERROR_IF(ctx->majorVersion() < 3 ||
                     (ctx->majorVersion() == 3 &&
                      ctx->minorVersion() == 0), GL_INVALID_ENUM);
        ctx->safe_glGetIntegerv(param, ptr);
        break;
    default:
        if (!ctx->m_state->getClientStateParameter<GLint>(param, ptr)) {
            ctx->safe_glGetIntegerv(param, ptr);
        }
        break;
    }
}


void GL2Encoder::s_glGetFloatv(void *self, GLenum param, GLfloat *ptr)
{
    GL2Encoder *ctx = (GL2Encoder *)self;
    assert(ctx->m_state != NULL);
    GLClientState* state = ctx->m_state;

    switch (param) {
    case GL_NUM_SHADER_BINARY_FORMATS:
        *ptr = 0;
        break;
    case GL_SHADER_BINARY_FORMATS:
        // do nothing
        break;

    case GL_COMPRESSED_TEXTURE_FORMATS: {
        GLint *compressedTextureFormats = ctx->getCompressedTextureFormats();
        if (ctx->m_num_compressedTextureFormats > 0 &&
                compressedTextureFormats != NULL) {
            for (int i = 0; i < ctx->m_num_compressedTextureFormats; i++) {
                ptr[i] = (GLfloat) compressedTextureFormats[i];
            }
        }
        break;
    }

    case GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS:
    case GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS:
    case GL_MAX_TEXTURE_IMAGE_UNITS:
        ctx->safe_glGetFloatv(param, ptr);
        *ptr = MIN(*ptr, (GLfloat)GLClientState::MAX_TEXTURE_UNITS);
        break;

    case GL_TEXTURE_BINDING_2D:
        *ptr = (GLfloat)state->getBoundTexture(GL_TEXTURE_2D);
        break;
    case GL_TEXTURE_BINDING_EXTERNAL_OES:
        *ptr = (GLfloat)state->getBoundTexture(GL_TEXTURE_EXTERNAL_OES);
        break;

    default:
        if (!ctx->m_state->getClientStateParameter<GLfloat>(param, ptr)) {
            ctx->safe_glGetFloatv(param, ptr);
        }
        break;
    }
}


void GL2Encoder::s_glGetBooleanv(void *self, GLenum param, GLboolean *ptr)
{
    GL2Encoder *ctx = (GL2Encoder *)self;
    assert(ctx->m_state != NULL);
    GLClientState* state = ctx->m_state;

    switch (param) {
    case GL_NUM_SHADER_BINARY_FORMATS:
        *ptr = GL_FALSE;
        break;
    case GL_SHADER_BINARY_FORMATS:
        // do nothing
        break;

    case GL_COMPRESSED_TEXTURE_FORMATS: {
        GLint *compressedTextureFormats = ctx->getCompressedTextureFormats();
        if (ctx->m_num_compressedTextureFormats > 0 &&
                compressedTextureFormats != NULL) {
            for (int i = 0; i < ctx->m_num_compressedTextureFormats; i++) {
                ptr[i] = compressedTextureFormats[i] != 0 ? GL_TRUE : GL_FALSE;
            }
        }
        break;
    }

    case GL_TEXTURE_BINDING_2D:
        *ptr = state->getBoundTexture(GL_TEXTURE_2D) != 0 ? GL_TRUE : GL_FALSE;
        break;
    case GL_TEXTURE_BINDING_EXTERNAL_OES:
        *ptr = state->getBoundTexture(GL_TEXTURE_EXTERNAL_OES) != 0
                ? GL_TRUE : GL_FALSE;
        break;

    default:
        if (!ctx->m_state->getClientStateParameter<GLboolean>(param, ptr)) {
            ctx->safe_glGetBooleanv(param, ptr);
        }
        *ptr = (*ptr != 0) ? GL_TRUE : GL_FALSE;
        break;
    }
}


void GL2Encoder::s_glEnableVertexAttribArray(void *self, GLuint index)
{
    GL2Encoder *ctx = (GL2Encoder *)self;
    assert(ctx->m_state);
    VALIDATE_VERTEX_ATTRIB_INDEX(index);
    ctx->m_glEnableVertexAttribArray_enc(ctx, index);
    ctx->m_state->enable(index, 1);
}

void GL2Encoder::s_glDisableVertexAttribArray(void *self, GLuint index)
{
    GL2Encoder *ctx = (GL2Encoder *)self;
    assert(ctx->m_state);
    VALIDATE_VERTEX_ATTRIB_INDEX(index);
    ctx->m_glDisableVertexAttribArray_enc(ctx, index);
    ctx->m_state->enable(index, 0);
}


void GL2Encoder::s_glGetVertexAttribiv(void *self, GLuint index, GLenum pname, GLint *params)
{
    GL2Encoder *ctx = (GL2Encoder *)self;
    assert(ctx->m_state);
    GLint maxIndex;
    ctx->glGetIntegerv(self, GL_MAX_VERTEX_ATTRIBS, &maxIndex);
    SET_ERROR_IF(!(index < maxIndex), GL_INVALID_VALUE);

    if (!ctx->m_state->getVertexAttribParameter<GLint>(index, pname, params)) {
        ctx->m_glGetVertexAttribiv_enc(self, index, pname, params);
    }
}

void GL2Encoder::s_glGetVertexAttribfv(void *self, GLuint index, GLenum pname, GLfloat *params)
{
    GL2Encoder *ctx = (GL2Encoder *)self;
    assert(ctx->m_state);
    GLint maxIndex;
    ctx->glGetIntegerv(self, GL_MAX_VERTEX_ATTRIBS, &maxIndex);
    SET_ERROR_IF(!(index < maxIndex), GL_INVALID_VALUE);

    if (!ctx->m_state->getVertexAttribParameter<GLfloat>(index, pname, params)) {
        ctx->m_glGetVertexAttribfv_enc(self, index, pname, params);
    }
}

void GL2Encoder::s_glGetVertexAttribPointerv(void *self, GLuint index, GLenum pname, GLvoid **pointer)
{
    GL2Encoder *ctx = (GL2Encoder *)self;
    if (ctx->m_state == NULL) return;
    GLint maxIndex;
    ctx->glGetIntegerv(self, GL_MAX_VERTEX_ATTRIBS, &maxIndex);
    SET_ERROR_IF(!(index < maxIndex), GL_INVALID_VALUE);
    SET_ERROR_IF(pname != GL_VERTEX_ATTRIB_ARRAY_POINTER, GL_INVALID_ENUM);
    (void)pname;

    *pointer = (GLvoid*)(ctx->m_state->getCurrAttributeBindingInfo(index).offset);
}

void GL2Encoder::calcIndexRange(const void* indices,
                                GLenum type,
                                GLsizei count,
                                int* minIndex_out,
                                int* maxIndex_out) {
    switch(type) {
    case GL_BYTE:
    case GL_UNSIGNED_BYTE:
        GLUtils::minmaxExcept(
                (unsigned char *)indices, count,
                minIndex_out, maxIndex_out,
                m_primitiveRestartEnabled, GLUtils::primitiveRestartIndex<unsigned char>());
        break;
    case GL_SHORT:
    case GL_UNSIGNED_SHORT:
        GLUtils::minmaxExcept(
                (unsigned short *)indices, count,
                minIndex_out, maxIndex_out,
                m_primitiveRestartEnabled, GLUtils::primitiveRestartIndex<unsigned short>());
        break;
    case GL_INT:
    case GL_UNSIGNED_INT:
        GLUtils::minmaxExcept(
                (unsigned int *)indices, count,
                minIndex_out, maxIndex_out,
                m_primitiveRestartEnabled, GLUtils::primitiveRestartIndex<unsigned int>());
        break;
    default:
        ALOGE("unsupported index buffer type %d\n", type);
    }
}

void* GL2Encoder::recenterIndices(const void* src,
                                  GLenum type,
                                  GLsizei count,
                                  int minIndex) {

    void* adjustedIndices = (void*)src;

    if (minIndex != 0) {
        adjustedIndices = m_fixedBuffer.alloc(glSizeof(type) * count);
        switch(type) {
        case GL_BYTE:
        case GL_UNSIGNED_BYTE:
            GLUtils::shiftIndicesExcept(
                    (unsigned char *)src,
                    (unsigned char *)adjustedIndices,
                    count, -minIndex,
                    m_primitiveRestartEnabled,
                    (unsigned char)m_primitiveRestartIndex);
            break;
        case GL_SHORT:
        case GL_UNSIGNED_SHORT:
            GLUtils::shiftIndicesExcept(
                    (unsigned short *)src,
                    (unsigned short *)adjustedIndices,
                    count, -minIndex,
                    m_primitiveRestartEnabled,
                    (unsigned short)m_primitiveRestartIndex);
            break;
        case GL_INT:
        case GL_UNSIGNED_INT:
            GLUtils::shiftIndicesExcept(
                    (unsigned int *)src,
                    (unsigned int *)adjustedIndices,
                    count, -minIndex,
                    m_primitiveRestartEnabled,
                    (unsigned int)m_primitiveRestartIndex);
            break;
        default:
            ALOGE("unsupported index buffer type %d\n", type);
        }
    }

    return adjustedIndices;
}

void GL2Encoder::getBufferIndexRange(BufferData* buf,
                                     const void* dataWithOffset,
                                     GLenum type,
                                     size_t count,
                                     size_t offset,
                                     int* minIndex_out,
                                     int* maxIndex_out) {

    if (buf->m_indexRangeCache.findRange(
                type, offset, count,
                m_primitiveRestartEnabled,
                minIndex_out,
                maxIndex_out)) {
        return;
    }

    calcIndexRange(dataWithOffset, type, count, minIndex_out, maxIndex_out);

    buf->m_indexRangeCache.addRange(
            type, offset, count, m_primitiveRestartEnabled,
            *minIndex_out, *maxIndex_out);

    ALOGV("%s: got range [%u %u] pr? %d", __FUNCTION__, *minIndex_out, *maxIndex_out, m_primitiveRestartEnabled);
}

// For detecting legacy usage of glVertexAttribPointer
void GL2Encoder::getVBOUsage(bool* hasClientArrays, bool* hasVBOs) const {
    if (hasClientArrays) *hasClientArrays = false;
    if (hasVBOs) *hasVBOs = false;

    for (int i = 0; i < m_state->nLocations(); i++) {
        const GLClientState::VertexAttribState& state = m_state->getState(i);
        if (state.enabled) {
            const GLClientState::BufferBinding& curr_binding = m_state->getCurrAttributeBindingInfo(i);
            GLuint bufferObject = curr_binding.buffer;
            if (bufferObject == 0 && curr_binding.offset && hasClientArrays) {
                *hasClientArrays = true;
            }
            if (bufferObject != 0 && hasVBOs) {
                *hasVBOs = true;
            }
        }
    }
}

void GL2Encoder::sendVertexAttributes(GLint first, GLsizei count, bool hasClientArrays, GLsizei primcount)
{
    assert(m_state);

    GLuint currentVao = m_state->currentVertexArrayObject();
    GLuint lastBoundVbo = m_state->currentArrayVbo();
    for (int i = 0; i < m_state->nLocations(); i++) {
        bool enableDirty;
        const GLClientState::VertexAttribState& state = m_state->getStateAndEnableDirty(i, &enableDirty);

        if (!enableDirty && !state.enabled) {
            continue;
        }

        if (state.enabled) {
            const GLClientState::BufferBinding& curr_binding = m_state->getCurrAttributeBindingInfo(i);
            GLuint bufferObject = curr_binding.buffer;
            if (hasClientArrays && lastBoundVbo != bufferObject) {
                this->m_glBindBuffer_enc(this, GL_ARRAY_BUFFER, bufferObject);
                lastBoundVbo = bufferObject;
            }

            int divisor = curr_binding.divisor;
            int stride = curr_binding.stride;
            int effectiveStride = curr_binding.effectiveStride;
            uintptr_t offset = curr_binding.offset;

            int firstIndex = effectiveStride * first;
            if (firstIndex && divisor && !primcount) {
                // If firstIndex != 0 according to effectiveStride * first,
                // it needs to be adjusted if a divisor has been specified,
                // even if we are not in glDraw***Instanced.
                firstIndex = 0;
            }

            if (bufferObject == 0) {
                unsigned int datalen = state.elementSize * count;
                if (divisor && primcount) {
                    ALOGV("%s: divisor for att %d: %d, w/ stride %d (effective stride %d) size %d type 0x%x) datalen %u",
                            __FUNCTION__, i, divisor, state.stride, effectiveStride, state.elementSize, state.type, datalen);
                    int actual_count = std::max(1, (int)((primcount + divisor - 1) / divisor));
                    datalen = state.elementSize * actual_count;
                    ALOGV("%s: actual datalen %u", __FUNCTION__, datalen);
                }
                if (state.elementSize == 0) {
                    // The vertex attribute array is uninitialized. Abandon it.
                    ALOGE("a vertex attribute array is uninitialized. Skipping corresponding vertex attribute.");
                    this->m_glDisableVertexAttribArray_enc(this, i);
                    continue;
                }
                m_glEnableVertexAttribArray_enc(this, i);

                if (datalen && (!offset || !((unsigned char*)offset + firstIndex))) {
                    ALOGD("%s: bad offset / len!!!!!", __FUNCTION__);
                    continue;
                }
                if (state.isInt) {
                    this->glVertexAttribIPointerDataAEMU(this, i, state.size, state.type, stride, (unsigned char *)offset + firstIndex, datalen);
                } else {
                    this->glVertexAttribPointerData(this, i, state.size, state.type, state.normalized, stride, (unsigned char *)offset + firstIndex, datalen);
                }
            } else {
                const BufferData* buf = m_shared->getBufferData(bufferObject);
                // The following expression actually means bufLen = stride*count;
                // But the last element doesn't have to fill up the whole stride.
                // So it becomes the current form.
                unsigned int bufLen = effectiveStride * (count ? (count - 1) : 0) + state.elementSize;
                if (divisor && primcount) {
                    int actual_count = std::max(1, (int)((primcount + divisor - 1) / divisor));
                    bufLen = effectiveStride * (actual_count ? (actual_count - 1) : 0) + state.elementSize;
                }
                if (buf && firstIndex >= 0 && firstIndex + bufLen <= buf->m_size) {
                    if (hasClientArrays) {
                        m_glEnableVertexAttribArray_enc(this, i);
                        if (state.isInt) {
                            this->glVertexAttribIPointerOffsetAEMU(this, i, state.size, state.type, stride, offset + firstIndex);
                        } else {
                            this->glVertexAttribPointerOffset(this, i, state.size, state.type, state.normalized, stride, offset + firstIndex);
                        }
                    }
                } else {
                    ALOGE("a vertex attribute index out of boundary is detected. Skipping corresponding vertex attribute. buf=%p", buf);
                    if (buf) {
                        ALOGE("Out of bounds vertex attribute info: "
                                "clientArray? %d attribute %d vbo %u allocedBufferSize %u bufferDataSpecified? %d wantedStart %u wantedEnd %u",
                                hasClientArrays, i, bufferObject, buf->m_size, buf != NULL, firstIndex, firstIndex + bufLen);
                    }
                    m_glDisableVertexAttribArray_enc(this, i);
                }
            }
        } else {
            if (hasClientArrays) {
                this->m_glDisableVertexAttribArray_enc(this, i);
            }
        }
    }

    if (hasClientArrays && lastBoundVbo != m_state->currentArrayVbo()) {
        this->m_glBindBuffer_enc(this, GL_ARRAY_BUFFER, m_state->currentArrayVbo());
    }
}

void GL2Encoder::flushDrawCall() {
    // This used to be every other draw call, but
    // now that we are using real GPU buffers on host,
    // set this to every 200 draw calls
    // (tuned on z840 linux NVIDIA Quadro K2200)
    if (m_drawCallFlushCount % 200 == 0) {
        m_stream->flush();
    }
    m_drawCallFlushCount++;
}

static bool isValidDrawMode(GLenum mode)
{
    bool retval = false;
    switch (mode) {
    case GL_POINTS:
    case GL_LINE_STRIP:
    case GL_LINE_LOOP:
    case GL_LINES:
    case GL_TRIANGLE_STRIP:
    case GL_TRIANGLE_FAN:
    case GL_TRIANGLES:
        retval = true;
    }
    return retval;
}

void GL2Encoder::s_glDrawArrays(void *self, GLenum mode, GLint first, GLsizei count)
{
    GL2Encoder *ctx = (GL2Encoder *)self;
    assert(ctx->m_state != NULL);
    SET_ERROR_IF(!isValidDrawMode(mode), GL_INVALID_ENUM);
    SET_ERROR_IF(count < 0, GL_INVALID_VALUE);

    bool has_client_vertex_arrays = false;
    bool has_indirect_arrays = false;
    ctx->getVBOUsage(&has_client_vertex_arrays,
                     &has_indirect_arrays);

    if (has_client_vertex_arrays ||
        (!has_client_vertex_arrays &&
         !has_indirect_arrays)) {
        ctx->sendVertexAttributes(first, count, true);
        ctx->m_glDrawArrays_enc(ctx, mode, 0, count);
    } else {
        ctx->sendVertexAttributes(0, count, false);
        ctx->m_glDrawArrays_enc(ctx, mode, first, count);
    }
}


void GL2Encoder::s_glDrawElements(void *self, GLenum mode, GLsizei count, GLenum type, const void *indices)
{

    GL2Encoder *ctx = (GL2Encoder *)self;
    assert(ctx->m_state != NULL);
    SET_ERROR_IF(!isValidDrawMode(mode), GL_INVALID_ENUM);
    SET_ERROR_IF(count < 0, GL_INVALID_VALUE);
    SET_ERROR_IF(!(type == GL_UNSIGNED_BYTE || type == GL_UNSIGNED_SHORT || type == GL_UNSIGNED_INT), GL_INVALID_ENUM);
    SET_ERROR_IF(ctx->m_state->getTransformFeedbackActiveUnpaused(), GL_INVALID_OPERATION);

    bool has_client_vertex_arrays = false;
    bool has_indirect_arrays = false;
    int nLocations = ctx->m_state->nLocations();
    GLintptr offset = 0;

    ctx->getVBOUsage(&has_client_vertex_arrays, &has_indirect_arrays);

    if (!has_client_vertex_arrays && !has_indirect_arrays) {
        // ALOGW("glDrawElements: no vertex arrays / buffers bound to the command\n");
        GLenum status = ctx->m_glCheckFramebufferStatus_enc(self, GL_FRAMEBUFFER);
        SET_ERROR_IF(status != GL_FRAMEBUFFER_COMPLETE, GL_INVALID_FRAMEBUFFER_OPERATION);
    }

    BufferData* buf = NULL;
    int minIndex = 0, maxIndex = 0;

    // For validation/immediate index array purposes,
    // we need the min/max vertex index of the index array.
    // If the VBO != 0, this may not be the first time we have
    // used this particular index buffer. getBufferIndexRange
    // can more quickly get min/max vertex index by
    // caching previous results.
    if (ctx->m_state->currentIndexVbo() != 0) {
        buf = ctx->m_shared->getBufferData(ctx->m_state->currentIndexVbo());
        offset = (GLintptr)indices;
        indices = (void*)((GLintptr)buf->m_fixedBuffer.ptr() + (GLintptr)indices);
        ctx->getBufferIndexRange(buf,
                                 indices,
                                 type,
                                 (size_t)count,
                                 (size_t)offset,
                                 &minIndex, &maxIndex);
    } else {
        // In this case, the |indices| field holds a real
        // array, so calculate the indices now. They will
        // also be needed to know how much data to
        // transfer to host.
        ctx->calcIndexRange(indices,
                            type,
                            count,
                            &minIndex,
                            &maxIndex);
    }

    bool adjustIndices = true;
    if (ctx->m_state->currentIndexVbo() != 0) {
        if (!has_client_vertex_arrays) {
            ctx->sendVertexAttributes(0, maxIndex + 1, false);
            ctx->m_glBindBuffer_enc(self, GL_ELEMENT_ARRAY_BUFFER, ctx->m_state->currentIndexVbo());
            ctx->glDrawElementsOffset(ctx, mode, count, type, offset);
            ctx->flushDrawCall();
            adjustIndices = false;
        } else {
            BufferData * buf = ctx->m_shared->getBufferData(ctx->m_state->currentIndexVbo());
            ctx->m_glBindBuffer_enc(self, GL_ELEMENT_ARRAY_BUFFER, 0);
        }
    }
    if (adjustIndices) {
        void *adjustedIndices =
            ctx->recenterIndices(indices,
                                 type,
                                 count,
                                 minIndex);

        if (has_indirect_arrays || 1) {
            ctx->sendVertexAttributes(minIndex, maxIndex - minIndex + 1, true);
            ctx->glDrawElementsData(ctx, mode, count, type, adjustedIndices,
                                    count * glSizeof(type));
            // XXX - OPTIMIZATION (see the other else branch) should be implemented
            if(!has_indirect_arrays) {
                //ALOGD("unoptimized drawelements !!!\n");
            }
        } else {
            // we are all direct arrays and immidate mode index array -
            // rebuild the arrays and the index array;
            ALOGE("glDrawElements: direct index & direct buffer data - will be implemented in later versions;\n");
        }
    }
}


GLint * GL2Encoder::getCompressedTextureFormats()
{
    if (m_compressedTextureFormats == NULL) {
        this->glGetIntegerv(this, GL_NUM_COMPRESSED_TEXTURE_FORMATS,
                            &m_num_compressedTextureFormats);
        if (m_num_compressedTextureFormats > 0) {
            // get number of texture formats;
            m_compressedTextureFormats = new GLint[m_num_compressedTextureFormats];
            this->glGetCompressedTextureFormats(this, m_num_compressedTextureFormats, m_compressedTextureFormats);
        }
    }
    return m_compressedTextureFormats;
}

// Replace uses of samplerExternalOES with sampler2D, recording the names of
// modified shaders in data. Also remove
//   #extension GL_OES_EGL_image_external : require
// statements.
//
// This implementation assumes the input has already been pre-processed. If not,
// a few cases will be mishandled:
//
// 1. "mySampler" will be incorrectly recorded as being a samplerExternalOES in
//    the following code:
//      #if 1
//      uniform sampler2D mySampler;
//      #else
//      uniform samplerExternalOES mySampler;
//      #endif
//
// 2. Comments that look like sampler declarations will be incorrectly modified
//    and recorded:
//      // samplerExternalOES hahaFooledYou
//
// 3. However, GLSL ES does not have a concatentation operator, so things like
//    this (valid in C) are invalid and not a problem:
//      #define SAMPLER(TYPE, NAME) uniform sampler#TYPE NAME
//      SAMPLER(ExternalOES, mySampler);
//
static bool replaceSamplerExternalWith2D(char* const str, ShaderData* const data)
{
    static const char STR_HASH_EXTENSION[] = "#extension";
    static const char STR_GL_OES_EGL_IMAGE_EXTERNAL[] = "GL_OES_EGL_image_external";
    static const char STR_SAMPLER_EXTERNAL_OES[] = "samplerExternalOES";
    static const char STR_SAMPLER2D_SPACE[]      = "sampler2D         ";

    // -- overwrite all "#extension GL_OES_EGL_image_external : xxx" statements
    char* c = str;
    while ((c = strstr(c, STR_HASH_EXTENSION))) {
        char* start = c;
        c += sizeof(STR_HASH_EXTENSION)-1;
        while (isspace(*c) && *c != '\0') {
            c++;
        }
        if (strncmp(c, STR_GL_OES_EGL_IMAGE_EXTERNAL,
                sizeof(STR_GL_OES_EGL_IMAGE_EXTERNAL)-1) == 0)
        {
            // #extension statements are terminated by end of line
            c = start;
            while (*c != '\0' && *c != '\r' && *c != '\n') {
                *c++ = ' ';
            }
        }
    }

    // -- replace "samplerExternalOES" with "sampler2D" and record name
    c = str;
    while ((c = strstr(c, STR_SAMPLER_EXTERNAL_OES))) {
        // Make sure "samplerExternalOES" isn't a substring of a larger token
        if (c == str || !isspace(*(c-1))) {
            c++;
            continue;
        }
        char* sampler_start = c;
        c += sizeof(STR_SAMPLER_EXTERNAL_OES)-1;
        if (!isspace(*c) && *c != '\0') {
            continue;
        }

        // capture sampler name
        while (isspace(*c) && *c != '\0') {
            c++;
        }
        if (!isalpha(*c) && *c != '_') {
            // not an identifier
            return false;
        }
        char* name_start = c;
        do {
            c++;
        } while (isalnum(*c) || *c == '_');
        data->samplerExternalNames.push_back(
                android::String8(name_start, c - name_start));

        // memcpy instead of strcpy since we don't want the NUL terminator
        memcpy(sampler_start, STR_SAMPLER2D_SPACE, sizeof(STR_SAMPLER2D_SPACE)-1);
    }

    return true;
}

void GL2Encoder::s_glShaderBinary(void *self, GLsizei n, const GLuint *shaders, GLenum binaryformat, const void* binary, GLsizei length)
{
    GL2Encoder* ctx = (GL2Encoder*)self;
    // Although it is not supported, need to set proper error code.
    SET_ERROR_IF(1, GL_INVALID_ENUM);
}

void GL2Encoder::s_glShaderSource(void *self, GLuint shader, GLsizei count, const GLchar * const *string, const GLint *length)
{
    GL2Encoder* ctx = (GL2Encoder*)self;
    ShaderData* shaderData = ctx->m_shared->getShaderData(shader);
    SET_ERROR_IF(!ctx->m_shared->isShaderOrProgramObject(shader), GL_INVALID_VALUE);
    SET_ERROR_IF(!shaderData, GL_INVALID_OPERATION);
    SET_ERROR_IF((count<0), GL_INVALID_VALUE);

    // Track original sources---they may be translated in the backend
    std::vector<std::string> orig_sources;
    for (int i = 0; i < count; i++) {
        orig_sources.push_back(std::string((const char*)(string[i])));
    }
    shaderData->sources = orig_sources;

    int len = glUtilsCalcShaderSourceLen((char**)string, (GLint*)length, count);
    char *str = new char[len + 1];
    glUtilsPackStrings(str, (char**)string, (GLint*)length, count);

    // TODO: pre-process str before calling replaceSamplerExternalWith2D().
    // Perhaps we can borrow Mesa's pre-processor?

    if (!replaceSamplerExternalWith2D(str, shaderData)) {
        delete[] str;
        ctx->setError(GL_OUT_OF_MEMORY);
        return;
    }
    ctx->glShaderString(ctx, shader, str, len + 1);
    delete[] str;
}

void GL2Encoder::s_glFinish(void *self)
{
    GL2Encoder *ctx = (GL2Encoder *)self;
    ctx->glFinishRoundTrip(self);
}

void GL2Encoder::s_glLinkProgram(void * self, GLuint program)
{
    GL2Encoder *ctx = (GL2Encoder *)self;
    bool isProgram = ctx->m_shared->isProgram(program);
    SET_ERROR_IF(!isProgram && !ctx->m_shared->isShader(program), GL_INVALID_VALUE);
    SET_ERROR_IF(!isProgram, GL_INVALID_OPERATION);

    ctx->m_glLinkProgram_enc(self, program);

    GLint linkStatus = 0;
    ctx->glGetProgramiv(self, program, GL_LINK_STATUS, &linkStatus);
    if (!linkStatus) {
        return;
    }

    //get number of active uniforms in the program
    GLint numUniforms=0;
    ctx->glGetProgramiv(self, program, GL_ACTIVE_UNIFORMS, &numUniforms);
    ctx->m_shared->initProgramData(program,numUniforms);

    //get the length of the longest uniform name
    GLint maxLength=0;
    ctx->glGetProgramiv(self, program, GL_ACTIVE_UNIFORM_MAX_LENGTH, &maxLength);

    GLint size;
    GLenum type;
    GLchar *name = new GLchar[maxLength+1];
    GLint location;
    //for each active uniform, get its size and starting location.
    for (GLint i=0 ; i<numUniforms ; ++i)
    {
        ctx->glGetActiveUniform(self, program, i, maxLength, NULL, &size, &type, name);
        location = ctx->m_glGetUniformLocation_enc(self, program, name);
        ctx->m_shared->setProgramIndexInfo(program, i, location, size, type, name);
    }
    ctx->m_shared->setupLocationShiftWAR(program);

    delete[] name;
}

void GL2Encoder::s_glDeleteProgram(void *self, GLuint program)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    ctx->m_glDeleteProgram_enc(self, program);

    ctx->m_shared->deleteProgramData(program);
}

void GL2Encoder::s_glGetUniformiv(void *self, GLuint program, GLint location, GLint* params)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    SET_ERROR_IF(!ctx->m_shared->isShaderOrProgramObject(program), GL_INVALID_VALUE);
    SET_ERROR_IF(!ctx->m_shared->isProgram(program), GL_INVALID_OPERATION);
    SET_ERROR_IF(!ctx->m_shared->isProgramInitialized(program), GL_INVALID_OPERATION);
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program, location);
    SET_ERROR_IF(ctx->m_shared->getProgramUniformType(program,hostLoc)==0, GL_INVALID_OPERATION);
    ctx->m_glGetUniformiv_enc(self, program, hostLoc, params);
}
void GL2Encoder::s_glGetUniformfv(void *self, GLuint program, GLint location, GLfloat* params)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    SET_ERROR_IF(!ctx->m_shared->isShaderOrProgramObject(program), GL_INVALID_VALUE);
    SET_ERROR_IF(!ctx->m_shared->isProgram(program), GL_INVALID_OPERATION);
    SET_ERROR_IF(!ctx->m_shared->isProgramInitialized(program), GL_INVALID_OPERATION);
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program,location);
    SET_ERROR_IF(ctx->m_shared->getProgramUniformType(program,hostLoc)==0, GL_INVALID_OPERATION);
    ctx->m_glGetUniformfv_enc(self, program, hostLoc, params);
}

GLuint GL2Encoder::s_glCreateProgram(void * self)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLuint program = ctx->m_glCreateProgram_enc(self);
    if (program!=0)
        ctx->m_shared->addProgramData(program);
    return program;
}

GLuint GL2Encoder::s_glCreateShader(void *self, GLenum shaderType)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    RET_AND_SET_ERROR_IF(!GLESv2Validation::shaderType(ctx, shaderType), GL_INVALID_ENUM, 0);
    GLuint shader = ctx->m_glCreateShader_enc(self, shaderType);
    if (shader != 0) {
        if (!ctx->m_shared->addShaderData(shader)) {
            ctx->m_glDeleteShader_enc(self, shader);
            return 0;
        }
    }
    return shader;
}

void GL2Encoder::s_glGetAttachedShaders(void *self, GLuint program, GLsizei maxCount,
        GLsizei* count, GLuint* shaders)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    SET_ERROR_IF(maxCount < 0, GL_INVALID_VALUE);
    ctx->m_glGetAttachedShaders_enc(self, program, maxCount, count, shaders);
}

void GL2Encoder::s_glGetShaderSource(void *self, GLuint shader, GLsizei bufsize,
            GLsizei* length, GLchar* source)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    SET_ERROR_IF(bufsize < 0, GL_INVALID_VALUE);
    ctx->m_glGetShaderSource_enc(self, shader, bufsize, length, source);
    ShaderData* shaderData = ctx->m_shared->getShaderData(shader);
    if (shaderData) {
        std::string returned;
        int curr_len = 0;
        for (int i = 0; i < shaderData->sources.size(); i++) {
            if (curr_len + shaderData->sources[i].size() < bufsize - 1) {
                returned += shaderData->sources[i];
            } else {
                returned += shaderData->sources[i].substr(0, bufsize - 1 - curr_len);
                break;
            }
        }
        memcpy(source, returned.substr(0, bufsize - 1).c_str(), bufsize);
    }
}

void GL2Encoder::s_glGetShaderInfoLog(void *self, GLuint shader, GLsizei bufsize,
        GLsizei* length, GLchar* infolog)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    SET_ERROR_IF(bufsize < 0, GL_INVALID_VALUE);
    ctx->m_glGetShaderInfoLog_enc(self, shader, bufsize, length, infolog);
}

void GL2Encoder::s_glGetProgramInfoLog(void *self, GLuint program, GLsizei bufsize,
        GLsizei* length, GLchar* infolog)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    SET_ERROR_IF(bufsize < 0, GL_INVALID_VALUE);
    ctx->m_glGetProgramInfoLog_enc(self, program, bufsize, length, infolog);
}

void GL2Encoder::s_glDeleteShader(void *self, GLenum shader)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    ctx->m_glDeleteShader_enc(self,shader);
    ctx->m_shared->unrefShaderData(shader);
}

void GL2Encoder::s_glAttachShader(void *self, GLuint program, GLuint shader)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    ctx->m_glAttachShader_enc(self, program, shader);
    ctx->m_shared->attachShader(program, shader);
}

void GL2Encoder::s_glDetachShader(void *self, GLuint program, GLuint shader)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    ctx->m_glDetachShader_enc(self, program, shader);
    ctx->m_shared->detachShader(program, shader);
}

int sArrIndexOfUniformExpr(const char* name, int* err) {
    *err = 0;
    int arrIndex = 0;
    int namelen = strlen(name);
    if (name[namelen-1] == ']') {
        const char *brace = strrchr(name,'[');
        if (!brace || sscanf(brace+1,"%d",&arrIndex) != 1) {
            *err = 1; return 0;
        }
    }
    return arrIndex;
}

int GL2Encoder::s_glGetUniformLocation(void *self, GLuint program, const GLchar *name)
{
    if (!name) return -1;

    GL2Encoder *ctx = (GL2Encoder*)self;

    // if we need the uniform location WAR
    // parse array index from the end of the name string
    int arrIndex = 0;
    bool needLocationWAR = ctx->m_shared->needUniformLocationWAR(program);
    if (needLocationWAR) {
        int err;
        arrIndex = sArrIndexOfUniformExpr(name, &err);
        if (err) return -1;
    }

    int hostLoc = ctx->m_glGetUniformLocation_enc(self, program, name);
    if (hostLoc >= 0 && needLocationWAR) {
        return ctx->m_shared->locationWARHostToApp(program, hostLoc, arrIndex);
    }
    return hostLoc;
}

bool GL2Encoder::updateHostTexture2DBinding(GLenum texUnit, GLenum newTarget)
{
    if (newTarget != GL_TEXTURE_2D && newTarget != GL_TEXTURE_EXTERNAL_OES)
        return false;

    m_state->setActiveTextureUnit(texUnit);

    GLenum oldTarget = m_state->getPriorityEnabledTarget(GL_TEXTURE_2D);
    if (newTarget != oldTarget) {
        if (newTarget == GL_TEXTURE_EXTERNAL_OES) {
            m_state->disableTextureTarget(GL_TEXTURE_2D);
            m_state->enableTextureTarget(GL_TEXTURE_EXTERNAL_OES);
        } else {
            m_state->disableTextureTarget(GL_TEXTURE_EXTERNAL_OES);
            m_state->enableTextureTarget(GL_TEXTURE_2D);
        }
        m_glActiveTexture_enc(this, texUnit);
        m_glBindTexture_enc(this, GL_TEXTURE_2D,
                m_state->getBoundTexture(newTarget));
        return true;
    }

    return false;
}

void GL2Encoder::updateHostTexture2DBindingsFromProgramData(GLuint program) {
    GL2Encoder *ctx = this;
    GLClientState* state = ctx->m_state;
    GLSharedGroupPtr shared = ctx->m_shared;

    GLenum origActiveTexture = state->getActiveTextureUnit();
    GLenum hostActiveTexture = origActiveTexture;
    GLint samplerIdx = -1;
    GLint samplerVal;
    GLenum samplerTarget;
    while ((samplerIdx = shared->getNextSamplerUniform(program, samplerIdx, &samplerVal, &samplerTarget)) != -1) {
        if (samplerVal < 0 || samplerVal >= GLClientState::MAX_TEXTURE_UNITS)
            continue;
        if (ctx->updateHostTexture2DBinding(GL_TEXTURE0 + samplerVal,
                    samplerTarget))
        {
            hostActiveTexture = GL_TEXTURE0 + samplerVal;
        }
    }
    state->setActiveTextureUnit(origActiveTexture);
    if (hostActiveTexture != origActiveTexture) {
        ctx->m_glActiveTexture_enc(ctx, origActiveTexture);
    }
}

void GL2Encoder::s_glUseProgram(void *self, GLuint program)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLSharedGroupPtr shared = ctx->m_shared;

    SET_ERROR_IF(program && !shared->isShaderOrProgramObject(program), GL_INVALID_VALUE);
    SET_ERROR_IF(program && !shared->isProgram(program), GL_INVALID_OPERATION);

    ctx->m_glUseProgram_enc(self, program);
    ctx->m_state->setCurrentProgram(program);
    ctx->m_state->setCurrentShaderProgram(program);

    ctx->updateHostTexture2DBindingsFromProgramData(program);
}

void GL2Encoder::s_glUniform1f(void *self , GLint location, GLfloat x)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(ctx->m_state->currentShaderProgram(),location);
    ctx->m_glUniform1f_enc(self, hostLoc, x);
}

void GL2Encoder::s_glUniform1fv(void *self , GLint location, GLsizei count, const GLfloat* v)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(ctx->m_state->currentShaderProgram(),location);
    ctx->m_glUniform1fv_enc(self, hostLoc, count, v);
}

void GL2Encoder::s_glUniform1i(void *self , GLint location, GLint x)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;
    GLSharedGroupPtr shared = ctx->m_shared;

    GLint hostLoc = ctx->m_shared->locationWARAppToHost(ctx->m_state->currentShaderProgram(),location);
    ctx->m_glUniform1i_enc(self, hostLoc, x);

    GLenum target;
    if (shared->setSamplerUniform(state->currentShaderProgram(), location, x, &target)) {
        GLenum origActiveTexture = state->getActiveTextureUnit();
        if (ctx->updateHostTexture2DBinding(GL_TEXTURE0 + x, target)) {
            ctx->m_glActiveTexture_enc(self, origActiveTexture);
        }
        state->setActiveTextureUnit(origActiveTexture);
    }
}

void GL2Encoder::s_glUniform1iv(void *self , GLint location, GLsizei count, const GLint* v)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(ctx->m_state->currentShaderProgram(),location);
    ctx->m_glUniform1iv_enc(self, hostLoc, count, v);
}

void GL2Encoder::s_glUniform2f(void *self , GLint location, GLfloat x, GLfloat y)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(ctx->m_state->currentShaderProgram(),location);
    ctx->m_glUniform2f_enc(self, hostLoc, x, y);
}

void GL2Encoder::s_glUniform2fv(void *self , GLint location, GLsizei count, const GLfloat* v)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(ctx->m_state->currentShaderProgram(),location);
    ctx->m_glUniform2fv_enc(self, hostLoc, count, v);
}

void GL2Encoder::s_glUniform2i(void *self , GLint location, GLint x, GLint y)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(ctx->m_state->currentShaderProgram(),location);
    ctx->m_glUniform2i_enc(self, hostLoc, x, y);
}

void GL2Encoder::s_glUniform2iv(void *self , GLint location, GLsizei count, const GLint* v)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(ctx->m_state->currentShaderProgram(),location);
    ctx->m_glUniform2iv_enc(self, hostLoc, count, v);
}

void GL2Encoder::s_glUniform3f(void *self , GLint location, GLfloat x, GLfloat y, GLfloat z)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(ctx->m_state->currentShaderProgram(),location);
    ctx->m_glUniform3f_enc(self, hostLoc, x, y, z);
}

void GL2Encoder::s_glUniform3fv(void *self , GLint location, GLsizei count, const GLfloat* v)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(ctx->m_state->currentShaderProgram(),location);
    ctx->m_glUniform3fv_enc(self, hostLoc, count, v);
}

void GL2Encoder::s_glUniform3i(void *self , GLint location, GLint x, GLint y, GLint z)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(ctx->m_state->currentShaderProgram(),location);
    ctx->m_glUniform3i_enc(self, hostLoc, x, y, z);
}

void GL2Encoder::s_glUniform3iv(void *self , GLint location, GLsizei count, const GLint* v)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(ctx->m_state->currentShaderProgram(),location);
    ctx->m_glUniform3iv_enc(self, hostLoc, count, v);
}

void GL2Encoder::s_glUniform4f(void *self , GLint location, GLfloat x, GLfloat y, GLfloat z, GLfloat w)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(ctx->m_state->currentShaderProgram(),location);
    ctx->m_glUniform4f_enc(self, hostLoc, x, y, z, w);
}

void GL2Encoder::s_glUniform4fv(void *self , GLint location, GLsizei count, const GLfloat* v)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(ctx->m_state->currentShaderProgram(),location);
    ctx->m_glUniform4fv_enc(self, hostLoc, count, v);
}

void GL2Encoder::s_glUniform4i(void *self , GLint location, GLint x, GLint y, GLint z, GLint w)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(ctx->m_state->currentShaderProgram(),location);
    ctx->m_glUniform4i_enc(self, hostLoc, x, y, z, w);
}

void GL2Encoder::s_glUniform4iv(void *self , GLint location, GLsizei count, const GLint* v)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(ctx->m_state->currentShaderProgram(),location);
    ctx->m_glUniform4iv_enc(self, hostLoc, count, v);
}

void GL2Encoder::s_glUniformMatrix2fv(void *self , GLint location, GLsizei count, GLboolean transpose, const GLfloat* value)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(ctx->m_state->currentShaderProgram(),location);
    ctx->m_glUniformMatrix2fv_enc(self, hostLoc, count, transpose, value);
}

void GL2Encoder::s_glUniformMatrix3fv(void *self , GLint location, GLsizei count, GLboolean transpose, const GLfloat* value)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(ctx->m_state->currentShaderProgram(),location);
    ctx->m_glUniformMatrix3fv_enc(self, hostLoc, count, transpose, value);
}

void GL2Encoder::s_glUniformMatrix4fv(void *self , GLint location, GLsizei count, GLboolean transpose, const GLfloat* value)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(ctx->m_state->currentShaderProgram(),location);
    ctx->m_glUniformMatrix4fv_enc(self, hostLoc, count, transpose, value);
}

void GL2Encoder::s_glActiveTexture(void* self, GLenum texture)
{
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;
    GLenum err;

    SET_ERROR_IF((err = state->setActiveTextureUnit(texture)) != GL_NO_ERROR, err);

    ctx->m_glActiveTexture_enc(ctx, texture);
}

void GL2Encoder::s_glBindTexture(void* self, GLenum target, GLuint texture)
{
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;
    GLenum err;
    GLboolean firstUse;

    SET_ERROR_IF(!GLESv2Validation::textureTarget(ctx, target), GL_INVALID_ENUM);
    SET_ERROR_IF((err = state->bindTexture(target, texture, &firstUse)) != GL_NO_ERROR, err);

    if (target != GL_TEXTURE_2D && target != GL_TEXTURE_EXTERNAL_OES) {
        ctx->m_glBindTexture_enc(ctx, target, texture);
        return;
    }

    GLenum priorityTarget = state->getPriorityEnabledTarget(GL_TEXTURE_2D);

    if (target == GL_TEXTURE_EXTERNAL_OES && firstUse) {
        ctx->m_glBindTexture_enc(ctx, GL_TEXTURE_2D, texture);
        ctx->m_glTexParameteri_enc(ctx, GL_TEXTURE_2D,
                GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        ctx->m_glTexParameteri_enc(ctx, GL_TEXTURE_2D,
                GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        ctx->m_glTexParameteri_enc(ctx, GL_TEXTURE_2D,
                GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        if (target != priorityTarget) {
            ctx->m_glBindTexture_enc(ctx, GL_TEXTURE_2D,
                    state->getBoundTexture(GL_TEXTURE_2D));
        }
    }

    if (target == priorityTarget) {
        ctx->m_glBindTexture_enc(ctx, GL_TEXTURE_2D, texture);
    }
}

void GL2Encoder::s_glDeleteTextures(void* self, GLsizei n, const GLuint* textures)
{
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;

    state->deleteTextures(n, textures);
    ctx->m_glDeleteTextures_enc(ctx, n, textures);
}

void GL2Encoder::s_glGetTexParameterfv(void* self,
        GLenum target, GLenum pname, GLfloat* params)
{
    GL2Encoder* ctx = (GL2Encoder*)self;
    const GLClientState* state = ctx->m_state;

    if (target == GL_TEXTURE_2D || target == GL_TEXTURE_EXTERNAL_OES) {
        ctx->override2DTextureTarget(target);
        ctx->m_glGetTexParameterfv_enc(ctx, GL_TEXTURE_2D, pname, params);
        ctx->restore2DTextureTarget(target);
    } else {
        ctx->m_glGetTexParameterfv_enc(ctx, target, pname, params);
    }
}

void GL2Encoder::s_glGetTexParameteriv(void* self,
        GLenum target, GLenum pname, GLint* params)
{
    GL2Encoder* ctx = (GL2Encoder*)self;
    const GLClientState* state = ctx->m_state;

    switch (pname) {
    case GL_REQUIRED_TEXTURE_IMAGE_UNITS_OES:
        *params = 1;
        break;

    default:
        if (target == GL_TEXTURE_2D || target == GL_TEXTURE_EXTERNAL_OES) {
            ctx->override2DTextureTarget(target);
            ctx->m_glGetTexParameteriv_enc(ctx, GL_TEXTURE_2D, pname, params);
            ctx->restore2DTextureTarget(target);
        } else {
            ctx->m_glGetTexParameteriv_enc(ctx, target, pname, params);
        }
        break;
    }
}

static bool isValidTextureExternalParam(GLenum pname, GLenum param)
{
    switch (pname) {
    case GL_TEXTURE_MIN_FILTER:
    case GL_TEXTURE_MAG_FILTER:
        return param == GL_NEAREST || param == GL_LINEAR;

    case GL_TEXTURE_WRAP_S:
    case GL_TEXTURE_WRAP_T:
        return param == GL_CLAMP_TO_EDGE;

    default:
        return true;
    }
}

void GL2Encoder::s_glTexParameterf(void* self,
        GLenum target, GLenum pname, GLfloat param)
{
    GL2Encoder* ctx = (GL2Encoder*)self;
    const GLClientState* state = ctx->m_state;

    SET_ERROR_IF((target == GL_TEXTURE_EXTERNAL_OES &&
            !isValidTextureExternalParam(pname, (GLenum)param)),
            GL_INVALID_ENUM);

    if (target == GL_TEXTURE_2D || target == GL_TEXTURE_EXTERNAL_OES) {
        ctx->override2DTextureTarget(target);
        ctx->m_glTexParameterf_enc(ctx, GL_TEXTURE_2D, pname, param);
        ctx->restore2DTextureTarget(target);
    } else {
        ctx->m_glTexParameterf_enc(ctx, target, pname, param);
    }
}

void GL2Encoder::s_glTexParameterfv(void* self,
        GLenum target, GLenum pname, const GLfloat* params)
{
    GL2Encoder* ctx = (GL2Encoder*)self;
    const GLClientState* state = ctx->m_state;

    SET_ERROR_IF((target == GL_TEXTURE_EXTERNAL_OES &&
            !isValidTextureExternalParam(pname, (GLenum)params[0])),
            GL_INVALID_ENUM);

    if (target == GL_TEXTURE_2D || target == GL_TEXTURE_EXTERNAL_OES) {
        ctx->override2DTextureTarget(target);
        ctx->m_glTexParameterfv_enc(ctx, GL_TEXTURE_2D, pname, params);
        ctx->restore2DTextureTarget(target);
    } else {
        ctx->m_glTexParameterfv_enc(ctx, target, pname, params);
    }
}

void GL2Encoder::s_glTexParameteri(void* self,
        GLenum target, GLenum pname, GLint param)
{
    GL2Encoder* ctx = (GL2Encoder*)self;
    const GLClientState* state = ctx->m_state;

    SET_ERROR_IF((target == GL_TEXTURE_EXTERNAL_OES &&
            !isValidTextureExternalParam(pname, (GLenum)param)),
            GL_INVALID_ENUM);

    if (target == GL_TEXTURE_2D || target == GL_TEXTURE_EXTERNAL_OES) {
        ctx->override2DTextureTarget(target);
        ctx->m_glTexParameteri_enc(ctx, GL_TEXTURE_2D, pname, param);
        ctx->restore2DTextureTarget(target);
    } else {
        ctx->m_glTexParameteri_enc(ctx, target, pname, param);
    }
}

static int ilog2(uint32_t x) {
    int p = 0;
    while ((1 << p) < x)
        p++;
    return p;
}

void GL2Encoder::s_glTexImage2D(void* self, GLenum target, GLint level,
        GLint internalformat, GLsizei width, GLsizei height, GLint border,
        GLenum format, GLenum type, const GLvoid* pixels)
{
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;

    SET_ERROR_IF(!GLESv2Validation::textureTarget(ctx, target), GL_INVALID_ENUM);
    SET_ERROR_IF(!GLESv2Validation::pixelType(ctx, type), GL_INVALID_ENUM);
    SET_ERROR_IF(!GLESv2Validation::pixelFormat(ctx, format), GL_INVALID_ENUM);
    // If unpack buffer is nonzero, verify unmapped state.
    SET_ERROR_IF(ctx->isBufferTargetMapped(GL_PIXEL_UNPACK_BUFFER), GL_INVALID_OPERATION);

    GLint max_texture_size;
    GLint max_cube_map_texture_size;
    ctx->glGetIntegerv(ctx, GL_MAX_TEXTURE_SIZE, &max_texture_size);
    ctx->glGetIntegerv(ctx, GL_MAX_CUBE_MAP_TEXTURE_SIZE, &max_cube_map_texture_size);
    SET_ERROR_IF(level < 0, GL_INVALID_VALUE);
    SET_ERROR_IF(level > ilog2(max_texture_size), GL_INVALID_VALUE);
    SET_ERROR_IF((target == GL_TEXTURE_CUBE_MAP) &&
                 (level > ilog2(max_cube_map_texture_size)), GL_INVALID_VALUE);
    SET_ERROR_IF(width < 0 || height < 0, GL_INVALID_VALUE);
    SET_ERROR_IF(width > max_texture_size, GL_INVALID_VALUE);
    SET_ERROR_IF(height > max_texture_size, GL_INVALID_VALUE);
    SET_ERROR_IF(GLESv2Validation::isCubeMapTarget(target) && width > max_cube_map_texture_size, GL_INVALID_VALUE);
    SET_ERROR_IF(GLESv2Validation::isCubeMapTarget(target) && height > max_cube_map_texture_size, GL_INVALID_VALUE);
    SET_ERROR_IF(border != 0, GL_INVALID_VALUE);
    // If unpack buffer is nonzero, verify buffer data fits and is evenly divisible by the type.
    SET_ERROR_IF(ctx->boundBuffer(GL_PIXEL_UNPACK_BUFFER) &&
                 ctx->getBufferData(GL_PIXEL_UNPACK_BUFFER) &&
                 (ctx->m_state->pboNeededDataSize(width, height, 1, format, type, 0) >
                  ctx->getBufferData(GL_PIXEL_UNPACK_BUFFER)->m_size),
                 GL_INVALID_OPERATION);
    SET_ERROR_IF(ctx->boundBuffer(GL_PIXEL_UNPACK_BUFFER) &&
                 ctx->getBufferData(GL_PIXEL_UNPACK_BUFFER) &&
                 (ctx->getBufferData(GL_PIXEL_UNPACK_BUFFER)->m_size %
                  glSizeof(type)),
                 GL_INVALID_OPERATION);
    SET_ERROR_IF(ctx->boundBuffer(GL_PIXEL_UNPACK_BUFFER) &&
                 ctx->getBufferData(GL_PIXEL_UNPACK_BUFFER) &&
                 ((uintptr_t)pixels % glSizeof(type)),
                 GL_INVALID_OPERATION);
    SET_ERROR_IF(state->isBoundTextureImmutableFormat(target), GL_INVALID_OPERATION);

    GLenum stateTarget = target;
    if (target == GL_TEXTURE_CUBE_MAP_POSITIVE_X ||
        target == GL_TEXTURE_CUBE_MAP_POSITIVE_Y ||
        target == GL_TEXTURE_CUBE_MAP_POSITIVE_Z ||
        target == GL_TEXTURE_CUBE_MAP_NEGATIVE_X ||
        target == GL_TEXTURE_CUBE_MAP_NEGATIVE_Y ||
        target == GL_TEXTURE_CUBE_MAP_NEGATIVE_Z)
        stateTarget = GL_TEXTURE_CUBE_MAP;

    state->setBoundTextureInternalFormat(stateTarget, internalformat);
    state->setBoundTextureFormat(stateTarget, format);
    state->setBoundTextureType(stateTarget, type);
    state->setBoundTextureDims(stateTarget, level, width, height, 1);

    if (target == GL_TEXTURE_2D || target == GL_TEXTURE_EXTERNAL_OES) {
        ctx->override2DTextureTarget(target);
    }

    if (ctx->boundBuffer(GL_PIXEL_UNPACK_BUFFER)) {
        ctx->glTexImage2DOffsetAEMU(
                ctx, target, level, internalformat,
                width, height, border,
                format, type, (uintptr_t)pixels);
    } else {
        ctx->m_glTexImage2D_enc(
                ctx, target, level, internalformat,
                width, height, border,
                format, type, pixels);
    }

    if (target == GL_TEXTURE_2D || target == GL_TEXTURE_EXTERNAL_OES) {
        ctx->restore2DTextureTarget(target);
    }
}

void GL2Encoder::s_glTexSubImage2D(void* self, GLenum target, GLint level,
        GLint xoffset, GLint yoffset, GLsizei width, GLsizei height, GLenum format,
        GLenum type, const GLvoid* pixels)
{
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;
 
    SET_ERROR_IF(!GLESv2Validation::textureTarget(ctx, target), GL_INVALID_ENUM);
    SET_ERROR_IF(!GLESv2Validation::pixelType(ctx, type), GL_INVALID_ENUM);
    SET_ERROR_IF(!GLESv2Validation::pixelFormat(ctx, format), GL_INVALID_ENUM);
    // If unpack buffer is nonzero, verify unmapped state.
    SET_ERROR_IF(ctx->isBufferTargetMapped(GL_PIXEL_UNPACK_BUFFER), GL_INVALID_OPERATION);

    GLint max_texture_size;
    GLint max_cube_map_texture_size;
    ctx->glGetIntegerv(ctx, GL_MAX_TEXTURE_SIZE, &max_texture_size);
    ctx->glGetIntegerv(ctx, GL_MAX_CUBE_MAP_TEXTURE_SIZE, &max_cube_map_texture_size);
    SET_ERROR_IF(level < 0, GL_INVALID_VALUE);
    SET_ERROR_IF(level > ilog2(max_texture_size), GL_INVALID_VALUE);
    SET_ERROR_IF(GLESv2Validation::isCubeMapTarget(target) &&
                 level > ilog2(max_cube_map_texture_size), GL_INVALID_VALUE);
    SET_ERROR_IF(width < 0 || height < 0, GL_INVALID_VALUE);
    SET_ERROR_IF(xoffset < 0 || yoffset < 0, GL_INVALID_VALUE);

    GLuint tex = state->getBoundTexture(target);
    GLsizei neededWidth = xoffset + width;
    GLsizei neededHeight = yoffset + height;
    GLsizei neededDepth = 1;

    if (tex && !state->queryTexEGLImageBacked(tex)) {
        SET_ERROR_IF(
                (neededWidth > state->queryTexWidth(level, tex) ||
                 neededHeight > state->queryTexHeight(level, tex) ||
                 neededDepth > state->queryTexDepth(level, tex)),
                GL_INVALID_VALUE);
    }

    // If unpack buffer is nonzero, verify buffer data fits and is evenly divisible by the type.
    SET_ERROR_IF(ctx->boundBuffer(GL_PIXEL_UNPACK_BUFFER) &&
                 ctx->getBufferData(GL_PIXEL_UNPACK_BUFFER) &&
                 (state->pboNeededDataSize(width, height, 1, format, type, 0) >
                  ctx->getBufferData(GL_PIXEL_UNPACK_BUFFER)->m_size),
                 GL_INVALID_OPERATION);
    SET_ERROR_IF(ctx->boundBuffer(GL_PIXEL_UNPACK_BUFFER) &&
                 ctx->getBufferData(GL_PIXEL_UNPACK_BUFFER) &&
                 (ctx->getBufferData(GL_PIXEL_UNPACK_BUFFER)->m_size %
                  glSizeof(type)),
                 GL_INVALID_OPERATION);
    SET_ERROR_IF(!ctx->boundBuffer(GL_PIXEL_UNPACK_BUFFER) && !pixels, GL_INVALID_OPERATION);

    if (target == GL_TEXTURE_2D || target == GL_TEXTURE_EXTERNAL_OES) {
        ctx->override2DTextureTarget(target);
    }

    if (ctx->boundBuffer(GL_PIXEL_UNPACK_BUFFER)) {
        ctx->glTexSubImage2DOffsetAEMU(
                ctx, target, level,
                xoffset, yoffset, width, height,
                format, type, (uintptr_t)pixels);
    } else {
        ctx->m_glTexSubImage2D_enc(ctx, target, level, xoffset, yoffset, width,
                height, format, type, pixels);
    }

    if (target == GL_TEXTURE_2D || target == GL_TEXTURE_EXTERNAL_OES) {
        ctx->restore2DTextureTarget(target);
    }
}

void GL2Encoder::s_glCopyTexImage2D(void* self, GLenum target, GLint level,
        GLenum internalformat, GLint x, GLint y,
        GLsizei width, GLsizei height, GLint border)
{
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;

    SET_ERROR_IF(ctx->glCheckFramebufferStatus(ctx, GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE,
                 GL_INVALID_FRAMEBUFFER_OPERATION);
    // This is needed to work around underlying OpenGL drivers
    // (such as those feeding some some AMD GPUs) that expect
    // positive components of cube maps to be defined _before_
    // the negative components (otherwise a segfault occurs).
    GLenum extraTarget =
        state->copyTexImageLuminanceCubeMapAMDWorkaround
            (target, level, internalformat);

    if (extraTarget) {
        ctx->m_glCopyTexImage2D_enc(ctx, extraTarget, level, internalformat,
                                    x, y, width, height, border);
    }

    ctx->m_glCopyTexImage2D_enc(ctx, target, level, internalformat,
                                x, y, width, height, border);

    state->setBoundTextureInternalFormat(target, internalformat);
    state->setBoundTextureDims(target, level, width, height, 1);
}

void GL2Encoder::s_glTexParameteriv(void* self,
        GLenum target, GLenum pname, const GLint* params)
{
    GL2Encoder* ctx = (GL2Encoder*)self;
    const GLClientState* state = ctx->m_state;

    SET_ERROR_IF((target == GL_TEXTURE_EXTERNAL_OES &&
            !isValidTextureExternalParam(pname, (GLenum)params[0])),
            GL_INVALID_ENUM);

    if (target == GL_TEXTURE_2D || target == GL_TEXTURE_EXTERNAL_OES) {
        ctx->override2DTextureTarget(target);
        ctx->m_glTexParameteriv_enc(ctx, GL_TEXTURE_2D, pname, params);
        ctx->restore2DTextureTarget(target);
    } else {
        ctx->m_glTexParameteriv_enc(ctx, target, pname, params);
    }
}

bool GL2Encoder::texture2DNeedsOverride(GLenum target) const {
    return (target == GL_TEXTURE_2D || target == GL_TEXTURE_EXTERNAL_OES) &&
           target != m_state->getPriorityEnabledTarget(GL_TEXTURE_2D);
}

void GL2Encoder::override2DTextureTarget(GLenum target)
{
    if (texture2DNeedsOverride(target)) {
        m_glBindTexture_enc(this, GL_TEXTURE_2D,
                m_state->getBoundTexture(target));
    }
}

void GL2Encoder::restore2DTextureTarget(GLenum target)
{
    if (texture2DNeedsOverride(target)) {
        GLuint priorityEnabledBoundTexture =
                m_state->getBoundTexture(
                    m_state->getPriorityEnabledTarget(GL_TEXTURE_2D));
        GLuint texture2DBoundTexture =
                m_state->getBoundTexture(GL_TEXTURE_2D);
        if (!priorityEnabledBoundTexture) {
            m_glBindTexture_enc(this, GL_TEXTURE_2D, texture2DBoundTexture);
        } else {
            m_glBindTexture_enc(this, GL_TEXTURE_2D, priorityEnabledBoundTexture);
        }
    }
}

void GL2Encoder::associateEGLImage(GLenum target, GLeglImageOES eglImage) {
    m_state->setBoundEGLImage(target, eglImage);
}


GLuint GL2Encoder::boundBuffer(GLenum target) const {
    return m_state->getBuffer(target);
}

BufferData* GL2Encoder::getBufferData(GLenum target) const {
    GLuint bufferId = m_state->getBuffer(target);
    if (!bufferId) return NULL;
    return m_shared->getBufferData(bufferId);
}

BufferData* GL2Encoder::getBufferDataById(GLuint bufferId) const {
    if (!bufferId) return NULL;
    return m_shared->getBufferData(bufferId);
}

bool GL2Encoder::isBufferMapped(GLuint buffer) const {
    return m_shared->getBufferData(buffer)->m_mapped;
}

bool GL2Encoder::isBufferTargetMapped(GLenum target) const {
    BufferData* buf = getBufferData(target);
    if (!buf) return false;
    return buf->m_mapped;
}

void GL2Encoder::s_glGenRenderbuffers(void* self,
        GLsizei n, GLuint* renderbuffers) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;

    SET_ERROR_IF(n < 0, GL_INVALID_VALUE);

    ctx->m_glGenFramebuffers_enc(self, n, renderbuffers);
    state->addRenderbuffers(n, renderbuffers);
}

void GL2Encoder::s_glDeleteRenderbuffers(void* self,
        GLsizei n, const GLuint* renderbuffers) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;

    SET_ERROR_IF(n < 0, GL_INVALID_VALUE);

    ctx->m_glDeleteRenderbuffers_enc(self, n, renderbuffers);

    // Nope, lets just leak those for now.
    // The spec has an *amazingly* convoluted set of conditions for when
    // render buffers are actually deleted:
    // glDeleteRenderbuffers deletes the n renderbuffer objects whose names are stored in the array addressed by renderbuffers. Unused names in renderbuffers that have been marked as used for the purposes of glGenRenderbuffers are marked as unused again. The name zero is reserved by the GL and is silently ignored, should it occur in renderbuffers, as are other unused names. Once a renderbuffer object is deleted, its name is again unused and it has no contents. If a renderbuffer that is currently bound to the target GL_RENDERBUFFER is deleted, it is as though glBindRenderbuffer had been executed with a target of GL_RENDERBUFFER and a name of zero.
    //
    // If a renderbuffer object is attached to one or more attachment points in the currently bound framebuffer, then it as if glFramebufferRenderbuffer had been called, with a renderbuffer of zero for each attachment point to which this image was attached in the currently bound framebuffer. In other words, this renderbuffer object is first detached from all attachment ponits in the currently bound framebuffer. ***Note that the renderbuffer image is specifically not detached from any non-bound framebuffers***
    //
    // So, just detach this one from the bound FBO, and ignore the rest.
    for (int i = 0; i < n; i++) {
        state->detachRbo(renderbuffers[i]);
    }
    // state->removeRenderbuffers(n, renderbuffers);
}

void GL2Encoder::s_glBindRenderbuffer(void* self,
        GLenum target, GLuint renderbuffer) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;

    SET_ERROR_IF((target != GL_RENDERBUFFER),
                 GL_INVALID_ENUM);

    ctx->m_glBindRenderbuffer_enc(self, target, renderbuffer);
    state->bindRenderbuffer(target, renderbuffer);
}

void GL2Encoder::s_glRenderbufferStorage(void* self,
        GLenum target, GLenum internalformat,
        GLsizei width, GLsizei height) {
    GL2Encoder* ctx = (GL2Encoder*) self;
    GLClientState* state = ctx->m_state;

    SET_ERROR_IF(target != GL_RENDERBUFFER, GL_INVALID_ENUM);
    SET_ERROR_IF(
        !GLESv2Validation::rboFormat(ctx, internalformat),
        GL_INVALID_ENUM);

    state->setBoundRenderbufferFormat(internalformat);
    state->setBoundRenderbufferSamples(0);

    ctx->m_glRenderbufferStorage_enc(self, target, internalformat,
                                     width, height);
}

void GL2Encoder::s_glFramebufferRenderbuffer(void* self,
        GLenum target, GLenum attachment,
        GLenum renderbuffertarget, GLuint renderbuffer) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;

    SET_ERROR_IF(!GLESv2Validation::framebufferTarget(ctx, target), GL_INVALID_ENUM);
    SET_ERROR_IF(!GLESv2Validation::framebufferAttachment(ctx, attachment), GL_INVALID_ENUM);
    state->attachRbo(target, attachment, renderbuffer);

    ctx->m_glFramebufferRenderbuffer_enc(self, target, attachment, renderbuffertarget, renderbuffer);
}

void GL2Encoder::s_glGenFramebuffers(void* self,
        GLsizei n, GLuint* framebuffers) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;

    SET_ERROR_IF(n < 0, GL_INVALID_VALUE);

    ctx->m_glGenFramebuffers_enc(self, n, framebuffers);
    state->addFramebuffers(n, framebuffers);
}

void GL2Encoder::s_glDeleteFramebuffers(void* self,
        GLsizei n, const GLuint* framebuffers) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;

    SET_ERROR_IF(n < 0, GL_INVALID_VALUE);

    ctx->m_glDeleteFramebuffers_enc(self, n, framebuffers);
    state->removeFramebuffers(n, framebuffers);
}

void GL2Encoder::s_glBindFramebuffer(void* self,
        GLenum target, GLuint framebuffer) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;

    SET_ERROR_IF(!GLESv2Validation::framebufferTarget(ctx, target), GL_INVALID_ENUM);

    state->bindFramebuffer(target, framebuffer);

    ctx->m_glBindFramebuffer_enc(self, target, framebuffer);
}

void GL2Encoder::s_glFramebufferTexture2D(void* self,
        GLenum target, GLenum attachment,
        GLenum textarget, GLuint texture, GLint level) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;

    SET_ERROR_IF(!GLESv2Validation::framebufferTarget(ctx, target), GL_INVALID_ENUM);
    SET_ERROR_IF(!GLESv2Validation::framebufferAttachment(ctx, attachment), GL_INVALID_ENUM);
    state->attachTextureObject(target, attachment, texture);

    ctx->m_glFramebufferTexture2D_enc(self, target, attachment, textarget, texture, level);
}

void GL2Encoder::s_glFramebufferTexture3DOES(void* self,
        GLenum target, GLenum attachment,
        GLenum textarget, GLuint texture, GLint level, GLint zoffset) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;

    state->attachTextureObject(target, attachment, texture);

    ctx->m_glFramebufferTexture3DOES_enc(self, target, attachment, textarget, texture, level, zoffset);
}

void GL2Encoder::s_glGetFramebufferAttachmentParameteriv(void* self,
        GLenum target, GLenum attachment, GLenum pname, GLint* params) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    const GLClientState* state = ctx->m_state;
    SET_ERROR_IF(!GLESv2Validation::framebufferTarget(ctx, target), GL_INVALID_ENUM);
    SET_ERROR_IF(pname != GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME &&
                 pname != GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE &&
                 !state->attachmentHasObject(target, attachment),
                 GL_INVALID_OPERATION);
    SET_ERROR_IF((pname == GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL ||
                  pname == GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_CUBE_MAP_FACE ||
                  pname == GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LAYER) &&
                 (!state->attachmentHasObject(target, attachment) ||
                  state->getBoundFramebufferAttachmentType(target, attachment) !=
                  FBO_ATTACHMENT_TEXTURE),
                 !state->attachmentHasObject(target, attachment) ?
                 GL_INVALID_OPERATION : GL_INVALID_ENUM);
    SET_ERROR_IF(attachment == GL_DEPTH_STENCIL_ATTACHMENT &&
                 pname == GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME &&
                 (state->objectOfAttachment(target, GL_DEPTH_ATTACHMENT) !=
                  state->objectOfAttachment(target, GL_STENCIL_ATTACHMENT)),
                 GL_INVALID_OPERATION);
    SET_ERROR_IF(state->boundFramebuffer(target) &&
                 (attachment == GL_BACK ||
                  attachment == GL_FRONT),
                 GL_INVALID_OPERATION);
    ctx->m_glGetFramebufferAttachmentParameteriv_enc(self, target, attachment, pname, params);
}

bool GL2Encoder::isCompleteFbo(GLenum target, const GLClientState* state,
                               GLenum attachment) const {
    FboFormatInfo fbo_format_info;
    state->getBoundFramebufferFormat(target, attachment, &fbo_format_info);

    bool res;
    switch (fbo_format_info.type) {
    case FBO_ATTACHMENT_RENDERBUFFER:
        switch (fbo_format_info.rb_format) {
        case GL_R16F:
        case GL_RG16F:
        case GL_RGBA16F:
        case GL_R32F:
        case GL_RG32F:
        case GL_RGBA32F:
        case GL_R11F_G11F_B10F:
            res = majorVersion() >= 3 && hasExtension("GL_EXT_color_buffer_float");
            break;
        case GL_RGB16F:
            res = majorVersion() >= 3 && hasExtension("GL_EXT_color_buffer_half_float");
            break;
        case GL_STENCIL_INDEX8:
            if (attachment == GL_STENCIL_ATTACHMENT) {
                res = true;
            } else {
                res = false;
            }
            break;
        default:
            res = true;
        }
        break;
    case FBO_ATTACHMENT_TEXTURE:
        switch (fbo_format_info.tex_internalformat) {
        case GL_R16F:
        case GL_RG16F:
        case GL_RGBA16F:
        case GL_R32F:
        case GL_RG32F:
        case GL_RGBA32F:
        case GL_R11F_G11F_B10F:
            res = majorVersion() >= 3 && hasExtension("GL_EXT_color_buffer_float");
            break;
        case GL_RGB16F:
            res = majorVersion() >= 3 && hasExtension("GL_EXT_color_buffer_half_float");
            break;
        case GL_RED:
        case GL_RG:
        case GL_SRGB8:
        case GL_RGB32UI:
        case GL_RGB16UI:
        case GL_RGB8UI:
        case GL_RGB32I:
        case GL_RGB16I:
        case GL_RGB8I:
        case GL_R8_SNORM:
        case GL_RG8_SNORM:
        case GL_RGB8_SNORM:
        case GL_RGBA8_SNORM:
            res = false;
            break;
        // No float/half-float formats allowed for RGB(A)
        case GL_RGB:
        case GL_RGBA:
            switch (fbo_format_info.tex_type) {
            case GL_FLOAT:
            case GL_HALF_FLOAT_OES:
            case GL_UNSIGNED_INT_10F_11F_11F_REV:
            case GL_UNSIGNED_INT_2_10_10_10_REV:
                res = false;
                break;
            default:
                res = true;
            }
            break;
        default:
            res = true;
        }
        break;
    case FBO_ATTACHMENT_NONE:
        res = true;
        break;
    default:
        res = true;
    }
    return res;
}

bool GL2Encoder::checkFramebufferCompleteness(GLenum target, const GLClientState* state) const {
    bool res = true;

    for (int i = 0; i < state->getMaxColorAttachments(); i++) {
        res = res && isCompleteFbo(target, state, glUtilsColorAttachmentName(i));
    }

    res = res && isCompleteFbo(target, state, GL_DEPTH_ATTACHMENT);
    res = res && isCompleteFbo(target, state, GL_STENCIL_ATTACHMENT);

    return res;
}

GLenum GL2Encoder::s_glCheckFramebufferStatus(void* self, GLenum target) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;

    bool fboCompleteByCodec =
        ctx->checkFramebufferCompleteness(target, state);

    if (!fboCompleteByCodec) {
        state->setCheckFramebufferStatus(target, GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT);
        return GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT;
    } else {
        // double check with underlying opengl to avoid craziness.
        GLenum host_checkstatus = ctx->m_glCheckFramebufferStatus_enc(self, target);
        state->setCheckFramebufferStatus(target, host_checkstatus);
        if (host_checkstatus == GL_FRAMEBUFFER_INCOMPLETE_DIMENSIONS) return GL_FRAMEBUFFER_COMPLETE;
        return host_checkstatus;
    }
}

void GL2Encoder::s_glGenVertexArrays(void* self, GLsizei n, GLuint* arrays) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;
    SET_ERROR_IF(n < 0, GL_INVALID_VALUE);

    ctx->m_glGenVertexArrays_enc(self, n, arrays);
    for (int i = 0; i < n; i++) {
        ALOGV("%s: gen vao %u", __FUNCTION__, arrays[i]);
    }
    state->addVertexArrayObjects(n, arrays);
}

void GL2Encoder::s_glDeleteVertexArrays(void* self, GLsizei n, const GLuint* arrays) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;
    SET_ERROR_IF(n < 0, GL_INVALID_VALUE);

    ctx->m_glDeleteVertexArrays_enc(self, n, arrays);
    for (int i = 0; i < n; i++) {
        ALOGV("%s: delete vao %u", __FUNCTION__, arrays[i]);
    }
    state->removeVertexArrayObjects(n, arrays);
}

void GL2Encoder::s_glBindVertexArray(void* self, GLuint array) {
    ALOGV("%s: call. array=%u\n", __FUNCTION__, array);
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;
    SET_ERROR_IF(!state->isVertexArrayObject(array), GL_INVALID_OPERATION);
    ctx->m_glBindVertexArray_enc(self, array);
    state->setVertexArrayObject(array);
}

void* GL2Encoder::s_glMapBufferRange(void* self, GLenum target, GLintptr offset, GLsizeiptr length, GLbitfield access) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;

    // begin validation (lots)
    
    RET_AND_SET_ERROR_IF(!GLESv2Validation::bufferTarget(ctx, target), GL_INVALID_ENUM, NULL);

    GLuint boundBuffer = ctx->m_state->getBuffer(target);

    RET_AND_SET_ERROR_IF(boundBuffer == 0, GL_INVALID_OPERATION, NULL);

    BufferData* buf = ctx->m_shared->getBufferData(boundBuffer);
    RET_AND_SET_ERROR_IF(!buf, GL_INVALID_VALUE, NULL);

    GLsizeiptr bufferDataSize = buf->m_size;

    RET_AND_SET_ERROR_IF(offset < 0, GL_INVALID_VALUE, NULL);
    RET_AND_SET_ERROR_IF(length < 0, GL_INVALID_VALUE, NULL);
    RET_AND_SET_ERROR_IF(offset + length > bufferDataSize, GL_INVALID_VALUE, NULL);
    RET_AND_SET_ERROR_IF(access & ~GLESv2Validation::allBufferMapAccessFlags, GL_INVALID_VALUE, NULL);

    RET_AND_SET_ERROR_IF(buf->m_mapped, GL_INVALID_OPERATION, NULL);
    RET_AND_SET_ERROR_IF(!(access & (GL_MAP_READ_BIT | GL_MAP_WRITE_BIT)), GL_INVALID_OPERATION, NULL);
    RET_AND_SET_ERROR_IF(
        (access & GL_MAP_READ_BIT) &&
             ((access & GL_MAP_INVALIDATE_RANGE_BIT) ||
              (access & GL_MAP_INVALIDATE_BUFFER_BIT) ||
              (access & GL_MAP_UNSYNCHRONIZED_BIT) ||
              (access & GL_MAP_FLUSH_EXPLICIT_BIT)), GL_INVALID_OPERATION, NULL);

    // end validation; actually do stuff now
   
    buf->m_mapped = true;
    buf->m_mappedAccess = access;
    buf->m_mappedOffset = offset;
    buf->m_mappedLength = length;

    char* todo = (char*)buf->m_fixedBuffer.ptr() + offset;
    ctx->glMapBufferRangeAEMU(
            ctx, target,
            offset, length,
            access,
            todo);

    return todo;
}

GLboolean GL2Encoder::s_glUnmapBuffer(void* self, GLenum target) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;

    RET_AND_SET_ERROR_IF(!GLESv2Validation::bufferTarget(ctx, target), GL_INVALID_ENUM, GL_FALSE);

    GLuint boundBuffer = ctx->m_state->getBuffer(target);

    RET_AND_SET_ERROR_IF(boundBuffer == 0, GL_INVALID_OPERATION, GL_FALSE);

    BufferData* buf = ctx->m_shared->getBufferData(boundBuffer);
    RET_AND_SET_ERROR_IF(!buf, GL_INVALID_VALUE, GL_FALSE);
    RET_AND_SET_ERROR_IF(!buf->m_mapped, GL_INVALID_OPERATION, GL_FALSE);

    if (buf->m_mappedAccess & GL_MAP_WRITE_BIT) {
        // invalide index range cache here
        if (buf->m_mappedAccess & GL_MAP_INVALIDATE_BUFFER_BIT) {
            buf->m_indexRangeCache.invalidateRange(0, buf->m_size);
        } else {
            buf->m_indexRangeCache.invalidateRange(buf->m_mappedOffset, buf->m_mappedLength);
        }
    }

    GLboolean host_res = GL_TRUE;

    ctx->glUnmapBufferAEMU(
            ctx, target,
            buf->m_mappedOffset,
            buf->m_mappedLength,
            buf->m_mappedAccess,
            (void*)((char*)buf->m_fixedBuffer.ptr() + buf->m_mappedOffset),
            &host_res);

    buf->m_mapped = false;
    buf->m_mappedAccess = 0;
    buf->m_mappedOffset = 0;
    buf->m_mappedLength = 0;

    return host_res;
}

void GL2Encoder::s_glFlushMappedBufferRange(void* self, GLenum target, GLintptr offset, GLsizeiptr length) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;

    SET_ERROR_IF(!GLESv2Validation::bufferTarget(ctx, target), GL_INVALID_ENUM);

    GLuint boundBuffer = ctx->m_state->getBuffer(target);
    SET_ERROR_IF(!boundBuffer, GL_INVALID_OPERATION);

    BufferData* buf = ctx->m_shared->getBufferData(boundBuffer);
    SET_ERROR_IF(!buf, GL_INVALID_VALUE);
    SET_ERROR_IF(!buf->m_mapped, GL_INVALID_OPERATION);
    SET_ERROR_IF(!(buf->m_mappedAccess & GL_MAP_FLUSH_EXPLICIT_BIT), GL_INVALID_OPERATION);

    SET_ERROR_IF(offset < 0, GL_INVALID_VALUE);
    SET_ERROR_IF(length < 0, GL_INVALID_VALUE);
    SET_ERROR_IF(offset + length > buf->m_mappedLength, GL_INVALID_VALUE);

    GLintptr totalOffset = buf->m_mappedOffset + offset;

    buf->m_indexRangeCache.invalidateRange(totalOffset, length);

    ctx->glFlushMappedBufferRangeAEMU(
            ctx, target,
            totalOffset,
            length,
            buf->m_mappedAccess,
            (void*)((char*)buf->m_fixedBuffer.ptr() + totalOffset));
}

void GL2Encoder::s_glCompressedTexImage2D(void* self, GLenum target, GLint level, GLenum internalformat, GLsizei width, GLsizei height, GLint border, GLsizei imageSize, const GLvoid* data) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;

    SET_ERROR_IF(!GLESv2Validation::textureTarget(ctx, target), GL_INVALID_ENUM);
    // Filter compressed formats support.
    SET_ERROR_IF(!GLESv2Validation::supportedCompressedFormat(ctx, internalformat), GL_INVALID_ENUM);
    // Verify level <= log2(GL_MAX_TEXTURE_SIZE).
    GLint max_texture_size;
    GLint max_cube_map_texture_size;
    ctx->glGetIntegerv(ctx, GL_MAX_TEXTURE_SIZE, &max_texture_size);
    ctx->glGetIntegerv(ctx, GL_MAX_CUBE_MAP_TEXTURE_SIZE, &max_cube_map_texture_size);
    SET_ERROR_IF(level < 0, GL_INVALID_VALUE);
    SET_ERROR_IF(level > ilog2(max_texture_size), GL_INVALID_VALUE);
    SET_ERROR_IF(level > ilog2(max_cube_map_texture_size), GL_INVALID_VALUE);
    SET_ERROR_IF(width > max_texture_size, GL_INVALID_VALUE);
    SET_ERROR_IF(height > max_texture_size, GL_INVALID_VALUE);
    SET_ERROR_IF(border, GL_INVALID_VALUE);
    // If unpack buffer is nonzero, verify unmapped state.
    SET_ERROR_IF(ctx->isBufferTargetMapped(GL_PIXEL_UNPACK_BUFFER), GL_INVALID_OPERATION);
    SET_ERROR_IF(width < 0 || height < 0, GL_INVALID_VALUE);
    // If unpack buffer is nonzero, verify buffer data fits.
    SET_ERROR_IF(ctx->boundBuffer(GL_PIXEL_UNPACK_BUFFER) &&
                 ctx->getBufferData(GL_PIXEL_UNPACK_BUFFER) &&
                 (imageSize > ctx->getBufferData(GL_PIXEL_UNPACK_BUFFER)->m_size),
                 GL_INVALID_OPERATION);
    // TODO: Fix:
    // If |imageSize| is inconsistent with compressed dimensions.
    // SET_ERROR_IF(GLESv2Validation::compressedTexImageSize(internalformat, width, height, 1) != imageSize, GL_INVALID_VALUE);

    GLenum stateTarget = target;
    if (target == GL_TEXTURE_CUBE_MAP_POSITIVE_X ||
        target == GL_TEXTURE_CUBE_MAP_POSITIVE_Y ||
        target == GL_TEXTURE_CUBE_MAP_POSITIVE_Z ||
        target == GL_TEXTURE_CUBE_MAP_NEGATIVE_X ||
        target == GL_TEXTURE_CUBE_MAP_NEGATIVE_Y ||
        target == GL_TEXTURE_CUBE_MAP_NEGATIVE_Z)
        stateTarget = GL_TEXTURE_CUBE_MAP;
    state->setBoundTextureInternalFormat(stateTarget, (GLint)internalformat);
    state->setBoundTextureDims(stateTarget, level, width, height, 1);

    if (ctx->boundBuffer(GL_PIXEL_UNPACK_BUFFER)) {
        ctx->glCompressedTexImage2DOffsetAEMU(
                ctx, target, level, internalformat,
                width, height, border,
                imageSize, (uintptr_t)data);
    } else {
        ctx->m_glCompressedTexImage2D_enc(
                ctx, target, level, internalformat,
                width, height, border,
                imageSize, data);
    }
}

void GL2Encoder::s_glCompressedTexSubImage2D(void* self, GLenum target, GLint level, GLint xoffset, GLint yoffset, GLsizei width, GLsizei height, GLenum format, GLsizei imageSize, const GLvoid* data) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;

    SET_ERROR_IF(!GLESv2Validation::textureTarget(ctx, target), GL_INVALID_ENUM);
    // If unpack buffer is nonzero, verify unmapped state.
    SET_ERROR_IF(ctx->isBufferTargetMapped(GL_PIXEL_UNPACK_BUFFER), GL_INVALID_OPERATION);
    GLint max_texture_size;
    GLint max_cube_map_texture_size;
    ctx->glGetIntegerv(ctx, GL_MAX_TEXTURE_SIZE, &max_texture_size);
    ctx->glGetIntegerv(ctx, GL_MAX_CUBE_MAP_TEXTURE_SIZE, &max_cube_map_texture_size);
    SET_ERROR_IF(level < 0, GL_INVALID_VALUE);
    SET_ERROR_IF(level > ilog2(max_texture_size), GL_INVALID_VALUE);
    SET_ERROR_IF(level > ilog2(max_cube_map_texture_size), GL_INVALID_VALUE);
    SET_ERROR_IF(width < 0 || height < 0, GL_INVALID_VALUE);
    // If unpack buffer is nonzero, verify buffer data fits.
    SET_ERROR_IF(ctx->boundBuffer(GL_PIXEL_UNPACK_BUFFER) &&
                 ctx->getBufferData(GL_PIXEL_UNPACK_BUFFER) &&
                 (imageSize > ctx->getBufferData(GL_PIXEL_UNPACK_BUFFER)->m_size),
                 GL_INVALID_OPERATION);
    SET_ERROR_IF(xoffset < 0 || yoffset < 0, GL_INVALID_VALUE);

    if (ctx->boundBuffer(GL_PIXEL_UNPACK_BUFFER)) {
        ctx->glCompressedTexSubImage2DOffsetAEMU(
                ctx, target, level,
                xoffset, yoffset,
                width, height, format,
                imageSize, (uintptr_t)data);
    } else {
        ctx->m_glCompressedTexSubImage2D_enc(
                ctx, target, level,
                xoffset, yoffset,
                width, height, format,
                imageSize, data);
    }
}

void GL2Encoder::s_glBindBufferRange(void* self, GLenum target, GLuint index, GLuint buffer, GLintptr offset, GLsizeiptr size) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;

    SET_ERROR_IF(!GLESv2Validation::bufferTarget(ctx, target), GL_INVALID_ENUM);

    // Only works with certain targets
    SET_ERROR_IF(
        !(target == GL_ATOMIC_COUNTER_BUFFER ||
          target == GL_SHADER_STORAGE_BUFFER ||
          target == GL_TRANSFORM_FEEDBACK_BUFFER ||
          target == GL_UNIFORM_BUFFER),
        GL_INVALID_ENUM);

    // Can't exceed range
    SET_ERROR_IF(index < 0 ||
                 index >= state->getMaxIndexedBufferBindings(target),
                 GL_INVALID_VALUE);
    SET_ERROR_IF(buffer && size <= 0, GL_INVALID_VALUE);
    SET_ERROR_IF((target == GL_ATOMIC_COUNTER_BUFFER ||
                  target == GL_TRANSFORM_FEEDBACK_BUFFER) &&
                 (size % 4 || offset % 4),
                 GL_INVALID_VALUE);

    GLint ssbo_offset_align, ubo_offset_align;
    ctx->s_glGetIntegerv(ctx, GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT, &ssbo_offset_align);
    ctx->s_glGetIntegerv(ctx, GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT, &ubo_offset_align);
    SET_ERROR_IF(target == GL_SHADER_STORAGE_BUFFER &&
                 offset % ssbo_offset_align,
                 GL_INVALID_VALUE);
    SET_ERROR_IF(target == GL_UNIFORM_BUFFER &&
                 offset % ubo_offset_align,
                 GL_INVALID_VALUE);

    state->bindBuffer(target, buffer);
    ctx->m_state->addBuffer(buffer);
    state->bindIndexedBuffer(target, index, buffer, offset, size, 0, 0);
    ctx->m_glBindBufferRange_enc(self, target, index, buffer, offset, size);
}

void GL2Encoder::s_glBindBufferBase(void* self, GLenum target, GLuint index, GLuint buffer) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;

    SET_ERROR_IF(!GLESv2Validation::bufferTarget(ctx, target), GL_INVALID_ENUM);

    // Only works with certain targets
    SET_ERROR_IF(
        !(target == GL_ATOMIC_COUNTER_BUFFER ||
          target == GL_SHADER_STORAGE_BUFFER ||
          target == GL_TRANSFORM_FEEDBACK_BUFFER ||
          target == GL_UNIFORM_BUFFER),
        GL_INVALID_ENUM);
    // Can't exceed range
    SET_ERROR_IF(index < 0 ||
                 index >= state->getMaxIndexedBufferBindings(target),
                 GL_INVALID_VALUE);

    state->bindBuffer(target, buffer);
    ctx->m_state->addBuffer(buffer);
    BufferData* buf = ctx->getBufferDataById(buffer);
    state->bindIndexedBuffer(target, index, buffer, 0, buf ? buf->m_size : 0, 0, 0);
    ctx->m_glBindBufferBase_enc(self, target, index, buffer);
}

void GL2Encoder::s_glCopyBufferSubData(void *self , GLenum readtarget, GLenum writetarget, GLintptr readoffset, GLintptr writeoffset, GLsizeiptr size) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;

    SET_ERROR_IF(!GLESv2Validation::bufferTarget(ctx, readtarget), GL_INVALID_ENUM);
    SET_ERROR_IF(!GLESv2Validation::bufferTarget(ctx, writetarget), GL_INVALID_ENUM);
    SET_ERROR_IF((readtarget == GL_ATOMIC_COUNTER_BUFFER ||
                  readtarget == GL_DISPATCH_INDIRECT_BUFFER ||
                  readtarget == GL_DRAW_INDIRECT_BUFFER ||
                  readtarget == GL_SHADER_STORAGE_BUFFER), GL_INVALID_ENUM);
    SET_ERROR_IF((writetarget == GL_ATOMIC_COUNTER_BUFFER ||
                  writetarget == GL_DISPATCH_INDIRECT_BUFFER ||
                  writetarget == GL_DRAW_INDIRECT_BUFFER ||
                  writetarget == GL_SHADER_STORAGE_BUFFER), GL_INVALID_ENUM);
    SET_ERROR_IF(!ctx->boundBuffer(readtarget), GL_INVALID_OPERATION);
    SET_ERROR_IF(!ctx->boundBuffer(writetarget), GL_INVALID_OPERATION);
    SET_ERROR_IF(ctx->isBufferTargetMapped(readtarget), GL_INVALID_OPERATION);
    SET_ERROR_IF(ctx->isBufferTargetMapped(writetarget), GL_INVALID_OPERATION);
    SET_ERROR_IF(readoffset < 0, GL_INVALID_VALUE);
    SET_ERROR_IF(writeoffset < 0, GL_INVALID_VALUE);
    SET_ERROR_IF(size < 0, GL_INVALID_VALUE);
    SET_ERROR_IF(
        ctx->getBufferData(readtarget) &&
        (readoffset + size > ctx->getBufferData(readtarget)->m_size),
        GL_INVALID_VALUE);
    SET_ERROR_IF(
        ctx->getBufferData(writetarget) &&
        (writeoffset + size > ctx->getBufferData(writetarget)->m_size),
        GL_INVALID_VALUE);
    SET_ERROR_IF(readtarget == writetarget &&
                 !((writeoffset >= readoffset + size) ||
                   (readoffset >= writeoffset + size)),
                 GL_INVALID_VALUE);

    ctx->m_glCopyBufferSubData_enc(self, readtarget, writetarget, readoffset, writeoffset, size);
}

void GL2Encoder::s_glGetBufferParameteriv(void* self, GLenum target, GLenum pname, GLint* params) {
    GL2Encoder* ctx = (GL2Encoder*)self;

    SET_ERROR_IF(!GLESv2Validation::bufferTarget(ctx, target), GL_INVALID_ENUM);
    SET_ERROR_IF(
        target != GL_ARRAY_BUFFER &&
        target != GL_ELEMENT_ARRAY_BUFFER &&
        target != GL_COPY_READ_BUFFER &&
        target != GL_COPY_WRITE_BUFFER &&
        target != GL_PIXEL_PACK_BUFFER &&
        target != GL_PIXEL_UNPACK_BUFFER &&
        target != GL_TRANSFORM_FEEDBACK_BUFFER &&
        target != GL_UNIFORM_BUFFER,
        GL_INVALID_ENUM);
    SET_ERROR_IF(!GLESv2Validation::bufferParam(ctx, pname), GL_INVALID_ENUM);
    SET_ERROR_IF(!ctx->boundBuffer(target), GL_INVALID_OPERATION);
    SET_ERROR_IF(pname != GL_BUFFER_ACCESS_FLAGS &&
                 pname != GL_BUFFER_MAPPED &&
                 pname != GL_BUFFER_SIZE &&
                 pname != GL_BUFFER_USAGE &&
                 pname != GL_BUFFER_MAP_LENGTH &&
                 pname != GL_BUFFER_MAP_OFFSET,
                 GL_INVALID_ENUM);

    if (!params) return;

    BufferData* buf = ctx->getBufferData(target);

    switch (pname) {
        case GL_BUFFER_ACCESS_FLAGS:
            *params = buf ? buf->m_mappedAccess : 0;
            break;
        case GL_BUFFER_MAPPED:
            *params = buf ? (buf->m_mapped ? GL_TRUE : GL_FALSE) : GL_FALSE;
            break;
        case GL_BUFFER_SIZE:
            *params = buf ? buf->m_size : 0;
            break;
        case GL_BUFFER_USAGE:
            *params = buf ? buf->m_usage : GL_STATIC_DRAW;
            break;
        case GL_BUFFER_MAP_LENGTH:
            *params = buf ? buf->m_mappedLength : 0;
            break;
        case GL_BUFFER_MAP_OFFSET:
            *params = buf ? buf->m_mappedOffset : 0;
            break;
        default:
            break;
    }
}

void GL2Encoder::s_glGetBufferParameteri64v(void* self, GLenum target, GLenum pname, GLint64* params) {
    GL2Encoder* ctx = (GL2Encoder*)self;

    SET_ERROR_IF(!GLESv2Validation::bufferTarget(ctx, target), GL_INVALID_ENUM);
    SET_ERROR_IF(
        target != GL_ARRAY_BUFFER &&
        target != GL_ELEMENT_ARRAY_BUFFER &&
        target != GL_COPY_READ_BUFFER &&
        target != GL_COPY_WRITE_BUFFER &&
        target != GL_PIXEL_PACK_BUFFER &&
        target != GL_PIXEL_UNPACK_BUFFER &&
        target != GL_TRANSFORM_FEEDBACK_BUFFER &&
        target != GL_UNIFORM_BUFFER,
        GL_INVALID_ENUM);
    SET_ERROR_IF(!GLESv2Validation::bufferParam(ctx, pname), GL_INVALID_ENUM);
    SET_ERROR_IF(!ctx->boundBuffer(target), GL_INVALID_OPERATION);
    SET_ERROR_IF(pname != GL_BUFFER_ACCESS_FLAGS &&
                 pname != GL_BUFFER_MAPPED &&
                 pname != GL_BUFFER_SIZE &&
                 pname != GL_BUFFER_USAGE &&
                 pname != GL_BUFFER_MAP_LENGTH &&
                 pname != GL_BUFFER_MAP_OFFSET,
                 GL_INVALID_ENUM);

    if (!params) return;

    BufferData* buf = ctx->getBufferData(target);

    switch (pname) {
        case GL_BUFFER_ACCESS_FLAGS:
            *params = buf ? buf->m_mappedAccess : 0;
            break;
        case GL_BUFFER_MAPPED:
            *params = buf ? (buf->m_mapped ? GL_TRUE : GL_FALSE) : GL_FALSE;
            break;
        case GL_BUFFER_SIZE:
            *params = buf ? buf->m_size : 0;
            break;
        case GL_BUFFER_USAGE:
            *params = buf ? buf->m_usage : GL_STATIC_DRAW;
            break;
        case GL_BUFFER_MAP_LENGTH:
            *params = buf ? buf->m_mappedLength : 0;
            break;
        case GL_BUFFER_MAP_OFFSET:
            *params = buf ? buf->m_mappedOffset : 0;
            break;
        default:
            break;
    }
}

void GL2Encoder::s_glGetBufferPointerv(void* self, GLenum target, GLenum pname, GLvoid** params) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    SET_ERROR_IF(!GLESv2Validation::bufferTarget(ctx, target), GL_INVALID_ENUM);
    SET_ERROR_IF(
        target == GL_ATOMIC_COUNTER_BUFFER ||
        target == GL_DISPATCH_INDIRECT_BUFFER ||
        target == GL_DRAW_INDIRECT_BUFFER ||
        target == GL_SHADER_STORAGE_BUFFER,
        GL_INVALID_ENUM);
    SET_ERROR_IF(pname != GL_BUFFER_MAP_POINTER, GL_INVALID_ENUM);
    SET_ERROR_IF(!ctx->boundBuffer(target), GL_INVALID_OPERATION);
    if (!params) return;

    BufferData* buf = ctx->getBufferData(target);

    if (!buf || !buf->m_mapped) { *params = NULL; return; }

    *params = (GLvoid*)((char*)buf->m_fixedBuffer.ptr() + buf->m_mappedOffset);
}

static const char* const kNameDelimiter = ";";

static std::string packVarNames(GLsizei count, const char** names, GLint* err_out) {

#define VALIDATE(cond, err) if (cond) { *err_out = err; return packed; } \

    std::string packed;
    // validate the array of char[]'s
    const char* currName;
    for (GLsizei i = 0; i < count; i++) {
        currName = names[i];
        VALIDATE(!currName, GL_INVALID_OPERATION);
        // check if has reasonable size
        size_t len = strlen(currName);
        VALIDATE(!len, GL_INVALID_OPERATION);
        // check for our delimiter, which if present
        // in the name, means an invalid name anyway.
        VALIDATE(strstr(currName, kNameDelimiter),
                 GL_INVALID_OPERATION);
        packed += currName;
        packed += ";";
    }

    *err_out = GL_NO_ERROR;
    return packed;
}

void GL2Encoder::s_glGetUniformIndices(void* self, GLuint program, GLsizei uniformCount, const GLchar ** uniformNames, GLuint* uniformIndices) {
    GL2Encoder* ctx = (GL2Encoder*)self;

    if (!uniformCount) return;

    GLint err = GL_NO_ERROR;
    std::string packed = packVarNames(uniformCount, (const char**)uniformNames, &err);
    SET_ERROR_IF(err != GL_NO_ERROR, GL_INVALID_OPERATION);

    bool needLocationWAR = ctx->m_shared->needUniformLocationWAR(program);
    std::vector<int> arrIndices;
    for (size_t i = 0; i < uniformCount; i++) {
        int err;
        arrIndices.push_back(sArrIndexOfUniformExpr(uniformNames[i], &err));
        if (err) {
            ALOGE("%s: invalid uniform name %s!", __FUNCTION__, uniformNames[i]);
            return;
        }
    }

    ctx->glGetUniformIndicesAEMU(ctx, program, uniformCount, (const GLchar*)&packed[0], packed.size() + 1, uniformIndices);

    for (int i = 0; i < uniformCount; i++) {
        if (uniformIndices[i] >= 0 && needLocationWAR) {
            uniformIndices[i] =
                ctx->m_shared->locationWARHostToApp(program, uniformIndices[i], arrIndices[i]);
        }
    }
}

void GL2Encoder::s_glUniform1ui(void* self, GLint location, GLuint v0) {
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;
    GLSharedGroupPtr shared = ctx->m_shared;

    GLint hostLoc = ctx->m_shared->locationWARAppToHost(ctx->m_state->currentShaderProgram(),location);
    ctx->m_glUniform1ui_enc(self, hostLoc, v0);

    GLenum target;
    if (shared->setSamplerUniform(state->currentShaderProgram(), location, v0, &target)) {
        GLenum origActiveTexture = state->getActiveTextureUnit();
        if (ctx->updateHostTexture2DBinding(GL_TEXTURE0 + v0, target)) {
            ctx->m_glActiveTexture_enc(self, origActiveTexture);
        }
        state->setActiveTextureUnit(origActiveTexture);
    }
}

void GL2Encoder::s_glUniform2ui(void* self, GLint location, GLuint v0, GLuint v1) {
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(ctx->m_state->currentShaderProgram(),location);
    ctx->m_glUniform2ui_enc(self, hostLoc, v0, v1);
}

void GL2Encoder::s_glUniform3ui(void* self, GLint location, GLuint v0, GLuint v1, GLuint v2) {
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(ctx->m_state->currentShaderProgram(),location);
    ctx->m_glUniform3ui_enc(self, hostLoc, v0, v1, v2);
}

void GL2Encoder::s_glUniform4ui(void* self, GLint location, GLint v0, GLuint v1, GLuint v2, GLuint v3) {
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(ctx->m_state->currentShaderProgram(),location);
    ctx->m_glUniform4ui_enc(self, hostLoc, v0, v1, v2, v3);
}

void GL2Encoder::s_glUniform1uiv(void* self, GLint location, GLsizei count, const GLuint *value) {
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(ctx->m_state->currentShaderProgram(),location);
    ctx->m_glUniform1uiv_enc(self, hostLoc, count, value);
}

void GL2Encoder::s_glUniform2uiv(void* self, GLint location, GLsizei count, const GLuint *value) {
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(ctx->m_state->currentShaderProgram(),location);
    ctx->m_glUniform2uiv_enc(self, hostLoc, count, value);
}

void GL2Encoder::s_glUniform3uiv(void* self, GLint location, GLsizei count, const GLuint *value) {
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(ctx->m_state->currentShaderProgram(),location);
    ctx->m_glUniform3uiv_enc(self, hostLoc, count, value);
}

void GL2Encoder::s_glUniform4uiv(void* self, GLint location, GLsizei count, const GLuint *value) {
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(ctx->m_state->currentShaderProgram(),location);
    ctx->m_glUniform4uiv_enc(self, hostLoc, count, value);
}

void GL2Encoder::s_glUniformMatrix2x3fv(void* self, GLint location, GLsizei count, GLboolean transpose, const GLfloat *value) {
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(ctx->m_state->currentShaderProgram(),location);
    ctx->m_glUniformMatrix2x3fv_enc(self, hostLoc, count, transpose, value);
}

void GL2Encoder::s_glUniformMatrix3x2fv(void* self, GLint location, GLsizei count, GLboolean transpose, const GLfloat *value) {
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(ctx->m_state->currentShaderProgram(),location);
    ctx->m_glUniformMatrix3x2fv_enc(self, hostLoc, count, transpose, value);
}

void GL2Encoder::s_glUniformMatrix2x4fv(void* self, GLint location, GLsizei count, GLboolean transpose, const GLfloat *value) {
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(ctx->m_state->currentShaderProgram(),location);
    ctx->m_glUniformMatrix2x4fv_enc(self, hostLoc, count, transpose, value);
}

void GL2Encoder::s_glUniformMatrix4x2fv(void* self, GLint location, GLsizei count, GLboolean transpose, const GLfloat *value) {
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(ctx->m_state->currentShaderProgram(),location);
    ctx->m_glUniformMatrix4x2fv_enc(self, hostLoc, count, transpose, value);
}

void GL2Encoder::s_glUniformMatrix3x4fv(void* self, GLint location, GLsizei count, GLboolean transpose, const GLfloat *value) {
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(ctx->m_state->currentShaderProgram(),location);
    ctx->m_glUniformMatrix3x4fv_enc(self, hostLoc, count, transpose, value);
}

void GL2Encoder::s_glUniformMatrix4x3fv(void* self, GLint location, GLsizei count, GLboolean transpose, const GLfloat *value) {
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(ctx->m_state->currentShaderProgram(),location);
    ctx->m_glUniformMatrix4x3fv_enc(self, hostLoc, count, transpose, value);
}

void GL2Encoder::s_glGetUniformuiv(void* self, GLuint program, GLint location, GLuint* params) {
    GL2Encoder *ctx = (GL2Encoder*)self;
    SET_ERROR_IF(!ctx->m_shared->isShaderOrProgramObject(program), GL_INVALID_VALUE);
    SET_ERROR_IF(!ctx->m_shared->isProgram(program), GL_INVALID_OPERATION);
    SET_ERROR_IF(!ctx->m_shared->isProgramInitialized(program), GL_INVALID_OPERATION);
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program, location);
    SET_ERROR_IF(ctx->m_shared->getProgramUniformType(program,hostLoc)==0, GL_INVALID_OPERATION);
    ctx->m_glGetUniformuiv_enc(self, program, hostLoc, params);
}

void GL2Encoder::s_glGetActiveUniformBlockiv(void* self, GLuint program, GLuint uniformBlockIndex, GLenum pname, GLint* params) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;

    // refresh client state's # active uniforms in this block
    if (pname == GL_UNIFORM_BLOCK_ACTIVE_UNIFORM_INDICES) {
        // TODO if worth it: cache uniform count and other params,
        // invalidate on program relinking.
        GLint numActiveUniforms;
        ctx->m_glGetActiveUniformBlockiv_enc(ctx,
                program, uniformBlockIndex,
                GL_UNIFORM_BLOCK_ACTIVE_UNIFORMS,
                &numActiveUniforms);
        ctx->m_state->setNumActiveUniformsInUniformBlock(
                program, uniformBlockIndex, numActiveUniforms);
    }

    ctx->m_glGetActiveUniformBlockiv_enc(ctx,
            program, uniformBlockIndex,
            pname, params);
}

void GL2Encoder::s_glGetVertexAttribIiv(void* self, GLuint index, GLenum pname, GLint* params) {
    GL2Encoder *ctx = (GL2Encoder *)self;
    assert(ctx->m_state);
    GLint maxIndex;
    ctx->glGetIntegerv(self, GL_MAX_VERTEX_ATTRIBS, &maxIndex);
    SET_ERROR_IF(!(index < maxIndex), GL_INVALID_VALUE);

    if (!ctx->m_state->getVertexAttribParameter<GLint>(index, pname, params)) {
        ctx->m_glGetVertexAttribIiv_enc(self, index, pname, params);
    }
}

void GL2Encoder::s_glGetVertexAttribIuiv(void* self, GLuint index, GLenum pname, GLuint* params) {
    GL2Encoder *ctx = (GL2Encoder *)self;
    assert(ctx->m_state);
    GLint maxIndex;
    ctx->glGetIntegerv(self, GL_MAX_VERTEX_ATTRIBS, &maxIndex);
    SET_ERROR_IF(!(index < maxIndex), GL_INVALID_VALUE);

    if (!ctx->m_state->getVertexAttribParameter<GLuint>(index, pname, params)) {
        ctx->m_glGetVertexAttribIuiv_enc(self, index, pname, params);
    }
}

void GL2Encoder::s_glVertexAttribIPointer(void* self, GLuint index, GLint size, GLenum type, GLsizei stride, const GLvoid* pointer) {
    GL2Encoder *ctx = (GL2Encoder *)self;
    assert(ctx->m_state != NULL);
    VALIDATE_VERTEX_ATTRIB_INDEX(index);
    SET_ERROR_IF((size < 1 || size > 4), GL_INVALID_VALUE);
    SET_ERROR_IF(
        !(type == GL_BYTE ||
          type == GL_UNSIGNED_BYTE ||
          type == GL_SHORT ||
          type == GL_UNSIGNED_SHORT ||
          type == GL_INT ||
          type == GL_UNSIGNED_INT),
        GL_INVALID_ENUM);
    SET_ERROR_IF(stride < 0, GL_INVALID_VALUE);

    ctx->m_state->setVertexAttribBinding(index, index);
    ctx->m_state->setVertexAttribFormat(index, size, type, false, 0, true);
    GLsizei effectiveStride = stride;
    if (stride == 0) {
        effectiveStride = glSizeof(type) * size; 
    }
    ctx->m_state->bindIndexedBuffer(0, index, ctx->m_state->currentArrayVbo(), (uintptr_t)pointer, 0, stride, effectiveStride);

    if (ctx->m_state->currentArrayVbo() != 0) {
        ctx->glVertexAttribIPointerOffsetAEMU(ctx, index, size, type, stride, (uintptr_t)pointer);
    } else {
        SET_ERROR_IF(ctx->m_state->currentVertexArrayObject() != 0 && pointer, GL_INVALID_OPERATION);
        // wait for client-array handler
    }
}

void GL2Encoder::s_glVertexAttribDivisor(void* self, GLuint index, GLuint divisor) {
    GL2Encoder *ctx = (GL2Encoder *)self;
    assert(ctx->m_state != NULL);
    VALIDATE_VERTEX_ATTRIB_INDEX(index);
    ctx->m_state->setVertexAttribBinding(index, index);
    ctx->m_state->setVertexBindingDivisor(index, divisor);
    ctx->m_glVertexAttribDivisor_enc(ctx, index, divisor);
}

void GL2Encoder::s_glRenderbufferStorageMultisample(void* self,
        GLenum target, GLsizei samples, GLenum internalformat,
        GLsizei width, GLsizei height) {
    GL2Encoder *ctx = (GL2Encoder *)self;
    GLClientState* state = ctx->m_state;

    SET_ERROR_IF(target != GL_RENDERBUFFER, GL_INVALID_ENUM);
    SET_ERROR_IF(!GLESv2Validation::rboFormat(ctx, internalformat), GL_INVALID_ENUM);

    GLint max_samples;
    ctx->s_glGetInternalformativ(ctx, target, internalformat, GL_SAMPLES, 1, &max_samples);
    SET_ERROR_IF(samples > max_samples, GL_INVALID_OPERATION);

    state->setBoundRenderbufferFormat(internalformat);
    state->setBoundRenderbufferSamples(samples);
    ctx->m_glRenderbufferStorageMultisample_enc(
            self, target, samples, internalformat, width, height);
}

void GL2Encoder::s_glDrawBuffers(void* self, GLsizei n, const GLenum* bufs) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    SET_ERROR_IF(!ctx->m_state->boundFramebuffer(GL_DRAW_FRAMEBUFFER) && n > 1, GL_INVALID_OPERATION);
    SET_ERROR_IF(n < 0 || n > ctx->m_state->getMaxDrawBuffers(), GL_INVALID_VALUE);
    for (int i = 0; i < n; i++) {
        SET_ERROR_IF(
            bufs[i] != GL_NONE &&
            bufs[i] != GL_BACK &&
            glUtilsColorAttachmentIndex(bufs[i]) == -1,
            GL_INVALID_ENUM);
        SET_ERROR_IF(
            !ctx->m_state->boundFramebuffer(GL_DRAW_FRAMEBUFFER) &&
            glUtilsColorAttachmentIndex(bufs[i]) != -1,
            GL_INVALID_OPERATION);
        SET_ERROR_IF(
            ctx->m_state->boundFramebuffer(GL_DRAW_FRAMEBUFFER) &&
            ((glUtilsColorAttachmentIndex(bufs[i]) != -1 &&
              glUtilsColorAttachmentIndex(bufs[i]) != i) ||
             (glUtilsColorAttachmentIndex(bufs[i]) == -1 &&
              bufs[i] != GL_NONE)),
            GL_INVALID_OPERATION);
    }

    ctx->m_glDrawBuffers_enc(ctx, n, bufs);
}

void GL2Encoder::s_glReadBuffer(void* self, GLenum src) {
    GL2Encoder* ctx = (GL2Encoder*)self;

    SET_ERROR_IF(
        glUtilsColorAttachmentIndex(src) != -1 &&
         (glUtilsColorAttachmentIndex(src) >=
         ctx->m_state->getMaxColorAttachments()),
        GL_INVALID_OPERATION);
    SET_ERROR_IF(
        src != GL_NONE &&
        src != GL_BACK &&
        src > GL_COLOR_ATTACHMENT0 &&
        src < GL_DEPTH_ATTACHMENT &&
        (src - GL_COLOR_ATTACHMENT0) >
        ctx->m_state->getMaxColorAttachments(),
        GL_INVALID_OPERATION);
    SET_ERROR_IF(
        src != GL_NONE &&
        src != GL_BACK &&
        glUtilsColorAttachmentIndex(src) == -1,
        GL_INVALID_ENUM);
    SET_ERROR_IF(
        !ctx->m_state->boundFramebuffer(GL_READ_FRAMEBUFFER) &&
        src != GL_NONE &&
        src != GL_BACK,
        GL_INVALID_OPERATION);
    SET_ERROR_IF(
        ctx->m_state->boundFramebuffer(GL_READ_FRAMEBUFFER) &&
        src != GL_NONE &&
        glUtilsColorAttachmentIndex(src) == -1,
        GL_INVALID_OPERATION);

    ctx->m_glReadBuffer_enc(ctx, src);
}

void GL2Encoder::s_glFramebufferTextureLayer(void* self, GLenum target, GLenum attachment, GLuint texture, GLint level, GLint layer) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;

    SET_ERROR_IF(!GLESv2Validation::framebufferTarget(ctx, target), GL_INVALID_ENUM);
    SET_ERROR_IF(!GLESv2Validation::framebufferAttachment(ctx, attachment), GL_INVALID_ENUM);
    GLenum lastBoundTarget = state->queryTexLastBoundTarget(texture);
    SET_ERROR_IF(lastBoundTarget != GL_TEXTURE_2D_ARRAY &&
                 lastBoundTarget != GL_TEXTURE_3D,
                 GL_INVALID_OPERATION);
    state->attachTextureObject(target, attachment, texture);

    GLint max3DTextureSize;
    ctx->glGetIntegerv(ctx, GL_MAX_3D_TEXTURE_SIZE, &max3DTextureSize);
    SET_ERROR_IF(
            layer >= max3DTextureSize,
            GL_INVALID_VALUE);

    ctx->m_glFramebufferTextureLayer_enc(self, target, attachment, texture, level, layer);
}

void GL2Encoder::s_glTexStorage2D(void* self, GLenum target, GLsizei levels, GLenum internalformat, GLsizei width, GLsizei height) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;

    SET_ERROR_IF(
        target != GL_TEXTURE_2D &&
        target != GL_TEXTURE_CUBE_MAP,
        GL_INVALID_ENUM);
    SET_ERROR_IF(!GLESv2Validation::pixelInternalFormat(internalformat), GL_INVALID_ENUM);
    SET_ERROR_IF(!state->getBoundTexture(target), GL_INVALID_OPERATION);
    SET_ERROR_IF(levels < 1 || width < 1 || height < 1, GL_INVALID_VALUE);
    SET_ERROR_IF(levels > ilog2((uint32_t)std::max(width, height)) + 1,
                 GL_INVALID_OPERATION);
    SET_ERROR_IF(state->isBoundTextureImmutableFormat(target), GL_INVALID_OPERATION);

    state->setBoundTextureInternalFormat(target, internalformat);
    state->setBoundTextureDims(target, -1, width, height, 1);
    state->setBoundTextureImmutableFormat(target);
    ctx->m_glTexStorage2D_enc(ctx, target, levels, internalformat, width, height);
}

void GL2Encoder::s_glTransformFeedbackVaryings(void* self, GLuint program, GLsizei count, const char** varyings, GLenum bufferMode) {
    GL2Encoder* ctx = (GL2Encoder*)self;

    SET_ERROR_IF(!ctx->m_shared->isProgram(program), GL_INVALID_VALUE);

    GLint maxCount = 0;
    ctx->glGetIntegerv(ctx, GL_MAX_TRANSFORM_FEEDBACK_SEPARATE_ATTRIBS, &maxCount);

    SET_ERROR_IF(
        bufferMode == GL_SEPARATE_ATTRIBS &&
        maxCount < count,
        GL_INVALID_VALUE);
    SET_ERROR_IF(
        bufferMode != GL_INTERLEAVED_ATTRIBS &&
        bufferMode != GL_SEPARATE_ATTRIBS,
        GL_INVALID_ENUM);

    if (!count) return;

    GLint err = GL_NO_ERROR;
    std::string packed = packVarNames(count, varyings, &err);
    SET_ERROR_IF(err != GL_NO_ERROR, GL_INVALID_OPERATION);

    ctx->glTransformFeedbackVaryingsAEMU(ctx, program, count, (const char*)&packed[0], packed.size() + 1, bufferMode);
}

void GL2Encoder::s_glBeginTransformFeedback(void* self, GLenum primitiveMode) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;
    ctx->m_glBeginTransformFeedback_enc(ctx, primitiveMode);
    state->setTransformFeedbackActiveUnpaused(true);
}

void GL2Encoder::s_glEndTransformFeedback(void* self) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;
    ctx->m_glEndTransformFeedback_enc(ctx);
    state->setTransformFeedbackActiveUnpaused(false);
}

void GL2Encoder::s_glPauseTransformFeedback(void* self) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;
    ctx->m_glPauseTransformFeedback_enc(ctx);
    state->setTransformFeedbackActiveUnpaused(false);
}

void GL2Encoder::s_glResumeTransformFeedback(void* self) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;
    ctx->m_glResumeTransformFeedback_enc(ctx);
    state->setTransformFeedbackActiveUnpaused(true);
}

void GL2Encoder::s_glTexImage3D(void* self, GLenum target, GLint level, GLint internalFormat,
                               GLsizei width, GLsizei height, GLsizei depth,
                               GLint border, GLenum format, GLenum type, const GLvoid* data) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;

    SET_ERROR_IF(target != GL_TEXTURE_3D &&
                 target != GL_TEXTURE_2D_ARRAY,
                 GL_INVALID_ENUM);
    SET_ERROR_IF(!GLESv2Validation::pixelType(ctx, type), GL_INVALID_ENUM);
    SET_ERROR_IF(!GLESv2Validation::pixelFormat(ctx, format), GL_INVALID_ENUM);

    // If unpack buffer is nonzero, verify unmapped state.
    SET_ERROR_IF(ctx->isBufferTargetMapped(GL_PIXEL_UNPACK_BUFFER), GL_INVALID_OPERATION);

    GLint max_texture_size;
    GLint max_3d_texture_size;
    ctx->glGetIntegerv(ctx, GL_MAX_TEXTURE_SIZE, &max_texture_size);
    ctx->glGetIntegerv(ctx, GL_MAX_3D_TEXTURE_SIZE, &max_3d_texture_size);
    SET_ERROR_IF(level < 0, GL_INVALID_VALUE);
    SET_ERROR_IF(level > ilog2(max_texture_size), GL_INVALID_VALUE);
    SET_ERROR_IF(level > ilog2(max_3d_texture_size), GL_INVALID_VALUE);

    SET_ERROR_IF(width < 0 || height < 0 || depth < 0, GL_INVALID_VALUE);
    SET_ERROR_IF(width > GL_MAX_TEXTURE_SIZE, GL_INVALID_VALUE);
    SET_ERROR_IF(height > GL_MAX_TEXTURE_SIZE, GL_INVALID_VALUE);
    SET_ERROR_IF(depth > GL_MAX_TEXTURE_SIZE, GL_INVALID_VALUE);
    SET_ERROR_IF(width > GL_MAX_3D_TEXTURE_SIZE, GL_INVALID_VALUE);
    SET_ERROR_IF(height > GL_MAX_3D_TEXTURE_SIZE, GL_INVALID_VALUE);
    SET_ERROR_IF(depth > GL_MAX_3D_TEXTURE_SIZE, GL_INVALID_VALUE);
    SET_ERROR_IF(border != 0, GL_INVALID_VALUE);
    // If unpack buffer is nonzero, verify buffer data fits and is evenly divisible by the type.
    SET_ERROR_IF(ctx->boundBuffer(GL_PIXEL_UNPACK_BUFFER) &&
                 ctx->getBufferData(GL_PIXEL_UNPACK_BUFFER) &&
                 (ctx->m_state->pboNeededDataSize(width, height, depth, format, type, 0) >
                  ctx->getBufferData(GL_PIXEL_UNPACK_BUFFER)->m_size),
                 GL_INVALID_OPERATION);
    SET_ERROR_IF(ctx->boundBuffer(GL_PIXEL_UNPACK_BUFFER) &&
                 ctx->getBufferData(GL_PIXEL_UNPACK_BUFFER) &&
                 (ctx->getBufferData(GL_PIXEL_UNPACK_BUFFER)->m_size %
                  glSizeof(type)),
                 GL_INVALID_OPERATION);
    SET_ERROR_IF(state->isBoundTextureImmutableFormat(target), GL_INVALID_OPERATION);

    state->setBoundTextureInternalFormat(target, internalFormat);
    state->setBoundTextureFormat(target, format);
    state->setBoundTextureType(target, type);
    state->setBoundTextureDims(target, level, width, height, depth);

    if (ctx->boundBuffer(GL_PIXEL_UNPACK_BUFFER)) {
        ctx->glTexImage3DOffsetAEMU(
                ctx, target, level, internalFormat,
                width, height, depth,
                border, format, type, (uintptr_t)data);
    } else {
        ctx->m_glTexImage3D_enc(ctx,
                target, level, internalFormat,
                width, height, depth,
                border, format, type, data);
    }
}

void GL2Encoder::s_glTexSubImage3D(void* self, GLenum target, GLint level, GLint xoffset, GLint yoffset, GLint zoffset, GLsizei width, GLsizei height, GLsizei depth, GLenum format, GLenum type, const GLvoid* data) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;

    SET_ERROR_IF(target != GL_TEXTURE_3D &&
                 target != GL_TEXTURE_2D_ARRAY,
                 GL_INVALID_ENUM);
    SET_ERROR_IF(!GLESv2Validation::pixelType(ctx, type), GL_INVALID_ENUM);
    SET_ERROR_IF(!GLESv2Validation::pixelFormat(ctx, format), GL_INVALID_ENUM);
    // If unpack buffer is nonzero, verify unmapped state.
    SET_ERROR_IF(ctx->isBufferTargetMapped(GL_PIXEL_UNPACK_BUFFER), GL_INVALID_OPERATION);
    GLint max_texture_size;
    GLint max_3d_texture_size;
    ctx->glGetIntegerv(ctx, GL_MAX_TEXTURE_SIZE, &max_texture_size);
    ctx->glGetIntegerv(ctx, GL_MAX_3D_TEXTURE_SIZE, &max_3d_texture_size);
    SET_ERROR_IF(level < 0, GL_INVALID_VALUE);
    SET_ERROR_IF(level > ilog2(max_texture_size), GL_INVALID_VALUE);
    SET_ERROR_IF(level > ilog2(max_3d_texture_size), GL_INVALID_VALUE);
    SET_ERROR_IF(width < 0 || height < 0 || depth < 0, GL_INVALID_VALUE);
    SET_ERROR_IF(xoffset < 0 || yoffset < 0 || zoffset < 0, GL_INVALID_VALUE);
    GLuint tex = state->getBoundTexture(target);
    GLsizei neededWidth = xoffset + width;
    GLsizei neededHeight = yoffset + height;
    GLsizei neededDepth = zoffset + depth;

    SET_ERROR_IF(tex &&
                 (neededWidth > state->queryTexWidth(level, tex) ||
                  neededHeight > state->queryTexHeight(level, tex) ||
                  neededDepth > state->queryTexDepth(level, tex)),
                 GL_INVALID_VALUE);
    // If unpack buffer is nonzero, verify buffer data fits and is evenly divisible by the type.
    SET_ERROR_IF(ctx->boundBuffer(GL_PIXEL_UNPACK_BUFFER) &&
                 ctx->getBufferData(GL_PIXEL_UNPACK_BUFFER) &&
                 (ctx->m_state->pboNeededDataSize(width, height, depth, format, type, 0) >
                  ctx->getBufferData(GL_PIXEL_UNPACK_BUFFER)->m_size),
                 GL_INVALID_OPERATION);
    SET_ERROR_IF(ctx->boundBuffer(GL_PIXEL_UNPACK_BUFFER) &&
                 ctx->getBufferData(GL_PIXEL_UNPACK_BUFFER) &&
                 (ctx->getBufferData(GL_PIXEL_UNPACK_BUFFER)->m_size %
                  glSizeof(type)),
                 GL_INVALID_OPERATION);
    SET_ERROR_IF(!ctx->boundBuffer(GL_PIXEL_UNPACK_BUFFER) && !data, GL_INVALID_OPERATION);
    SET_ERROR_IF(xoffset < 0 || yoffset < 0 || zoffset < 0, GL_INVALID_VALUE);

    if (ctx->boundBuffer(GL_PIXEL_UNPACK_BUFFER)) {
        ctx->glTexSubImage3DOffsetAEMU(ctx,
                target, level,
                xoffset, yoffset, zoffset,
                width, height, depth,
                format, type, (uintptr_t)data);
    } else {
        ctx->m_glTexSubImage3D_enc(ctx,
                target, level,
                xoffset, yoffset, zoffset,
                width, height, depth,
                format, type, data);
    }
}

void GL2Encoder::s_glCompressedTexImage3D(void* self, GLenum target, GLint level, GLenum internalformat, GLsizei width, GLsizei height, GLsizei depth, GLint border, GLsizei imageSize, const GLvoid* data) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;

    // Filter compressed formats support.
    SET_ERROR_IF(!GLESv2Validation::supportedCompressedFormat(ctx, internalformat), GL_INVALID_ENUM);
    // If unpack buffer is nonzero, verify unmapped state.
    SET_ERROR_IF(ctx->isBufferTargetMapped(GL_PIXEL_UNPACK_BUFFER), GL_INVALID_OPERATION);
    SET_ERROR_IF(width < 0 || height < 0 || depth < 0, GL_INVALID_VALUE);
    SET_ERROR_IF(border, GL_INVALID_VALUE);
    // If unpack buffer is nonzero, verify buffer data fits.
    SET_ERROR_IF(ctx->boundBuffer(GL_PIXEL_UNPACK_BUFFER) &&
                 ctx->getBufferData(GL_PIXEL_UNPACK_BUFFER) &&
                 (imageSize > ctx->getBufferData(GL_PIXEL_UNPACK_BUFFER)->m_size),
                 GL_INVALID_OPERATION);
    // TODO: Fix:
    // If |imageSize| is too small for compressed dimensions.
    // SET_ERROR_IF(GLESv2Validation::compressedTexImageSize(internalformat, width, height, depth) > imageSize, GL_INVALID_VALUE);
    state->setBoundTextureInternalFormat(target, (GLint)internalformat);
    state->setBoundTextureDims(target, level, width, height, depth);

    if (ctx->boundBuffer(GL_PIXEL_UNPACK_BUFFER)) {
        ctx->glCompressedTexImage3DOffsetAEMU(
                ctx, target, level, internalformat,
                width, height, depth, border,
                imageSize, (uintptr_t)data);
    } else {
        ctx->m_glCompressedTexImage3D_enc(
                ctx, target, level, internalformat,
                width, height, depth, border,
                imageSize, data);
    }
}

void GL2Encoder::s_glCompressedTexSubImage3D(void* self, GLenum target, GLint level, GLint xoffset, GLint yoffset, GLint zoffset, GLsizei width, GLsizei height, GLsizei depth, GLenum format, GLsizei imageSize, const GLvoid* data) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;

    SET_ERROR_IF(!GLESv2Validation::textureTarget(ctx, target), GL_INVALID_ENUM);
    // If unpack buffer is nonzero, verify unmapped state.
    SET_ERROR_IF(ctx->isBufferTargetMapped(GL_PIXEL_UNPACK_BUFFER), GL_INVALID_OPERATION);
    SET_ERROR_IF(width < 0 || height < 0 || depth < 0, GL_INVALID_VALUE);
    // If unpack buffer is nonzero, verify buffer data fits.
    SET_ERROR_IF(ctx->boundBuffer(GL_PIXEL_UNPACK_BUFFER) &&
                 ctx->getBufferData(GL_PIXEL_UNPACK_BUFFER) &&
                 (imageSize > ctx->getBufferData(GL_PIXEL_UNPACK_BUFFER)->m_size),
                 GL_INVALID_OPERATION);
    SET_ERROR_IF(xoffset < 0 || yoffset < 0 || zoffset < 0, GL_INVALID_VALUE);

    if (ctx->boundBuffer(GL_PIXEL_UNPACK_BUFFER)) {
        ctx->glCompressedTexSubImage3DOffsetAEMU(
                ctx, target, level,
                xoffset, yoffset, zoffset,
                width, height, depth,
                format, imageSize, (uintptr_t)data);
    } else {
        ctx->m_glCompressedTexSubImage3D_enc(
                ctx, target, level,
                xoffset, yoffset, zoffset,
                width, height, depth,
                format, imageSize, data);

    }
}

void GL2Encoder::s_glTexStorage3D(void* self, GLenum target, GLsizei levels, GLenum internalformat, GLsizei width, GLsizei height, GLsizei depth) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;
    SET_ERROR_IF(target != GL_TEXTURE_3D &&
                 target != GL_TEXTURE_2D_ARRAY,
                 GL_INVALID_ENUM);
    SET_ERROR_IF(!GLESv2Validation::pixelInternalFormat(internalformat), GL_INVALID_ENUM);
    SET_ERROR_IF(!state->getBoundTexture(target), GL_INVALID_OPERATION);
    SET_ERROR_IF(levels < 1 || width < 1 || height < 1, GL_INVALID_VALUE);
    SET_ERROR_IF(target == GL_TEXTURE_3D && (levels > ilog2((uint32_t)std::max(width, std::max(height, depth))) + 1),
                 GL_INVALID_OPERATION);
    SET_ERROR_IF(target == GL_TEXTURE_2D_ARRAY && (levels > ilog2((uint32_t)std::max(width, height)) + 1),
                 GL_INVALID_OPERATION);
    SET_ERROR_IF(state->isBoundTextureImmutableFormat(target), GL_INVALID_OPERATION);

    state->setBoundTextureInternalFormat(target, internalformat);
    state->setBoundTextureDims(target, -1, width, height, depth);
    state->setBoundTextureImmutableFormat(target);
    ctx->m_glTexStorage3D_enc(ctx, target, levels, internalformat, width, height, depth);
    state->setBoundTextureImmutableFormat(target);
}

void GL2Encoder::s_glDrawArraysInstanced(void* self, GLenum mode, GLint first, GLsizei count, GLsizei primcount) {
    GL2Encoder *ctx = (GL2Encoder *)self;
    assert(ctx->m_state != NULL);
    SET_ERROR_IF(!isValidDrawMode(mode), GL_INVALID_ENUM);
    SET_ERROR_IF(count < 0, GL_INVALID_VALUE);

    bool has_client_vertex_arrays = false;
    bool has_indirect_arrays = false;
    ctx->getVBOUsage(&has_client_vertex_arrays,
                     &has_indirect_arrays);

    if (has_client_vertex_arrays ||
        (!has_client_vertex_arrays &&
         !has_indirect_arrays)) {
        ctx->sendVertexAttributes(first, count, true, primcount);
        ctx->m_glDrawArraysInstanced_enc(ctx, mode, 0, count, primcount);
    } else {
        ctx->sendVertexAttributes(0, count, false, primcount);
        ctx->m_glDrawArraysInstanced_enc(ctx, mode, first, count, primcount);
    }
    ctx->m_stream->flush();
}

void GL2Encoder::s_glDrawElementsInstanced(void* self, GLenum mode, GLsizei count, GLenum type, const void* indices, GLsizei primcount)
{

    GL2Encoder *ctx = (GL2Encoder *)self;
    assert(ctx->m_state != NULL);
    SET_ERROR_IF(!isValidDrawMode(mode), GL_INVALID_ENUM);
    SET_ERROR_IF(count < 0, GL_INVALID_VALUE);
    SET_ERROR_IF(!(type == GL_UNSIGNED_BYTE || type == GL_UNSIGNED_SHORT || type == GL_UNSIGNED_INT), GL_INVALID_ENUM);
    SET_ERROR_IF(ctx->m_state->getTransformFeedbackActiveUnpaused(), GL_INVALID_OPERATION);

    bool has_client_vertex_arrays = false;
    bool has_indirect_arrays = false;
    int nLocations = ctx->m_state->nLocations();
    GLintptr offset = 0;

    ctx->getVBOUsage(&has_client_vertex_arrays, &has_indirect_arrays);

    if (!has_client_vertex_arrays && !has_indirect_arrays) {
        // ALOGW("glDrawElements: no vertex arrays / buffers bound to the command\n");
        GLenum status = ctx->m_glCheckFramebufferStatus_enc(self, GL_FRAMEBUFFER);
        SET_ERROR_IF(status != GL_FRAMEBUFFER_COMPLETE, GL_INVALID_FRAMEBUFFER_OPERATION);
    }

    BufferData* buf = NULL;
    int minIndex = 0, maxIndex = 0;

    // For validation/immediate index array purposes,
    // we need the min/max vertex index of the index array.
    // If the VBO != 0, this may not be the first time we have
    // used this particular index buffer. getBufferIndexRange
    // can more quickly get min/max vertex index by
    // caching previous results.
    if (ctx->m_state->currentIndexVbo() != 0) {
        buf = ctx->m_shared->getBufferData(ctx->m_state->currentIndexVbo());
        offset = (GLintptr)indices;
        indices = (void*)((GLintptr)buf->m_fixedBuffer.ptr() + (GLintptr)indices);
        ctx->getBufferIndexRange(buf,
                                 indices,
                                 type,
                                 (size_t)count,
                                 (size_t)offset,
                                 &minIndex, &maxIndex);
    } else {
        // In this case, the |indices| field holds a real
        // array, so calculate the indices now. They will
        // also be needed to know how much data to
        // transfer to host.
        ctx->calcIndexRange(indices,
                            type,
                            count,
                            &minIndex,
                            &maxIndex);
    }

    bool adjustIndices = true;
    if (ctx->m_state->currentIndexVbo() != 0) {
        if (!has_client_vertex_arrays) {
            ctx->sendVertexAttributes(0, maxIndex + 1, false, primcount);
            ctx->m_glBindBuffer_enc(self, GL_ELEMENT_ARRAY_BUFFER, ctx->m_state->currentIndexVbo());
            ctx->glDrawElementsInstancedOffsetAEMU(ctx, mode, count, type, offset, primcount);
            ctx->flushDrawCall();
            adjustIndices = false;
        } else {
            BufferData * buf = ctx->m_shared->getBufferData(ctx->m_state->currentIndexVbo());
            ctx->m_glBindBuffer_enc(self, GL_ELEMENT_ARRAY_BUFFER, 0);
        }
    }
    if (adjustIndices) {
        void *adjustedIndices =
            ctx->recenterIndices(indices,
                                 type,
                                 count,
                                 minIndex);

        if (has_indirect_arrays || 1) {
            ctx->sendVertexAttributes(minIndex, maxIndex - minIndex + 1, true, primcount);
            ctx->glDrawElementsInstancedDataAEMU(ctx, mode, count, type, adjustedIndices, primcount, count * glSizeof(type));
            ctx->m_stream->flush();
            // XXX - OPTIMIZATION (see the other else branch) should be implemented
            if(!has_indirect_arrays) {
                //ALOGD("unoptimized drawelements !!!\n");
            }
        } else {
            // we are all direct arrays and immidate mode index array -
            // rebuild the arrays and the index array;
            ALOGE("glDrawElements: direct index & direct buffer data - will be implemented in later versions;\n");
        }
    }
}

void GL2Encoder::s_glDrawRangeElements(void* self, GLenum mode, GLuint start, GLuint end, GLsizei count, GLenum type, const void* indices)
{

    GL2Encoder *ctx = (GL2Encoder *)self;
    assert(ctx->m_state != NULL);
    SET_ERROR_IF(!isValidDrawMode(mode), GL_INVALID_ENUM);
    SET_ERROR_IF(end < start, GL_INVALID_VALUE);
    SET_ERROR_IF(count < 0, GL_INVALID_VALUE);
    SET_ERROR_IF(!(type == GL_UNSIGNED_BYTE || type == GL_UNSIGNED_SHORT || type == GL_UNSIGNED_INT), GL_INVALID_ENUM);
    SET_ERROR_IF(ctx->m_state->getTransformFeedbackActiveUnpaused(), GL_INVALID_OPERATION);

    bool has_client_vertex_arrays = false;
    bool has_indirect_arrays = false;
    int nLocations = ctx->m_state->nLocations();
    GLintptr offset = 0;

    ctx->getVBOUsage(&has_client_vertex_arrays, &has_indirect_arrays);

    if (!has_client_vertex_arrays && !has_indirect_arrays) {
        // ALOGW("glDrawElements: no vertex arrays / buffers bound to the command\n");
        GLenum status = ctx->m_glCheckFramebufferStatus_enc(self, GL_FRAMEBUFFER);
        SET_ERROR_IF(status != GL_FRAMEBUFFER_COMPLETE, GL_INVALID_FRAMEBUFFER_OPERATION);
    }

    BufferData* buf = NULL;
    int minIndex = 0, maxIndex = 0;

    // For validation/immediate index array purposes,
    // we need the min/max vertex index of the index array.
    // If the VBO != 0, this may not be the first time we have
    // used this particular index buffer. getBufferIndexRange
    // can more quickly get min/max vertex index by
    // caching previous results.
    if (ctx->m_state->currentIndexVbo() != 0) {
        buf = ctx->m_shared->getBufferData(ctx->m_state->currentIndexVbo());
        offset = (GLintptr)indices;
        indices = (void*)((GLintptr)buf->m_fixedBuffer.ptr() + (GLintptr)indices);
        ctx->getBufferIndexRange(buf,
                                 indices,
                                 type,
                                 (size_t)count,
                                 (size_t)offset,
                                 &minIndex, &maxIndex);
    } else {
        // In this case, the |indices| field holds a real
        // array, so calculate the indices now. They will
        // also be needed to know how much data to
        // transfer to host.
        ctx->calcIndexRange(indices,
                            type,
                            count,
                            &minIndex,
                            &maxIndex);
    }

    bool adjustIndices = true;
    if (ctx->m_state->currentIndexVbo() != 0) {
        if (!has_client_vertex_arrays) {
            ctx->sendVertexAttributes(0, maxIndex + 1, false);
            ctx->m_glBindBuffer_enc(self, GL_ELEMENT_ARRAY_BUFFER, ctx->m_state->currentIndexVbo());
            ctx->glDrawElementsOffset(ctx, mode, count, type, offset);
            ctx->flushDrawCall();
            adjustIndices = false;
        } else {
            BufferData * buf = ctx->m_shared->getBufferData(ctx->m_state->currentIndexVbo());
            ctx->m_glBindBuffer_enc(self, GL_ELEMENT_ARRAY_BUFFER, 0);
        }
    }
    if (adjustIndices) {
        void *adjustedIndices =
            ctx->recenterIndices(indices,
                                 type,
                                 count,
                                 minIndex);

        if (has_indirect_arrays || 1) {
            ctx->sendVertexAttributes(minIndex, maxIndex - minIndex + 1, true);
            ctx->glDrawElementsData(ctx, mode, count, type, adjustedIndices, count * glSizeof(type));
            ctx->m_stream->flush();
            // XXX - OPTIMIZATION (see the other else branch) should be implemented
            if(!has_indirect_arrays) {
                //ALOGD("unoptimized drawelements !!!\n");
            }
        } else {
            // we are all direct arrays and immidate mode index array -
            // rebuild the arrays and the index array;
            ALOGE("glDrawElements: direct index & direct buffer data - will be implemented in later versions;\n");
        }
    }
}

// struct GLStringKey {
//     GLenum name;
//     GLuint index;
// };
// 
// struct GLStringKeyCompare {
//     bool operator() (const GLStringKey& a,
//                      const GLStringKey& b) const {
//         if (a.name != b.name) return a.name < b.name;
//         if (a.index != b.index) return a.index < b.index;
//         return false;
//     }
// };
// 
// typedef std::map<GLStringKey, std::string, GLStringKeyCompare> GLStringStore;
// 
// static GLStringStore sGLStringStore;
// bool sGLStringStoreInitialized = false;

const GLubyte* GL2Encoder::s_glGetStringi(void* self, GLenum name, GLuint index) {
    GL2Encoder *ctx = (GL2Encoder *)self;
    GLubyte *retval =  (GLubyte *) "";

    RET_AND_SET_ERROR_IF(
        name != GL_VENDOR &&
        name != GL_RENDERER &&
        name != GL_VERSION &&
        name != GL_EXTENSIONS,
        GL_INVALID_ENUM,
        retval);

    RET_AND_SET_ERROR_IF(
        name == GL_VENDOR ||
        name == GL_RENDERER ||
        name == GL_VERSION ||
        name == GL_EXTENSIONS &&
        index != 0,
        GL_INVALID_VALUE,
        retval);

    switch (name) {
    case GL_VENDOR:
        retval = gVendorString;
        break;
    case GL_RENDERER:
        retval = gRendererString;
        break;
    case GL_VERSION:
        retval = gVersionString;
        break;
    case GL_EXTENSIONS:
        retval = gExtensionsString;
        break;
    }

    return retval;
}

void GL2Encoder::s_glGetProgramBinary(void* self, GLuint program, GLsizei bufSize, GLsizei* length, GLenum* binaryFormat, void* binary) {
    GL2Encoder *ctx = (GL2Encoder *)self;

    SET_ERROR_IF(!ctx->m_shared->isProgram(program), GL_INVALID_OPERATION);

    GLint linkStatus = 0;
    ctx->glGetProgramiv(self, program, GL_LINK_STATUS, &linkStatus);
    GLint properLength = 0;
    ctx->glGetProgramiv(self, program, GL_PROGRAM_BINARY_LENGTH, &properLength);

    SET_ERROR_IF(!linkStatus, GL_INVALID_OPERATION);
    SET_ERROR_IF(bufSize < properLength, GL_INVALID_OPERATION);

    ctx->m_glGetProgramBinary_enc(ctx, program, bufSize, length, binaryFormat, binary);
}

void GL2Encoder::s_glReadPixels(void* self, GLint x, GLint y, GLsizei width, GLsizei height, GLenum format, GLenum type, GLvoid* pixels) {
    GL2Encoder *ctx = (GL2Encoder *)self;

    SET_ERROR_IF(!GLESv2Validation::readPixelsFormat(format), GL_INVALID_ENUM);
    SET_ERROR_IF(!GLESv2Validation::readPixelsType(type), GL_INVALID_ENUM);
    SET_ERROR_IF(width < 0 || height < 0, GL_INVALID_VALUE);
    SET_ERROR_IF(ctx->isBufferTargetMapped(GL_PIXEL_PACK_BUFFER), GL_INVALID_OPERATION);
    SET_ERROR_IF(ctx->boundBuffer(GL_PIXEL_PACK_BUFFER) &&
                 ctx->getBufferData(GL_PIXEL_PACK_BUFFER) &&
                 (ctx->m_state->pboNeededDataSize(width, height, 1, format, type, 1) >
                  ctx->getBufferData(GL_PIXEL_PACK_BUFFER)->m_size),
                 GL_INVALID_OPERATION);
    /*
GL_INVALID_OPERATION is generated if the readbuffer of the currently bound framebuffer is a fixed point normalized surface and format and type are neither GL_RGBA and GL_UNSIGNED_BYTE, respectively, nor the format/type pair returned by querying GL_IMPLEMENTATION_COLOR_READ_FORMAT and GL_IMPLEMENTATION_COLOR_READ_TYPE.

GL_INVALID_OPERATION is generated if the readbuffer of the currently bound framebuffer is a floating point surface and format and type are neither GL_RGBA and GL_FLOAT, respectively, nor the format/type pair returned by querying GL_IMPLEMENTATION_COLOR_READ_FORMAT and GL_IMPLEMENTATION_COLOR_READ_TYPE.

GL_INVALID_OPERATION is generated if the readbuffer of the currently bound framebuffer is a signed integer surface and format and type are neither GL_RGBA_INTEGER and GL_INT, respectively, nor the format/type pair returned by querying GL_IMPLEMENTATION_COLOR_READ_FORMAT and GL_IMPLEMENTATION_COLOR_READ_TYPE.

GL_INVALID_OPERATION is generated if the readbuffer of the currently bound framebuffer is an unsigned integer surface and format and type are neither GL_RGBA_INTEGER and GL_UNSIGNED_INT, respectively, nor the format/type pair returned by querying GL_IMPLEMENTATION_COLOR_READ_FORMAT and GL_IMPLEMENTATION_COLOR_READ_TYPE.
*/

    FboFormatInfo fbo_format_info;
    ctx->m_state->getBoundFramebufferFormat(
            GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, &fbo_format_info);
    SET_ERROR_IF(
        fbo_format_info.type == FBO_ATTACHMENT_TEXTURE &&
        !GLESv2Validation::readPixelsFboFormatMatch(
            format, type, fbo_format_info.tex_type),
        GL_INVALID_OPERATION);

    if (ctx->boundBuffer(GL_PIXEL_PACK_BUFFER)) {
        ctx->glReadPixelsOffsetAEMU(
                ctx, x, y, width, height,
                format, type, (uintptr_t)pixels);
    } else {
        ctx->m_glReadPixels_enc(
                ctx, x, y, width, height,
                format, type, pixels);
    }
}

// Track enabled state for some things like:
// - Primitive restart
void GL2Encoder::s_glEnable(void* self, GLenum what) {
    GL2Encoder *ctx = (GL2Encoder *)self;

    switch (what) {
    case GL_PRIMITIVE_RESTART_FIXED_INDEX:
        ctx->m_primitiveRestartEnabled = true;
        break;
    }

    ctx->m_glEnable_enc(ctx, what);
}

void GL2Encoder::s_glDisable(void* self, GLenum what) {
    GL2Encoder *ctx = (GL2Encoder *)self;

    switch (what) {
    case GL_PRIMITIVE_RESTART_FIXED_INDEX:
        ctx->m_primitiveRestartEnabled = false;
        break;
    }

    ctx->m_glDisable_enc(ctx, what);
}

void GL2Encoder::s_glClearBufferiv(void* self, GLenum buffer, GLint drawBuffer, const GLint * value) {
    GL2Encoder *ctx = (GL2Encoder *)self;

    SET_ERROR_IF(buffer == GL_DEPTH || buffer == GL_DEPTH_STENCIL, GL_INVALID_ENUM);

    ctx->m_glClearBufferiv_enc(ctx, buffer, drawBuffer, value);
}

void GL2Encoder::s_glClearBufferuiv(void* self, GLenum buffer, GLint drawBuffer, const GLuint * value) {
    GL2Encoder *ctx = (GL2Encoder *)self;

    SET_ERROR_IF(buffer == GL_DEPTH || buffer == GL_STENCIL || buffer == GL_DEPTH_STENCIL, GL_INVALID_ENUM);

    ctx->m_glClearBufferuiv_enc(ctx, buffer, drawBuffer, value);
}

void GL2Encoder::s_glClearBufferfv(void* self, GLenum buffer, GLint drawBuffer, const GLfloat * value) {
    GL2Encoder *ctx = (GL2Encoder *)self;

    SET_ERROR_IF(buffer == GL_STENCIL || buffer == GL_DEPTH_STENCIL, GL_INVALID_ENUM);

    ctx->m_glClearBufferfv_enc(ctx, buffer, drawBuffer, value);
}

void GL2Encoder::s_glBlitFramebuffer(void* self, GLint srcX0, GLint srcY0, GLint srcX1, GLint srcY1, GLint dstX0, GLint dstY0, GLint dstX1, GLint dstY1, GLbitfield mask, GLenum filter) {
    GL2Encoder *ctx = (GL2Encoder *)self;
    GLClientState* state = ctx->m_state;

    bool validateColor = mask | GL_COLOR_BUFFER_BIT;
    bool validateDepth = mask | GL_DEPTH_BUFFER_BIT;
    bool validateStencil = mask | GL_STENCIL_BUFFER_BIT;

    FboFormatInfo read_fbo_format_info;
    FboFormatInfo draw_fbo_format_info;
    if (validateColor) {
        state->getBoundFramebufferFormat(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, &read_fbo_format_info);
        state->getBoundFramebufferFormat(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, &draw_fbo_format_info);

        if (read_fbo_format_info.type == FBO_ATTACHMENT_TEXTURE &&
            draw_fbo_format_info.type == FBO_ATTACHMENT_TEXTURE) {
            SET_ERROR_IF(
                    state->boundFramebuffer(GL_READ_FRAMEBUFFER) &&
                    state->boundFramebuffer(GL_DRAW_FRAMEBUFFER) &&
                    !GLESv2Validation::blitFramebufferFormat(
                        read_fbo_format_info.tex_type,
                        draw_fbo_format_info.tex_type),
                    GL_INVALID_OPERATION);
        }
    }

    if (validateDepth) {
        state->getBoundFramebufferFormat(GL_READ_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, &read_fbo_format_info);
        state->getBoundFramebufferFormat(GL_DRAW_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, &draw_fbo_format_info);

        if (read_fbo_format_info.type == FBO_ATTACHMENT_RENDERBUFFER &&
            draw_fbo_format_info.type == FBO_ATTACHMENT_RENDERBUFFER) {
            SET_ERROR_IF(
                    state->boundFramebuffer(GL_READ_FRAMEBUFFER) &&
                    state->boundFramebuffer(GL_DRAW_FRAMEBUFFER) &&
                    !GLESv2Validation::blitFramebufferFormat(
                        read_fbo_format_info.rb_format,
                        draw_fbo_format_info.rb_format),
                    GL_INVALID_OPERATION);
        }
    }

    if (validateStencil) {
        state->getBoundFramebufferFormat(GL_READ_FRAMEBUFFER, GL_STENCIL_ATTACHMENT, &read_fbo_format_info);
        state->getBoundFramebufferFormat(GL_DRAW_FRAMEBUFFER, GL_STENCIL_ATTACHMENT, &draw_fbo_format_info);

        if (read_fbo_format_info.type == FBO_ATTACHMENT_RENDERBUFFER &&
            draw_fbo_format_info.type == FBO_ATTACHMENT_RENDERBUFFER) {
            SET_ERROR_IF(
                    state->boundFramebuffer(GL_READ_FRAMEBUFFER) &&
                    state->boundFramebuffer(GL_DRAW_FRAMEBUFFER) &&
                    !GLESv2Validation::blitFramebufferFormat(
                        read_fbo_format_info.rb_format,
                        draw_fbo_format_info.rb_format),
                    GL_INVALID_OPERATION);
        }
    }

    state->getBoundFramebufferFormat(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, &draw_fbo_format_info);
    SET_ERROR_IF(
            draw_fbo_format_info.type == FBO_ATTACHMENT_RENDERBUFFER &&
            draw_fbo_format_info.rb_multisamples > 0,
            GL_INVALID_OPERATION);
    SET_ERROR_IF(
            draw_fbo_format_info.type == FBO_ATTACHMENT_TEXTURE &&
            draw_fbo_format_info.tex_multisamples > 0,
            GL_INVALID_OPERATION);

    state->getBoundFramebufferFormat(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, &read_fbo_format_info);
    SET_ERROR_IF(
            read_fbo_format_info.type == FBO_ATTACHMENT_RENDERBUFFER &&
            read_fbo_format_info.rb_multisamples > 0 &&
            draw_fbo_format_info.type == FBO_ATTACHMENT_RENDERBUFFER &&
            state->boundFramebuffer(GL_READ_FRAMEBUFFER) &&
            state->boundFramebuffer(GL_DRAW_FRAMEBUFFER) &&
            (read_fbo_format_info.rb_format !=
             draw_fbo_format_info.rb_format),
            GL_INVALID_OPERATION);
    SET_ERROR_IF(
            read_fbo_format_info.type == FBO_ATTACHMENT_RENDERBUFFER &&
            read_fbo_format_info.rb_multisamples > 0 &&
            draw_fbo_format_info.type == FBO_ATTACHMENT_RENDERBUFFER &&
            (srcX0 != dstX0 || srcY0 != dstY0 ||
             srcX1 != dstX1 || srcY1 != dstY1),
            GL_INVALID_OPERATION);

	ctx->m_glBlitFramebuffer_enc(ctx,
            srcX0, srcY0, srcX1, srcY1,
            dstX0, dstY0, dstX1, dstY1,
            mask, filter);
}

void GL2Encoder::s_glGetInternalformativ(void* self, GLenum target, GLenum internalformat, GLenum pname, GLsizei bufSize, GLint *params) {
    GL2Encoder *ctx = (GL2Encoder *)self;

    SET_ERROR_IF(pname != GL_NUM_SAMPLE_COUNTS &&
                 pname != GL_SAMPLES,
                 GL_INVALID_ENUM);
    SET_ERROR_IF(!GLESv2Validation::internalFormatTarget(ctx, target), GL_INVALID_ENUM);
    SET_ERROR_IF(!GLESv2Validation::unsizedFormat(internalformat) &&
                 !GLESv2Validation::colorRenderableFormat(ctx, internalformat) &&
                 !GLESv2Validation::depthRenderableFormat(ctx, internalformat) &&
                 !GLESv2Validation::stencilRenderableFormat(ctx, internalformat),
                 GL_INVALID_ENUM);
    SET_ERROR_IF(bufSize < 0, GL_INVALID_VALUE);

    if (bufSize < 1) return;

    // Desktop OpenGL can allow a mindboggling # samples per pixel (such as 64).
    // Limit to 4 (spec minimum) to keep dEQP tests from timing out.
    switch (pname) {
        case GL_NUM_SAMPLE_COUNTS:
            *params = 3;
            break;
        case GL_SAMPLES:
            params[0] = 4;
            if (bufSize > 1) params[1] = 2;
            if (bufSize > 2) params[2] = 1;
            break;
        default:
            break;
    }
}

void GL2Encoder::s_glGenerateMipmap(void* self, GLenum target) {
    GL2Encoder *ctx = (GL2Encoder *)self;
    GLClientState* state = ctx->m_state;

    SET_ERROR_IF(target != GL_TEXTURE_2D &&
                 target != GL_TEXTURE_3D &&
                 target != GL_TEXTURE_CUBE_MAP,
                 GL_INVALID_ENUM);

    GLuint tex = state->getBoundTexture(target);
    GLenum internalformat = state->queryTexInternalFormat(tex);
    GLenum format = state->queryTexFormat(tex);

    SET_ERROR_IF(tex && GLESv2Validation::isCompressedFormat(internalformat),
                 GL_INVALID_OPERATION);
    SET_ERROR_IF(tex &&
                 !GLESv2Validation::unsizedFormat(internalformat) &&
                 !(GLESv2Validation::colorRenderableFormat(ctx, internalformat) &&
                   GLESv2Validation::filterableTexFormat(ctx, internalformat)),
                 GL_INVALID_OPERATION);

    ctx->m_glGenerateMipmap_enc(ctx, target);
}

void GL2Encoder::s_glBindSampler(void* self, GLuint unit, GLuint sampler) {
    GL2Encoder *ctx = (GL2Encoder *)self;
    GLint maxCombinedUnits;
    ctx->glGetIntegerv(ctx, GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS, &maxCombinedUnits);
    SET_ERROR_IF(unit >= maxCombinedUnits, GL_INVALID_VALUE);

    ctx->m_glBindSampler_enc(ctx, unit, sampler);
}

GLsync GL2Encoder::s_glFenceSync(void* self, GLenum condition, GLbitfield flags) {
    GL2Encoder *ctx = (GL2Encoder *)self;
    uint64_t syncHandle = ctx->glFenceSyncAEMU(ctx, condition, flags);
    return (GLsync)(uintptr_t)syncHandle;
}

GLenum GL2Encoder::s_glClientWaitSync(void* self, GLsync wait_on, GLbitfield flags, GLuint64 timeout) {
    GL2Encoder *ctx = (GL2Encoder *)self;
    return ctx->glClientWaitSyncAEMU(ctx, (uint64_t)(uintptr_t)wait_on, flags, timeout);
}

void GL2Encoder::s_glWaitSync(void* self, GLsync wait_on, GLbitfield flags, GLuint64 timeout) {
    GL2Encoder *ctx = (GL2Encoder *)self;
    ctx->glWaitSyncAEMU(ctx, (uint64_t)(uintptr_t)wait_on, flags, timeout);
}

void GL2Encoder::s_glDeleteSync(void* self, GLsync sync) {
    GL2Encoder *ctx = (GL2Encoder *)self;

    if (!sync) return;

    ctx->glDeleteSyncAEMU(ctx, (uint64_t)(uintptr_t)sync);
}

GLboolean GL2Encoder::s_glIsSync(void* self, GLsync sync) {
    GL2Encoder *ctx = (GL2Encoder *)self;
    return ctx->glIsSyncAEMU(ctx, (uint64_t)(uintptr_t)sync);
}

void GL2Encoder::s_glGetSynciv(void* self, GLsync sync, GLenum pname, GLsizei bufSize, GLsizei *length, GLint *values) {
    GL2Encoder *ctx = (GL2Encoder *)self;

    SET_ERROR_IF(bufSize < 0, GL_INVALID_VALUE);

    return ctx->glGetSyncivAEMU(ctx, (uint64_t)(uintptr_t)sync, pname, bufSize, length, values);
}

#define LIMIT_CASE(target, lim) \
    case target: \
        ctx->glGetIntegerv(ctx, lim, &limit); \
        SET_ERROR_IF(index < 0 || index >= limit, GL_INVALID_VALUE); \
        break; \

void GL2Encoder::s_glGetIntegeri_v(void* self, GLenum target, GLuint index, GLint* params) {
    GL2Encoder *ctx = (GL2Encoder *)self;
    GLClientState* state = ctx->m_state;

    GLint limit;

    switch (target) {
    LIMIT_CASE(GL_TRANSFORM_FEEDBACK_BUFFER_BINDING, GL_MAX_TRANSFORM_FEEDBACK_SEPARATE_ATTRIBS)
    LIMIT_CASE(GL_UNIFORM_BUFFER_BINDING, GL_MAX_UNIFORM_BUFFER_BINDINGS)
    LIMIT_CASE(GL_ATOMIC_COUNTER_BUFFER_BINDING, GL_MAX_ATOMIC_COUNTER_BUFFER_BINDINGS)
    LIMIT_CASE(GL_SHADER_STORAGE_BUFFER_BINDING, GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS)
    default:
        break;
    }

    const GLClientState::VertexAttribBindingVector& currBindings =
        state->currentVertexBufferBindings();

    switch (target) {
    case GL_VERTEX_BINDING_DIVISOR:
    case GL_VERTEX_BINDING_OFFSET:
    case GL_VERTEX_BINDING_STRIDE:
    case GL_VERTEX_BINDING_BUFFER:
        SET_ERROR_IF(index < 0 || index > currBindings.size(), GL_INVALID_VALUE);
        break;
    default:
        break;
    }

    switch (target) {
    case GL_VERTEX_BINDING_DIVISOR:
        *params = currBindings[index].divisor;
        return;
    case GL_VERTEX_BINDING_OFFSET:
        *params = currBindings[index].offset;
        return;
    case GL_VERTEX_BINDING_STRIDE:
        *params = currBindings[index].effectiveStride;
        return;
    case GL_VERTEX_BINDING_BUFFER:
        *params = currBindings[index].buffer;
        return;
    default:
        break;
    }

    ctx->safe_glGetIntegeri_v(target, index, params);
}

void GL2Encoder::s_glGetInteger64i_v(void* self, GLenum target, GLuint index, GLint64* params) {
    GL2Encoder *ctx = (GL2Encoder *)self;
    GLClientState* state = ctx->m_state;

    GLint limit;

    switch (target) {
    LIMIT_CASE(GL_TRANSFORM_FEEDBACK_BUFFER_BINDING, GL_MAX_TRANSFORM_FEEDBACK_SEPARATE_ATTRIBS)
    LIMIT_CASE(GL_UNIFORM_BUFFER_BINDING, GL_MAX_UNIFORM_BUFFER_BINDINGS)
    LIMIT_CASE(GL_ATOMIC_COUNTER_BUFFER_BINDING, GL_MAX_ATOMIC_COUNTER_BUFFER_BINDINGS)
    LIMIT_CASE(GL_SHADER_STORAGE_BUFFER_BINDING, GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS)
    default:
        break;
    }

    const GLClientState::VertexAttribBindingVector& currBindings =
        state->currentVertexBufferBindings();

    switch (target) {
    case GL_VERTEX_BINDING_DIVISOR:
    case GL_VERTEX_BINDING_OFFSET:
    case GL_VERTEX_BINDING_STRIDE:
    case GL_VERTEX_BINDING_BUFFER:
        SET_ERROR_IF(index < 0 || index > currBindings.size(), GL_INVALID_VALUE);
        break;
    default:
        break;
    }

    switch (target) {
    case GL_VERTEX_BINDING_DIVISOR:
        *params = currBindings[index].divisor;
        return;
    case GL_VERTEX_BINDING_OFFSET:
        *params = currBindings[index].offset;
        return;
    case GL_VERTEX_BINDING_STRIDE:
        *params = currBindings[index].effectiveStride;
        return;
    case GL_VERTEX_BINDING_BUFFER:
        *params = currBindings[index].buffer;
        return;
    default:
        break;
    }

    ctx->safe_glGetInteger64i_v(target, index, params);
}

void GL2Encoder::s_glGetInteger64v(void* self, GLenum param, GLint64* val) {
    GL2Encoder *ctx = (GL2Encoder *)self;
    ctx->safe_glGetInteger64v(param, val);
}

void GL2Encoder::s_glGetBooleani_v(void* self, GLenum param, GLuint index, GLboolean* val) {
    GL2Encoder *ctx = (GL2Encoder *)self;
    ctx->safe_glGetBooleani_v(param, index, val);
}

void GL2Encoder::s_glGetShaderiv(void* self, GLuint shader, GLenum pname, GLint* params) {
    GL2Encoder *ctx = (GL2Encoder *)self;
    ctx->m_glGetShaderiv_enc(self, shader, pname, params);
    if (pname == GL_SHADER_SOURCE_LENGTH) {
        ShaderData* shaderData = ctx->m_shared->getShaderData(shader);
        if (shaderData) {
            int totalLen = 0;
            for (int i = 0; i < shaderData->sources.size(); i++) {
                totalLen += shaderData->sources[i].size();
            }
            if (totalLen != 0) {
                *params = totalLen + 1; // account for null terminator
            }
        }
    }
}

void GL2Encoder::s_glActiveShaderProgram(void* self, GLuint pipeline, GLuint program) {
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;
    GLSharedGroupPtr shared = ctx->m_shared;

    SET_ERROR_IF(!pipeline, GL_INVALID_OPERATION);
    SET_ERROR_IF(program && !shared->isShaderOrProgramObject(program), GL_INVALID_VALUE);
    SET_ERROR_IF(program && !shared->isProgram(program), GL_INVALID_OPERATION);

    ctx->m_glActiveShaderProgram_enc(ctx, pipeline, program);
    if (!state->currentProgram()) {
        state->setCurrentShaderProgram(program);
    }
}

GLuint GL2Encoder::s_glCreateShaderProgramv(void* self, GLenum type, GLsizei count, const char** strings) {

    GLint* length = NULL;
    GL2Encoder* ctx = (GL2Encoder*)self;

    int len = glUtilsCalcShaderSourceLen((char**)strings, length, count);
    char *str = new char[len + 1];
    glUtilsPackStrings(str, (char**)strings, (GLint*)length, count);
   
    // Do GLSharedGroup and location WorkARound-specific initialization 
    // Phase 1: create a ShaderData and initialize with replaceSamplerExternalWith2D()
    uint32_t spDataId = ctx->m_shared->addNewShaderProgramData();
    ShaderProgramData* spData = ctx->m_shared->getShaderProgramDataById(spDataId);
    ShaderData* sData = spData->shaderData;

    if (!replaceSamplerExternalWith2D(str, sData)) {
        delete [] str;
        ctx->setError(GL_OUT_OF_MEMORY);
        ctx->m_shared->deleteShaderProgramDataById(spDataId);
        return -1;
    }

    GLuint res = ctx->glCreateShaderProgramvAEMU(ctx, type, count, str, len + 1);
    delete [] str;

    // Phase 2: do glLinkProgram-related initialization for locationWorkARound
    GLint linkStatus = 0;
    ctx->glGetProgramiv(self, res, GL_LINK_STATUS ,&linkStatus);
    if (!linkStatus) {
        ctx->m_shared->deleteShaderProgramDataById(spDataId);
        return -1;
    }

    ctx->m_shared->associateGLShaderProgram(res, spDataId);

    GLint numUniforms = 0;
    ctx->glGetProgramiv(self, res, GL_ACTIVE_UNIFORMS, &numUniforms);
    ctx->m_shared->initShaderProgramData(res, numUniforms);

    GLint maxLength=0;
    ctx->glGetProgramiv(self, res, GL_ACTIVE_UNIFORM_MAX_LENGTH, &maxLength);

    GLint size; GLenum uniformType; GLchar* name = new GLchar[maxLength + 1];

    for (GLint i = 0; i < numUniforms; ++i) {
        ctx->glGetActiveUniform(self, res, i, maxLength, NULL, &size, &uniformType, name);
        GLint location = ctx->m_glGetUniformLocation_enc(self, res, name);
        ctx->m_shared->setShaderProgramIndexInfo(res, i, location, size, uniformType, name);
    }

    ctx->m_shared->setupShaderProgramLocationShiftWAR(res);

    delete [] name;

    return res;
}

void GL2Encoder::s_glProgramUniform1f(void* self, GLuint program, GLint location, GLfloat v0)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program, location);
    ctx->m_glProgramUniform1f_enc(self, program, hostLoc, v0);
}

void GL2Encoder::s_glProgramUniform1fv(void* self, GLuint program, GLint location, GLsizei count, const GLfloat *value)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program, location);
    ctx->m_glProgramUniform1fv_enc(self, program, hostLoc, count, value);
}

void GL2Encoder::s_glProgramUniform1i(void* self, GLuint program, GLint location, GLint v0)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program, location);
    ctx->m_glProgramUniform1i_enc(self, program, hostLoc, v0);

    GLClientState* state = ctx->m_state;
    GLSharedGroupPtr shared = ctx->m_shared;
    GLenum target;

    if (shared->setSamplerUniform(program, location, v0, &target)) {
        GLenum origActiveTexture = state->getActiveTextureUnit();
        if (ctx->updateHostTexture2DBinding(GL_TEXTURE0 + v0, target)) {
            ctx->m_glActiveTexture_enc(self, origActiveTexture);
        }
        state->setActiveTextureUnit(origActiveTexture);
    }
}

void GL2Encoder::s_glProgramUniform1iv(void* self, GLuint program, GLint location, GLsizei count, const GLint *value)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program, location);
    ctx->m_glProgramUniform1iv_enc(self, program, hostLoc, count, value);
}

void GL2Encoder::s_glProgramUniform1ui(void* self, GLuint program, GLint location, GLuint v0)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program, location);
    ctx->m_glProgramUniform1ui_enc(self, program, hostLoc, v0);

    GLClientState* state = ctx->m_state;
    GLSharedGroupPtr shared = ctx->m_shared;
    GLenum target;

    if (shared->setSamplerUniform(program, location, v0, &target)) {
        GLenum origActiveTexture = state->getActiveTextureUnit();
        if (ctx->updateHostTexture2DBinding(GL_TEXTURE0 + v0, target)) {
            ctx->m_glActiveTexture_enc(self, origActiveTexture);
        }
        state->setActiveTextureUnit(origActiveTexture);
    }
}

void GL2Encoder::s_glProgramUniform1uiv(void* self, GLuint program, GLint location, GLsizei count, const GLuint *value)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program, location);
    ctx->m_glProgramUniform1uiv_enc(self, program, hostLoc, count, value);
}

void GL2Encoder::s_glProgramUniform2f(void* self, GLuint program, GLint location, GLfloat v0, GLfloat v1)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program, location);
    ctx->m_glProgramUniform2f_enc(self, program, hostLoc, v0, v1);
}

void GL2Encoder::s_glProgramUniform2fv(void* self, GLuint program, GLint location, GLsizei count, const GLfloat *value)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program, location);
    ctx->m_glProgramUniform2fv_enc(self, program, hostLoc, count, value);
}

void GL2Encoder::s_glProgramUniform2i(void* self, GLuint program, GLint location, GLint v0, GLint v1)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program, location);
    ctx->m_glProgramUniform2i_enc(self, program, hostLoc, v0, v1);
}

void GL2Encoder::s_glProgramUniform2iv(void* self, GLuint program, GLint location, GLsizei count, const GLint *value)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program, location);
    ctx->m_glProgramUniform2iv_enc(self, program, hostLoc, count, value);
}

void GL2Encoder::s_glProgramUniform2ui(void* self, GLuint program, GLint location, GLint v0, GLuint v1)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program, location);
    ctx->m_glProgramUniform2ui_enc(self, program, hostLoc, v0, v1);
}

void GL2Encoder::s_glProgramUniform2uiv(void* self, GLuint program, GLint location, GLsizei count, const GLuint *value)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program, location);
    ctx->m_glProgramUniform2uiv_enc(self, program, hostLoc, count, value);
}

void GL2Encoder::s_glProgramUniform3f(void* self, GLuint program, GLint location, GLfloat v0, GLfloat v1, GLfloat v2)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program, location);
    ctx->m_glProgramUniform3f_enc(self, program, hostLoc, v0, v1, v2);
}

void GL2Encoder::s_glProgramUniform3fv(void* self, GLuint program, GLint location, GLsizei count, const GLfloat *value)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program, location);
    ctx->m_glProgramUniform3fv_enc(self, program, hostLoc, count, value);
}

void GL2Encoder::s_glProgramUniform3i(void* self, GLuint program, GLint location, GLint v0, GLint v1, GLint v2)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program, location);
    ctx->m_glProgramUniform3i_enc(self, program, hostLoc, v0, v1, v2);
}

void GL2Encoder::s_glProgramUniform3iv(void* self, GLuint program, GLint location, GLsizei count, const GLint *value)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program, location);
    ctx->m_glProgramUniform3iv_enc(self, program, hostLoc, count, value);
}

void GL2Encoder::s_glProgramUniform3ui(void* self, GLuint program, GLint location, GLint v0, GLint v1, GLuint v2)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program, location);
    ctx->m_glProgramUniform3ui_enc(self, program, hostLoc, v0, v1, v2);
}

void GL2Encoder::s_glProgramUniform3uiv(void* self, GLuint program, GLint location, GLsizei count, const GLuint *value)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program, location);
    ctx->m_glProgramUniform3uiv_enc(self, program, hostLoc, count, value);
}

void GL2Encoder::s_glProgramUniform4f(void* self, GLuint program, GLint location, GLfloat v0, GLfloat v1, GLfloat v2, GLfloat v3)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program, location);
    ctx->m_glProgramUniform4f_enc(self, program, hostLoc, v0, v1, v2, v3);
}

void GL2Encoder::s_glProgramUniform4fv(void* self, GLuint program, GLint location, GLsizei count, const GLfloat *value)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program, location);
    ctx->m_glProgramUniform4fv_enc(self, program, hostLoc, count, value);
}

void GL2Encoder::s_glProgramUniform4i(void* self, GLuint program, GLint location, GLint v0, GLint v1, GLint v2, GLint v3)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program, location);
    ctx->m_glProgramUniform4i_enc(self, program, hostLoc, v0, v1, v2, v3);
}

void GL2Encoder::s_glProgramUniform4iv(void* self, GLuint program, GLint location, GLsizei count, const GLint *value)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program, location);
    ctx->m_glProgramUniform4iv_enc(self, program, hostLoc, count, value);
}

void GL2Encoder::s_glProgramUniform4ui(void* self, GLuint program, GLint location, GLint v0, GLint v1, GLint v2, GLuint v3)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program, location);
    ctx->m_glProgramUniform4ui_enc(self, program, hostLoc, v0, v1, v2, v3);
}

void GL2Encoder::s_glProgramUniform4uiv(void* self, GLuint program, GLint location, GLsizei count, const GLuint *value)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program, location);
    ctx->m_glProgramUniform4uiv_enc(self, program, hostLoc, count, value);
}

void GL2Encoder::s_glProgramUniformMatrix2fv(void* self, GLuint program, GLint location, GLsizei count, GLboolean transpose, const GLfloat *value)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program, location);
    ctx->m_glProgramUniformMatrix2fv_enc(self, program, hostLoc, count, transpose, value);
}

void GL2Encoder::s_glProgramUniformMatrix2x3fv(void* self, GLuint program, GLint location, GLsizei count, GLboolean transpose, const GLfloat *value)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program, location);
    ctx->m_glProgramUniformMatrix2x3fv_enc(self, program, hostLoc, count, transpose, value);
}

void GL2Encoder::s_glProgramUniformMatrix2x4fv(void* self, GLuint program, GLint location, GLsizei count, GLboolean transpose, const GLfloat *value)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program, location);
    ctx->m_glProgramUniformMatrix2x4fv_enc(self, program, hostLoc, count, transpose, value);
}

void GL2Encoder::s_glProgramUniformMatrix3fv(void* self, GLuint program, GLint location, GLsizei count, GLboolean transpose, const GLfloat *value)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program, location);
    ctx->m_glProgramUniformMatrix3fv_enc(self, program, hostLoc, count, transpose, value);
}

void GL2Encoder::s_glProgramUniformMatrix3x2fv(void* self, GLuint program, GLint location, GLsizei count, GLboolean transpose, const GLfloat *value)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program, location);
    ctx->m_glProgramUniformMatrix3x2fv_enc(self, program, hostLoc, count, transpose, value);
}

void GL2Encoder::s_glProgramUniformMatrix3x4fv(void* self, GLuint program, GLint location, GLsizei count, GLboolean transpose, const GLfloat *value)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program, location);
    ctx->m_glProgramUniformMatrix3x4fv_enc(self, program, hostLoc, count, transpose, value);
}

void GL2Encoder::s_glProgramUniformMatrix4fv(void* self, GLuint program, GLint location, GLsizei count, GLboolean transpose, const GLfloat *value)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program, location);
    ctx->m_glProgramUniformMatrix4fv_enc(self, program, hostLoc, count, transpose, value);
}

void GL2Encoder::s_glProgramUniformMatrix4x2fv(void* self, GLuint program, GLint location, GLsizei count, GLboolean transpose, const GLfloat *value)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program, location);
    ctx->m_glProgramUniformMatrix4x2fv_enc(self, program, hostLoc, count, transpose, value);
}

void GL2Encoder::s_glProgramUniformMatrix4x3fv(void* self, GLuint program, GLint location, GLsizei count, GLboolean transpose, const GLfloat *value)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLint hostLoc = ctx->m_shared->locationWARAppToHost(program, location);
    ctx->m_glProgramUniformMatrix4x3fv_enc(self, program, hostLoc, count, transpose, value);
}

void GL2Encoder::s_glProgramParameteri(void* self, GLuint program, GLenum pname, GLint value) {
    GL2Encoder* ctx = (GL2Encoder*)self;
    ctx->m_glProgramParameteri_enc(self, program, pname, value);
}

void GL2Encoder::s_glUseProgramStages(void *self, GLuint pipeline, GLbitfield stages, GLuint program)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;
    GLSharedGroupPtr shared = ctx->m_shared;

    SET_ERROR_IF(!pipeline, GL_INVALID_OPERATION);
    SET_ERROR_IF(program && !shared->isShaderOrProgramObject(program), GL_INVALID_VALUE);
    SET_ERROR_IF(program && !shared->isProgram(program), GL_INVALID_OPERATION);

    ctx->m_glUseProgramStages_enc(self, pipeline, stages, program);
    state->associateProgramWithPipeline(program, pipeline);

    // There is an active non-separable shader program in effect; no need to update external/2D bindings.
    if (state->currentProgram()) {
        return;
    }

    // Otherwise, update host texture 2D bindings.
    ctx->updateHostTexture2DBindingsFromProgramData(program);
}

void GL2Encoder::s_glBindProgramPipeline(void* self, GLuint pipeline)
{
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;

    ctx->m_glBindProgramPipeline_enc(self, pipeline);

    // There is an active non-separable shader program in effect; no need to update external/2D bindings.
    if (!pipeline || state->currentProgram()) {
        return;
    }

    GLClientState::ProgramPipelineIterator it = state->programPipelineBegin();
    for (; it != state->programPipelineEnd(); ++it) {
        if (it->second == pipeline) {
            ctx->updateHostTexture2DBindingsFromProgramData(it->first);
        }
    }
}

void GL2Encoder::s_glGetProgramResourceiv(void* self, GLuint program, GLenum programInterface, GLuint index, GLsizei propCount, const GLenum * props, GLsizei bufSize, GLsizei * length, GLint * params) {
    GL2Encoder *ctx = (GL2Encoder*)self;
    SET_ERROR_IF(bufSize < 0, GL_INVALID_VALUE);
    if (bufSize == 0) {
        if (length) *length = 0;
        return;
    }

    // Avoid modifying |name| if |*length| < bufSize.
    GLint* intermediate = new GLint[bufSize];
    GLsizei* myLength = length ? length : new GLsizei;
    bool needFreeLength = length == NULL;

    ctx->m_glGetProgramResourceiv_enc(self, program, programInterface, index, propCount, props, bufSize, myLength, intermediate);
    GLsizei writtenInts = *myLength;
    memcpy(params, intermediate, writtenInts * sizeof(GLint));

    delete [] intermediate;
    if (needFreeLength)
        delete myLength;
}

GLuint GL2Encoder::s_glGetProgramResourceIndex(void* self, GLuint program, GLenum programInterface, const char* name) {
    GL2Encoder *ctx = (GL2Encoder*)self;
    return ctx->m_glGetProgramResourceIndex_enc(self, program, programInterface, name);
}

GLint GL2Encoder::s_glGetProgramResourceLocation(void* self, GLuint program, GLenum programInterface, const char* name) {
    GL2Encoder *ctx = (GL2Encoder*)self;
    return ctx->m_glGetProgramResourceLocation_enc(self, program, programInterface, name);
}

void GL2Encoder::s_glGetProgramResourceName(void* self, GLuint program, GLenum programInterface, GLuint index, GLsizei bufSize, GLsizei* length, char* name) {
    GL2Encoder *ctx = (GL2Encoder*)self;
    SET_ERROR_IF(bufSize < 0, GL_INVALID_VALUE);
    if (bufSize == 0) {
        if (length) *length = 0;
        return;
    }

    // Avoid modifying |name| if |*length| < bufSize.
    char* intermediate = new char[bufSize];
    GLsizei* myLength = length ? length : new GLsizei;
    bool needFreeLength = length == NULL;

    ctx->m_glGetProgramResourceName_enc(self, program, programInterface, index, bufSize, myLength, intermediate);
    GLsizei writtenStrLen = *myLength;
    memcpy(name, intermediate, writtenStrLen + 1);

    delete [] intermediate;
    if (needFreeLength)
        delete myLength;
}

void GL2Encoder::s_glGetProgramPipelineInfoLog(void* self, GLuint pipeline, GLsizei bufSize, GLsizei* length, GLchar* infoLog) {
    GL2Encoder *ctx = (GL2Encoder*)self;
    SET_ERROR_IF(bufSize < 0, GL_INVALID_VALUE);
    if (bufSize == 0) {
        if (length) *length = 0;
        return;
    }

    // Avoid modifying |infoLog| if |*length| < bufSize.
    GLchar* intermediate = new GLchar[bufSize];
    GLsizei* myLength = length ? length : new GLsizei;
    bool needFreeLength = length == NULL;

    ctx->m_glGetProgramPipelineInfoLog_enc(self, pipeline, bufSize, myLength, intermediate);
    GLsizei writtenStrLen = *myLength;
    memcpy(infoLog, intermediate, writtenStrLen + 1);

    delete [] intermediate;
    if (needFreeLength)
        delete myLength;
}

void GL2Encoder::s_glVertexAttribFormat(void* self, GLuint attribindex, GLint size, GLenum type, GLboolean normalized, GLuint relativeoffset) {
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;

    VALIDATE_VERTEX_ATTRIB_INDEX(attribindex);
    SET_ERROR_IF(!state->currentVertexArrayObject(), GL_INVALID_OPERATION);

    state->setVertexAttribFormat(attribindex, size, type, normalized, relativeoffset, false);
    ctx->m_glVertexAttribFormat_enc(ctx, attribindex, size, type, normalized, relativeoffset);
}

void GL2Encoder::s_glVertexAttribIFormat(void* self, GLuint attribindex, GLint size, GLenum type, GLuint relativeoffset) {
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;

    VALIDATE_VERTEX_ATTRIB_INDEX(attribindex);
    SET_ERROR_IF(!state->currentVertexArrayObject(), GL_INVALID_OPERATION);

    state->setVertexAttribFormat(attribindex, size, type, GL_FALSE, relativeoffset, true);
    ctx->m_glVertexAttribIFormat_enc(ctx, attribindex, size, type, relativeoffset);
}

void GL2Encoder::s_glVertexBindingDivisor(void* self, GLuint bindingindex, GLuint divisor) {
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;

    SET_ERROR_IF(!state->currentVertexArrayObject(), GL_INVALID_OPERATION);

    state->setVertexBindingDivisor(bindingindex, divisor);
    ctx->m_glVertexBindingDivisor_enc(ctx, bindingindex, divisor);
}

void GL2Encoder::s_glVertexAttribBinding(void* self, GLuint attribindex, GLuint bindingindex) {
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;
    VALIDATE_VERTEX_ATTRIB_INDEX(attribindex);
    SET_ERROR_IF(!state->currentVertexArrayObject(), GL_INVALID_OPERATION);

    state->setVertexAttribBinding(attribindex, bindingindex);
    ctx->m_glVertexAttribBinding_enc(ctx, attribindex, bindingindex);
}

void GL2Encoder::s_glBindVertexBuffer(void* self, GLuint bindingindex, GLuint buffer, GLintptr offset, GLintptr stride) {
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;

    SET_ERROR_IF(offset < 0, GL_INVALID_VALUE);

    GLint maxStride;
    ctx->glGetIntegerv(ctx, GL_MAX_VERTEX_ATTRIB_STRIDE, &maxStride);
    SET_ERROR_IF(stride < 0 || stride > maxStride, GL_INVALID_VALUE);

    SET_ERROR_IF(!state->currentVertexArrayObject(), GL_INVALID_OPERATION);

    state->bindIndexedBuffer(0, bindingindex, buffer, offset, 0, stride, stride);
    ctx->m_glBindVertexBuffer_enc(ctx, bindingindex, buffer, offset, stride);
}

void GL2Encoder::s_glDrawArraysIndirect(void* self, GLenum mode, const void* indirect) {
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;

    bool hasClientArrays = false;
    ctx->getVBOUsage(&hasClientArrays, NULL);

    SET_ERROR_IF(hasClientArrays, GL_INVALID_OPERATION);
    SET_ERROR_IF(!state->currentVertexArrayObject(), GL_INVALID_OPERATION);
    SET_ERROR_IF(!ctx->boundBuffer(GL_DRAW_INDIRECT_BUFFER), GL_INVALID_OPERATION);

    GLuint indirectStructSize = glUtilsIndirectStructSize(INDIRECT_COMMAND_DRAWARRAYS);
    if (ctx->boundBuffer(GL_DRAW_INDIRECT_BUFFER)) {
        // BufferData* buf = ctx->getBufferData(target);
        // if (buf) {
        //     SET_ERROR_IF((GLuint)(uintptr_t)indirect + indirectStructSize > buf->m_size, GL_INVALID_VALUE);
        // }
        ctx->glDrawArraysIndirectOffsetAEMU(ctx, mode, (uintptr_t)indirect);
    } else {
        // Client command structs are technically allowed in desktop OpenGL, but not in ES.
        // This is purely for debug/dev purposes.
        ctx->glDrawArraysIndirectDataAEMU(ctx, mode, indirect, indirectStructSize);
    }
}

void GL2Encoder::s_glDrawElementsIndirect(void* self, GLenum mode, GLenum type, const void* indirect) {
    GL2Encoder *ctx = (GL2Encoder*)self;

    GLClientState* state = ctx->m_state;

    bool hasClientArrays = false;
    ctx->getVBOUsage(&hasClientArrays, NULL);

    SET_ERROR_IF(hasClientArrays, GL_INVALID_OPERATION);
    SET_ERROR_IF(!state->currentVertexArrayObject(), GL_INVALID_OPERATION);
    SET_ERROR_IF(!ctx->boundBuffer(GL_DRAW_INDIRECT_BUFFER), GL_INVALID_OPERATION);

    SET_ERROR_IF(ctx->m_state->getTransformFeedbackActiveUnpaused(), GL_INVALID_OPERATION);

    GLuint indirectStructSize = glUtilsIndirectStructSize(INDIRECT_COMMAND_DRAWELEMENTS);
    if (ctx->boundBuffer(GL_DRAW_INDIRECT_BUFFER)) {
        // BufferData* buf = ctx->getBufferData(target);
        // if (buf) {
        //     SET_ERROR_IF((GLuint)(uintptr_t)indirect + indirectStructSize > buf->m_size, GL_INVALID_VALUE);
        // }
        ctx->glDrawElementsIndirectOffsetAEMU(ctx, mode, type, (uintptr_t)indirect);
    } else {
        // Client command structs are technically allowed in desktop OpenGL, but not in ES.
        // This is purely for debug/dev purposes.
        ctx->glDrawElementsIndirectDataAEMU(ctx, mode, type, indirect, indirectStructSize);
    }

}

void GL2Encoder::s_glTexStorage2DMultisample(void* self, GLenum target, GLsizei samples, GLenum internalformat, GLsizei width, GLsizei height, GLboolean fixedsamplelocations) {
    GL2Encoder *ctx = (GL2Encoder*)self;
    GLClientState* state = ctx->m_state;

    SET_ERROR_IF(target != GL_TEXTURE_2D_MULTISAMPLE, GL_INVALID_ENUM);
    SET_ERROR_IF(!GLESv2Validation::pixelInternalFormat(internalformat), GL_INVALID_ENUM);
    SET_ERROR_IF(!state->getBoundTexture(target), GL_INVALID_OPERATION);
    SET_ERROR_IF(width < 1 || height < 1, GL_INVALID_VALUE);
    SET_ERROR_IF(state->isBoundTextureImmutableFormat(target), GL_INVALID_OPERATION);
    GLint max_samples;
    ctx->s_glGetInternalformativ(ctx, target, internalformat, GL_SAMPLES, 1, &max_samples);
    SET_ERROR_IF(samples > max_samples, GL_INVALID_OPERATION);

    state->setBoundTextureInternalFormat(target, internalformat);
    state->setBoundTextureDims(target, 0, width, height, 1);
    state->setBoundTextureImmutableFormat(target);
    state->setBoundTextureSamples(target, samples);

    ctx->m_glTexStorage2DMultisample_enc(ctx, target, samples, internalformat, width, height, fixedsamplelocations);
}

