package main

import (
	"fmt"
	"strings"

	mkparser "android/soong/androidmk/parser"

	bpparser "github.com/google/blueprint/parser"
)

func stringToStringValue(s string) bpparser.Expression {
	return &bpparser.String{
		Value: s,
	}
}

func addValues(val1, val2 bpparser.Expression) (bpparser.Expression, error) {
	if val1 == nil {
		return val2, nil
	}

	if val1.Type() == bpparser.StringType && val2.Type() == bpparser.ListType {
		val1 = &bpparser.List{
			Values: []bpparser.Expression{val1},
		}
	} else if val2.Type() == bpparser.StringType && val1.Type() == bpparser.ListType {
		val2 = &bpparser.List{
			Values: []bpparser.Expression{val1},
		}
	} else if val1.Type() != val2.Type() {
		return nil, fmt.Errorf("cannot add mismatched types")
	}

	return &bpparser.Operator{
		Operator: '+',
		Args:     [2]bpparser.Expression{val1, val2},
	}, nil
}

func makeToStringExpression(ms *mkparser.MakeString, scope mkparser.Scope) (bpparser.Expression, error) {

	var val bpparser.Expression
	var err error

	if ms.Strings[0] != "" {
		val = stringToStringValue(ms.Strings[0])
	}

	for i, s := range ms.Strings[1:] {
		if ret, ok := ms.Variables[i].EvalFunction(scope); ok {
			val, err = addValues(val, stringToStringValue(ret))
		} else {
			name := ms.Variables[i].Name
			if !name.Const() {
				return nil, fmt.Errorf("Unsupported non-const variable name %s", name.Dump())
			}
			tmp := &bpparser.Variable{
				Name:  name.Value(nil),
				Value: &bpparser.String{},
			}

			if tmp.Name == "TOP" {
				if s[0] == '/' {
					s = s[1:]
				} else {
					s = "." + s
				}
			} else {
				val, err = addValues(val, tmp)
				if err != nil {
					return nil, err
				}
			}
		}

		if s != "" {
			tmp := stringToStringValue(s)
			val, err = addValues(val, tmp)
			if err != nil {
				return nil, err
			}
		}
	}

	return val, nil
}

func stringToListValue(s string) bpparser.Expression {
	list := strings.Fields(s)
	valList := make([]bpparser.Expression, len(list))
	for i, l := range list {
		valList[i] = &bpparser.String{
			Value: l,
		}
	}
	return &bpparser.List{
		Values: valList,
	}

}

func makeToListExpression(ms *mkparser.MakeString, scope mkparser.Scope) (bpparser.Expression, error) {

	fields := ms.Split(" \t")

	var listOfListValues []bpparser.Expression

	listValue := &bpparser.List{}

	for _, f := range fields {
		if len(f.Variables) == 1 && f.Strings[0] == "" && f.Strings[1] == "" {
			if ret, ok := f.Variables[0].EvalFunction(scope); ok {
				listValue.Values = append(listValue.Values, &bpparser.String{
					Value: ret,
				})
			} else {
				// Variable by itself, variable is probably a list
				if !f.Variables[0].Name.Const() {
					return nil, fmt.Errorf("unsupported non-const variable name")
				}
				if f.Variables[0].Name.Value(nil) == "TOP" {
					listValue.Values = append(listValue.Values, &bpparser.String{
						Value: ".",
					})
				} else {
					if len(listValue.Values) > 0 {
						listOfListValues = append(listOfListValues, listValue)
					}
					listOfListValues = append(listOfListValues, &bpparser.Variable{
						Name:  f.Variables[0].Name.Value(nil),
						Value: &bpparser.List{},
					})
					listValue = &bpparser.List{}
				}
			}
		} else {
			s, err := makeToStringExpression(f, scope)
			if err != nil {
				return nil, err
			}
			if s == nil {
				continue
			}

			listValue.Values = append(listValue.Values, s)
		}
	}

	if len(listValue.Values) > 0 {
		listOfListValues = append(listOfListValues, listValue)
	}

	if len(listOfListValues) == 0 {
		return listValue, nil
	}

	val := listOfListValues[0]
	for _, tmp := range listOfListValues[1:] {
		var err error
		val, err = addValues(val, tmp)
		if err != nil {
			return nil, err
		}
	}

	return val, nil
}

func stringToBoolValue(s string) (bpparser.Expression, error) {
	var b bool
	s = strings.TrimSpace(s)
	switch s {
	case "true":
		b = true
	case "false", "":
		b = false
	case "-frtti": // HACK for LOCAL_RTTI_VALUE
		b = true
	default:
		return nil, fmt.Errorf("unexpected bool value %s", s)
	}
	return &bpparser.Bool{
		Value: b,
	}, nil
}

func makeToBoolExpression(ms *mkparser.MakeString) (bpparser.Expression, error) {
	if !ms.Const() {
		if len(ms.Variables) == 1 && ms.Strings[0] == "" && ms.Strings[1] == "" {
			name := ms.Variables[0].Name
			if !name.Const() {
				return nil, fmt.Errorf("unsupported non-const variable name")
			}
			return &bpparser.Variable{
				Name:  name.Value(nil),
				Value: &bpparser.Bool{},
			}, nil
		} else {
			return nil, fmt.Errorf("non-const bool expression %s", ms.Dump())
		}
	}

	return stringToBoolValue(ms.Value(nil))
}
