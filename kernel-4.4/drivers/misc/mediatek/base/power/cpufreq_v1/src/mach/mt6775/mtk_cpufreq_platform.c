/*
 * Copyright (C) 2016 MediaTek Inc.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See http://www.gnu.org/licenses/gpl-2.0.html for more details.
 */

#include <linux/of.h>
#include <linux/of_address.h>
#include <linux/regulator/consumer.h>

#include <mt-plat/mtk_devinfo.h>
#ifdef CONFIG_MTK_FREQ_HOPPING
#include <mach/mtk_freqhopping.h>
#else
#define FH_PLL1 1
#define FH_PLL2 2
#define FH_PLL4 4
#endif

#include "mtk_cpufreq_platform.h"
#include "../../mtk_cpufreq_hybrid.h"

static struct regulator *regulator_proc_b;
static struct regulator *regulator_sram_b;
static struct regulator *regulator_proc_l;
static struct regulator *regulator_sram_l;

static unsigned long apmixed_base	= 0x10212000;
static unsigned long mcucfg_base	= 0x0c530000;

#define APMIXED_NODE    "mediatek,apmixedsys"
#define MCUCFG_NODE     "mediatek,mcucfg"

#define ARMPLL_L_CON1       (apmixed_base + 0x324)	/* ARMPLL2 */
#define ARMPLL_B_CON1       (apmixed_base + 0x334)	/* ARMPLL3 */
#define CCIPLL_CON1         (apmixed_base + 0x354)

#define CKDIV1_L_CFG        (mcucfg_base + 0x7a0)	/* MP0_PLL_DIVIDER */
#define CKDIV1_B_CFG        (mcucfg_base + 0x7a8)	/* MP2_PLL_DIVIDER */
#define CKDIV1_CCI_CFG      (mcucfg_base + 0x7c0)	/* BUS_PLL_DIVIDER */

struct mt_cpu_dvfs cpu_dvfs[NR_MT_CPU_DVFS] = {
	[MT_CPU_DVFS_L] = {
		.name		= __stringify(MT_CPU_DVFS_L),
		.id		= MT_CPU_DVFS_L,
		.cpu_id		= 0,
		.idx_normal_max_opp = -1,
		.idx_opp_ppm_base = 15,
		.idx_opp_ppm_limit = 0,
		.Vproc_buck_id	= CPU_DVFS_VPROC_L,
		.Vsram_buck_id	= CPU_DVFS_VSRAM_L,
		.Pll_id		= PLL_L_CLUSTER,
	},

	[MT_CPU_DVFS_B] = {
		.name		= __stringify(MT_CPU_DVFS_B),
		.id		= MT_CPU_DVFS_B,
		.cpu_id		= 4,
		.idx_normal_max_opp = -1,
		.idx_opp_ppm_base = 15,
		.idx_opp_ppm_limit = 0,
		.Vproc_buck_id	= CPU_DVFS_VPROC_B,
		.Vsram_buck_id	= CPU_DVFS_VSRAM_B,
		.Pll_id		= PLL_B_CLUSTER,
	},

	[MT_CPU_DVFS_CCI] = {
		.name		= __stringify(MT_CPU_DVFS_CCI),
		.id		= MT_CPU_DVFS_CCI,
		.cpu_id		= 10,
		.idx_normal_max_opp = -1,
		.idx_opp_ppm_base = 15,
		.idx_opp_ppm_limit = 0,
		.Vproc_buck_id	= CPU_DVFS_VPROC_L,
		.Vsram_buck_id	= CPU_DVFS_VSRAM_L,
		.Pll_id		= PLL_CCI_CLUSTER,
	},
};

/* PMIC Part */
/* buck_ops_vproc_b */
static unsigned int get_cur_volt_mt6355_vproc11(struct buck_ctrl_t *buck_p)
{
	unsigned int rdata;

	rdata = regulator_get_voltage(regulator_proc_b) / 10;

	return rdata;
}

static int set_cur_volt_mt6355_vproc11(struct buck_ctrl_t *buck_p, unsigned int volt)
{
	unsigned int max_volt = MAX_VSRAM_VOLT + 625;

	return regulator_set_voltage(regulator_proc_b, volt * 10, max_volt * 10);
}

static unsigned int mt6355_vproc11_transfer2pmicval(unsigned int volt)
{
	return ((volt - 40625) + 625 - 1) / 625;
}

static unsigned int mt6355_vproc11_transfer2volt(unsigned int val)
{
	return val * 625 + 40625;
}

static unsigned int mt6355_vproc11_settletime(unsigned int old_volt, unsigned int new_volt)
{
	if (new_volt > old_volt)
		return ((new_volt - old_volt) + 1408 - 1) / 1408 + PMIC_CMD_DELAY_TIME;
	else
		return ((old_volt - new_volt) + 1408 - 1) / 1408 + PMIC_CMD_DELAY_TIME;
}

/* buck_ops_vsram_b */
static unsigned int get_cur_volt_mt6355_vsram_gpu(struct buck_ctrl_t *buck_p)
{
	unsigned int rdata;

	rdata = regulator_get_voltage(regulator_sram_b) / 10;

	return rdata;
}

static int set_cur_volt_mt6355_vsram_gpu(struct buck_ctrl_t *buck_p, unsigned int volt)
{
	unsigned int max_volt = MAX_VSRAM_VOLT + 625;

	return regulator_set_voltage(regulator_sram_b, volt * 10, max_volt * 10);
}

static unsigned int mt6355_vsram_gpu_transfer2pmicval(unsigned int volt)
{
	return ((volt - 51875) + 625 - 1) / 625;
}

static unsigned int mt6355_vsram_gpu_transfer2volt(unsigned int val)
{
	return val * 625 + 51875;
}

static unsigned int mt6355_vsram_gpu_settletime(unsigned int old_volt, unsigned int new_volt)
{
	if (new_volt > old_volt)
		return ((new_volt - old_volt) + 1408 - 1) / 1408 + PMIC_CMD_DELAY_TIME;
	else
		return ((old_volt - new_volt) + 1408 - 1) / 1408 + PMIC_CMD_DELAY_TIME;
}

/* buck_ops_vproc_l */
static unsigned int get_cur_volt_rt5738_vproc(struct buck_ctrl_t *buck_p)
{
	unsigned int rdata;

#ifdef CONFIG_HYBRID_CPU_DVFS
	/* Note : cannot read rt5738 at kernel when sspm enable rt5738 */
	rdata = cpuhvfs_get_cur_volt(MT_CPU_DVFS_L);
#else
	rdata = regulator_get_voltage(regulator_proc_l) / 10;
#endif

	return rdata;
}

static int set_cur_volt_rt5738_vproc(struct buck_ctrl_t *buck_p, unsigned int volt)
{
	unsigned int max_volt = MAX_VSRAM_VOLT + 625;

	return regulator_set_voltage(regulator_proc_l, volt * 10, max_volt * 10);
}

static unsigned int rt5738_vproc_transfer2pmicval(unsigned int volt)
{
	return ((volt - 30000) + 500 - 1) / 500;
}

static unsigned int rt5738_vproc_transfer2volt(unsigned int val)
{
	return val * 500 + 30000;
}

static unsigned int rt5738_vproc_settletime(unsigned int old_volt, unsigned int new_volt)
{
	if (new_volt > old_volt)
		return ((new_volt - old_volt) + 1200 - 1) / 1200 + PMIC_CMD_DELAY_TIME;
	else
		return ((old_volt - new_volt) + 1200 - 1) / 1200 + PMIC_CMD_DELAY_TIME;
}

/* buck_ops_vsram_l */
static unsigned int get_cur_volt_mt6355_vsram_proc(struct buck_ctrl_t *buck_p)
{
	unsigned int rdata;

	rdata = regulator_get_voltage(regulator_sram_l) / 10;

	return rdata;
}

static int set_cur_volt_mt6355_vsram_proc(struct buck_ctrl_t *buck_p, unsigned int volt)
{
	unsigned int max_volt = MAX_VSRAM_VOLT + 625;

	return regulator_set_voltage(regulator_sram_l, volt * 10, max_volt * 10);
}

static unsigned int mt6355_vsram_proc_transfer2pmicval(unsigned int volt)
{
	return ((volt - 51875) + 625 - 1) / 625;
}

static unsigned int mt6355_vsram_proc_transfer2volt(unsigned int val)
{
	return val * 625 + 51875;
}

static unsigned int mt6355_vsram_proc_settletime(unsigned int old_volt, unsigned int new_volt)
{
	if (new_volt > old_volt)
		return ((new_volt - old_volt) + 1877 - 1) / 1877 + PMIC_CMD_DELAY_TIME;
	else
		return ((old_volt - new_volt) + 805 - 1) / 805 + PMIC_CMD_DELAY_TIME;
}

/* upper layer CANNOT use 'set' function in secure path */
static struct buck_ctrl_ops buck_ops_vproc_b = {
	.get_cur_volt       = get_cur_volt_mt6355_vproc11,
	.set_cur_volt       = set_cur_volt_mt6355_vproc11,
	.transfer2pmicval   = mt6355_vproc11_transfer2pmicval,
	.transfer2volt  = mt6355_vproc11_transfer2volt,
	.settletime     = mt6355_vproc11_settletime,
};

static struct buck_ctrl_ops buck_ops_vsram_b = {
	.get_cur_volt       = get_cur_volt_mt6355_vsram_gpu,
	.set_cur_volt       = set_cur_volt_mt6355_vsram_gpu,
	.transfer2pmicval   = mt6355_vsram_gpu_transfer2pmicval,
	.transfer2volt  = mt6355_vsram_gpu_transfer2volt,
	.settletime     = mt6355_vsram_gpu_settletime,
};

static struct buck_ctrl_ops buck_ops_vproc_l = {
	.get_cur_volt       = get_cur_volt_rt5738_vproc,
	.set_cur_volt       = set_cur_volt_rt5738_vproc,
	.transfer2pmicval   = rt5738_vproc_transfer2pmicval,
	.transfer2volt  = rt5738_vproc_transfer2volt,
	.settletime     = rt5738_vproc_settletime,
};

static struct buck_ctrl_ops buck_ops_vsram_l = {
	.get_cur_volt       = get_cur_volt_mt6355_vsram_proc,
	.set_cur_volt       = set_cur_volt_mt6355_vsram_proc,
	.transfer2pmicval   = mt6355_vsram_proc_transfer2pmicval,
	.transfer2volt  = mt6355_vsram_proc_transfer2volt,
	.settletime     = mt6355_vsram_proc_settletime,
};

struct buck_ctrl_t buck_ctrl[NR_MT_BUCK] = {
	[CPU_DVFS_VPROC_B] = {
		.name		= __stringify(BUCK_MT6355_VPROC11),
		.buck_id	= CPU_DVFS_VPROC_B,
		.buck_ops	= &buck_ops_vproc_b,
	},

	[CPU_DVFS_VSRAM_B] = {
		.name		= __stringify(LDO_MT6355_VSRAM_GPU),
		.buck_id	= CPU_DVFS_VSRAM_B,
		.buck_ops	= &buck_ops_vsram_b,
	},

	[CPU_DVFS_VPROC_L] = {
		.name		= __stringify(BUCK_RT5738_VPROC),
		.buck_id	= CPU_DVFS_VPROC_L,
		.buck_ops	= &buck_ops_vproc_l,
	},

	[CPU_DVFS_VSRAM_L] = {
		.name		= __stringify(LDO_MT6355_VSRAM_PROC),
		.buck_id	= CPU_DVFS_VSRAM_L,
		.buck_ops	= &buck_ops_vsram_l,
	},
};

/* PMIC Part */
void prepare_pmic_config(struct mt_cpu_dvfs *p)
{
}

int __attribute__((weak)) sync_dcm_set_mp0_freq(unsigned int mhz)
{
	return 0;
}

int __attribute__((weak)) sync_dcm_set_mp1_freq(unsigned int mhz)
{
	return 0;
}

int __attribute__((weak)) sync_dcm_set_mp2_freq(unsigned int mhz)
{
	return 0;
}

int __attribute__((weak)) sync_dcm_set_cci_freq(unsigned int mhz)
{
	return 0;
}

/* PLL Part */
void prepare_pll_addr(enum mt_cpu_dvfs_pll_id pll_id)
{
	struct pll_ctrl_t *pll_p = id_to_pll_ctrl(pll_id);

	pll_p->armpll_addr = (unsigned int *)(pll_id == PLL_L_CLUSTER ? ARMPLL_L_CON1 :
					      pll_id == PLL_B_CLUSTER ? ARMPLL_B_CON1 :  CCIPLL_CON1);

	pll_p->armpll_div_addr = (unsigned int *)(pll_id == PLL_L_CLUSTER ? CKDIV1_L_CFG :
						  pll_id == PLL_B_CLUSTER ? CKDIV1_B_CFG : CKDIV1_CCI_CFG);
}

unsigned int _cpu_dds_calc(unsigned int khz)
{
	unsigned int dds;

	dds = ((khz / 1000) << 14) / 26;

	return dds;
}

static void adjust_armpll_dds(struct pll_ctrl_t *pll_p, unsigned int vco, unsigned int pos_div)
{
	unsigned int dds;
	unsigned int val;

	dds = _GET_BITS_VAL_(21:0, _cpu_dds_calc(vco));

	val = cpufreq_read(pll_p->armpll_addr) & ~(_BITMASK_(21:0));
	val |= dds;

	cpufreq_write(pll_p->armpll_addr, val | _BIT_(31) /* CHG */);
	udelay(PLL_SETTLE_TIME);
}

static void adjust_posdiv(struct pll_ctrl_t *pll_p, unsigned int pos_div)
{
	unsigned int sel;

	sel = (pos_div == 1 ? 0 :
	       pos_div == 2 ? 1 :
	       pos_div == 4 ? 2 : 0);

	cpufreq_write_mask(pll_p->armpll_addr, 30:28, sel);
	udelay(POS_SETTLE_TIME);
}

static void adjust_clkdiv(struct pll_ctrl_t *pll_p, unsigned int clk_div)
{
	unsigned int sel;

	sel = (clk_div == 1 ? 8 :
	       clk_div == 2 ? 10 :
	       clk_div == 4 ? 11 : 8);

	cpufreq_write_mask(pll_p->armpll_div_addr, 21:17, sel);
}

unsigned char get_posdiv(struct pll_ctrl_t *pll_p)
{
	unsigned char sel, cur_posdiv;

	sel = _GET_BITS_VAL_(30:28, cpufreq_read(pll_p->armpll_addr));
	cur_posdiv = (sel == 0 ? 1 :
		      sel == 1 ? 2 :
		      sel == 2 ? 4 : 1);

	return cur_posdiv;
}

unsigned char get_clkdiv(struct pll_ctrl_t *pll_p)
{
	unsigned char sel, cur_clkdiv;

	sel = _GET_BITS_VAL_(21:17, cpufreq_read(pll_p->armpll_div_addr));
	cur_clkdiv = (sel == 8 ? 1 :
		      sel == 10 ? 2 :
		      sel == 11 ? 4 : 1);

	return cur_clkdiv;
}

static void adjust_freq_hopping(struct pll_ctrl_t *pll_p, unsigned int dds)
{
#ifdef CONFIG_MTK_FREQ_HOPPING
	mt_dfs_armpll(pll_p->hopping_id, dds);
#endif
}

/* Frequency API */
static unsigned int pll_to_clk(unsigned int pll_f, unsigned int ckdiv1)
{
	unsigned int freq = pll_f;

	switch (ckdiv1) {
	case 8:
		break;
	case 9:
		freq = freq * 3 / 4;
		break;
	case 10:
		freq = freq * 2 / 4;
		break;
	case 11:
		freq = freq * 1 / 4;
		break;
	case 16:
		break;
	case 17:
		freq = freq * 4 / 5;
		break;
	case 18:
		freq = freq * 3 / 5;
		break;
	case 19:
		freq = freq * 2 / 5;
		break;
	case 20:
		freq = freq * 1 / 5;
		break;
	case 24:
		break;
	case 25:
		freq = freq * 5 / 6;
		break;
	case 26:
		freq = freq * 4 / 6;
		break;
	case 27:
		freq = freq * 3 / 6;
		break;
	case 28:
		freq = freq * 2 / 6;
		break;
	case 29:
		freq = freq * 1 / 6;
		break;
	default:
		break;
	}

	return freq;
}

static unsigned int _cpu_freq_calc(unsigned int con1, unsigned int ckdiv1)
{
	unsigned int freq;
	unsigned int posdiv;

	posdiv = _GET_BITS_VAL_(30:28, con1);

	con1 &= _BITMASK_(21:0);
	freq = ((con1 * 26) >> 14) * 1000;

	switch (posdiv) {
	case 0:
		break;
	case 1:
		freq = freq / 2;
		break;
	case 2:
		freq = freq / 4;
		break;
	case 3:
		freq = freq / 8;
		break;
	default:
		freq = freq / 16;
		break;
	};

	return pll_to_clk(freq, ckdiv1);
}

unsigned int get_cur_phy_freq(struct pll_ctrl_t *pll_p)
{
	unsigned int con1;
	unsigned int ckdiv1;
	unsigned int cur_khz;

	con1 = cpufreq_read(pll_p->armpll_addr);
	ckdiv1 = cpufreq_read(pll_p->armpll_div_addr);
	ckdiv1 = _GET_BITS_VAL_(21:17, ckdiv1);

	cur_khz = _cpu_freq_calc(con1, ckdiv1);

	cpufreq_ver("@%s: (%s) = cur_khz = %u, con1[0x%p] = 0x%x, ckdiv1_val = 0x%x\n",
		    __func__, pll_p->name, cur_khz, pll_p->armpll_addr, con1, ckdiv1);

	return cur_khz;
}

static void _cpu_clock_switch(struct pll_ctrl_t *pll_p, enum top_ckmuxsel sel)
{
	cpufreq_write_mask(pll_p->armpll_div_addr, 10:9, sel);
}

static enum top_ckmuxsel _get_cpu_clock_switch(struct pll_ctrl_t *pll_p)
{
	return _GET_BITS_VAL_(10:9, cpufreq_read(pll_p->armpll_div_addr));
}

/* upper layer CANNOT use 'set' function in secure path */
static struct pll_ctrl_ops pll_ops_l = {
	.get_cur_freq		= get_cur_phy_freq,
	.set_armpll_dds		= adjust_armpll_dds,
	.set_armpll_posdiv	= adjust_posdiv,
	.set_armpll_clkdiv	= adjust_clkdiv,
	.set_freq_hopping	= adjust_freq_hopping,
	.clksrc_switch		= _cpu_clock_switch,
	.get_clksrc		= _get_cpu_clock_switch,
	.set_sync_dcm		= sync_dcm_set_mp0_freq,
};

static struct pll_ctrl_ops pll_ops_b = {
	.get_cur_freq		= get_cur_phy_freq,
	.set_armpll_dds		= adjust_armpll_dds,
	.set_armpll_posdiv	= adjust_posdiv,
	.set_armpll_clkdiv	= adjust_clkdiv,
	.set_freq_hopping	= adjust_freq_hopping,
	.clksrc_switch		= _cpu_clock_switch,
	.get_clksrc		= _get_cpu_clock_switch,
	.set_sync_dcm		= sync_dcm_set_mp2_freq,
};

static struct pll_ctrl_ops pll_ops_cci = {
	.get_cur_freq		= get_cur_phy_freq,
	.set_armpll_dds		= adjust_armpll_dds,
	.set_armpll_posdiv	= adjust_posdiv,
	.set_armpll_clkdiv	= adjust_clkdiv,
	.set_freq_hopping	= adjust_freq_hopping,
	.clksrc_switch		= _cpu_clock_switch,
	.get_clksrc		= _get_cpu_clock_switch,
	.set_sync_dcm		= sync_dcm_set_cci_freq,
};

struct pll_ctrl_t pll_ctrl[NR_MT_PLL] = {
	[PLL_L_CLUSTER] = {
		.name		= __stringify(PLL_L_CLUSTER),
		.pll_id		= PLL_L_CLUSTER,
		.hopping_id	= FH_PLL1,	/* ARMPLL2 */
		.pll_ops	= &pll_ops_l,
	},

	[PLL_B_CLUSTER] = {
		.name		= __stringify(PLL_B_CLUSTER),
		.pll_id		= PLL_B_CLUSTER,
		.hopping_id	= FH_PLL2,	/* ARMPLL3 */
		.pll_ops	= &pll_ops_b,
	},

	[PLL_CCI_CLUSTER] = {
		.name		= __stringify(PLL_CCI_CLUSTER),
		.pll_id		= PLL_CCI_CLUSTER,
		.hopping_id	= FH_PLL4,	/* CCIPLL */
		.pll_ops	= &pll_ops_cci,
	},
};

/* Always put action cpu at last */
struct hp_action_tbl cpu_dvfs_hp_action[] = {
	{
		.action		= CPU_DOWN_PREPARE,
		.cluster	= MT_CPU_DVFS_L,
		.trigged_core	= 1,
		.hp_action_cfg[MT_CPU_DVFS_L].action_id = FREQ_LOW,
	},
	{
		.action		= CPU_DOWN_PREPARE,
		.cluster	= MT_CPU_DVFS_B,
		.trigged_core	= 1,
		.hp_action_cfg[MT_CPU_DVFS_B].action_id = FREQ_LOW,
	},
	{
		.action		= CPU_DOWN_PREPARE | CPU_TASKS_FROZEN,
		.cluster	= MT_CPU_DVFS_L,
		.trigged_core	= 1,
		.hp_action_cfg[MT_CPU_DVFS_L].action_id = FREQ_LOW,
	},
	{
		.action		= CPU_DOWN_PREPARE | CPU_TASKS_FROZEN,
		.cluster	= MT_CPU_DVFS_B,
		.trigged_core	= 1,
		.hp_action_cfg[MT_CPU_DVFS_B].action_id = FREQ_LOW,
	},
};

unsigned int nr_hp_action = ARRAY_SIZE(cpu_dvfs_hp_action);
static int can_turbo;
void mt_cpufreq_turbo_action(unsigned long action,
	unsigned int *cpus, enum mt_cpu_dvfs_id cluster_id)
{
	can_turbo = 0;
}

int mt_cpufreq_turbo_config(enum mt_cpu_dvfs_id id,
	unsigned int turbo_f, unsigned int turbo_v)
{
	return 1;
}

int mt_cpufreq_regulator_map(struct platform_device *pdev)
{
	regulator_proc_b = regulator_get(&pdev->dev, "vproc11");
	if (GEN_DB_ON(IS_ERR(regulator_proc_b), "vproc11 Get Failed"))
		return -ENODEV;

	regulator_sram_b = regulator_get(&pdev->dev, "vsram_gpu");
	if (GEN_DB_ON(IS_ERR(regulator_sram_b), "vsram_gpu Get Failed"))
		return -ENODEV;

	regulator_proc_l = regulator_get(&pdev->dev, "ext_buck_cpul");
	if (GEN_DB_ON(IS_ERR(regulator_proc_l), "rt5738 Get Failed"))
		return -ENODEV;

	regulator_sram_l = regulator_get(&pdev->dev, "vsram_proc");
	if (GEN_DB_ON(IS_ERR(regulator_sram_l), "Vsram_proc Get Failed"))
		return -ENODEV;

	return 0;
}

int mt_cpufreq_dts_map(void)
{
	struct device_node *node;

	/* apmixed */
	node = of_find_compatible_node(NULL, NULL, APMIXED_NODE);
	if (GEN_DB_ON(!node, "APMIXED Not Found"))
		return -ENODEV;

	apmixed_base = (unsigned long)of_iomap(node, 0);
	if (GEN_DB_ON(!apmixed_base, "APMIXED Map Failed"))
		return -ENOMEM;

	/* mcucfg */
	node = of_find_compatible_node(NULL, NULL, MCUCFG_NODE);
	if (GEN_DB_ON(!node, "MCUCFG Not Found"))
		return -ENODEV;

	mcucfg_base = (unsigned long)of_iomap(node, 0);
	if (GEN_DB_ON(!mcucfg_base, "MCUCFG Map Failed"))
		return -ENOMEM;

	return 0;
}

unsigned int _mt_cpufreq_get_cpu_level(void)
{
	unsigned int lv = CPU_LEVEL_0;

	turbo_flag = 0;

	return lv;
}
