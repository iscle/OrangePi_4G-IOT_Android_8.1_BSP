/* 
INTEL CONFIDENTIAL
Copyright 2009 Intel Corporation All Rights Reserved. 
The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
*/

/**
* SECTION:mixdisplay
* @short_description: Lightweight base class for the MIX media display
*
*/
#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include "mixdisplay.h"
#include <gobject/gvaluecollector.h>

#define DEBUG_REFCOUNT

static void mix_display_class_init (gpointer g_class, gpointer class_data);
static void mix_display_init (GTypeInstance * instance, gpointer klass);

static void mix_value_display_init (GValue * value);
static void mix_value_display_free (GValue * value);
static void mix_value_display_copy (const GValue * src_value,
				    GValue * dest_value);
static gpointer mix_value_display_peek_pointer (const GValue * value);
static gchar *mix_value_display_collect (GValue * value,
					 guint n_collect_values,
					 GTypeCValue * collect_values,
					 guint collect_flags);
static gchar *mix_value_display_lcopy (const GValue * value,
				       guint n_collect_values,
				       GTypeCValue * collect_values,
				       guint collect_flags);

static void mix_display_finalize (MixDisplay * obj);
static gboolean mix_display_copy_default (MixDisplay * target,
					  const MixDisplay * src);
static MixDisplay *mix_display_dup_default (const MixDisplay * obj);
static gboolean mix_display_equal_default (MixDisplay * first,
					   MixDisplay * second);

GType
mix_display_get_type (void)
{
  static GType _mix_display_type = 0;

  if (G_UNLIKELY (_mix_display_type == 0))
    {

      GTypeValueTable value_table = {
	mix_value_display_init,
	mix_value_display_free,
	mix_value_display_copy,
	mix_value_display_peek_pointer,
	"p",
	mix_value_display_collect,
	"p",
	mix_value_display_lcopy
      };

      GTypeInfo info = {
	sizeof (MixDisplayClass),
	NULL,
	NULL,
	mix_display_class_init,
	NULL,
	NULL,
	sizeof (MixDisplay),
	0,
	(GInstanceInitFunc) mix_display_init,
	NULL
      };

      static const GTypeFundamentalInfo fundamental_info = {
	(G_TYPE_FLAG_CLASSED | G_TYPE_FLAG_INSTANTIATABLE |
	 G_TYPE_FLAG_DERIVABLE | G_TYPE_FLAG_DEEP_DERIVABLE)
      };

      info.value_table = &value_table;

      _mix_display_type = g_type_fundamental_next ();
      g_type_register_fundamental (_mix_display_type, "MixDisplay",
				   &info, &fundamental_info,
				   G_TYPE_FLAG_ABSTRACT);

    }

  return _mix_display_type;
}

static void
mix_display_class_init (gpointer g_class, gpointer class_data)
{
  MixDisplayClass *klass = MIX_DISPLAY_CLASS (g_class);

  klass->dup = mix_display_dup_default;
  klass->copy = mix_display_copy_default;
  klass->finalize = mix_display_finalize;
  klass->equal = mix_display_equal_default;
}

static void
mix_display_init (GTypeInstance * instance, gpointer klass)
{
  MixDisplay *obj = MIX_DISPLAY_CAST (instance);

  obj->refcount = 1;
}

gboolean
mix_display_copy (MixDisplay * target, const MixDisplay * src)
{
  /* Use the target object class. Because it knows what it is looking for. */
  MixDisplayClass *klass = MIX_DISPLAY_GET_CLASS (target);
  if (klass->copy)
    {
      return klass->copy (target, src);
    }
  else
    {
      return mix_display_copy_default (target, src);
    }
}

/**
* mix_display_copy_default:
* @target:
* @src:
* 
* The default copy method of this object. Perhap copy at this level.
* Assign this to the copy vmethod.
*/
static gboolean
mix_display_copy_default (MixDisplay * target, const MixDisplay * src)
{
  if (MIX_IS_DISPLAY (target) && MIX_IS_DISPLAY (src))
    {
      // TODO perform deep copy.
      return TRUE;
    }
  return FALSE;
}

static void
mix_display_finalize (MixDisplay * obj)
{
  /* do nothing */
}

MixDisplay *
mix_display_dup (const MixDisplay * obj)
{
  MixDisplayClass *klass = MIX_DISPLAY_GET_CLASS (obj);

  if (klass->dup)
    {
      return klass->dup (obj);
    }
  else if (MIX_IS_DISPLAY (obj))
    {
      return mix_display_dup_default (obj);
    }
  return NULL;
}

static MixDisplay *
mix_display_dup_default (const MixDisplay * obj)
{
  MixDisplay *ret = mix_display_new ();
  if (mix_display_copy (ret, obj))
    {
      return ret;
    }

  return NULL;
}

MixDisplay *
mix_display_new (GType type)
{
  MixDisplay *obj;

  /* we don't support dynamic types because they really aren't useful,
   * and could cause refcount problems */
  obj = (MixDisplay *) g_type_create_instance (type);

  return obj;
}

MixDisplay *
mix_display_ref (MixDisplay * obj)
{
  g_return_val_if_fail (MIX_IS_DISPLAY (obj), NULL);

  g_atomic_int_inc (&obj->refcount);

  return obj;
}

static void
mix_display_free (MixDisplay * obj)
{
  MixDisplayClass *klass = NULL;

  klass = MIX_DISPLAY_GET_CLASS (obj);
  klass->finalize (obj);

  /* Should we support recycling the object? */
  /* If so, refcount handling is slightly different. */
  /* i.e. If the refcount is still 0 we can really free the object, else the finalize method recycled the object -- but to where? */

  if (g_atomic_int_get (&obj->refcount) == 0)
    {

      g_type_free_instance ((GTypeInstance *) obj);
    }
}

void
mix_display_unref (MixDisplay * obj)
{
  g_return_if_fail (obj != NULL);
  g_return_if_fail (obj->refcount > 0);

  if (G_UNLIKELY (g_atomic_int_dec_and_test (&obj->refcount)))
    {
      mix_display_free (obj);
    }
}

static void
mix_value_display_init (GValue * value)
{
  value->data[0].v_pointer = NULL;
}

static void
mix_value_display_free (GValue * value)
{
  if (value->data[0].v_pointer)
    {
      mix_display_unref (MIX_DISPLAY_CAST (value->data[0].v_pointer));
    }
}

static void
mix_value_display_copy (const GValue * src_value, GValue * dest_value)
{
  if (src_value->data[0].v_pointer)
    {
      dest_value->data[0].v_pointer =
	mix_display_ref (MIX_DISPLAY_CAST (src_value->data[0].v_pointer));
    }
  else
    {
      dest_value->data[0].v_pointer = NULL;
    }
}

static gpointer
mix_value_display_peek_pointer (const GValue * value)
{
  return value->data[0].v_pointer;
}

static gchar *
mix_value_display_collect (GValue * value, guint n_collect_values,
			   GTypeCValue * collect_values, guint collect_flags)
{
  mix_value_set_display (value, collect_values[0].v_pointer);

  return NULL;
}

static gchar *
mix_value_display_lcopy (const GValue * value,
			 guint n_collect_values,
			 GTypeCValue * collect_values, guint collect_flags)
{
  gpointer *obj_p = collect_values[0].v_pointer;

  if (!obj_p)
    {
      return g_strdup_printf ("value location for '%s' passed as NULL",
			      G_VALUE_TYPE_NAME (value));
    }

  if (!value->data[0].v_pointer)
    *obj_p = NULL;
  else if (collect_flags & G_VALUE_NOCOPY_CONTENTS)
    *obj_p = value->data[0].v_pointer;
  else
    *obj_p = mix_display_ref (value->data[0].v_pointer);

  return NULL;
}

/**
* mix_value_set_display:
* @value:       a valid #GValue of %MIX_TYPE_DISPLAY derived type
* @obj: object value to set
*
* Set the contents of a %MIX_TYPE_DISPLAY derived #GValue to
* @obj.
* The caller retains ownership of the reference.
*/
void
mix_value_set_display (GValue * value, MixDisplay * obj)
{
  gpointer *pointer_p;

  g_return_if_fail (MIX_VALUE_HOLDS_DISPLAY (value));
  g_return_if_fail (obj == NULL || MIX_IS_DISPLAY (obj));

  pointer_p = &value->data[0].v_pointer;
  mix_display_replace ((MixDisplay **) pointer_p, obj);
}

/**
* mix_value_take_display:
* @value: a valid #GValue of #MIX_TYPE_DISPLAY derived type
* @obj: object value to take
*
* Set the contents of a #MIX_TYPE_DISPLAY derived #GValue to
* @obj.
* Takes over the ownership of the caller's reference to @obj;
* the caller doesn't have to unref it any more.
*/
void
mix_value_take_display (GValue * value, MixDisplay * obj)
{
  gpointer *pointer_p;

  g_return_if_fail (MIX_VALUE_HOLDS_DISPLAY (value));
  g_return_if_fail (obj == NULL || MIX_IS_DISPLAY (obj));

  pointer_p = &value->data[0].v_pointer;
  mix_display_replace ((MixDisplay **) pointer_p, obj);
  if (obj)
    mix_display_unref (obj);
}

/**
* mix_value_get_display:
* @value: a valid #GValue of #MIX_TYPE_DISPLAY derived type
* @returns:object contents of @value 
*
* refcount of the MixDisplay is not increased.
*/
MixDisplay *
mix_value_get_display (const GValue * value)
{
  g_return_val_if_fail (MIX_VALUE_HOLDS_DISPLAY (value), NULL);

  return value->data[0].v_pointer;
}

/**
* mix_value_dup_display:
* @value:   a valid #GValue of %MIX_TYPE_DISPLAY derived type
* @returns: object contents of @value
*
* refcount of MixDisplay is increased.
*/
MixDisplay *
mix_value_dup_display (const GValue * value)
{
  g_return_val_if_fail (MIX_VALUE_HOLDS_DISPLAY (value), NULL);

  return mix_display_ref (value->data[0].v_pointer);
}


static void
param_display_init (GParamSpec * pspec)
{
  /* GParamSpecDisplay *ospec = G_PARAM_SPEC_DISPLAY (pspec); */
}

static void
param_display_set_default (GParamSpec * pspec, GValue * value)
{
  value->data[0].v_pointer = NULL;
}

static gboolean
param_display_validate (GParamSpec * pspec, GValue * value)
{
  gboolean validated = FALSE;
  MixParamSpecDisplay *ospec = MIX_PARAM_SPEC_DISPLAY (pspec);
  MixDisplay *obj = value->data[0].v_pointer;

  if (obj && !g_value_type_compatible (G_OBJECT_TYPE (obj), G_PARAM_SPEC_VALUE_TYPE (ospec)))
    {
      mix_display_unref (obj);
      value->data[0].v_pointer = NULL;
      validated = TRUE;
    }

  return validated;
}

static gint
param_display_values_cmp (GParamSpec * pspec,
			  const GValue * value1, const GValue * value2)
{
  guint8 *p1 = value1->data[0].v_pointer;
  guint8 *p2 = value2->data[0].v_pointer;


  return p1 < p2 ? -1 : p1 > p2;
}

GType
mix_param_spec_display_get_type (void)
{
  static GType type;

  if (G_UNLIKELY (type) == 0)
    {
      static const GParamSpecTypeInfo pspec_info = {
	sizeof (MixParamSpecDisplay),	/* instance_size */
	16,			/* n_preallocs */
	param_display_init,	/* instance_init */
	G_TYPE_OBJECT,		/* value_type */
	NULL,			/* finalize */
	param_display_set_default,	/* value_set_default */
	param_display_validate,	/* value_validate */
	param_display_values_cmp,	/* values_cmp */
      };
      /* FIXME 0.11: Should really be MixParamSpecDisplay */
      type = g_param_type_register_static ("GParamSpecDisplay", &pspec_info);
    }

  return type;
}

/**
* mix_param_spec_display:
* @name: the canonical name of the property
* @nick: the nickname of the property
* @blurb: a short description of the property
* @object_type: the #MixDisplayType for the property
* @flags: a combination of #GParamFlags
* @returns: a newly allocated #GParamSpec instance
*
* Creates a new #GParamSpec instance that hold #MixDisplay references.
*
*/
GParamSpec *
mix_param_spec_display (const char *name, const char *nick,
			const char *blurb, GType object_type,
			GParamFlags flags)
{
  MixParamSpecDisplay *ospec;

  g_return_val_if_fail (g_type_is_a (object_type, MIX_TYPE_DISPLAY), NULL);

  ospec = g_param_spec_internal (MIX_TYPE_PARAM_DISPLAY,
				 name, nick, blurb, flags);
  G_PARAM_SPEC (ospec)->value_type = object_type;

  return G_PARAM_SPEC (ospec);
}

/**
* mix_display_replace:
* @olddata: pointer to a pointer to a object to be replaced
* @newdata: pointer to new object
*
* Modifies a pointer to point to a new object.  The modification
* is done atomically, and the reference counts are updated correctly.
* Either @newdata and the value pointed to by @olddata may be NULL.
*/
void
mix_display_replace (MixDisplay ** olddata, MixDisplay * newdata)
{
  MixDisplay *olddata_val;

  g_return_if_fail (olddata != NULL);

  olddata_val = g_atomic_pointer_get ((gpointer *) olddata);

  if (olddata_val == newdata)
    return;

  if (newdata)
    mix_display_ref (newdata);

  while (!g_atomic_pointer_compare_and_exchange
	 ((gpointer *) olddata, olddata_val, newdata))
    {
      olddata_val = g_atomic_pointer_get ((gpointer *) olddata);
    }

  if (olddata_val)
    mix_display_unref (olddata_val);

}

gboolean
mix_display_equal (MixDisplay * first, MixDisplay * second)
{
  if (MIX_IS_DISPLAY (first))
    {
      MixDisplayClass *klass = MIX_DISPLAY_GET_CLASS (first);

      if (klass->equal)
	{
	  return klass->equal (first, second);
	}
      else
	{
	  return mix_display_equal_default (first, second);
	}
    }
  else
    return FALSE;
}

static gboolean
mix_display_equal_default (MixDisplay * first, MixDisplay * second)
{
  if (MIX_IS_DISPLAY (first) && MIX_IS_DISPLAY (second))
    {
      gboolean ret = TRUE;

      // Do data comparison here.

      return ret;
    }
  else
    return FALSE;
}
