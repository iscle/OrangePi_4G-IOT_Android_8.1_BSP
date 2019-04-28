/*
 * Driver interaction with Linux nl80211/cfg80211
 * Copyright (c) 2002-2015, Jouni Malinen <j@w1.fi>
 * Copyright (c) 2003-2004, Instant802 Networks, Inc.
 * Copyright (c) 2005-2006, Devicescape Software, Inc.
 * Copyright (c) 2007, Johannes Berg <johannes@sipsolutions.net>
 * Copyright (c) 2009-2010, Atheros Communications
 * Copyright (c) 2017, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE
 *
 */

#include <errno.h>
#include <netlink/genl/family.h>
#include <netlink/genl/ctrl.h>
#include <linux/pkt_sched.h>
#include <unistd.h>
#include <log/log.h>
#include "cld80211_lib.h"

#undef LOG_TAG
#define LOG_TAG "CLD80211"
#define SOCK_BUF_SIZE (256*1024)

struct family_data {
	const char *group;
	int id;
};


static struct nl_sock * create_nl_socket(int protocol)
{
	struct nl_sock *sock;

	sock = nl_socket_alloc();
	if (sock == NULL) {
		ALOGE("%s: Failed to create NL socket, err: %d",
		      getprogname(), errno);
		return NULL;
	}

	if (nl_connect(sock, protocol)) {
		ALOGE("%s: Could not connect sock, err: %d",
		      getprogname(), errno);
		nl_socket_free(sock);
		return NULL;
	}

	return sock;
}


static int init_exit_sockets(struct cld80211_ctx *ctx)
{
	ctx->exit_sockets[0] = -1;
	ctx->exit_sockets[1] = -1;
	if (socketpair(AF_UNIX, SOCK_STREAM, 0, &ctx->exit_sockets[0]) == -1) {
		ALOGE("%s: Failed to create exit socket pair", getprogname());
		return -1;
	}
	ALOGI("%s: initialized exit socket pair", getprogname());

	return 0;
}


static void cleanup_exit_sockets(struct cld80211_ctx *ctx)
{
	if (ctx->exit_sockets[0] >= 0) {
		close(ctx->exit_sockets[0]);
		ctx->exit_sockets[0] = -1;
	}

	if (ctx->exit_sockets[1] >= 0) {
		close(ctx->exit_sockets[1]);
		ctx->exit_sockets[1] = -1;
	}
}


void exit_cld80211_recv(struct cld80211_ctx *ctx)
{
	if (!ctx) {
		ALOGE("%s: ctx is NULL: %s", getprogname(), __func__);
		return;
	}
	TEMP_FAILURE_RETRY(write(ctx->exit_sockets[0], "E", 1));
	ALOGI("%s: Sent msg on exit sock to unblock poll()", getprogname());
}


/* Event handlers */
static int response_handler(struct nl_msg *msg, void *arg)
{
	UNUSED(msg);
	UNUSED(arg);
	ALOGI("%s: Received nlmsg response: no callback registered;drop it",
	      getprogname());

	return NL_SKIP;
}


static int ack_handler(struct nl_msg *msg, void *arg)
{
	int *err = (int *)arg;
	*err = 0;
	UNUSED(msg);
	return NL_STOP;
}


static int finish_handler(struct nl_msg *msg, void *arg)
{
	int *ret = (int *)arg;
	*ret = 0;
	UNUSED(msg);
	return NL_SKIP;
}


static int error_handler(struct sockaddr_nl *nla, struct nlmsgerr *err,
			 void *arg)
{
	int *ret = (int *)arg;
	*ret = err->error;

	UNUSED(nla);
	ALOGE("%s: error_handler received : %d", getprogname(), err->error);
	return NL_SKIP;
}


static int no_seq_check(struct nl_msg *msg, void *arg)
{
	UNUSED(msg);
	UNUSED(arg);
	return NL_OK;
}


int cld80211_recv_msg(struct nl_sock *sock, struct nl_cb *cb)
{
	if (!sock || !cb) {
		ALOGE("%s: %s is NULL", getprogname(), sock?"cb":"sock");
		return -EINVAL;
	}

	int res = nl_recvmsgs(sock, cb);
	if(res)
		ALOGE("%s: Error :%d while reading nl msg , err: %d",
		      getprogname(), res, errno);
	return res;
}


static void cld80211_handle_event(int events, struct nl_sock *sock,
				  struct nl_cb *cb)
{
	if (events & POLLERR) {
		ALOGE("%s: Error reading from socket", getprogname());
		cld80211_recv_msg(sock, cb);
	} else if (events & POLLHUP) {
		ALOGE("%s: Remote side hung up", getprogname());
	} else if (events & POLLIN) {
		cld80211_recv_msg(sock, cb);
	} else {
		ALOGE("%s: Unknown event - %0x", getprogname(), events);
	}
}


static int family_handler(struct nl_msg *msg, void *arg)
{
	struct family_data *res = arg;
	struct nlattr *tb[CTRL_ATTR_MAX + 1];
	struct genlmsghdr *gnlh = nlmsg_data(nlmsg_hdr(msg));
	struct nlattr *mcgrp;
	int i;

	nla_parse(tb, CTRL_ATTR_MAX, genlmsg_attrdata(gnlh, 0),
			genlmsg_attrlen(gnlh, 0), NULL);
	if (!tb[CTRL_ATTR_MCAST_GROUPS])
		return NL_SKIP;

	nla_for_each_nested(mcgrp, tb[CTRL_ATTR_MCAST_GROUPS], i) {
		struct nlattr *tb2[CTRL_ATTR_MCAST_GRP_MAX + 1];
		nla_parse(tb2, CTRL_ATTR_MCAST_GRP_MAX, nla_data(mcgrp),
				nla_len(mcgrp), NULL);

		if (!tb2[CTRL_ATTR_MCAST_GRP_NAME] ||
			!tb2[CTRL_ATTR_MCAST_GRP_ID] ||
			strncmp(nla_data(tb2[CTRL_ATTR_MCAST_GRP_NAME]),
				   res->group,
				   nla_len(tb2[CTRL_ATTR_MCAST_GRP_NAME])) != 0)
			continue;
		res->id = nla_get_u32(tb2[CTRL_ATTR_MCAST_GRP_ID]);
		break;
	};

	return NL_SKIP;
}


static int get_multicast_id(struct cld80211_ctx *ctx, const char *group, bool sync_driver)
{
	struct family_data res = { group, -ENOENT };
	struct nl_msg *nlmsg = nlmsg_alloc();

	if (!nlmsg) {
		return -1;
	}

	genlmsg_put(nlmsg, 0, 0, ctx->nlctrl_familyid, 0, 0,
	            CTRL_CMD_GETFAMILY, 0);
	nla_put_string(nlmsg, CTRL_ATTR_FAMILY_NAME, "cld80211");

	if (sync_driver == true) {
		cld80211_send_recv_msg(ctx, nlmsg, family_handler, &res);
		ALOGI("%s: nlctrl family id: %d group: %s mcast_id: %d", getprogname(),
				ctx->nlctrl_familyid, group, res.id);
	}
	nlmsg_free(nlmsg);
	return res.id;
}


int cld80211_add_mcast_group(struct cld80211_ctx *ctx, const char* mcgroup)
{
	if (!ctx || !mcgroup) {
		ALOGE("%s: ctx/mcgroup is NULL: %s", getprogname(), __func__);
		return 0;
	}
	int id = get_multicast_id(ctx, mcgroup, true);
	if (id < 0) {
		ALOGE("%s: Could not find group %s, errno: %d id: %d",
		      getprogname(), mcgroup, errno, id);
		return id;
	}

	int ret = nl_socket_add_membership(ctx->sock, id);
	if (ret < 0) {
		ALOGE("%s: Could not add membership to group %s, errno: %d",
		      getprogname(), mcgroup, errno);
	}

	return ret;
}


int cld80211_remove_mcast_group(struct cld80211_ctx *ctx, const char* mcgroup)
{
	if (!ctx || !mcgroup) {
		ALOGE("%s: ctx/mcgroup is NULL: %s", getprogname(), __func__);
		return 0;
	}
	int id = get_multicast_id(ctx, mcgroup, false);
	if (id < 0) {
		ALOGE("%s: Could not find group %s, errno: %d id: %d",
		      getprogname(), mcgroup, errno, id);
		return id;
	}

	int ret = nl_socket_drop_membership(ctx->sock, id);
	if (ret < 0) {
		ALOGE("%s: Could not drop membership from group %s, errno: %d,"
		      " ret: %d", getprogname(), mcgroup, errno, ret);
		return ret;
	}

	return 0;
}


struct nl_msg *cld80211_msg_alloc(struct cld80211_ctx *ctx, int cmd,
				  struct nlattr **nla_data, int pid)
{
	struct nl_msg *nlmsg;

	if (!ctx || !nla_data) {
		ALOGE("%s: ctx is null: %s", getprogname(), __func__);
		return NULL;
	}

	nlmsg = nlmsg_alloc();
	if (nlmsg == NULL) {
		ALOGE("%s: Out of memory", getprogname());
		return NULL;
	}

	genlmsg_put(nlmsg, pid, /* seq = */ 0, ctx->netlink_familyid,
			0, 0, cmd, /* version = */ 0);

	*nla_data = nla_nest_start(nlmsg, CLD80211_ATTR_VENDOR_DATA);
	if (!nla_data)
		goto cleanup;

	return nlmsg;

cleanup:
	if (nlmsg)
		nlmsg_free(nlmsg);
	return NULL;
}


int cld80211_send_msg(struct cld80211_ctx *ctx, struct nl_msg *nlmsg)
{
	int err;

	if (!ctx || !ctx->sock || !nlmsg) {
		ALOGE("%s: Invalid data from client", getprogname());
		return -EINVAL;
	}

	err = nl_send_auto_complete(ctx->sock, nlmsg);  /* send message */
	if (err < 0) {
		ALOGE("%s: failed to send msg: %d", getprogname(), err);
		return err;
	}

	return 0;
}


int cld80211_send_recv_msg(struct cld80211_ctx *ctx, struct nl_msg *nlmsg,
			   int (*valid_handler)(struct nl_msg *, void *),
			   void *valid_data)
{
	int err;

	if (!ctx || !ctx->sock || !nlmsg) {
		ALOGE("%s: Invalid data from client", getprogname());
		return -EINVAL;
	}

	struct nl_cb *cb = nl_cb_alloc(NL_CB_DEFAULT);
	if (!cb)
		return -ENOMEM;

	err = nl_send_auto_complete(ctx->sock, nlmsg);  /* send message */
	if (err < 0)
		goto out;

	err = 1;

	nl_cb_set(cb, NL_CB_SEQ_CHECK, NL_CB_CUSTOM, no_seq_check, NULL);
	nl_cb_err(cb, NL_CB_CUSTOM, error_handler, &err);
	nl_cb_set(cb, NL_CB_FINISH, NL_CB_CUSTOM, finish_handler, &err);
	nl_cb_set(cb, NL_CB_ACK, NL_CB_CUSTOM, ack_handler, &err);

	if (valid_handler)
		nl_cb_set(cb, NL_CB_VALID, NL_CB_CUSTOM,
			  valid_handler, valid_data);
	else
		nl_cb_set(cb, NL_CB_VALID, NL_CB_CUSTOM,
			  response_handler, valid_data);

	while (err > 0) {    /* wait for reply */
		int res = nl_recvmsgs(ctx->sock, cb);
		if (res) {
			ALOGE("%s: cld80211: nl_recvmsgs failed: %d",
			      getprogname(), res);
		}
	}
out:
	nl_cb_put(cb);
	return err;
}


int cld80211_recv(struct cld80211_ctx *ctx, int timeout, bool recv_multi_msg,
		  int (*valid_handler)(struct nl_msg *, void *),
		  void *cbctx)
{
	struct pollfd pfd[2];
	struct nl_cb *cb;
	int err;

	if (!ctx || !ctx->sock || !valid_handler) {
		ALOGE("%s: Invalid data from client", getprogname());
		return -EINVAL;
	}

	cb = nl_cb_alloc(NL_CB_DEFAULT);
	if (!cb)
		return -ENOMEM;

	memset(&pfd[0], 0, 2*sizeof(struct pollfd));

	err = 1;

	nl_cb_err(cb, NL_CB_CUSTOM, error_handler, &err);
	nl_cb_set(cb, NL_CB_FINISH, NL_CB_CUSTOM, finish_handler, &err);
	nl_cb_set(cb, NL_CB_ACK, NL_CB_CUSTOM, ack_handler, &err);
	nl_cb_set(cb, NL_CB_SEQ_CHECK, NL_CB_CUSTOM, no_seq_check, NULL);
	nl_cb_set(cb, NL_CB_VALID, NL_CB_CUSTOM, valid_handler, cbctx);

	pfd[0].fd = nl_socket_get_fd(ctx->sock);
	pfd[0].events = POLLIN;

	pfd[1].fd = ctx->exit_sockets[1];
	pfd[1].events = POLLIN;

	do {
		pfd[0].revents = 0;
		pfd[1].revents = 0;
		int result = poll(pfd, 2, timeout);
		if (result < 0) {
			ALOGE("%s: Error polling socket", getprogname());
		} else if (pfd[0].revents & (POLLIN | POLLHUP | POLLERR)) {
			cld80211_handle_event(pfd[0].revents, ctx->sock, cb);
			if (!recv_multi_msg)
				break;
		} else {
			ALOGI("%s: Exiting poll", getprogname());
			break;
		}
	} while (1);

	nl_cb_put(cb);
	return 0;
}


struct cld80211_ctx * cld80211_init()
{
	struct cld80211_ctx *ctx;

	ctx = (struct cld80211_ctx *)malloc(sizeof(struct cld80211_ctx));
	if (ctx == NULL) {
		ALOGE("%s: Failed to alloc cld80211_ctx", getprogname());
		return NULL;
	}
	memset(ctx, 0, sizeof(struct cld80211_ctx));

	ctx->sock = create_nl_socket(NETLINK_GENERIC);
	if (ctx->sock == NULL) {
		ALOGE("%s: Failed to create socket port", getprogname());
		goto cleanup;
	}

	/* Set the socket buffer size */
	if (nl_socket_set_buffer_size(ctx->sock, SOCK_BUF_SIZE , 0) < 0) {
		ALOGE("%s: Could not set nl_socket RX buffer size for sock: %s",
		      getprogname(), strerror(errno));
		/* continue anyway with the default (smaller) buffer */
	}

	ctx->netlink_familyid = genl_ctrl_resolve(ctx->sock, "cld80211");
	if (ctx->netlink_familyid < 0) {
		ALOGE("%s: Could not resolve cld80211 familty id",
		      getprogname());
		goto cleanup;
	}

	ctx->nlctrl_familyid = genl_ctrl_resolve(ctx->sock, "nlctrl");
	if (ctx->nlctrl_familyid < 0) {
		ALOGE("%s: net link family nlctrl is not present: %d err:%d",
			getprogname(), ctx->nlctrl_familyid, errno);
		goto cleanup;
	}


	if (init_exit_sockets(ctx) != 0) {
		ALOGE("%s: Failed to initialize exit sockets", getprogname());
		goto cleanup;
	}

	return ctx;
cleanup:
	if (ctx->sock) {
		nl_socket_free(ctx->sock);
	}
	free (ctx);
	return NULL;
}


void cld80211_deinit(struct cld80211_ctx *ctx)
{
	if (!ctx || !ctx->sock) {
		ALOGE("%s: ctx/sock is NULL", getprogname());
		return;
	}
	nl_socket_free(ctx->sock);
	cleanup_exit_sockets(ctx);
	free (ctx);
}
