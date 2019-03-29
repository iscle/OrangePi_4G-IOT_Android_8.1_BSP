LOCAL_PATH:= $(call my-dir)

## sources have been created from Drystone-2.1.sh with below command:
# ./Drystone-2.1.sh
# sed -i 's/printf ("  Ptr_Comp:          %d\\n", (int) /printf ("  Ptr_Comp:          %p\\n", /g' dhry_1.c
# sed -i 's,^} /\* Proc_,return 0; } /\* Proc_,g' *.c

include $(CLEAR_VARS)
LOCAL_MODULE := dhry
LOCAL_SRC_FILES := dhry_1.c dhry_2.c
LOCAL_CFLAGS := -O3 -fno-inline-functions -DMSC_CLOCK -DCLK_TCK=1000000
LOCAL_CFLAGS += -Wno-return-type -Wno-implicit-function-declaration -Wno-implicit-int
# Include both the 32 and 64 bit versions
LOCAL_MULTILIB := both
LOCAL_MODULE_STEM_32 := $(LOCAL_MODULE)32
LOCAL_MODULE_STEM_64 := $(LOCAL_MODULE)64
LOCAL_COMPATIBILITY_SUITE := cts vts general-tests
include $(BUILD_EXECUTABLE)
