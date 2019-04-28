#!/usr/bin/python
#
# Copyright 2016 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Partial implementation of xfrm netlink code and socket options."""

# pylint: disable=g-bad-todo

import os
from socket import *  # pylint: disable=wildcard-import

import cstruct
import netlink

# Base netlink constants. See include/uapi/linux/netlink.h.
NETLINK_XFRM = 6

# Netlink constants. See include/uapi/linux/xfrm.h.
# Message types.
XFRM_MSG_NEWSA = 16
XFRM_MSG_DELSA = 17
XFRM_MSG_GETSA = 18
XFRM_MSG_NEWPOLICY = 19
XFRM_MSG_DELPOLICY = 20
XFRM_MSG_GETPOLICY = 21
XFRM_MSG_ALLOCSPI = 22
XFRM_MSG_ACQUIRE = 23
XFRM_MSG_EXPIRE = 24
XFRM_MSG_UPDPOLICY = 25
XFRM_MSG_UPDSA = 26
XFRM_MSG_POLEXPIRE = 27
XFRM_MSG_FLUSHSA = 28
XFRM_MSG_FLUSHPOLICY = 29
XFRM_MSG_NEWAE = 30
XFRM_MSG_GETAE = 31
XFRM_MSG_REPORT = 32
XFRM_MSG_MIGRATE = 33
XFRM_MSG_NEWSADINFO = 34
XFRM_MSG_GETSADINFO = 35
XFRM_MSG_NEWSPDINFO = 36
XFRM_MSG_GETSPDINFO = 37
XFRM_MSG_MAPPING = 38

# Attributes.
XFRMA_UNSPEC = 0
XFRMA_ALG_AUTH = 1
XFRMA_ALG_CRYPT = 2
XFRMA_ALG_COMP = 3
XFRMA_ENCAP = 4
XFRMA_TMPL = 5
XFRMA_SA = 6
XFRMA_POLICY = 7
XFRMA_SEC_CTX = 8
XFRMA_LTIME_VAL = 9
XFRMA_REPLAY_VAL = 10
XFRMA_REPLAY_THRESH = 11
XFRMA_ETIMER_THRESH = 12
XFRMA_SRCADDR = 13
XFRMA_COADDR = 14
XFRMA_LASTUSED = 15
XFRMA_POLICY_TYPE = 16
XFRMA_MIGRATE = 17
XFRMA_ALG_AEAD = 18
XFRMA_KMADDRESS = 19
XFRMA_ALG_AUTH_TRUNC = 20
XFRMA_MARK = 21
XFRMA_TFCPAD = 22
XFRMA_REPLAY_ESN_VAL = 23
XFRMA_SA_EXTRA_FLAGS = 24
XFRMA_PROTO = 25
XFRMA_ADDRESS_FILTER = 26
XFRMA_PAD = 27

# Other netlink constants. See include/uapi/linux/xfrm.h.

# Directions.
XFRM_POLICY_IN = 0
XFRM_POLICY_OUT = 1
XFRM_POLICY_FWD = 2
XFRM_POLICY_MASK = 3

# Policy sharing.
XFRM_SHARE_ANY     = 0  #  /* No limitations */
XFRM_SHARE_SESSION = 1  #  /* For this session only */
XFRM_SHARE_USER    = 2  #  /* For this user only */
XFRM_SHARE_UNIQUE  = 3  #  /* Use once */

# Modes.
XFRM_MODE_TRANSPORT = 0
XFRM_MODE_TUNNEL = 1
XFRM_MODE_ROUTEOPTIMIZATION = 2
XFRM_MODE_IN_TRIGGER = 3
XFRM_MODE_BEET = 4
XFRM_MODE_MAX = 5

# Actions.
XFRM_POLICY_ALLOW = 0
XFRM_POLICY_BLOCK = 1

# Flags.
XFRM_POLICY_LOCALOK = 1
XFRM_POLICY_ICMP = 2

# Data structure formats.
# These aren't constants, they're classes. So, pylint: disable=invalid-name
XfrmSelector = cstruct.Struct(
    "XfrmSelector", "=16s16sHHHHHBBBxxxiI",
    "daddr saddr dport dport_mask sport sport_mask "
    "family prefixlen_d prefixlen_s proto ifindex user")

XfrmLifetimeCfg = cstruct.Struct(
    "XfrmLifetimeCfg", "=QQQQQQQQ",
    "soft_byte hard_byte soft_packet hard_packet "
    "soft_add_expires hard_add_expires soft_use_expires hard_use_expires")

XfrmLifetimeCur = cstruct.Struct(
    "XfrmLifetimeCur", "=QQQQ", "bytes packets add_time use_time")

XfrmAlgo = cstruct.Struct("XfrmAlgo", "=64AI", "name key_len")

XfrmAlgoAuth = cstruct.Struct("XfrmAlgoAuth", "=64AII",
                              "name key_len trunc_len")

XfrmAlgoAead = cstruct.Struct("XfrmAlgoAead", "=64AII", "name key_len icv_len")

XfrmStats = cstruct.Struct(
    "XfrmStats", "=III", "replay_window replay integrity_failed")

XfrmId = cstruct.Struct("XfrmId", "=16sIBxxx", "daddr spi proto")

XfrmUserTmpl = cstruct.Struct(
    "XfrmUserTmpl", "=SHxx16sIBBBxIII",
    "id family saddr reqid mode share optional aalgos ealgos calgos",
    [XfrmId])

XfrmEncapTmpl = cstruct.Struct(
    "XfrmEncapTmpl", "=HHHxx16s", "type sport dport oa")

XfrmUsersaInfo = cstruct.Struct(
    "XfrmUsersaInfo", "=SS16sSSSIIHBBB7x",
    "sel id saddr lft curlft stats seq reqid family mode replay_window flags",
    [XfrmSelector, XfrmId, XfrmLifetimeCfg, XfrmLifetimeCur, XfrmStats])

XfrmUserSpiInfo = cstruct.Struct(
    "XfrmUserSpiInfo", "=SII", "info min max", [XfrmUsersaInfo])

XfrmUsersaId = cstruct.Struct(
    "XfrmUsersaInfo", "=16sIHBx", "daddr spi family proto")

XfrmUserpolicyInfo = cstruct.Struct(
    "XfrmUserpolicyInfo", "=SSSIIBBBBxxxx",
    "sel lft curlft priority index dir action flags share",
    [XfrmSelector, XfrmLifetimeCfg, XfrmLifetimeCur])

XfrmUsersaFlush = cstruct.Struct("XfrmUsersaFlush", "=B", "proto")

# Socket options. See include/uapi/linux/in.h.
IP_IPSEC_POLICY = 16
IP_XFRM_POLICY = 17
IPV6_IPSEC_POLICY = 34
IPV6_XFRM_POLICY = 35

# UDP encapsulation constants. See include/uapi/linux/udp.h.
UDP_ENCAP = 100
UDP_ENCAP_ESPINUDP_NON_IKE = 1
UDP_ENCAP_ESPINUDP = 2

_INF = 2 ** 64 -1
NO_LIFETIME_CFG = XfrmLifetimeCfg((_INF, _INF, _INF, _INF, 0, 0, 0, 0))
NO_LIFETIME_CUR = "\x00" * len(XfrmLifetimeCur)

# IPsec constants.
IPSEC_PROTO_ANY	= 255


def RawAddress(addr):
  """Converts an IP address string to binary format."""
  family = AF_INET6 if ":" in addr else AF_INET
  return inet_pton(family, addr)


def PaddedAddress(addr):
  """Converts an IP address string to binary format for InetDiagSockId."""
  padded = RawAddress(addr)
  if len(padded) < 16:
    padded += "\x00" * (16 - len(padded))
  return padded


class Xfrm(netlink.NetlinkSocket):
  """Netlink interface to xfrm."""

  FAMILY = NETLINK_XFRM
  DEBUG = False

  def __init__(self):
    super(Xfrm, self).__init__()

  def _GetConstantName(self, value, prefix):
    return super(Xfrm, self)._GetConstantName(__name__, value, prefix)

  def MaybeDebugCommand(self, command, flags, data):
    if "ALL" not in self.NL_DEBUG and "XFRM" not in self.NL_DEBUG:
      return

    if command == XFRM_MSG_GETSA:
      if flags & netlink.NLM_F_DUMP:
        struct_type = XfrmUsersaInfo
      else:
        struct_type = XfrmUsersaId
    elif command == XFRM_MSG_DELSA:
      struct_type = XfrmUsersaId
    elif command == XFRM_MSG_ALLOCSPI:
      struct_type = XfrmUserSpiInfo
    else:
      struct_type = None

    cmdname = self._GetConstantName(command, "XFRM_MSG_")
    if struct_type:
      print "%s %s" % (cmdname, str(self._ParseNLMsg(data, struct_type)))
    else:
      print "%s" % cmdname

  def _Decode(self, command, unused_msg, nla_type, nla_data):
    """Decodes netlink attributes to Python types."""
    name = self._GetConstantName(nla_type, "XFRMA_")

    if name in ["XFRMA_ALG_CRYPT", "XFRMA_ALG_AUTH"]:
      data = cstruct.Read(nla_data, XfrmAlgo)[0]
    elif name == "XFRMA_ALG_AUTH_TRUNC":
      data = cstruct.Read(nla_data, XfrmAlgoAuth)[0]
    elif name == "XFRMA_ENCAP":
      data = cstruct.Read(nla_data, XfrmEncapTmpl)[0]
    else:
      data = nla_data

    return name, data

  def AddSaInfo(self, selector, xfrm_id, saddr, lifetimes, reqid, family, mode,
                replay_window, flags, nlattrs):
    # The kernel ignores these on input.
    cur = "\x00" * len(XfrmLifetimeCur)
    stats = "\x00" * len(XfrmStats)
    seq = 0
    sa = XfrmUsersaInfo((selector, xfrm_id, saddr, lifetimes, cur, stats, seq,
                         reqid, family, mode, replay_window, flags))
    msg = sa.Pack() + nlattrs
    flags = netlink.NLM_F_REQUEST | netlink.NLM_F_ACK
    self._SendNlRequest(XFRM_MSG_NEWSA, msg, flags)

  def AddMinimalSaInfo(self, src, dst, spi, proto, mode, reqid,
                       encryption, encryption_key,
                       auth_trunc, auth_trunc_key, encap):
    selector = XfrmSelector("\x00" * len(XfrmSelector))
    xfrm_id = XfrmId((PaddedAddress(dst), spi, proto))
    family = AF_INET6 if ":" in dst else AF_INET
    nlattrs = self._NlAttr(XFRMA_ALG_CRYPT,
                           encryption.Pack() + encryption_key)
    nlattrs += self._NlAttr(XFRMA_ALG_AUTH_TRUNC,
                            auth_trunc.Pack() + auth_trunc_key)
    if encap is not None:
      nlattrs += self._NlAttr(XFRMA_ENCAP, encap.Pack())
    self.AddSaInfo(selector, xfrm_id, PaddedAddress(src), NO_LIFETIME_CFG,
                   reqid, family, mode, 4, 0, nlattrs)

  def DeleteSaInfo(self, daddr, spi, proto):
    # TODO: deletes take a mark as well.
    family = AF_INET6 if ":" in daddr else AF_INET
    usersa_id = XfrmUsersaId((PaddedAddress(daddr), spi, family, proto))
    flags = netlink.NLM_F_REQUEST | netlink.NLM_F_ACK
    self._SendNlRequest(XFRM_MSG_DELSA, usersa_id.Pack(), flags)

  def AllocSpi(self, dst, proto, min_spi, max_spi):
    """Allocate (reserve) an SPI.

    This sends an XFRM_MSG_ALLOCSPI message and returns the resulting
    XfrmUsersaInfo struct.
    """
    spi = XfrmUserSpiInfo("\x00" * len(XfrmUserSpiInfo))
    spi.min = min_spi
    spi.max = max_spi
    spi.info.id.daddr = PaddedAddress(dst)
    spi.info.id.proto = proto

    msg = spi.Pack()
    flags = netlink.NLM_F_REQUEST
    self._SendNlRequest(XFRM_MSG_ALLOCSPI, msg, flags)
    # Read the response message.
    data = self._Recv()
    nl_hdr, data = cstruct.Read(data, netlink.NLMsgHdr)
    if nl_hdr.type == XFRM_MSG_NEWSA:
      return XfrmUsersaInfo(data)
    if nl_hdr.type == netlink.NLMSG_ERROR:
      error = netlink.NLMsgErr(data).error
      raise IOError(error, os.strerror(-error))
    raise ValueError("Unexpected netlink message type: %d" % nl_hdr.type)

  def DumpSaInfo(self):
    return self._Dump(XFRM_MSG_GETSA, None, XfrmUsersaInfo, "")

  def FindSaInfo(self, spi):
    sainfo = [sa for sa, attrs in self.DumpSaInfo() if sa.id.spi == spi]
    return sainfo[0] if sainfo else None

  def FlushSaInfo(self):
    usersa_flush = XfrmUsersaFlush((IPSEC_PROTO_ANY,))
    flags = netlink.NLM_F_REQUEST | netlink.NLM_F_ACK
    self._SendNlRequest(XFRM_MSG_FLUSHSA, usersa_flush.Pack(), flags)


if __name__ == "__main__":
  x = Xfrm()
  print x.DumpSaInfo()
