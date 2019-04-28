/************************************************************************
* Copyright (C) 2012-2017, Focaltech Systems (R)��All Rights Reserved.
*
* File Name: Focaltech_test_ft8607.c
*
* Author: Software Development Team, AE
*
* Created: 2016-08-01
*
* Abstract: test item for FT8607
*
************************************************************************/

/*****************************************************************************
* Included header files
*****************************************************************************/

#include "../focaltech_test.h"

/*****************************************************************************
* Private constant and macro definitions using #define
*****************************************************************************/
#define DEVIDE_MODE_ADDR                0x00
#define REG_LINE_NUM                    0x01
#define REG_TX_NUM                      0x02
#define REG_RX_NUM                      0x03
#define REG_RawBuf0                     0x6A
#define REG_RawBuf1                     0x6B
#define REG_OrderBuf0                   0x6C
#define REG_CbBuf0                      0x6E
#define REG_CbAddrH                     0x18
#define REG_CbAddrL                     0x19
#define REG_OrderAddrH                  0x1A
#define REG_OrderAddrL                  0x1B
#define FT_8607_LEFT_KEY_REG             0X1E
#define FT_8607_RIGHT_KEY_REG            0X1F
#define REG_8607_LCD_NOISE_FRAME         0X12
#define REG_8607_LCD_NOISE_START         0X11
#define REG_8607_LCD_NOISE_NUMBER        0X13
#define REG_CLB                          0x04
#define REG_FWVERSION                    0xA6
#define REG_FACTORYID                    0xA8
#define REG_FWCNT                        0x17


/*****************************************************************************
* Private enumerations, structures and unions using typedef
*****************************************************************************/
struct ft8607_test_item {
    bool rawdata_test;
    bool cb_test;
    bool short_test;
    bool lcd_noise_test;
    bool open_test;

};

struct ft8607_basic_threshold {
    int rawdata_test_min;
    int rawdata_test_max;
    bool cb_test_va_check;
    int cb_test_min;
    int cb_test_max;
    bool cb_test_vk_check;
    int cb_test_min_vk;
    int cb_test_max_vk;
    int short_res_min;
    int lcd_noise_test_frame;
    int lcd_noise_test_max;
    int lcd_noise_coefficient;
    int lcd_noise_coefficient_key;
    int open_test_cb_min;
};

enum enumTestItem_FT8607 {
    CODE_FT8607_ENTER_FACTORY_MODE = 0,//All IC are required to test items
    CODE_FT8607_RAWDATA_TEST = 7,
    CODE_FT8607_CB_TEST = 12,
    CODE_FT8607_SHORT_CIRCUIT_TEST = 14,
    CODE_FT8607_LCD_NOISE_TEST = 15,
    CODE_FT8607_OPEN_TEST = 24,
};

/*****************************************************************************
* Static variables
*****************************************************************************/

/*****************************************************************************
* Global variable or extern global variabls/functions
*****************************************************************************/

struct ft8607_test_item ft8607_item;
struct ft8607_basic_threshold ft8607_basic_thr;

/*****************************************************************************
* Static function prototypes
*****************************************************************************/
/////////////////////////////////////////////////////////////
static int ft8607_get_key_num(void);
static int ft8607_enter_factory_mode(void);
static int ft8607_rawdata_test(bool *test_result);
static int ft8607_cb_test(bool *test_result);
static int ft8607_short_test(bool *test_result);
static int ft8607_open_test(bool *test_result);
static int ft8607_lcdnoise_test(bool *test_result);


/************************************************************************
* Name: start_test_ft8607
* Brief:  Test entry. Determine which test item to test
* Input: none
* Output: none
* Return: Test Result, PASS or FAIL
***********************************************************************/

bool start_test_ft8607(void)
{
    bool test_result = true, temp_result = 1;
    int ret;
    int item_count = 0;

    FTS_TEST_FUNC_ENTER();

    //--------------2. test item
    if (0 == test_data.test_num)
        test_result = false;

    ////////Testing process, the order of the test_data.test_item structure of the test items
    for (item_count = 0; item_count < test_data.test_num; item_count++) {

        test_data.test_item_code = test_data.test_item[item_count].itemcode;

        ///////////////////////////////////////////////////////FT8607_ENTER_FACTORY_MODE
        if (CODE_FT8607_ENTER_FACTORY_MODE == test_data.test_item[item_count].itemcode) {
            ret = ft8607_enter_factory_mode();
            if (ERROR_CODE_OK != ret || (!temp_result)) {
                test_result = false;
                test_data.test_item[item_count].testresult = RESULT_NG;
                break;//if this item FAIL, no longer test.
            } else
                test_data.test_item[item_count].testresult = RESULT_PASS;
        }

        ///////////////////////////////////////////////////////FT8607_RAWDATA_TEST
        if (CODE_FT8607_RAWDATA_TEST == test_data.test_item[item_count].itemcode) {
            ret = ft8607_rawdata_test(&temp_result);
            if (ERROR_CODE_OK != ret || (!temp_result)) {
                test_result = false;
                test_data.test_item[item_count].testresult = RESULT_NG;
            } else
                test_data.test_item[item_count].testresult = RESULT_PASS;
        }


        ///////////////////////////////////////////////////////FT8607_CB_TEST
        if (CODE_FT8607_CB_TEST == test_data.test_item[item_count].itemcode) {
            ret = ft8607_cb_test(&temp_result); //
            if (ERROR_CODE_OK != ret || (!temp_result)) {
                test_result = false;
                test_data.test_item[item_count].testresult = RESULT_NG;
            } else
                test_data.test_item[item_count].testresult = RESULT_PASS;
        }

        ///////////////////////////////////////////////////////FT8607_SHORT_TEST
        if (CODE_FT8607_SHORT_CIRCUIT_TEST == test_data.test_item[item_count].itemcode) {
            ret = ft8607_short_test(&temp_result);
            if (ERROR_CODE_OK != ret || (!temp_result)) {
                test_result = false;
                test_data.test_item[item_count].testresult = RESULT_NG;
            } else
                test_data.test_item[item_count].testresult = RESULT_PASS;
        }

        ///////////////////////////////////////////////////////FT8607_Open_TEST
        if (CODE_FT8607_OPEN_TEST == test_data.test_item[item_count].itemcode) {
            ret = ft8607_open_test(&temp_result);
            if (ERROR_CODE_OK != ret || (!temp_result)) {
                test_result = false;
                test_data.test_item[item_count].testresult = RESULT_NG;
            } else
                test_data.test_item[item_count].testresult = RESULT_PASS;
        }

        ///////////////////////////////////////////////////////FT8607_LCD_NOISE_TEST
        if (CODE_FT8607_LCD_NOISE_TEST == test_data.test_item[item_count].itemcode) {
            ret =  ft8607_lcdnoise_test(&temp_result);
            if (ERROR_CODE_OK != ret || (!temp_result)) {
                test_result = false;
                test_data.test_item[item_count].testresult = RESULT_NG;
            } else
                test_data.test_item[item_count].testresult = RESULT_PASS;
        }

    }

    //--------------4. return result
    return test_result;
}

/************************************************************************
* Name: ft8607_enter_factory_mode
* Brief:  Check whether TP can enter Factory Mode, and do some thing
* Input: none
* Output: none
* Return: Comm Code. Code = 0x00 is OK, else fail.
***********************************************************************/
static int ft8607_enter_factory_mode(void)
{
    int ret = 0;

    ret = enter_factory_mode();
    if (ret < 0) {
        FTS_TEST_SAVE_INFO("enter factory mode fail, can't get tx/rx num");
        return ret;
    }
    ret = ft8607_get_key_num();
    if (ret < 0) {
        FTS_TEST_SAVE_INFO("get key num fail");
        return ret;
    }

    return ret;

}
/************************************************************************
* Name: ft8607_rawdata_test
* Brief:  TestItem: Rawdata Test. Check if Rawdata is within the range.
* Input: none
* Output: test_result, PASS or FAIL
* Return: Comm Code. Code = 0x00 is OK, else fail.
***********************************************************************/
static int ft8607_rawdata_test(bool *test_result)
{
    int ret = ERROR_CODE_OK;
    bool tmp_result = true;
    int i = 0;
    int *rawdata = NULL;

    FTS_TEST_SAVE_INFO("\n\n\n==============================Test Item: -------- Raw Data Test\n\n");

    memset(test_data.buffer, 0, ((test_data.screen_param.tx_num + 1) * test_data.screen_param.rx_num) * sizeof(int));
    rawdata = test_data.buffer;

    ret = enter_factory_mode();
    if (ERROR_CODE_OK != ret) {
        FTS_TEST_SAVE_INFO("\n Failed to Enter factory mode. Error Code: %d", ret);
        return ret;
    }

    for (i = 0 ; i < 3; i++) //Lost 3 Frames, In order to obtain stable data
        ret = get_rawdata_incell(rawdata);
    if ( ERROR_CODE_OK != ret ) {
        FTS_TEST_SAVE_INFO("\nFailed to get Raw Data!! Error Code: %d\n", ret);
        return ret;
    }
    //----------------------------------------------------------Show RawData
    show_data_incell(rawdata, true);

    //----------------------------------------------------------To Determine RawData if in Range or not
    tmp_result = compare_detailthreshold_data_incell(rawdata, test_data.incell_detail_thr.rawdata_test_min, test_data.incell_detail_thr.rawdata_test_max, true);

    //////////////////////////////Save Test Data
    save_testdata_incell(rawdata, "RawData Test", 0, test_data.screen_param.tx_num + 1, test_data.screen_param.rx_num, 1);

    if (tmp_result) {
        * test_result = true;
        FTS_TEST_SAVE_INFO("\n==========RawData Test is OK!\n");
    } else {
        * test_result = false;
        FTS_TEST_SAVE_INFO("\n==========RawData Test is NG!\n");
    }
    return ret;
}


/************************************************************************
* Name: ft8607_cb_test
* Brief:  TestItem: Cb Test. Check if Cb is within the range.
* Input: none
* Output: test_result, PASS or FAIL
* Return: Comm Code. Code = 0x00 is OK, else fail.
***********************************************************************/
static int ft8607_cb_test(bool *test_result)
{
    bool tmp_result = true;
    int ret = ERROR_CODE_OK;
    int i = 0;
    int tx_num = 0;
    int rx_num = 0;
    int key_num = 0;
    bool include_key = false;
    int *cbdata = NULL;

    FTS_TEST_SAVE_INFO("\n\n\n==============================Test Item: --------  CB Test\n\n");
    tx_num = test_data.screen_param.tx_num;
    rx_num = test_data.screen_param.rx_num;
    key_num = test_data.screen_param.key_num_total;
    memset(test_data.buffer, 0, ((tx_num + 1) * rx_num) * sizeof(int));
    cbdata = test_data.buffer;
    include_key = ft8607_basic_thr.cb_test_vk_check;

    ret = enter_factory_mode();
    if (ERROR_CODE_OK != ret) {
        tmp_result = false;
        FTS_TEST_SAVE_INFO("\n Failed to Enter factory mode. Error Code: %d", ret);
        goto TEST_ERR;
    }

    for ( i = 0; i < 10; i++) {
        FTS_TEST_SAVE_INFO("\n start chipclb times:%d. ", i);
        ret = chip_clb_incell();
        sys_delay(50);
        if ( ret == ERROR_CODE_OK) {
            break;
        }
    }
    if ( i == 10) {
        FTS_TEST_SAVE_INFO("\n ReCalib Failed.");
        tmp_result = false;
    }

    ret = get_cb_incell( 0, tx_num * rx_num + key_num, cbdata );
    if ( ERROR_CODE_OK != ret ) {
        tmp_result = false;
        FTS_TEST_SAVE_INFO("\nFailed to get CB value...\n");
        goto TEST_ERR;
    }

    //------------------------------------------------Show CbData
    show_data_incell(cbdata, include_key);

    //----------------------------------------------------------To Determine Cb if in Range or not
    tmp_result = compare_detailthreshold_data_incell(
                     cbdata,
                     test_data.incell_detail_thr.cb_test_min,
                     test_data.incell_detail_thr.cb_test_max,
                     include_key);

    //////////////////////////////Save Test Data
    save_testdata_incell(cbdata, "CB Test", 0, tx_num + 1, rx_num, 1);

    if (tmp_result) {
        * test_result = true;
        FTS_TEST_SAVE_INFO("\n==========CB Test is OK!\n");
    } else {
        * test_result = false;
        FTS_TEST_SAVE_INFO("\n==========CB Test is NG!\n");
    }

    return ret;
TEST_ERR:

    * test_result = false;
    FTS_TEST_SAVE_INFO("\n==========CB Test is NG!\n");
    return ret;

}

/************************************************************************
* Name: ft8607_short_test
* Brief:  Get short circuit test mode data, judge whether there is a short circuit
* Input: test_result
* Output: test_result, PASS or FAIL
* Return: Comm Code. Code = 0x00 is OK, else fail.
***********************************************************************/
static int ft8607_short_test(bool *test_result)
{
    int ret = ERROR_CODE_OK;
    bool temp_result = true;
    int res_min = 0;
    int all_adc_data_num = 0;
    unsigned char tx_num = 0, rx_num = 0, channel_num = 0;
    int row = 0;
    int col = 0;
    int i = 0;
    int tmp_adc = 0;
    int value_min = 0;
    int value_max = 0;
    int *adcdata = NULL;

    FTS_TEST_SAVE_INFO("\n==============================Test Item: -------- Short Circuit Test \r\n");

    memset(test_data.buffer, 0, ((test_data.screen_param.tx_num + 1) * test_data.screen_param.rx_num) * sizeof(int));
    adcdata = test_data.buffer;

    ret = enter_factory_mode();
    if (ERROR_CODE_OK != ret) {
        temp_result = false;
        FTS_TEST_SAVE_INFO("\n Failed to Enter factory mode. Error Code: %d", ret);
        goto TEST_END;
    }

    ret = read_reg(0x02, &tx_num);
    ret = read_reg(0x03, &rx_num);
    if (ERROR_CODE_OK != ret) {
        temp_result = false;
        FTS_TEST_SAVE_INFO("\n// Failed to read reg. Error Code: %d", ret);
        goto TEST_END;
    }

    FTS_TEST_SAVE_INFO("\n tx_num:%d.  rx_num:%d.", tx_num, rx_num);

    channel_num = tx_num + rx_num;
    all_adc_data_num = tx_num * rx_num + test_data.screen_param.key_num_total;

    for (i = 0; i < 1; i++) {
        ret = weakshort_get_adc_data_incell(rx_num, all_adc_data_num * 2, adcdata);
        sys_delay(50);
        if (ERROR_CODE_OK != ret) {
            temp_result = false;
            FTS_TEST_SAVE_INFO("\n // Failed to get AdcData. Error Code: %d", ret);
            goto TEST_END;
        }
    }

    //show ADCData
#if 0
    FTS_TEST_SAVE_INFO("\nADCData:\n");
    for (i = 0; i < all_adc_data_num; i++) {
        FTS_TEST_SAVE_INFO("%-4d  ", adc_data[i]);
        if (0 == (i + 1) % rx_num) {
            FTS_TEST_SAVE_INFO("\n\n");
        }
    }
    FTS_TEST_SAVE_INFO("\n\n");
#endif

    for ( row = 0; row < test_data.screen_param.tx_num + 1; ++row ) {
        for ( col = 0; col < test_data.screen_param.rx_num; ++col ) {
            tmp_adc = adcdata[row * rx_num + col];
            if (tmp_adc > 2007) tmp_adc = 2007;
            adcdata[row * rx_num + col] = (tmp_adc * 100) / (2047 - tmp_adc);
        }
    }

    show_data_incell(adcdata, true);

    //////////////////////// analyze
    res_min = ft8607_basic_thr.short_res_min;
    value_min = res_min;
    value_max = 100000000;
    FTS_TEST_SAVE_INFO("\n Short Circuit test , Set_Range=(%d, %d). \n", \
                       value_min, value_max);

    temp_result = compare_data_incell(adcdata, value_min, value_max, value_min, value_max, true);

    save_testdata_incell( adcdata, "Short Circuit Test", 0, test_data.screen_param.tx_num + 1, test_data.screen_param.rx_num, 1 );

TEST_END:

    if (temp_result) {
        FTS_TEST_SAVE_INFO("\n==========Short Circuit Test is OK!");
        * test_result = true;
    } else {
        FTS_TEST_SAVE_INFO("\n==========Short Circuit Test is NG!");
        * test_result = false;
    }

    return ret;
}

/************************************************************************
* Name: ft8607_open_test
* Brief:  Check if channel is opens
* Input: test_result
* Output: test_result, PASS or FAIL
* Return: Comm Code. Code = 0x00 is OK, else fail.
***********************************************************************/
static int ft8607_open_test(bool *test_result)
{
    int ret = ERROR_CODE_OK;
    bool tmp_result = true;
    int index = 0;
    unsigned char ch_value = 0xff;
    int min = 0;
    int max = 0;
    unsigned char reg_value = 0;
    int *opendata = NULL;

    FTS_TEST_SAVE_INFO("\n\r\n\r\n==============================Test Item: --------  Open Test");

    memset(test_data.buffer, 0, ((test_data.screen_param.tx_num + 1) * test_data.screen_param.rx_num) * sizeof(int));
    opendata = test_data.buffer;

    ret = enter_factory_mode();
    sys_delay(50);
    if (ERROR_CODE_OK != ret) {
        tmp_result = false;
        FTS_TEST_SAVE_INFO("\n\r\n//=========  Enter Factory Failed!");
        goto TEST_ERR;
    }

    if (test_data.screen_param.selected_ic == IC_FT8607) {
        ret = write_reg(0xf8, 0x01);
        for ( index = 0; index < 40; ++index ) {
            ret = read_reg( 0xf8, &ch_value );
            if ( ret != ERROR_CODE_OK ) {
                tmp_result = false;
                break;
            }
            if ( ch_value == 0xaa )  break;
            sys_delay( 50 );
        }

        if ( ch_value != 0xaa ) {
            tmp_result = false;
            FTS_TEST_SAVE_INFO( "\r\n//Open Test Timeout!" );
            goto TEST_ERR;
        }
    }

    if (test_data.screen_param.selected_ic == IC_FT8607U) {
        ret = read_reg(0x21, &reg_value);
        ret = write_reg(0x21, 2);
        if (ERROR_CODE_OK != ret) {
            tmp_result = false;
            FTS_TEST_SAVE_INFO("\n\r\n//=========  write reg failed !");
            goto TEST_ERR;
        }

        ////auto clb
        ret = chip_clb_incell();
        if (ERROR_CODE_OK != ret) {
            tmp_result = false;
            FTS_TEST_SAVE_INFO("\n\r\n//========= auto clb Failed  !");
            goto TEST_ERR;
        }
    }

    //get cb data
    ret = get_cb_incell( 0, test_data.screen_param.tx_num * test_data.screen_param.rx_num + test_data.screen_param.key_num_total, opendata );
    if ( ERROR_CODE_OK != ret ) {
        tmp_result = false;
        FTS_TEST_SAVE_INFO("\n\r\n\r\n//=========get CB Failed!");

    }
    if (test_data.screen_param.selected_ic == IC_FT8607U) {
        ret = write_reg( 0x21, reg_value );
    }
    ////auto clb
    ret = chip_clb_incell();
    if (ERROR_CODE_OK != ret) {
        tmp_result = false;
        FTS_TEST_SAVE_INFO("\n\r\n//========= auto clb Failed!");
        goto TEST_ERR;
    }

    // show open data
    show_data_incell(opendata, false);

    //  analyze
    min = ft8607_basic_thr.open_test_cb_min;
    max = 200;
    tmp_result = compare_data_incell(opendata, min, max, min, max, false);

    // save data
    save_testdata_incell(opendata, "Open Test", 0, test_data.screen_param.tx_num, test_data.screen_param.rx_num, 1);
    if (tmp_result) {
        * test_result = true;
        FTS_TEST_SAVE_INFO("\n==========Open Test is OK!\n");
    } else {
        * test_result = false;
        FTS_TEST_SAVE_INFO("\n==========Open Test is NG!\n");
    }

    return ret;

TEST_ERR:

    chip_clb_incell();
    * test_result = false;
    FTS_TEST_SAVE_INFO("\n==========Open Test is NG!\n");
    return ret;
}

/************************************************************************
* Name: ft8607_lcdnoise_test
* Brief:  TestItem: LCD NoiseTest. Check if Noise is within the range.
* Input: test_result
* Output: test_result, PASS or FAIL
* Return: Comm Code. Code = 0x00 is OK, else fail.
***********************************************************************/
static int ft8607_lcdnoise_test(bool *test_result)
{
    int ret = ERROR_CODE_COMM_ERROR;
    bool result_flag = true;
    unsigned char old_mode = 0;
    int row = 0;
    int col = 0;
    int value_min = 0;
    int value_max = 0;
    int value_max_vk = 0;
    int retry = 0;
    unsigned char status = 0xff;
    unsigned char ch_noise_value_va = 0xff, ch_noise_value_key = 0xff;
    int *lcdnoise = NULL;

    FTS_TEST_SAVE_INFO("\n==============================Test Item: -------- LCD Noise Test \r\n");

    memset(test_data.buffer, 0, ((test_data.screen_param.tx_num + 1) * test_data.screen_param.rx_num) * sizeof(int));
    lcdnoise = test_data.buffer;

    ret = enter_factory_mode();
    if (ERROR_CODE_OK != ret) {
        result_flag = false;
        FTS_TEST_SAVE_INFO("\n Failed to Enter factory mode. Error Code: %d", ret);
        goto TEST_END;
    }

    //switch is differ mode
    read_reg( 0x06, &old_mode );
    write_reg( 0x06, 1 );

    //clear  FW counter
    ret = write_reg( 0x01, 0xAD );

    //set scan number
    FTS_TEST_SAVE_INFO("\n lcd_noise_test_frame:%d. ",  ft8607_basic_thr.lcd_noise_test_frame );
    ret = write_reg( REG_8607_LCD_NOISE_FRAME,  (unsigned char)(ft8607_basic_thr.lcd_noise_test_frame & 0xFF) );
    ret = write_reg( REG_8607_LCD_NOISE_FRAME + 1, (unsigned char)( (ft8607_basic_thr.lcd_noise_test_frame >> 8) & 0xFF) );

    //  start test.
    ret = write_reg( REG_8607_LCD_NOISE_START, 0x01 );

    //check status
    for ( retry = 0; retry < 50; ++retry ) {
        status = 0xff;
        ret = read_reg( REG_8607_LCD_NOISE_START, &status );
        if ( ERROR_CODE_OK == ret && status == 0x00  ) {
            FTS_TEST_SAVE_INFO("\n read register 0x%x, get value is:%d. \n ", REG_8607_LCD_NOISE_START, status);
            break;
        }
        sys_delay( 500 );
    }
    if ( retry == 50 ) {
        result_flag = false;
        FTS_TEST_SAVE_INFO("\nScan Noise Time Out!");
        goto TEST_END;
    }

    ret = read_mass_data(FACTORY_REG_RAWDATA_ADDR, test_data.screen_param.tx_num * test_data.screen_param.rx_num * 2 + test_data.screen_param.key_num_total * 2, lcdnoise );
    if ( ERROR_CODE_OK != ret ) {
        FTS_TEST_SAVE_INFO("\nFailed to Read Data.\n");
        result_flag = false;
        goto TEST_END;
    }

    for ( row = 0; row < test_data.screen_param.tx_num + 1; ++row ) {
        for ( col = 0; col < test_data.screen_param.rx_num; ++col ) {
            lcdnoise[row * test_data.screen_param.rx_num + col] = focal_abs( lcdnoise[row * test_data.screen_param.rx_num + col]);
        }
    }

    //show data of lcd_noise
    show_data_incell(lcdnoise, true);

    //////////////////////// analyze
    if (test_data.screen_param.selected_ic == IC_FT8607U) {
        value_min = 0;
        value_max = ft8607_basic_thr.lcd_noise_coefficient * ch_noise_value_va * 32 / 100;
        FTS_TEST_SAVE_INFO("\nVA_Set_Range=(%d, %d). ", value_min, value_max);
        result_flag = compare_data_incell(lcdnoise, value_min, value_max, value_min, value_max, true);
    }

    else if (test_data.screen_param.selected_ic == IC_FT8607) {
        value_min = 0;
        value_max = ft8607_basic_thr.lcd_noise_coefficient * ch_noise_value_va * 32 / 100;
        value_max_vk = ft8607_basic_thr.lcd_noise_coefficient_key * ch_noise_value_key * 32 / 100;
        FTS_TEST_SAVE_INFO("\nVA_Set_Range=(%d, %d). Key_Set_Range=(%d, %d). ", value_min, value_max, value_min, value_max_vk);
        result_flag = compare_data_incell(lcdnoise, value_min, value_max, value_min, value_max_vk, true);
    }

    //  Save Test Data
    save_testdata_incell(lcdnoise, "LCD Noise Test", 0, test_data.screen_param.tx_num + 1, test_data.screen_param.rx_num, 1);

TEST_END:

    write_reg( 0x06, old_mode );
    sys_delay( 20 );
    write_reg( REG_8607_LCD_NOISE_START, 0x00 );
    if (result_flag) {
        FTS_TEST_SAVE_INFO("\n==========LCD Noise Test is OK!");
        * test_result = true;
    } else {
        FTS_TEST_SAVE_INFO("\n==========LCD Noise Test is NG!");
        * test_result = false;
    }
    return ret;
}
static int ft8607_get_key_num(void)
{
    int ret = 0;
    int i = 0;
    u8 keyval = 0;

    test_data.screen_param.key_num = 0;
    for (i = 0; i < 3; i++) {
        ret = read_reg( FT_8607_LEFT_KEY_REG, &keyval );
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
        ret = read_reg( FT_8607_RIGHT_KEY_REG, &keyval );
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

void init_basicthreshold_ft8607(char *strIniFile)
{
    char str[512] = {0};

    FTS_TEST_FUNC_ENTER();

    /////////////////////////////////// RawData Test
    GetPrivateProfileString("Basic_Threshold", "RawDataTest_Min", "5000", str, strIniFile);
    ft8607_basic_thr.rawdata_test_min = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "RawDataTest_Max", "11000", str, strIniFile);
    ft8607_basic_thr.rawdata_test_max = fts_atoi(str);

    //////////////////////////////////////////////////////////// CB Test
    GetPrivateProfileString("Basic_Threshold", "CBTest_VA_Check", "1", str, strIniFile);
    ft8607_basic_thr.cb_test_va_check = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "CBTest_Min", "3", str, strIniFile);
    ft8607_basic_thr.cb_test_min = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "CBTest_Max", "60", str, strIniFile);
    ft8607_basic_thr.cb_test_max = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "CBTest_VKey_Check", "1", str, strIniFile);
    ft8607_basic_thr.cb_test_vk_check = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "CBTest_Min_Vkey", "3", str, strIniFile);
    ft8607_basic_thr.cb_test_min_vk = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "CBTest_Max_Vkey", "100", str, strIniFile);
    ft8607_basic_thr.cb_test_max_vk = fts_atoi(str);

    //////////////////////////////////////////////////////////// Short Circuit Test
    GetPrivateProfileString("Basic_Threshold", "ShortCircuit_ResMin", "200", str, strIniFile);
    ft8607_basic_thr.short_res_min = fts_atoi(str);

    //////////////////////////////////////////////////////////// LCD Noise Test
    GetPrivateProfileString("Basic_Threshold", "LCD_NoiseTest_Frame", "50", str, strIniFile);
    ft8607_basic_thr.lcd_noise_test_frame = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "LCD_NoiseTest_Max", "30", str, strIniFile);
    ft8607_basic_thr.lcd_noise_test_max = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "LCD_NoiseTest_Coefficient", "50", str, strIniFile);
    ft8607_basic_thr.lcd_noise_coefficient = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "LCD_NoiseTest_Coefficient_Key", "50", str, strIniFile);
    ft8607_basic_thr.lcd_noise_coefficient_key = fts_atoi(str);

    //////////////////////////////////////////////////////////// Open Test
    GetPrivateProfileString("Basic_Threshold", "OpenTest_CBMin", "64", str, strIniFile);
    ft8607_basic_thr.open_test_cb_min = fts_atoi(str);

    FTS_TEST_FUNC_EXIT();
}

void init_testitem_ft8607(char  *strIniFile)
{
    char str[512] = {0};

    FTS_TEST_FUNC_ENTER();

    /////////////////////////////////// RawData Test
    GetPrivateProfileString("TestItem", "RAWDATA_TEST", "1", str, strIniFile);
    ft8607_item.rawdata_test = fts_atoi(str);

    /////////////////////////////////// CB_TEST
    GetPrivateProfileString("TestItem", "CB_TEST", "1", str, strIniFile);
    ft8607_item.cb_test = fts_atoi(str);

    /////////////////////////////////// SHORT_CIRCUIT_TEST
    GetPrivateProfileString("TestItem", "SHORT_CIRCUIT_TEST", "1", str, strIniFile);
    ft8607_item.short_test = fts_atoi(str);

    /////////////////////////////////// LCD_NOISE_TEST
    GetPrivateProfileString("TestItem", "LCD_NOISE_TEST", "0", str, strIniFile);
    ft8607_item.lcd_noise_test = fts_atoi(str);

    /////////////////////////////////// OPEN_TEST
    GetPrivateProfileString("TestItem", "OPEN_TEST", "0", str, strIniFile);
    ft8607_item.open_test = fts_atoi(str);
    FTS_TEST_FUNC_EXIT();
}

void init_detailthreshold_ft8607(char *ini)
{
    FTS_TEST_FUNC_ENTER();

    OnInit_InvalidNode(ini);
    OnInit_DThreshold_RawDataTest(ini);
    OnInit_DThreshold_CBTest(ini);

    FTS_TEST_FUNC_EXIT();
}

void set_testitem_sequence_ft8607(void)
{

    test_data.test_num = 0;

    FTS_TEST_FUNC_ENTER();

    //////////////////////////////////////////////////Enter Factory Mode
    fts_set_testitem(CODE_FT8607_ENTER_FACTORY_MODE);

    //////////////////////////////////////////////////Short Test
    if ( ft8607_item.short_test == 1) {
        fts_set_testitem(CODE_FT8607_SHORT_CIRCUIT_TEST);
    }

    //////////////////////////////////////////////////Open Test
    if ( ft8607_item.open_test == 1) {
        fts_set_testitem(CODE_FT8607_OPEN_TEST);
    }
    //////////////////////////////////////////////////CB_TEST
    if ( ft8607_item.cb_test == 1) {
        fts_set_testitem(CODE_FT8607_CB_TEST);
    }

    //////////////////////////////////////////////////LCD_NOISE_TEST
    if ( ft8607_item.lcd_noise_test == 1) {
        fts_set_testitem(CODE_FT8607_LCD_NOISE_TEST);
    }

    //////////////////////////////////////////////////RawData Test
    if ( ft8607_item.rawdata_test == 1) {
        fts_set_testitem(CODE_FT8607_RAWDATA_TEST);
    }

    FTS_TEST_FUNC_EXIT();
}

struct test_funcs test_func_ft8607 = {
    .ic_series = TEST_ICSERIES(IC_FT8607),
    .init_testitem = init_testitem_ft8607,
    .init_basicthreshold = init_basicthreshold_ft8607,
    .init_detailthreshold = init_detailthreshold_ft8607,
    .set_testitem_sequence  = set_testitem_sequence_ft8607,
    .start_test = start_test_ft8607,
};
