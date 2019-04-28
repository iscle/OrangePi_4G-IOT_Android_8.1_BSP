/************************************************************************
* Copyright (C) 2012-2017, Focaltech Systems (R)£¬All Rights Reserved.
*
* File Name: focaltech_test_ft8716.c
*
* Author: LiuWeiGuang
*
* Created: 2016-08-01
*
* Abstract: test item for FT8716
*
************************************************************************/

/*****************************************************************************
* Included header files
*****************************************************************************/
#include "../focaltech_test.h"

/*****************************************************************************
* Private constant and macro definitions using #define
*****************************************************************************/

/*****************************************************************************
* Private enumerations, structures and unions using typedef
*****************************************************************************/
struct ft8716_test_item {
    bool rawdata_test;
    bool cb_test;
    bool short_circuit_test;
    bool open_test;
    bool lcd_noise_test;
    bool key_short_test;
};

struct ft8716_basic_threshold {
    bool rawdata_test_va_check;
    int rawdata_test_min;
    int rawdata_test_max;
    bool rawdata_test_vkey_check;
    int rawdata_test_min_vkey;
    int rawdata_test_max_vkey;
    bool cb_test_va_check;
    int cb_test_min;
    int cb_test_max;
    bool cb_test_vkey_check;
    int cb_test_min_vkey;
    int cb_test_max_vkey;
    bool cb_test_vkey_dcheck_check;
    bool short_va_check;
    bool short_vkey_check;
    int short_va_resistor_min;
    int short_vkey_resistor_min;
    int open_test_cb_min;
    bool open_test_k1_check;
    int open_test_k1_threshold;
    bool open_test_k2_check;
    int open_test_k2_threshold;
    int lcd_noise_test_frame;
    int lcd_noise_test_coefficient;
    int lcd_noise_test_coefficient_key;
    u8 lcd_noise_test_mode;
    int keyshort_k1;
    int keyshort_cb_max;
};

enum enumTestItem_FT8716 {
    CODE_FT8716_ENTER_FACTORY_MODE = 0,
    CODE_FT8716_RAWDATA_TEST = 7,
    CODE_FT8716_CB_TEST = 12,
    CODE_FT8716_SHORT_CIRCUIT_TEST = 14,
    CODE_FT8716_OPEN_TEST = 15,
    CODE_FT8716_LCD_NOISE_TEST = 19,
    CODE_FT8716U_KEY_SHORT_TEST = 26,
};

/*****************************************************************************
* Static variables
*****************************************************************************/

/*****************************************************************************
* Global variable or extern global variabls/functions
*****************************************************************************/
static struct ft8716_basic_threshold ft8716_basicthreshold;
static struct ft8716_test_item ft8716_testitem;

/************************************************************************
* Name: sqrt_new
* Brief:  calculate sqrt of input.
* Input: unsigned int n
* Output: none
* Return: sqrt of n.
***********************************************************************/
static u32 sqrt_new(u32 n)
{
    u32  val = 0, last = 0;
    u8 i = 0;;

    if (n < 6) {
        if (n < 2) {
            return n;
        }
        return n / 2;
    }
    val = n;
    i = 0;
    while (val > 1) {
        val >>= 1;
        i++;
    }
    val <<= (i >> 1);
    val = (val + val + val) >> 1;
    do {
        last = val;
        val = ((val + n / val) >> 1);
    } while (focal_abs(val - last) > 1);
    return val;
}

int get_key_num(void)
{
    int ret = 0;
    int i = 0;
    u8 keyval = 0;

    test_data.screen_param.key_num = 0;
    for (i = 0; i < 3; i++) {
        ret = read_reg( FACTORY_REG_LEFT_KEY, &keyval );
        if (0 == ret) {
            if (((keyval >> 0) & 0x01)) {
                test_data.screen_param.left_key1 = true;
                ++test_data.screen_param.key_num;
            }
            if (((keyval >> 1) & 0x01)) {
                test_data.screen_param.left_key2 = true;
                ++test_data.screen_param.key_num;
            }
            if (((keyval >> 2) & 0x01)) {
                test_data.screen_param.left_key3 = true;
                ++test_data.screen_param.key_num;
            }
            break;
        } else {
            sys_delay(150);
            continue;
        }
    }

    if (i >= 3) {
        FTS_TEST_SAVE_INFO("can't get left key num");
        return ret;
    }

    for (i = 0; i < 3; i++) {
        ret = read_reg( FACTORY_REG_RIGHT_KEY, &keyval );
        if (0 == ret) {
            if (((keyval >> 0) & 0x01)) {
                test_data.screen_param.right_key1 = true;
                ++test_data.screen_param.key_num;
            }
            if (((keyval >> 1) & 0x01)) {
                test_data.screen_param.right_key2 = true;
                ++test_data.screen_param.key_num;
            }
            if (((keyval >> 2) & 0x01)) {
                test_data.screen_param.right_key3 = true;
                ++test_data.screen_param.key_num;
            }
            break;
        } else {
            sys_delay(150);
            continue;
        }
    }

    if (i >= 3) {
        FTS_TEST_SAVE_INFO("can't get right key num");
        return ret;
    }

    return 0;
}


/************************************************************************
* Name: ft8716_enter_factory_mode
* Brief:  Check whether TP can enter Factory Mode, and do some thing
* Input: none
* Output: none
* Return: return 0 if succuss
***********************************************************************/
int ft8716_enter_factory_mode(void)
{
    int ret = 0;
    u8 keyFit = 0;

    ret = enter_factory_mode();
    if (ret < 0) {
        FTS_TEST_SAVE_INFO("enter factory mode fail, can't get tx/rx num");
        return ret;
    }

    ret = get_key_num();
    if (ret < 0) {
        FTS_TEST_SAVE_INFO("get key num fail");
        return ret;
    }

    ret = read_reg(0xFC, &keyFit );
    if ((0 == ret)  && ((keyFit & 0x0F) == 0x01)) {
        test_data.screen_param.key_flag = 1;
    }

    return ret;
}

/************************************************************************
* Name: ft8716_rawdata_test
* Brief:
* Input:
* Output:
* Return: return 0 if success
***********************************************************************/
int ft8716_rawdata_test(bool *test_result)
{
    int ret = 0;
    bool temp_result = true;
    int i = 0;
    u8 tx_num = 0;
    u8 rx_num = 0;
    bool key_support = false;
    int *rawbuf = NULL;

    FTS_TEST_SAVE_INFO("\n\n==============================Test Item: -------- Raw Data Test\n");

    tx_num = test_data.screen_param.tx_num;
    rx_num = test_data.screen_param.rx_num;
    key_support = ft8716_basicthreshold.rawdata_test_vkey_check;

    memset(test_data.buffer, 0, ((test_data.screen_param.tx_num + 1) * test_data.screen_param.rx_num) * sizeof(int));
    rawbuf = test_data.buffer;

    ret = enter_factory_mode();
    if (ret) {
        FTS_TEST_SAVE_INFO("Failed to Enter Factory Mode\n");
        goto RAWDATA_TEST_ERR;
    }
    //----------------------------------------------------------Read RawData
    /* get 3rd Frames, In order to obtain stable data */
    for (i = 0 ; i < 3; i++) {
        ret = get_rawdata_incell(rawbuf);
    }
    if (ret) {
        FTS_TEST_SAVE_INFO("Failed to get Raw Data,ret=%d", ret);
        goto RAWDATA_TEST_ERR;
    }

    //----------------------------------------------------------Show RawData
    show_data_incell(rawbuf, key_support);

    //----------------------------------------------------------To Determine RawData if in Range or not
    temp_result = compare_detailthreshold_data_incell(rawbuf, test_data.incell_detail_thr.rawdata_test_min, test_data.incell_detail_thr.rawdata_test_max, key_support);

    //////////////////////////////Save Test Data
    save_testdata_incell(rawbuf, "RawData Test", 0, tx_num + 1, rx_num, 1);
    //----------------------------------------------------------Return Result
    FTS_TEST_SAVE_INFO("\n==========RawData Test is %s. \n\n", (temp_result ? "OK" : "NG"));

    if (temp_result) {
        *test_result = true;
    } else {
        *test_result = false;
    }

    return 0;

RAWDATA_TEST_ERR:
    *test_result = false;
    FTS_TEST_SAVE_INFO("\n\n/==========RAWDATA Test is NG!");
    return ret;
}

/************************************************************************
* Name: ft8716_cb_test
* Brief:
* Input:
* Output:
* Return: return 0 if success
***********************************************************************/
int ft8716_cb_test(bool *test_result)
{
    bool temp_result = true;
    unsigned char ret = ERROR_CODE_OK;
    int chx = 0;
    int chy = 0;
    int tmptx = 0;
    bool key_support = false;
    u8 key_cb_width = 0;
    int key_num = 0;
    int cb_byte_num = 0;
    u8 tx_num = 0;
    u8 rx_num = 0;
    int *cb_buf = NULL;
    int *tmpbuf = NULL;

    FTS_TEST_SAVE_INFO("\n\n==============================Test Item: --------  CB Test\n");

    memset(test_data.buffer, 0, ((test_data.screen_param.tx_num + 1) * test_data.screen_param.rx_num) * sizeof(int));
    cb_buf = test_data.buffer;

    key_support = ft8716_basicthreshold.cb_test_vkey_check;

    ret = enter_factory_mode();
    if (ret) {
        FTS_TEST_SAVE_INFO("Failed to Enter Factory Mode\n");
        return ret;
    }

    ret = chip_clb_incell();
    if (ret) {
        FTS_TEST_SAVE_INFO("\r\n//========= auto clb Failed!");
        goto CB_TEST_ERR;
    }

    ret = read_reg(FACTORY_REG_KEY_CBWIDTH, &key_cb_width);
    if (ret) {
        FTS_TEST_SAVE_INFO("\r\n//=========  Read Reg 0x0B Failed!");
        goto CB_TEST_ERR;
    }

    if (0 == key_cb_width) {
        key_num = test_data.screen_param.key_num_total;
    } else {
        key_num = test_data.screen_param.key_num_total * 2;
    }

    tx_num = test_data.screen_param.tx_num;
    rx_num = test_data.screen_param.rx_num;
    cb_byte_num = (int)(tx_num * rx_num  + key_num);

    tmpbuf = fts_malloc(cb_byte_num * sizeof(u8));
    if (NULL == tmpbuf) {
        FTS_TEST_SAVE_INFO("cb memory malloc fail");
        goto CB_TEST_ERR;
    }
    memset(tmpbuf, 0, cb_byte_num);
    memset(cb_buf, 0, cb_byte_num * sizeof(int));
    ret = get_cb_incell(0, cb_byte_num, tmpbuf);
    if (ret) {
        FTS_TEST_SAVE_INFO("Failed to get CB value\n");
        goto CB_TEST_ERR;
    }

    ///VA area
    for (chx = 0; chx < tx_num; chx++) {
        tmptx = chx * rx_num;
        for (chy = 0; chy < rx_num; chy++) {
            cb_buf[tmptx + chy] = tmpbuf[tmptx + chy];
        }
    }

    tmptx = tx_num * rx_num;
    for (chy = 0; chy < key_num; chy++) {
        if (key_cb_width != 0) {
            cb_buf[tmptx + chy / 2] = (short)((tmpbuf[tmptx + chy] & 0x01 ) << 8) + tmpbuf[tmptx + chy + 1];
            chy++;
        } else {
            cb_buf[tmptx + chy] = tmpbuf[tmptx + chy];
        }
    }
    //------------------------------------------------Show CbData
    show_data_incell(cb_buf, key_support);

    //----------------------------------------------------------To Determine Cb if in Range or not
    temp_result = compare_detailthreshold_data_incell(cb_buf, test_data.incell_detail_thr.cb_test_min, test_data.incell_detail_thr.cb_test_max, key_support);

    //////////////////////////////Save Test Data
    save_testdata_incell(cb_buf, "CB Test", 0, tx_num + 1, rx_num, 1);
    FTS_TEST_SAVE_INFO("\n========== CB Test is %s. \n\n", (temp_result ? "OK" : "NG"));

    *test_result = temp_result;
    if (tmpbuf) {
        fts_free(tmpbuf);
    }
    return 0;

CB_TEST_ERR:
    FTS_TEST_SAVE_INFO("\n\n/==========CB Test is NG!");
    *test_result = false;
    if (tmpbuf) {
        fts_free(tmpbuf);
    }
    return ret;
}

/************************************************************************
* Name: ft8716_short_test
* Brief:
* Input:
* Output:
* Return: return 0 if success
***********************************************************************/
int ft8716_short_test(bool *test_result)
{
    int ret = 0;
    bool temp_result = true;
    int adc_data_num = 0;
    int chx = 0;
    int chy = 0;
    int adc_tmp = 0;
    int min = 0;
    int min_vk = 0;
    int max = 0;
    bool key_support = false;
    u8 tx_num = 0;
    u8 rx_num = 0;
    u8 channel_num = 0;
    int *adc_data = NULL;
    int tmptx = 0;
    bool include_key = false;

    FTS_TEST_SAVE_INFO("\r\n\r\n==============================Test Item: -------- Short Circuit Test \r\n");

    memset(test_data.buffer, 0, ((test_data.screen_param.tx_num + 1) * test_data.screen_param.rx_num) * sizeof(int));
    adc_data = test_data.buffer;

    ret = enter_factory_mode();
    if (ret) {
        FTS_TEST_SAVE_INFO("\r\n\r\n// Failed to Enter factory mode.");
        goto SHORT_TEST_ERR;
    }

    /* get adc & short resistor data */
    tx_num = test_data.screen_param.tx_num;
    rx_num = test_data.screen_param.rx_num;
    channel_num = tx_num + rx_num;

    if (test_data.screen_param.selected_ic == IC_FT8716) {
        adc_data_num = tx_num * rx_num + test_data.screen_param.key_num_total;
        include_key = true;
    } else  {
        adc_data_num = tx_num * rx_num ;
        include_key = false;
    }

    memset(adc_data, 0, adc_data_num * sizeof(int));
    ret = weakshort_get_adc_data_incell(channel_num, adc_data_num * 2, adc_data);
    if (ret) {
        FTS_TEST_SAVE_INFO("// Failed to get AdcData. Error Code: %d", ret);
        goto SHORT_TEST_ERR;
    }

    /* revise and calculate resistor */
    for (chx = 0; chx < tx_num + 1; chx++) {
        tmptx = chx * rx_num;
        for (chy = 0; chy < rx_num; chy++) {
            adc_tmp = adc_data[tmptx + chy];
            if (adc_tmp > 2007)
                adc_tmp = 2007;
            adc_data[tmptx + chy] = (adc_tmp * 100) / (2047 - adc_tmp);
        }
    }

    show_data_incell(adc_data, include_key);

    /* check short threshold */
    key_support =  ft8716_basicthreshold.short_vkey_check;
    min = ft8716_basicthreshold.short_va_resistor_min;
    min_vk = ft8716_basicthreshold.short_vkey_resistor_min;
    max = 100000000;
    temp_result = compare_data_incell(adc_data, min, max, min_vk, max, key_support);

    FTS_TEST_SAVE_INFO("\n========== ShortCircuit Test is %s. \n\n", (temp_result  ? "OK" : "NG"));

    save_testdata_incell( adc_data, "Short Circuit Test", 0, tx_num + 1, rx_num, 1 );

    *test_result = temp_result;
    return 0;

SHORT_TEST_ERR:
    *test_result = false;
    FTS_TEST_SAVE_INFO(" ShortCircuit Test is NG. \n\n");

    return ret;
}

/************************************************************************
* Name: ft8716u_key_short_test
* Brief:  Get Key short circuit test mode data, judge whether there is a short circuit
* Input: none
* Output: none
* Return: return 0 if success
***********************************************************************/
int ft8716u_key_short_test(bool *test_result)
{
    int ret = 0;
    bool temp_result = true;
    u8 key_num = 0;
    int i = 0;
    u8 test_finish = 0xFF;
    short tmpval;
    u8 k1_value = 0;
    int key_cb[KEY_NUM_MAX * 2] = { 0 };

    FTS_TEST_SAVE_INFO("\r\n\r\n==============================Test Item: -------- KEY Short Test \r\n");

    ret = enter_factory_mode();
    if (ret) {
        FTS_TEST_SAVE_INFO("\r\n\r\n// Failed to Enter factory mode.");
        goto KEYSHORT_TEST_ERR;
    }

    ret = read_reg(FACTORY_REG_K1, &k1_value);
    if (ret) {
        FTS_TEST_SAVE_INFO("\r\n//=========  Read K1 Reg Failed!");
        goto KEYSHORT_TEST_ERR;
    }

    ret = write_reg(FACTORY_REG_K1, ft8716_basicthreshold.keyshort_k1);
    if (ret) {
        FTS_TEST_SAVE_INFO("\r\n//=========  Write K1 Reg Failed!");
        goto KEYSHORT_TEST_ERR;
    }

    ret = wait_state_update();
    if (ret) {
        FTS_TEST_SAVE_INFO("\r\n//=========Wait State Update Failed!");
        goto KEYSHORT_TEST_ERR;
    }

    //Start KEY short Test
    ret = write_reg(0x2E, 0x01);
    if (ret) {
        FTS_TEST_SAVE_INFO("start key short test fail");
        goto KEYSHORT_TEST_ERR;
    }

    //check test is finished or not
    for ( i = 0; i < 20; ++i) {
        ret =  read_reg(0x2F, &test_finish);
        if ((0 == ret) && (0 == test_finish))
            break;

        sys_delay(50);
    }
    if (i >= 20) {
        FTS_TEST_SAVE_INFO("\r\n Test is not finished.\r\n");
        goto KEYSHORT_TEST_ERR;
    }

    if (test_data.screen_param.key_flag)
        key_num = test_data.screen_param.key_num * 2;
    else
        key_num = test_data.screen_param.key_num_total * 2;

    ret = get_cb_incell(0, key_num, key_cb);
    if (ret) {
        FTS_TEST_SAVE_INFO("\r\n\r\n//=========  CB test Failed!");
        goto KEYSHORT_TEST_ERR;
    }

    for ( i = 0; i < key_num; i += 2) {
        tmpval = (short)((key_cb[i] & 0x01 ) << 8) + key_cb[i + 1];
        if (tmpval  > ft8716_basicthreshold.keyshort_cb_max) {
            temp_result = false;
            FTS_TEST_SAVE_INFO("Point( 0, %-2d): %-9d  ", i / 2, tmpval);
        }
    }

    //Restore K1 Value, start CB calibration
    ret = write_reg(FACTORY_REG_K1, k1_value);
    if (ret) {
        FTS_TEST_SAVE_INFO("\r\n//========= restore k1 value fail");
        goto KEYSHORT_TEST_ERR;
    }

    ret = chip_clb_incell();
    if (ret) {
        FTS_TEST_SAVE_INFO("\r\n//========= auto clb Failed!");
        goto KEYSHORT_TEST_ERR;
    }

    FTS_TEST_SAVE_INFO(" KEY Short Test is %s. \n\n", (temp_result  ? "OK" : "NG"));

    *test_result = temp_result;
    return 0;

KEYSHORT_TEST_ERR:
    *test_result = false;
    FTS_TEST_SAVE_INFO("\r\n\r\n==========//KEY Short Test is NG!");

    return ret;
}

/************************************************************************
* Name: ft8716_open_test
* Brief:  Check if channel is open
* Input:
* Output:
* Return: return 0 if success
***********************************************************************/
int ft8716_open_test(bool *test_result)
{
    int ret = 0;
    bool temp_result = true;
    int cb_min = 0;
    int cb_max = 0;
    int i = 0;
    u8 gip_mode = 0xFF;
    u8 source_mode = 0xFF;
    u8 k1_value = 0;
    u8 k2_value = 0;
    u8 tx_num = 0;
    u8 rx_num = 0;
    int cb_byte_num = 0;
    int *cb_buf = NULL;
    int *tmpbuf = NULL;

    FTS_TEST_SAVE_INFO("\r\n\r\n==============================Test Item: --------  Open Test");

    memset(test_data.buffer, 0, ((test_data.screen_param.tx_num + 1) * test_data.screen_param.rx_num) * sizeof(int));
    cb_buf = test_data.buffer;

    ret = enter_factory_mode();
    if (ret) {
        FTS_TEST_SAVE_INFO("\r\n//=========  Enter Factory Failed!");
        goto OPEN_TEST_ERR;
    }

    /* set driver mode */
    if (test_data.screen_param.selected_ic != IC_FT8716) {
        ret = read_reg(FACTORY_REG_SOURCE_DRIVER_MODE, &source_mode);
        if (ret) {
            FTS_TEST_SAVE_INFO("\r\n//========= read source driver mode fail");
            goto OPEN_TEST_ERR;
        }
    }

    ret = read_reg(FACTORY_REG_GIP_DRIVER_MODE, &gip_mode);
    if (ret) {
        FTS_TEST_SAVE_INFO("\r\n//========= read gip driver mode fail");
        goto OPEN_TEST_ERR;
    }

    if (test_data.screen_param.selected_ic != IC_FT8716) {
        ret = write_reg(FACTORY_REG_SOURCE_DRIVER_MODE, 0x03);
        if (ret) {
            FTS_TEST_SAVE_INFO("\r\n//========= write source driver mode fail");
            goto OPEN_TEST_ERR;
        }
    }
    ret = write_reg(FACTORY_REG_GIP_DRIVER_MODE, 0x02);
    if (ret) {
        FTS_TEST_SAVE_INFO("\r\n//========= write gip driver mode fail");
        goto OPEN_TEST_ERR;
    }
    sys_delay(50);

    if (wait_state_update()) {
        FTS_TEST_SAVE_INFO("\r\n//=========Wait State Update Failed!");
        goto OPEN_TEST_ERR;
    }

    /* set k1/k2 */
    if (ft8716_basicthreshold.open_test_k1_check) {
        ret = read_reg(FACTORY_REG_K1, &k1_value);
        if (ret) {
            FTS_TEST_SAVE_INFO("\r\n//=========  Read Reg Failed!");
            goto OPEN_TEST_ERR;
        }
        ret = write_reg(FACTORY_REG_K1, ft8716_basicthreshold.open_test_k1_threshold);
        if (ret) {
            FTS_TEST_SAVE_INFO("\r\n//=========  Write Reg Failed!");
            goto OPEN_TEST_ERR;
        }
    }

    if (ft8716_basicthreshold.open_test_k2_check) {
        ret = read_reg(FACTORY_REG_K2, &k2_value);
        if (ret) {
            FTS_TEST_SAVE_INFO("\r\n//=========  Read Reg Failed!");
            goto OPEN_TEST_ERR;
        }
        ret = write_reg(FACTORY_REG_K2, ft8716_basicthreshold.open_test_k2_threshold);
        if (ret) {
            FTS_TEST_SAVE_INFO("\r\n//=========  Write Reg Failed!");
            goto OPEN_TEST_ERR;
        }
    }

    /* get cb data */
    ret = chip_clb_incell();
    if (ret) {
        FTS_TEST_SAVE_INFO("\r\n//========= auto clb Failed!");
        goto OPEN_TEST_ERR;
    }

    tx_num = test_data.screen_param.tx_num;
    rx_num = test_data.screen_param.rx_num;
    cb_byte_num = (int)(tx_num * rx_num );

    tmpbuf = fts_malloc(cb_byte_num * sizeof(u8));
    if (NULL == tmpbuf) {
        FTS_TEST_SAVE_INFO("cb memory malloc fail");
        goto OPEN_TEST_ERR;
    }
    memset(tmpbuf, 0, cb_byte_num);
    memset(cb_buf, 0, cb_byte_num * sizeof(int));
    ret = get_cb_incell(0, cb_byte_num, tmpbuf);
    if (ret) {
        FTS_TEST_SAVE_INFO("\r\n\r\n//=========get CB Failed!");
        goto OPEN_TEST_ERR;
    }

    for (i = 0; i < cb_byte_num; i++) {
        cb_buf[i] = tmpbuf[i];
    }

    show_data_incell(cb_buf, false);

    cb_min = ft8716_basicthreshold.open_test_cb_min;
    cb_max = 256;
    temp_result = compare_data_incell(cb_buf, cb_min, cb_max, 0, 0, false);

    save_testdata_incell(cb_buf, "Open Test", 0, tx_num, rx_num, 1);

    /* restore register */
    if (ft8716_basicthreshold.open_test_k1_check) {
        ret = write_reg(FACTORY_REG_K1, k1_value);
        if (ret) {
            FTS_TEST_SAVE_INFO("\r\n//=========  restore K1 fail");
            goto OPEN_TEST_ERR;
        }
    }

    if (ft8716_basicthreshold.open_test_k2_check) {
        ret = write_reg(FACTORY_REG_K2, k2_value);
        if (ret) {
            FTS_TEST_SAVE_INFO("\r\n//=========  restore K2 fail");
            goto OPEN_TEST_ERR;
        }
    }

    ret = write_reg(FACTORY_REG_GIP_DRIVER_MODE, gip_mode);
    if (ret) {
        FTS_TEST_SAVE_INFO("\r\n//=========  restore GIP DRV MODE fail");
        goto OPEN_TEST_ERR;
    }

    if (test_data.screen_param.selected_ic != IC_FT8716) {
        ret = write_reg(FACTORY_REG_SOURCE_DRIVER_MODE, source_mode);
        if (ret) {
            FTS_TEST_SAVE_INFO("\r\n//=========  restore SOURCE DRV MODE fail");
            goto OPEN_TEST_ERR;
        }
    }
    sys_delay(50);

    ret = chip_clb_incell();
    if (ret) {
        FTS_TEST_SAVE_INFO("\r\n//========= auto clb Failed!");
        goto OPEN_TEST_ERR;
    }
    FTS_TEST_SAVE_INFO("\n\r==========Open Test is %s. \n\n", (temp_result  ? "OK" : "NG"));

    if (tmpbuf)
        fts_free(tmpbuf);
    *test_result = temp_result;
    return 0;

OPEN_TEST_ERR:
    if (tmpbuf)
        fts_free(tmpbuf);
    *test_result = false;
    FTS_TEST_SAVE_INFO("\r\nOpen Test is NG. \n\n");

    return ret;
}

/************************************************************************
 * Name: ft8716_lcd_noise_test
 * Brief: obtain is differ data and calculate the corresponding type
 *        of noise value.
 * Input:
 * Output:
 * Return: return 0 if success
 ***********************************************************************/
int ft8716_lcd_noise_test(bool *test_result)
{
    int ret = 0;
    bool temp_result = true;
    int frame_num = 0;
    int i = 0;
    int chx = 0;
    int chy = 0;
    u8 tx_num = 0;
    u8 rx_num = 0;
    int tmptx = 0;
    int va_num = 0;
    u8 key_num = 0;
    int max = 0;
    int max_vk = 0;
    u8 act_lcdnoise_num = 0;
    u8 reg_value = 0;
    int *diff_buf = NULL;

    FTS_TEST_SAVE_INFO("\r\n\r\n==============================Test Item: -------- LCD Noise Test \r\n");

    tx_num = test_data.screen_param.tx_num;
    rx_num = test_data.screen_param.rx_num;
    memset(test_data.buffer, 0, ((tx_num + 1) * rx_num) * sizeof(int));
    diff_buf = test_data.buffer;

    ret = enter_factory_mode();
    if (ret) {
        FTS_TEST_SAVE_INFO("Enter Factory Mode fail\n");
        goto LCDNOISE_TEST_ERR;
    }

    /* write data select */
    ret = write_reg(FACTORY_REG_DATA_SELECT, 0x01);
    if (ret) {
        FTS_TEST_SAVE_INFO("write data select fail");
        goto LCDNOISE_TEST_ERR;
    }

    frame_num = ft8716_basicthreshold.lcd_noise_test_frame;
    ret = write_reg(FACTORY_REG_LCD_NOISE_FRAME, frame_num / 4);
    if (ret) {
        FTS_TEST_SAVE_INFO("\r\n write lcd noise frame fail");
        goto LCDNOISE_TEST_ERR;
    }

    ret = write_reg(FACTORY_REG_LCD_NOISE_START, 0x01);
    if (ret) {
        FTS_TEST_SAVE_INFO("start lcd noise test fail");
        goto LCDNOISE_TEST_ERR;
    }

    sys_delay(frame_num * 8);
    for (i = 0; i < frame_num; i++) {
        ret = read_reg(FACTORY_REG_LCD_NOISE_START, &reg_value );
        if ((0 == ret) && (0x00 == reg_value)) {
            sys_delay(FACTORY_TEST_DELAY);
            ret = read_reg(DEVIDE_MODE_ADDR, &reg_value );
            if ((0 == ret) && (0x00 == (reg_value >> 7)))
                break;
        }

        sys_delay(50);
    }
    if (i >= frame_num) {
        ret = write_reg(FACTORY_REG_LCD_NOISE_START, 0x00);
        if (ret) {
            FTS_TEST_SAVE_INFO("write 0x00 to lcd noise start reg fail");
        } else
            ret = -EIO;

        FTS_TEST_SAVE_INFO( "\r\nLCD NOISE Time Over" );
        goto LCDNOISE_TEST_ERR;
    }

    key_num = test_data.screen_param.key_num_total;
    va_num = tx_num * rx_num;
    memset(diff_buf, 0, (tx_num + 1) * rx_num * sizeof(int));
    //--------------------------------------------Read RawData
    /* Read Data for va Area */
    ret = write_reg(FACTORY_REG_LINE_ADDR, 0xAD);
    if (ret) {
        FTS_TEST_SAVE_INFO("wirte AD to reg01 fail");
        return ret;
    }
    ret = read_mass_data(FACTORY_REG_RAWDATA_ADDR, va_num * 2, diff_buf);
    if (ret) {
        FTS_TEST_SAVE_INFO("Failed to Get VA RawData\n");
        goto LCDNOISE_TEST_ERR;
    }

    /* Read Data for key Area */
    ret = write_reg(FACTORY_REG_LINE_ADDR, 0xAE);
    if (ret) {
        FTS_TEST_SAVE_INFO("wirte AE to reg01 fail");
        return ret;
    }
    ret = read_mass_data(FACTORY_REG_RAWDATA_ADDR, key_num * 2, diff_buf + va_num);
    if (ret) {
        FTS_TEST_SAVE_INFO("Failed to Get KEY RawData\n");
        goto LCDNOISE_TEST_ERR;
    }

    ret = write_reg(FACTORY_REG_DATA_SELECT, 0x00);
    ret = write_reg(FACTORY_REG_LCD_NOISE_START, 0x00);
    if (ret) {
        FTS_TEST_SAVE_INFO( "\r\nRestore Failed" );
        goto LCDNOISE_TEST_ERR;
    }

    ret = read_reg(FACTORY_REG_LCD_NOISE_NUMBER, &act_lcdnoise_num);
    if (0 == act_lcdnoise_num) {
        act_lcdnoise_num = 1;
    }

    for (chx = 0; chx < tx_num + 1; chx++) {
        tmptx = chx * rx_num;
        for (chy = 0; chy < rx_num; chy++ ) {
            if (1 == ft8716_basicthreshold.lcd_noise_test_mode)
                diff_buf[tmptx + chy] = sqrt_new(diff_buf[tmptx + chy] / act_lcdnoise_num);
        }
    }

    // show data
    show_data_incell(diff_buf, true);

    max = ft8716_basicthreshold.lcd_noise_test_coefficient * test_data.va_touch_thr * 32 / 100;
    max_vk = ft8716_basicthreshold.lcd_noise_test_coefficient_key * test_data.key_touch_thr * 32 / 100;
    temp_result = compare_data_incell(diff_buf, 0, max, 0, max_vk, true);

    save_testdata_incell(diff_buf, "LCD Noise Test", 0, tx_num + 1, rx_num, 1 );

    FTS_TEST_SAVE_INFO(" \n ==========LCD Noise Test is %s. \n\n", (temp_result  ? "OK" : "NG"));
    *test_result = temp_result;
    return 0;

LCDNOISE_TEST_ERR:
    write_reg(FACTORY_REG_DATA_SELECT, 0x00);
    write_reg(FACTORY_REG_LCD_NOISE_START, 0x00);
    *test_result = false;
    FTS_TEST_SAVE_INFO("LCD Noise Test is NG. \n\n");
    return ret;
}

/************************************************************************
* Name: start_test_ft8716
* Brief: test entry
* Input:
* Output:
* Return: Test Result, PASS or FAIL
***********************************************************************/
bool start_test_ft8716(void)
{
    int ret = 0;
    bool test_result = true;
    bool temp_result = true;
    int item_count = 0;
    u8 item_code = 0;

    if (0 == test_data.test_num) {
        FTS_TEST_SAVE_INFO("test item == 0\n");
        return false;
    }

    for (item_count = 0; item_count < test_data.test_num; item_count++) {
        test_data.test_item_code = test_data.test_item[item_count].itemcode;
        item_code = test_data.test_item[item_count].itemcode;

        /* FT8716_ENTER_FACTORY_MODE */
        if (CODE_FT8716_ENTER_FACTORY_MODE == item_code) {
            ret = ft8716_enter_factory_mode();
            if (ret || (!temp_result)) {
                test_result = false;
                test_data.test_item[item_count].testresult = RESULT_NG;
                break; /* if this item FAIL, no longer test */
            } else
                test_data.test_item[item_count].testresult = RESULT_PASS;
        }

        /* FT8716_RAWDATA_TEST */
        if (CODE_FT8716_RAWDATA_TEST == item_code) {
            ret = ft8716_rawdata_test(&temp_result);
            if (ret || (!temp_result)) {
                test_result = false;
                test_data.test_item[item_count].testresult = RESULT_NG;
            } else
                test_data.test_item[item_count].testresult = RESULT_PASS;
        }

        /* FT8716_CB_TEST */
        if (CODE_FT8716_CB_TEST == item_code) {
            ret = ft8716_cb_test(&temp_result); //
            if (ret || (!temp_result)) {
                test_result = false;
                test_data.test_item[item_count].testresult = RESULT_NG;
            } else
                test_data.test_item[item_count].testresult = RESULT_PASS;
        }

        /* FT8716_ShortCircuit_TEST */
        if (CODE_FT8716_SHORT_CIRCUIT_TEST == item_code) {
            ret = ft8716_short_test(&temp_result);
            if (ret || (!temp_result)) {
                test_result = false;
                test_data.test_item[item_count].testresult = RESULT_NG;
            } else
                test_data.test_item[item_count].testresult = RESULT_PASS;
        }

        /* FT8716_Open_TEST */
        if (CODE_FT8716_OPEN_TEST == item_code) {
            ret = ft8716_open_test(&temp_result);
            if (ret || (!temp_result)) {
                test_result = false;
                test_data.test_item[item_count].testresult = RESULT_NG;
            } else
                test_data.test_item[item_count].testresult = RESULT_PASS;
        }

        /* FT8716_LCDNoise_TEST */
        if (CODE_FT8716_LCD_NOISE_TEST == item_code) {
            ret = ft8716_lcd_noise_test(&temp_result);
            if (ret || (!temp_result)) {
                test_result = false;
                test_data.test_item[item_count].testresult = RESULT_NG;
            } else
                test_data.test_item[item_count].testresult = RESULT_PASS;
        }

        /* FT8716U_KEY_SHORT_TEST */
        if (CODE_FT8716U_KEY_SHORT_TEST == item_code) {
            ret = ft8716u_key_short_test(&temp_result);
            if (ret || (!temp_result)) {
                test_result = false;
                test_data.test_item[item_count].testresult = RESULT_NG;
            } else
                test_data.test_item[item_count].testresult = RESULT_PASS;
        }


    }

    //--------------4. return result
    return test_result;

}

void init_testitem_ft8716(char *strIniFile)
{
    char str[512];

    FTS_TEST_FUNC_ENTER();

    /////////////////////////////////// RawData Test
    GetPrivateProfileString("TestItem", "RAWDATA_TEST", "1", str, strIniFile);
    ft8716_testitem.rawdata_test = fts_atoi(str);

    /////////////////////////////////// CB_TEST
    GetPrivateProfileString("TestItem", "CB_TEST", "1", str, strIniFile);
    ft8716_testitem.cb_test = fts_atoi(str);

    /////////////////////////////////// SHORT_CIRCUIT_TEST
    GetPrivateProfileString("TestItem", "SHORT_CIRCUIT_TEST", "1", str, strIniFile);
    ft8716_testitem.short_circuit_test = fts_atoi(str);

    /////////////////////////////////// OPEN_TEST
    GetPrivateProfileString("TestItem", "OPEN_TEST", "0", str, strIniFile);
    ft8716_testitem.open_test = fts_atoi(str);

    /////////////////////////////////// LCD_NOISE_TEST
    GetPrivateProfileString("TestItem", "LCD_NOISE_TEST", "0", str, strIniFile);
    ft8716_testitem.lcd_noise_test = fts_atoi(str);
    //////////////////////////////////////////////////////////// KEY_SHORT_TEST
    GetPrivateProfileString("TestItem", "KEY_SHORT_TEST", "0", str, strIniFile);
    ft8716_testitem.key_short_test = fts_atoi(str);

    FTS_TEST_FUNC_EXIT();
}

void init_basicthreshold_ft8716(char *strIniFile)
{
    char str[512];

    FTS_TEST_FUNC_ENTER();

    //////////////////////////////////////////////////////////// RawData Test
    GetPrivateProfileString("Basic_Threshold", "RawDataTest_VA_Check", "1", str, strIniFile);
    ft8716_basicthreshold.rawdata_test_va_check = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "RawDataTest_Min", "5000", str, strIniFile);
    ft8716_basicthreshold.rawdata_test_min = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "RawDataTest_Max", "11000", str, strIniFile);
    ft8716_basicthreshold.rawdata_test_max = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "RawDataTest_VKey_Check", "1", str, strIniFile);
    ft8716_basicthreshold.rawdata_test_vkey_check = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "RawDataTest_Min_VKey", "5000", str, strIniFile);
    ft8716_basicthreshold.rawdata_test_min_vkey = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "RawDataTest_Max_VKey", "11000", str, strIniFile);
    ft8716_basicthreshold.rawdata_test_max_vkey = fts_atoi(str);

    //////////////////////////////////////////////////////////// CB Test
    GetPrivateProfileString("Basic_Threshold", "CBTest_VA_Check", "1", str, strIniFile);
    ft8716_basicthreshold.cb_test_va_check = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "CBTest_Min", "3", str, strIniFile);
    ft8716_basicthreshold.cb_test_min = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "CBTest_Max", "100", str, strIniFile);
    ft8716_basicthreshold.cb_test_max = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "CBTest_VKey_Check", "1", str, strIniFile);
    ft8716_basicthreshold.cb_test_vkey_check = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "CBTest_Min_Vkey", "3", str, strIniFile);
    ft8716_basicthreshold.cb_test_min_vkey = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "CBTest_Max_Vkey", "100", str, strIniFile);
    ft8716_basicthreshold.cb_test_max_vkey = fts_atoi(str);

    //////////////////////////////////////////////////////////// Short Circuit Test  VA,Key
    GetPrivateProfileString("Basic_Threshold", "ShortCircuit_VA_Check", "1", str, strIniFile);
    ft8716_basicthreshold.short_va_check = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "ShortCircuit_VKey_Check", "1", str, strIniFile);
    ft8716_basicthreshold.short_vkey_check = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "ShortCircuit_VA_ResMin", "200", str, strIniFile);
    ft8716_basicthreshold.short_va_resistor_min = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "ShortCircuit_Key_ResMin", "200", str, strIniFile);
    ft8716_basicthreshold.short_vkey_resistor_min = fts_atoi(str);


    ////////////////////////////////////////////////////////////open test
    GetPrivateProfileString("Basic_Threshold", "OpenTest_CBMin", "100", str, strIniFile);
    ft8716_basicthreshold.open_test_cb_min = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "OpenTest_Check_K1", "0", str, strIniFile);
    ft8716_basicthreshold.open_test_k1_check = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "OpenTest_K1Threshold", "30", str, strIniFile);
    ft8716_basicthreshold.open_test_k1_threshold = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "OpenTest_Check_K2", "0", str, strIniFile);
    ft8716_basicthreshold.open_test_k2_check = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "OpenTest_K2Threshold", "5", str, strIniFile);
    ft8716_basicthreshold.open_test_k2_threshold = fts_atoi(str);


    ////////////////////////////////////////////////////////////LCDNoiseTest
    GetPrivateProfileString("Basic_Threshold", "LCDNoiseTest_FrameNum", "200", str, strIniFile);
    ft8716_basicthreshold.lcd_noise_test_frame = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "LCDNoiseTest_Coefficient", "60", str, strIniFile);
    ft8716_basicthreshold.lcd_noise_test_coefficient = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "LCDNoiseTest_Coefficient_Key", "60", str, strIniFile);
    ft8716_basicthreshold.lcd_noise_test_coefficient_key = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "LCDNoiseTest_NoiseMode", "0", str, strIniFile);
    ft8716_basicthreshold.lcd_noise_test_mode = fts_atoi(str);

    ////////////////////////////////////////////////////////////KEYShrotTest
    GetPrivateProfileString("Basic_Threshold", "KEY_Short_Test_K1_Value", "54", str, strIniFile);
    ft8716_basicthreshold.keyshort_k1 = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "KEY_Short_Test_CB_Max", "300", str, strIniFile);
    ft8716_basicthreshold.keyshort_cb_max = fts_atoi(str);

    FTS_TEST_FUNC_EXIT();
}

void init_detailthreshold_ft8716(char *ini)
{
    FTS_TEST_FUNC_ENTER();

    OnInit_InvalidNode(ini);
    OnInit_DThreshold_CBTest(ini);
    OnThreshold_VkAndVaRawDataSeparateTest(ini);

    FTS_TEST_FUNC_EXIT();
}

void set_testitem_sequence_ft8716(void)
{
    test_data.test_num = 0;

    FTS_TEST_FUNC_ENTER();

    //////////////////////////////////////////////////Enter Factory Mode
    fts_set_testitem(CODE_FT8716_ENTER_FACTORY_MODE);

    //////////////////////////////////////////////////OPEN_TEST
    if ( ft8716_testitem.open_test == 1) {
        fts_set_testitem(CODE_FT8716_OPEN_TEST);
    }

    //////////////////////////////////////////////////SHORT_CIRCUIT_TEST
    if ( ft8716_testitem.short_circuit_test == 1) {
        fts_set_testitem(CODE_FT8716_SHORT_CIRCUIT_TEST) ;
    }

    //////////////////////////////////////////////////CB_TEST
    if ( ft8716_testitem.cb_test == 1) {
        fts_set_testitem(CODE_FT8716_CB_TEST);
    }

    //////////////////////////////////////////////////LCD_NOISE_TEST
    if ( ft8716_testitem.lcd_noise_test == 1) {
        fts_set_testitem(CODE_FT8716_LCD_NOISE_TEST);
    }

    //////////////////////////////////////////////////RawData Test
    if ( ft8716_testitem.rawdata_test == 1) {
        fts_set_testitem(CODE_FT8716_RAWDATA_TEST);
    }

    //////////////////////////////////////////////////KEY_SHORT_TEST  for 8716U
    if ( ft8716_testitem.key_short_test == 1) {
        fts_set_testitem(CODE_FT8716U_KEY_SHORT_TEST) ;
    }
    FTS_TEST_FUNC_EXIT();

}

struct test_funcs test_func_ft8716 = {
    .ic_series = TEST_ICSERIES(IC_FT8716),
    .init_testitem = init_testitem_ft8716,
    .init_basicthreshold = init_basicthreshold_ft8716,
    .init_detailthreshold = init_detailthreshold_ft8716,
    .set_testitem_sequence  = set_testitem_sequence_ft8716,
    .start_test = start_test_ft8716,
};
