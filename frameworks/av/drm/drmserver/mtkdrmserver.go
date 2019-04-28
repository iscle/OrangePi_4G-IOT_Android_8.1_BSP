package mtkDrmServer
import (
	"android/soong/android"
	"android/soong/cc"
	"android/soong/android/mediatek"
)
func mtkDrmServerDefaults(ctx android.LoadHookContext) {
	type props struct {
		Target struct {
			Android struct {
				Cflags []string
				Include_dirs []string
				Legacy_shared_libs []string
			}
		}
	}
	p := &props{}

	var includeDirs []string
	var legacysharedlibs []string

	if mediatek.GetFeature("MTK_DRM_APP") == "yes" {
			if mediatek.GetFeature("MTK_OMADRM_SUPPORT") == "yes" {
					p.Target.Android.Cflags = append(p.Target.Android.Cflags, "-DMTK_OMA_DRM_SUPPORT")
					includeDirs = append(includeDirs, "vendor/mediatek/proprietary/frameworks/av/drm/include")
					legacysharedlibs = append(legacysharedlibs, "libdrmmtkutil")
			} else if mediatek.GetFeature("MTK_CTA_SET") == "yes" {
						p.Target.Android.Cflags = append(p.Target.Android.Cflags, "-DMTK_CTA_DRM_SUPPORT")
						includeDirs = append(includeDirs, "vendor/mediatek/proprietary/frameworks/av/drm/include")
						legacysharedlibs = append(legacysharedlibs, "libdrmmtkutil")
			}
	} else if mediatek.GetFeature("MTK_WVDRM_SUPPORT") == "yes" {
			p.Target.Android.Cflags = append(p.Target.Android.Cflags, "-DMTK_WV_DRM_SUPPORT")
			includeDirs = append(includeDirs, "vendor/mediatek/proprietary/frameworks/av/drm/include")
			legacysharedlibs = append(legacysharedlibs, "libdrmmtkutil")
}

	p.Target.Android.Include_dirs = includeDirs
	p.Target.Android.Legacy_shared_libs = legacysharedlibs
	ctx.AppendProperties(p)
}
func init() {
	android.RegisterModuleType("mtk_drm_server_defaults", mtkDrmServerDefaultsFactory)
}
func mtkDrmServerDefaultsFactory() (android.Module) {
	module := cc.DefaultsFactory()
	android.AddLoadHook(module, mtkDrmServerDefaults)
	return module
}
