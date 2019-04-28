### all of the standard pc files we need to generate
pcfiles = mixaudio.pc

all-local: $(pcfiles)

pkgconfigdir = $(libdir)/pkgconfig
pkgconfig_DATA = $(pcfiles)

EXTRA_DIST = mixaudio.pc.in	

CLEANFILES = $(pcfiles)
