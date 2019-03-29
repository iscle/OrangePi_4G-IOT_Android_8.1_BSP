ifneq (true,$(strip $(TARGET_NO_KERNEL)))

MTK_KERNEL_MODULES :=

-include $(MTK_TARGET_PROJECT_FOLDER)/kernel_modules.mk

$(MTK_KERNEL_MODULES): kernel ;

endif # TARGET_NO_KERNEL
