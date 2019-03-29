#include "GLESTextureUtils.h"

#include "glUtils.h"

#include <cutils/log.h>

namespace GLESTextureUtils {

// Based on computations in
// https://swiftshader.googlesource.com/SwiftShader/+/master/src/OpenGL/common/Image.cpp
// such as Image::loadImageData,
// ComputePitch/ComputePackingOffset

#define HIGHEST_MULTIPLE_OF(align, x) \
    (( ( x ) + ( align ) - 1) & ~( ( align ) - 1)) \

static int computePixelSize(GLenum format, GLenum type) {

#define FORMAT_ERROR(format, type) \
    ALOGE("%s:%d unknown format/type 0x%x 0x%x", __FUNCTION__, __LINE__, format, type) \

    switch(type) {
    case GL_BYTE:
        switch(format) {
        case GL_R8:
        case GL_R8I:
        case GL_R8_SNORM:
        case GL_RED:             return sizeof(char);
        case GL_RED_INTEGER:     return sizeof(char);
        case GL_RG8:
        case GL_RG8I:
        case GL_RG8_SNORM:
        case GL_RG:              return sizeof(char) * 2;
        case GL_RG_INTEGER:      return sizeof(char) * 2;
        case GL_RGB8:
        case GL_RGB8I:
        case GL_RGB8_SNORM:
        case GL_RGB:             return sizeof(char) * 3;
        case GL_RGB_INTEGER:     return sizeof(char) * 3;
        case GL_RGBA8:
        case GL_RGBA8I:
        case GL_RGBA8_SNORM:
        case GL_RGBA:            return sizeof(char) * 4;
        case GL_RGBA_INTEGER:    return sizeof(char) * 4;
        default: FORMAT_ERROR(format, type);
        }
        break;
    case GL_UNSIGNED_BYTE:
        switch(format) {
        case GL_R8:
        case GL_R8UI:
        case GL_RED:             return sizeof(unsigned char);
        case GL_RED_INTEGER:     return sizeof(unsigned char);
        case GL_ALPHA8_EXT:
        case GL_ALPHA:           return sizeof(unsigned char);
        case GL_LUMINANCE8_EXT:
        case GL_LUMINANCE:       return sizeof(unsigned char);
        case GL_LUMINANCE8_ALPHA8_EXT:
        case GL_LUMINANCE_ALPHA: return sizeof(unsigned char) * 2;
        case GL_RG8:
        case GL_RG8UI:
        case GL_RG:              return sizeof(unsigned char) * 2;
        case GL_RG_INTEGER:      return sizeof(unsigned char) * 2;
        case GL_RGB8:
        case GL_RGB8UI:
        case GL_SRGB8:
        case GL_RGB:             return sizeof(unsigned char) * 3;
        case GL_RGB_INTEGER:     return sizeof(unsigned char) * 3;
        case GL_RGBA8:
        case GL_RGBA8UI:
        case GL_SRGB8_ALPHA8:
        case GL_RGBA:            return sizeof(unsigned char) * 4;
        case GL_RGBA_INTEGER:    return sizeof(unsigned char) * 4;
        case GL_BGRA_EXT:
        case GL_BGRA8_EXT:       return sizeof(unsigned char)* 4;
        default: FORMAT_ERROR(format, type);
        }
        break;
    case GL_SHORT:
        switch(format) {
        case GL_R16I:
        case GL_RED_INTEGER:     return sizeof(short);
        case GL_RG16I:
        case GL_RG_INTEGER:      return sizeof(short) * 2;
        case GL_RGB16I:
        case GL_RGB_INTEGER:     return sizeof(short) * 3;
        case GL_RGBA16I:
        case GL_RGBA_INTEGER:    return sizeof(short) * 4;
        default: FORMAT_ERROR(format, type);
        }
        break;
    case GL_UNSIGNED_SHORT:
        switch(format) {
        case GL_DEPTH_COMPONENT16:
        case GL_DEPTH_COMPONENT: return sizeof(unsigned short);
        case GL_R16UI:
        case GL_RED_INTEGER:     return sizeof(unsigned short);
        case GL_RG16UI:
        case GL_RG_INTEGER:      return sizeof(unsigned short) * 2;
        case GL_RGB16UI:
        case GL_RGB_INTEGER:     return sizeof(unsigned short) * 3;
        case GL_RGBA16UI:
        case GL_RGBA_INTEGER:    return sizeof(unsigned short) * 4;
        default: FORMAT_ERROR(format, type);
        }
        break;
    case GL_INT:
        switch(format) {
        case GL_R32I:
        case GL_RED_INTEGER:     return sizeof(int);
        case GL_RG32I:
        case GL_RG_INTEGER:      return sizeof(int) * 2;
        case GL_RGB32I:
        case GL_RGB_INTEGER:     return sizeof(int) * 3;
        case GL_RGBA32I:
        case GL_RGBA_INTEGER:    return sizeof(int) * 4;
        default: FORMAT_ERROR(format, type);
        }
        break;
    case GL_UNSIGNED_INT:
        switch(format) {
        case GL_DEPTH_COMPONENT16:
        case GL_DEPTH_COMPONENT24:
        case GL_DEPTH_COMPONENT32_OES:
        case GL_DEPTH_COMPONENT: return sizeof(unsigned int);
        case GL_R32UI:
        case GL_RED_INTEGER:     return sizeof(unsigned int);
        case GL_RG32UI:
        case GL_RG_INTEGER:      return sizeof(unsigned int) * 2;
        case GL_RGB32UI:
        case GL_RGB_INTEGER:     return sizeof(unsigned int) * 3;
        case GL_RGBA32UI:
        case GL_RGBA_INTEGER:    return sizeof(unsigned int) * 4;
        default: FORMAT_ERROR(format, type);
        }
        break;
    case GL_UNSIGNED_SHORT_4_4_4_4:
    case GL_UNSIGNED_SHORT_5_5_5_1:
    case GL_UNSIGNED_SHORT_5_6_5:
    case GL_UNSIGNED_SHORT_4_4_4_4_REV_EXT:
    case GL_UNSIGNED_SHORT_1_5_5_5_REV_EXT:
        return sizeof(unsigned short);
    case GL_UNSIGNED_INT_10F_11F_11F_REV:
    case GL_UNSIGNED_INT_5_9_9_9_REV:
    case GL_UNSIGNED_INT_2_10_10_10_REV:
    case GL_UNSIGNED_INT_24_8_OES:
        return sizeof(unsigned int);
    case GL_FLOAT_32_UNSIGNED_INT_24_8_REV:
        return sizeof(float) + sizeof(unsigned int);
    case GL_FLOAT:
        switch(format) {
        case GL_DEPTH_COMPONENT32F:
        case GL_DEPTH_COMPONENT: return sizeof(float);
        case GL_ALPHA32F_EXT:
        case GL_ALPHA:           return sizeof(float);
        case GL_LUMINANCE32F_EXT:
        case GL_LUMINANCE:       return sizeof(float);
        case GL_LUMINANCE_ALPHA32F_EXT:
        case GL_LUMINANCE_ALPHA: return sizeof(float) * 2;
        case GL_RED:             return sizeof(float);
        case GL_R32F:            return sizeof(float);
        case GL_RG:              return sizeof(float) * 2;
        case GL_RG32F:           return sizeof(float) * 2;
        case GL_RGB:             return sizeof(float) * 3;
        case GL_RGB32F:          return sizeof(float) * 3;
        case GL_RGBA:            return sizeof(float) * 4;
        case GL_RGBA32F:         return sizeof(float) * 4;
        default: FORMAT_ERROR(format, type);
        }
        break;
    case GL_HALF_FLOAT:
    case GL_HALF_FLOAT_OES:
        switch(format) {
        case GL_ALPHA16F_EXT:
        case GL_ALPHA:           return sizeof(unsigned short);
        case GL_LUMINANCE16F_EXT:
        case GL_LUMINANCE:       return sizeof(unsigned short);
        case GL_LUMINANCE_ALPHA16F_EXT:
        case GL_LUMINANCE_ALPHA: return sizeof(unsigned short) * 2;
        case GL_RED:             return sizeof(unsigned short);
        case GL_R16F:            return sizeof(unsigned short);
        case GL_RG:              return sizeof(unsigned short) * 2;
        case GL_RG16F:           return sizeof(unsigned short) * 2;
        case GL_RGB:             return sizeof(unsigned short) * 3;
        case GL_RGB16F:          return sizeof(unsigned short) * 3;
        case GL_RGBA:            return sizeof(unsigned short) * 4;
        case GL_RGBA16F:         return sizeof(unsigned short) * 4;
        default: FORMAT_ERROR(format, type);
        }
        break;
    default: FORMAT_ERROR(format, type);
    }

    return 0;
}

static int computePitch(GLsizei inputWidth, GLenum format, GLenum type, int align) {
    GLsizei unaligned_width = computePixelSize(format, type) * inputWidth;
    return HIGHEST_MULTIPLE_OF(align, unaligned_width);
}

static int computePackingOffset(GLenum format, GLenum type, GLsizei width, GLsizei height, int align, int skipPixels, int skipRows, int skipImages) {
    GLsizei alignedPitch = computePitch(width, format, type, align);
    int packingOffsetRows =
        (skipImages * height + skipRows);
    return packingOffsetRows * alignedPitch + skipPixels * computePixelSize(format, type);
}

void computeTextureStartEnd(
        GLsizei width, GLsizei height, GLsizei depth,
        GLenum format, GLenum type,
        int unpackAlignment,
        int unpackRowLength,
        int unpackImageHeight,
        int unpackSkipPixels,
        int unpackSkipRows,
        int unpackSkipImages,
        int* start,
        int* end) {

    GLsizei inputWidth = (unpackRowLength == 0) ? width : unpackRowLength;
    GLsizei inputPitch = computePitch(inputWidth, format, type, unpackAlignment);
    GLsizei inputHeight = (unpackImageHeight == 0) ? height : unpackImageHeight;

    ALOGV("%s: input idim %d %d %d w p h %d %d %d:", __FUNCTION__, width, height, depth, inputWidth, inputPitch, inputHeight);

    int startVal = computePackingOffset(format, type, inputWidth, inputHeight, unpackAlignment, unpackSkipPixels, unpackSkipRows, unpackSkipImages);
    int endVal = startVal + inputPitch * inputHeight * depth;

    if (start) *start = startVal;
    if (end) *end = endVal;

    ALOGV("%s: start/end: %d %d", __FUNCTION__, *start, *end);

}

int computeTotalImageSize(
        GLsizei width, GLsizei height, GLsizei depth,
        GLenum format, GLenum type,
        int unpackAlignment,
        int unpackRowLength,
        int unpackImageHeight,
        int unpackSkipPixels,
        int unpackSkipRows,
        int unpackSkipImages) {

    int start, end;
    computeTextureStartEnd(
            width, height, depth,
            format, type,
            unpackAlignment,
            unpackRowLength,
            unpackImageHeight,
            unpackSkipPixels,
            unpackSkipRows,
            unpackSkipImages,
            &start,
            &end);
    return end;
}

int computeNeededBufferSize(
        GLsizei width, GLsizei height, GLsizei depth,
        GLenum format, GLenum type,
        int unpackAlignment,
        int unpackRowLength,
        int unpackImageHeight,
        int unpackSkipPixels,
        int unpackSkipRows,
        int unpackSkipImages) {

    int start, end;
    computeTextureStartEnd(
            width, height, depth,
            format, type,
            unpackAlignment,
            unpackRowLength,
            unpackImageHeight,
            unpackSkipPixels,
            unpackSkipRows,
            unpackSkipImages,
            &start,
            &end);
    return end - start;
}

} // namespace GLESTextureUtils
