package parser

import (
	"strings"
	"testing"
)

var splitNTestCases = []struct {
	in       *MakeString
	expected []*MakeString
	sep      string
	n        int
}{
	{
		in: &MakeString{
			Strings: []string{
				"a b c",
				"d e f",
				" h i j",
			},
			Variables: []Variable{
				Variable{Name: SimpleMakeString("var1", NoPos)},
				Variable{Name: SimpleMakeString("var2", NoPos)},
			},
		},
		sep: " ",
		n:   -1,
		expected: []*MakeString{
			SimpleMakeString("a", NoPos),
			SimpleMakeString("b", NoPos),
			&MakeString{
				Strings: []string{"c", "d"},
				Variables: []Variable{
					Variable{Name: SimpleMakeString("var1", NoPos)},
				},
			},
			SimpleMakeString("e", NoPos),
			&MakeString{
				Strings: []string{"f", ""},
				Variables: []Variable{
					Variable{Name: SimpleMakeString("var2", NoPos)},
				},
			},
			SimpleMakeString("h", NoPos),
			SimpleMakeString("i", NoPos),
			SimpleMakeString("j", NoPos),
		},
	},
	{
		in: &MakeString{
			Strings: []string{
				"a b c",
				"d e f",
				" h i j",
			},
			Variables: []Variable{
				Variable{Name: SimpleMakeString("var1", NoPos)},
				Variable{Name: SimpleMakeString("var2", NoPos)},
			},
		},
		sep: " ",
		n:   3,
		expected: []*MakeString{
			SimpleMakeString("a", NoPos),
			SimpleMakeString("b", NoPos),
			&MakeString{
				Strings: []string{"c", "d e f", " h i j"},
				Variables: []Variable{
					Variable{Name: SimpleMakeString("var1", NoPos)},
					Variable{Name: SimpleMakeString("var2", NoPos)},
				},
			},
		},
	},
}

func TestMakeStringSplitN(t *testing.T) {
	for _, test := range splitNTestCases {
		got := test.in.SplitN(test.sep, test.n)
		gotString := dumpArray(got)
		expectedString := dumpArray(test.expected)
		if gotString != expectedString {
			t.Errorf("expected:\n%s\ngot:\n%s", expectedString, gotString)
		}
	}
}

func dumpArray(a []*MakeString) string {
	ret := make([]string, len(a))

	for i, s := range a {
		ret[i] = s.Dump()
	}

	return strings.Join(ret, "|||")
}
