package parser

import (
	"strings"
	"unicode"
)

// A MakeString is a string that may contain variable substitutions in it.
// It can be considered as an alternating list of raw Strings and variable
// substitutions, where the first and last entries in the list must be raw
// Strings (possibly empty).  A MakeString that starts with a variable
// will have an empty first raw string, and a MakeString that ends with a
// variable will have an empty last raw string.  Two sequential Variables
// will have an empty raw string between them.
//
// The MakeString is stored as two lists, a list of raw Strings and a list
// of Variables.  The raw string list is always one longer than the variable
// list.
type MakeString struct {
	StringPos Pos
	Strings   []string
	Variables []Variable
}

func SimpleMakeString(s string, pos Pos) *MakeString {
	return &MakeString{
		StringPos: pos,
		Strings:   []string{s},
	}
}

func (ms *MakeString) Pos() Pos {
	return ms.StringPos
}

func (ms *MakeString) End() Pos {
	pos := ms.StringPos
	if len(ms.Strings) > 1 {
		pos = ms.Variables[len(ms.Variables)-1].End()
	}
	return Pos(int(pos) + len(ms.Strings[len(ms.Strings)-1]))
}

func (ms *MakeString) appendString(s string) {
	if len(ms.Strings) == 0 {
		ms.Strings = []string{s}
		return
	} else {
		ms.Strings[len(ms.Strings)-1] += s
	}
}

func (ms *MakeString) appendVariable(v Variable) {
	if len(ms.Strings) == 0 {
		ms.Strings = []string{"", ""}
		ms.Variables = []Variable{v}
	} else {
		ms.Strings = append(ms.Strings, "")
		ms.Variables = append(ms.Variables, v)
	}
}

func (ms *MakeString) appendMakeString(other *MakeString) {
	last := len(ms.Strings) - 1
	ms.Strings[last] += other.Strings[0]
	ms.Strings = append(ms.Strings, other.Strings[1:]...)
	ms.Variables = append(ms.Variables, other.Variables...)
}

func (ms *MakeString) Value(scope Scope) string {
	if len(ms.Strings) == 0 {
		return ""
	} else {
		ret := ms.Strings[0]
		for i := range ms.Strings[1:] {
			ret += ms.Variables[i].Value(scope)
			ret += ms.Strings[i+1]
		}
		return ret
	}
}

func (ms *MakeString) Dump() string {
	if len(ms.Strings) == 0 {
		return ""
	} else {
		ret := ms.Strings[0]
		for i := range ms.Strings[1:] {
			ret += ms.Variables[i].Dump()
			ret += ms.Strings[i+1]
		}
		return ret
	}
}

func (ms *MakeString) Const() bool {
	return len(ms.Strings) <= 1
}

func (ms *MakeString) Empty() bool {
	return len(ms.Strings) == 0 || (len(ms.Strings) == 1 && ms.Strings[0] == "")
}

func (ms *MakeString) Split(sep string) []*MakeString {
	return ms.SplitN(sep, -1)
}

func (ms *MakeString) SplitN(sep string, n int) []*MakeString {
	ret := []*MakeString{}

	curMs := SimpleMakeString("", ms.Pos())

	var i int
	var s string
	for i, s = range ms.Strings {
		if n != 0 {
			split := splitAnyN(s, sep, n)
			if n != -1 {
				if len(split) > n {
					panic("oops!")
				} else {
					n -= len(split)
				}
			}
			curMs.appendString(split[0])

			for _, r := range split[1:] {
				ret = append(ret, curMs)
				curMs = SimpleMakeString(r, ms.Pos())
			}
		} else {
			curMs.appendString(s)
		}

		if i < len(ms.Strings)-1 {
			curMs.appendVariable(ms.Variables[i])
		}
	}

	ret = append(ret, curMs)
	return ret
}

func (ms *MakeString) TrimLeftSpaces() {
	l := len(ms.Strings[0])
	ms.Strings[0] = strings.TrimLeftFunc(ms.Strings[0], unicode.IsSpace)
	ms.StringPos += Pos(len(ms.Strings[0]) - l)
}

func (ms *MakeString) TrimRightSpaces() {
	last := len(ms.Strings) - 1
	ms.Strings[last] = strings.TrimRightFunc(ms.Strings[last], unicode.IsSpace)
}

func (ms *MakeString) TrimRightOne() {
	last := len(ms.Strings) - 1
	if len(ms.Strings[last]) > 1 {
		ms.Strings[last] = ms.Strings[last][0 : len(ms.Strings[last])-1]
	}
}

func (ms *MakeString) EndsWith(ch rune) bool {
	s := ms.Strings[len(ms.Strings)-1]
	return s[len(s)-1] == uint8(ch)
}

func splitAnyN(s, sep string, n int) []string {
	ret := []string{}
	for n == -1 || n > 1 {
		index := strings.IndexAny(s, sep)
		if index >= 0 {
			ret = append(ret, s[0:index])
			s = s[index+1:]
			if n > 0 {
				n--
			}
		} else {
			break
		}
	}
	ret = append(ret, s)
	return ret
}
