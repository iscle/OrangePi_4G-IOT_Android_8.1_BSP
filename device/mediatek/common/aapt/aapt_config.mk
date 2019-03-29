# Setting aapt config by lcm height and width if it is not defined
ifeq (,$(strip $($eval $(PRODUCT_AAPT_PREF_CONFIG))))
  ifeq ($(LCM_HEIGHT)_$(LCM_WIDTH),320_240)
    PRODUCT_AAPT_PREF_CONFIG := ldpi
  else ifeq ($(LCM_HEIGHT)_$(LCM_WIDTH),400_240)
    PRODUCT_AAPT_PREF_CONFIG := ldpi
  else ifeq ($(LCM_HEIGHT)_$(LCM_WIDTH),432_240)
    PRODUCT_AAPT_PREF_CONFIG := ldpi
  else ifeq ($(LCM_HEIGHT)_$(LCM_WIDTH),480_320)
    PRODUCT_AAPT_PREF_CONFIG := mdpi
  else ifeq ($(LCM_HEIGHT)_$(LCM_WIDTH),640_480)
    PRODUCT_AAPT_PREF_CONFIG := mdpi
  else ifeq ($(LCM_HEIGHT)_$(LCM_WIDTH),800_480)
    PRODUCT_AAPT_PREF_CONFIG := hdpi
  else ifeq ($(LCM_HEIGHT)_$(LCM_WIDTH),854_480)
    PRODUCT_AAPT_PREF_CONFIG := hdpi
  else ifeq ($(LCM_HEIGHT)_$(LCM_WIDTH),960_540)
    PRODUCT_AAPT_PREF_CONFIG := hdpi
  else ifeq ($(LCM_HEIGHT)_$(LCM_WIDTH),1024_600)
    PRODUCT_AAPT_PREF_CONFIG := hdpi
  else ifeq ($(LCM_HEIGHT)_$(LCM_WIDTH),1280_720)
    PRODUCT_AAPT_PREF_CONFIG := xhdpi
  else ifeq ($(LCM_HEIGHT)_$(LCM_WIDTH),1280_768)
    PRODUCT_AAPT_PREF_CONFIG := xhdpi
  else ifeq ($(LCM_HEIGHT)_$(LCM_WIDTH),1280_800)
    PRODUCT_AAPT_PREF_CONFIG := xhdpi
  else ifeq ($(LCM_HEIGHT)_$(LCM_WIDTH),1920_1080)
    PRODUCT_AAPT_PREF_CONFIG := xxhdpi
  else ifeq ($(LCM_HEIGHT)_$(LCM_WIDTH),1920_1200)
    PRODUCT_AAPT_PREF_CONFIG := xxhdpi
  else ifeq ($(LCM_HEIGHT)_$(LCM_WIDTH),2560_1440)
    PRODUCT_AAPT_PREF_CONFIG := xxxhdpi
  else ifeq ($(LCM_HEIGHT)_$(LCM_WIDTH),2560_1600)
    PRODUCT_AAPT_PREF_CONFIG := xxxhdpi
  endif
endif

# Slim rom use sw599dp for rom optimization
ifeq (yes,$(strip $(MTK_GMO_ROM_OPTIMIZE)))
  PRODUCT_AAPT_CONFIG += sw599dp
endif
