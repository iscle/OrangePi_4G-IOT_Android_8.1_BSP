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

#ifndef C2COMPONENT_H_

#define C2COMPONENT_H_

#include <stdbool.h>
#include <stdint.h>

#include <list>
#include <memory>
#include <vector>
#include <functional>

#include <C2Param.h>
#include <C2Work.h>

namespace android {

/// \defgroup components Components
/// @{

class C2Component;

class C2ComponentListener {
public:
    virtual void onWorkDone(std::weak_ptr<C2Component> component,
                            std::vector<std::unique_ptr<C2Work>> workItems) = 0;

    virtual void onTripped(std::weak_ptr<C2Component> component,
                           std::vector<std::shared_ptr<C2SettingResult>> settingResult) = 0;

    virtual void onError(std::weak_ptr<C2Component> component,
                         uint32_t errorCode) = 0;

    // virtual void onTunnelReleased(<from>, <to>) = 0;

    // virtual void onComponentReleased(<id>) = 0;

protected:
    virtual ~C2ComponentListener();
};

/**
 * Component interface object. This object contains all of the configuration of a potential or
 * actual component. It can be created and used independently of an actual C2Component instance to
 * query support and parameters for various component settings and configurations for a potential
 * component. Actual components also expose this interface.
 */

class C2ComponentInterface {
public:
    // ALWAYS AVAILABLE METHODS
    // =============================================================================================

    /**
     * Returns the name of this component or component interface object.
     * This is a unique name for this component or component interface 'class'; however, multiple
     * instances of this component SHALL have the same name.
     *
     * This method MUST be supported in any state. This call does not change the state nor the
     * internal states of the component.
     *
     * This method MUST be "non-blocking" and return within 1ms.
     *
     * \return the name of this component or component interface object.
     * \retval an empty string if there was not enough memory to allocate the actual name.
     */
    virtual C2String getName() const = 0;

    /**
     * Returns a unique ID for this component or interface object.
     * This ID is used as work targets, unique work IDs, and when configuring tunneling.
     *
     * This method MUST be supported in any state. This call does not change the state nor the
     * internal states of the component.
     *
     * This method MUST be "non-blocking" and return within 1ms.
     *
     * \return a unique node ID for this component or component interface instance.
     */
    virtual node_id getId() const = 0;

    /**
     * Queries a set of parameters from the component or interface object.
     * Querying is performed at best effort: the component SHALL query all supported parameters and
     * skip unsupported ones, or heap allocated parameters that could not be allocated. Any errors
     * are communicated in the return value. Additionally, preallocated (e.g. stack) parameters that
     * could not be queried are invalidated. Parameters to be allocated on the heap are omitted from
     * the result.
     *
     * \note Parameter values do not depend on the order of query.
     *
     * \todo This method cannot be used to query info-buffers. Is that a problem?
     *
     * This method MUST be supported in any state. This call does not change the state nor the
     * internal states of the component.
     *
     * This method MUST be "non-blocking" and return within 1ms.
     *
     * \param[in,out] stackParams   a list of params queried. These are initialized specific to each
     *                      setting; e.g. size and index are set and rest of the members are
     *                      cleared.
     *                      \note Flexible settings that are of incorrect size will be invalidated.
     * \param[in] heapParamIndices a vector of param indices for params to be queried and returned on the
     *                      heap. These parameters will be returned in heapParams. Unsupported param
     *                      indices will be ignored.
     * \param[out] heapParams    a list of params where to which the supported heap parameters will be
     *                      appended in the order they appear in heapParamIndices.
     *
     * \retval C2_OK        all parameters could be queried
     * \retval C2_BAD_INDEX all supported parameters could be queried, but some parameters were not
     *                      supported
     * \retval C2_NO_MEMORY could not allocate memory for a supported parameter
     * \retval C2_CORRUPTED some unknown error prevented the querying of the parameters
     *                      (unexpected)
     */
    virtual status_t query_nb(
        const std::vector<C2Param* const> &stackParams,
        const std::vector<C2Param::Index> &heapParamIndices,
        std::vector<std::unique_ptr<C2Param>>* const heapParams) const = 0;

    /**
     * Sets a set of parameters for the component or interface object.
     * Tuning is performed at best effort: the component SHALL update all supported configuration at
     * best effort (unless configured otherwise) and skip unsupported ones. Any errors are
     * communicated in the return value and in |failures|.
     *
     * \note Parameter tuning DOES depend on the order of the tuning parameters. E.g. some parameter
     * update may allow some subsequent parameter update.
     *
     * This method MUST be supported in any state.
     *
     * This method MUST be "non-blocking" and return within 1ms.
     *
     * \param[in,out] params          a list of parameter updates. These will be updated to the actual
     *                      parameter values after the updates (this is because tuning is performed
     *                      at best effort).
     *                      \todo params that could not be updated are not marked here, so are
     *                      confusing - are they "existing" values or intended to be configured
     *                      values?
     * \param[out] failures        a list of parameter failures
     *
     * \retval C2_OK        all parameters could be updated successfully
     * \retval C2_BAD_INDEX all supported parameters could be updated successfully, but some
     *                      parameters were not supported
     * \retval C2_BAD_VALUE some supported parameters could not be updated successfully because
     *                      they contained unsupported values. These are returned in |failures|.
     * \retval C2_NO_MEMORY some supported parameters could not be updated successfully because
     *                      they contained unsupported values, but could not allocate a failure
     *                      object for them.
     * \retval C2_CORRUPTED some unknown error prevented the update of the parameters
     *                      (unexpected)
     */
    virtual status_t config_nb(
            const std::vector<C2Param* const> &params,
            std::vector<std::unique_ptr<C2SettingResult>>* const failures) = 0;

    /**
     * Atomically sets a set of parameters for the component or interface object.
     *
     * \note This method is used mainly for reserving resources for a component.
     *
     * The component SHALL update all supported configuration at
     * best effort(TBD) (unless configured otherwise) and skip unsupported ones. Any errors are
     * communicated in the return value and in |failures|.
     *
     * \note Parameter tuning DOES depend on the order of the tuning parameters. E.g. some parameter
     * update may allow some subsequent parameter update.
     *
     * This method MUST be supported in any state.
     *
     * This method may be momentarily blocking, but MUST return within 5ms.
     *
     * \param params[in,out]          a list of parameter updates. These will be updated to the actual
     *                      parameter values after the updates (this is because tuning is performed
     *                      at best effort).
     *                      \todo params that could not be updated are not marked here, so are
     *                      confusing - are they "existing" values or intended to be configured
     *                      values?
     * \param failures[out]        a list of parameter failures
     *
     * \retval C2_OK        all parameters could be updated successfully
     * \retval C2_BAD_INDEX all supported parameters could be updated successfully, but some
     *                      parameters were not supported
     * \retval C2_BAD_VALUE some supported parameters could not be updated successfully because
     *                      they contained unsupported values. These are returned in |failures|.
     * \retval C2_NO_MEMORY some supported parameters could not be updated successfully because
     *                      they contained unsupported values, but could not allocate a failure
     *                      object for them.
     * \retval C2_CORRUPTED some unknown error prevented the update of the parameters
     *                      (unexpected)
     */
    virtual status_t commit_sm(
            const std::vector<C2Param* const> &params,
            std::vector<std::unique_ptr<C2SettingResult>>* const failures) = 0;

    // TUNNELING
    // =============================================================================================

    /**
     * Creates a tunnel from this component to the target component.
     *
     * If the component is successfully created, subsequent work items queued may include a
     * tunneled path between these components.
     *
     * This method MUST be supported in any state.
     *
     * This method may be momentarily blocking, but MUST return within 5ms.
     *
     * \retval C2_OK        the tunnel was successfully created
     * \retval C2_BAD_INDEX the target component does not exist
     * \retval C2_ALREADY_EXIST the tunnel already exists
     * \retval C2_UNSUPPORTED  the tunnel is not supported
     *
     * \retval C2_TIMED_OUT could not create the tunnel within the time limit (unexpected)
     * \retval C2_CORRUPTED some unknown error prevented the creation of the tunnel (unexpected)
     */
    virtual status_t createTunnel_sm(node_id targetComponent) = 0;

    /**
     * Releases a tunnel from this component to the target component.
     *
     * The release of a tunnel is delayed while there are pending work items for the tunnel.
     * After releasing a tunnel, subsequent work items queued MUST NOT include a tunneled
     * path between these components.
     *
     * This method MUST be supported in any state.
     *
     * This method may be momentarily blocking, but MUST return within 5ms.
     *
     * \retval C2_OK        the tunnel was marked for release successfully
     * \retval C2_BAD_INDEX the target component does not exist
     * \retval C2_NOT_FOUND the tunnel does not exist
     *
     * \retval C2_TIMED_OUT could not mark the tunnel for release within the time limit (unexpected)
     * \retval C2_CORRUPTED some unknown error prevented the release of the tunnel (unexpected)
     */
    virtual status_t releaseTunnel_sm(node_id targetComponent) = 0;


    // REFLECTION MECHANISM (USED FOR EXTENSION)
    // =============================================================================================

    /**
     * Returns the parameter reflector.
     *
     * This is used to describe parameter fields.
     *
     * \return a shared parameter reflector object.
     */
    virtual std::shared_ptr<C2ParamReflector> getParamReflector() const = 0;

    /**
     * Returns the set of supported parameters.
     *
     * \param[out] params a vector of supported parameters will be appended to this vector.
     *
     * \retval C2_OK        the operation completed successfully.
     * \retval C2_NO_MEMORY not enough memory to complete this method.
     */
    virtual status_t getSupportedParams(
            std::vector<std::shared_ptr<C2ParamDescriptor>> * const params) const = 0;

    /**
     *
     * \todo should this take a list considering that setting some fields may further limit other
     * fields in the same list?
     */
    virtual status_t getSupportedValues(
            const std::vector<const C2ParamField> fields,
            std::vector<C2FieldSupportedValues>* const values) const = 0;

    virtual ~C2ComponentInterface() = default;
};

class C2Component {
public:
    // METHODS AVAILABLE WHEN RUNNING
    // =============================================================================================

    /**
     * Queues up work for the component.
     *
     * This method MUST be supported in running (including tripped) states.
     *
     * This method MUST be "non-blocking" and return within 1ms
     *
     * It is acceptable for this method to return OK and return an error value using the
     * onWorkDone() callback.
     *
     * \retval C2_OK        the work was successfully queued
     * \retval C2_BAD_INDEX some component(s) in the work do(es) not exist
     * \retval C2_UNSUPPORTED  the components are not tunneled
     *
     * \retval C2_NO_MEMORY not enough memory to queue the work
     * \retval C2_CORRUPTED some unknown error prevented queuing the work (unexpected)
     */
    virtual status_t queue_nb(std::list<std::unique_ptr<C2Work>>* const items) = 0;

    /**
     * Announces a work to be queued later for the component. This reserves a slot for the queue
     * to ensure correct work ordering even if the work is queued later.
     *
     * This method MUST be supported in running (including tripped) states.
     *
     * This method MUST be "non-blocking" and return within 1 ms
     *
     * \retval C2_OK        the work announcement has been successfully recorded
     * \retval C2_BAD_INDEX some component(s) in the work outline do(es) not exist
     * \retval C2_UNSUPPORTED  the componentes are not tunneled
     *
     * \retval C2_NO_MEMORY not enough memory to record the work announcement
     * \retval C2_CORRUPTED some unknown error prevented recording the announcement (unexpected)
     *
     * \todo Can this be rolled into queue_nb?
     */
    virtual status_t announce_nb(const std::vector<C2WorkOutline> &items) = 0;

    /**
     * Discards and abandons any pending work for the component, and optionally any component
     * downstream.
     *
     * \todo define this: we could flush all work before last item queued for component across all
     *                    components linked to this; flush only work items that are queued to this
     *                    component
     * \todo return work # of last flushed item; or all flushed (but not returned items)
     * \todo we could make flush take a work item and flush all work before/after that item to allow
     *       TBD (slicing/seek?)
     * \todo we could simply take a list of numbers and flush those... this is bad for decoders
     *       also, what would happen to fine grade references?
     *
     * This method MUST be supported in running (including tripped) states.
     *
     * This method may be momentarily blocking, but must return within 5ms.
     *
     * Work that could be immediately abandoned/discarded SHALL be returned in |flushedWork|; this
     * can be done in an arbitrary order.
     *
     * Work that could not be abandoned or discarded immediately SHALL be marked to be
     * discarded at the earliest opportunity, and SHALL be returned via the onWorkDone() callback.
     *
     * \param flushThrough    flush work from this component and all components connected downstream
     *                      from it via tunneling.
     *
     * \retval C2_OK        the work announcement has been successfully recorded
     * \retval C2_TIMED_OUT the flush could not be completed within the time limit (unexpected)
     * \retval C2_CORRUPTED some unknown error prevented flushing from completion (unexpected)
     */
    virtual status_t flush_sm(bool flushThrough, std::list<std::unique_ptr<C2Work>>* const flushedWork) = 0;

    /**
     * Drains the component, and optionally downstream components
     *
     * \todo define this; we could place EOS to all upstream components, just this component, or
     *       all upstream and downstream component.
     * \todo should EOS carry over to downstream components?
     *
     * Marks last work item as "end-of-stream", so component is notified not to wait for further
     * work before it processes work already queued. This method is called to set the end-of-stream
     * flag after work has been queued. Client can continue to queue further work immediately after
     * this method returns.
     *
     * This method MUST be supported in running (including tripped) states.
     *
     * This method MUST be "non-blocking" and return within 1ms.
     *
     * Work that is completed SHALL be returned via the onWorkDone() callback.
     *
     * \param drainThrough    marks the last work item with a persistent "end-of-stream" marker that
     *                      will drain downstream components.
     *
     * \todo this may confuse work-ordering downstream; could be an mode enum
     *
     * \retval C2_OK        the work announcement has been successfully recorded
     * \retval C2_TIMED_OUT the flush could not be completed within the time limit (unexpected)
     * \retval C2_CORRUPTED some unknown error prevented flushing from completion (unexpected)
     */
    virtual status_t drain_nb(bool drainThrough) = 0;

    // STATE CHANGE METHODS
    // =============================================================================================

    /**
     * Starts the component.
     *
     * This method MUST be supported in stopped state.
     *
     * \todo This method MUST return within 500ms. Seems this should be able to return quickly, as
     * there are no immediate guarantees. Though there are guarantees for responsiveness immediately
     * after start returns.
     *
     * \todo Could we just start a ComponentInterface to get a Component?
     *
     * \retval C2_OK        the work announcement has been successfully recorded
     * \retval C2_NO_MEMORY not enough memory to start the component
     * \retval C2_TIMED_OUT the component could not be started within the time limit (unexpected)
     * \retval C2_CORRUPTED some unknown error prevented starting the component (unexpected)
     */
    virtual status_t start() = 0;

    /**
     * Stops the component.
     *
     * This method MUST be supported in running (including tripped) state.
     *
     * This method MUST return withing 500ms.
     *
     * Upon this call, all pending work SHALL be abandoned.
     *
     * \todo should this return completed work, since client will just free it? Perhaps just to
     * verify accounting.
     *
     * This does not alter any settings and tunings that may have resulted in a tripped state.
     * (Is this material given the definition? Perhaps in case we want to start again.)
     */
    virtual status_t stop() = 0;

    /**
     * Resets the component.
     *
     * This method MUST be supported in running (including tripped) state.
     *
     * This method MUST be supported during any other call (\todo or just blocking ones?)
     *
     * This method MUST return withing 500ms.
     *
     * After this call returns all work is/must be abandoned, all references should be released.
     *
     * \todo should this return completed work, since client will just free it? Also, if it unblocks
     * a stop, where should completed work be returned?
     *
     * This brings settings back to their default - "guaranteeing" no tripped space.
     *
     * \todo reclaim support - it seems that since ownership is passed, this will allow reclaiming stuff.
     */
    virtual void reset() = 0;

    /**
     * Releases the component.
     *
     * This method MUST be supported in any state. (\todo Or shall we force reset() first to bring
     * to a known state?)
     *
     * This method MUST return withing 500ms.
     *
     * \todo should this return completed work, since client will just free it? Also, if it unblocks
     * a stop, where should completed work be returned?
     *
     * TODO: does it matter if this call has a short time limit? Yes, as upon return all references
     * shall be abandoned.
     */
    virtual void release() = 0;

    /**
     * Returns the interface for this component.
     *
     * \return the component interface
     */
    virtual std::shared_ptr<C2ComponentInterface> intf() = 0;

protected:
    virtual ~C2Component() = default;
};

class C2FrameInfoParser {
public:
    /**
     * \return the content type supported by this info parser.
     *
     * \todo this may be redundant
     */
    virtual C2StringLiteral getType() const = 0;

    /**
     * \return a vector of supported parameter indices parsed by this info parser.
     *
     * \todo sticky vs. non-sticky params? this may be communicated by param-reflector.
     */
    virtual const std::vector<C2Param::Index> getParsedParams() const = 0;

    /**
     * Resets this info parser. This brings this parser to its initial state after creation.
     *
     * This method SHALL return within 5ms.
     *
     * \retval C2_OK        the info parser was reset
     * \retval C2_TIMED_OUT could not reset the parser within the time limit (unexpected)
     * \retval C2_CORRUPTED some unknown error prevented the resetting of the parser (unexpected)
     */
    virtual status_t reset() { return C2_OK; }

    virtual status_t parseFrame(C2BufferPack &frame);

    virtual ~C2FrameInfoParser() = default;
};

struct C2ComponentInfo {
    // TBD

};

class C2AllocatorStore {
public:
    // TBD

    enum Type {
        LINEAR,     ///< basic linear allocator type
        GRALLOC,    ///< basic gralloc allocator type
    };

    /**
     * Creates an allocator.
     *
     * \param type      the type of allocator to create
     * \param allocator shared pointer where the created allocator is stored. Cleared on failure
     *                  and updated on success.
     *
     * \retval C2_OK        the allocator was created successfully
     * \retval C2_TIMED_OUT could not create the allocator within the time limit (unexpected)
     * \retval C2_CORRUPTED some unknown error prevented the creation of the allocator (unexpected)
     *
     * \retval C2_NOT_FOUND no such allocator
     * \retval C2_NO_MEMORY not enough memory to create the allocator
     */
    virtual status_t createAllocator(Type type, std::shared_ptr<C2Allocator>* const allocator) = 0;

    virtual ~C2AllocatorStore() = default;
};

class C2ComponentStore {
    /**
     * Creates a component.
     *
     * This method SHALL return within 100ms.
     *
     * \param name          name of the component to create
     * \param component     shared pointer where the created component is stored. Cleared on
     *                      failure and updated on success.
     *
     * \retval C2_OK        the component was created successfully
     * \retval C2_TIMED_OUT could not create the component within the time limit (unexpected)
     * \retval C2_CORRUPTED some unknown error prevented the creation of the component (unexpected)
     *
     * \retval C2_NOT_FOUND no such component
     * \retval C2_NO_MEMORY not enough memory to create the component
     */
    virtual status_t createComponent(C2String name, std::shared_ptr<C2Component>* const component);

    /**
     * Creates a component interface.
     *
     * This method SHALL return within 100ms.
     *
     * \param name          name of the component interface to create
     * \param interface     shared pointer where the created interface is stored
     *
     * \retval C2_OK        the component interface was created successfully
     * \retval C2_TIMED_OUT could not create the component interface within the time limit
     *                      (unexpected)
     * \retval C2_CORRUPTED some unknown error prevented the creation of the component interface
     *                      (unexpected)
     *
     * \retval C2_NOT_FOUND no such component interface
     * \retval C2_NO_MEMORY not enough memory to create the component interface
     *
     * \todo Do we need an interface, or could this just be a component that is never started?
     */
    virtual status_t createInterface(C2String name, std::shared_ptr<C2ComponentInterface>* const interface);

    /**
     * Returns the list of components supported by this component store.
     *
     * This method SHALL return within 1ms.
     *
     * \retval vector of component information.
     */
    virtual std::vector<std::unique_ptr<const C2ComponentInfo>> getComponents();

    // -------------------------------------- UTILITY METHODS --------------------------------------

    // on-demand buffer layout conversion (swizzling)
    virtual status_t copyBuffer(std::shared_ptr<C2GraphicBuffer> src, std::shared_ptr<C2GraphicBuffer> dst);

    // status_t selectPreferredColor(formats<A>, formats<B>);

    // GLOBAL SETTINGS
    // system-wide stride & slice-height (???)

    /**
     * Queries a set of system-wide parameters.
     * Querying is performed at best effort: the store SHALL query all supported parameters and
     * skip unsupported ones, or heap allocated parameters that could not be allocated. Any errors
     * are communicated in the return value. Additionally, preallocated (e.g. stack) parameters that
     * could not be queried are invalidated. Parameters to be allocated on the heap are omitted from
     * the result.
     *
     * \note Parameter values do not depend on the order of query.
     *
     * This method MUST be "non-blocking" and return within 1ms.
     *
     * \param stackParams     a list of params queried. These are initialized specific to each
     *                      setting; e.g. size and index are set and rest of the members are
     *                      cleared.
     *                      NOTE: Flexible settings that are of incorrect size will be invalidated.
     * \param heapParamIndices a vector of param indices for params to be queried and returned on the
     *                      heap. These parameters will be returned in heapParams. Unsupported param
     *                      indices will be ignored.
     * \param heapParams      a list of params where to which the supported heap parameters will be
     *                      appended in the order they appear in heapParamIndices.
     *
     * \retval C2_OK        all parameters could be queried
     * \retval C2_BAD_INDEX all supported parameters could be queried, but some parameters were not
     *                      supported
     * \retval C2_NO_MEMORY could not allocate memory for a supported parameter
     * \retval C2_CORRUPTED some unknown error prevented the querying of the parameters
     *                      (unexpected)
     */
    virtual status_t query_nb(
        const std::vector<C2Param* const> &stackParams,
        const std::vector<C2Param::Index> &heapParamIndices,
        std::vector<std::unique_ptr<C2Param>>* const heapParams) = 0;

    /**
     * Sets a set of system-wide parameters.
     *
     * \note There are no settable system-wide parameters defined thus far, but may be added in the
     * future.
     *
     * Tuning is performed at best effort: the store SHALL update all supported configuration at
     * best effort (unless configured otherwise) and skip unsupported ones. Any errors are
     * communicated in the return value and in |failures|.
     *
     * \note Parameter tuning DOES depend on the order of the tuning parameters. E.g. some parameter
     * update may allow some subsequent parameter update.
     *
     * This method MUST be "non-blocking" and return within 1ms.
     *
     * \param params          a list of parameter updates. These will be updated to the actual
     *                      parameter values after the updates (this is because tuning is performed
     *                      at best effort).
     *                      \todo params that could not be updated are not marked here, so are
     *                      confusing - are they "existing" values or intended to be configured
     *                      values?
     * \param failures        a list of parameter failures
     *
     * \retval C2_OK        all parameters could be updated successfully
     * \retval C2_BAD_INDEX all supported parameters could be updated successfully, but some
     *                      parameters were not supported
     * \retval C2_BAD_VALUE some supported parameters could not be updated successfully because
     *                      they contained unsupported values. These are returned in |failures|.
     * \retval C2_NO_MEMORY some supported parameters could not be updated successfully because
     *                      they contained unsupported values, but could not allocate a failure
     *                      object for them.
     * \retval C2_CORRUPTED some unknown error prevented the update of the parameters
     *                      (unexpected)
     */
    virtual status_t config_nb(
            const std::vector<C2Param* const> &params,
            std::list<std::unique_ptr<C2SettingResult>>* const failures) = 0;

    virtual ~C2ComponentStore() = default;
};

// ================================================================================================

/// @}

}  // namespace android

#endif  // C2COMPONENT_H_
