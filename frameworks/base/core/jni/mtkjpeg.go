package mtkJpegEnhanceArt
import (
	"android/soong/android"
	"android/soong/cc"
	"android/soong/android/mediatek"
//	"github.com/google/blueprint"
)

func mtkJpegEnhanceArtDefaults(ctx android.LoadHookContext) {
	type props struct {
		Target struct {
			Android struct {
				Cflags []string
				Include_dirs []string
				Srcs []string
			}
		}
	}
	p := &props{}
	//featureValue := android.MtkFeatureValues

	var includeDirs []string
	var srcs []string

	if mediatek.GetFeature("MTK_JPEG_HW_RESIZER_TYPE") == "HW_RESIZER_TYPE_2"  {
		p.Target.Android.Cflags = append(p.Target.Android.Cflags, "-DMTK_IMAGE_ENABLE_PQ_FOR_JPEG")
		p.Target.Android.Cflags = append(p.Target.Android.Cflags, "-DMTK_SKIA_MULTI_THREAD_JPEG_REGION")

		includeDirs = append(includeDirs, "external/skia/include/mtk")

		srcs = append(srcs, "android/graphics/mtk/BitmapRegionDecoder.cpp")
	} else {
		srcs = append(srcs, "android/graphics/BitmapRegionDecoder.cpp")
	}

	p.Target.Android.Include_dirs = includeDirs
	p.Target.Android.Srcs = srcs

	ctx.AppendProperties(p)
}

func init() {
	android.RegisterModuleType("mtk_jpeg_enhance_art_defaults", mtkJpegEnhanceArtDefaultsFactory)
}

func mtkJpegEnhanceArtDefaultsFactory() (android.Module) {
	module := cc.DefaultsFactory()
	android.AddLoadHook(module, mtkJpegEnhanceArtDefaults)
	return module
}