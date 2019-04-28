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

#ifndef MTK_HWUI_DISPLAYLISTRECORDER_H
#define MTK_HWUI_DISPLAYLISTRECORDER_H

#include "../Matrix.h"
#include "../utils/LinearAllocator.h"
#include "../RecordedOp.h"

#include <utils/Log.h>
#include <utils/Singleton.h>
#include <utils/String8.h>
#include <utils/Trace.h>
#include <unordered_map>
#include <set>
#include <SkMatrix.h>
#include <SkPath.h>
#include <SkPaint.h>
#include <SkRegion.h>

namespace android {
namespace uirenderer {

/*
 * DisplayListRecorder records a list of display list op of a frame.
 * The result is different from RenderNode's output. It gives
 * more information about the current frame like clip region, color,
 * transformation, text, alpha ... so we can be easier to analyze
 * display wrong issues.
 */

///////////////////////////////////////////////////////////////////////////////
// Forward declaration
///////////////////////////////////////////////////////////////////////////////

class DisplayListRecorder;
class RenderNode;
class OpNode;
class OpList;
class LayerBuilder;
class DisplayListRecorderHost;
class Outline;
class OpGroupNode;
struct RecordedOp;

///////////////////////////////////////////////////////////////////////////////
// Defines
///////////////////////////////////////////////////////////////////////////////

namespace RecordMode {
    enum {
        Operation,
        View,
        System
    };
};

#define node_id void*
typedef void (*RunnableFunction)(DisplayListRecorder* recorder, node_id id, int level, int mode, void* data);

#define MTK_RECT_STRING "(%.0f, %.0f, %.0f, %.0f)"
#define MTK_RECT_ARGS(r) \
    (r)->left, (r)->top, (r)->right, (r)->bottom

#define MTK_SK_RECT_STRING "(%.0f, %.0f, %.0f, %.0f)"
#define MTK_SK_RECT_ARGS(r) \
        (r)->left(), (r)->top(), (r)->right(), (r)->bottom()

/* These MACRO are for SkMatrix which is row major
 *       | 0 1 2 |
 *       | 3 4 5 |
 *       | 6 7 8 |
*/
#define MTK_SK_MATRIX_STRING "{[%f %f %f] [%f %f %f] [%f %f %f]}"
#define MTK_SK_MATRIX_ARGS(m) \
    (m)->get(0), (m)->get(1), (m)->get(2), \
    (m)->get(3), (m)->get(4), (m)->get(5), \
    (m)->get(6), (m)->get(7), (m)->get(8)

/* but Matrix4 is column major
*       | 0  4  8  12 |
*       | 1  5  9  13 |
*       | 2  6 10  14 |
*       | 3  7 11  15 |
*/
#define MTK_MATRIX_4_STRING "{0x%x [%f %f %f %f] [%f %f %f %f] [%f %f %f %f] [%f %f %f %f]}"
#define MTK_MATRIX_4_ARGS(m) \
    (m)->getType(), \
    (m)->data[0], (m)->data[4], (m)->data[8], (m)->data[12], \
    (m)->data[1], (m)->data[5], (m)->data[9], (m)->data[13], \
    (m)->data[2], (m)->data[6], (m)->data[10], (m)->data[14], \
    (m)->data[3], (m)->data[7], (m)->data[11], (m)->data[15] \

#define MALLOC(TYPE) MALLOC_SIZE(TYPE, 1)
#define MALLOC_SIZE(TYPE, SIZE) reinterpret_cast<TYPE*>(allocator().template alloc<char>(SIZE * sizeof(TYPE)))
#define COPY(TYPE, SRC) (__tmp##TYPE = MALLOC(TYPE), *__tmp##TYPE = SRC, __tmp##TYPE)

#define NODE_LOG(...) recorder->log(level, mode, __VA_ARGS__)
#define NODE_ARGS(name) name ## Args

#define NODE_CREATE0(name) NC(name,,,,,,,,,,,)
#define NODE_CREATE1(name, a1) NC(name, a1,,,,,,,,,,)
#define NODE_CREATE2(name, a1, a2) NC(name, a1,a2,,,,,,,,,)
#define NODE_CREATE3(name, a1, a2, a3) NC(name, a1,a2,a3,,,,,,,,)
#define NODE_CREATE4(name, a1, a2, a3, a4) NC(name, a1,a2,a3,a4,,,,,,,)
#define NODE_CREATE5(name, a1, a2, a3, a4, a5) NC(name, a1,a2,a3,a4,a5,,,,,,)
#define NODE_CREATE6(name, a1, a2, a3, a4, a5, a6) NC(name, a1,a2,a3,a4,a5,a6,,,,,)
#define NODE_CREATE7(name, a1, a2, a3, a4, a5, a6, a7) NC(name, a1,a2,a3,a4,a5,a6,a7,,,,)
#define NODE_CREATE8(name, a1, a2, a3, a4, a5, a6, a7, a8) NC(name, a1,a2,a3,a4,a5,a6,a7,a8,,,)
#define NODE_CREATE9(name, a1, a2, a3, a4, a5, a6, a7, a8, a9) NC(name, a1,a2,a3,a4,a5,a6,a7,a8,a9,,)
#define NODE_CREATE10(name, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10) NC(name, a1,a2,a3,a4,a5,a6,a7,a8,a9,a10,)
#define NODE_CREATE11(name, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11) NC(name, a1,a2,a3,a4,a5,a6,a7,a8,a9,a10,a11)
#define NC(name, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11) \
        typedef struct { \
            a1; a2; a3; a4; a5; a6; a7; a8; a9; a10; a11;\
        } NODE_ARGS(name); \
        static void Node_Bridge_ ## name(DisplayListRecorder* recorder, \
            node_id id, int level, int mode, NODE_ARGS(name)* args)

#define SETUP_NODE_BASIC(name, isGroupNode, mode) \
    if (CC_LIKELY(!isEnabled())) return; \
    OpNode* node = createNode((RunnableFunction) Node_Bridge_ ## name, mOpList->mCurrentLevel, mode, isGroupNode); \
    NODE_ARGS(name) *args = MALLOC(NODE_ARGS(name)); \
    node->setData(args); \
    addOp(node); \
    Rect* __tmpRect = nullptr; \
    Matrix4* __tmpMatrix4 = nullptr; \
    SkRect* __tmpSkRect = nullptr; \
    SkIRect* __tmpSkIRect = nullptr; \
    unused(__tmpRect, __tmpMatrix4, __tmpSkRect, __tmpSkIRect)

#define SETUP_NODE(name) SETUP_NODE_BASIC(name, false, mode)
#define MTK_BASE_PARAMS int mode, CanvasState& state

///////////////////////////////////////////////////////////////////////////////
// MTK State Ops
///////////////////////////////////////////////////////////////////////////////

#define MTK_MAP_OPS_BASED_ON_TYPE(MTK_OP_FN) \
        MAP_OPS_BASED_ON_TYPE(MTK_OP_FN, MTK_OP_FN, MTK_OP_FN, MTK_OP_FN) \
        MTK_OP_FN(MTKSaveOp) \
        MTK_OP_FN(MTKRestoreToCountOp) \
        MTK_OP_FN(MTKSaveLayerOp) \
        MTK_OP_FN(MTKTranslateOp) \
        MTK_OP_FN(MTKRotateOp) \
        MTK_OP_FN(MTKScaleOp) \
        MTK_OP_FN(MTKSkewOp) \
        MTK_OP_FN(MTKConcatOp) \
        MTK_OP_FN(MTKSetMatrixOp) \
        MTK_OP_FN(MTKClipRectOp) \
        MTK_OP_FN(MTKClipPathOp)

namespace RecordedOpId {
    enum {
        MTKSaveOp = RecordedOpId::Count,
        MTKRestoreToCountOp,
        MTKSaveLayerOp,
        MTKTranslateOp,
        MTKRotateOp,
        MTKScaleOp,
        MTKSkewOp,
        MTKConcatOp,
        MTKSetMatrixOp,
        MTKClipRectOp,
        MTKClipPathOp,
    };
}

struct MTKSaveOp : RecordedOp {
    MTKSaveOp(int flags)
            : RecordedOp(RecordedOpId::MTKSaveOp, Rect(), Matrix4::identity(), nullptr, nullptr)
            , flags(flags) {}
    const int flags;
};

struct MTKRestoreToCountOp : RecordedOp {
        MTKRestoreToCountOp(int count)
            : RecordedOp(RecordedOpId::MTKRestoreToCountOp, Rect(), Matrix4::identity(), nullptr, nullptr)
            , count(count) {}
    const int count;
};

struct MTKSaveLayerOp : RecordedOp {
    MTKSaveLayerOp(BASE_PARAMS, int flags)
            : SUPER(MTKSaveLayerOp)
            , flags(flags) {}
    const int flags;
};

struct MTKTranslateOp : RecordedOp {
    MTKTranslateOp(const Matrix4& localMatrix, float dx, float dy)
        : RecordedOp(RecordedOpId::MTKTranslateOp, Rect(), localMatrix, nullptr, nullptr)
        , dx(dx)
        , dy(dy) {}
    const float dx;
    const float dy;
};

struct MTKRotateOp : RecordedOp {
    MTKRotateOp(const Matrix4& localMatrix, float degrees)
        : RecordedOp(RecordedOpId::MTKRotateOp, Rect(), localMatrix, nullptr, nullptr)
        , degrees(degrees) {}
    const float degrees;
};

struct MTKScaleOp : RecordedOp {
        MTKScaleOp(const Matrix4& localMatrix, float sx, float sy)
            : RecordedOp(RecordedOpId::MTKScaleOp, Rect(), localMatrix, nullptr, nullptr)
            , sx(sx)
            , sy(sy) {}
    const float sx;
    const float sy;
};

struct MTKSkewOp : RecordedOp {
        MTKSkewOp(const Matrix4& localMatrix, float sx, float sy)
            : RecordedOp(RecordedOpId::MTKSkewOp, Rect(), localMatrix, nullptr, nullptr)
            , sx(sx)
            , sy(sy) {}
    const float sx;
    const float sy;
};

struct MTKConcatOp : RecordedOp {
        MTKConcatOp(const Matrix4& localMatrix, const SkMatrix& matrix)
            : RecordedOp(RecordedOpId::MTKConcatOp, Rect(), localMatrix, nullptr, nullptr)
            , matrix(matrix) {}
    const mat4 matrix;
};

struct MTKSetMatrixOp : RecordedOp {
        MTKSetMatrixOp(const Matrix4& localMatrix, const SkMatrix& matrix)
            : RecordedOp(RecordedOpId::MTKSetMatrixOp, Rect(), localMatrix, nullptr, nullptr)
            , matrix(matrix) {}
    const mat4 matrix;
};


struct MTKClipRectOp : RecordedOp {
        MTKClipRectOp(const Matrix4& localMatrix, float left, float top, float right, float bottom, SkClipOp op)
            : RecordedOp(RecordedOpId::MTKClipRectOp, Rect(), localMatrix, nullptr, nullptr)
            , area(left, top, right, bottom)
            , op(op) {}
    const Rect area;
    const SkClipOp op;
};

struct MTKClipPathOp : RecordedOp {
        MTKClipPathOp(const Matrix4& localMatrix, const SkPath* path, SkClipOp op)
            : RecordedOp(RecordedOpId::MTKClipPathOp, Rect(), localMatrix, nullptr, nullptr)
            , path(path)
            , op(op) {}
    const SkPath* path;
    const SkClipOp op;
};

///////////////////////////////////////////////////////////////////////////////
// OpNode
///////////////////////////////////////////////////////////////////////////////

class OpNode {
    friend class DisplayListRecorder;
public:
    OpNode(RunnableFunction function, int level, int mode)
        : mFunction(function)
        , mGroup(nullptr)
        , mPrev(nullptr)
        , mNext(nullptr)
        , mData(nullptr)
        , mId(this)
        , mLevel(level)
        , mMode(mode) {}
    // These objects should always be allocated with a LinearAllocator, and never destroyed/deleted.
    // standard new() intentionally not implemented, and delete/deconstructor should never be used.
    ~OpNode() { LOG_ALWAYS_FATAL("OpNode destructor not supported"); }
    static void operator delete(void* ptr) { LOG_ALWAYS_FATAL("OpNode delete not supported"); }
    static void* operator new(size_t size) = delete; /** PURPOSELY OMITTED **/
    static void* operator new(size_t size, LinearAllocator& allocator) {
        return allocator.alloc<char>(size);
    }

    void setData(void* data) {
        LOG_ALWAYS_FATAL_IF(mData, "Can't call OpNode payload twice!!");
        mData = data;
    }

private:
    RunnableFunction mFunction;
    OpGroupNode* mGroup;
    OpNode* mPrev;
    OpNode* mNext;
    void* mData;
    node_id mId;
    int mLevel;
    int mMode;
};

class OpGroupNode : public OpNode {
    friend class DisplayListRecorder;
public:
    OpGroupNode(RunnableFunction function, int level, int mode, int offset)
        : OpNode(function, level, mode)
        , mSaveCountOffset(offset) {}

private:
    int mSaveCountOffset = 0;
};

///////////////////////////////////////////////////////////////////////////////
// OpList
///////////////////////////////////////////////////////////////////////////////

class OpList {
    friend class DisplayListRecorder;
public:
    OpList(node_id id, int width, int height, void* layer)
        : mId(id)
        , mWidth(width)
        , mHeight(height)
        , mLayer(layer) {}
    // These objects should always be allocated with a LinearAllocator, and never destroyed/deleted.
    // standard new() intentionally not implemented, and delete/deconstructor should never be used.
    ~OpList() { LOG_ALWAYS_FATAL("OpList destructor not supported"); }
    static void operator delete(void* ptr) { LOG_ALWAYS_FATAL("OpList delete not supported"); }
    static void* operator new(size_t size) = delete; /** PURPOSELY OMITTED **/
    static void* operator new(size_t size, LinearAllocator& allocator) {
        return allocator.alloc<char>(size);
    }

    int getWidth() { return mWidth; }
    int getHeight() { return mHeight; }

private:
    OpNode* mHead = nullptr;
    OpNode* mTail = nullptr;
    OpGroupNode* mCurrentGroup = nullptr;
    node_id mId;
    int mWidth;
    int mHeight;
    void* mLayer;
    int mCurrentLevel = 0;
    int mSaveCount = 0;
};

///////////////////////////////////////////////////////////////////////////////
// DisplayListRecorder
///////////////////////////////////////////////////////////////////////////////

class DisplayListRecorder {
public:
    DisplayListRecorder(LinearAllocator& allocator) : mAllocator(allocator) {}
    ~DisplayListRecorder() {};

public:
    void setHost(DisplayListRecorderHost* host) { mHost = host; }
    DisplayListRecorderHost* getHost() { return mHost; } // host can be null

    void prepareRecording(node_id id, int width, int height, void* layer = nullptr);
    void finishRecording();
    LinearAllocator& allocator() { return mAllocator; }

    void addOp(OpNode* op);
    void addStart(CanvasState& state, RenderNode* rendernode);
    void addEnd(RenderNode* rendernode);
//    void addReject(CanvasState& state, RenderNode* rendernode, bool isQuick);
    void addStartVirtual(node_id virtualNode, float width, float height);
    void addEndVirtual(node_id virtualNode);

    void addSave(MTK_BASE_PARAMS, int flags, int count);
    void addSaveLayer(MTK_BASE_PARAMS, Rect area, int alpha, int flags);
    void addRestoreToCount(MTK_BASE_PARAMS, int count);
    void addTranslate(MTK_BASE_PARAMS, float dx, float dy,  float dz = 0);
    void addConcatMatrix(MTK_BASE_PARAMS,const Matrix4& matrix);
    void addClipRect(MTK_BASE_PARAMS, float left, float top, float right, float bottom, SkClipOp op);

    void addScaleAlpha(MTK_BASE_PARAMS, float alpha);
    void addSetClippingOutline(MTK_BASE_PARAMS, const Outline* outline);
    void addSetClippingRoundRect(MTK_BASE_PARAMS, const Rect& rect, float radius, bool highPriority);

    bool isEnabled();
    void log(int level, int mode, const char* fmt, ...);
    void dump(FILE* file = nullptr);
    void record(MTK_BASE_PARAMS, const RecordedOp& op);
    const char* getName();
    int getFrameCount();

    void increaseSaveCountOffset () {
        if (mOpList && mOpList->mCurrentGroup)
            mOpList->mCurrentGroup->mSaveCountOffset++;
    }

    void decreaseSaveCountOffset () {
        if (mOpList && mOpList->mCurrentGroup)
            mOpList->mCurrentGroup->mSaveCountOffset--;
    }

private:
#define X(Type) void recordImpl(MTK_BASE_PARAMS, const Type& op);
    MTK_MAP_OPS_BASED_ON_TYPE(X)
#undef X

    OpNode* createNode(RunnableFunction function, int level, int mode, bool isGroupNode = false) {
        OpNode* node = nullptr;
        if (isGroupNode) {
            node = new (allocator()) OpGroupNode(function, level, mode, mOpList->mSaveCount);
        } else {
            node = new (allocator()) OpNode(function, level, mode);
        }
        return node;
    }

    // helper
    char* unicharToChar(const SkPaint* paint, const glyph_t* text, int count);
    char* regionToChar(const SkRegion& clipRegion);
    char* copyCharString(const char* str, int len);
    Matrix4* loadCurrentTransform(CanvasState& state, const RecordedOp& op);

    DisplayListRecorderHost* mHost = nullptr;
    OpList* mOpList = nullptr;
    char* mTempBuffer = nullptr;
    FILE* mTempFile = nullptr;

    // allocator into which all ops were allocated
    LinearAllocator& mAllocator;
};

///////////////////////////////////////////////////////////////////////////////
// DisplayListRecorderHost
///////////////////////////////////////////////////////////////////////////////

class DisplayListRecorderHost {
public:
    DisplayListRecorderHost() {}
    virtual ~DisplayListRecorderHost() {}

    virtual const std::string& name() = 0;

    void onBeginFrame() { mFrameCount = mFrameCount < INT_MAX - 1 ? mFrameCount + 1 : 1; };
    void onEndFrame() {};
    int getFrameCount() { return mFrameCount; }

private:
    int mFrameCount = 0;
};

///////////////////////////////////////////////////////////////////////////////
// DisplayListRecorderClient
///////////////////////////////////////////////////////////////////////////////

#define RECORD_FRAME(CMD) \
    frameBuilder.recorder().prepareRecording(&frameBuilder, \
        frameBuilder.getViewportWidth(), frameBuilder.getViewportHeight()); \
    frameBuilder.recorder().addStartVirtual(&frameBuilder, \
        frameBuilder.getViewportWidth(), frameBuilder.getViewportHeight()); \
    CMD; \
    frameBuilder.recorder().addEndVirtual(&frameBuilder); \
    frameBuilder.recorder().finishRecording();

#define RECORD_DRAW(CMD) \
    if (recorder().isEnabled()) { \
        if (opIndex == chunk.beginOpIndex) prevDrawOpIndex = currentDrawOpIndex = -1; \
        for (size_t i = currentDrawOpIndex + 1; i < displayList.getAllOps().size(); ++i) { \
            const RecordedOp* localOp = displayList.getAllOps()[i]; \
            if (localOp == op) { \
                prevDrawOpIndex = currentDrawOpIndex; \
                currentDrawOpIndex = i; \
                break; \
            } \
        } \
        for (int i = prevDrawOpIndex + 1; i < currentDrawOpIndex; ++i) { \
            const RecordedOp* localOp = displayList.getAllOps()[i]; \
            recorder().record(RecordMode::Operation, mCanvasState.getCanvasState(), *localOp); \
        } \
    } \
    CMD; \
    if (recorder().isEnabled()) { \
        recorder().record(RecordMode::Operation, mCanvasState.getCanvasState(), *op); \
        if (opIndex == chunk.endOpIndex - 1) { \
            for (size_t i = currentDrawOpIndex + 1; i < displayList.getAllOps().size(); ++i) { \
                const RecordedOp* localOp = displayList.getAllOps()[i]; \
                recorder().record(RecordMode::Operation, mCanvasState.getCanvasState(), *localOp); \
            } \
        } \
    }

#define RECORD_LAYER(CMD) \
        recorder().prepareRecording(layerNode, \
            layerNode->getWidth(), layerNode->getHeight(), layerNode->getLayer()); \
        { \
            ScopedRecord record(recorder(), mCanvasState, layerNode); \
            CMD; \
        } \
        recorder().finishRecording()

class DisplayListRecorderClient {
public:
    DisplayListRecorderClient() : mRecorder(mAllocator) {}
    DisplayListRecorderClient(DisplayListRecorderHost* host, uint32_t viewportWidth, uint32_t viewportHeight)
        : mRecorder(mAllocator)
        , mViewportWidth(viewportWidth)
        , mViewportHeight(viewportHeight) {
        mRecorder.setHost(host);
        if (host) host->onBeginFrame();
    }

    void setViewport(uint32_t viewportWidth, uint32_t viewportHeight) {
        mViewportWidth = viewportWidth;
        mViewportHeight = viewportHeight;
    }

    void setDisplayListRecorderHost(DisplayListRecorderHost* host) {
        mRecorder.setHost(host);
        if (host) host->onBeginFrame();
    }

    virtual ~DisplayListRecorderClient() {
        if (mRecorder.getHost()) mRecorder.getHost()->onEndFrame();
    }

    DisplayListRecorder& recorder() {
        return mRecorder;
    }

    uint32_t getViewportWidth() { return mViewportWidth; }
    uint32_t getViewportHeight() { return mViewportHeight; }

private:
    LinearAllocator mAllocator;
    DisplayListRecorder mRecorder;
    uint32_t mViewportWidth = 0;
    uint32_t mViewportHeight = 0;
};

///////////////////////////////////////////////////////////////////////////////
// CanvasStateBuilding
///////////////////////////////////////////////////////////////////////////////

class CanvasStateBuilding {
public:
    CanvasStateBuilding(CanvasStateClient& renderer, DisplayListRecorder& recorder)
        : mCanvasState(renderer)
        , mRecorder(recorder) {}
    virtual ~CanvasStateBuilding() {};

    void initializeSaveStack(int viewportWidth, int viewportHeight,
            float clipLeft, float clipTop, float clipRight, float clipBottom,
            const Vector3& lightCenter) {
        mCanvasState.initializeSaveStack(viewportWidth, viewportHeight,
            clipLeft, clipTop, clipRight, clipBottom, lightCenter);
    }
    inline const Snapshot* currentSnapshot() const { return mCanvasState.currentSnapshot(); }
    inline Snapshot* writableSnapshot() { return mCanvasState.writableSnapshot(); }
    inline const mat4* currentTransform() const { return currentSnapshot()->transform; }
    int getSaveCount() const { return mCanvasState.getSaveCount(); }

    int save(int flags);
    void restore();
    void restoreToCount(int saveCount);
    void concatMatrix(const Matrix4& matrix);
    void translate(float dx, float dy, float dz = 0.0f);
    bool clipRect(float left, float top, float right, float bottom, SkClipOp op);

    void scaleAlpha(float alpha);
    void setClippingOutline(LinearAllocator& allocator, const Outline* outline);
    void setClippingRoundRect(LinearAllocator& allocator,
        const Rect& rect, float radius, bool highPriority = true);

    bool quickRejectConservative(float left, float top, float right, float bottom) const {
        return mCanvasState.quickRejectConservative(left, top, right, bottom);
    }
    const Rect& getLocalClipBounds() const { return mCanvasState.getLocalClipBounds(); }
    const Rect& getRenderTargetClipBounds() const { return mCanvasState.getRenderTargetClipBounds(); }
    void setProjectionPathMask(const SkPath* path) {
        mCanvasState.setProjectionPathMask(path);
    }

    CanvasState& getCanvasState() { return mCanvasState; }
    void setRecordMode(int mode) { mMode = mode; }

private:
    CanvasState mCanvasState;
    DisplayListRecorder& mRecorder;
    int mMode = RecordMode::System;
};

///////////////////////////////////////////////////////////////////////////////
// CanvasStateRecording
///////////////////////////////////////////////////////////////////////////////

class CanvasStateRecording {
public:
    CanvasStateRecording(CanvasStateClient& renderer)
        : mCanvasState(renderer) {}
    virtual ~CanvasStateRecording() {};

    void initializeRecordingSaveStack(int viewportWidth, int viewportHeight) {
        mCanvasState.initializeRecordingSaveStack(viewportWidth, viewportHeight);
    }
    void initializeSaveStack(int viewportWidth, int viewportHeight,
            float clipLeft, float clipTop, float clipRight, float clipBottom,
            const Vector3& lightCenter) {
        mCanvasState.initializeSaveStack(viewportWidth, viewportHeight,
            clipLeft, clipTop, clipRight, clipBottom, lightCenter);
    }
    inline const Snapshot* currentSnapshot() const { return mCanvasState.currentSnapshot(); }
    inline Snapshot* writableSnapshot() { return mCanvasState.writableSnapshot(); }
    inline const mat4* currentTransform() const { return currentSnapshot()->transform; }
    int getSaveCount() const { return mCanvasState.getSaveCount(); }

    int save(int flags, bool inSaveLayer = false);
    void restore();
    void restoreToCount(int saveCount);

    void getMatrix(SkMatrix* outMatrix) const { mCanvasState.getMatrix(outMatrix); }
    void setMatrix(const SkMatrix& matrix);
    void concatMatrix(const SkMatrix& matrix);
    void translate(float dx, float dy, float dz = 0.0f);
    void rotate(float degrees);
    void scale(float sx, float sy);
    void skew(float sx, float sy);

    bool quickRejectConservative(float left, float top, float right, float bottom) const {
       return mCanvasState.quickRejectConservative(left, top, right, bottom);
    }
    const Rect& getLocalClipBounds() const { return mCanvasState.getLocalClipBounds(); }
    int getWidth() const { return mCanvasState.getWidth(); }
    int getHeight() const { return mCanvasState.getHeight(); }

    bool clipRect(float left, float top, float right, float bottom, SkClipOp op);
    bool clipPath(const SkPath* path, SkClipOp op);

    void flushRestoreToCount();
    void flushTranslate();
    void flushAndAddOp(RecordedOp* op);
    void addToAllOps(RecordedOp* op);

    void resetForRecording(DisplayList* displayList = nullptr);

private:
    LinearAllocator& alloc() { return mOwner->allocator; }

    int mRestoreSaveCount = -1;
    float mTranslateX = 0.0f;
    float mTranslateY = 0.0f;
    bool mHasDeferredTranslate = false;

    CanvasState mCanvasState;
    DisplayList* mOwner = nullptr;
};

///////////////////////////////////////////////////////////////////////////////
// MTKOpDumper
///////////////////////////////////////////////////////////////////////////////

class ScopedRecord {
public:
    inline ScopedRecord(DisplayListRecorder& recorder, CanvasStateBuilding& state, RenderNode* node)
        : mRecorder(recorder)
        , mRenderNode(node) {
        mRecorder.addStart(state.getCanvasState(), mRenderNode);
    }

    inline ~ScopedRecord() {
        mRecorder.addEnd(mRenderNode);
    }
private:
    DisplayListRecorder mRecorder;
    RenderNode* mRenderNode = nullptr;
};

///////////////////////////////////////////////////////////////////////////////
// MTKOpDumper
///////////////////////////////////////////////////////////////////////////////

class MTKOpDumper {
public:
    static void dump(const RecordedOp& op, std::ostream& output, int level = 0);
};


}; // namespace uirenderer
}; // namespace android

#endif /* MTK_HWUI_DISPLAYLISTRECORDER */
