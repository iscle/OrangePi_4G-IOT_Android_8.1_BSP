package mtkDebugFW

import (
	"android/soong/android"
	"android/soong/cc"
	"android/soong/android/mediatek"

	"github.com/google/blueprint/proptools"
)

func mtkDebugFWDefaults(ctx android.LoadHookContext) {
	type props struct {
		Target struct {
			Android_arm struct {
				Cflags []string
			}
			Android_arm64 struct {
				Cflags []string
			}
		}
	}
	p := &props{}
	if (mediatek.GetFeature("MTK_USER_SPACE_DEBUG_FW") == "yes") &&
		proptools.Bool(ctx.AConfig().ProductVariables.Eng) {
		p.Target.Android_arm.Cflags = append(p.Target.Android_arm.Cflags, "-marm")
		p.Target.Android_arm.Cflags = append(p.Target.Android_arm.Cflags, "-fno-omit-frame-pointer")
	}
	ctx.AppendProperties(p)
}

func init() {
	android.RegisterModuleType("mtk_debug_fw_defaults", mtkDebugFWDefaultsFactory)
}

func mtkDebugFWDefaultsFactory() android.Module {
	module := cc.DefaultsFactory()
	android.AddLoadHook(module, mtkDebugFWDefaults)
	return module
}
