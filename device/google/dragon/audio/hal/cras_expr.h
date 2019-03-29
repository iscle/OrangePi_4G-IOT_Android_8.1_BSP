/* Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#ifndef CRAS_EXPR_H_
#define CRAS_EXPR_H_

#ifdef __cplusplus
extern "C" {
#endif

#include "array.h"

/* Value */

enum cras_expr_value_type {
	CRAS_EXPR_VALUE_TYPE_NONE,
	CRAS_EXPR_VALUE_TYPE_BOOLEAN,
	CRAS_EXPR_VALUE_TYPE_INT,
	CRAS_EXPR_VALUE_TYPE_STRING,
	CRAS_EXPR_VALUE_TYPE_FUNCTION,
};

DECLARE_ARRAY_TYPE(struct cras_expr_value, cras_expr_value_array);
typedef void (*cras_expr_function_type)(cras_expr_value_array *operands,
					struct cras_expr_value *result);

struct cras_expr_value {
	enum cras_expr_value_type type;
	union {
		char boolean;
		int integer;
		const char *string;
		cras_expr_function_type function;
	} u;
};

/* initial value for the value type is zero */
#define CRAS_EXPR_VALUE_INIT {}

/* Expression */

enum expr_type {
	EXPR_TYPE_NONE,
	EXPR_TYPE_LITERAL,
	EXPR_TYPE_VARIABLE,
	EXPR_TYPE_COMPOUND,
};

DECLARE_ARRAY_TYPE(struct cras_expr_expression *, expr_array);

struct cras_expr_expression {
	enum expr_type type;
	union {
		struct cras_expr_value literal;
		const char *variable;
		expr_array children;
	} u;
};

/* Environment */

DECLARE_ARRAY_TYPE(const char *, string_array);

struct cras_expr_env {
	string_array keys;
	cras_expr_value_array values;
};

/* initial value for the environment type is zero */
#define CRAS_EXPR_ENV_INIT {}

void cras_expr_env_install_builtins(struct cras_expr_env *env);
void cras_expr_env_set_variable_boolean(struct cras_expr_env *env,
					const char *name, char boolean);
void cras_expr_env_set_variable_integer(struct cras_expr_env *env,
					const char *name, int integer);
void cras_expr_env_set_variable_string(struct cras_expr_env *env,
				       const char *name, const char *str);
void cras_expr_env_free(struct cras_expr_env *env);

struct cras_expr_expression *cras_expr_expression_parse(const char *str);
void cras_expr_expression_eval(struct cras_expr_expression *expr,
			       struct cras_expr_env *env,
			       struct cras_expr_value *value);
int cras_expr_expression_eval_boolean(struct cras_expr_expression *expr,
				      struct cras_expr_env *env, char *boolean);
int cras_expr_expression_eval_int(struct cras_expr_expression *expr,
				  struct cras_expr_env *env, int *integer);
void cras_expr_expression_free(struct cras_expr_expression *expr);
void cras_expr_value_free(struct cras_expr_value *value);

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* CRAS_EXPR_H_ */
