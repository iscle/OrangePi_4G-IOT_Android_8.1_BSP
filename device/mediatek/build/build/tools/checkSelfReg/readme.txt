===================================================================================
Script : ChangeSelfReg.py
This is a script used to check self register options in customer platform.
If OPTR_SPEC_SEG_DEF = OP09_SPEC0212_SEGC and CMCC_LIGHT_CUST_SUPPORT = yes,
turn off:MTK_CT4GREG_APP=no and MTK_DEVREG_APP=no
===================================================================================

Usage:
 python  ./ChangeSelfReg.py  customer-projectConfig-file-path

Example:
 python  ./ChangeSelfReg.py  \\..\alps\device\[Customer]\[ProjectName]\ProjectConfig.mk

