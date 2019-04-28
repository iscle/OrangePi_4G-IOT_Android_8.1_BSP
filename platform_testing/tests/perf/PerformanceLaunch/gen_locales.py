#!/usr/bin/env python

#
# Generates a series of res/values-{locale}/ directories with a strings.xml
# file in each containing 4 resources, many_config_1, many_config_2, many_config_3,
# and many_config_4.
#

import os

template = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="many_config_1">ManyConfig1-{0}</string>
    <string name="many_config_2">ManyConfig1-{0}</string>
    <string name="many_config_3">ManyConfig1-{0}</string>
    <string name="many_config_4">ManyConfig1-{0}</string>
</resources>"""

localeStr = "en_US af_ZA am_ET ar_EG bg_BG bn_BD ca_ES cs_CZ da_DK de_DE el_GR en_AU en_GB en_IN es_ES es_US et_EE eu_ES \
fa_IR fi_FI fr_CA fr_FR gl_ES hi_IN hr_HR hu_HU hy_AM in_ID is_IS it_IT iw_IL ja_JP ka_GE km_KH ko_KR ky_KG lo_LA lt_LT \
lv_LV km_MH kn_IN mn_MN ml_IN mk_MK mr_IN ms_MY my_MM ne_NP nb_NO nl_NL pl_PL pt_BR pt_PT ro_RO ru_RU si_LK sk_SK sl_SI \
sr_RS sv_SE sw_TZ ta_IN te_IN th_TH tl_PH tr_TR uk_UA vi_VN zh_CN zh_HK zh_TW zu_ZA en_XA ar_XB"

locales = [locale.replace("_", "-r") for locale in localeStr.split(" ")]

for locale in locales:
    try:
        os.mkdir("res/values-{0}".format(locale))
    except:
        pass

    with open("res/values-{0}/strings.xml".format(locale), "w") as f:
        f.write(template.format(locale))

