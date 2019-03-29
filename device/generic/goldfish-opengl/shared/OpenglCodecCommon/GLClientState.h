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
#ifndef _GL_CLIENT_STATE_H_
#define _GL_CLIENT_STATE_H_

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

#include <stdio.h>
#include <stdlib.h>
#include "ErrorLog.h"
#include "codec_defs.h"

#include <vector>
#include <map>
#include <set>

// Tracking framebuffer objects:
// which framebuffer is bound,
// and which texture names
// are currently bound to which attachment points.
struct FboProps {
    GLuint name;
    bool previouslyBound;
    std::vector<GLuint> colorAttachmenti_textures;
    GLuint depthAttachment_texture;
    GLuint stencilAttachment_texture;
    GLuint depthstencilAttachment_texture;

    std::vector<bool> colorAttachmenti_hasTex;
    bool depthAttachment_hasTexObj;
    bool stencilAttachment_hasTexObj;
    bool depthstencilAttachment_hasTexObj;

    std::vector<GLuint> colorAttachmenti_rbos;
    GLuint depthAttachment_rbo;
    GLuint stencilAttachment_rbo;
    GLuint depthstencilAttachment_rbo;

    std::vector<bool> colorAttachmenti_hasRbo;
    bool depthAttachment_hasRbo;
    bool stencilAttachment_hasRbo;
    bool depthstencilAttachment_hasRbo;
};

// Same for Rbo's
struct RboProps {
    GLenum target;
    GLuint name;
    GLenum format;
    GLsizei multisamples;
    bool previouslyBound;
};

// Enum for describing whether a framebuffer attachment
// is a texture or renderbuffer.
enum FboAttachmentType {
    FBO_ATTACHMENT_RENDERBUFFER = 0,
    FBO_ATTACHMENT_TEXTURE = 1,
    FBO_ATTACHMENT_NONE = 2
};

// Tracking FBO format
struct FboFormatInfo {
    FboAttachmentType type;
    GLenum rb_format;
    GLsizei rb_multisamples;

    GLint tex_internalformat;
    GLenum tex_format;
    GLenum tex_type;
    GLsizei tex_multisamples;
};

class GLClientState {
public:
    typedef enum {
        VERTEX_LOCATION = 0,
        NORMAL_LOCATION = 1,
        COLOR_LOCATION = 2,
        POINTSIZE_LOCATION = 3,
        TEXCOORD0_LOCATION = 4,
        TEXCOORD1_LOCATION = 5,
        TEXCOORD2_LOCATION = 6,
        TEXCOORD3_LOCATION = 7,
        TEXCOORD4_LOCATION = 8,
        TEXCOORD5_LOCATION = 9,
        TEXCOORD6_LOCATION = 10,
        TEXCOORD7_LOCATION = 11,
        MATRIXINDEX_LOCATION = 12,
        WEIGHT_LOCATION = 13,
        LAST_LOCATION = 14
    } StateLocation;

    typedef struct {
        GLint enabled;
        GLint size;
        GLenum type;
        GLsizei stride;
        void *data;
        GLuint reloffset;
        GLuint bufferObject;
        GLenum glConst;
        unsigned int elementSize;
        bool enableDirty;  // true if any enable state has changed since last draw
        bool normalized;
        GLuint divisor;
        bool isInt;
        int bindingindex;
    } VertexAttribState;

    struct BufferBinding {
        GLintptr offset;
        GLintptr stride;
        GLintptr effectiveStride;
        GLsizeiptr size;
        GLuint buffer;
        GLuint divisor;
    };

    typedef std::vector<VertexAttribState> VertexAttribStateVector;
    typedef std::vector<BufferBinding> VertexAttribBindingVector;

    struct VAOState {
        VAOState(GLuint ibo, int nLoc, int nBindings) :
            element_array_buffer_binding(ibo),
            attribState(nLoc),
            bindingState(nBindings) { }
        VertexAttribStateVector attribState;
        VertexAttribBindingVector bindingState;
        GLuint element_array_buffer_binding;
    };

    typedef std::map<GLuint, VAOState> VAOStateMap;
    struct VAOStateRef {
        VAOStateRef() { }
        VAOStateRef(
                VAOStateMap::iterator iter) : it(iter) { }
        VertexAttribState& operator[](size_t k) { return it->second.attribState[k]; }
        BufferBinding& bufferBinding(size_t k) { return it->second.bindingState[k]; }
        VertexAttribBindingVector& bufferBindings() { return it->second.bindingState; }
        const VertexAttribBindingVector& bufferBindings_const() const { return it->second.bindingState; }
        GLuint vaoId() const { return it->first; }
        GLuint& iboId() { return it->second.element_array_buffer_binding; }
        VAOStateMap::iterator it;
    };

    typedef struct {
        int unpack_alignment;

        int unpack_row_length;
        int unpack_image_height;
        int unpack_skip_pixels;
        int unpack_skip_rows;
        int unpack_skip_images;

        int pack_alignment;

        int pack_row_length;
        int pack_skip_pixels;
        int pack_skip_rows;
    } PixelStoreState;

    enum {
        MAX_TEXTURE_UNITS = 256,
    };

public:
    GLClientState();
    GLClientState(int majorVersion, int minorVersion);
    ~GLClientState();
    int nLocations() { return m_nLocations; }
    const PixelStoreState *pixelStoreState() { return &m_pixelStore; }
    int setPixelStore(GLenum param, GLint value);
    GLuint currentVertexArrayObject() const { return m_currVaoState.vaoId(); }
    const VertexAttribBindingVector& currentVertexBufferBindings() const {
        return m_currVaoState.bufferBindings_const();
    }

    GLuint currentArrayVbo() { return m_arrayBuffer; }
    GLuint currentIndexVbo() { return m_currVaoState.iboId(); }
    void enable(int location, int state);
    // Vertex array objects and vertex attributes
    void addVertexArrayObjects(GLsizei n, GLuint* arrays);
    void removeVertexArrayObjects(GLsizei n, const GLuint* arrays);
    void addVertexArrayObject(GLuint name);
    void removeVertexArrayObject(GLuint name);
    void setVertexArrayObject(GLuint vao);
    bool isVertexArrayObject(GLuint vao) const;
    void setVertexAttribState(int  location, int size, GLenum type, GLboolean normalized, GLsizei stride, const void *data, bool isInt = false);
    void setVertexBindingDivisor(int bindingindex, GLuint divisor);
    const BufferBinding& getCurrAttributeBindingInfo(int attribindex);
    void setVertexAttribBinding(int attribindex, int bindingindex);
    void setVertexAttribFormat(int location, int size, GLenum type, GLboolean normalized, GLuint reloffset, bool isInt = false);
    const VertexAttribState& getState(int location);
    const VertexAttribState& getStateAndEnableDirty(int location, bool *enableChanged);
    int getLocation(GLenum loc);
    void setActiveTexture(int texUnit) {m_activeTexture = texUnit; };
    int getActiveTexture() const { return m_activeTexture; }
    void setMaxVertexAttribs(int val) {
        m_maxVertexAttribs = val;
        m_maxVertexAttribsDirty = false;
    }

    void addBuffer(GLuint id);
    void removeBuffer(GLuint id);
    bool bufferIdExists(GLuint id) const;
    void unBindBuffer(GLuint id);

    int bindBuffer(GLenum target, GLuint id);
    void bindIndexedBuffer(GLenum target, GLuint index, GLuint buffer, GLintptr offset, GLsizeiptr size, GLintptr stride, GLintptr effectiveStride);
    int getMaxIndexedBufferBindings(GLenum target) const;

    int getBuffer(GLenum target);

    size_t pixelDataSize(GLsizei width, GLsizei height, GLsizei depth, GLenum format, GLenum type, int pack) const;
    size_t pboNeededDataSize(GLsizei width, GLsizei height, GLsizei depth, GLenum format, GLenum type, int pack) const;
    size_t clearBufferNumElts(GLenum buffer) const;

    void setCurrentProgram(GLint program) { m_currentProgram = program; }
    void setCurrentShaderProgram(GLint program) { m_currentShaderProgram = program; }
    GLint currentProgram() const { return m_currentProgram; }
    GLint currentShaderProgram() const { return m_currentShaderProgram; }

    struct UniformBlockInfoKey {
        GLuint program;
        GLuint uniformBlockIndex;
    };
    struct UniformBlockInfoKeyCompare {
        bool operator() (const UniformBlockInfoKey& a,
                         const UniformBlockInfoKey& b) const {
            if (a.program != b.program) return a.program < b.program;
            if (a.uniformBlockIndex != b.uniformBlockIndex) return a.uniformBlockIndex < b.uniformBlockIndex;
            return false;
        }
    };
    struct UniformBlockUniformInfo {
        size_t numActiveUniforms;
    };

    typedef std::map<UniformBlockInfoKey, UniformBlockUniformInfo, UniformBlockInfoKeyCompare> UniformBlockInfoMap;
    UniformBlockInfoMap m_uniformBlockInfoMap;

    void setNumActiveUniformsInUniformBlock(GLuint program, GLuint uniformBlockIndex, GLint numActiveUniforms);
    size_t numActiveUniformsInUniformBlock(GLuint program, GLuint uniformBlockIndex) const;

    typedef std::map<GLuint, GLuint> ProgramPipelineMap;
    typedef ProgramPipelineMap::iterator ProgramPipelineIterator;
    void associateProgramWithPipeline(GLuint program, GLuint pipeline);
    ProgramPipelineIterator programPipelineBegin();
    ProgramPipelineIterator programPipelineEnd();

    /* OES_EGL_image_external
     *
     * These functions manipulate GL state which interacts with the
     * OES_EGL_image_external extension, to support client-side emulation on
     * top of host implementations that don't have it.
     *
     * Most of these calls should only be used with TEXTURE_2D or
     * TEXTURE_EXTERNAL_OES texture targets; TEXTURE_CUBE_MAP or other extension
     * targets should bypass this. An exception is bindTexture(), which should
     * see all glBindTexture() calls for any target.
     */

    // glActiveTexture(GL_TEXTURE0 + i)
    // Sets the active texture unit. Up to MAX_TEXTURE_UNITS are supported.
    GLenum setActiveTextureUnit(GLenum texture);
    GLenum getActiveTextureUnit() const;

    // glEnable(GL_TEXTURE_(2D|EXTERNAL_OES))
    void enableTextureTarget(GLenum target);

    // glDisable(GL_TEXTURE_(2D|EXTERNAL_OES))
    void disableTextureTarget(GLenum target);

    // Implements the target priority logic:
    // * Return GL_TEXTURE_EXTERNAL_OES if enabled, else
    // * Return GL_TEXTURE_2D if enabled, else
    // * Return the allDisabled value.
    // For some cases passing GL_TEXTURE_2D for allDisabled makes callee code
    // simpler; for other cases passing a recognizable enum like GL_ZERO or
    // GL_INVALID_ENUM is appropriate.
    GLenum getPriorityEnabledTarget(GLenum allDisabled) const;

    // glBindTexture(GL_TEXTURE_*, ...)
    // Set the target binding of the active texture unit to texture. Returns
    // GL_NO_ERROR on success or GL_INVALID_OPERATION if the texture has
    // previously been bound to a different target. If firstUse is not NULL,
    // it is set to indicate whether this is the first use of the texture.
    // For accurate error detection, bindTexture should be called for *all*
    // targets, not just 2D and EXTERNAL_OES.
    GLenum bindTexture(GLenum target, GLuint texture, GLboolean* firstUse);
    void setBoundEGLImage(GLenum target, GLeglImageOES image);

    // Return the texture currently bound to GL_TEXTURE_(2D|EXTERNAL_OES).
    GLuint getBoundTexture(GLenum target) const;
    // Other publicly-visible texture queries
    GLenum queryTexLastBoundTarget(GLuint name) const;
    GLenum queryTexFormat(GLuint name) const;
    GLint queryTexInternalFormat(GLuint name) const;
    GLsizei queryTexWidth(GLsizei level, GLuint name) const;
    GLsizei queryTexHeight(GLsizei level, GLuint name) const;
    GLsizei queryTexDepth(GLsizei level, GLuint name) const;
    bool queryTexEGLImageBacked(GLuint name) const;

    // For AMD GPUs, it is easy for the emulator to segfault
    // (esp. in dEQP) when a cube map is defined using glCopyTexImage2D
    // and uses GL_LUMINANCE as internal format.
    // In particular, the segfault happens when negative components of
    // cube maps are defined before positive ones,
    // This procedure checks internal state to see if we have defined
    // the positive component of a cube map already. If not, it returns
    // which positive component needs to be defined first.
    // If there is no need for the extra definition, 0 is returned.
    GLenum copyTexImageLuminanceCubeMapAMDWorkaround(GLenum target, GLint level,
                                                     GLenum internalformat);

    // Tracks the format of the currently bound texture.
    // This is to pass dEQP tests for fbo completeness.
    void setBoundTextureInternalFormat(GLenum target, GLint format);
    void setBoundTextureFormat(GLenum target, GLenum format);
    void setBoundTextureType(GLenum target, GLenum type);
    void setBoundTextureDims(GLenum target, GLsizei level, GLsizei width, GLsizei height, GLsizei depth);
    void setBoundTextureSamples(GLenum target, GLsizei samples);

    // glTexStorage2D disallows any change in texture format after it is set for a particular texture.
    void setBoundTextureImmutableFormat(GLenum target);
    bool isBoundTextureImmutableFormat(GLenum target) const;

    // glDeleteTextures(...)
    // Remove references to the to-be-deleted textures.
    void deleteTextures(GLsizei n, const GLuint* textures);

    // Render buffer objects
    void addRenderbuffers(GLsizei n, GLuint* renderbuffers);
    void removeRenderbuffers(GLsizei n, const GLuint* renderbuffers);
    bool usedRenderbufferName(GLuint name) const;
    void bindRenderbuffer(GLenum target, GLuint name);
    GLuint boundRenderbuffer() const;
    void setBoundRenderbufferFormat(GLenum format);
    void setBoundRenderbufferSamples(GLsizei samples);

    // Frame buffer objects
    void addFramebuffers(GLsizei n, GLuint* framebuffers);
    void removeFramebuffers(GLsizei n, const GLuint* framebuffers);
    bool usedFramebufferName(GLuint name) const;
    void bindFramebuffer(GLenum target, GLuint name);
    void setCheckFramebufferStatus(GLenum target, GLenum status);
    GLenum getCheckFramebufferStatus(GLenum target) const;
    GLuint boundFramebuffer(GLenum target) const;

    // Texture object -> FBO
    void attachTextureObject(GLenum target, GLenum attachment, GLuint texture);
    GLuint getFboAttachmentTextureId(GLenum target, GLenum attachment) const;

    // RBO -> FBO
    void detachRbo(GLuint renderbuffer);
    void detachRboFromFbo(GLenum target, GLenum attachment, GLuint renderbuffer);
    void attachRbo(GLenum target, GLenum attachment, GLuint renderbuffer);
    GLuint getFboAttachmentRboId(GLenum target, GLenum attachment) const;

    // FBO attachments in general
    bool attachmentHasObject(GLenum target, GLenum attachment) const;
    GLuint objectOfAttachment(GLenum target, GLenum attachment) const;

    // Transform feedback state
    void setTransformFeedbackActiveUnpaused(bool activeUnpaused);
    bool getTransformFeedbackActiveUnpaused() const;

    void setTextureData(SharedTextureDataMap* sharedTexData);
    // set eglsurface property on default framebuffer
    // if coming from eglMakeCurrent
    void fromMakeCurrent();
    // set indexed buffer state.
    // We need to query the underlying OpenGL to get
    // accurate values for indexed buffers
    // and # render targets.
    void initFromCaps(
        int max_transform_feedback_separate_attribs,
        int max_uniform_buffer_bindings,
        int max_atomic_counter_buffer_bindings,
        int max_shader_storage_buffer_bindings,
        int max_vertex_attrib_bindings,
        int max_color_attachments,
        int max_draw_buffers);
    bool needsInitFromCaps() const;

    // Queries the format backing the current framebuffer.
    // Type differs depending on whether the attachment
    // is a texture or renderbuffer.
    void getBoundFramebufferFormat(
            GLenum target,
            GLenum attachment,
            FboFormatInfo* res_info) const;
    FboAttachmentType getBoundFramebufferAttachmentType(
            GLenum target,
            GLenum attachment) const;
    int getMaxColorAttachments() const;
    int getMaxDrawBuffers() const;
private:
    void init();
    bool m_initialized;
    PixelStoreState m_pixelStore;

    std::set<GLuint> mBufferIds;

    // GL_ARRAY_BUFFER_BINDING is separate from VAO state
    GLuint m_arrayBuffer;
    VAOStateMap m_vaoMap;
    VAOStateRef m_currVaoState;

    // Other buffer id's, other targets
    GLuint m_copyReadBuffer;
    GLuint m_copyWriteBuffer;

    GLuint m_pixelPackBuffer;
    GLuint m_pixelUnpackBuffer;

    GLuint m_transformFeedbackBuffer;
    GLuint m_uniformBuffer;

    GLuint m_atomicCounterBuffer;
    GLuint m_dispatchIndirectBuffer;
    GLuint m_drawIndirectBuffer;
    GLuint m_shaderStorageBuffer;

    bool m_transformFeedbackActiveUnpaused;

    int m_max_transform_feedback_separate_attribs;
    int m_max_uniform_buffer_bindings;
    int m_max_atomic_counter_buffer_bindings;
    int m_max_shader_storage_buffer_bindings;
    int m_max_vertex_attrib_bindings;
    std::vector<BufferBinding> m_indexedTransformFeedbackBuffers;
    std::vector<BufferBinding> m_indexedUniformBuffers;
    std::vector<BufferBinding> m_indexedAtomicCounterBuffers;
    std::vector<BufferBinding> m_indexedShaderStorageBuffers;

    int m_glesMajorVersion;
    int m_glesMinorVersion;
    int m_maxVertexAttribs;
    bool m_maxVertexAttribsDirty;
    int m_nLocations;
    int m_activeTexture;
    GLint m_currentProgram;
    GLint m_currentShaderProgram;
    ProgramPipelineMap m_programPipelines;

    enum TextureTarget {
        TEXTURE_2D = 0,
        TEXTURE_EXTERNAL = 1,
        TEXTURE_CUBE_MAP = 2,
        TEXTURE_2D_ARRAY = 3,
        TEXTURE_3D = 4,
        TEXTURE_2D_MULTISAMPLE = 5,
        TEXTURE_TARGET_COUNT
    };
    struct TextureUnit {
        unsigned int enables;
        GLuint texture[TEXTURE_TARGET_COUNT];
    };
    struct TextureState {
        TextureUnit unit[MAX_TEXTURE_UNITS];
        TextureUnit* activeUnit;
        // Initialized from shared group.
        SharedTextureDataMap* textureRecs;
    };
    TextureState m_tex;

    // State tracking of cube map definitions.
    // Currently used only for driver workarounds
    // when using GL_LUMINANCE and defining cube maps with
    // glCopyTexImage2D.
    struct CubeMapDef {
        GLuint id;
        GLenum target;
        GLint level;
        GLenum internalformat;
    };
    struct CubeMapDefCompare {
        bool operator() (const CubeMapDef& a,
                         const CubeMapDef& b) const {
            if (a.id != b.id) return a.id < b.id;
            if (a.target != b.target) return a.target < b.target;
            if (a.level != b.level) return a.level < b.level;
            if (a.internalformat != b.internalformat)
                return a.internalformat < b.internalformat;
            return false;
        }
    };
    std::set<CubeMapDef, CubeMapDefCompare> m_cubeMapDefs;
    void writeCopyTexImageState(GLenum target, GLint level,
                                GLenum internalformat);
    GLenum copyTexImageNeededTarget(GLenum target, GLint level,
                                    GLenum internalformat);

    int m_max_color_attachments;
    int m_max_draw_buffers;
    struct RboState {
        GLuint boundRenderbuffer;
        size_t boundRenderbufferIndex;
        std::vector<RboProps> rboData;
    };
    RboState mRboState;
    void addFreshRenderbuffer(GLuint name);
    void setBoundRenderbufferIndex();
    size_t getRboIndex(GLuint name) const;
    RboProps& boundRboProps();
    const RboProps& boundRboProps_const() const;

    struct FboState {
        GLuint boundDrawFramebuffer;
        GLuint boundReadFramebuffer;
        size_t boundFramebufferIndex;
        std::map<GLuint, FboProps> fboData;
        GLenum drawFboCheckStatus;
        GLenum readFboCheckStatus;
    };
    FboState mFboState;
    void addFreshFramebuffer(GLuint name);
    FboProps& boundFboProps(GLenum target);
    const FboProps& boundFboProps_const(GLenum target) const;

    // Querying framebuffer format
    GLenum queryRboFormat(GLuint name) const;
    GLsizei queryRboSamples(GLuint name) const;
    GLenum queryTexType(GLuint name) const;
    GLsizei queryTexSamples(GLuint name) const;

    static int compareTexId(const void* pid, const void* prec);
    TextureRec* addTextureRec(GLuint id, GLenum target);
    TextureRec* getTextureRec(GLuint id) const;

public:
    void getClientStatePointer(GLenum pname, GLvoid** params);

    template <class T>
    int getVertexAttribParameter(GLuint index, GLenum param, T *ptr)
    {
        bool handled = true;
        const VertexAttribState& vertexAttrib = getState(index);
        const BufferBinding& vertexAttribBufferBinding =
            m_currVaoState.bufferBindings_const()[vertexAttrib.bindingindex];

        switch(param) {
#define GL_VERTEX_ATTRIB_BINDING 0x82D4
        case GL_VERTEX_ATTRIB_BINDING:
            *ptr = (T)vertexAttrib.bindingindex;
            break;
#define GL_VERTEX_ATTRIB_RELATIVE_OFFSET 0x82D5
        case GL_VERTEX_ATTRIB_RELATIVE_OFFSET:
            *ptr = (T)vertexAttrib.reloffset;
            break;
        case GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING:
            *ptr = (T)(vertexAttribBufferBinding.buffer);
            break;
        case GL_VERTEX_ATTRIB_ARRAY_ENABLED:
            *ptr = (T)(vertexAttrib.enabled);
            break;
#define GL_VERTEX_ATTRIB_ARRAY_INTEGER 0x88FD
        case GL_VERTEX_ATTRIB_ARRAY_INTEGER:
            *ptr = (T)(vertexAttrib.isInt);
            break;
        case GL_VERTEX_ATTRIB_ARRAY_SIZE:
            *ptr = (T)(vertexAttrib.size);
            break;
        case GL_VERTEX_ATTRIB_ARRAY_STRIDE:
            *ptr = (T)(vertexAttribBufferBinding.stride);
            break;
        case GL_VERTEX_ATTRIB_ARRAY_TYPE:
            *ptr = (T)(vertexAttrib.type);
            break;
        case GL_VERTEX_ATTRIB_ARRAY_NORMALIZED:
            *ptr = (T)(vertexAttrib.normalized);
            break;
        case GL_CURRENT_VERTEX_ATTRIB:
            handled = false;
            break;
        default:
            handled = false;
            ERR("unknown vertex-attrib parameter param %d\n", param);
        }
        return handled;
    }

    template <class T>
    bool getClientStateParameter(GLenum param, T* out)
    {
        bool isClientStateParam = false;
        switch (param) {
        case GL_CLIENT_ACTIVE_TEXTURE: {
            GLint tex = getActiveTexture() + GL_TEXTURE0;
            *out = tex;
            isClientStateParam = true;
            break;
            }
        case GL_VERTEX_ARRAY_SIZE: {
            const GLClientState::VertexAttribState& state = getState(GLClientState::VERTEX_LOCATION);
            *out = state.size;
            isClientStateParam = true;
            break;
            }
        case GL_VERTEX_ARRAY_TYPE: {
            const GLClientState::VertexAttribState& state = getState(GLClientState::VERTEX_LOCATION);
            *out = state.type;
            isClientStateParam = true;
            break;
            }
        case GL_VERTEX_ARRAY_STRIDE: {
            const GLClientState::VertexAttribState& state = getState(GLClientState::VERTEX_LOCATION);
            *out = state.stride;
            isClientStateParam = true;
            break;
            }
        case GL_COLOR_ARRAY_SIZE: {
            const GLClientState::VertexAttribState& state = getState(GLClientState::COLOR_LOCATION);
            *out = state.size;
            isClientStateParam = true;
            break;
            }
        case GL_COLOR_ARRAY_TYPE: {
            const GLClientState::VertexAttribState& state = getState(GLClientState::COLOR_LOCATION);
            *out = state.type;
            isClientStateParam = true;
            break;
            }
        case GL_COLOR_ARRAY_STRIDE: {
            const GLClientState::VertexAttribState& state = getState(GLClientState::COLOR_LOCATION);
            *out = state.stride;
            isClientStateParam = true;
            break;
            }
        case GL_NORMAL_ARRAY_TYPE: {
            const GLClientState::VertexAttribState& state = getState(GLClientState::NORMAL_LOCATION);
            *out = state.type;
            isClientStateParam = true;
            break;
            }
        case GL_NORMAL_ARRAY_STRIDE: {
            const GLClientState::VertexAttribState& state = getState(GLClientState::NORMAL_LOCATION);
            *out = state.stride;
            isClientStateParam = true;
            break;
            }
        case GL_TEXTURE_COORD_ARRAY_SIZE: {
            const GLClientState::VertexAttribState& state = getState(getActiveTexture() + GLClientState::TEXCOORD0_LOCATION);
            *out = state.size;
            isClientStateParam = true;
            break;
            }
        case GL_TEXTURE_COORD_ARRAY_TYPE: {
            const GLClientState::VertexAttribState& state = getState(getActiveTexture() + GLClientState::TEXCOORD0_LOCATION);
            *out = state.type;
            isClientStateParam = true;
            break;
            }
        case GL_TEXTURE_COORD_ARRAY_STRIDE: {
            const GLClientState::VertexAttribState& state = getState(getActiveTexture() + GLClientState::TEXCOORD0_LOCATION);
            *out = state.stride;
            isClientStateParam = true;
            break;
            }
        case GL_POINT_SIZE_ARRAY_TYPE_OES: {
            const GLClientState::VertexAttribState& state = getState(GLClientState::POINTSIZE_LOCATION);
            *out = state.type;
            isClientStateParam = true;
            break;
            }
        case GL_POINT_SIZE_ARRAY_STRIDE_OES: {
            const GLClientState::VertexAttribState& state = getState(GLClientState::POINTSIZE_LOCATION);
            *out = state.stride;
            isClientStateParam = true;
            break;
            }
        case GL_MATRIX_INDEX_ARRAY_SIZE_OES: {
            const GLClientState::VertexAttribState& state = getState(GLClientState::MATRIXINDEX_LOCATION);
            *out = state.size;
            isClientStateParam = true;
            break;
            }
        case GL_MATRIX_INDEX_ARRAY_TYPE_OES: {
            const GLClientState::VertexAttribState& state = getState(GLClientState::MATRIXINDEX_LOCATION);
            *out = state.type;
            isClientStateParam = true;
            break;
            }
        case GL_MATRIX_INDEX_ARRAY_STRIDE_OES: {
            const GLClientState::VertexAttribState& state = getState(GLClientState::MATRIXINDEX_LOCATION);
            *out = state.stride;
            isClientStateParam = true;
            break;
            }
        case GL_WEIGHT_ARRAY_SIZE_OES: {
            const GLClientState::VertexAttribState& state = getState(GLClientState::WEIGHT_LOCATION);
            *out = state.size;
            isClientStateParam = true;
            break;
            }
        case GL_WEIGHT_ARRAY_TYPE_OES: {
            const GLClientState::VertexAttribState& state = getState(GLClientState::WEIGHT_LOCATION);
            *out = state.type;
            isClientStateParam = true;
            break;
            }
        case GL_WEIGHT_ARRAY_STRIDE_OES: {
            const GLClientState::VertexAttribState& state = getState(GLClientState::WEIGHT_LOCATION);
            *out = state.stride;
            isClientStateParam = true;
            break;
            }
        case GL_VERTEX_ARRAY_BUFFER_BINDING: {
            const GLClientState::VertexAttribState& state = getState(GLClientState::VERTEX_LOCATION);
            *out = state.bufferObject;
            isClientStateParam = true;
            break;
            }
        case GL_NORMAL_ARRAY_BUFFER_BINDING: {
            const GLClientState::VertexAttribState& state = getState(GLClientState::NORMAL_LOCATION);
            *out = state.bufferObject;
            isClientStateParam = true;
            break;
            }
        case GL_COLOR_ARRAY_BUFFER_BINDING: {
            const GLClientState::VertexAttribState& state = getState(GLClientState::COLOR_LOCATION);
            *out = state.bufferObject;
            isClientStateParam = true;
            break;
            }
        case GL_TEXTURE_COORD_ARRAY_BUFFER_BINDING: {
            const GLClientState::VertexAttribState& state = getState(getActiveTexture()+GLClientState::TEXCOORD0_LOCATION);
            *out = state.bufferObject;
            isClientStateParam = true;
            break;
            }
        case GL_POINT_SIZE_ARRAY_BUFFER_BINDING_OES: {
            const GLClientState::VertexAttribState& state = getState(GLClientState::POINTSIZE_LOCATION);
            *out = state.bufferObject;
            isClientStateParam = true;
            break;
            }
        case GL_MATRIX_INDEX_ARRAY_BUFFER_BINDING_OES: {
            const GLClientState::VertexAttribState& state = getState(GLClientState::MATRIXINDEX_LOCATION);
            *out = state.bufferObject;
            isClientStateParam = true;
            break;
            }
        case GL_WEIGHT_ARRAY_BUFFER_BINDING_OES: {
            const GLClientState::VertexAttribState& state = getState(GLClientState::WEIGHT_LOCATION);
            *out = state.bufferObject;
            isClientStateParam = true;
            break;
            }
        case GL_ARRAY_BUFFER_BINDING: {
            int buffer = getBuffer(GL_ARRAY_BUFFER);
            *out = buffer;
            isClientStateParam = true;
            break;
            }
        case GL_ELEMENT_ARRAY_BUFFER_BINDING: {
            int buffer = getBuffer(GL_ELEMENT_ARRAY_BUFFER);
            *out = buffer;
            isClientStateParam = true;
            break;
            }
        case GL_MAX_VERTEX_ATTRIBS: {
            if (m_maxVertexAttribsDirty) {
                isClientStateParam = false;
            } else {
                *out = m_maxVertexAttribs;
                isClientStateParam = true;
            }
            break;
        }
        }
        return isClientStateParam;
    }

};
#endif
