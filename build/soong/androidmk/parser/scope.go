package parser

import "strings"

type Scope interface {
	Get(name string) string
	Set(name, value string)
	Call(name string, args []string) string
	SetFunc(name string, f func([]string) string)
}

type scope struct {
	variables map[string]string
	functions map[string]func([]string) string
	parent    Scope
}

func (s *scope) Get(name string) string {
	if val, ok := s.variables[name]; ok {
		return val
	} else if s.parent != nil {
		return s.parent.Get(name)
	} else if val, ok := builtinScope[name]; ok {
		return val
	} else {
		return "<'" + name + "' unset>"
	}
}

func (s *scope) Set(name, value string) {
	s.variables[name] = value
}

func (s *scope) Call(name string, args []string) string {
	if f, ok := s.functions[name]; ok {
		return f(args)
	}

	return "<func:'" + name + "' unset>"
}

func (s *scope) SetFunc(name string, f func([]string) string) {
	s.functions[name] = f
}

func NewScope(parent Scope) Scope {
	return &scope{
		variables: make(map[string]string),
		functions: make(map[string]func([]string) string),
		parent:    parent,
	}
}

var builtinScope map[string]string

func init() {
	builtinScope := make(map[string]string)
	builtinScope["__builtin_dollar"] = "$"
}

func (v Variable) EvalFunction(scope Scope) (string, bool) {
	f := v.Name.SplitN(" \t", 2)
	if len(f) > 1 && f[0].Const() {
		fname := f[0].Value(nil)
		if isFunctionName(fname) {
			args := f[1].Split(",")
			argVals := make([]string, len(args))
			for i, a := range args {
				argVals[i] = a.Value(scope)
			}

			if fname == "call" {
				return scope.Call(argVals[0], argVals[1:]), true
			} else {
				return "__builtin_func:" + fname + " " + strings.Join(argVals, " "), true
			}
		}
	}

	return "", false
}

func (v Variable) Value(scope Scope) string {
	if ret, ok := v.EvalFunction(scope); ok {
		return ret
	}
	return scope.Get(v.Name.Value(scope))
}

func toVariable(ms *MakeString) (Variable, bool) {
	if len(ms.Variables) == 1 && ms.Strings[0] == "" && ms.Strings[1] == "" {
		return ms.Variables[0], true
	}
	return Variable{}, false
}
