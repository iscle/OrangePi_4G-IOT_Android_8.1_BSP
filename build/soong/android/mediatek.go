package android

import (
	"path/filepath"

	"android/soong/android/mediatek"
)

func (c *config) MtkTargetProjectName() string {
	if mediatek.MtkTargetProject != "" {
		return mediatek.MtkTargetProject
	}
	return c.DeviceName()
}

func PathForLegacyModuleIntermediate(ctx ModuleContext, class, name, common, stem string) Path {
	var builtPaths []string
	if ctx.Device() {
		builtPaths = []string{"target"}
	} else {
		builtPaths = []string{"host"}
	}
	if common != "" {
		builtPaths = append(builtPaths, "common", "obj", class, name+"_intermediates")
	} else {
		if ctx.Device() {
			builtPaths = append(builtPaths, "product", ctx.AConfig().MtkTargetProjectName())
			if ctx.PrimaryArch() {
				builtPaths = append(builtPaths, "obj")
			} else {
				builtPaths = append(builtPaths, "obj"+"_"+ctx.Arch().ArchType.String())
			}
		} else {
			builtPaths = append(builtPaths, ctx.Os().String()+"-x86")
			if ctx.PrimaryArch() {
				builtPaths = append(builtPaths, "obj")
			} else {
				builtPaths = append(builtPaths, "obj"+"32")
			}
		}
		builtPaths = append(builtPaths, class, name+"_intermediates")
	}
	if stem != "" {
		builtPaths = append(builtPaths, stem)
	}
	return OutputPath{basePath{filepath.Join("..", filepath.Join(builtPaths...)), pathConfig(ctx), ""}}
}

/*
suffix:
 .toc

 .so .so.toc
 .a .a
 classes.jack classes.dex.toc (target JAVA_LIBRARIES)
 classes.jack classes.jack (target STATIC_JAVA_LIBRARIES)
 .jar .jar (host JAVA_LIBRARIES/STATIC_JAVA_LIBRARIES)
*/
func PathForLegacyModuleBuilt(ctx ModuleContext, class, name, suffix string) Path {
	var builtPaths []string
	var stem string
	if ctx.Device() {
		builtPaths = []string{"target"}
	} else {
		builtPaths = []string{"host"}
	}
	if (class == "JAVA_LIBRARIES") || (class == "STATIC_JAVA_LIBRARIES") {
		if ctx.Device() {
			builtPaths = append(builtPaths, "common", "obj", "JAVA_LIBRARIES", name+"_intermediates")
			stem = "classes" + suffix
		} else if class == "JAVA_LIBRARIES" {
			builtPaths = append(builtPaths, ctx.Os().String()+"-x86", "framework")
			stem = name + suffix
		} else if class == "STATIC_JAVA_LIBRARIES" {
			builtPaths = append(builtPaths, "common", "obj", "JAVA_LIBRARIES", name+"_intermediates")
			stem = "javalib" + suffix
		}
	} else {
		if ctx.Device() {
			builtPaths = append(builtPaths, "product", ctx.AConfig().MtkTargetProjectName())
			if ctx.PrimaryArch() {
				builtPaths = append(builtPaths, "obj")
			} else {
				builtPaths = append(builtPaths, "obj"+"_"+ctx.Arch().ArchType.String())
			}
		} else {
			builtPaths = append(builtPaths, ctx.Os().String()+"-x86")
			if ctx.PrimaryArch() {
				builtPaths = append(builtPaths, "obj")
			} else {
				builtPaths = append(builtPaths, "obj"+"32")
			}
		}
		if class == "SHARED_LIBRARIES" {
			builtPaths = append(builtPaths, "lib")
		} else {
			builtPaths = append(builtPaths, class, name+"_intermediates")
		}
		stem = name + suffix
	}
	builtPaths = append(builtPaths, stem)
	return OutputPath{basePath{filepath.Join("..", filepath.Join(builtPaths...)), pathConfig(ctx), ""}}
}
