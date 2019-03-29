# BoardConfig.mk
#
# Product-specific compile-time definitions.
#

# same as mips except HAL
include device/generic/mips/BoardConfig.mk

ifeq ($(HOST_OS),linux)
  WITH_DEXPREOPT := true
endif
