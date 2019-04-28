/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */

#ifdef ENABLE_DR_HWUI

#include "CanvasState.h"
#include "Debug.h"
#include "DeferredLayerUpdater.h"
#include "LayerBuilder.h"
#include "MTKDisplayListRecorder.h"
#include "RenderNode.h"
#include "Outline.h"
#include "hwui/Canvas.h"
#include "renderstate/OffscreenBufferPool.h"
#include "BakedOpState.h"

#include <utils/Log.h>
//#include <utils/Trace.h>

namespace android {
namespace uirenderer {

#define BUFFER_SIZE 1000 * sizeof(char)
#define RETURN_IF_DISABLED() if (CC_LIKELY(!isEnabled()) return

///////////////////////////////////////////////////////////////////////////////
// DisplayListRecorder
///////////////////////////////////////////////////////////////////////////////

const char* DisplayListRecorder::getName() {
    return mHost ?  mHost->name().c_str() : "NullHost";
}

int DisplayListRecorder::getFrameCount() {
    return mHost ? mHost->getFrameCount() : INT_MAX;
}

bool DisplayListRecorder::isEnabled() {
    return g_HWUI_debug_continuous_frame && g_HWUI_debug_record_state && mOpList;
}

void DisplayListRecorder::prepareRecording(node_id id, int width, int height, void* layer) {
    if (g_HWUI_debug_continuous_frame) {
        // all memory used in recorder is allocated by LinearAllocator.
        // It will be released with the allocator. Don't delete it twice.
        // Allocate new op list to avoid the same memory used again
        // because we use the address as id for parsing
        mOpList = new (allocator()) OpList(id, width, height, layer);
        if (!mTempBuffer) // allocate at the first time
            mTempBuffer = (char*) allocator().alloc<char>(BUFFER_SIZE);
    }
}

void DisplayListRecorder::finishRecording() {
    if (g_HWUI_debug_continuous_frame) {
        // must create data/HWUI_dump before dump
        char file[512];
        snprintf(file, sizeof(file), "/data/data/%s/frame_%p_%09d.txt",
            Dumper::getInstance().mProcessName, mOpList->mId, getFrameCount());
        FILE* fPtr = fopen(file, "w");
        ALOGD("%s [%s]: %dx%d %s%s", __FUNCTION__, getName(),
            mOpList->getWidth(), mOpList->getHeight(), file,
            fPtr != nullptr ? "" : " can't open");

        if (fPtr != nullptr) {
            dump(fPtr);
            fclose(fPtr);
        } else {
            snprintf(file, sizeof(file), "/data/HWUI_dump/frame_%p_%09d.txt",
                mOpList->mId, getFrameCount());
            fPtr = fopen(file, "w");
            ALOGD("%s [%s]: %dx%d %s%s", __FUNCTION__, getName(),
                mOpList->getWidth(), mOpList->getHeight(), file,
                fPtr != nullptr ? "" : " can't open");

            if (fPtr != nullptr) {
                dump(fPtr);
                fclose(fPtr);
            } else {
                dump();
            }
        }
    }
    mOpList = nullptr;
}

void DisplayListRecorder::log(int level, int mode, const char* fmt, ...) {
    if (mTempBuffer) {
        va_list ap;

        va_start(ap, fmt);
        vsnprintf(mTempBuffer, BUFFER_SIZE, fmt, ap);
        va_end(ap);

        // must match RecordMode
        const char* str[] = {"", " by property", " by FrameBuilder"};

        if (mTempFile) {
            fprintf(mTempFile, "%*s%s%s\n", level * 2, "",
                mTempBuffer, str[mode]);
        } else {
            ALOGD("%*s%s%s", level * 2, "",
                mTempBuffer, str[mode]);
        }
    }
}

void DisplayListRecorder::addOp(OpNode* op) {
    LOG_ALWAYS_FATAL_IF(!mOpList, "op list is not prepared yet!!");

    if (mOpList->mTail) {
        mOpList->mTail->mNext = op;
        op->mPrev = mOpList->mTail;
        op->mGroup = mOpList->mCurrentGroup;
        mOpList->mTail = op;
    } else {
        mOpList->mTail = mOpList->mHead = op;
    }
}

void DisplayListRecorder::dump(FILE* file) {
    mTempFile = file;
    if (mOpList) {
        bool notLayer = !mOpList->mLayer && mHost;
        char layer[20];
        snprintf(layer, 20, "Layer %p", mOpList->mLayer);
        log(0, false, "--defer%s start (%dx%d, %s) frame#%d <%p>",
            notLayer ? "" : " layer",
            mOpList->getWidth(), mOpList->getHeight(),
            notLayer ? mHost->name().c_str() : layer,
            notLayer ? mHost->getFrameCount() : 0, mOpList->mId);
        int count = 0;
        OpNode* current = mOpList->mHead;
        while(current) {
            current->mFunction(this,
                current->mId, current->mLevel, current->mMode, current->mData);
            current = current->mNext;
            count++;
        }

        log(0, RecordMode::Operation, "--defer%s end frame#%d <%p>",
            notLayer ? "" : " layer",
            notLayer ? mHost->getFrameCount() : 0,
            mOpList->mId);

        log(0, RecordMode::Operation, "--flush%s start (%dx%d, %s) frame#%d <%p>",
            notLayer ? "" : " layer", mOpList->getWidth(), mOpList->getHeight(),
            notLayer ? mHost->name().c_str() : layer,
            notLayer ? mHost->getFrameCount() : 0, mOpList->mId);

        // not support performance report

        log(0, RecordMode::Operation, "--flush%s end frame#%d <%p>",
            notLayer ? "" : " layer",
            notLayer ? mHost->getFrameCount() : 0,
            mOpList->mId);

        log(0, RecordMode::Operation, "total %d op, allocator %p usedSize = %d",
            count, &allocator(), allocator().usedSize());
    }
    mTempFile = nullptr;
}

///////////////////////////////////////////////////////////////////////////////
// helper function
///////////////////////////////////////////////////////////////////////////////

static void unused(Rect* a, Matrix4* b, SkRect* c, SkIRect* d) {}

char* DisplayListRecorder::copyCharString(const char* str, int len) {
    char* buffer = MALLOC_SIZE(char, len + 1);
    memcpy(buffer, str, len);
    buffer[len] = 0;
    return buffer;
}

char* DisplayListRecorder::regionToChar(const SkRegion& clipRegion) {
    if (g_HWUI_debug_continuous_frame && clipRegion.isComplex()) {
        String8 dump, points;
        int count = 0;
        SkRegion::Iterator iter(clipRegion);
        while (!iter.done()) {
            const SkIRect& r = iter.rect();
            points.appendFormat("(%d,%d,%d,%d)", r.fLeft, r.fTop, r.fRight, r.fBottom);
            count++;
            if (count > 0 && count % 16 == 0) points.append("\n");
            iter.next();
        }
        dump.appendFormat("===> currRegions %d <%p>\n%s", count, this, points.string());
        return copyCharString(dump.string(), dump.size());
    }
    return nullptr;
}

char* DisplayListRecorder::unicharToChar(const SkPaint* paint, const glyph_t* text, int count) {
    if (g_HWUI_debug_continuous_frame) {
        SkUnichar *tmpText = MALLOC_SIZE(SkUnichar, count);
        paint->glyphsToUnichars(text, count, tmpText);
        int total = utf32_to_utf8_length((char32_t*)tmpText, count) + 1;
        char* str = MALLOC_SIZE(char, total);
        utf32_to_utf8((char32_t*)tmpText, count, str, total);
        return str;
    }
    return nullptr;
}

///////////////////////////////////////////////////////////////////////////////
// op related implementation
///////////////////////////////////////////////////////////////////////////////

#define VIRTUAL_NAME "VirtualNode"
NODE_CREATE3(StartOp, char* name, Rect* clipRect, char* clipRegion) {
    NODE_LOG("Start display list (%p, %s) ===> currClip" MTK_RECT_STRING,
        id, args->name, MTK_RECT_ARGS(args->clipRect));
    if (args->clipRegion) NODE_LOG("%s", args->clipRegion);
}

NODE_CREATE11(RejectOp, char* name, void* displayList, float alpha, bool shouldClip, bool outlineEmpty,
        float scaleX, float scaleY, bool isQuick, bool isEmpty, int width, int height) {
    if (args->isQuick) {
        NODE_LOG("QuickRejected display list (%p, %s), displaylist %p, width %d, height %d, isEmpty %d",
            id, args->name, args->displayList, args->width, args->height, args->isEmpty);
    } else {
        NODE_LOG("Rejected display list (%p, %s), displaylist %p, alpha %f, shouldClip %d,"
            "outlineEmpty %d, scaleX %f, scaleY %f",
            id, args->name, args->displayList, args->alpha, args->shouldClip,
            args->outlineEmpty, args->scaleX, args->scaleY);
    }
}

void DisplayListRecorder::addStart(CanvasState& state, RenderNode* renderNode) {
    const RenderProperties& properties = renderNode->properties();
    const Outline& outline = properties.getOutline();

    if (properties.getAlpha() <= 0
            || (outline.getShouldClip() && outline.isEmpty())
            || properties.getScaleX() == 0
            || properties.getScaleY() == 0) {
        SETUP_NODE_BASIC(RejectOp, false, RecordMode::Operation);

        // for not quick
        node->mId = renderNode;
        node->mGroup = mOpList->mCurrentGroup = (OpGroupNode*) node;

        args->name = copyCharString(renderNode->getName(), strlen(renderNode->getName()));
        args->displayList = (void*)(renderNode->getDisplayList());
        args->alpha = properties.getAlpha();
        args->shouldClip = outline.getShouldClip();
        args->outlineEmpty = outline.isEmpty();
        args->scaleX = properties.getScaleX();
        args->scaleY = properties.getScaleY();

        // for quick
        args->isQuick = false;
        args->isEmpty = state.currentSnapshot()->getRenderTargetClip().isEmpty();
        args->width = properties.getWidth();
        args->height = properties.getHeight();
    } else {
        SETUP_NODE_BASIC(StartOp, true, RecordMode::Operation);
        node->mId = renderNode;
        node->mGroup = mOpList->mCurrentGroup = (OpGroupNode*) node;

        args->name = copyCharString(renderNode->getName(), strlen(renderNode->getName()));
        args->clipRect = COPY(Rect, state.currentSnapshot()->getClipArea().getClipRect());
        args->clipRegion = regionToChar(state.currentSnapshot()->getClipArea().getClipRegion());
    }

    mOpList->mCurrentLevel++;
}

void DisplayListRecorder::addStartVirtual(node_id virtualNode, float width, float height) {
    SETUP_NODE_BASIC(StartOp, true, RecordMode::Operation);
    node->mId = virtualNode;
    node->mGroup = mOpList->mCurrentGroup = (OpGroupNode*) node;

    args->name = copyCharString(VIRTUAL_NAME, strlen(VIRTUAL_NAME));
    args->clipRect = COPY(Rect, Rect(width, height));
    args->clipRegion = nullptr;

    mOpList->mCurrentLevel++;
}

NODE_CREATE1(EndOp, const char* name) {
    NODE_LOG("Done (%p, %s)", id, args->name);
}

void DisplayListRecorder::addEnd(RenderNode* renderNode) {
    SETUP_NODE_BASIC(EndOp, false, RecordMode::Operation);
    mOpList->mCurrentGroup = node->mGroup->mPrev ? node->mGroup->mPrev->mGroup : nullptr;
    mOpList->mCurrentLevel--;
    node->mLevel--;
    node->mId = renderNode;
    args->name = copyCharString(renderNode->getName(), strlen(renderNode->getName()));
}

void DisplayListRecorder::addEndVirtual(node_id virtualNode) {
    SETUP_NODE_BASIC(EndOp, false, RecordMode::Operation);
    mOpList->mCurrentGroup = node->mGroup->mPrev ? node->mGroup->mPrev->mGroup : nullptr;
    mOpList->mCurrentLevel--;
    node->mLevel--;
    node->mId = virtualNode;
    args->name = copyCharString(VIRTUAL_NAME, strlen(VIRTUAL_NAME));
}

NODE_CREATE2(SaveOp, int flags, int count) {
    NODE_LOG("Save flags 0x%x, count %d <%p>", args->flags, args->count);
}

void DisplayListRecorder::addSave(MTK_BASE_PARAMS, int flags, int count) {
    SETUP_NODE(SaveOp);

    args->flags = flags;
    args->count = mOpList->mSaveCount = count;
}

NODE_CREATE4(SaveLayerOp, Rect* area, int alpha, int flags, int count) {
    NODE_LOG("SaveLayer%s " MTK_RECT_STRING ", alpha %d, flags 0x%x, count %d <%p>",
        args->alpha < 255 ? "Alpha" : "", MTK_RECT_ARGS(args->area),
        args->alpha, args->flags, args->count, id);
}

void DisplayListRecorder::addSaveLayer(MTK_BASE_PARAMS, Rect area, int alpha, int flags) {
    SETUP_NODE(SaveLayerOp);
    args->alpha = alpha;
    args->flags = flags;
    args->count = mOpList->mSaveCount = state.save(flags);
    args->area = COPY(Rect, area);
}

NODE_CREATE1(RestoreToCountOp, int count) {
    NODE_LOG("Restore to count %d <%p>", args->count, id);
}

void DisplayListRecorder::addRestoreToCount(MTK_BASE_PARAMS, int count){
    SETUP_NODE(RestoreToCountOp);
    args->count = mOpList->mSaveCount = count;
}

NODE_CREATE3(TranslateOp, float dx, float dy, Matrix4* currentTransform) {
    NODE_LOG("Translate by (%.2f, %.2f) ===> currTrans" MTK_MATRIX_4_STRING, args->dx, args->dy,
        MTK_MATRIX_4_ARGS(args->currentTransform));
}

void DisplayListRecorder::addTranslate(MTK_BASE_PARAMS, float dx, float dy, float dz) {
    SETUP_NODE(TranslateOp);
    args->dx = dx;
    args->dy = dy;
    args->currentTransform = COPY(Matrix4, *state.currentTransform());
}

NODE_CREATE2(ContactMatrixOp, Matrix4* matrix, Matrix4* currentTransform) {
    NODE_LOG("ConcatMatrix " MTK_MATRIX_4_STRING " ===> currTrans" MTK_MATRIX_4_STRING,
        MTK_MATRIX_4_ARGS(args->matrix), MTK_MATRIX_4_ARGS(args->currentTransform));
}

void DisplayListRecorder::addConcatMatrix(MTK_BASE_PARAMS, const Matrix4& matrix) {
    SETUP_NODE(ContactMatrixOp);
    args->matrix = COPY(Matrix4, matrix);
    args->currentTransform = COPY(Matrix4, *state.currentTransform());
}


NODE_CREATE3(ClipRectOp, Rect* area, Rect* clipRect, char* clipRegion)  {
    NODE_LOG("ClipRect " MTK_RECT_STRING " <%p> ===> currClip" MTK_RECT_STRING,
        MTK_RECT_ARGS(args->area), id, MTK_RECT_ARGS(args->clipRect));
    if (args->clipRegion) {
        NODE_LOG("%s", args->clipRegion);
    }
}

void DisplayListRecorder::addClipRect(MTK_BASE_PARAMS,
    float left, float top, float right, float bottom, SkClipOp op) {
    SETUP_NODE(ClipRectOp);
    args->area = COPY(Rect, Rect(left, top, right, bottom));
    args->clipRect = COPY(Rect, state.currentSnapshot()->getClipArea().getClipRect());
    args->clipRegion = regionToChar(state.currentSnapshot()->getClipArea().getClipRegion());
}

NODE_CREATE1(ScaleAlphaOp, float alpha) {
    NODE_LOG("ScaleAlpha %.2f", args->alpha);
}

void DisplayListRecorder::addScaleAlpha(MTK_BASE_PARAMS, float alpha) {
    SETUP_NODE(ScaleAlphaOp);
    args->alpha = alpha;
}

NODE_CREATE4(SetClippingOutline, const Outline* outline, bool isRR, Rect* rect, float radius) {
    NODE_LOG("SetClippingOutline outline %p, isRR %d, bounds " MTK_RECT_STRING ", r %f <%p>",
        args->outline, args->isRR, MTK_RECT_ARGS(args->rect), args->radius, id);
}

void DisplayListRecorder::addSetClippingOutline(MTK_BASE_PARAMS, const Outline* outline) {
    SETUP_NODE(SetClippingOutline);
    Rect bounds;
    float radius;
    args->outline = outline;
    args->isRR = outline->getAsRoundRect(&bounds, &radius);
    args->rect = COPY(Rect, bounds);
    args->radius = radius;
}

NODE_CREATE2(SetClippingRoundRectOp, Rect* rect, float radius) {
    NODE_LOG("SetClippingRoundRect " MTK_RECT_STRING ", r %f <%p>",
        MTK_RECT_ARGS(args->rect), args->radius, id);
}

void DisplayListRecorder::addSetClippingRoundRect(MTK_BASE_PARAMS,
        const Rect& rect, float radius, bool highPriority) {
    SETUP_NODE(SetClippingRoundRectOp);
    args->rect = COPY(Rect, rect);
    args->radius = radius;
}

#define OP_CASE(X) case RecordedOpId::X: recordImpl(mode, state, static_cast<const X&>(op)); break;
void DisplayListRecorder::record(MTK_BASE_PARAMS, const RecordedOp& op) {
    if (CC_LIKELY(!isEnabled())) return;
    switch (op.opId) {
        MTK_MAP_OPS_BASED_ON_TYPE(OP_CASE)
    default:
        break;
    }
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const RenderNodeOp& op) {
}

NODE_CREATE7(CirclePropsOp, float x, float y, float radius,
        int style, float strokeWidth , bool isAntiAlias, int color) {
    NODE_LOG("Draw Circle Props x %f, y %f, r %f, style %d, width %f, AA %d, color 0x%08x <%p>",
        args->x, args->y, args->radius, args->style,
        args->strokeWidth, args->isAntiAlias, args->color, id);
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const CirclePropsOp& op) {
    SETUP_NODE(CirclePropsOp);
    args->x = *op.x;
    args->y = *op.y;
    args->radius = *op.radius;
    args->style = op.paint->getStyle();
    args->strokeWidth = op.paint->getStrokeWidth();
    args->isAntiAlias = op.paint->isAntiAlias();
    args->color = op.paint->getColor();
}

NODE_CREATE6(RoundRectPropsOp, Rect* localBounds, float rx, float ry,
        int style, bool isAntiAlias, int color) {
    NODE_LOG("Draw RoundRect Props " MTK_RECT_STRING ", rx %f, ry %f, style %d, AA %d, color 0x%08x <%p>",
    MTK_RECT_ARGS(args->localBounds), args->rx, args->ry, args->style, args->isAntiAlias,
    args->color, id);
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const RoundRectPropsOp& op) {
    SETUP_NODE(RoundRectPropsOp);
    args->localBounds = COPY(Rect, Rect(*op.left, *op.top, *op.right, *op.bottom));
    args->rx = *op.rx;
    args->ry = *op.ry;
    args->style = op.paint->getStyle();
    args->isAntiAlias = op.paint->isAntiAlias();
    args->color = op.paint->getColor();
}

NODE_CREATE4(BeginLayerOp, Rect* area, int alpha, int flags, int count) {
    NODE_LOG("Begin%sLayerOp " MTK_RECT_STRING ", alpha %d, flags 0x%x, count %d <%p>",
        args->flags & SaveFlags::ClipToLayer ? "" : "Unclipped", MTK_RECT_ARGS(args->area),
        args->alpha, args->flags, args->count, id);
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const BeginLayerOp& op) {
    SETUP_NODE(BeginLayerOp);
    args->alpha = PaintUtils::getAlphaDirect(op.paint);
    args->flags = SaveFlags::MatrixClip | SaveFlags::ClipToLayer;
    args->count = state.getSaveCount();
    args->area = COPY(Rect, op.unmappedBounds);
}

NODE_CREATE1(EndLayerOp, int count) {
    NODE_LOG("EndLayerOp count %d <%p>", args->count, id);
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const EndLayerOp& op) {
    SETUP_NODE(EndLayerOp);
    args->count = state.getSaveCount();
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const BeginUnclippedLayerOp& op) {
    SETUP_NODE(BeginLayerOp);
    args->alpha = PaintUtils::getAlphaDirect(op.paint);
    args->flags = SaveFlags::MatrixClip;
    args->count = state.getSaveCount();
    args->area = COPY(Rect, op.unmappedBounds);
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const EndUnclippedLayerOp& op) {
    SETUP_NODE(EndLayerOp);
    args->count = state.getSaveCount();
}

NODE_CREATE3(VectorDrawableOp, VectorDrawable::Tree* vectorDrawable,
    Rect* localBounds, Rect* resolved) {
    NODE_LOG("Draw bitmap %p from Vector Drawable at " MTK_RECT_STRING " <%p> ==> resloved" MTK_RECT_STRING,
        args->vectorDrawable, MTK_RECT_ARGS(args->localBounds), id, MTK_RECT_ARGS(args->resolved));
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const VectorDrawableOp& op) {
    SETUP_NODE(VectorDrawableOp);
    args->vectorDrawable = op.vectorDrawable;
    args->localBounds = COPY(Rect, op.unmappedBounds);
    ResolvedRenderState resolved(allocator(), *state.writableSnapshot(), op, false, false);
    args->resolved = COPY(Rect, resolved.clippedBounds);
}

NODE_CREATE7(ShadowOp, float x, float y, float z, float radius,
    float alpha, Matrix4* transformXY, Matrix4* transformZ) {
    NODE_LOG("Draw Shadow center (%f, %f, %f), radius %f, alpha %f, transformXY "
        MTK_MATRIX_4_STRING ", transformZ " MTK_MATRIX_4_STRING " <%p>",
        args->x, args->y, args->z, args->radius, args->alpha,
        MTK_MATRIX_4_ARGS(args->transformXY), MTK_MATRIX_4_ARGS(args->transformZ), id);
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const ShadowOp& op) {
    SETUP_NODE(ShadowOp);
    args->x = op.shadowTask->lightCenter.x;
    args->y = op.shadowTask->lightCenter.y;
    args->z = op.shadowTask->lightCenter.z;
    args->radius = op.shadowTask->lightRadius;
    args->alpha =op.casterAlpha;
    args->transformXY = COPY(Matrix4, op.shadowTask->transformXY);
    args->transformZ = COPY(Matrix4, op.shadowTask->transformZ);
}

NODE_CREATE11(DrawLayerOp, void* layer, float x, float y, float width, float height,
    float textureWidth, float textureHeight, int alpha, int isIdentity, int isBlend, int isTextureLayer) {
    NODE_LOG("Draw Layer %p at (%.2f, %.2f), textureSize (%.2f, %.2f), layerSize"
        " (%.2f, %.2f), alpha %d, isIdentity %d, isBlend %d, isTextureLayer %d <%p>",
        args->layer, args->x, args->y, args->textureWidth, args->textureHeight, args->width, args->height,
        args->alpha, args->isIdentity, args->isBlend, args->isTextureLayer, id);
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const LayerOp& op) {
    SETUP_NODE(DrawLayerOp);
    args->layer = *op.layerHandle;
    args->x = op.unmappedBounds.left;
    args->y = op.unmappedBounds.top;
    args->width = op.unmappedBounds.getWidth();
    args->height = op.unmappedBounds.getHeight();
    args->textureWidth = (*op.layerHandle)->texture.width();
    args->textureHeight = (*op.layerHandle)->texture.height();
    args->alpha = PaintUtils::getAlphaDirect(op.paint);
    args->isIdentity = true;
    args->isBlend = false;
    args->isTextureLayer = false;
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const CopyToLayerOp& op) {
    // not supported
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const CopyFromLayerOp& op) {
    // not supported
}

NODE_CREATE10(ArcOp, Rect* localBounds, float startAngle, float sweepAngle, int useCenter, int style,
    bool isAntiAlias, int color, float strokeWidth, int strokeJoin, int strokeCap) {
    NODE_LOG("Draw Arc " MTK_RECT_STRING ", start %f, sweep %f, useCenter %d, style %d,"
        "AA %d, color 0x%08x, width %f, join %d, cap %d <%p>", MTK_RECT_ARGS(args->localBounds),
        args->startAngle, args->sweepAngle, args->useCenter, args->style, args->isAntiAlias,
        args->color, args->strokeWidth, args->strokeJoin, args->strokeCap, id);
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const ArcOp& op) {
    SETUP_NODE(ArcOp);
    args->localBounds = COPY(Rect, op.unmappedBounds);
    args->startAngle =op.startAngle;
    args->sweepAngle = op.sweepAngle;
    args->useCenter = op.useCenter;
    args->style = op.paint->getStyle();
    args->isAntiAlias = op.paint->isAntiAlias();
    args->color = op.paint->getColor();
    args->strokeWidth = op.paint->getStrokeWidth();
    args->strokeJoin = op.paint->getStrokeJoin();
    args->strokeCap = op.paint->getStrokeCap();
}

NODE_CREATE5(BitmapMeshOp, const Bitmap* bitmap, int meshWidth, int meshHeight,
    Rect* localBounds, Rect* resolved) {
    NODE_LOG("Draw bitmap %p mesh %d x %d at " MTK_RECT_STRING " <%p> ==> resloved" MTK_RECT_STRING,
        args->bitmap, args->meshWidth, args->meshHeight,
        MTK_RECT_ARGS(args->localBounds), id, MTK_RECT_ARGS(args->resolved));
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const BitmapMeshOp& op) {
    SETUP_NODE(BitmapMeshOp);
    args->bitmap = op.bitmap;
    args->meshWidth = op.meshWidth;
    args->meshHeight = op.meshHeight;
    args->localBounds = COPY(Rect, op.unmappedBounds);
    ResolvedRenderState resolved(allocator(), *state.writableSnapshot(), op, false, false);
    args->resolved = COPY(Rect, resolved.clippedBounds);
}

NODE_CREATE4(BitmapRectOp, const Bitmap* bitmap, Rect *src, Rect* localBounds, Rect* resolved) {
    NODE_LOG("Draw bitmap %p src=" MTK_RECT_STRING ", dst=" MTK_RECT_STRING " <%p> ==> resloved" MTK_RECT_STRING,
         args->bitmap, MTK_RECT_ARGS(args->src), MTK_RECT_ARGS(args->localBounds), id, MTK_RECT_ARGS(args->resolved));
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const BitmapRectOp& op) {
    SETUP_NODE(BitmapRectOp);
    args->bitmap = op.bitmap;
    args->src = COPY(Rect, op.src);
    args->localBounds = COPY(Rect, op.unmappedBounds);
    ResolvedRenderState resolved(allocator(), *state.writableSnapshot(), op, false, false);
    args->resolved = COPY(Rect, resolved.clippedBounds);
}

NODE_CREATE2(ColorOp, int color, SkBlendMode mode) {
    NODE_LOG("Draw color 0x%08x, mode %d <%p>", args->color, args->mode, id);
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const ColorOp& op) {
    SETUP_NODE(ColorOp);
    args->color = op.color;
    args->mode = op.mode;
}

NODE_CREATE1(FunctorOp, Functor* functor) {
    NODE_LOG("Draw Functor %p <%p>", args->functor, id);
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const FunctorOp& op) {
    SETUP_NODE(FunctorOp);
    args->functor = op.functor;
}

NODE_CREATE7(LinesOp, int count, int color, float strokeWidth, int strokeJoin,
    int strokeCap, Rect* localBounds, char* points) {
    NODE_LOG("Draw Lines count %d, color 0x%08x, width %f, join %d, cap %d in " MTK_RECT_STRING
        " <%p> ===> Points(%s)", args->count, args->color, args->strokeWidth, args->strokeJoin,
        args->strokeCap, MTK_RECT_ARGS(args->localBounds), id, args->points ? args->points : "");
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const LinesOp& op) {
    SETUP_NODE(LinesOp);
    args->count = op.floatCount;
    args->localBounds = COPY(Rect, op.unmappedBounds);
    args->color = op.paint->getColor();
    args->strokeWidth = op.paint->getStrokeWidth();
    args->strokeJoin = op.paint->getStrokeJoin();
    args->strokeCap = op.paint->getStrokeCap();
    if (op.floatCount > 0) {
        String8 log;
        for (int i = 0; i < op.floatCount; i += 2) {
            log.appendFormat("(%d,%d)", (int) op.points[i], (int) op.points[i + 1]);
        }
        args->points = copyCharString(log.string(), log.size());
    } else {
        args->points = nullptr;
    }
}

NODE_CREATE4(OvalOp, Rect* localBounds, int style, bool isAntiAlias, int color) {
    NODE_LOG("Draw Oval " MTK_RECT_STRING ", style %d, AA %d, color 0x%08x <%p>",
        MTK_RECT_ARGS(args->localBounds), args->style, args->isAntiAlias, args->color, id);
}


void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const OvalOp& op) {
    SETUP_NODE(OvalOp);
    args->localBounds = COPY(Rect, op.unmappedBounds);
    args->style = op.paint->getStyle();
    args->isAntiAlias = op.paint->isAntiAlias();
    args->color = op.paint->getColor();
}

NODE_CREATE7(PathOp, int countPoints, int color, float strokeWidth, int strokeJoin,
    int strokeCap, Rect* localBounds, char* points) {
    NODE_LOG("Draw Path count %d, color 0x%08x, width %f, join %d, cap %d in " MTK_RECT_STRING
        " <%p> ===> Points(%s)", args->countPoints, args->color, args->strokeWidth, args->strokeJoin,
        args->strokeCap, MTK_RECT_ARGS(args->localBounds), id, args->points ? args->points : "");
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const PathOp& op) {
    SETUP_NODE(PathOp);
    args->countPoints = op.path->countPoints();
    args->localBounds = COPY(Rect, op.unmappedBounds);
    args->color = op.paint->getColor();
    args->strokeWidth = op.paint->getStrokeWidth();
    args->strokeJoin = op.paint->getStrokeJoin();
    args->strokeCap = op.paint->getStrokeCap();
    if (op.path->countPoints() > 0) {
        String8 log;
        int total = op.path->countPoints();
        for (int i = 0; i < total; i++) {
            SkPoint p = op.path->getPoint(i);
            log.appendFormat("(%d,%d)", (int) (p.x()), (int) (p.y()));
        }
        args->points = copyCharString(log.string(), log.size());
    } else {
        args->points = nullptr;
    }
}

NODE_CREATE7(PointsOp, int count, int color, float strokeWidth, int strokeJoin,
    int strokeCap, Rect* localBounds, char* points) {
    NODE_LOG("Draw Points count %d, color 0x%08x, width %f, join %d, cap %d in " MTK_RECT_STRING
        " <%p> ===> Points(%s)", args->count, args->color, args->strokeWidth, args->strokeJoin,
        args->strokeCap, MTK_RECT_ARGS(args->localBounds), id, args->points ? args->points : "");
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const PointsOp& op) {
    SETUP_NODE(PointsOp);
    args->count = op.floatCount;
    args->localBounds = COPY(Rect, op.unmappedBounds);
    args->color = op.paint->getColor();
    args->strokeWidth = op.paint->getStrokeWidth();
    args->strokeJoin = op.paint->getStrokeJoin();
    args->strokeCap = op.paint->getStrokeCap();
    if (op.floatCount > 0) {
        String8 log;
        for (int i = 0; i < op.floatCount; i += 2) {
            log.appendFormat("(%d,%d)", (int) op.points[i], (int) op.points[i + 1]);
        }
        args->points = copyCharString(log.string(), log.size());
    } else {
        args->points = nullptr;
    }
}

NODE_CREATE5(RectOp, Rect* localBounds, int style, int isAntiAlias,
      SkBlendMode xfermode, int color) {
    NODE_LOG("Draw Rect " MTK_RECT_STRING ", style %d, AA %d, mode %d, color 0x%08x <%p>",
        MTK_RECT_ARGS(args->localBounds), args->style, args->isAntiAlias,
        args->xfermode, args->color, id);
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const RectOp& op) {
    SETUP_NODE(RectOp);
    args->localBounds = COPY(Rect, op.unmappedBounds);
    args->style = op.paint->getStyle();
    args->isAntiAlias = op.paint->isAntiAlias();
    args->xfermode = PaintUtils::getBlendModeDirect(op.paint);
    args->color = op.paint->getColor();
}

NODE_CREATE6(RoundRectOp, Rect* localBounds, float rx, float ry,
        int style, bool isAntiAlias, int color) {
    NODE_LOG("Draw RoundRect " MTK_RECT_STRING ", rx %f, ry %f, style %d, AA %d, color 0x%08x <%p>",
    MTK_RECT_ARGS(args->localBounds), args->rx, args->ry, args->style, args->isAntiAlias,
    args->color, id);
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const RoundRectOp& op) {
    SETUP_NODE(RoundRectOp);
    args->localBounds = COPY(Rect, op.unmappedBounds);
    args->rx = op.rx;
    args->ry = op.ry;
    args->style = op.paint->getStyle();
    args->isAntiAlias = op.paint->isAntiAlias();
    args->color = op.paint->getColor();
}

NODE_CREATE1(SimpleRectsOp, int count) {
    NODE_LOG("Draw Rects count %d <%p>", args->count, id);
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const SimpleRectsOp& op) {
    SETUP_NODE(SimpleRectsOp);
    args->count = op.vertexCount;
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const TextOnPathOp& op) {
    // not supported
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const TextureLayerOp& op) {
    SETUP_NODE(DrawLayerOp);
    // TODO use layerHandle?
    Layer* layer = op.layerHandle->backingLayer();
    args->layer = layer;
    args->x = op.unmappedBounds.left;
    args->y = op.unmappedBounds.top;
    args->width = op.unmappedBounds.getWidth();
    args->height = op.unmappedBounds.getHeight();
    args->textureWidth = layer->getWidth();
    args->textureHeight = layer->getHeight();
    args->alpha = layer->getAlpha();
    args->isIdentity = layer->getTransform().isIdentity();
    args->isBlend = layer->isBlend();
    args->isTextureLayer = true;
}

NODE_CREATE4(BitmapOp, const Bitmap* bitmap, bool entry, Rect* localBounds, Rect* resolved) {
    NODE_LOG("Draw bitmap %p%s at " MTK_RECT_STRING " <%p> ==> resloved" MTK_RECT_STRING,
        args->bitmap, args->entry ? " using AssetAtlas" : "",
        MTK_RECT_ARGS(args->localBounds), id, MTK_RECT_ARGS(args->resolved));
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const BitmapOp& op) {
    SETUP_NODE(BitmapOp);
    args->bitmap = op.bitmap;
    args->entry = false; // TODO: asset atlas/texture id lookup?
    args->localBounds = COPY(Rect, op.unmappedBounds);

    ResolvedRenderState resolved(allocator(), *state.writableSnapshot(), op, false, false);
    args->resolved = COPY(Rect, resolved.clippedBounds);
}

NODE_CREATE3(PatchOp, const Bitmap* bitmap, bool entry, Rect* localBounds) {
    NODE_LOG("Draw patch %p%s at " MTK_RECT_STRING " <%p>", args->bitmap,
        args->entry ? " using AssetAtlas" : "", MTK_RECT_ARGS(args->localBounds), id);
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const PatchOp& op) {
    SETUP_NODE(PatchOp);
    args->bitmap = op.bitmap;
    args->entry = false;
    args->localBounds = COPY(Rect, op.unmappedBounds);
}

NODE_CREATE4(TextOp, char* str, int glyphCount, int color, Rect* localBounds) {
    NODE_LOG("Draw Text \"%s\", glyphCount %d, color 0x%08x at " MTK_RECT_STRING " <%p>",
        args->str, args->glyphCount, args->color,
        MTK_RECT_ARGS(args->localBounds), id);
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const TextOp& op) {
    SETUP_NODE(TextOp);
    args->glyphCount = op.glyphCount;
    args->color = op.paint->getColor();
    args->localBounds = COPY(Rect, op.unmappedBounds);
    args->str = unicharToChar(op.paint, op.glyphs, op.glyphCount);
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const MTKSaveOp& op) {
    addSave(RecordMode::Operation, state, op.flags, state.save(op.flags));
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const MTKRestoreToCountOp& op) {
    int offset = mOpList->mCurrentGroup ? mOpList->mCurrentGroup->mSaveCountOffset : 0;
    addRestoreToCount(RecordMode::Operation, state, offset + op.count);
    state.restoreToCount(offset + op.count);
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const MTKSaveLayerOp& op) {
    addSaveLayer(mode, state, op.unmappedBounds, PaintUtils::getAlphaDirect(op.paint), op.flags);
}

Matrix4* DisplayListRecorder::loadCurrentTransform(CanvasState& state, const RecordedOp& op) {
    Matrix4 transform;
    transform.loadMultiply(*state.currentTransform(), op.localMatrix);
    Matrix4* copy = MALLOC(Matrix4);
    *copy = transform;
    return copy;
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const MTKTranslateOp& op) {
    SETUP_NODE(TranslateOp);
    args->dx = op.dx;
    args->dy = op.dy;
    args->currentTransform = loadCurrentTransform(state, op);
}

NODE_CREATE2(RotateOp, float degrees, Matrix4* currentTransform) {
    NODE_LOG("Rotate by %f degrees ===> currTrans" MTK_MATRIX_4_STRING,
        args->degrees, MTK_MATRIX_4_ARGS(args->currentTransform));
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const MTKRotateOp& op) {
    SETUP_NODE(RotateOp);
    args->degrees = op.degrees;
    args->currentTransform = loadCurrentTransform(state, op);
}

NODE_CREATE3(ScaleOp, float sx, float sy, Matrix4* currentTransform) {
    NODE_LOG("Scale by (%.2f, %.2f) ===> currTrans" MTK_MATRIX_4_STRING, args->sx, args->sy,
                MTK_MATRIX_4_ARGS(args->currentTransform));
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const MTKScaleOp& op) {
    SETUP_NODE(ScaleOp);
    args->sx = op.sx;
    args->sy = op.sy;
    args->currentTransform = loadCurrentTransform(state, op);
}

NODE_CREATE3(SkewOp, float sx, float sy, Matrix4* currentTransform) {
    NODE_LOG("Skew by (%.2f, %.2f) ===> currTrans" MTK_MATRIX_4_STRING, args->sx, args->sy,
                MTK_MATRIX_4_ARGS(args->currentTransform));
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const MTKSkewOp& op) {
    SETUP_NODE(SkewOp);
    args->sx = op.sx;
    args->sy = op.sy;
    args->currentTransform = loadCurrentTransform(state, op);
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const MTKConcatOp& op) {
    SETUP_NODE(ContactMatrixOp);
    args->matrix = COPY(Matrix4, op.matrix);
    args->currentTransform = loadCurrentTransform(state, op);
}

NODE_CREATE2(SetMatrixOp, Matrix4* matrix, Matrix4* currentTransform) {
    NODE_LOG("SetMatrix " MTK_MATRIX_4_STRING " ===> currTrans" MTK_MATRIX_4_STRING,
        MTK_MATRIX_4_ARGS(args->matrix), MTK_MATRIX_4_ARGS(args->currentTransform));
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const MTKSetMatrixOp& op) {
    SETUP_NODE(SetMatrixOp);
    args->matrix = COPY(Matrix4, op.matrix);
    args->currentTransform = loadCurrentTransform(state, op);
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const MTKClipRectOp& op) {
    int count = state.save(SaveFlags::MatrixClip);
    state.concatMatrix(op.localMatrix);
    state.clipRect(op.area.left, op.area.top, op.area.right, op.area.bottom, op.op);
    addClipRect(RecordMode::Operation, state, op.area.left, op.area.top, op.area.right, op.area.bottom, op.op);
    state.restoreToCount(count);
}

NODE_CREATE3(ClipPathOp, SkRect* bounds, Rect* clipRect, char* clipRegion)  {
    NODE_LOG("ClipPath bounds " MTK_SK_RECT_STRING " <%p> ===> currClip" MTK_RECT_STRING,
         MTK_SK_RECT_ARGS(args->bounds), id, MTK_RECT_ARGS(args->clipRect));
    if (args->clipRegion) {
        NODE_LOG("%s", args->clipRegion);
    }
}

void DisplayListRecorder::recordImpl(MTK_BASE_PARAMS, const MTKClipPathOp& op) {
    SETUP_NODE(ClipPathOp);
    int count = state.save(SaveFlags::MatrixClip);
    state.concatMatrix(op.localMatrix);
    state.clipPath(op.path, op.op);
    args->bounds = COPY(SkRect, op.path->getBounds());
    args->clipRect = COPY(Rect, state.currentSnapshot()->getClipArea().getClipRect());
    args->clipRegion = regionToChar(state.currentSnapshot()->getClipArea().getClipRegion());
    state.restoreToCount(count);
}

///////////////////////////////////////////////////////////////////////////////
// CanvasStateBuilding
///////////////////////////////////////////////////////////////////////////////

int CanvasStateBuilding::save(int flags) {
    mRecorder.addSave(mMode, mCanvasState, flags, mCanvasState.getSaveCount());
    return mCanvasState.save(flags);
}

void CanvasStateBuilding::restore() {
    mRecorder.addRestoreToCount(mMode, mCanvasState, mCanvasState.getSaveCount() - 1);
    mCanvasState.restore();
}

void CanvasStateBuilding::restoreToCount(int saveCount) {
    mRecorder.addRestoreToCount(mMode, mCanvasState, saveCount);
    mCanvasState.restoreToCount(saveCount);
}
void CanvasStateBuilding::concatMatrix(const Matrix4& matrix) {
    mCanvasState.concatMatrix(matrix);
    mRecorder.addConcatMatrix(mMode, mCanvasState, matrix);
}
void CanvasStateBuilding::translate(float dx, float dy, float dz) {
    mCanvasState.translate(dx, dy, dz);
    mRecorder.addTranslate(mMode, mCanvasState, dx, dy, dz);
}

bool CanvasStateBuilding::clipRect(float left, float top, float right, float bottom, SkClipOp op) {
    bool ret = mCanvasState.clipRect(left, top, right, bottom, op);
    mRecorder.addClipRect(mMode, mCanvasState, left, top, right, bottom, op);
    return ret;
}

void CanvasStateBuilding::scaleAlpha(float alpha) {
    mCanvasState.scaleAlpha(alpha);
    mRecorder.addScaleAlpha(mMode, mCanvasState, alpha);
}

void CanvasStateBuilding::setClippingOutline(LinearAllocator& allocator, const Outline* outline) {
    mCanvasState.setClippingOutline(allocator, outline);
    mRecorder.addSetClippingOutline(mMode, mCanvasState, outline);
}

void CanvasStateBuilding::setClippingRoundRect(LinearAllocator& allocator,
        const Rect& rect, float radius, bool highPriority) {
    mCanvasState.setClippingRoundRect(allocator, rect, radius, highPriority);
    mRecorder.addSetClippingRoundRect(mMode, mCanvasState, rect, radius, highPriority);
}

///////////////////////////////////////////////////////////////////////////////
// CanvasStateRecording
///////////////////////////////////////////////////////////////////////////////

int CanvasStateRecording::save(int flags, bool inSaveLayer) {
    if (!inSaveLayer)
        flushAndAddOp(alloc().create_trivial<MTKSaveOp>(flags));
    int count = mCanvasState.save(flags);
    if (inSaveLayer) {
        flushRestoreToCount();
        flushTranslate();
    }
    return count;
}

void CanvasStateRecording::restore() {
    if (mRestoreSaveCount < 0) {
        restoreToCount(getSaveCount() - 1);
        return;
    }

    mRestoreSaveCount--;
    flushTranslate();
    mCanvasState.restore();
}

void CanvasStateRecording::restoreToCount(int saveCount) {
    if (mCanvasState.getSaveCount() != saveCount) {
        mRestoreSaveCount = saveCount;
    }
    flushTranslate();
    mCanvasState.restoreToCount(saveCount);
}

#define FLUSH_ADD(CMD, OP) \
    flushRestoreToCount(); \
    flushTranslate(); \
    CMD; \
    if (g_HWUI_debug_record_state) \
        addToAllOps(OP);

void CanvasStateRecording::setMatrix(const SkMatrix& matrix) {
    FLUSH_ADD(mCanvasState.setMatrix(matrix),
        alloc().create_trivial<MTKSetMatrixOp>(
            *mCanvasState.currentSnapshot()->transform,
            matrix));
}

void CanvasStateRecording::concatMatrix(const SkMatrix& matrix) {
    FLUSH_ADD(mCanvasState.concatMatrix(matrix),
        alloc().create_trivial<MTKConcatOp>(
            *mCanvasState.currentSnapshot()->transform,
            matrix));
}

void CanvasStateRecording::rotate(float degrees) {
    if (degrees == 0) return;

    FLUSH_ADD(mCanvasState.rotate(degrees),
        alloc().create_trivial<MTKRotateOp>(
            *mCanvasState.currentSnapshot()->transform,
            degrees));
}

void CanvasStateRecording::scale(float sx, float sy) {
    if (sx == 1 && sy == 1) return;

    FLUSH_ADD(mCanvasState.scale(sx, sy),
        alloc().create_trivial<MTKScaleOp>(
            *mCanvasState.currentSnapshot()->transform,
            sx,
            sy));
}

void CanvasStateRecording::skew(float sx, float sy) {
    FLUSH_ADD(mCanvasState.skew(sx, sy),
        alloc().create_trivial<MTKSkewOp>(
            *mCanvasState.currentSnapshot()->transform,
            sx,
            sy));
}

void CanvasStateRecording::translate(float dx, float dy, float dz) {
    if (dx == 0 && dy == 0) return;

    mHasDeferredTranslate = true;
    mTranslateX += dx;
    mTranslateY += dy;
    flushRestoreToCount();
    mCanvasState.translate(dx, dy, 0);
}

#define ADD_RETURN(CMD, OP) \
    if (g_HWUI_debug_record_state) \
        addToAllOps(OP); \
    return CMD\

bool CanvasStateRecording::clipRect(float left, float top, float right, float bottom, SkClipOp op) {
    ADD_RETURN(mCanvasState.clipRect(left, top, right, bottom, op),
        alloc().create_trivial<MTKClipRectOp>(
            *mCanvasState.currentSnapshot()->transform,
            left, top, right, bottom, op));
}

bool CanvasStateRecording::clipPath(const SkPath* path, SkClipOp op) {
    ADD_RETURN(mCanvasState.clipPath(path, op),
        alloc().create_trivial<MTKClipPathOp>(
            *mCanvasState.currentSnapshot()->transform,
            path, op));
}

void CanvasStateRecording::resetForRecording(DisplayList* displayList) {
    if (!displayList) {
        flushRestoreToCount();
        flushTranslate();
    }

    mOwner = displayList;
    mRestoreSaveCount = -1;
}

void CanvasStateRecording::flushRestoreToCount() {
    if (mRestoreSaveCount >= 0) {
        if (mOwner && g_HWUI_debug_record_state) {
            addToAllOps(alloc().create_trivial<MTKRestoreToCountOp>(mRestoreSaveCount));
        }
        mRestoreSaveCount = -1;
    }
}

void CanvasStateRecording::flushTranslate() {
    if (mHasDeferredTranslate) {
        if (mTranslateX != 0.0f || mTranslateY != 0.0f) {
            if (mOwner && g_HWUI_debug_record_state) {
                addToAllOps(alloc().create_trivial<MTKTranslateOp>(
                        *mCanvasState.currentSnapshot()->transform,
                        mTranslateX,
                        mTranslateY));
            }
            mTranslateX = mTranslateY = 0.0f;
        }
        mHasDeferredTranslate = false;
    }
}

void CanvasStateRecording::flushAndAddOp(RecordedOp* op) {
    flushRestoreToCount();
    flushTranslate();
    addToAllOps(op);
}

void CanvasStateRecording::addToAllOps(RecordedOp* op) {
    if (mOwner && g_HWUI_debug_record_state) {
        mOwner->allOps.push_back(op);

        if (op->opId == RecordedOpId::BeginLayerOp ||
            op->opId == RecordedOpId::BeginUnclippedLayerOp) {
            flushAndAddOp(alloc().create_trivial<MTKSaveLayerOp>(
                 op->unmappedBounds,
                 *mCanvasState.currentSnapshot()->transform,
                 op->localClip,
                 op->paint,
                 op->opId == RecordedOpId::BeginLayerOp ? SaveFlags::ClipToLayer : 0));
        }
    }
}

#define STRINGIFY(n) #n,
static const char* sOpNameLut[] = {
        MAP_OPS_BASED_ON_TYPE(STRINGIFY, STRINGIFY, STRINGIFY, STRINGIFY)
        "SaveOp",
        "RestoreToCountOp",
        "SaveLayerOp",
        "TranslateOp",
        "RotateOp",
        "ScaleOp",
        "SkewOp",
        "ConcatOp",
        "SetMatrixOp",
        "ClipRectOp",
        "ClipPathOp",
        "ClipRegionOp",
    };

void MTKOpDumper::dump(const RecordedOp& op, std::ostream& output, int level) {
    for (int i = 0; i < level; i++) {
        output << "  ";
    }

    Rect localBounds(op.unmappedBounds);
    op.localMatrix.mapRect(localBounds);
    output << sOpNameLut[op.opId] << " localMatrix=" << op.localMatrix << ", unmappedBounds="
        << op.unmappedBounds << ", localBounds=" << localBounds;

    if (op.localClip) {
        output << std::fixed << std::setprecision(0)
             << " clip=" << op.localClip->rect
             << " mode=" << (int)op.localClip->mode;
    }
}

}; // namespace uirenderer
}; // namespace android

#endif
