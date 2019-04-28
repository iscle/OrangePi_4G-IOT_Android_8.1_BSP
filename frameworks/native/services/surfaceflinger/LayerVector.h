/*
 * Copyright (C) 2016 The Android Open Source Project
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

#ifndef ANDROID_SURFACE_FLINGER_LAYER_VECTOR_H
#define ANDROID_SURFACE_FLINGER_LAYER_VECTOR_H

#include <utils/SortedVector.h>
#include <utils/RefBase.h>

#include <functional>

namespace android {
class Layer;

/*
 * Used by the top-level SurfaceFlinger state and individual layers
 * to track layers they own sorted according to Z-order. Provides traversal
 * functions for traversing all owned layers, and their descendents.
 */
class LayerVector : public SortedVector<sp<Layer>> {
public:
    LayerVector();
    LayerVector(const LayerVector& rhs);
    ~LayerVector() override;

    enum class StateSet {
        Invalid,
        Current,
        Drawing,
    };

    // Sorts layer by layer-stack, Z order, and finally creation order (sequence).
    int do_compare(const void* lhs, const void* rhs) const override;

    using Visitor = std::function<void(Layer*)>;
    void traverseInReverseZOrder(StateSet stateSet, const Visitor& visitor) const;
    void traverseInZOrder(StateSet stateSet, const Visitor& visitor) const;
};
}

#endif /* ANDROID_SURFACE_FLINGER_LAYER_VECTOR_H_ */
