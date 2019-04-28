/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */

/**
 * SECTION:mixdisplayx11
 * @short_description: VideoInit parameters
 *
 * A data object which stores videoinit specific parameters.
 */

#include "mixdisplayx11.h"

#define SAFE_FREE(p) if(p) { g_free(p); p = NULL; }

static GType _mix_displayx11_type = 0;
static MixDisplayClass *parent_class = NULL;

#define _do_init { _mix_displayx11_type = g_define_type_id; }

gboolean mix_displayx11_copy(MixDisplay * target, const MixDisplay * src);
MixDisplay *mix_displayx11_dup(const MixDisplay * obj);
gboolean mix_displayx11_equal(MixDisplay * first, MixDisplay * second);
static void mix_displayx11_finalize(MixDisplay * obj);

G_DEFINE_TYPE_WITH_CODE (MixDisplayX11, mix_displayx11,
		MIX_TYPE_DISPLAY, _do_init);

static void mix_displayx11_init(MixDisplayX11 * self) {

	/* Initialize member varibles */
	self->display = NULL;
	self->drawable = 0;
}

static void mix_displayx11_class_init(MixDisplayX11Class * klass) {
	MixDisplayClass *mixdisplay_class = MIX_DISPLAY_CLASS(klass);

	/* setup static parent class */
	parent_class = (MixDisplayClass *) g_type_class_peek_parent(klass);

	mixdisplay_class->finalize = mix_displayx11_finalize;
	mixdisplay_class->copy = (MixDisplayCopyFunction) mix_displayx11_copy;
	mixdisplay_class->dup = (MixDisplayDupFunction) mix_displayx11_dup;
	mixdisplay_class->equal = (MixDisplayEqualFunction) mix_displayx11_equal;
}

MixDisplayX11 *
mix_displayx11_new(void) {
	MixDisplayX11 *ret = (MixDisplayX11 *) g_type_create_instance(
			MIX_TYPE_DISPLAYX11);

	return ret;
}

void mix_displayx11_finalize(MixDisplay * obj) {
	/* clean up here. */
	/* MixDisplayX11 *self = MIX_DISPLAYX11 (obj); */

	/* NOTE: we don't need to do anything
	 * with display and drawable */

	/* Chain up parent */
	if (parent_class->finalize)
		parent_class->finalize(obj);
}

MixDisplayX11 *
mix_displayx11_ref(MixDisplayX11 * mix) {
	return (MixDisplayX11 *) mix_display_ref(MIX_DISPLAY(mix));
}

/**
 * mix_mixdisplayx11_dup:
 * @obj: a #MixDisplayX11 object
 * @returns: a newly allocated duplicate of the object.
 *
 * Copy duplicate of the object.
 */
MixDisplay *
mix_displayx11_dup(const MixDisplay * obj) {
	MixDisplay *ret = NULL;

	if (MIX_IS_DISPLAYX11(obj)) {
		MixDisplayX11 *duplicate = mix_displayx11_new();
		if (mix_displayx11_copy(MIX_DISPLAY(duplicate), MIX_DISPLAY(obj))) {
			ret = MIX_DISPLAY(duplicate);
		} else {
			mix_displayx11_unref(duplicate);
		}
	}
	return ret;
}

/**
 * mix_mixdisplayx11_copy:
 * @target: copy to target
 * @src: copy from src
 * @returns: boolean indicates if copy is successful.
 *
 * Copy instance data from @src to @target.
 */
gboolean mix_displayx11_copy(MixDisplay * target, const MixDisplay * src) {
	MixDisplayX11 *this_target, *this_src;

	if (MIX_IS_DISPLAYX11(target) && MIX_IS_DISPLAYX11(src)) {
		// Cast the base object to this child object
		this_target = MIX_DISPLAYX11(target);
		this_src = MIX_DISPLAYX11(src);

		// Copy properties from source to target.

		this_target->display = this_src->display;
		this_target->drawable = this_src->drawable;

		// Now chainup base class
		if (parent_class->copy) {
			return parent_class->copy(MIX_DISPLAY_CAST(target),
					MIX_DISPLAY_CAST(src));
		} else {
			return TRUE;
		}
	}
	return FALSE;
}

/**
 * mix_mixdisplayx11_equal:
 * @first: first object to compare
 * @second: seond object to compare
 * @returns: boolean indicates if instance are equal.
 *
 * Copy instance data from @src to @target.
 */
gboolean mix_displayx11_equal(MixDisplay * first, MixDisplay * second) {
	gboolean ret = FALSE;

	MixDisplayX11 *this_first, *this_second;

	this_first = MIX_DISPLAYX11(first);
	this_second = MIX_DISPLAYX11(second);

	if (MIX_IS_DISPLAYX11(first) && MIX_IS_DISPLAYX11(second)) {
		// Compare member variables

		// TODO: if in the copy method we just copy the pointer of display, the comparison
		//      below is enough. But we need to decide how to copy!

		if (this_first->display == this_second->display && this_first->drawable
				== this_second->drawable) {
			// members within this scope equal. chaining up.
			MixDisplayClass *klass = MIX_DISPLAY_CLASS(parent_class);
			if (klass->equal)
				ret = parent_class->equal(first, second);
			else
				ret = TRUE;
		}
	}
	return ret;
}

#define MIX_DISPLAYX11_SETTER_CHECK_INPUT(obj) \
	if(!obj) return MIX_RESULT_NULL_PTR; \
	if(!MIX_IS_DISPLAYX11(obj)) return MIX_RESULT_FAIL; \

#define MIX_DISPLAYX11_GETTER_CHECK_INPUT(obj, prop) \
	if(!obj || !prop) return MIX_RESULT_NULL_PTR; \
	if(!MIX_IS_DISPLAYX11(obj)) return MIX_RESULT_FAIL; \

MIX_RESULT mix_displayx11_set_display(MixDisplayX11 * obj, Display * display) {
	MIX_DISPLAYX11_SETTER_CHECK_INPUT (obj);

	// TODO: needs to decide to clone or just copy pointer
	obj->display = display;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_displayx11_get_display(MixDisplayX11 * obj, Display ** display) {
	MIX_DISPLAYX11_GETTER_CHECK_INPUT (obj, display);

	// TODO: needs to decide to clone or just copy pointer
	*display = obj->display;

	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_displayx11_set_drawable(MixDisplayX11 * obj, Drawable drawable) {
	MIX_DISPLAYX11_SETTER_CHECK_INPUT (obj);

	// TODO: needs to decide to clone or just copy pointer
	obj->drawable = drawable;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_displayx11_get_drawable(MixDisplayX11 * obj, Drawable * drawable) {
	MIX_DISPLAYX11_GETTER_CHECK_INPUT (obj, drawable);

	// TODO: needs to decide to clone or just copy pointer
	*drawable = obj->drawable;
	return MIX_RESULT_SUCCESS;
}
