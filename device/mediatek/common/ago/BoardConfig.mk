ifeq (yes,$(strip $(MTK_GMO_RAM_OPTIMIZE)))
    #A-GO
    MALLOC_SVELTE := true
endif
