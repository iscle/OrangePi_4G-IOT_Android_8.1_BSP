#include "../../src/mixframemanager.h"

gboolean stop_thread = FALSE;
GCond* data_cond = NULL;
GMutex* data_mutex = NULL;


void *deque_function(void *data) {

	MixFrameManager *fm = (MixFrameManager *) data;
	MIX_RESULT mixresult;
	MixVideoFrame *mvf = NULL;
	guint64 pts;
	while(!stop_thread) {

		g_mutex_lock (data_mutex);

		mixresult = mix_framemanager_dequeue(fm, &mvf);
		if(mixresult == MIX_RESULT_SUCCESS) {
			mixresult = mix_videoframe_get_timestamp(mvf, &pts);
			g_print("dequeued timestamp = %"G_GINT64_FORMAT"\n", pts);
			/* mix_videoframe_unref(mvf); */
		} else if(mixresult == MIX_RESULT_FRAME_NOTAVAIL) {
			g_print("mixresult == MIX_RESULT_FRAME_NOTAVAIL\n");
			g_cond_wait (data_cond, data_mutex);
		}

		g_mutex_unlock (data_mutex);

	}
}

void shuffle(GPtrArray *list) {
	guint idx, jdx;
	guint len = list->len;
	for (idx = 0; idx < len - 1; idx++) {
		jdx = rand() % len;
		if (idx != jdx) {
			gpointer tmp = g_ptr_array_index(list, jdx);
			g_ptr_array_index(list, jdx) = g_ptr_array_index(list, idx);
			g_ptr_array_index(list, idx) = tmp;
		}
	}
}

int main() {
	MIX_RESULT mixresult;

	gint fps_n = 24000;
	gint fps_d = 1001;

/*
	gint fps_n = 2500000;
	gint fps_d = 104297;
*/
	GPtrArray *fa = NULL;
	MixFrameManager *fm = NULL;
	MixVideoFrame *mvf = NULL;
	MixVideoFrame *mvf_1st = NULL;

	gint idx = 0;
	guint64 pts = 0;

	GThread *deque_thread = NULL;
	GError *deque_thread_error = NULL;

	/* first ting first */
	g_type_init();

	/* create frame manager */
	fm = mix_framemanager_new();
	if (!fm) {
		goto cleanup;
	}

	/* initialize frame manager */
	mixresult = mix_framemanager_initialize(fm,
			MIX_FRAMEORDER_MODE_DISPLAYORDER, fps_n, fps_d);
	if (mixresult != MIX_RESULT_SUCCESS) {
		goto cleanup;
	}

	/* create frame_array */
	fa = g_ptr_array_sized_new(64);
	if (!fa) {
		goto cleanup;
	}

	for (idx = 0; idx < 16; idx++) {
		/* generate MixVideoFrame */
		mvf = mix_videoframe_new();
		if (!mvf) {
			goto cleanup;
		}

		pts = idx * G_USEC_PER_SEC * G_GINT64_CONSTANT(1000) * fps_d / fps_n;
		mixresult = mix_videoframe_set_timestamp(mvf, pts);
		if (mixresult != MIX_RESULT_SUCCESS) {
			goto cleanup;
		}

		g_print("original timestamp = %"G_GINT64_FORMAT"\n", pts);

		if (idx == 0) {
			mvf_1st = mvf;
		} else {
			g_ptr_array_add(fa, (gpointer) mvf);
		}
	}

	/* shuffle the array */
	shuffle( fa);

	data_mutex = g_mutex_new ();
	if(!data_mutex) {
		goto cleanup;
	}

	data_cond =  g_cond_new();
	if(!data_cond) {
		goto cleanup;
	}


	/* create another thread to dequeue */
	deque_thread = g_thread_create((GThreadFunc) deque_function, (void *) fm,
			TRUE, &deque_thread_error);
	if (!deque_thread) {
		goto cleanup;
	}

	/* enqueue */
	mixresult = mix_framemanager_enqueue(fm, mvf_1st);
	if (mixresult != MIX_RESULT_SUCCESS) {
		goto cleanup;
	}

	mixresult = mix_videoframe_get_timestamp(mvf_1st, &pts);
	if (mixresult != MIX_RESULT_SUCCESS) {
		goto cleanup;
	}
	g_print("shuffled timestamp = %"G_GINT64_FORMAT"\n", pts);

	for (idx = 0; idx < fa->len; idx++) {

		g_mutex_lock (data_mutex);

		/* wait for 100ms to enqueue another frame */
		g_usleep(G_USEC_PER_SEC / 10 );

		mvf = (MixVideoFrame *) g_ptr_array_index(fa, idx);
		mixresult = mix_framemanager_enqueue(fm, mvf);

		/* wake up deque thread */
		g_cond_signal (data_cond);


		g_mutex_unlock (data_mutex);

		if (mixresult != MIX_RESULT_SUCCESS) {
			goto cleanup;
		}

		mixresult = mix_videoframe_get_timestamp(mvf, &pts);
		if (mixresult != MIX_RESULT_SUCCESS) {
			goto cleanup;
		}

		g_print("shuffled timestamp = %"G_GINT64_FORMAT"\n", pts);
	}

	getchar();

	stop_thread = TRUE;

	/* wake up deque thread */
	g_cond_signal (data_cond);

	g_thread_join(deque_thread);

cleanup:

	if(data_mutex) {
		g_mutex_free(data_mutex);
	}

	if(data_cond) {
		g_cond_free(data_cond);
	}

	if (fm) {
		mix_framemanager_unref(fm);
	}

	if (fa) {
		g_ptr_array_free(fa, TRUE);
	}

	return 0;
}
