SUBDIRS = src

#Uncomment the following line if building documentation using gtkdoc
#SUBDIRS += docs

pkgconfigdir = $(libdir)/pkgconfig
pkgconfig_DATA=mixvideo.pc mixvideoint.pc
EXTRA_DIST = autogen.sh mixvideo.spec 
DISTCHECK_CONFIGURE_FLAGS=--enable-gtk-doc
