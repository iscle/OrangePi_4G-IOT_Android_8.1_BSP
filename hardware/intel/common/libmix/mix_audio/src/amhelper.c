#include "amhelper.h"
#include <mixlog.h>

static DBusGConnection *connection;

static DBusGProxy *proxy_lpe = NULL;

static gboolean am_enable=FALSE;

/* Connect to am dbus server
 * return -1 means failed
 * return 0 means succeeded
 * */
gint dbus_init() {
    GError *error;
    const char *name = "org.moblin.audiomanager";

    const char *path_lpe = "/org/moblin/audiomanager/lpe";
    const char *interface_lpe = "org.moblin.audiomanager.lpe";

    const gchar* env  = g_getenv("MIX_AM");
    if (env && env[0] == '1') {
	am_enable = TRUE;
    }
    else
	am_enable = FALSE;

    if (am_enable) {
	    error = NULL;
	    connection = dbus_g_bus_get(DBUS_BUS_SESSION, &error);

	    if (connection == NULL) {
		mix_log(MIX_AUDIO_COMP, MIX_LOG_LEVEL_WARNING, "Failed to open connection to bus: %s\n",
		            error->message);
		g_error_free(error);
		return -1;
	    }
	    mix_log(MIX_AUDIO_COMP, MIX_LOG_LEVEL_VERBOSE, "Successfully get a dbus connection\n");

	    proxy_lpe = dbus_g_proxy_new_for_name(connection, name,
		                            path_lpe, interface_lpe);
	    if (proxy_lpe == NULL) {
		mix_log(MIX_AUDIO_COMP, MIX_LOG_LEVEL_WARNING, "Failed to connect to AM dbus server\n");
		return -1;
	    } 
	    else {
	    	mix_log(MIX_AUDIO_COMP, MIX_LOG_LEVEL_VERBOSE, "Successfully connected to AM dbus\npath: %s\ninterface: %s\n",
			path_lpe, interface_lpe);
	    }
    }
    return 0;
}

gint32 lpe_stream_register(guint32 lpe_stream_id, char* media_role, char* lpe_stream_name, guint32 stream_type)
{
  GError *error;
  gint32 s_output = 0;
  error = NULL;

  if (am_enable) {
	  mix_log(MIX_AUDIO_COMP, MIX_LOG_LEVEL_INFO, "lpe_stream_id: %d\n", lpe_stream_id);

	  if (lpe_stream_id == 0) {
		return 0;
	  }
	  if(!dbus_g_proxy_call (proxy_lpe, "LPEStreamRegister", &error, G_TYPE_UINT, 
		lpe_stream_id, G_TYPE_STRING, media_role, G_TYPE_STRING, lpe_stream_name, G_TYPE_UINT, stream_type,
		G_TYPE_INVALID, G_TYPE_INT, &s_output, G_TYPE_INVALID)) {
		mix_log(MIX_AUDIO_COMP, MIX_LOG_LEVEL_WARNING, "LPEStreamRegister failed: %s\n", error->message);
                g_error_free(error);
		return s_output;
	  }

	  mix_log(MIX_AUDIO_COMP, MIX_LOG_LEVEL_INFO, "LPEStreamRegister returned am stream id %d\n", s_output);
  }

  return s_output;
}

gint32 lpe_stream_unregister(guint32 am_stream_id)
{
  GError *error;
  gint32 s_output = 0;

  if (am_enable) {
	  error = NULL;
	  if(!dbus_g_proxy_call (proxy_lpe, "LPEStreamUnregister", &error, G_TYPE_UINT, am_stream_id, 
		G_TYPE_INVALID, G_TYPE_INT, &s_output, G_TYPE_INVALID)){
		mix_log(MIX_AUDIO_COMP, MIX_LOG_LEVEL_WARNING, "LPEStreamUnregister failed: %s\n", error->message);
		g_error_free(error);
		return s_output;
	  }
  }
  return s_output;
}

gint32 lpe_stream_notify_pause(guint32 stream_id)
{
  GError *error;
  gint32 s_output=0;

  if (am_enable) {
    dbus_g_proxy_call (proxy_lpe, "LPEStreamNotifyPause", &error, G_TYPE_UINT, stream_id, G_TYPE_INVALID, G_TYPE_INT, &s_output, G_TYPE_INVALID);
  }

  return s_output;
}

gint32 lpe_stream_notify_resume(guint32 stream_id)
{
  GError *error;
  gint32 s_output=0;

  if (am_enable) {
    dbus_g_proxy_call (proxy_lpe, "LPEStreamNotifyResume", &error, G_TYPE_UINT, stream_id, G_TYPE_INVALID, G_TYPE_INT, &s_output, G_TYPE_INVALID);
  }

  return s_output;
}

