package main

import (
	"bytes"
	"fmt"
	"io/ioutil"
	"os"
	"strings"
	"text/scanner"

	"android/soong/bpfix/bpfix"

	mkparser "android/soong/androidmk/parser"

	bpparser "github.com/google/blueprint/parser"
)

// TODO: non-expanded variables with expressions

type bpFile struct {
	comments          []*bpparser.CommentGroup
	defs              []bpparser.Definition
	localAssignments  map[string]*bpparser.Property
	globalAssignments map[string]*bpparser.Expression
	scope             mkparser.Scope
	module            *bpparser.Module

	mkPos scanner.Position // Position of the last handled line in the makefile
	bpPos scanner.Position // Position of the last emitted line to the blueprint file

	inModule bool
}

func (f *bpFile) insertComment(s string) {
	f.comments = append(f.comments, &bpparser.CommentGroup{
		Comments: []*bpparser.Comment{
			&bpparser.Comment{
				Comment: []string{s},
				Slash:   f.bpPos,
			},
		},
	})
	f.bpPos.Offset += len(s)
}

func (f *bpFile) insertExtraComment(s string) {
	f.insertComment(s)
	f.bpPos.Line++
}

func (f *bpFile) errorf(node mkparser.Node, s string, args ...interface{}) {
	orig := node.Dump()
	s = fmt.Sprintf(s, args...)
	f.insertExtraComment(fmt.Sprintf("// ANDROIDMK TRANSLATION ERROR: %s", s))

	lines := strings.Split(orig, "\n")
	for _, l := range lines {
		f.insertExtraComment("// " + l)
	}
}

func (f *bpFile) setMkPos(pos, end scanner.Position) {
	if pos.Line < f.mkPos.Line {
		panic(fmt.Errorf("out of order lines, %q after %q", pos, f.mkPos))
	}
	f.bpPos.Line += (pos.Line - f.mkPos.Line)
	f.mkPos = end
}

type conditional struct {
	cond string
	eq   bool
}

func main() {
	b, err := ioutil.ReadFile(os.Args[1])
	if err != nil {
		fmt.Println(err.Error())
		return
	}

	output, errs := convertFile(os.Args[1], bytes.NewBuffer(b))
	if len(errs) > 0 {
		for _, err := range errs {
			fmt.Fprintln(os.Stderr, "ERROR: ", err)
		}
		os.Exit(1)
	}

	fmt.Print(output)
}

func convertFile(filename string, buffer *bytes.Buffer) (string, []error) {
	p := mkparser.NewParser(filename, buffer)

	nodes, errs := p.Parse()
	if len(errs) > 0 {
		return "", errs
	}

	file := &bpFile{
		scope:             androidScope(),
		localAssignments:  make(map[string]*bpparser.Property),
		globalAssignments: make(map[string]*bpparser.Expression),
	}

	var conds []*conditional
	var assignmentCond *conditional

	for _, node := range nodes {
		file.setMkPos(p.Unpack(node.Pos()), p.Unpack(node.End()))

		switch x := node.(type) {
		case *mkparser.Comment:
			file.insertComment("//" + x.Comment)
		case *mkparser.Assignment:
			handleAssignment(file, x, assignmentCond)
		case *mkparser.Directive:
			switch x.Name {
			case "include":
				val := x.Args.Value(file.scope)
				switch {
				case soongModuleTypes[val]:
					handleModuleConditionals(file, x, conds)
					makeModule(file, val)
				case val == clear_vars:
					resetModule(file)
				default:
					file.errorf(x, "unsupported include")
					continue
				}
			case "ifeq", "ifneq", "ifdef", "ifndef":
				args := x.Args.Dump()
				eq := x.Name == "ifeq" || x.Name == "ifdef"
				if _, ok := conditionalTranslations[args]; ok {
					newCond := conditional{args, eq}
					conds = append(conds, &newCond)
					if file.inModule {
						if assignmentCond == nil {
							assignmentCond = &newCond
						} else {
							file.errorf(x, "unsupported nested conditional in module")
						}
					}
				} else {
					file.errorf(x, "unsupported conditional")
					conds = append(conds, nil)
					continue
				}
			case "else":
				if len(conds) == 0 {
					file.errorf(x, "missing if before else")
					continue
				} else if conds[len(conds)-1] == nil {
					file.errorf(x, "else from unsupported contitional")
					continue
				}
				conds[len(conds)-1].eq = !conds[len(conds)-1].eq
			case "endif":
				if len(conds) == 0 {
					file.errorf(x, "missing if before endif")
					continue
				} else if conds[len(conds)-1] == nil {
					file.errorf(x, "endif from unsupported contitional")
					conds = conds[:len(conds)-1]
				} else {
					if assignmentCond == conds[len(conds)-1] {
						assignmentCond = nil
					}
					conds = conds[:len(conds)-1]
				}
			default:
				file.errorf(x, "unsupported directive")
				continue
			}
		default:
			file.errorf(x, "unsupported line")
		}
	}

	tree := &bpparser.File{
		Defs:     file.defs,
		Comments: file.comments,
	}

	// check for common supported but undesirable structures and clean them up
	fixed, err := bpfix.FixTree(tree, bpfix.NewFixRequest().AddAll())
	if err != nil {
		return "", []error{err}
	}

	out, err := bpparser.Print(fixed)
	if err != nil {
		return "", []error{err}
	}

	return string(out), nil
}

func handleAssignment(file *bpFile, assignment *mkparser.Assignment, c *conditional) {
	if !assignment.Name.Const() {
		file.errorf(assignment, "unsupported non-const variable name")
		return
	}

	if assignment.Target != nil {
		file.errorf(assignment, "unsupported target assignment")
		return
	}

	name := assignment.Name.Value(nil)
	prefix := ""

	if strings.HasPrefix(name, "LOCAL_") {
		for _, x := range propertyPrefixes {
			if strings.HasSuffix(name, "_"+x.mk) {
				name = strings.TrimSuffix(name, "_"+x.mk)
				prefix = x.bp
				break
			}
		}

		if c != nil {
			if prefix != "" {
				file.errorf(assignment, "prefix assignment inside conditional, skipping conditional")
			} else {
				var ok bool
				if prefix, ok = conditionalTranslations[c.cond][c.eq]; !ok {
					panic("unknown conditional")
				}
			}
		}
	} else {
		if c != nil {
			eq := "eq"
			if !c.eq {
				eq = "neq"
			}
			file.errorf(assignment, "conditional %s %s on global assignment", eq, c.cond)
		}
	}

	appendVariable := assignment.Type == "+="

	var err error
	if prop, ok := rewriteProperties[name]; ok {
		err = prop(variableAssignmentContext{file, prefix, assignment.Value, appendVariable})
	} else {
		switch {
		case name == "LOCAL_ARM_MODE":
			// This is a hack to get the LOCAL_ARM_MODE value inside
			// of an arch: { arm: {} } block.
			armModeAssign := assignment
			armModeAssign.Name = mkparser.SimpleMakeString("LOCAL_ARM_MODE_HACK_arm", assignment.Name.Pos())
			handleAssignment(file, armModeAssign, c)
		case strings.HasPrefix(name, "LOCAL_"):
			file.errorf(assignment, "unsupported assignment to %s", name)
			return
		default:
			var val bpparser.Expression
			val, err = makeVariableToBlueprint(file, assignment.Value, bpparser.ListType)
			if err == nil {
				err = setVariable(file, appendVariable, prefix, name, val, false)
			}
		}
	}
	if err != nil {
		file.errorf(assignment, err.Error())
	}
}

func handleModuleConditionals(file *bpFile, directive *mkparser.Directive, conds []*conditional) {
	for _, c := range conds {
		if c == nil {
			continue
		}

		if _, ok := conditionalTranslations[c.cond]; !ok {
			panic("unknown conditional " + c.cond)
		}

		disabledPrefix := conditionalTranslations[c.cond][!c.eq]

		// Create a fake assignment with enabled = false
		val, err := makeVariableToBlueprint(file, mkparser.SimpleMakeString("false", mkparser.NoPos), bpparser.BoolType)
		if err == nil {
			err = setVariable(file, false, disabledPrefix, "enabled", val, true)
		}
		if err != nil {
			file.errorf(directive, err.Error())
		}
	}
}

func makeModule(file *bpFile, t string) {
	file.module.Type = t
	file.module.TypePos = file.module.LBracePos
	file.module.RBracePos = file.bpPos
	file.defs = append(file.defs, file.module)
	file.inModule = false
}

func resetModule(file *bpFile) {
	file.module = &bpparser.Module{}
	file.module.LBracePos = file.bpPos
	file.localAssignments = make(map[string]*bpparser.Property)
	file.inModule = true
}

func makeVariableToBlueprint(file *bpFile, val *mkparser.MakeString,
	typ bpparser.Type) (bpparser.Expression, error) {

	var exp bpparser.Expression
	var err error
	switch typ {
	case bpparser.ListType:
		exp, err = makeToListExpression(val, file.scope)
	case bpparser.StringType:
		exp, err = makeToStringExpression(val, file.scope)
	case bpparser.BoolType:
		exp, err = makeToBoolExpression(val)
	default:
		panic("unknown type")
	}

	if err != nil {
		return nil, err
	}

	return exp, nil
}

func setVariable(file *bpFile, plusequals bool, prefix, name string, value bpparser.Expression, local bool) error {

	if prefix != "" {
		name = prefix + "." + name
	}

	pos := file.bpPos

	var oldValue *bpparser.Expression
	if local {
		oldProp := file.localAssignments[name]
		if oldProp != nil {
			oldValue = &oldProp.Value
		}
	} else {
		oldValue = file.globalAssignments[name]
	}

	if local {
		if oldValue != nil && plusequals {
			val, err := addValues(*oldValue, value)
			if err != nil {
				return fmt.Errorf("unsupported addition: %s", err.Error())
			}
			val.(*bpparser.Operator).OperatorPos = pos
			*oldValue = val
		} else {
			names := strings.Split(name, ".")
			container := &file.module.Properties

			for i, n := range names[:len(names)-1] {
				fqn := strings.Join(names[0:i+1], ".")
				prop := file.localAssignments[fqn]
				if prop == nil {
					prop = &bpparser.Property{
						Name:    n,
						NamePos: pos,
						Value: &bpparser.Map{
							Properties: []*bpparser.Property{},
						},
					}
					file.localAssignments[fqn] = prop
					*container = append(*container, prop)
				}
				container = &prop.Value.(*bpparser.Map).Properties
			}

			prop := &bpparser.Property{
				Name:    names[len(names)-1],
				NamePos: pos,
				Value:   value,
			}
			file.localAssignments[name] = prop
			*container = append(*container, prop)
		}
	} else {
		if oldValue != nil && plusequals {
			a := &bpparser.Assignment{
				Name:      name,
				NamePos:   pos,
				Value:     value,
				OrigValue: value,
				EqualsPos: pos,
				Assigner:  "+=",
			}
			file.defs = append(file.defs, a)
		} else {
			a := &bpparser.Assignment{
				Name:      name,
				NamePos:   pos,
				Value:     value,
				OrigValue: value,
				EqualsPos: pos,
				Assigner:  "=",
			}
			file.globalAssignments[name] = &a.Value
			file.defs = append(file.defs, a)
		}
	}
	return nil
}
