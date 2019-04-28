/* 
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved. 
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
*/

#ifndef __MIX_AUDIO_H__
#define __MIX_AUDIO_H__

#include <glib-object.h>
#include "mixacp.h"
#include "mixaip.h"
#include "mixdrmparams.h"
#include "mixresult.h"
#include "mixaudiotypes.h"

/*
 * Type macros.
 */
#define MIX_TYPE_AUDIO                  (mix_audio_get_type ())
#define MIX_AUDIO(obj)                  (G_TYPE_CHECK_INSTANCE_CAST ((obj), MIX_TYPE_AUDIO, MixAudio))
#define MIX_IS_AUDIO(obj)               (G_TYPE_CHECK_INSTANCE_TYPE ((obj), MIX_TYPE_AUDIO))
#define MIX_AUDIO_CLASS(klass)          (G_TYPE_CHECK_CLASS_CAST ((klass), MIX_TYPE_AUDIO, MixAudioClass))
#define MIX_IS_AUDIO_CLASS(klass)       (G_TYPE_CHECK_CLASS_TYPE ((klass), MIX_TYPE_AUDIO))
#define MIX_AUDIO_GET_CLASS(obj)        (G_TYPE_INSTANCE_GET_CLASS ((obj), MIX_TYPE_AUDIO, MixAudioClass))

typedef struct _MixAudio        MixAudio;
typedef struct _MixAudioClass   MixAudioClass;

/**
 * MixStreamState:
 * @MIX_STREAM_NULL: Stream is not allocated.
 * @MIX_STREAM_STOPPED: Stream is at STOP state. This is the only state DNR is allowed.
 * @MIX_STREAM_PLAYING: Stream is at Playing state.
 * @MIX_STREAM_PAUSED: Stream is Paused.
 * @MIX_STREAM_DRAINING: Stream is draining -- remaining of the buffer in the device are playing. This state is special due to the limitation that no other control operations are allowed at this state. Stream will become @MIX_STREAM_STOPPED automatically when this data draining has completed.
 * @MIX_STREAM_LAST: Last index in the enumeration.
 *
 * Stream State during Decode and Render or Encode mode. These states do not apply to Decode and Return mode.
 */
typedef enum {
  MIX_STREAM_NULL=0,
  MIX_STREAM_STOPPED,
  MIX_STREAM_PLAYING,
  MIX_STREAM_PAUSED,
  MIX_STREAM_DRAINING,
  MIX_STREAM_LAST
} MixStreamState;

/**
 * MixState:
 * @MIX_STATE_UNINITIALIZED: MIX is not initialized.
 * @MIX_STATE_INITIALIZED: MIX is initialized.
 * @MIX_STATE_CONFIGURED: MIX is configured successfully.
 * @MIX_STATE_LAST: Last index in the enumeration.
 * 
 * The varies states the device is in.
 */
typedef enum {
  MIX_STATE_NULL=0,
  MIX_STATE_UNINITIALIZED,
  MIX_STATE_INITIALIZED,
  MIX_STATE_CONFIGURED,
  MIX_STATE_LAST
} MixState;

/**
 * MixCodecMode:
 * @MIX_CODING_INVALID: Indicates device uninitialied for any mode.
 * @MIX_CODING_ENCODE: Indicates device is opened for encoding.
 * @MIX_CODING_DECODE: Indicates device is opened for decoding.
 * @MIX_CODING_LAST: Last index in the enumeration.
 * 
 * Mode where device is operating on. See mix_audio_initialize().
 */
typedef enum {
  MIX_CODING_INVALID=0,
  MIX_CODING_ENCODE,
  MIX_CODING_DECODE,
  MIX_CODING_LAST
} MixCodecMode;

/**
 * MixVolType:
 * @MIX_VOL_PERCENT: volume is expressed in percentage.
 * @MIX_VOL_DECIBELS: volume is expressed in decibel.
 * @MIX_VOL_LAST: last entry.
 * 
 * See mix_audio_getvolume() and mix_audio_setvolume().
 */
typedef enum {
  MIX_VOL_PERCENT=0,
  MIX_VOL_DECIBELS,
  MIX_VOL_LAST
} MixVolType;

/**
 * MixVolRamp:
 * @MIX_RAMP_LINEAR: volume is expressed in percentage.
 * @MIX_RAMP_EXPONENTIAL: volume is expressed in decibel.
 * @MIX_RAMP_LAST: last entry.
 * 
 * See mix_audio_getvolume() and mix_audio_setvolume().
 */
typedef enum 
{
  MIX_RAMP_LINEAR = 0,
  MIX_RAMP_EXPONENTIAL,
  MIX_RAMP_LAST
} MixVolRamp;

/**
 * MixIOVec:
 * @data: data pointer
 * @size: size of buffer in @data
 * 
 * Scatter-gather style structure. To be used by mix_audio_decode() method for input and output buffer.
 */
typedef struct {
  guchar *data;
  gint size;
} MixIOVec;

/**
 * MixDeviceState:
 * @MIX_AUDIO_DEV_CLOSED: TBD
 * @MIX_AUDIO_DEV_OPENED: TBD
 * @MIX_AUDIO_DEV_ALLOCATED: TBD
 * 
 * Device state.
 */
typedef enum {
  MIX_AUDIO_DEV_CLOSED=0,
  MIX_AUDIO_DEV_OPENED,
  MIX_AUDIO_DEV_ALLOCATED
} MixDeviceState;

/**
 * MixAudioClass:
 * @parent_class: Parent class;
 * 
 * MI-X Audio object class
 */
struct _MixAudioClass
{
  /*< public >*/
  GObjectClass parent_class;

  /*< virtual public >*/
  MIX_RESULT (*initialize) (MixAudio *mix, MixCodecMode mode, MixAudioInitParams *aip, MixDrmParams *drminitparams);
  MIX_RESULT (*configure) (MixAudio *mix, MixAudioConfigParams *audioconfigparams, MixDrmParams *drmparams);
  MIX_RESULT (*decode) (MixAudio *mix, const MixIOVec *iovin, gint iovincnt, guint64 *insize, MixIOVec *iovout, gint iovoutcnt, guint64 *outsize);
  MIX_RESULT (*capture_encode) (MixAudio *mix, MixIOVec *iovout, gint iovoutcnt);
  MIX_RESULT (*start) (MixAudio *mix);
  MIX_RESULT (*stop_drop) (MixAudio *mix);
  MIX_RESULT (*stop_drain) (MixAudio *mix);
  MIX_RESULT (*pause) (MixAudio *mix);
  MIX_RESULT (*resume) (MixAudio *mix);
  MIX_RESULT (*get_timestamp) (MixAudio *mix, guint64 *msecs);
  MIX_RESULT (*set_mute) (MixAudio *mix, gboolean mute);
  MIX_RESULT (*get_mute) (MixAudio *mix, gboolean* muted);
  MIX_RESULT (*get_max_vol) (MixAudio *mix, gint *maxvol);
  MIX_RESULT (*get_min_vol) (MixAudio *mix, gint *minvol);
  MIX_RESULT (*get_volume) (MixAudio *mix, gint *currvol, MixVolType type);
  MIX_RESULT (*set_volume) (MixAudio *mix, gint currvol, MixVolType type, gulong msecs, MixVolRamp ramptype);
  MIX_RESULT (*deinitialize) (MixAudio *mix);
  MIX_RESULT (*get_stream_state) (MixAudio *mix, MixStreamState *streamState);
  MIX_RESULT (*get_state) (MixAudio *mix, MixState *state);
  MIX_RESULT (*is_am_available) (MixAudio *mix, MixAudioManager am, gboolean *avail);
  MIX_RESULT (*get_output_configuration) (MixAudio *mix, MixAudioConfigParams **audioconfigparams);
};

/**
 * MixAudio:
 * @parent: Parent object.
 * @streamState: Current state of the stream
 * @decodeMode: Current decode mode of the device. This value is valid only when @codingMode equals #MIX_CODING_DECODE.
 * @fileDescriptor: File Descriptor to the opened device.
 * @state: State of the current #MixAudio session.
 * @codecMode: Current codec mode of the session.
 * @useIAM: Is current stream configured to use Intel Audio Manager.
 * @encoding: <emphasis>Not Used.</emphasis>
 *
 * MI-X Audio object
 */
struct _MixAudio
{
  /*< public >*/
  GObject parent;

  /*< public >*/

  /*< private >*/
  MixStreamState streamState;
  gchar *encoding;
  MixState state;
  MixCodecMode codecMode;
  gboolean useIAM;
  int fileDescriptor;
  gint streamID;
  guint32 amStreamID;
  GStaticRecMutex streamlock; // lock that must be acquired to invoke stream method.
  GStaticRecMutex controllock; // lock that must be acquired to call control function.
  MixAudioConfigParams *audioconfigparams;
  gboolean am_registered;
  MixDeviceState deviceState;

  guint64 ts_last;
  guint64 ts_elapsed;
  guint64 bytes_written;
};

/**
 * mix_audio_get_type:
 * @returns: type
 * 
 * Get the type of object.
 */
GType mix_audio_get_type (void);

/**
 * mix_audio_new:
 * @returns: A newly allocated instance of #MixAudio
 * 
 * Use this method to create new instance of #MixAudio
 */
MixAudio *mix_audio_new(void);

/**
 * mix_audio_ref:
 * @mix: object to add reference
 * @returns: the MixAudio instance where reference count has been increased.
 * 
 * Add reference count.
 */
MixAudio *mix_audio_ref(MixAudio *mix);

/**
 * mix_audio_unref:
 * @obj: object to unref.
 * 
 * Decrement reference count of the object.
 */
#define mix_audio_unref(obj) g_object_unref (G_OBJECT(obj))

/* Class Methods */

/**
 * mix_audio_get_version:
 * @returns: #MIX_RESULT_SUCCESS 
 * 
 * Returns the version of the MI-X library.
 * 
 */
MIX_RESULT mix_audio_get_version(guint* major, guint *minor);

/**
 * mix_audio_initialize:
 * @mix: #MixAudio object.
 * @mode: Requested #MixCodecMode.
 * @aip: Audio initialization parameters.
 * @drminitparams: <emphasis>Optional.</emphasis> DRM initialization param if applicable.
 * @returns: #MIX_RESULT_SUCCESS on successful initilaization. #MIX_RESULT_ALREADY_INIT if session is already initialized.
 * 
 * This function will initialize an encode or decode session with this #MixAudio instance.  During this call, the device will be opened. If the device is not available, an error is returned to the caller so that an alternative (e.g. software decoding) can be configured instead. Use mix_audio_deinitialize() to close the device.
 * 
 * A previous initialized session must be de-initialized using mix_audio_deinitialize() before it can be initialized again.
 */
MIX_RESULT mix_audio_initialize(MixAudio *mix, MixCodecMode mode, MixAudioInitParams *aip, MixDrmParams *drminitparams);

/**
 * mix_audio_configure:
 * @mix: #MixAudio object.
 * @audioconfigparams: a #MixAudioConfigParams derived object containing information for the specific stream type.
 * @drmparams: <emphasis>Optional.</emphasis> DRM initialization param if applicable.
 * @returns: Result indicates successful or not.
 * 
 * This function can be used to configure a stream for the current session.  The caller can use this function to do the following:
 * 
 * <itemizedlist>
 * <listitem>Choose decoding mode (direct-render or decode-return)</listitem>
 * <listitem>Provide DRM parameters (using DRMparams object)</listitem>
 * <listitem>Provide stream parameters (using STRMparams objects)</listitem>
 * <listitem>Provide a stream name for the Intel Smart Sound Technology stream</listitem>
 * </itemizedlist>
 * 
 * SST stream parameters will be set during this call, and stream resources allocated in SST.
 *
 * <note>
 * <title>Intel Audio Manager support:</title>
 * <para>If Intel Audio Manager support is enabled, and if @mode is specified to #MIX_DECODE_DIRECTRENDER, the SST stream will be registered with Intel Audio Manager in the context of this call, using the stream name provided in @streamname. Application will receive a notification from Intel Audio Manager that the stream has been created during or soon after this call. The application should be ready to handle either possibility.  A stream ID (associated with the stream name) will be provided by Intel Audio Manager which will be used for subsequent notifications from Intel Audio Manager or calls to Intel Audio Manager for muting, pause and resume. See mix_audio_getstreamid()</para>
 * <para>If a stream is already registered with Intel Audio Manager, application must pass the same @streamname argument to retain the session. Otherwise, the existing stream will be unregistered and a new stream will be registered with the new @streamname.
 * </para>
 * </note>
 * 
 * If @mode is specified to #MIX_DECODE_DIRECTRENDER but direct-render mode is not available (due to end user use of alternative output device), an error indication will be returned to the caller so that an alternate pipeline configuration can be created (e.g. including a Pulse Audio sink, and support for output buffers).  In this case, the caller will need to call mix_audio_configure() again to with @mode specify as #MIX_DECODE_DECODERETURN to request decode-return mode.
 * 
 * This method can be called multiple times if reconfiguration of the stream is needed. However, this method must be called when the stream is in #MIX_STREAM_STOPPED state.
 * 
 */
MIX_RESULT mix_audio_configure(MixAudio *mix, MixAudioConfigParams *audioconfigparams, MixDrmParams *drmparams);

/**
 * mix_audio_decode:
 * @mix: #MixAudio object.
 * @iovin: a pointer to an array of #MixIOVec structure that contains the input buffers
 * @iovincnt: the number of entry in the @iovin array
 * @iovout: a pointer to an arrya of #MixIOVec structure that represent the output buffer. During input, each size in the #MixIOVec array represents the available buffer size pointed to by data. Upon return, each size value will be updated to reflect how much data has been filled. This parameter is ignored if stream is configured to #MIX_DECODE_DIRECTRENDER. See mix_audio_configure() for more detail.
 * @iovoutcnt: in/out parameter which when input, it contains the number of entry available in the @iovout array. Upon return, this value will be updated to reflect how many entry in the @iovout array has been populated with data. This parameter is ignored if stream is configured to #MIX_DECODE_DIRECTRENDER. See mix_audio_configure() for more detail.
 * @outsize: Total number of bytes returned for the decode session. This parameter is ignored if stream is configured to #MIX_DECODE_DIRECTRENDER.
 * @returns: #MIX_RESULT
 * 
 * This function is used to initiate HW accelerated decoding of encoded data buffers.  This function may be used in two major modes, direct-render or decode-return.  
 *
 * With direct-render, input buffers are provided by the caller which hold encoded audio data, and no output buffers are provided.  The encoded data is decoded, and the decoded data is sent directly to the output speaker.  This allows very low power audio rendering and is the best choice of operation for longer battery life.
 *
 * <note>
 * <title>Intel Audio Manager Support</title>
 * However, if the user has connected a different target output device, such as Bluetooth headphones, this mode cannot be used as the decoded audio must be directed to the Pulse Audio stack where the output to Bluetooth device can be supported, per Intel Audio Manager guidelines.  This mode is called decode-return, and requires the caller to provide output buffers for the decoded data.
 * </note>
 * 
 * Input buffers in both modes are one or more user space buffers using a scatter/gather style vector interface.
 *
 * Output buffers for the decode-return mode are one or more user space buffers in a scatter style vector interface.  Buffers will be filled in order and lengths of data filled will be returned.
 *
 * This call will block until data has been completely copied or queued to the driver.  All user space buffers may be used or released when this call returns.
 * 
 * Note: If the stream is configured as #MIX_DECODE_DIRECTRENDER, and whenever the stream in #MIX_STREAM_STOPPED state, the call to mix_audio_decode() will not start the playback until mix_audio_start() is called. This behavior would allow application to queue up data but delay the playback until appropriate time.
 * 
 */
MIX_RESULT mix_audio_decode(MixAudio *mix, const MixIOVec *iovin, gint iovincnt, guint64 *insize, MixIOVec *iovout, gint iovoutcnt, guint64 *outsize);

/**
 * mix_audio_capture_encode:
 * @mix: #MixAudio object.
 * @iovout: Capture audio samples.
 * @iovoutcnt: Number of entry in the input vector @iovout.
 * @returns: #MIX_RESULT
 * 
 * To read encoded data from device.
 * 
 * <comment>
 * NOTE: May need to rename to "read_encoded" or other name. Since "encode" seems to mean taking raw audio and convert to compressed audio.
 * </comment>
 */
MIX_RESULT mix_audio_capture_encode(MixAudio *mix, MixIOVec *iovout, gint iovoutcnt);

/**
 * mix_audio_start:
 * @mix: #MixAudio object.
 * @returns: #MIX_RESULT_SUCCESS if the resulting state is either #MIX_STREAM_PLAYING or #MIX_STREAM_PAUSED. Fail code otherwise.
 * 
 * If the stream is configured to #MIX_DECODE_DIRECTRENDER, application use this call to change the stream out of the #MIX_STREAM_STOPPED state. If mix_audio_decode() is called and blocking in a seperate thread prior to this call. This method causes the device to start rendering data.
 * 
 * In #MIX_DECODE_DECODERETURN, this method is no op.
 */
MIX_RESULT mix_audio_start(MixAudio *mix);

/**
 * mix_audio_stop_drop:
 * @mix: #MixAudio object.
 * @returns: #MIX_RESULT_SUCCESS if the resulting state has successfully reached #MIX_STREAM_STOPPED. Fail code otherwise.
 * 
 * If the stream is configured to #MIX_DECODE_DIRECTRENDER, application uses this function to stop the processing and playback of audio.
 * 
 * All remaining frames to be decoded or rendered will be discarded and playback will stop immediately, unblocks any pending mix_audio_decode().
 * 
 * If #MIX_STOP_DRAIN is requested, the call will block with stream state set to #MIX_STREAM_DRAINING, and return only until all remaining frame in previously submitted buffers are decoded and rendered. When #MIX_STOP_DRAIN returns successfully, the stream would have reached #MIX_STREAM_STOPPED successfully.
 * 
 * After this call, timestamp retrived by mix_audio_gettimestamp() is reset to zero.
 * 
 * Note that this method returns #MIX_RESULT_WRONG_STATE if the stream is in #MIX_STREAM_DRAINING state.
 * 
 */
MIX_RESULT mix_audio_stop_drop(MixAudio *mix);

/**
 * mix_audio_stop_drain:
 * @mix: #MixAudio object.
 * @returns: #MIX_RESULT_SUCCESS if the resulting state has successfully reached #MIX_STREAM_STOPPED. Fail code otherwise.
 * 
 * If the stream is configured to #MIX_DECODE_DIRECTRENDER, application uses this function to stop the processing and playback of audio.
 * 
 * The call will block with stream state set to #MIX_STREAM_DRAINING, and return only until all remaining frame in previously submitted buffers are decoded and rendered.
 * 
 * Note that this method blocks until #MIX_STREAM_STOPPED is reached if it is called when the stream is already in #MIX_STREAM_DRAINING state.
 * 
 */
MIX_RESULT mix_audio_stop_drain(MixAudio *mix);

/**
 * mix_audio_pause:
 * @mix: #MixAudio object.
 * @returns: #MIX_RESULT_SUCCESS if #MIX_STREAM_PAUSED state is reached successfully. #MIX_RESULT_WRONG_STATE if operation is not allowed with the current state.
 * 
 * If the stream is configured to #MIX_DECODE_DIRECTRENDER, application uses this call to change the stream state from #MIX_STREAM_PLAYING to #MIX_STREAM_PAUSED. Note that this method returns sucessful only when the resulting state reaches #MIX_STREAM_PAUSED. Meaning it will return fail code if it is called in a state such as #MIX_STREAM_STOPPED, where transitioning to #MIX_STREAM_PAUSED is not possible.
 * 
 * In some situation, where there is potential race condition with the DRAINING operation, this method may return MIX_RESULT_NEED_RETRY to indicate last operation result is inclusive and request caller to call again.
 */
MIX_RESULT mix_audio_pause(MixAudio *mix);

/**
 * mix_audio_resume:
 * @mix: #MixAudio object.
 * @returns: #MIX_RESULT_SUCCESS if #MIX_STREAM_PLAYING state is reached successfully. #MIX_RESULT_WRONG_STATE if operation is not allowed with the current state.
 *
 * If the stream is configured to #MIX_DECODE_DIRECTRENDER, application uses this call to change the stream state to #MIX_STREAM_PLAYING. Note that this method returns sucessful only when the resulting state reaches #MIX_STREAM_PAUSED. Meaning it will return fail code if it is called in a state such as #MIX_STREAM_DRAINING, where transitioning to #MIX_STREAM_PLAYING is not possible.
 * 
 */
MIX_RESULT mix_audio_resume(MixAudio *mix);


/**
 * mix_audio_get_timestamp:
 * @mix: #MixAudio object.
 * @msecs: play time in milliseconds.
 * @returns: #MIX_RESULT_SUCCESS if the timestamp is available. #MIX_RESULT_WRONG_MODE if operation is not allowed with the current mode.
 * 
 * This function can be used to retrieve the current timestamp for audio playback in milliseconds.  The timestamp will reflect the amount of audio data rendered since the start of stream, or since the last stop.  Note that the timestamp is always reset to zero when the stream enter #MIX_STREAM_STOPPED state. The timestamp is an unsigned long value, so the value will wrap when the timestamp reaches #ULONG_MAX. This function is only valid in direct-render mode.
 */
MIX_RESULT mix_audio_get_timestamp(MixAudio *mix, guint64 *msecs);

/**
 * mix_audio_set_mute:
 * @mix: #MixAudio object.
 * @mute: Turn mute on/off.
 * @returns: #MIX_RESULT_SUCCESS on success or other fail code.
 * 
 * This function is used to mute and unmute audio playback. While muted, playback would continue but silently. This function is only valid when the session is configured to #MIX_DECODE_DIRECTRENDER mode.
 * 
 * Note that playback volumn may change due to change of global settings while stream is muted.
 */
MIX_RESULT mix_audio_set_mute(MixAudio *mix, gboolean mute);

/**
 * mix_audio_get_mute:
 * @mix: #MixAudio object.
 * @muted: current mute state.
 * @returns: #MIX_RESULT_SUCCESS on success or other fail code.
 * 
 * Get Mute.
 */
MIX_RESULT mix_audio_get_mute(MixAudio *mix, gboolean* muted);

/**
 * mix_audio_get_max_vol:
 * @mix: #MixAudio object.
 * @maxvol: pointer to receive max volumn.
 * @returns: #MIX_RESULT_SUCCESS on success or other fail code.
 * 
 * This function can be used if the application will be setting the audio volume using decibels instead of percentage.  The maximum volume in decibels supported by the driver will be returned.  This value can be used to determine the upper bound of the decibel range in calculating volume levels.  This value is a signed integer. This function is only valid if stream is configured to #MIX_DECODE_DIRECTRENDER mode.
 * 
 */
MIX_RESULT mix_audio_get_max_vol(MixAudio *mix, gint *maxvol);

/**
 * mix_audio_get_min_vol:
 * @mix: #MixAudio object.
 * @minvol: pointer to receive max volumn.
 * @returns: #MIX_RESULT_SUCCESS on success or other fail code.
 * 
 * This function can be used if the application will be setting the audio volume using decibels instead of percentage.  The minimum volume in decibels supported by the driver will be returned.  This value can be used to determine the lower bound of the decibel range in calculating volume levels.  This value is a signed integer. This function is only valid if stream is configured to #MIX_DECODE_DIRECTRENDER mode.
 * 
 */
MIX_RESULT mix_audio_get_min_vol(MixAudio *mix, gint *minvol);

/**
 * mix_audio_get_volume:
 * @mix: #MixAudio object.
 * @currvol: Current volume. Note that if @type equals #MIX_VOL_PERCENT, this value will be return within the range of 0 to 100 inclusive.
 * @type: The type represented by @currvol.
 * @returns: #MIX_RESULT_SUCCESS on success or other fail code.
 * 
 * This function returns the current volume setting in either decibels or percentage. This function is only valid if stream is configured to #MIX_DECODE_DIRECTRENDER mode.
 * 
 */
MIX_RESULT mix_audio_get_volume(MixAudio *mix, gint *currvol, MixVolType type);

/**
 * mix_audio_set_volume:
 * @mix: #MixAudio object.
 * @currvol: Current volume. Note that if @type equals #MIX_VOL_PERCENT, this value will be trucated to within the range of 0 to 100 inclusive.
 * @type: The type represented by @currvol.
 * @returns: #MIX_RESULT_SUCCESS on success or other fail code.
 * 
 * This function sets the current volume setting in either decibels or percentage.  This function is only valid if the stream is configured to #MIX_DECODE_DIRECTRENDER mode.
 * 
 */
MIX_RESULT mix_audio_set_volume(MixAudio *mix, gint currvol, MixVolType type, gulong msecs, MixVolRamp ramptype);

/**
 * mix_audio_deinitialize:
 * @mix: #MixAudio object.
 * @returns: #MIX_RESULT_SUCCESS on success or other fail code.
 * 
 * This function will uninitialize a session with this MI-X instance.  During this call, the SST device will be closed and resources including mmapped buffers would be freed.This function should be called by the application once mix_audio_init() has been called.
 * 
 * <note>
 * <title>Intel Audio Manager Support</title>
 * The SST stream would be unregistered with Intel Audio Manager if it was registered.  
 * </note>
 * 
 * Note that if this method should not fail normally. If it does return failure, the state of this object and the underlying mechanism is compromised and application should not attempt to reuse this object.
 */
MIX_RESULT mix_audio_deinitialize(MixAudio *mix);

/**
 * mix_audio_get_stream_state:
 * @mix: #MixAudio object.
 * @streamState: pointer to receive stream state.
 * @returns: #MIX_RESULT
 * 
 * Get the stream state of the current stream.
 */
MIX_RESULT mix_audio_get_stream_state(MixAudio *mix, MixStreamState *streamState);

/**
 * mix_audio_get_state:
 * @mix: #MixAudio object.
 * @state: pointer to receive state
 * @returns: Current device state.
 * 
 * Get the device state of the audio session.
 */
MIX_RESULT mix_audio_get_state(MixAudio *mix, MixState *state);

/**
 * mix_audio_am_is_enabled:
 * @mix: #MixAudio object.
 * @returns: boolean indicates if Intel Audio Manager is enabled with the current session.
 * 
 * This method checks if the current session is configure to use Intel Audio Manager. Note that Intel Audio Manager is considered disabled if the stream has not be initialized to use the service explicitly.
 */
gboolean mix_audio_am_is_enabled(MixAudio *mix);

// Real implementation for Base class
//MIX_RESULT mix_audio_get_version(guint* major, guint *minor);

/**
 * mix_audio_is_am_available:
 * @mix: TBD
 * @am: TBD
 * @avail: TBD
 * @returns: TBD
 * 
 * Check if AM is available.
 */
MIX_RESULT mix_audio_is_am_available(MixAudio *mix, MixAudioManager am, gboolean *avail);

/**
 * mix_audio_get_output_configuration:
 * @mix: #MixAudio object.
 * @audioconfigparams: double pointer to hold output configuration.
 * @returns: #MIX_RESULT_SUCCESS on success or other fail code.
 * 
 * This method retrieve the current configuration. This can be called after initialization. If a stream has been configured, it returns the corresponding derive object of MixAudioConfigParams.
 */
MIX_RESULT mix_audio_get_output_configuration(MixAudio *mix, MixAudioConfigParams **audioconfigparams);

/**
 * mix_audio_get_stream_byte_decoded:
 * @mix: #MixAudio object.
 * @msecs: stream byte decoded..
 * @returns: #MIX_RESULT_SUCCESS if the value is available. #MIX_RESULT_WRONG_MODE if operation is not allowed with the current mode.
 * 
 * Retrive the culmulative byte decoded.
 * 
 * <remark>Not Implemented.</remark>
 */
MIX_RESULT mix_audio_get_stream_byte_decoded(MixAudio *mix, guint64 *byte);

#endif /* __MIX_AUDIO_H__ */
