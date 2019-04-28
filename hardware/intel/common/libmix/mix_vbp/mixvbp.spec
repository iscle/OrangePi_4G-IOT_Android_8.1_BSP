# INTEL CONFIDENTIAL
# Copyright 2009 Intel Corporation All Rights Reserved. 
# The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.
#
# No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.

Summary: MIX Video Bitstream Parser
Name: mixvbp
Version: 0.1.15
Release: 1
Source0: %{name}-%{version}.tar.gz
NoSource: 0
License: Proprietary
Group: System Environment/Libraries
BuildRoot: %{_tmppath}/%{name}-root
ExclusiveArch: i586

%description
MIX Video Bitstream Parser is an user library interface for various video format bitstream parsing

%package devel
Summary: Libraries include files
Group: Development/Libraries
Requires: %{name} = %{version}

%description devel
The %{name}-devel package contains the header files and static libraries for building applications which use %{name}.

%prep
%setup -q
%build
./autogen.sh
./configure --prefix=%{_prefix}
make
%install
make DESTDIR=$RPM_BUILD_ROOT install
rm -f $RPM_BUILD_ROOT/%{_prefix}/lib/libmixvbp_mpeg2*

%clean
rm -rf $RPM_BUILD_ROOT
%files
%defattr(-,root,root)
%{_prefix}/lib/libmixvbp.so*
%{_prefix}/lib/libmixvbp_vc1.so*
%{_prefix}/lib/libmixvbp_h264.so*
%{_prefix}/lib/libmixvbp_mpeg4.so*

%files devel
%defattr(-,root,root)
%{_prefix}/include
%{_prefix}/lib/*.la
%{_prefix}/lib/pkgconfig/mixvbp.pc
