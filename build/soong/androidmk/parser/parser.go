package parser

import (
	"errors"
	"fmt"
	"io"
	"sort"
	"text/scanner"
)

var errTooManyErrors = errors.New("too many errors")

const maxErrors = 100

type ParseError struct {
	Err error
	Pos scanner.Position
}

func (e *ParseError) Error() string {
	return fmt.Sprintf("%s: %s", e.Pos, e.Err)
}

func (p *parser) Parse() ([]Node, []error) {
	defer func() {
		if r := recover(); r != nil {
			if r == errTooManyErrors {
				return
			}
			panic(r)
		}
	}()

	p.parseLines()
	p.accept(scanner.EOF)
	p.nodes = append(p.nodes, p.comments...)
	sort.Sort(byPosition(p.nodes))

	return p.nodes, p.errors
}

type parser struct {
	scanner  scanner.Scanner
	tok      rune
	errors   []error
	comments []Node
	nodes    []Node
	lines    []int
}

func NewParser(filename string, r io.Reader) *parser {
	p := &parser{}
	p.lines = []int{0}
	p.scanner.Init(r)
	p.scanner.Error = func(sc *scanner.Scanner, msg string) {
		p.errorf(msg)
	}
	p.scanner.Whitespace = 0
	p.scanner.IsIdentRune = func(ch rune, i int) bool {
		return ch > 0 && ch != ':' && ch != '#' && ch != '=' && ch != '+' && ch != '$' &&
			ch != '\\' && ch != '(' && ch != ')' && ch != '{' && ch != '}' && ch != ';' &&
			ch != '|' && ch != '?' && ch != '\r' && !isWhitespace(ch)
	}
	p.scanner.Mode = scanner.ScanIdents
	p.scanner.Filename = filename
	p.next()
	return p
}

func (p *parser) Unpack(pos Pos) scanner.Position {
	offset := int(pos)
	line := sort.Search(len(p.lines), func(i int) bool { return p.lines[i] > offset }) - 1
	return scanner.Position{
		Filename: p.scanner.Filename,
		Line:     line + 1,
		Column:   offset - p.lines[line] + 1,
		Offset:   offset,
	}
}

func (p *parser) pos() Pos {
	pos := p.scanner.Position
	if !pos.IsValid() {
		pos = p.scanner.Pos()
	}
	return Pos(pos.Offset)
}

func (p *parser) errorf(format string, args ...interface{}) {
	err := &ParseError{
		Err: fmt.Errorf(format, args...),
		Pos: p.scanner.Position,
	}
	p.errors = append(p.errors, err)
	if len(p.errors) >= maxErrors {
		panic(errTooManyErrors)
	}
}

func (p *parser) accept(toks ...rune) bool {
	for _, tok := range toks {
		if p.tok != tok {
			p.errorf("expected %s, found %s", scanner.TokenString(tok),
				scanner.TokenString(p.tok))
			return false
		}
		p.next()
	}
	return true
}

func (p *parser) next() {
	if p.tok != scanner.EOF {
		p.tok = p.scanner.Scan()
		for p.tok == '\r' {
			p.tok = p.scanner.Scan()
		}
	}
	if p.tok == '\n' {
		p.lines = append(p.lines, p.scanner.Position.Offset+1)
	}
}

func (p *parser) parseLines() {
	for {
		p.ignoreWhitespace()

		if p.parseDirective() {
			continue
		}

		ident := p.parseExpression('=', '?', ':', '#', '\n')

		p.ignoreSpaces()

		switch p.tok {
		case '?':
			p.accept('?')
			if p.tok == '=' {
				p.parseAssignment("?=", nil, ident)
			} else {
				p.errorf("expected = after ?")
			}
		case '+':
			p.accept('+')
			if p.tok == '=' {
				p.parseAssignment("+=", nil, ident)
			} else {
				p.errorf("expected = after +")
			}
		case ':':
			p.accept(':')
			switch p.tok {
			case '=':
				p.parseAssignment(":=", nil, ident)
			default:
				p.parseRule(ident)
			}
		case '=':
			p.parseAssignment("=", nil, ident)
		case '#', '\n', scanner.EOF:
			ident.TrimRightSpaces()
			if v, ok := toVariable(ident); ok {
				p.nodes = append(p.nodes, &v)
			} else if !ident.Empty() {
				p.errorf("expected directive, rule, or assignment after ident " + ident.Dump())
			}
			switch p.tok {
			case scanner.EOF:
				return
			case '\n':
				p.accept('\n')
			case '#':
				p.parseComment()
			}
		default:
			p.errorf("expected assignment or rule definition, found %s\n",
				p.scanner.TokenText())
			return
		}
	}
}

func (p *parser) parseDirective() bool {
	if p.tok != scanner.Ident || !isDirective(p.scanner.TokenText()) {
		return false
	}

	d := p.scanner.TokenText()
	pos := p.pos()
	p.accept(scanner.Ident)
	endPos := NoPos

	expression := SimpleMakeString("", pos)

	switch d {
	case "endif", "endef", "else":
		// Nothing
	case "define":
		expression, endPos = p.parseDefine()
	default:
		p.ignoreSpaces()
		expression = p.parseExpression()
	}

	p.nodes = append(p.nodes, &Directive{
		NamePos: pos,
		Name:    d,
		Args:    expression,
		EndPos:  endPos,
	})
	return true
}

func (p *parser) parseDefine() (*MakeString, Pos) {
	value := SimpleMakeString("", p.pos())

loop:
	for {
		switch p.tok {
		case scanner.Ident:
			value.appendString(p.scanner.TokenText())
			if p.scanner.TokenText() == "endef" {
				p.accept(scanner.Ident)
				break loop
			}
			p.accept(scanner.Ident)
		case '\\':
			p.parseEscape()
			switch p.tok {
			case '\n':
				value.appendString(" ")
			case scanner.EOF:
				p.errorf("expected escaped character, found %s",
					scanner.TokenString(p.tok))
				break loop
			default:
				value.appendString(`\` + string(p.tok))
			}
			p.accept(p.tok)
		//TODO: handle variables inside defines?  result depends if
		//define is used in make or rule context
		//case '$':
		//	variable := p.parseVariable()
		//	value.appendVariable(variable)
		case scanner.EOF:
			p.errorf("unexpected EOF while looking for endef")
			break loop
		default:
			value.appendString(p.scanner.TokenText())
			p.accept(p.tok)
		}
	}

	return value, p.pos()
}

func (p *parser) parseEscape() {
	p.scanner.Mode = 0
	p.accept('\\')
	p.scanner.Mode = scanner.ScanIdents
}

func (p *parser) parseExpression(end ...rune) *MakeString {
	value := SimpleMakeString("", p.pos())

	endParen := false
	for _, r := range end {
		if r == ')' {
			endParen = true
		}
	}
	parens := 0

loop:
	for {
		if endParen && parens > 0 && p.tok == ')' {
			parens--
			value.appendString(")")
			p.accept(')')
			continue
		}

		for _, r := range end {
			if p.tok == r {
				break loop
			}
		}

		switch p.tok {
		case '\n':
			break loop
		case scanner.Ident:
			value.appendString(p.scanner.TokenText())
			p.accept(scanner.Ident)
		case '\\':
			p.parseEscape()
			switch p.tok {
			case '\n':
				value.appendString(" ")
			case scanner.EOF:
				p.errorf("expected escaped character, found %s",
					scanner.TokenString(p.tok))
				return value
			default:
				value.appendString(`\` + string(p.tok))
			}
			p.accept(p.tok)
		case '#':
			p.parseComment()
			break loop
		case '$':
			var variable Variable
			variable = p.parseVariable()
			value.appendVariable(variable)
		case scanner.EOF:
			break loop
		case '(':
			if endParen {
				parens++
			}
			value.appendString("(")
			p.accept('(')
		default:
			value.appendString(p.scanner.TokenText())
			p.accept(p.tok)
		}
	}

	if parens > 0 {
		p.errorf("expected closing paren %s", value.Dump())
	}
	return value
}

func (p *parser) parseVariable() Variable {
	pos := p.pos()
	p.accept('$')
	var name *MakeString
	switch p.tok {
	case '(':
		return p.parseBracketedVariable('(', ')', pos)
	case '{':
		return p.parseBracketedVariable('{', '}', pos)
	case '$':
		name = SimpleMakeString("__builtin_dollar", NoPos)
	case scanner.EOF:
		p.errorf("expected variable name, found %s",
			scanner.TokenString(p.tok))
	default:
		name = p.parseExpression(variableNameEndRunes...)
	}

	return p.nameToVariable(name)
}

func (p *parser) parseBracketedVariable(start, end rune, pos Pos) Variable {
	p.accept(start)
	name := p.parseExpression(end)
	p.accept(end)
	return p.nameToVariable(name)
}

func (p *parser) nameToVariable(name *MakeString) Variable {
	return Variable{
		Name: name,
	}
}

func (p *parser) parseRule(target *MakeString) {
	prerequisites, newLine := p.parseRulePrerequisites(target)

	recipe := ""
	recipePos := p.pos()
loop:
	for {
		if newLine {
			if p.tok == '\t' {
				p.accept('\t')
				newLine = false
				continue loop
			} else if p.parseDirective() {
				newLine = false
				continue
			} else {
				break loop
			}
		}

		newLine = false
		switch p.tok {
		case '\\':
			p.parseEscape()
			recipe += string(p.tok)
			p.accept(p.tok)
		case '\n':
			newLine = true
			recipe += "\n"
			p.accept('\n')
		case scanner.EOF:
			break loop
		default:
			recipe += p.scanner.TokenText()
			p.accept(p.tok)
		}
	}

	if prerequisites != nil {
		p.nodes = append(p.nodes, &Rule{
			Target:        target,
			Prerequisites: prerequisites,
			Recipe:        recipe,
			RecipePos:     recipePos,
		})
	}
}

func (p *parser) parseRulePrerequisites(target *MakeString) (*MakeString, bool) {
	newLine := false

	p.ignoreSpaces()

	prerequisites := p.parseExpression('#', '\n', ';', ':', '=')

	switch p.tok {
	case '\n':
		p.accept('\n')
		newLine = true
	case '#':
		p.parseComment()
		newLine = true
	case ';':
		p.accept(';')
	case ':':
		p.accept(':')
		if p.tok == '=' {
			p.parseAssignment(":=", target, prerequisites)
			return nil, true
		} else {
			more := p.parseExpression('#', '\n', ';')
			prerequisites.appendMakeString(more)
		}
	case '=':
		p.parseAssignment("=", target, prerequisites)
		return nil, true
	default:
		p.errorf("unexpected token %s after rule prerequisites", scanner.TokenString(p.tok))
	}

	return prerequisites, newLine
}

func (p *parser) parseComment() {
	pos := p.pos()
	p.accept('#')
	comment := ""
loop:
	for {
		switch p.tok {
		case '\\':
			p.parseEscape()
			if p.tok == '\n' {
				comment += "\n"
			} else {
				comment += "\\" + p.scanner.TokenText()
			}
			p.accept(p.tok)
		case '\n':
			p.accept('\n')
			break loop
		case scanner.EOF:
			break loop
		default:
			comment += p.scanner.TokenText()
			p.accept(p.tok)
		}
	}

	p.comments = append(p.comments, &Comment{
		CommentPos: pos,
		Comment:    comment,
	})
}

func (p *parser) parseAssignment(t string, target *MakeString, ident *MakeString) {
	// The value of an assignment is everything including and after the first
	// non-whitespace character after the = until the end of the logical line,
	// which may included escaped newlines
	p.accept('=')
	value := p.parseExpression()
	value.TrimLeftSpaces()
	if ident.EndsWith('+') && t == "=" {
		ident.TrimRightOne()
		t = "+="
	}

	ident.TrimRightSpaces()

	p.nodes = append(p.nodes, &Assignment{
		Name:   ident,
		Value:  value,
		Target: target,
		Type:   t,
	})
}

type androidMkModule struct {
	assignments map[string]string
}

type androidMkFile struct {
	assignments map[string]string
	modules     []androidMkModule
	includes    []string
}

var directives = [...]string{
	"define",
	"else",
	"endef",
	"endif",
	"ifdef",
	"ifeq",
	"ifndef",
	"ifneq",
	"include",
	"-include",
}

var functions = [...]string{
	"abspath",
	"addprefix",
	"addsuffix",
	"basename",
	"dir",
	"notdir",
	"subst",
	"suffix",
	"filter",
	"filter-out",
	"findstring",
	"firstword",
	"flavor",
	"join",
	"lastword",
	"patsubst",
	"realpath",
	"shell",
	"sort",
	"strip",
	"wildcard",
	"word",
	"wordlist",
	"words",
	"origin",
	"foreach",
	"call",
	"info",
	"error",
	"warning",
	"if",
	"or",
	"and",
	"value",
	"eval",
	"file",
}

func init() {
	sort.Strings(directives[:])
	sort.Strings(functions[:])
}

func isDirective(s string) bool {
	for _, d := range directives {
		if s == d {
			return true
		} else if s < d {
			return false
		}
	}
	return false
}

func isFunctionName(s string) bool {
	for _, f := range functions {
		if s == f {
			return true
		} else if s < f {
			return false
		}
	}
	return false
}

func isWhitespace(ch rune) bool {
	return ch == ' ' || ch == '\t' || ch == '\n'
}

func isValidVariableRune(ch rune) bool {
	return ch != scanner.Ident && ch != ':' && ch != '=' && ch != '#'
}

var whitespaceRunes = []rune{' ', '\t', '\n'}
var variableNameEndRunes = append([]rune{':', '=', '#', ')', '}'}, whitespaceRunes...)

func (p *parser) ignoreSpaces() int {
	skipped := 0
	for p.tok == ' ' || p.tok == '\t' {
		p.accept(p.tok)
		skipped++
	}
	return skipped
}

func (p *parser) ignoreWhitespace() {
	for isWhitespace(p.tok) {
		p.accept(p.tok)
	}
}
