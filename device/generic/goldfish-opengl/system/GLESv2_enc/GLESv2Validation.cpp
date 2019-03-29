/*
* Copyright (C) 2016 The Android Open Source Project
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

#include "GLESv2Validation.h"

#include <sstream>

namespace GLESv2Validation {

GLbitfield allBufferMapAccessFlags =
    GL_MAP_READ_BIT | GL_MAP_WRITE_BIT |
    GL_MAP_INVALIDATE_RANGE_BIT |
    GL_MAP_INVALIDATE_BUFFER_BIT |
    GL_MAP_FLUSH_EXPLICIT_BIT |
    GL_MAP_UNSYNCHRONIZED_BIT;

bool bufferTarget(GL2Encoder* ctx, GLenum target) {
    int glesMajorVersion = ctx->majorVersion();
    int glesMinorVersion = ctx->minorVersion();
    switch (target) {
    case GL_ARRAY_BUFFER: // Vertex attributes
    case GL_ELEMENT_ARRAY_BUFFER: // Vertex array indices
        return true;
        // GLES 3.0 buffers
    case GL_COPY_READ_BUFFER: // Buffer copy source
    case GL_COPY_WRITE_BUFFER: // Buffer copy destination
    case GL_PIXEL_PACK_BUFFER: // Pixel read target
    case GL_PIXEL_UNPACK_BUFFER: // Texture data source
    case GL_TRANSFORM_FEEDBACK_BUFFER: // Transform feedback buffer
    case GL_UNIFORM_BUFFER: // Uniform block storage
        return glesMajorVersion >= 3;
        // GLES 3.1 buffers
    case GL_ATOMIC_COUNTER_BUFFER: // Atomic counter storage
    case GL_DISPATCH_INDIRECT_BUFFER: // Indirect compute dispatch commands
    case GL_DRAW_INDIRECT_BUFFER: // Indirect command arguments
    case GL_SHADER_STORAGE_BUFFER: // Read-write storage for shaders
        return glesMajorVersion >= 3 && glesMinorVersion >= 1;
    default:
        return false;
    }
}

bool bufferParam(GL2Encoder* ctx, GLenum pname) {
    int glesMajorVersion = ctx->majorVersion();
    int glesMinorVersion = ctx->minorVersion();
    switch (pname) {
    case GL_BUFFER_SIZE:
    case GL_BUFFER_USAGE:
        return true;
    case GL_BUFFER_ACCESS_FLAGS:
    case GL_BUFFER_MAPPED:
    case GL_BUFFER_MAP_LENGTH:
    case GL_BUFFER_MAP_OFFSET:
        return glesMajorVersion >= 3;
    default:
        return false;
    }
}

bool pixelStoreParam(GL2Encoder* ctx, GLenum param) {
    int glesMajorVersion = ctx->majorVersion();
    switch(param) {
    case GL_UNPACK_ALIGNMENT:
    case GL_PACK_ALIGNMENT:
        return true;
    case GL_UNPACK_ROW_LENGTH:
    case GL_UNPACK_IMAGE_HEIGHT:
    case GL_UNPACK_SKIP_PIXELS:
    case GL_UNPACK_SKIP_ROWS:
    case GL_UNPACK_SKIP_IMAGES:
    case GL_PACK_ROW_LENGTH:
    case GL_PACK_SKIP_PIXELS:
    case GL_PACK_SKIP_ROWS:
        return glesMajorVersion >= 3;
    default:
        return false;
    }
}

bool pixelStoreValue(GLenum param, GLint value) {
    switch(param) {
    case GL_UNPACK_ALIGNMENT:
    case GL_PACK_ALIGNMENT:
        return (value == 1) || (value == 2) || (value == 4) || (value == 8);
    case GL_UNPACK_ROW_LENGTH:
    case GL_UNPACK_IMAGE_HEIGHT:
    case GL_UNPACK_SKIP_PIXELS:
    case GL_UNPACK_SKIP_ROWS:
    case GL_UNPACK_SKIP_IMAGES:
    case GL_PACK_ROW_LENGTH:
    case GL_PACK_SKIP_PIXELS:
    case GL_PACK_SKIP_ROWS:
        return value >= 0;
    default:
        return false;
    }
}

bool rboFormat(GL2Encoder* ctx, GLenum internalformat) {
    int glesMajorVersion = ctx->majorVersion();

    switch (internalformat) {
    // Funny internal formats
    // that will cause an incomplete framebuffer
    // attachment error pre-gles3. For dEQP,
    // we can also just abort early here in
    // RenderbufferStorage with a GL_INVALID_ENUM.
    case GL_DEPTH_COMPONENT32F:
    case GL_R8:
    case GL_R8UI:
    case GL_R8I:
    case GL_R16UI:
    case GL_R16I:
    case GL_R32UI:
    case GL_R32I:
    case GL_RG8:
    case GL_RG8UI:
    case GL_RG8I:
    case GL_RG16UI:
    case GL_RG16I:
    case GL_RG32UI:
    case GL_RG32I:
    case GL_SRGB8_ALPHA8:
    case GL_RGBA8UI:
    case GL_RGBA8I:
    case GL_RGB10_A2:
    case GL_RGB10_A2UI:
    case GL_RGBA16UI:
    case GL_RGBA16I:
    case GL_RGBA32I:
    case GL_RGBA32UI:
    case GL_RGB32F:
        return glesMajorVersion >= 3;
        // These 4 formats are still not OK,
        // but dEQP expects GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT or
        // GL_FRAMEBUFFER_UNSUPPORTED if the extension is not present,
        // not a GL_INVALID_ENUM from earlier on.
        // So let's forward these to the rest of
        // FBO initialization
        // In GLES 3, these are rejected immediately if not
        // supported.
    case GL_R16F:
    case GL_RG16F:
    case GL_RGBA16F:
    case GL_R32F:
    case GL_RG32F:
    case GL_RGBA32F:
    case GL_R11F_G11F_B10F:
        return glesMajorVersion >= 3 && ctx->hasExtension("GL_EXT_color_buffer_float");
    case GL_RGB16F:
        return glesMajorVersion >= 3 && ctx->hasExtension("GL_EXT_color_buffer_half_float");
        // dEQP expects GL_FRAMEBUFFER_UNSUPPORTED or GL_FRAMEBUFFER_COMPLETE
        // for this format
        // These formats are OK
    case GL_DEPTH_COMPONENT16:
    case GL_DEPTH_COMPONENT24:
    case GL_DEPTH_COMPONENT32_OES:
    case GL_RGBA4:
    case GL_RGB5_A1:
    case GL_RGB565:
    case GL_RGB8_OES:
    case GL_RGBA8_OES:
    case GL_STENCIL_INDEX8:
    case GL_DEPTH32F_STENCIL8:
    case GL_DEPTH24_STENCIL8_OES:
        return true;
        break;
        // Everything else: still not OK,
        // and they need the GL_INVALID_ENUM
    }
    return false;
}

bool framebufferTarget(GL2Encoder* ctx, GLenum target) {
    int glesMajorVersion = ctx->majorVersion();
    switch (target) {
    case GL_FRAMEBUFFER:
        return true;
    case GL_DRAW_FRAMEBUFFER:
    case GL_READ_FRAMEBUFFER:
        return glesMajorVersion >= 3;
    }
    return false;
}

bool framebufferAttachment(GL2Encoder* ctx, GLenum attachment) {
    int glesMajorVersion = ctx->majorVersion();
    switch (attachment) {
    case GL_COLOR_ATTACHMENT0:
    case GL_DEPTH_ATTACHMENT:
    case GL_STENCIL_ATTACHMENT:
        return true;
    case GL_COLOR_ATTACHMENT1:
    case GL_COLOR_ATTACHMENT2:
    case GL_COLOR_ATTACHMENT3:
    case GL_COLOR_ATTACHMENT4:
    case GL_COLOR_ATTACHMENT5:
    case GL_COLOR_ATTACHMENT6:
    case GL_COLOR_ATTACHMENT7:
    case GL_COLOR_ATTACHMENT8:
    case GL_COLOR_ATTACHMENT9:
    case GL_COLOR_ATTACHMENT10:
    case GL_COLOR_ATTACHMENT11:
    case GL_COLOR_ATTACHMENT12:
    case GL_COLOR_ATTACHMENT13:
    case GL_COLOR_ATTACHMENT14:
    case GL_COLOR_ATTACHMENT15:
    case GL_DEPTH_STENCIL_ATTACHMENT:
        return glesMajorVersion >= 3;
    }
    return false;
}

bool readPixelsFormat(GLenum format) {
    switch (format) {
    case GL_RED:
    case GL_RED_INTEGER:
    case GL_RG:
    case GL_RG_INTEGER:
    case GL_RGB:
    case GL_RGB_INTEGER:
    case GL_RGBA:
    case GL_RGBA_INTEGER:
    case GL_LUMINANCE_ALPHA:
    case GL_LUMINANCE:
    case GL_ALPHA:
        return true;
    }
    return false;
}

bool readPixelsType(GLenum format) {
    switch (format) {
    case GL_UNSIGNED_BYTE:
    case GL_BYTE:
    case GL_HALF_FLOAT:
    case GL_FLOAT:
    case GL_INT:
    case GL_UNSIGNED_SHORT_5_6_5:
    case GL_UNSIGNED_SHORT_4_4_4_4:
    case GL_UNSIGNED_SHORT_5_5_5_1:
    case GL_UNSIGNED_INT:
    case GL_UNSIGNED_INT_2_10_10_10_REV:
    case GL_UNSIGNED_INT_10F_11F_11F_REV:
    case GL_UNSIGNED_INT_5_9_9_9_REV:
        return true;
    }
    return false;
}

bool vertexAttribType(GL2Encoder* ctx, GLenum type)
{
    int glesMajorVersion = ctx->majorVersion();
    bool retval = false;
    switch (type) {
    case GL_BYTE:
    case GL_UNSIGNED_BYTE:
    case GL_SHORT:
    case GL_UNSIGNED_SHORT:
    case GL_FIXED:
    case GL_FLOAT:
    // The following are technically only available if certain GLES2 extensions are.
    // However, they are supported by desktop GL3, which is a reasonable requirement
    // for the desktop GL version. Therefore, consider them valid.
    case GL_INT:
    case GL_UNSIGNED_INT:
    case GL_HALF_FLOAT_OES:
        retval = true;
        break;
    case GL_HALF_FLOAT:
    case GL_INT_2_10_10_10_REV:
    case GL_UNSIGNED_INT_2_10_10_10_REV:
        retval = glesMajorVersion >= 3;
        break;
    }
    return retval;
}

bool readPixelsFboFormatMatch(GLenum format, GLenum type, GLenum fboTexType) {
#define INVALID_TYPE_MATCH(x, y) \
    if (type == x && fboTexType == y) return false; \
    if (type == y && fboTexType == x) return false; \

    // These are meant to reject additional format/type mismatches
    // not caught by underlying system.
    INVALID_TYPE_MATCH(GL_FLOAT, GL_BYTE)
    INVALID_TYPE_MATCH(GL_FLOAT, GL_UNSIGNED_BYTE)
    INVALID_TYPE_MATCH(GL_FLOAT, GL_UNSIGNED_INT)
    INVALID_TYPE_MATCH(GL_FLOAT, GL_INT)

    return true;
}

bool blitFramebufferFormat(GLenum readFormat, GLenum drawFormat) {
#define INVALID_MATCH(x, y) \
    if (readFormat == x && drawFormat == y) return false; \
    if (readFormat == y && drawFormat == x) return false; \

    INVALID_MATCH(GL_FLOAT, GL_BYTE)
    INVALID_MATCH(GL_FLOAT, GL_UNSIGNED_BYTE)
    INVALID_MATCH(GL_FLOAT, GL_UNSIGNED_INT)
    INVALID_MATCH(GL_FLOAT, GL_INT)
    INVALID_MATCH(GL_INT, GL_UNSIGNED_BYTE);
    INVALID_MATCH(GL_UNSIGNED_INT, GL_UNSIGNED_BYTE);
    INVALID_MATCH(GL_INT, GL_BYTE);
    INVALID_MATCH(GL_UNSIGNED_INT, GL_BYTE);
    INVALID_MATCH(GL_DEPTH32F_STENCIL8, GL_DEPTH24_STENCIL8);

    return true;
}

bool textureTarget(GL2Encoder* ctx, GLenum target) {
    int glesMajorVersion = ctx->majorVersion();
    int glesMinorVersion = ctx->minorVersion();
    switch (target) {
    case GL_TEXTURE_EXTERNAL_OES:
    case GL_TEXTURE_2D:
    case GL_TEXTURE_CUBE_MAP:
    case GL_TEXTURE_CUBE_MAP_POSITIVE_X:
    case GL_TEXTURE_CUBE_MAP_POSITIVE_Y:
    case GL_TEXTURE_CUBE_MAP_POSITIVE_Z:
    case GL_TEXTURE_CUBE_MAP_NEGATIVE_X:
    case GL_TEXTURE_CUBE_MAP_NEGATIVE_Y:
    case GL_TEXTURE_CUBE_MAP_NEGATIVE_Z:
        return true;
    case GL_TEXTURE_2D_ARRAY:
    case GL_TEXTURE_3D:
        return glesMajorVersion >= 3;
    case GL_TEXTURE_2D_MULTISAMPLE:
        return glesMajorVersion >= 3 &&
               glesMinorVersion >= 1;
    default:
        break;
    }
    return false;
}

static GLsizei ceildiv(GLsizei x, GLsizei y) {
    return (x + y - 1) / y;
}

GLsizei compressedTexImageSize(GLenum internalformat, GLsizei width, GLsizei height, GLsizei depth) {
    GLsizei base_size = ceildiv(width, 4) * ceildiv(height, 4) * depth;
#define COMPRESSED_TEX_IMAGE_SIZE_CASE(internal, multiplier) \
    case internal: \
        return base_size * multiplier; \

    switch (internalformat) {
    COMPRESSED_TEX_IMAGE_SIZE_CASE(GL_COMPRESSED_R11_EAC, 8)
    COMPRESSED_TEX_IMAGE_SIZE_CASE(GL_COMPRESSED_SIGNED_R11_EAC, 8)
    COMPRESSED_TEX_IMAGE_SIZE_CASE(GL_COMPRESSED_RG11_EAC, 16)
    COMPRESSED_TEX_IMAGE_SIZE_CASE(GL_COMPRESSED_SIGNED_RG11_EAC, 16)
    COMPRESSED_TEX_IMAGE_SIZE_CASE(GL_COMPRESSED_RGB8_ETC2, 8)
    COMPRESSED_TEX_IMAGE_SIZE_CASE(GL_COMPRESSED_SRGB8_ETC2, 8)
    COMPRESSED_TEX_IMAGE_SIZE_CASE(GL_COMPRESSED_RGB8_PUNCHTHROUGH_ALPHA1_ETC2, 8)
    COMPRESSED_TEX_IMAGE_SIZE_CASE(GL_COMPRESSED_SRGB8_PUNCHTHROUGH_ALPHA1_ETC2, 8)
    COMPRESSED_TEX_IMAGE_SIZE_CASE(GL_COMPRESSED_RGBA8_ETC2_EAC, 16)
    COMPRESSED_TEX_IMAGE_SIZE_CASE(GL_COMPRESSED_SRGB8_ALPHA8_ETC2_EAC, 16)
    default:
        break;
    }

    return 0;
}

bool isCompressedFormat(GLenum internalformat) {
#define COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(internal) \
    case internal: \
        return true; \

    switch (internalformat) {
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_R11_EAC)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_SIGNED_R11_EAC)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_RG11_EAC)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_SIGNED_RG11_EAC)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_RGB8_ETC2)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_SRGB8_ETC2)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_RGB8_PUNCHTHROUGH_ALPHA1_ETC2)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_SRGB8_PUNCHTHROUGH_ALPHA1_ETC2)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_RGBA8_ETC2_EAC)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_SRGB8_ALPHA8_ETC2_EAC)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_RGBA_ASTC_4x4_KHR)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_RGBA_ASTC_5x4_KHR)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_RGBA_ASTC_5x5_KHR)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_RGBA_ASTC_6x5_KHR)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_RGBA_ASTC_6x6_KHR)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_RGBA_ASTC_8x5_KHR)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_RGBA_ASTC_8x6_KHR)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_RGBA_ASTC_8x8_KHR)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_RGBA_ASTC_10x5_KHR)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_RGBA_ASTC_10x6_KHR)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_RGBA_ASTC_10x8_KHR)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_RGBA_ASTC_10x10_KHR)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_RGBA_ASTC_12x10_KHR)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_RGBA_ASTC_12x12_KHR)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_SRGB8_ALPHA8_ASTC_4x4_KHR)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_SRGB8_ALPHA8_ASTC_5x4_KHR)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_SRGB8_ALPHA8_ASTC_5x5_KHR)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_SRGB8_ALPHA8_ASTC_6x5_KHR)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_SRGB8_ALPHA8_ASTC_6x6_KHR)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_SRGB8_ALPHA8_ASTC_8x5_KHR)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_SRGB8_ALPHA8_ASTC_8x6_KHR)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_SRGB8_ALPHA8_ASTC_8x8_KHR)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_SRGB8_ALPHA8_ASTC_10x5_KHR)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_SRGB8_ALPHA8_ASTC_10x6_KHR)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_SRGB8_ALPHA8_ASTC_10x8_KHR)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_SRGB8_ALPHA8_ASTC_10x10_KHR)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_SRGB8_ALPHA8_ASTC_12x10_KHR)
    COMPRESSED_TEX_IMAGE_IS_COMPRESSED_FORMAT_CASE(GL_COMPRESSED_SRGB8_ALPHA8_ASTC_12x12_KHR)
    default:
        break;
    }
    return false;
}

bool supportedCompressedFormat(GL2Encoder* ctx, GLenum internalformat) {
    int glesMajorVersion = ctx->majorVersion();
    int glesMinorVersion = ctx->minorVersion();
#define COMPRESSED_TEX_IMAGE_SUPPORT_CASE(internal, maj, min) \
    case internal: \
        if (maj < 3) return true; \
        if (glesMajorVersion < maj) return false; \
        if (glesMinorVersion < min) return false; \
        break; \

#define COMPRESSED_TEX_IMAGE_NOTSUPPORTED(internal) \
    case internal: \
        return false ; \

    switch (internalformat) {
    COMPRESSED_TEX_IMAGE_SUPPORT_CASE(GL_COMPRESSED_R11_EAC, 2, 0)
    COMPRESSED_TEX_IMAGE_SUPPORT_CASE(GL_COMPRESSED_SIGNED_R11_EAC, 2, 0)
    COMPRESSED_TEX_IMAGE_SUPPORT_CASE(GL_COMPRESSED_RG11_EAC, 2, 0)
    COMPRESSED_TEX_IMAGE_SUPPORT_CASE(GL_COMPRESSED_SIGNED_RG11_EAC, 2, 0)
    COMPRESSED_TEX_IMAGE_SUPPORT_CASE(GL_COMPRESSED_RGB8_ETC2, 2, 0)
    COMPRESSED_TEX_IMAGE_SUPPORT_CASE(GL_COMPRESSED_SRGB8_ETC2, 2, 0)
    COMPRESSED_TEX_IMAGE_SUPPORT_CASE(GL_COMPRESSED_RGB8_PUNCHTHROUGH_ALPHA1_ETC2, 2, 0)
    COMPRESSED_TEX_IMAGE_SUPPORT_CASE(GL_COMPRESSED_SRGB8_PUNCHTHROUGH_ALPHA1_ETC2, 2, 0)
    COMPRESSED_TEX_IMAGE_SUPPORT_CASE(GL_COMPRESSED_RGBA8_ETC2_EAC, 2, 0)
    COMPRESSED_TEX_IMAGE_SUPPORT_CASE(GL_COMPRESSED_SRGB8_ALPHA8_ETC2_EAC, 2, 0)
    COMPRESSED_TEX_IMAGE_NOTSUPPORTED(GL_COMPRESSED_RGBA_ASTC_4x4_KHR)
    COMPRESSED_TEX_IMAGE_NOTSUPPORTED(GL_COMPRESSED_RGBA_ASTC_5x4_KHR)
    COMPRESSED_TEX_IMAGE_NOTSUPPORTED(GL_COMPRESSED_RGBA_ASTC_5x5_KHR)
    COMPRESSED_TEX_IMAGE_NOTSUPPORTED(GL_COMPRESSED_RGBA_ASTC_6x5_KHR)
    COMPRESSED_TEX_IMAGE_NOTSUPPORTED(GL_COMPRESSED_RGBA_ASTC_6x6_KHR)
    COMPRESSED_TEX_IMAGE_NOTSUPPORTED(GL_COMPRESSED_RGBA_ASTC_8x5_KHR)
    COMPRESSED_TEX_IMAGE_NOTSUPPORTED(GL_COMPRESSED_RGBA_ASTC_8x6_KHR)
    COMPRESSED_TEX_IMAGE_NOTSUPPORTED(GL_COMPRESSED_RGBA_ASTC_8x8_KHR)
    COMPRESSED_TEX_IMAGE_NOTSUPPORTED(GL_COMPRESSED_RGBA_ASTC_10x5_KHR)
    COMPRESSED_TEX_IMAGE_NOTSUPPORTED(GL_COMPRESSED_RGBA_ASTC_10x6_KHR)
    COMPRESSED_TEX_IMAGE_NOTSUPPORTED(GL_COMPRESSED_RGBA_ASTC_10x8_KHR)
    COMPRESSED_TEX_IMAGE_NOTSUPPORTED(GL_COMPRESSED_RGBA_ASTC_10x10_KHR)
    COMPRESSED_TEX_IMAGE_NOTSUPPORTED(GL_COMPRESSED_RGBA_ASTC_12x10_KHR)
    COMPRESSED_TEX_IMAGE_NOTSUPPORTED(GL_COMPRESSED_RGBA_ASTC_12x12_KHR)
    COMPRESSED_TEX_IMAGE_NOTSUPPORTED(GL_COMPRESSED_SRGB8_ALPHA8_ASTC_4x4_KHR)
    COMPRESSED_TEX_IMAGE_NOTSUPPORTED(GL_COMPRESSED_SRGB8_ALPHA8_ASTC_5x4_KHR)
    COMPRESSED_TEX_IMAGE_NOTSUPPORTED(GL_COMPRESSED_SRGB8_ALPHA8_ASTC_5x5_KHR)
    COMPRESSED_TEX_IMAGE_NOTSUPPORTED(GL_COMPRESSED_SRGB8_ALPHA8_ASTC_6x5_KHR)
    COMPRESSED_TEX_IMAGE_NOTSUPPORTED(GL_COMPRESSED_SRGB8_ALPHA8_ASTC_6x6_KHR)
    COMPRESSED_TEX_IMAGE_NOTSUPPORTED(GL_COMPRESSED_SRGB8_ALPHA8_ASTC_8x5_KHR)
    COMPRESSED_TEX_IMAGE_NOTSUPPORTED(GL_COMPRESSED_SRGB8_ALPHA8_ASTC_8x6_KHR)
    COMPRESSED_TEX_IMAGE_NOTSUPPORTED(GL_COMPRESSED_SRGB8_ALPHA8_ASTC_8x8_KHR)
    COMPRESSED_TEX_IMAGE_NOTSUPPORTED(GL_COMPRESSED_SRGB8_ALPHA8_ASTC_10x5_KHR)
    COMPRESSED_TEX_IMAGE_NOTSUPPORTED(GL_COMPRESSED_SRGB8_ALPHA8_ASTC_10x6_KHR)
    COMPRESSED_TEX_IMAGE_NOTSUPPORTED(GL_COMPRESSED_SRGB8_ALPHA8_ASTC_10x8_KHR)
    COMPRESSED_TEX_IMAGE_NOTSUPPORTED(GL_COMPRESSED_SRGB8_ALPHA8_ASTC_10x10_KHR)
    COMPRESSED_TEX_IMAGE_NOTSUPPORTED(GL_COMPRESSED_SRGB8_ALPHA8_ASTC_12x10_KHR)
    COMPRESSED_TEX_IMAGE_NOTSUPPORTED(GL_COMPRESSED_SRGB8_ALPHA8_ASTC_12x12_KHR)
    default:
        break;
    }
    return true;
}

bool unsizedFormat(GLenum format) {
    switch (format) {
    case GL_RED:
    case GL_RED_INTEGER:
    case GL_DEPTH_COMPONENT:
    case GL_DEPTH_STENCIL:
    case GL_RG:
    case GL_RG_INTEGER:
    case GL_RGB:
    case GL_RGB_INTEGER:
    case GL_RGBA:
    case GL_RGBA_INTEGER:
    case GL_ALPHA:
    case GL_LUMINANCE:
    case GL_LUMINANCE_ALPHA:
        return true;
    }
    return false;
}

// TODO: fix this
bool filterableTexFormat(GL2Encoder* ctx, GLenum internalformat) {
    switch (internalformat) {
    case GL_R32F:
    case GL_RG32F:
    case GL_RGB32F:
    case GL_RGBA32F:
        return ctx->hasExtension("GL_OES_texture_float");
    case GL_R8UI:
    case GL_R8I:
    case GL_R16UI:
    case GL_R16I:
    case GL_R32UI:
    case GL_R32I:
    case GL_RG8UI:
    case GL_RG8I:
    case GL_RG16UI:
    case GL_RG16I:
    case GL_RG32UI:
    case GL_RG32I:
    case GL_RGBA8UI:
    case GL_RGBA8I:
    case GL_RGB10_A2UI:
    case GL_RGBA16UI:
    case GL_RGBA16I:
    case GL_RGBA32I:
    case GL_RGBA32UI:
        return false;
    }
    return true;
}


bool colorRenderableFormat(GL2Encoder* ctx, GLenum internalformat) {
    int glesMajorVersion = ctx->majorVersion();
    switch (internalformat) {
    case GL_R8:
    case GL_RG8:
    case GL_RGB8:
    case GL_RGB565:
    case GL_RGBA4:
    case GL_RGB5_A1:
    case GL_RGBA8:
    case GL_RGB10_A2:
    case GL_RGB10_A2UI:
    case GL_SRGB8_ALPHA8:
    case GL_R8I:
    case GL_R8UI:
    case GL_R16I:
    case GL_R16UI:
    case GL_R32I:
    case GL_R32UI:
    case GL_RG8I:
    case GL_RG8UI:
    case GL_RG16I:
    case GL_RG16UI:
    case GL_RG32I:
    case GL_RG32UI:
    case GL_RGBA8I:
    case GL_RGBA8UI:
    case GL_RGBA16I:
    case GL_RGBA16UI:
    case GL_RGBA32I:
    case GL_RGBA32UI:
        return true;
    case GL_R16F:
    case GL_RG16F:
    case GL_RGBA16F:
    case GL_R32F:
    case GL_RG32F:
    case GL_RGBA32F:
    case GL_R11F_G11F_B10F:
        return glesMajorVersion >= 3 && ctx->hasExtension("GL_EXT_color_buffer_float");
        break;
    case GL_RGB16F:
        return glesMajorVersion >= 3 && ctx->hasExtension("GL_EXT_color_buffer_half_float");
        break;
    }
    return false;
}

bool depthRenderableFormat(GL2Encoder* ctx, GLenum internalformat) {
    switch (internalformat) {
    case GL_DEPTH_COMPONENT:
    case GL_DEPTH_STENCIL:
    case GL_DEPTH_COMPONENT16:
    case GL_DEPTH_COMPONENT24:
    case GL_DEPTH_COMPONENT32F:
    case GL_DEPTH24_STENCIL8:
    case GL_DEPTH32F_STENCIL8:
        return true;
    }
    return false;
}

bool stencilRenderableFormat(GL2Encoder* ctx, GLenum internalformat) {
    switch (internalformat) {
    case GL_DEPTH_STENCIL:
    case GL_STENCIL_INDEX8:
    case GL_DEPTH24_STENCIL8:
    case GL_DEPTH32F_STENCIL8:
        return true;
    }
    return false;
}

bool isCubeMapTarget(GLenum target) {
    switch (target) {
    case GL_TEXTURE_CUBE_MAP_POSITIVE_X:
    case GL_TEXTURE_CUBE_MAP_POSITIVE_Y:
    case GL_TEXTURE_CUBE_MAP_POSITIVE_Z:
    case GL_TEXTURE_CUBE_MAP_NEGATIVE_X:
    case GL_TEXTURE_CUBE_MAP_NEGATIVE_Y:
    case GL_TEXTURE_CUBE_MAP_NEGATIVE_Z:
        return true;
    default:
        break;
    }
    return false;
}

#define LIST_VALID_TEXFORMATS(f) \
    f(GL_DEPTH_COMPONENT) \
    f(GL_DEPTH_STENCIL) \
    f(GL_RED) \
    f(GL_RED_INTEGER) \
    f(GL_RG) \
    f(GL_RGB) \
    f(GL_RGBA) \
    f(GL_RGBA_INTEGER) \
    f(GL_RGB_INTEGER) \
    f(GL_RG_INTEGER) \
    f(GL_BGRA_EXT) \
    f(GL_ALPHA) \
    f(GL_LUMINANCE) \
    f(GL_LUMINANCE_ALPHA) \

#define LIST_VALID_TEXTYPES(f) \
    f(GL_BYTE) \
    f(GL_FLOAT) \
    f(GL_FLOAT_32_UNSIGNED_INT_24_8_REV) \
    f(GL_HALF_FLOAT) \
    f(GL_HALF_FLOAT_OES) \
    f(GL_INT) \
    f(GL_SHORT) \
    f(GL_UNSIGNED_BYTE) \
    f(GL_UNSIGNED_INT) \
    f(GL_UNSIGNED_INT_10F_11F_11F_REV) \
    f(GL_UNSIGNED_INT_2_10_10_10_REV) \
    f(GL_UNSIGNED_INT_24_8) \
    f(GL_UNSIGNED_INT_5_9_9_9_REV) \
    f(GL_UNSIGNED_SHORT) \
    f(GL_UNSIGNED_SHORT_4_4_4_4) \
    f(GL_UNSIGNED_SHORT_5_5_5_1) \
    f(GL_UNSIGNED_SHORT_5_6_5) \

bool pixelType(GL2Encoder* ctx, GLenum type) {
    int glesMajorVersion = ctx->majorVersion();
    if (glesMajorVersion < 3) {
        switch (type) {
        case GL_UNSIGNED_BYTE:
        case GL_UNSIGNED_SHORT:
        case GL_UNSIGNED_SHORT_5_6_5:
        case GL_UNSIGNED_SHORT_4_4_4_4:
        case GL_UNSIGNED_SHORT_5_5_5_1:
        case GL_UNSIGNED_INT:
        case GL_UNSIGNED_INT_10F_11F_11F_REV:
        case GL_UNSIGNED_INT_24_8:
        case GL_HALF_FLOAT:
        case GL_HALF_FLOAT_OES:
        case GL_FLOAT:
            return true;
        }
        return false;
    }

#define GLES3_TYPE_CASE(type) \
    case type: \

    switch (type) {
        LIST_VALID_TEXTYPES(GLES3_TYPE_CASE)
            return glesMajorVersion >= 3;
        default:
            break;
    }

    return false;
}

bool pixelFormat(GL2Encoder* ctx, GLenum format) {
    int glesMajorVersion = ctx->majorVersion();
    if (glesMajorVersion < 3) {
        switch (format) {
            case GL_DEPTH_COMPONENT:
                // GLES3 compatible
                // Required in dEQP
            case GL_RED:
            case GL_RG:
            case GL_DEPTH_STENCIL_OES:
            case GL_ALPHA:
            case GL_RGB:
            case GL_RGBA:
            case GL_BGRA_EXT:
            case GL_LUMINANCE:
            case GL_LUMINANCE_ALPHA:
                return true;
        }
        return false;
    }

#define GLES3_FORMAT_CASE(format) \
    case format:

    switch (format) {
        LIST_VALID_TEXFORMATS(GLES3_FORMAT_CASE)
            return glesMajorVersion >= 3;
        default:
            break;
    }
    return false;
}
#define LIST_VALID_TEX_INTERNALFORMATS(f) \
    f(GL_R8) \
    f(GL_R8_SNORM) \
    f(GL_R16F) \
    f(GL_R32F) \
    f(GL_R8UI) \
    f(GL_R8I) \
    f(GL_R16UI) \
    f(GL_R16I) \
    f(GL_R32UI) \
    f(GL_R32I) \
    f(GL_RG8) \
    f(GL_RG8_SNORM) \
    f(GL_RG16F) \
    f(GL_RG32F) \
    f(GL_RG8UI) \
    f(GL_RG8I) \
    f(GL_RG16UI) \
    f(GL_RG16I) \
    f(GL_RG32UI) \
    f(GL_RG32I) \
    f(GL_RGB8) \
    f(GL_SRGB8) \
    f(GL_RGB565) \
    f(GL_RGB8_SNORM) \
    f(GL_R11F_G11F_B10F) \
    f(GL_RGB9_E5) \
    f(GL_RGB16F) \
    f(GL_RGB32F) \
    f(GL_RGB8UI) \
    f(GL_RGB8I) \
    f(GL_RGB16UI) \
    f(GL_RGB16I) \
    f(GL_RGB32UI) \
    f(GL_RGB32I) \
    f(GL_RGBA8) \
    f(GL_SRGB8_ALPHA8) \
    f(GL_RGBA8_SNORM) \
    f(GL_RGB5_A1) \
    f(GL_RGBA4) \
    f(GL_RGB10_A2) \
    f(GL_RGBA16F) \
    f(GL_RGBA32F) \
    f(GL_RGBA8UI) \
    f(GL_RGBA8I) \
    f(GL_RGB10_A2UI) \
    f(GL_RGBA16UI) \
    f(GL_RGBA16I) \
    f(GL_RGBA32I) \
    f(GL_RGBA32UI) \
    f(GL_DEPTH_COMPONENT16) \
    f(GL_DEPTH_COMPONENT24) \
    f(GL_DEPTH_COMPONENT32F) \
    f(GL_DEPTH24_STENCIL8) \
    f(GL_DEPTH32F_STENCIL8) \
    f(GL_COMPRESSED_R11_EAC) \
    f(GL_COMPRESSED_SIGNED_R11_EAC) \
    f(GL_COMPRESSED_RG11_EAC) \
    f(GL_COMPRESSED_SIGNED_RG11_EAC) \
    f(GL_COMPRESSED_RGB8_ETC2) \
    f(GL_COMPRESSED_SRGB8_ETC2) \
    f(GL_COMPRESSED_RGB8_PUNCHTHROUGH_ALPHA1_ETC2) \
    f(GL_COMPRESSED_SRGB8_PUNCHTHROUGH_ALPHA1_ETC2) \
    f(GL_COMPRESSED_RGBA8_ETC2_EAC) \
    f(GL_COMPRESSED_SRGB8_ALPHA8_ETC2_EAC) \

bool pixelInternalFormat(GLenum internalformat) {
#define VALID_INTERNAL_FORMAT(format) \
    case format: \
        return true; \

    switch (internalformat) {
    LIST_VALID_TEX_INTERNALFORMATS(VALID_INTERNAL_FORMAT)
    default:
        break;
    }
    return false;
}

bool shaderType(GL2Encoder* ctx, GLenum type) {
    int glesMajorVersion = ctx->majorVersion();
    int glesMinorVersion = ctx->minorVersion();
    switch (type) {
    case GL_VERTEX_SHADER:
    case GL_FRAGMENT_SHADER:
        return true;
    case GL_COMPUTE_SHADER:
        return glesMajorVersion >= 3 && glesMinorVersion >= 1;
    }
    return false;
}

bool internalFormatTarget(GL2Encoder* ctx, GLenum target) {
    int glesMajorVersion = ctx->majorVersion();
    int glesMinorVersion = ctx->minorVersion();
    switch (target) {
    case GL_RENDERBUFFER:
        return true;
    case GL_TEXTURE_2D_MULTISAMPLE:
        return glesMajorVersion >= 3 && glesMinorVersion >= 1;
    }
    return false;
}

std::string vertexAttribIndexRangeErrorMsg(GL2Encoder* ctx, GLuint index) {
    std::stringstream ss;
    GLint maxIndex;
    ctx->glGetIntegerv(ctx, GL_MAX_VERTEX_ATTRIBS, &maxIndex);
    ss << "Invalid vertex attribute index. Wanted index: " << index << ". Max index: " << maxIndex;
    return ss.str();
}

} // namespace GLESv2Validation
