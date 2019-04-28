/* 
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved. 
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
*/

/**
 * SECTION:mixparams
 * @short_description: Lightweight base class for the MIX media params
 *
 */
#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include "mixparams.h"
#include <gobject/gvaluecollector.h>


#define DEBUG_REFCOUNT

static void mix_params_class_init (gpointer g_class, gpointer class_data);
static void mix_params_init (GTypeInstance * instance, gpointer klass);

static void mix_params_finalize(MixParams * obj);
static gboolean mix_params_copy_default (MixParams *target, const MixParams *src);
static MixParams *mix_params_dup_default(const MixParams *obj);
static gboolean mix_params_equal_default (MixParams *first, MixParams *second);

GType mix_params_get_type (void)
{
  static GType _mix_params_type = 0;

  if (G_UNLIKELY (_mix_params_type == 0)) {

    GTypeInfo info = {
      sizeof (MixParamsClass),
      NULL, 
      NULL,
      mix_params_class_init,
      NULL,
      NULL,
      sizeof (MixParams),
      0,
      (GInstanceInitFunc) mix_params_init,
      NULL
    };

    static const GTypeFundamentalInfo fundamental_info = {
      (G_TYPE_FLAG_CLASSED | G_TYPE_FLAG_INSTANTIATABLE |
          G_TYPE_FLAG_DERIVABLE | G_TYPE_FLAG_DEEP_DERIVABLE)
    };

    info.value_table = NULL;

    _mix_params_type = g_type_fundamental_next ();
    g_type_register_fundamental (_mix_params_type, "MixParams", &info, &fundamental_info, G_TYPE_FLAG_ABSTRACT);

  }

  return _mix_params_type;
}

static void mix_params_class_init (gpointer g_class, gpointer class_data)
{
  MixParamsClass *klass = MIX_PARAMS_CLASS (g_class);

  klass->dup = mix_params_dup_default;
  klass->copy = mix_params_copy_default;
  klass->finalize = mix_params_finalize;
  klass->equal = mix_params_equal_default;
}

static void mix_params_init (GTypeInstance * instance, gpointer klass)
{
  MixParams *obj = MIX_PARAMS_CAST (instance);

  obj->refcount = 1;
}

gboolean mix_params_copy (MixParams *target, const MixParams *src)
{
  /* Use the target object class. Because it knows what it is looking for. */
  MixParamsClass *klass = MIX_PARAMS_GET_CLASS(target); 
  if (klass->copy)
  {
    return klass->copy(target, src);
  }
  else
  {
    return mix_params_copy_default(target, src);
  }
}

/**
 * mix_params_copy_default:
 * @target: target 
 * @src: source
 * 
 * The default copy method of this object. Perhap copy at this level.
 * Assign this to the copy vmethod.
 */
static gboolean mix_params_copy_default (MixParams *target, const MixParams *src)
{
  if (MIX_IS_PARAMS(target) && MIX_IS_PARAMS(src))
  {
    // TODO perform deep copy.
    return TRUE;
  }
  return FALSE;
}

static void mix_params_finalize (MixParams * obj)
{
  /* do nothing */
}

MixParams *mix_params_dup(const MixParams *obj)
{
  MixParamsClass *klass = MIX_PARAMS_GET_CLASS(obj);
  
  if (klass->dup)
  {
    return klass->dup(obj);
  }
  else if (MIX_IS_PARAMS(obj))
  {
    return mix_params_dup_default(obj);
  }
  return NULL;
}

static MixParams *mix_params_dup_default(const MixParams *obj)
{
    MixParams *ret = mix_params_new();
    if (mix_params_copy(ret, obj))
    {
      return ret;
    }

    return NULL;
}

MixParams* mix_params_new (GType type)
{
  MixParams *obj;

  /* we don't support dynamic types because they really aren't useful,
   * and could cause refcount problems */
  obj = (MixParams *) g_type_create_instance (type);

  return obj;
}

MixParams* mix_params_ref (MixParams *obj)
{
  g_return_val_if_fail(MIX_IS_PARAMS (obj), NULL);

  g_atomic_int_inc(&obj->refcount);

  return obj;
}

static void mix_params_free(MixParams *obj)
{
  MixParamsClass *klass = NULL;

  klass = MIX_PARAMS_GET_CLASS(obj);
  klass->finalize(obj);

  /* Should we support recycling the object? */
  /* If so, refcount handling is slightly different. */
  /* i.e. If the refcount is still 0 we can really free the object, else the finalize method recycled the object -- but to where? */

  if (g_atomic_int_get (&obj->refcount) == 0) {

    g_type_free_instance ((GTypeInstance *) obj);
  }
}

void mix_params_unref (MixParams *obj)
{
  g_return_if_fail (obj != NULL);
  g_return_if_fail (obj->refcount > 0);

  if (G_UNLIKELY (g_atomic_int_dec_and_test (&obj->refcount))) {
    mix_params_free (obj);
  }
}

/**
 * mix_params_replace:
 * @olddata: pointer to a pointer to a object to be replaced
 * @newdata: pointer to new object
 *
 * Modifies a pointer to point to a new object.  The modification
 * is done atomically, and the reference counts are updated correctly.
 * Either @newdata and the value pointed to by @olddata may be NULL.
 */
void mix_params_replace (MixParams **olddata, MixParams *newdata)
{
  MixParams *olddata_val;

  g_return_if_fail (olddata != NULL);

  olddata_val = g_atomic_pointer_get ((gpointer *) olddata);

  if (olddata_val == newdata)
    return;

  if (newdata)
    mix_params_ref (newdata);

  while (!g_atomic_pointer_compare_and_exchange ((gpointer *) olddata, olddata_val, newdata)) 
  {
    olddata_val = g_atomic_pointer_get ((gpointer *) olddata);
  }

  if (olddata_val)
    mix_params_unref (olddata_val);

}

gboolean mix_params_equal (MixParams *first, MixParams *second)
{
  if (MIX_IS_PARAMS(first))
  {
    MixParamsClass *klass = MIX_PARAMS_GET_CLASS(first);
    
    if (klass->equal)
    {
      return klass->equal(first, second);
    }
    else
    {
      return mix_params_equal_default(first, second);
    }
  }
  else
    return FALSE;
}

static gboolean mix_params_equal_default (MixParams *first, MixParams *second)
{
  if (MIX_IS_PARAMS(first) && MIX_IS_PARAMS(second))
  {
    gboolean ret = TRUE;

    // Do data comparison here.

    return ret;
  }
  else
    return FALSE;
}

/**
 * mix_value_dup_params:
 * @value:   a valid #GValue of %MIX_TYPE_PARAMS derived type
 * @returns: object contents of @value
 *
 * Get the contents of a #MIX_TYPE_PARAMS derived #GValue,
 * increasing its reference count.
 */
MixParams* mix_value_dup_params (const GValue * value)
{
  g_return_val_if_fail (MIX_VALUE_HOLDS_PARAMS (value), NULL);

  return mix_params_ref (value->data[0].v_pointer);
}


