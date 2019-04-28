Summary: MIX Common
Name: mixcommon
Version: 0.1.7
Release: 1
Source0: %{name}-%{version}.tar.gz
NoSource: 0
License: Proprietary
Group: System Environment/Libraries
BuildRoot: %{_tmppath}/%{name}-root
ExclusiveArch: i586

%description
MIX Common contains common classes, datatype, header files used by other MIX components

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
rm -rf $RPM_BUILD_ROOT
make DESTDIR=$RPM_BUILD_ROOT install
%clean
rm -rf $RPM_BUILD_ROOT
%files
%defattr(-,root,root)
%{_prefix}/lib/*.so*

%files devel
%defattr(-,root,root)
%{_prefix}/include
%{_prefix}/lib/*.la
%{_prefix}/lib/pkgconfig/mixcommon.pc
%doc COPYING
