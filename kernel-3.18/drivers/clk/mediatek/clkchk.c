/*
 * Copyright (C) 2017 MediaTek Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */

#include <linux/clk-provider.h>
#include <linux/syscore_ops.h>
#include "clkchk.h"

#define AEE_EXCP_CHECK_PLL_FAIL	1
#define CLKDBG_CCF_API_4_4	0
#define MAX_PLLS		32

#if AEE_EXCP_CHECK_PLL_FAIL
#include <mt-plat/aee.h>
#endif

#define TAG	"[clkchk] "

#define clk_warn(fmt, args...)	pr_warn(TAG fmt, ##args)

#if !CLKDBG_CCF_API_4_4

/* backward compatible */

static const char *clk_hw_get_name(const struct clk_hw *hw)
{
	return __clk_get_name(hw->clk);
}

static bool clk_hw_is_prepared(const struct clk_hw *hw)
{
	return __clk_is_prepared(hw->clk);
}

static bool clk_hw_is_enabled(const struct clk_hw *hw)
{
	return __clk_is_enabled(hw->clk);
}

static unsigned long clk_hw_get_rate(const struct clk_hw *hw)
{
	return __clk_get_rate(hw->clk);
}

static struct clk_hw *clk_hw_get_parent(const struct clk_hw *hw)
{
	return __clk_get_hw(clk_get_parent(hw->clk));
}

#endif /* !CLKDBG_CCF_API_4_4 */

static struct clkchk_cfg *clkchk_cfg;

static const char *ccf_state(struct clk_hw *hw)
{
	if (__clk_get_enable_count(hw->clk))
		return "enabled";

	if (clk_hw_is_prepared(hw))
		return "prepared";

	return "disabled";
}

static void print_enabled_clks(void)
{
	const char * const *cn = clkchk_cfg->all_clk_names;

	clk_warn("enabled clks:\n");

	for (; *cn; cn++) {
		struct clk *c = __clk_lookup(*cn);
		struct clk_hw *c_hw = __clk_get_hw(c);
		struct clk_hw *p_hw;

		if (IS_ERR_OR_NULL(c) || !c_hw)
			continue;

		p_hw = clk_hw_get_parent(c_hw);

		if (!p_hw)
			continue;

		if (!clk_hw_is_prepared(c_hw) && !__clk_get_enable_count(c))
			continue;

		clk_warn("[%-17s: %8s, %3d, %3d, %10ld, %17s]\n",
			clk_hw_get_name(c_hw),
			ccf_state(c_hw),
			clk_hw_is_prepared(c_hw),
			__clk_get_enable_count(c),
			clk_hw_get_rate(c_hw),
			p_hw ? clk_hw_get_name(p_hw) : "- ");
	}
}

static void check_pll_off(void)
{
	static struct clk *off_plls[MAX_PLLS];

	struct clk **c;
	int invalid = 0;
	char buf[128] = {0};
	int n = 0;

	if (!off_plls[0]) {
		const char * const *pn = clkchk_cfg->off_pll_names;
		struct clk **end = off_plls + MAX_PLLS - 1;

		for (c = off_plls; *pn && c < end; pn++, c++)
			*c = __clk_lookup(*pn);
	}

	for (c = off_plls; *c; c++) {
		struct clk_hw *c_hw = __clk_get_hw(*c);

		if (!c_hw)
			continue;

		if (!clk_hw_is_prepared(c_hw) && !clk_hw_is_enabled(c_hw))
			continue;

		n += snprintf(buf + n, sizeof(buf) - n, "%s ",
				clk_hw_get_name(c_hw));

		invalid++;
	}

	if (!invalid)
		return;

	/* invalid. output debug info */

	clk_warn("unexpected unclosed PLL: %s\n", buf);
	print_enabled_clks();

#if AEE_EXCP_CHECK_PLL_FAIL
	if (clkchk_cfg->aee_excp_on_fail)
		aee_kernel_exception("clkchk", "unclosed PLL: %s\n", buf);
#endif

	if (clkchk_cfg->warn_on_fail)
		WARN_ON(1);
}

static int clkchk_syscore_suspend(void)
{
	check_pll_off();

	return 0;
}

static void clkchk_syscore_resume(void)
{
}

static struct syscore_ops clkchk_syscore_ops = {
	.suspend = clkchk_syscore_suspend,
	.resume = clkchk_syscore_resume,
};

int clkchk_init(struct clkchk_cfg *cfg)
{
	const char * const *c;
	bool match = false;

	if (!cfg || !cfg->compatible || !cfg->all_clk_names
			|| !cfg->off_pll_names) {
		clk_warn("Invalid clkchk_cfg.\n");
		return -EINVAL;
	}

	clkchk_cfg = cfg;

	for (c = cfg->compatible; *c; c++) {
		if (of_machine_is_compatible(*c)) {
			match = true;
			break;
		}
	}

	if (!match)
		return -ENODEV;

	register_syscore_ops(&clkchk_syscore_ops);

	return 0;
}
