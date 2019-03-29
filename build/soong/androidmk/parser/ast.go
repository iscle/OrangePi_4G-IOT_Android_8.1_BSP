package parser

type Pos int

const NoPos Pos = 0

type Node interface {
	Dump() string
	Pos() Pos
	End() Pos
}

type Assignment struct {
	Target *MakeString
	Name   *MakeString
	Value  *MakeString
	Type   string
}

func (x *Assignment) Dump() string {
	target := ""
	if x.Target != nil {
		target = x.Target.Dump() + ": "
	}
	return target + x.Name.Dump() + " " + x.Type + " " + x.Value.Dump()
}

func (x *Assignment) Pos() Pos {
	if x.Target != nil {
		return x.Target.Pos()
	}
	return x.Name.Pos()
}

func (x *Assignment) End() Pos { return x.Value.End() }

type Comment struct {
	CommentPos Pos
	Comment    string
}

func (x *Comment) Dump() string {
	return "#" + x.Comment
}

func (x *Comment) Pos() Pos { return x.CommentPos }
func (x *Comment) End() Pos { return Pos(int(x.CommentPos) + len(x.Comment)) }

type Directive struct {
	NamePos Pos
	Name    string
	Args    *MakeString
	EndPos  Pos
}

func (x *Directive) Dump() string {
	return x.Name + " " + x.Args.Dump()
}

func (x *Directive) Pos() Pos { return x.NamePos }
func (x *Directive) End() Pos {
	if x.EndPos != NoPos {
		return x.EndPos
	}
	return x.Args.End()
}

type Rule struct {
	Target        *MakeString
	Prerequisites *MakeString
	RecipePos     Pos
	Recipe        string
}

func (x *Rule) Dump() string {
	recipe := ""
	if x.Recipe != "" {
		recipe = "\n" + x.Recipe
	}
	return "rule:       " + x.Target.Dump() + ": " + x.Prerequisites.Dump() + recipe
}

func (x *Rule) Pos() Pos { return x.Target.Pos() }
func (x *Rule) End() Pos { return Pos(int(x.RecipePos) + len(x.Recipe)) }

type Variable struct {
	Name *MakeString
}

func (x *Variable) Pos() Pos { return x.Name.Pos() }
func (x *Variable) End() Pos { return x.Name.End() }

func (x *Variable) Dump() string {
	return "$(" + x.Name.Dump() + ")"
}

// Sort interface for []Node by position
type byPosition []Node

func (s byPosition) Len() int {
	return len(s)
}

func (s byPosition) Swap(i, j int) {
	s[i], s[j] = s[j], s[i]
}

func (s byPosition) Less(i, j int) bool {
	return s[i].Pos() < s[j].Pos()
}
