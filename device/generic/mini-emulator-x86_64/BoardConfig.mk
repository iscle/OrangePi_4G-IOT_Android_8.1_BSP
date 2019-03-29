# BoardConfig.mk
#
# Product-specific compile-time definitions.
#

# same as x86 except HAL
include device/generic/x86_64/BoardConfig.mk

# Build OpenGLES emulation libraries
BUILD_EMULATOR := true
BUILD_EMULATOR_OPENGL := true
BUILD_EMULATOR_OPENGL_DRIVER := true

# share the same one across all mini-emulators
BOARD_EGL_CFG := device/generic/goldfish-opengl/system/egl/egl.cfg

