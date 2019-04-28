#include <stdio.h>
#include <glib.h>
#include <glib-object.h>
#include "mixvideo.h"
#include "mixdisplayx11.h"

int
main (int argc, char **argv)
{
  MIX_RESULT ret;

  g_type_init ();

/* test MixDisplay */
  {

    MixDisplayX11 *x11_clone = NULL;
    MixDisplayX11 *x11 = mix_displayx11_new ();

    MixDisplay *base = MIX_DISPLAY (x11);

    gboolean flag = MIX_IS_DISPLAYX11 (base);

    Drawable drawable = 1024;

    mix_displayx11_set_drawable (x11, drawable);

/* clone x11 */

    x11_clone = (MixDisplayX11 *) mix_display_dup (MIX_DISPLAY (x11));

    base = MIX_DISPLAY (x11_clone);

    flag = MIX_IS_DISPLAYX11 (base);

    mix_displayx11_get_drawable (x11_clone, &drawable);

/* TODO: add more test cases */

/* release */
    mix_display_unref (MIX_DISPLAY (x11));
    mix_display_unref (MIX_DISPLAY (x11_clone));
    g_print ("MixDisplayX11 test is done!\n");
  }

/* test MixVideoInitParams */
  {
    MixVideoInitParams *init_params = mix_videoinitparams_new ();

    MixDisplayX11 *x11 = mix_displayx11_new ();
    mix_displayx11_set_drawable (x11, 1024);

    mix_videoinitparams_set_display (init_params, MIX_DISPLAY (x11));

/* release */
    mix_params_unref (MIX_PARAMS (init_params));
    mix_display_unref (MIX_DISPLAY (x11));

    g_print ("MixVideoInitParams test is done!\n");
  }

/* test MixVideo */

  {
    MixVideo *video = mix_video_new ();
    MixVideoInitParams *init_params = mix_videoinitparams_new ();
    MixDisplayX11 *x11 = mix_displayx11_new ();
    MixDrmParams *drm = mix_drmparams_new ();
    MixCodecMode mode = MIX_CODEC_MODE_DECODE;

    mix_displayx11_set_drawable (x11, 1024);
    mix_videoinitparams_set_display (init_params, MIX_DISPLAY (x11));

    mix_video_initialize (video, mode, init_params, drm);

/* TODO: add more test cases */

/* unref the objects. */

    mix_params_unref (MIX_PARAMS (init_params));
    mix_params_unref (MIX_PARAMS (drm));
    mix_display_unref (MIX_DISPLAY (x11));
    g_object_unref (G_OBJECT (video));

    g_print ("MixVideo test is done!\n");
  }
}
