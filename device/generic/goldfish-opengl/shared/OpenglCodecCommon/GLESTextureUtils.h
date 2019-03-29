#ifndef GLES_TEXTURE_UTILS_H
#define GLES_TEXTURE_UTILS_H

#include <GLES3/gl31.h>

namespace GLESTextureUtils {

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
        int* end);

int computeTotalImageSize(
        GLsizei width, GLsizei height, GLsizei depth,
        GLenum format, GLenum type,
        int unpackAlignment,
        int unpackRowLength,
        int unpackImageHeight,
        int unpackSkipPixels,
        int unpackSkipRows,
        int unpackSkipImages);

int computeNeededBufferSize(
        GLsizei width, GLsizei height, GLsizei depth,
        GLenum format, GLenum type,
        int unpackAlignment,
        int unpackRowLength,
        int unpackImageHeight,
        int unpackSkipPixels,
        int unpackSkipRows,
        int unpackSkipImages);


} // namespace GLESTextureUtils
#endif
