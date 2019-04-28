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

#include "RenderDirectView.h"
#include "VideoTex.h"
#include "glError.h"
#include "shader.h"
#include "shader_simpleTex.h"

#include <log/log.h>
#include <math/mat4.h>


RenderDirectView::RenderDirectView(sp<IEvsEnumerator> enumerator,
                                   const ConfigManager::CameraInfo& cam) {
    mEnumerator = enumerator;
    mCameraInfo = cam;
}


bool RenderDirectView::activate() {
    // Ensure GL is ready to go...
    if (!prepareGL()) {
        ALOGE("Error initializing GL");
        return false;
    }

    // Load our shader program if we don't have it already
    if (!mShaderProgram) {
        mShaderProgram = buildShaderProgram(vtxShader_simpleTexture,
                                            pixShader_simpleTexture,
                                            "simpleTexture");
        if (!mShaderProgram) {
            ALOGE("Error buliding shader program");
            return false;
        }
    }

    // Construct our video texture
    mTexture.reset(createVideoTexture(mEnumerator, mCameraInfo.cameraId.c_str(), sDisplay));
    if (!mTexture) {
        ALOGE("Failed to set up video texture for %s (%s)",
              mCameraInfo.cameraId.c_str(), mCameraInfo.function.c_str());
// TODO:  For production use, we may actually want to fail in this case, but not yet...
//       return false;
    }

    return true;
}


void RenderDirectView::deactivate() {
    // Release our video texture
    // We can't hold onto it because some other Render object might need the same camera
    // TODO:  If start/stop costs become a problem, we could share video textures
    mTexture = nullptr;
}


bool RenderDirectView::drawFrame(const BufferDesc& tgtBuffer) {
    // Tell GL to render to the given buffer
    if (!attachRenderTarget(tgtBuffer)) {
        ALOGE("Failed to attached render target");
        return false;
    }

    // Select our screen space simple texture shader
    glUseProgram(mShaderProgram);

    // Set up the model to clip space transform (identity matrix if we're modeling in screen space)
    GLint loc = glGetUniformLocation(mShaderProgram, "cameraMat");
    if (loc < 0) {
        ALOGE("Couldn't set shader parameter 'cameraMat'");
        return false;
    } else {
        const android::mat4 identityMatrix;
        glUniformMatrix4fv(loc, 1, false, identityMatrix.asArray());
    }


    // Bind the texture and assign it to the shader's sampler
    mTexture->refresh();
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, mTexture->glId());


    GLint sampler = glGetUniformLocation(mShaderProgram, "tex");
    if (sampler < 0) {
        ALOGE("Couldn't set shader parameter 'tex'");
        return false;
    } else {
        // Tell the sampler we looked up from the shader to use texture slot 0 as its source
        glUniform1i(sampler, 0);
    }

    // We want our image to show up opaque regardless of alpha values
    glDisable(GL_BLEND);


    // Draw a rectangle on the screen
    GLfloat vertsCarPos[] = { -1.0,  1.0, 0.0f,   // left top in window space
                               1.0,  1.0, 0.0f,   // right top
                              -1.0, -1.0, 0.0f,   // left bottom
                               1.0, -1.0, 0.0f    // right bottom
    };
    // TODO:  We're flipping horizontally here, but should do it only for specified cameras!
    GLfloat vertsCarTex[] = { 1.0f, 1.0f,   // left top
                              0.0f, 1.0f,   // right top
                              1.0f, 0.0f,   // left bottom
                              0.0f, 0.0f    // right bottom
    };
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 0, vertsCarPos);
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 0, vertsCarTex);
    glEnableVertexAttribArray(0);
    glEnableVertexAttribArray(1);

    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    glDisableVertexAttribArray(0);
    glDisableVertexAttribArray(1);


    // Wait for the rendering to finish
    glFinish();

    return true;
}
