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

#include "RenderTopView.h"
#include "VideoTex.h"
#include "glError.h"
#include "shader.h"
#include "shader_simpleTex.h"
#include "shader_projectedTex.h"

#include <log/log.h>
#include <math/mat4.h>
#include <math/vec3.h>


// Simple aliases to make geometric math using vectors more readable
static const unsigned X = 0;
static const unsigned Y = 1;
static const unsigned Z = 2;
//static const unsigned W = 3;


// Since we assume no roll in these views, we can simplify the required math
static android::vec3 unitVectorFromPitchAndYaw(float pitch, float yaw) {
    float sinPitch, cosPitch;
    sincosf(pitch, &sinPitch, &cosPitch);
    float sinYaw, cosYaw;
    sincosf(yaw, &sinYaw, &cosYaw);
    return android::vec3(cosPitch * -sinYaw,
                         cosPitch * cosYaw,
                         sinPitch);
}


// Helper function to set up a perspective matrix with independent horizontal and vertical
// angles of view.
static android::mat4 perspective(float hfov, float vfov, float near, float far) {
    const float tanHalfFovX = tanf(hfov * 0.5f);
    const float tanHalfFovY = tanf(vfov * 0.5f);

    android::mat4 p(0.0f);
    p[0][0] = 1.0f / tanHalfFovX;
    p[1][1] = 1.0f / tanHalfFovY;
    p[2][2] = - (far + near) / (far - near);
    p[2][3] = -1.0f;
    p[3][2] = - (2.0f * far * near) / (far - near);
    return p;
}


// Helper function to set up a view matrix for a camera given it's yaw & pitch & location
// Yes, with a bit of work, we could use lookAt, but it does a lot of extra work
// internally that we can short cut.
static android::mat4 cameraLookMatrix(const ConfigManager::CameraInfo& cam) {
    float sinYaw, cosYaw;
    sincosf(cam.yaw, &sinYaw, &cosYaw);

    // Construct principal unit vectors
    android::vec3 vAt = unitVectorFromPitchAndYaw(cam.pitch, cam.yaw);
    android::vec3 vRt = android::vec3(cosYaw, sinYaw, 0.0f);
    android::vec3 vUp = -cross(vAt, vRt);
    android::vec3 eye = android::vec3(cam.position[X], cam.position[Y], cam.position[Z]);

    android::mat4 Result(1.0f);
    Result[0][0] = vRt.x;
    Result[1][0] = vRt.y;
    Result[2][0] = vRt.z;
    Result[0][1] = vUp.x;
    Result[1][1] = vUp.y;
    Result[2][1] = vUp.z;
    Result[0][2] =-vAt.x;
    Result[1][2] =-vAt.y;
    Result[2][2] =-vAt.z;
    Result[3][0] =-dot(vRt, eye);
    Result[3][1] =-dot(vUp, eye);
    Result[3][2] = dot(vAt, eye);
    return Result;
}


RenderTopView::RenderTopView(sp<IEvsEnumerator> enumerator,
                             const std::vector<ConfigManager::CameraInfo>& camList,
                             const ConfigManager& mConfig) :
    mEnumerator(enumerator),
    mConfig(mConfig) {

    // Copy the list of cameras we're to employ into our local storage.  We'll create and
    // associate a streaming video texture when we are activated.
    mActiveCameras.reserve(camList.size());
    for (unsigned i=0; i<camList.size(); i++) {
        mActiveCameras.emplace_back(camList[i]);
    }
}


bool RenderTopView::activate() {
    // Ensure GL is ready to go...
    if (!prepareGL()) {
        ALOGE("Error initializing GL");
        return false;
    }

    // Load our shader programs
    mPgmAssets.simpleTexture = buildShaderProgram(vtxShader_simpleTexture,
                                                 pixShader_simpleTexture,
                                                 "simpleTexture");
    if (!mPgmAssets.simpleTexture) {
        ALOGE("Failed to build shader program");
        return false;
    }
    mPgmAssets.projectedTexture = buildShaderProgram(vtxShader_projectedTexture,
                                                    pixShader_projectedTexture,
                                                    "projectedTexture");
    if (!mPgmAssets.projectedTexture) {
        ALOGE("Failed to build shader program");
        return false;
    }


    // Load the checkerboard text image
    mTexAssets.checkerBoard.reset(createTextureFromPng(
                                  "/system/etc/automotive/evs/LabeledChecker.png"));
    if (!mTexAssets.checkerBoard->glId()) {
        ALOGE("Failed to load checkerboard texture");
        return false;
    }

    // Load the car image
    mTexAssets.carTopView.reset(createTextureFromPng(
                                "/system/etc/automotive/evs/CarFromTop.png"));
    if (!mTexAssets.carTopView->glId()) {
        ALOGE("Failed to load carTopView texture");
        return false;
    }


    // Set up streaming video textures for our associated cameras
    for (auto&& cam: mActiveCameras) {
        cam.tex.reset(createVideoTexture(mEnumerator, cam.info.cameraId.c_str(), sDisplay));
        if (!cam.tex) {
            ALOGE("Failed to set up video texture for %s (%s)",
                  cam.info.cameraId.c_str(), cam.info.function.c_str());
// TODO:  For production use, we may actually want to fail in this case, but not yet...
//            return false;
        }
    }

    return true;
}


void RenderTopView::deactivate() {
    // Release our video textures
    // We can't hold onto it because some other Render object might need the same camera
    // TODO:  If start/stop costs become a problem, we could share video textures
    for (auto&& cam: mActiveCameras) {
        cam.tex = nullptr;
    }
}


bool RenderTopView::drawFrame(const BufferDesc& tgtBuffer) {
    // Tell GL to render to the given buffer
    if (!attachRenderTarget(tgtBuffer)) {
        ALOGE("Failed to attached render target");
        return false;
    }

    // Set up our top down projection matrix from car space (world units, Xfwd, Yright, Zup)
    // to view space (-1 to 1)
    const float top    = mConfig.getDisplayTopLocation();
    const float bottom = mConfig.getDisplayBottomLocation();
    const float right  = mConfig.getDisplayRightLocation(sAspectRatio);
    const float left   = mConfig.getDisplayLeftLocation(sAspectRatio);

    const float near = 10.0f;   // arbitrary top of view volume
    const float far = 0.0f;     // ground plane is at zero

    // We can use a simple, unrotated ortho view since the screen and car space axis are
    // naturally aligned in the top down view.
    // TODO:  Not sure if flipping top/bottom here is "correct" or a double reverse...
//    orthoMatrix = android::mat4::ortho(left, right, bottom, top, near, far);
    orthoMatrix = android::mat4::ortho(left, right, top, bottom, near, far);


    // Refresh our video texture contents.  We do it all at once in hopes of getting
    // better coherence among images.  This does not guarantee synchronization, of course...
    for (auto&& cam: mActiveCameras) {
        if (cam.tex) {
            cam.tex->refresh();
        }
    }

    // Iterate over all the cameras and project their images onto the ground plane
    for (auto&& cam: mActiveCameras) {
        renderCameraOntoGroundPlane(cam);
    }

    // Draw the car image
    renderCarTopView();

    // Wait for the rendering to finish
    glFinish();

    return true;
}


//
// Responsible for drawing the car's self image in the top down view.
// Draws in car model space (units of meters with origin at center of rear axel)
// NOTE:  We probably want to eventually switch to using a VertexArray based model system.
//
void RenderTopView::renderCarTopView() {
    // Compute the corners of our image footprint in car space
    const float carLengthInTexels = mConfig.carGraphicRearPixel() - mConfig.carGraphicFrontPixel();
    const float carSpaceUnitsPerTexel = mConfig.getCarLength() / carLengthInTexels;
    const float textureHeightInCarSpace = mTexAssets.carTopView->height() * carSpaceUnitsPerTexel;
    const float textureAspectRatio = (float)mTexAssets.carTopView->width() /
                                            mTexAssets.carTopView->height();
    const float pixelsBehindCarInImage = mTexAssets.carTopView->height() -
                                         mConfig.carGraphicRearPixel();
    const float textureExtentBehindCarInCarSpace = pixelsBehindCarInImage * carSpaceUnitsPerTexel;

    const float btCS = mConfig.getRearLocation() - textureExtentBehindCarInCarSpace;
    const float tpCS = textureHeightInCarSpace + btCS;
    const float ltCS = 0.5f * textureHeightInCarSpace * textureAspectRatio;
    const float rtCS = -ltCS;

    GLfloat vertsCarPos[] = { ltCS, tpCS, 0.0f,   // left top in car space
                              rtCS, tpCS, 0.0f,   // right top
                              ltCS, btCS, 0.0f,   // left bottom
                              rtCS, btCS, 0.0f    // right bottom
    };
    // NOTE:  We didn't flip the image in the texture, so V=0 is actually the top of the image
    GLfloat vertsCarTex[] = { 0.0f, 0.0f,   // left top
                              1.0f, 0.0f,   // right top
                              0.0f, 1.0f,   // left bottom
                              1.0f, 1.0f    // right bottom
    };
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 0, vertsCarPos);
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 0, vertsCarTex);
    glEnableVertexAttribArray(0);
    glEnableVertexAttribArray(1);


    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    glUseProgram(mPgmAssets.simpleTexture);
    GLint loc = glGetUniformLocation(mPgmAssets.simpleTexture, "cameraMat");
    glUniformMatrix4fv(loc, 1, false, orthoMatrix.asArray());
    glBindTexture(GL_TEXTURE_2D, mTexAssets.carTopView->glId());

    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);


    glDisable(GL_BLEND);

    glDisableVertexAttribArray(0);
    glDisableVertexAttribArray(1);
}


// NOTE:  Might be worth reviewing the ideas at
// http://math.stackexchange.com/questions/1691895/inverse-of-perspective-matrix
// to see if that simplifies the math, although we'll still want to compute the actual ground
// interception points taking into account the pitchLimit as below.
void RenderTopView::renderCameraOntoGroundPlane(const ActiveCamera& cam) {
    // How far is the farthest any camera should even consider projecting it's image?
    const float visibleSizeV = mConfig.getDisplayTopLocation() - mConfig.getDisplayBottomLocation();
    const float visibleSizeH = visibleSizeV * sAspectRatio;
    const float maxRange = (visibleSizeH > visibleSizeV) ? visibleSizeH : visibleSizeV;

    // Construct the projection matrix (View + Projection) associated with this sensor
    // TODO:  Consider just hard coding the far plane distance as it likely doesn't matter
    const android::mat4 V = cameraLookMatrix(cam.info);
    const android::mat4 P = perspective(cam.info.hfov, cam.info.vfov, cam.info.position[Z], maxRange);
    const android::mat4 projectionMatix = P*V;

    // Just draw the whole darn ground plane for now -- we're wasting fill rate, but so what?
    // A 2x optimization would be to draw only the 1/2 space of the window in the direction
    // the sensor is facing.  A more complex solution would be to construct the intersection
    // of the sensor volume with the ground plane and render only that geometry.
    const float top = mConfig.getDisplayTopLocation();
    const float bottom = mConfig.getDisplayBottomLocation();
    const float wsHeight = top - bottom;
    const float wsWidth = wsHeight * sAspectRatio;
    const float right =  wsWidth * 0.5f;
    const float left = -right;

    const android::vec3 topLeft(left, top, 0.0f);
    const android::vec3 topRight(right, top, 0.0f);
    const android::vec3 botLeft(left, bottom, 0.0f);
    const android::vec3 botRight(right, bottom, 0.0f);

    GLfloat vertsPos[] = { topLeft[X],  topLeft[Y],  topLeft[Z],
                           topRight[X], topRight[Y], topRight[Z],
                           botLeft[X],  botLeft[Y],  botLeft[Z],
                           botRight[X], botRight[Y], botRight[Z],
    };
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 0, vertsPos);
    glEnableVertexAttribArray(0);


    glDisable(GL_BLEND);

    glUseProgram(mPgmAssets.projectedTexture);
    GLint locCam = glGetUniformLocation(mPgmAssets.projectedTexture, "cameraMat");
    glUniformMatrix4fv(locCam, 1, false, orthoMatrix.asArray());
    GLint locProj = glGetUniformLocation(mPgmAssets.projectedTexture, "projectionMat");
    glUniformMatrix4fv(locProj, 1, false, projectionMatix.asArray());

    GLuint texId;
    if (cam.tex) {
        texId = cam.tex->glId();
    } else {
        texId = mTexAssets.checkerBoard->glId();
    }
    glBindTexture(GL_TEXTURE_2D, texId);

    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);


    glDisableVertexAttribArray(0);
}
