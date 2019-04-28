/*
 * Copyright (C) 2017 Google, Inc.
 *
 * This software is licensed under the terms of the GNU General Public
 * License version 2, as published by the Free Software Foundation, and
 * may be copied, distributed, and modified under those terms.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 */

#undef TRACE_SYSTEM
#define TRACE_SYSTEM android_fs

#if !defined(_TRACE_ANDROID_FS_H) || defined(TRACE_HEADER_MULTI_READ)
#define _TRACE_ANDROID_FS_H

#include <linux/tracepoint.h>
#include <trace/events/android_fs_template.h>

DEFINE_EVENT(android_fs_data_start_template, android_fs_dataread_start,
	TP_PROTO(struct inode *inode, loff_t offset, int bytes,
		 pid_t pid, const char *pathname, const char *command),
	TP_ARGS(inode, offset, bytes, pid, pathname, command));

DEFINE_EVENT(android_fs_data_end_template, android_fs_dataread_end,
	TP_PROTO(struct inode *inode, loff_t offset, int bytes),
	TP_ARGS(inode, offset, bytes));

DEFINE_EVENT(android_fs_data_start_template, android_fs_datawrite_start,
	TP_PROTO(struct inode *inode, loff_t offset, int bytes,
		 pid_t pid, const char *pathname, const char *command),
	TP_ARGS(inode, offset, bytes, pid, pathname, command));

DEFINE_EVENT(android_fs_data_end_template, android_fs_datawrite_end,
	TP_PROTO(struct inode *inode, loff_t offset, int bytes),
	     TP_ARGS(inode, offset, bytes));

TRACE_EVENT(android_fs_writepages,
	    TP_PROTO(struct inode *inode, int bytes,
		     const char *pathname),
	    TP_ARGS(inode, bytes, pathname),
	    TP_STRUCT__entry(
		    __string(pathbuf, pathname);
		    __field(int,	bytes);
		    __field(ino_t,	ino);
		    ),
	    TP_fast_assign(
		{
			/*
			 * Replace the spaces in filenames and cmdlines
			 * because this screws up the tooling that parses
			 * the traces.
			 */
			__assign_str(pathbuf, pathname);
			(void)strreplace(__get_str(pathbuf), ' ', '_');
			__entry->bytes		= bytes;
			__entry->ino		= inode->i_ino;
		}
	),
	TP_printk("entry_name %s, bytes %d,"
		  " ino %lu",
		  __get_str(pathbuf), __entry->bytes,
		  (unsigned long) __entry->ino)
);

#endif /* _TRACE_ANDROID_FS_H */

/* This part must be outside protection */
#include <trace/define_trace.h>

#ifndef ANDROID_FSTRACE_GET_PATHNAME
#define ANDROID_FSTRACE_GET_PATHNAME

/* Sizes an on-stack array, so careful if sizing this up ! */
#define MAX_TRACE_PATHBUF_LEN	256

static inline char *
android_fstrace_get_pathname(char *buf, int buflen, struct inode *inode)
{
	char *path;
	struct dentry *d;

	/*
	 * d_obtain_alias() will either iput() if it locates an existing
	 * dentry or transfer the reference to the new dentry created.
	 * So get an extra reference here.
	 */
	ihold(inode);
	d = d_obtain_alias(inode);
	if (likely(!IS_ERR(d))) {
		path = dentry_path_raw(d, buf, buflen);
		if (unlikely(IS_ERR(path))) {
			strcpy(buf, "ERROR");
			path = buf;
		}
		dput(d);
	} else {
		strcpy(buf, "ERROR");
		path = buf;
	}
	return path;
}
#endif
