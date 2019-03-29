/* Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#include <ctype.h>
#include <stdlib.h>
#include <syslog.h>

#include "array.h"
#include "cras_expr.h"

static const char *copy_str(const char *begin, const char *end)
{
	char *s = malloc(end - begin + 1);
	memcpy(s, begin, end - begin);
	s[end - begin] = '\0';
	return s;
}

static void value_set_boolean(struct cras_expr_value *value, char boolean)
{
	cras_expr_value_free(value);
	value->type = CRAS_EXPR_VALUE_TYPE_BOOLEAN;
	value->u.boolean = !!boolean;
}

static void value_set_integer(struct cras_expr_value *value, int integer)
{
	cras_expr_value_free(value);
	value->type = CRAS_EXPR_VALUE_TYPE_INT;
	value->u.integer = integer;
}

static void value_set_string2(struct cras_expr_value *value, const char *begin,
			      const char *end)
{
	cras_expr_value_free(value);
	value->type = CRAS_EXPR_VALUE_TYPE_STRING;
	value->u.string = copy_str(begin, end);
}

static void value_set_string(struct cras_expr_value *value, const char *str)
{
	value_set_string2(value, str, str + strlen(str));
}

static void cras_expr_value_set_function(struct cras_expr_value *value,
					 cras_expr_function_type function)
{
	cras_expr_value_free(value);
	value->type = CRAS_EXPR_VALUE_TYPE_FUNCTION;
	value->u.function = function;
}

static void copy_value(struct cras_expr_value *value,
		       struct cras_expr_value *original)
{
	cras_expr_value_free(value);  /* free the original value first */
	value->type = original->type;
	switch (value->type) {
	case CRAS_EXPR_VALUE_TYPE_NONE:
		break;
	case CRAS_EXPR_VALUE_TYPE_BOOLEAN:
		value->u.boolean = original->u.boolean;
		break;
	case CRAS_EXPR_VALUE_TYPE_INT:
		value->u.integer = original->u.integer;
		break;
	case CRAS_EXPR_VALUE_TYPE_STRING:
		value->u.string = strdup(original->u.string);
		break;
	case CRAS_EXPR_VALUE_TYPE_FUNCTION:
		value->u.function = original->u.function;
		break;
	}
}

void cras_expr_value_free(struct cras_expr_value *value)
{
	switch (value->type) {
	case CRAS_EXPR_VALUE_TYPE_STRING:
		free((char *)value->u.string);
		value->u.string = NULL;
		break;
	case CRAS_EXPR_VALUE_TYPE_NONE:
	case CRAS_EXPR_VALUE_TYPE_BOOLEAN:
	case CRAS_EXPR_VALUE_TYPE_INT:
	case CRAS_EXPR_VALUE_TYPE_FUNCTION:
		break;
	}
	value->type = CRAS_EXPR_VALUE_TYPE_NONE;
}

static struct cras_expr_value *find_value(struct cras_expr_env *env,
					  const char *name)
{
	int i;
	const char **key;

	FOR_ARRAY_ELEMENT(&env->keys, i, key) {
		if (strcmp(*key, name) == 0)
			return ARRAY_ELEMENT(&env->values, i);
	}
	return NULL;
}

/* Insert a (key, value) pair to the environment. The value is
 * initialized to zero. Return the pointer to value so it can be set
 * to the proper value. */
static struct cras_expr_value *insert_value(struct cras_expr_env *env,
					    const char *key)
{
	*ARRAY_APPEND_ZERO(&env->keys) = strdup(key);
	return ARRAY_APPEND_ZERO(&env->values);
}

static struct cras_expr_value *find_or_insert_value(struct cras_expr_env *env,
						    const char *key)
{
	struct cras_expr_value *value = find_value(env, key);
	if (!value)
		value = insert_value(env, key);
	return value;
}

static void function_not(cras_expr_value_array *operands,
			 struct cras_expr_value *result)
{
	struct cras_expr_value *value;
	int is_false;

	if (ARRAY_COUNT(operands) != 2) {
		cras_expr_value_free(result);
		syslog(LOG_ERR, "not takes one argument");
		return;
	}

	value = ARRAY_ELEMENT(operands, 1);
	is_false = (value->type == CRAS_EXPR_VALUE_TYPE_BOOLEAN &&
		    !value->u.boolean);
	value_set_boolean(result, is_false);
}

static void function_and(cras_expr_value_array *operands,
			 struct cras_expr_value *result)
{
	int i;
	struct cras_expr_value *value;
	int n = ARRAY_COUNT(operands);

	/* no operands -- return #t */
	if (n <= 1) {
		value_set_boolean(result, 1);
		return;
	}

	/* if there is any #f, return it */
	FOR_ARRAY_ELEMENT(operands, i, value) {
		if (i == 0)
			continue;  /* ignore "and" itself */
		if (value->type == CRAS_EXPR_VALUE_TYPE_BOOLEAN &&
		    !value->u.boolean) {
			value_set_boolean(result, 0);
			return;
		}
	}

	/* otherwise return the last element */
	copy_value(result, ARRAY_ELEMENT(operands, n - 1));
}

static void function_or(cras_expr_value_array *operands,
			struct cras_expr_value *result)
{
	int i;
	struct cras_expr_value *value;

	FOR_ARRAY_ELEMENT(operands, i, value) {
		if (i == 0)
			continue;  /* ignore "or" itself */
		if (value->type != CRAS_EXPR_VALUE_TYPE_BOOLEAN ||
		    value->u.boolean) {
			copy_value(result, value);
			return;
		}
	}

	value_set_boolean(result, 0);
}

static char function_equal_real(cras_expr_value_array *operands)
{
	int i;
	struct cras_expr_value *value, *prev;

	FOR_ARRAY_ELEMENT(operands, i, value) {
		if (i <= 1)
			continue;  /* ignore equal? and first operand */
		/* compare with the previous operand */

		prev = ARRAY_ELEMENT(operands, i - 1);

		if (prev->type != value->type)
			return 0;

		switch (prev->type) {
		case CRAS_EXPR_VALUE_TYPE_NONE:
			break;
		case CRAS_EXPR_VALUE_TYPE_BOOLEAN:
			if (prev->u.boolean != value->u.boolean)
				return 0;
			break;
		case CRAS_EXPR_VALUE_TYPE_INT:
			if (prev->u.integer != value->u.integer)
				return 0;
			break;
		case CRAS_EXPR_VALUE_TYPE_STRING:
			if (strcmp(prev->u.string, value->u.string) != 0)
				return 0;
			break;
		case CRAS_EXPR_VALUE_TYPE_FUNCTION:
			if (prev->u.function != value->u.function)
				return 0;
			break;
		}
	}

	return 1;
}

static void function_equal(cras_expr_value_array *operands,
			   struct cras_expr_value *result)
{
	value_set_boolean(result, function_equal_real(operands));
}

static void env_set_variable(struct cras_expr_env *env, const char *name,
			     struct cras_expr_value *new_value)
{
	struct cras_expr_value *value = find_or_insert_value(env, name);
	copy_value(value, new_value);
}

void cras_expr_env_install_builtins(struct cras_expr_env *env)
{
	struct cras_expr_value value = CRAS_EXPR_VALUE_INIT;

	/* initialize env with builtin functions */
	cras_expr_value_set_function(&value, &function_not);
	env_set_variable(env, "not", &value);

	cras_expr_value_set_function(&value, &function_and);
	env_set_variable(env, "and", &value);

	cras_expr_value_set_function(&value, &function_or);
	env_set_variable(env, "or", &value);

	cras_expr_value_set_function(&value, &function_equal);
	env_set_variable(env, "equal?", &value);

	cras_expr_value_free(&value);
}

void cras_expr_env_set_variable_boolean(struct cras_expr_env *env,
					const char *name, char boolean)
{
	struct cras_expr_value *value = find_or_insert_value(env, name);
	value_set_boolean(value, boolean);
}

void cras_expr_env_set_variable_integer(struct cras_expr_env *env,
					const char *name, int integer)
{
	struct cras_expr_value *value = find_or_insert_value(env, name);
	value_set_integer(value, integer);
}

void cras_expr_env_set_variable_string(struct cras_expr_env *env,
				       const char *name, const char *str)
{
	struct cras_expr_value *value = find_or_insert_value(env, name);
	value_set_string(value, str);
}

void cras_expr_env_free(struct cras_expr_env *env)
{
	int i;
	const char **key;
	struct cras_expr_value *value;

	FOR_ARRAY_ELEMENT(&env->keys, i, key) {
		free((char *)*key);
	}

	FOR_ARRAY_ELEMENT(&env->values, i, value) {
		cras_expr_value_free(value);
	}

	ARRAY_FREE(&env->keys);
	ARRAY_FREE(&env->values);
}

static struct cras_expr_expression *new_boolean_literal(char boolean)
{
	struct cras_expr_expression *expr;
	expr = calloc(1, sizeof(struct cras_expr_expression));
	expr->type = EXPR_TYPE_LITERAL;
	value_set_boolean(&expr->u.literal, boolean);
	return expr;
}

static struct cras_expr_expression *new_integer_literal(int integer)
{
	struct cras_expr_expression *expr;
	expr = calloc(1, sizeof(struct cras_expr_expression));
	expr->type = EXPR_TYPE_LITERAL;
	value_set_integer(&expr->u.literal, integer);
	return expr;
}

static struct cras_expr_expression *new_string_literal(const char *begin,
						       const char *end)
{
	struct cras_expr_expression *expr;
	expr = calloc(1, sizeof(struct cras_expr_expression));
	expr->type = EXPR_TYPE_LITERAL;
	value_set_string2(&expr->u.literal, begin, end);
	return expr;
}

static struct cras_expr_expression *new_variable(const char *begin,
						 const char *end)
{
	struct cras_expr_expression *expr;
	expr = calloc(1, sizeof(struct cras_expr_expression));
	expr->type = EXPR_TYPE_VARIABLE;
	expr->u.variable = copy_str(begin, end);
	return expr;
}

static struct cras_expr_expression *new_compound_expression()
{
	struct cras_expr_expression *expr;
	expr = calloc(1, sizeof(struct cras_expr_expression));
	expr->type = EXPR_TYPE_COMPOUND;
	return expr;
}

static void add_sub_expression(struct cras_expr_expression *expr,
			       struct cras_expr_expression *sub)
{
	ARRAY_APPEND(&expr->u.children, sub);
}

static int is_identifier_char(char c)
{
	if (isspace(c))
		return 0;
	if (c == '\0')
		return 0;
	if (isalpha(c))
		return 1;
	if (c == '_' || c == '-' || c == '?')
		return 1;
	return 0;
}

static struct cras_expr_expression *parse_one_expr(const char **str)
{
	/* skip whitespace */
	while (isspace(**str))
		(*str)++;

	if (**str == '\0')
		return NULL;

	/* boolean literal: #t, #f */
	if (**str == '#') {
		(*str)++;
		char c = **str;
		if (c == 't' || c == 'f') {
			(*str)++;
			return new_boolean_literal(c == 't');
		} else {
			syslog(LOG_ERR, "unexpected char after #: '%c'",
			       c);
		}
		return NULL;
	}

	/* integer literal: (-)[0-9]+ */
	if (isdigit(**str) || (**str == '-' && isdigit((*str)[1])))
		return new_integer_literal(strtol(*str, (char **)str, 10));

	/* string literal: "..." */
	if (**str == '"') {
		const char *begin = *str + 1;
		const char *end = strchr(begin, '"');
		if (end == NULL) {
			syslog(LOG_ERR, "no matching \"");
			end = begin;
			*str = begin;
		} else {
			*str = end + 1;
		}
		return new_string_literal(begin, end);
	}

	/* compound expression: (expr1 expr2 ...) */
	if (**str == '(') {
		(*str)++;
		struct cras_expr_expression *expr = new_compound_expression();
		while (1) {
			struct cras_expr_expression *next = parse_one_expr(str);
			if (next == NULL)
				break;
			add_sub_expression(expr, next);
		}
		if (**str != ')') {
			syslog(LOG_ERR, "no matching ): found '%c'", **str);
			cras_expr_expression_free(expr);
			return NULL;
		} else {
			(*str)++;
		}
		return expr;
	}

	/* variable name */
	if (is_identifier_char(**str)) {
		const char *begin = *str;
		while (is_identifier_char(**str))
			(*str)++;
		return new_variable(begin, *str);
	}

	return NULL;
}

struct cras_expr_expression *cras_expr_expression_parse(const char *str)
{
	if (!str)
		return NULL;
	return parse_one_expr(&str);
}

void cras_expr_expression_free(struct cras_expr_expression *expr)
{
	if (!expr)
		return;

	switch (expr->type) {
	case EXPR_TYPE_NONE:
		break;
	case EXPR_TYPE_LITERAL:
		cras_expr_value_free(&expr->u.literal);
		break;
	case EXPR_TYPE_VARIABLE:
		free((char *)expr->u.variable);
		break;
	case EXPR_TYPE_COMPOUND:
	{
		int i;
		struct cras_expr_expression **psub;
		FOR_ARRAY_ELEMENT(&expr->u.children, i, psub) {
			cras_expr_expression_free(*psub);
		}
		ARRAY_FREE(&expr->u.children);
		break;
	}
	}
	free(expr);
}

void cras_expr_expression_eval(struct cras_expr_expression *expr,
			       struct cras_expr_env *env,
			       struct cras_expr_value *result)
{
	cras_expr_value_free(result);

	switch (expr->type) {
	case EXPR_TYPE_NONE:
		break;
	case EXPR_TYPE_LITERAL:
		copy_value(result, &expr->u.literal);
		break;
	case EXPR_TYPE_VARIABLE:
	{
		struct cras_expr_value *value = find_value(env,
							   expr->u.variable);
		if (value == NULL) {
			syslog(LOG_ERR, "cannot find value for %s",
			       expr->u.variable);
		} else {
			copy_value(result, value);
		}
		break;
	}
	case EXPR_TYPE_COMPOUND:
	{
		int i;
		struct cras_expr_expression **psub;
		cras_expr_value_array values = ARRAY_INIT;
		struct cras_expr_value *value;

		FOR_ARRAY_ELEMENT(&expr->u.children, i, psub) {
			value = ARRAY_APPEND_ZERO(&values);
			cras_expr_expression_eval(*psub, env, value);
		}

		if (ARRAY_COUNT(&values) > 0) {
			struct cras_expr_value *f = ARRAY_ELEMENT(&values, 0);
			if (f->type == CRAS_EXPR_VALUE_TYPE_FUNCTION)
				f->u.function(&values, result);
			else
				syslog(LOG_ERR,
				       "first element is not a function");
		} else {
			syslog(LOG_ERR, "empty compound expression?");
		}

		FOR_ARRAY_ELEMENT(&values, i, value) {
			cras_expr_value_free(value);
		}

		ARRAY_FREE(&values);
		break;
	}
	}
}

int cras_expr_expression_eval_int(struct cras_expr_expression *expr,
				  struct cras_expr_env *env,
				  int *integer)
{
	int rc = 0;
	struct cras_expr_value value = CRAS_EXPR_VALUE_INIT;

	cras_expr_expression_eval(expr, env, &value);
	if (value.type == CRAS_EXPR_VALUE_TYPE_INT) {
		*integer = value.u.integer;
	} else {
		syslog(LOG_ERR, "value type is not integer (%d)", value.type);
		rc = -1;
	}
	cras_expr_value_free(&value);
	return rc;
}

int cras_expr_expression_eval_boolean(struct cras_expr_expression *expr,
				      struct cras_expr_env *env,
				      char *boolean)
{
	int rc = 0;
	struct cras_expr_value value = CRAS_EXPR_VALUE_INIT;

	cras_expr_expression_eval(expr, env, &value);
	if (value.type == CRAS_EXPR_VALUE_TYPE_BOOLEAN) {
		*boolean = value.u.boolean;
	} else {
		syslog(LOG_ERR, "value type is not boolean (%d)", value.type);
		rc = -1;
	}
	cras_expr_value_free(&value);
	return rc;
}
