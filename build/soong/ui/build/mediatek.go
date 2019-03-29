package build

import (
	"bufio"
	"io"
	"os"
	"path/filepath"
	"strings"
	"sync"
)

var (
	initFeatureOnce sync.Once

	featureValues map[string]string

	MtkTargetProject string
)

func (c *configImpl) MtkTargetProjectName() string {
	return MtkTargetProject
}

func MtkDumpMakeVars(ctx Context, config Config) {
	initFeatureOnce.Do(func() {
		featureValues = make(map[string]string)
		variablesFileName := filepath.Join(config.SoongOutDir(), "mtk_soong.config")
		if _, err := os.Stat(variablesFileName); err == nil {
			mtkFeatureOptions, err := parseProjectConfig(variablesFileName)
			if err != nil {
				ctx.Fatalln("Error parseing mtk_soong.config:", err)
			} else {
				for name, value := range mtkFeatureOptions {
					if _, ok := featureValues[name]; !ok {
						featureValues[name] = value
					}
				}
			}
		}
		if value, ok := featureValues["MTK_TARGET_PROJECT"]; ok {
			MtkTargetProject = value
		}
	})
}

func parseProjectConfig(configFile string) (map[string]string, error) {
	f, err := os.Open(configFile)
	if err != nil {
		return nil, err
	}
	defer f.Close()
	r := bufio.NewReader(f)
	options := make(map[string]string)
	var lineLast, lineCurr string
	for {
		buf, err := r.ReadString('\n')
		lineStrip := strings.TrimSpace(buf)
		if lineLast == "" {
			lineCurr = lineStrip
		} else {
			lineCurr = lineLast + " " + lineStrip
		}
		if strings.HasSuffix(lineStrip, "\\") {
			lineLast = strings.TrimRight(lineCurr, "\\")
		} else {
			lineLast = ""
			for i := 0; i < len(lineCurr); i++ {
				if lineCurr[i] == '=' {
					var j int
					if (lineCurr[i-1] == ':') ||
						(lineCurr[i-1] == '?') ||
						(lineCurr[i-1] == '+') {
						j = i - 1
					} else {
						j = i
					}
					key := strings.TrimSpace(string(lineCurr[:j]))
					value := strings.TrimSpace(string(lineCurr[i+1:]))
					options[key] = value
					break
				}
			}
		}
		if err == io.EOF {
			break
		}
	}
	return options, nil
}
