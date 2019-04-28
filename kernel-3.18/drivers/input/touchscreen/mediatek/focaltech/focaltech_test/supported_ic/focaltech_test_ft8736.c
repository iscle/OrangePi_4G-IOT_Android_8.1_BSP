/************************************************************************
* Copyright (C) 2012-2017, Focaltech Systems (R)£¬All Rights Reserved.
*
* File Name: focaltech_test_ft8716.c
*
* Author: Software Development
*
* Created: 2016-08-01
*
* Abstract: test item for FT8736
*
************************************************************************/

/*******************************************************************************
* Included header files
*******************************************************************************/
#include "../focaltech_test.h"

/*******************************************************************************
* Private constant and macro definitions using #define
*******************************************************************************/
/////////////////////////////////////////////////Reg FT8736
#define DEVIDE_MODE_ADDR    0x00
#define REG_LINE_NUM    0x01
#define REG_TX_NUM  0x02
#define REG_RX_NUM  0x03
#define FT8736_LEFT_KEY_REG    0X1E
#define FT8736_RIGHT_KEY_REG   0X1F
#define REG_CbAddrH         0x18
#define REG_CbAddrL         0x19
#define REG_OrderAddrH      0x1A
#define REG_OrderAddrL      0x1B
#define REG_RawBuf0         0x6A
#define REG_RawBuf1         0x6B
#define REG_OrderBuf0       0x6C
#define REG_CbBuf0          0x6E
#define REG_K1Delay         0x31
#define REG_K2Delay         0x32
#define REG_SCChannelCf     0x34
#define REG_8736_LCD_NOISE_FRAME        0x12
#define REG_8736_LCD_NOISE_START        0x11
#define REG_8736_LCD_NOISE_NUMBER       0x13
#define REG_8736_LCD_NOISE_DATA_READY   0x00
#define REG_CLB                        0x04

/*******************************************************************************
* Private enumerations, structures and unions using typedef
*******************************************************************************/
struct ft8736_test_item {
    bool rawdata_test;
    bool cb_test;
    bool short_test;
    bool lcd_noise_test;
    bool open_test;
};
struct ft8736_basic_threshold {
    char project_code[32];
    bool rawdata_test_va_check;
    int rawdata_test_min;
    int rawdata_test_max;
    bool rawdata_test_vk_check;
    int rawdata_test_min_vk;
    int rawdata_test_max_vk;

    bool cb_test_va_check;
    int cb_test_min;
    int cb_test_max;
    bool cb_test_vk_check;
    int cb_test_min_vk;
    int cb_test_max_vk;

    int short_res_min;
    //int ShortTest_K2Value;
    int lcd_noise_test_frame_num;
    int lcd_noise_test_coefficient;
    int lcd_noise_test_coefficient_key;
    u8 lcd_noise_test_noise_mode;
    int open_test_cb_min;
};

enum test_item_ft8736 {
    CODE_FT8736_ENTER_FACTORY_MODE = 0,//All IC are required to test items
    CODE_FT8736_RAWDATA_TEST = 7,
    CODE_FT8736_CB_TEST = 12,
    CODE_FT8736_SHORT_CIRCUIT_TEST = 14,
    CODE_FT8736_OPEN_TEST = 15,
    CODE_FT8736_LCD_NOISE_TEST = 19,
};

/*******************************************************************************
* Static variables
*******************************************************************************/

/*******************************************************************************
* Global variable or extern global variabls/functions
*******************************************************************************/
struct ft8736_test_item ft8736_item;
struct ft8736_basic_threshold ft8736_basic_thr;
/*******************************************************************************
* Static function prototypes
*******************************************************************************/
static u32 sqrt_new(u32 n);
static int ft8736_get_key_num(void);
static int ft8736_enter_factory_mode(void);
static int ft8736_rawdata_test(bool *test_result);
static int ft8736_cb_test(bool *test_result);
static int ft8736_lcdnoise_test(bool *test_result);
static int ft8736_open_test(bool *test_result);
static int ft8736_short_test(bool *test_result);  //This test item requires LCD driver coordination

/************************************************************************
* Name: start_test_ft8736
* Brief:  Test entry. Determine which test item to test
* Input: none
* Output: none
* Return: Test Result, PASS or FAIL
***********************************************************************/
bool start_test_ft8736(void)
{
    bool test_result = true, tmp_result = 1;
    int ret;
    int item_count = 0;

    FTS_TEST_FUNC_ENTER();

    //--------------2. test item
    if (0 == test_data.test_num)
        test_result = false;

    ////////Testing process, the order of the test_data.test_item structure of the test items
    for (item_count = 0; item_count < test_data.test_num; item_count++) {
        test_data.test_item_code = test_data.test_item[item_count].itemcode;

        ///////////////////////////////////////////////////////FT8736_ENTER_FACTORY_MODE
        if (CODE_FT8736_ENTER_FACTORY_MODE == test_data.test_item[item_count].itemcode) {
            ret = ft8736_enter_factory_mode();
            if (ERROR_CODE_OK != ret || (!tmp_result)) {
                test_result = false;
                test_data.test_item[item_count].testresult = RESULT_NG;
                break;//if this item FAIL, no longer test.
            } else
                test_data.test_item[item_count].testresult = RESULT_PASS;
        }

        ///////////////////////////////////////////////////////FT8736_RAWDATA_TEST
        if (CODE_FT8736_RAWDATA_TEST == test_data.test_item[item_count].itemcode) {
            ret = ft8736_rawdata_test(&tmp_result);
            if (ERROR_CODE_OK != ret || (!tmp_result)) {
                test_result = false;
                test_data.test_item[item_count].testresult = RESULT_NG;
            } else
                test_data.test_item[item_count].testresult = RESULT_PASS;
        }

        ///////////////////////////////////////////////////////FT8736_CB_TEST
        if (CODE_FT8736_CB_TEST == test_data.test_item[item_count].itemcode) {
            ret = ft8736_cb_test(&tmp_result); //
            if (ERROR_CODE_OK != ret || (!tmp_result)) {
                test_result = false;
                test_data.test_item[item_count].testresult = RESULT_NG;
            } else
                test_data.test_item[item_count].testresult = RESULT_PASS;
        }

        ///////////////////////////////////////////////////////FT8736_SHORT_TEST
        if (CODE_FT8736_SHORT_CIRCUIT_TEST == test_data.test_item[item_count].itemcode) {
            ret = ft8736_short_test(&tmp_result);
            if (ERROR_CODE_OK != ret || (!tmp_result)) {
                test_result = false;
                test_data.test_item[item_count].testresult = RESULT_NG;
            } else
                test_data.test_item[item_count].testresult = RESULT_PASS;
        }

        ///////////////////////////////////////////////////////FT8736_Open_TEST
        if (CODE_FT8736_OPEN_TEST == test_data.test_item[item_count].itemcode) {
            ret = ft8736_open_test(&tmp_result);
            if (ERROR_CODE_OK != ret || (!tmp_result)) {
                test_result = false;
                test_data.test_item[item_count].testresult = RESULT_NG;
            } else
                test_data.test_item[item_count].testresult = RESULT_PASS;
        }

        ///////////////////////////////////////////////////////FT8736_LCDNoise_TEST
        if (CODE_FT8736_LCD_NOISE_TEST == test_data.test_item[item_count].itemcode) {
            ret = ft8736_lcdnoise_test(&tmp_result);
            if (ERROR_CODE_OK != ret || (!tmp_result)) {
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
* Name: ft8736_enter_factory_mode
* Brief:  Check whether TP can enter Factory Mode, and do some thing
* Input: none
* Output: none
* Return: Comm Code. Code = 0x00 is OK, else fail.
***********************************************************************/
static int ft8736_enter_factory_mode(void)
{
    int ret = 0;

    ret = enter_factory_mode();
    if (ret < 0) {
        FTS_TEST_SAVE_INFO("enter factory mode fail, can't get tx/rx num");
        return ret;
    }
    ret = ft8736_get_key_num();
    if (ret < 0) {
        FTS_TEST_SAVE_INFO("get key num fail");
        return ret;
    }

    return ret;
}

/************************************************************************
* Name: ft8736_rawdata_test
* Brief:  TestItem: RawDataTest. Check if MCAP RawData is within the range.
* Input: test_result
* Output: test_result, PASS or FAIL
* Return: Comm Code. Code = 0x00 is OK, else fail.
***********************************************************************/
static int ft8736_rawdata_test(bool *test_result)
{
    int ret = ERROR_CODE_OK;
    bool tmp_result = true;
    int i = 0;
    bool include_key = ft8736_basic_thr.rawdata_test_vk_check;
    int *rawdata = NULL;

    FTS_TEST_SAVE_INFO("\n\n==============================Test Item: -------- Raw Data Test\n");

    memset(test_data.buffer, 0, ((test_data.screen_param.tx_num + 1) * test_data.screen_param.rx_num) * sizeof(int));
    rawdata = test_data.buffer;

    ret = enter_factory_mode();
    if (ERROR_CODE_OK != ret) {
        FTS_TEST_SAVE_INFO("\n Failed to Enter factory mode. Error Code: %d", ret);
        return ret;
    }
    //----------------------------------------------------------Read RawData
    for (i = 0 ; i < 3; i++) //Lost 3 Frames, In order to obtain stable data
        ret = get_rawdata_incell(rawdata);
    if ( ERROR_CODE_OK != ret ) {
        FTS_TEST_SAVE_INFO("\nFailed to get Raw Data!! Error Code: %d\n", ret);
        return ret;
    }

    //----------------------------------------------------------Show RawData
    show_data_incell(rawdata, include_key);

    //----------------------------------------------------------To Determine RawData if in Range or not
    tmp_result = compare_detailthreshold_data_incell(rawdata, test_data.incell_detail_thr.rawdata_test_min, test_data.incell_detail_thr.rawdata_test_max, include_key);

    //////////////////////////////Save Test Data
    save_testdata_incell(rawdata, "RawData Test", 0, test_data.screen_param.tx_num + 1, test_data.screen_param.rx_num, 1);

    //----------------------------------------------------------Return Result
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
* Name: ft8736_cb_test
* Brief:  TestItem: Cb Test. Check if Cb is within the range.
* Input: none
* Output: test_result, PASS or FAIL
* Return: Comm Code. Code = 0x00 is OK, else fail.
***********************************************************************/
static int ft8736_cb_test(bool *test_result)
{
    bool tmp_result = true;
    int ret = ERROR_CODE_OK;
    int col = 0;
    int i = 0;
    bool include_key = false;
    u8 uc_bits = 0;
    int read_key_len = test_data.screen_param.key_num_total;
    int *cbdata = NULL;

    include_key = ft8736_basic_thr.cb_test_vk_check;
    FTS_TEST_SAVE_INFO("\n\n\n==============================Test Item: --------  CB Test\n\n");

    memset(test_data.buffer, 0, ((test_data.screen_param.tx_num + 1) * test_data.screen_param.rx_num) * sizeof(int));
    cbdata = test_data.buffer;

    ret = enter_factory_mode();
    if (ERROR_CODE_OK != ret) {
        tmp_result = false;
        FTS_TEST_SAVE_INFO("\n Failed to Enter factory mode. Error Code: %d", ret);
    }

    for ( i = 0; i < 10; i++) {
        FTS_TEST_SAVE_INFO("\n start chipclb times:%d. ", i);
        //auto clb
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

    ret = read_reg(0x0B, &uc_bits);
    if (ERROR_CODE_OK != ret) {
        tmp_result = false;
        FTS_TEST_SAVE_INFO("\n Read Reg 0x0B Failed!");
    }

    read_key_len = test_data.screen_param.key_num_total;
    if (uc_bits != 0) {
        read_key_len = test_data.screen_param.key_num_total * 2;
    }

    ret = get_cb_incell( 0, (short)(test_data.screen_param.tx_num * test_data.screen_param.rx_num  + read_key_len), cbdata );
    if ( ERROR_CODE_OK != ret ) {
        tmp_result = false;
        FTS_TEST_SAVE_INFO("\nFailed to get CB value...\n");
        goto TEST_ERR;
    }

    for ( col = 0; col < read_key_len/*test_data.screen_param.key_num_total*/; ++col ) {
        if (uc_bits != 0) {
            cbdata[test_data.screen_param.tx_num * test_data.screen_param.rx_num + col / 2] = ((cbdata[ test_data.screen_param.tx_num * test_data.screen_param.rx_num + col ] & 0x01 ) << 8) +
                    cbdata[ test_data.screen_param.tx_num *
                            test_data.screen_param.rx_num + col + 1 ];
            col++;
        } else {
            cbdata[test_data.screen_param.tx_num * test_data.screen_param.rx_num + col] = cbdata[ test_data.screen_param.tx_num * test_data.screen_param.rx_num + col ];
        }
    }

    //------------------------------------------------Show CbData
    show_data_incell(cbdata, include_key);

    //----------------------------------------------------------To Determine Cb if in Range or not
    tmp_result = compare_detailthreshold_data_incell(cbdata, test_data.incell_detail_thr.cb_test_min, test_data.incell_detail_thr.cb_test_max, include_key);

    //////////////////////////////Save Test Data
    save_testdata_incell(cbdata, "CB Test", 0, test_data.screen_param.tx_num + 1, test_data.screen_param.rx_num, 1);
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
* Name: ft8736_short_test
* Brief:  Get short circuit test mode data, judge whether there is a short circuit
* Input: none
* Output: none
* Return: Comm Code. Code = 0x00 is OK, else fail.
***********************************************************************/
static int ft8736_short_test(bool *test_result)
{
    int ret = ERROR_CODE_OK;
    bool tmp_result = true;
    int res_min = 0;
    u8 tx_num = 0, rx_num = 0, channel_num = 0;
    int all_adc_data_num = 0;
    int row = 0;
    int col = 0;
    int tmp_adc = 0;
    int value_min = 0;
    int value_max = 0;
    int *adcdata = NULL;

    FTS_TEST_SAVE_INFO("\n==============================Test Item: -------- Short Circuit Test \r\n");

    memset(test_data.buffer, 0, ((test_data.screen_param.tx_num + 1) * test_data.screen_param.rx_num) * sizeof(int));
    adcdata = test_data.buffer;

    ret = enter_factory_mode();
    if (ERROR_CODE_OK != ret) {
        tmp_result = false;
        FTS_TEST_SAVE_INFO("\n Failed to Enter factory mode. Error Code: %d", ret);
        goto TEST_END;
    }

    /*****************************************************
    Befor ShortCircuitTest need LCD driver to control power off
    ******************************************************/
    ret = read_reg(0x02, &tx_num);
    ret = read_reg(0x03, &rx_num);
    if (ERROR_CODE_OK != ret) {
        tmp_result = false;
        FTS_TEST_SAVE_INFO("\n// Failed to read reg. Error Code: %d", ret);
        goto TEST_END;
    }

    FTS_TEST_SAVE_INFO("\n tx_num:%d.  rx_num:%d.", tx_num, rx_num);
    channel_num = tx_num + rx_num;
    all_adc_data_num = tx_num * rx_num + test_data.screen_param.key_num_total;

    ret = weakshort_get_adc_data_incell(rx_num, all_adc_data_num * 2, adcdata);
    sys_delay(50);
    if (ERROR_CODE_OK != ret) {
        tmp_result = false;
        FTS_TEST_SAVE_INFO("\n // Failed to get AdcData. Error Code: %d", ret);
        goto TEST_END;
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

    //  FTS_TEST_DBG("shortRes data:\n");
    for ( row = 0; row < test_data.screen_param.tx_num + 1; ++row ) {
        for ( col = 0; col <  test_data.screen_param.rx_num; ++col ) {
            tmp_adc = adcdata[row * rx_num + col];
            if (2007 <= tmp_adc)  tmp_adc = 2007;
            adcdata[row * rx_num + col] = (tmp_adc * 200) / (2047 - tmp_adc);
        }
    }

    show_data_incell(adcdata, true);

    //////////////////////// analyze
    res_min = ft8736_basic_thr.short_res_min;
    value_min = res_min;
    value_max = 100000000;
    FTS_TEST_SAVE_INFO("\n Short Circuit test , Set_Range=(%d, %d). \n", \
                       value_min, value_max);

    tmp_result = compare_data_incell(adcdata, value_min, value_max, value_min, value_max, true);

    save_testdata_incell( adcdata, "Short Circuit Test", 0, tx_num + 1, rx_num, 1 );

    /*****************************************************
    After ShortCircuitTest need LCD driver to control power on
    ******************************************************/

TEST_END:
    if (tmp_result) {
        FTS_TEST_SAVE_INFO("\n==========Short Circuit Test is OK!");
        * test_result = true;
    } else {
        FTS_TEST_SAVE_INFO("\n==========Short Circuit Test is NG!");
        * test_result = false;
    }

    return ret;
}

/************************************************************************
* Name: ft8736_open_test
* Brief:  Check if channel is open
* Input: none
* Output: none
* Return: Comm Code. Code = 0x00 is OK, else fail.
***********************************************************************/
static int ft8736_open_test(bool *test_result)
{
    int ret = ERROR_CODE_OK;
    bool tmp_result = true;
    u8 cb_value = 0xff;
    int min = 0;
    int max = 0;
    int *opendata = NULL;

    FTS_TEST_SAVE_INFO("\n\r\n\r\n==============================Test Item: --------  Open Test");

    memset(test_data.buffer, 0, ((test_data.screen_param.tx_num + 1) * test_data.screen_param.rx_num) * sizeof(int));
    opendata = test_data.buffer;

    ret = enter_factory_mode();
    sys_delay(50);
    if (ERROR_CODE_OK != ret) {
        tmp_result = false;
        FTS_TEST_SAVE_INFO("\n\r\n =========  Enter Factory Failed!");
        goto TEST_ERR;
    }

    // set GIP to VGHO2/VGLO2 in factory mode (0x22 register write 0x80)
    ret = read_reg(0x22, &cb_value);
    if (ERROR_CODE_OK != ret) {
        tmp_result = false;
        FTS_TEST_SAVE_INFO("\r\n// =========  Read Reg Failed!");
        goto TEST_ERR;
    }

    ret = write_reg(0x22, 0x80);
    sys_delay(50);
    if (ERROR_CODE_OK != ret) {
        tmp_result = false;
        FTS_TEST_SAVE_INFO("\r\n// =========  Write Reg Failed!");
        goto TEST_ERR;
    }

    if (!wait_state_update()) {
        tmp_result = false;
        FTS_TEST_SAVE_INFO("\r\n//=========Wait State Update Failed!");
        goto TEST_ERR;
    }

    //auto clb
    ret = chip_clb_incell();
    if (ERROR_CODE_OK != ret) {
        tmp_result = false;
        FTS_TEST_SAVE_INFO("\r\n//========= auto clb Failed!");
        goto TEST_ERR;
    }

    ret = get_cb_incell( 0, test_data.screen_param.tx_num * test_data.screen_param.rx_num + test_data.screen_param.key_num_total, opendata );
    if ( ERROR_CODE_OK != ret ) {
        tmp_result = false;
        FTS_TEST_SAVE_INFO("\r\n\r\n//=========get CB Failed!!");
        goto TEST_ERR;
    }

    // show open data
    show_data_incell(opendata, false);

    //  analyze
    min = ft8736_basic_thr.open_test_cb_min;
    max = 200;
    tmp_result = compare_data_incell(opendata, min, max, min, max, false);

    // save data
    save_testdata_incell(opendata, "Open Test", 0, test_data.screen_param.tx_num, test_data.screen_param.rx_num, 1);

    // restore the register value of 0x22
    ret = write_reg(0x22, cb_value);
    sys_delay(50);
    if (ERROR_CODE_OK != ret) {
        tmp_result = false;
        FTS_TEST_SAVE_INFO("\r\n//=========  Write Reg Failed!");
        goto TEST_ERR;
    }

    ret = chip_clb_incell();
    if (ERROR_CODE_OK != ret) {
        tmp_result = false;
        FTS_TEST_SAVE_INFO("\r\n//========= auto clb Failed!");
        goto TEST_ERR;
    }

    if (tmp_result) {
        * test_result = true;
        FTS_TEST_SAVE_INFO("\n==========Open Test is OK!\n");
    } else {
        * test_result = false;
        FTS_TEST_SAVE_INFO("\n==========Open Test is NG!\n");
    }
    return ret;

TEST_ERR:
    * test_result = false;
    FTS_TEST_SAVE_INFO("\n==========Open Test is NG!\n");
    return ret;
}

/************************************************************************
* Name: ft8736_lcdnoise_test
* Brief:   obtain is differ mode  the data and calculate the corresponding type of noise value.
* Input: none
* Output: none
* Return: Comm Code. Code = 0x00 is OK, else fail.
***********************************************************************/
static int ft8736_lcdnoise_test(bool *test_result)
{
    int ret = ERROR_CODE_OK;
    bool result_flag = true;
    int frame_num = 0;
    int i = 0;
    int row = 0;
    int col = 0;
    int value_min = 0;
    int value_max = 0;
    int value_max_vk = 0;
    int bytes_num  = 0;
    u8 reg_data = 0, old_mode = 0, ch_new_mode = 0, data_ready = 0;
    int *lcdnoise = NULL;

    FTS_TEST_SAVE_INFO("\n==============================Test Item: -------- LCD Noise Test \r\n");

    memset(test_data.buffer, 0, ((test_data.screen_param.tx_num + 1) * test_data.screen_param.rx_num) * sizeof(int));
    lcdnoise = test_data.buffer;

    ret = enter_factory_mode();
    if (ERROR_CODE_OK != ret) {
        result_flag = false;
        FTS_TEST_SAVE_INFO("\n Failed to Enter factory mode. Error Code: %d", ret);
        goto TEST_ERR;
    }

    ret =  read_reg( 0x06, &old_mode);
    ret =  write_reg( 0x06, 0x01 );
    sys_delay(10);

    ret = read_reg( 0x06, &ch_new_mode );
    if ( ret != ERROR_CODE_OK || ch_new_mode != 1 ) {
        result_flag = false;
        FTS_TEST_SAVE_INFO("\r\nSwitch Mode Failed!\r\n");
        goto TEST_ERR;
    }

    frame_num = ft8736_basic_thr.lcd_noise_test_frame_num / 4;
    ret = write_reg( REG_8736_LCD_NOISE_FRAME, frame_num );
    if (ret != ERROR_CODE_OK) {
        result_flag = false;
        FTS_TEST_SAVE_INFO("\r\n//=========  Write Reg Failed!");
        goto TEST_ERR;
    }

    ret = write_reg( REG_8736_LCD_NOISE_START, 0x01 );
    for ( i = 0; i < 100; i++) {
        ret = read_reg( REG_8736_LCD_NOISE_DATA_READY, &data_ready );
        sys_delay(200);

        if ( 0x00 == (data_ready >> 7) ) {
            break;
        } else {
            sys_delay( 100 );
        }

        if ( 99 == i ) {
            ret = write_reg( REG_8736_LCD_NOISE_START, 0x00 );
            if (ret != ERROR_CODE_OK) {
                result_flag = false;
                FTS_TEST_SAVE_INFO("\r\nRestore Failed!");
                goto TEST_ERR;
            }

            result_flag = false;
            FTS_TEST_SAVE_INFO("\r\nTime Over!");
            goto TEST_ERR;
        }
    }

    bytes_num = 2 * ( test_data.screen_param.tx_num *  test_data.screen_param.rx_num +  test_data.screen_param.key_num_total);

    ret = write_reg(0x01, 0xAD);
    ret = read_mass_data( 0x6a, bytes_num, lcdnoise );
    if (ret) {
        FTS_TEST_SAVE_INFO("Failed to Read Data.\n");
        goto TEST_ERR;
    }

    ret = write_reg( REG_8736_LCD_NOISE_START, 0x00 );
    if (ret != ERROR_CODE_OK) {
        result_flag = false;
        FTS_TEST_SAVE_INFO("\r\nRestore Failed!");
        goto TEST_ERR;
    }

    ret = read_reg( REG_8736_LCD_NOISE_NUMBER, &reg_data );
    if ( reg_data <= 0 ) {
        reg_data = 1;
    }

    ret = write_reg( 0x06, old_mode );
    if (ret != ERROR_CODE_OK) {
        result_flag = false;
        FTS_TEST_SAVE_INFO("\r\nWrite Reg Failed!");
        goto TEST_ERR;
    }

    if (0 == ft8736_basic_thr.lcd_noise_test_noise_mode) {
        for (  row = 0; row <= test_data.screen_param.tx_num + 1; ++row ) {
            for ( col = 0; col < test_data.screen_param.rx_num; ++col ) {
                lcdnoise[row * test_data.screen_param.rx_num + col] = lcdnoise[row * test_data.screen_param.rx_num + col];
            }
        }
    }

    if (1 == ft8736_basic_thr.lcd_noise_test_noise_mode) {
        for ( row = 0; row <= test_data.screen_param.tx_num + 1; ++row ) {
            for (  col = 0; col < test_data.screen_param.rx_num; ++col ) {
                lcdnoise[row * test_data.screen_param.rx_num + col] = sqrt_new(lcdnoise[row * test_data.screen_param.rx_num + col] / reg_data);
            }
        }
    }

    //show data of lcd_noise
    show_data_incell(lcdnoise, true);

    //////////////////////// analyze
    value_min = 0;
    value_max = ft8736_basic_thr.lcd_noise_test_coefficient * test_data.va_touch_thr * 32 / 100;
    value_max_vk = ft8736_basic_thr.lcd_noise_test_coefficient_key * test_data.key_touch_thr * 32 / 100;
    result_flag = compare_data_incell(lcdnoise, value_min, value_max, value_min, value_max_vk, true);

    //  Save Test Data
    save_testdata_incell(lcdnoise, "LCD Noise Test", 0, test_data.screen_param.tx_num + 1, test_data.screen_param.rx_num, 1 );

    if (result_flag) {
        * test_result = true;
        FTS_TEST_SAVE_INFO("\n==========LCD Noise Test is OK!");
    } else {
        * test_result = false;
        FTS_TEST_SAVE_INFO("\n==========LCD Noise Test is NG!");
    }
    return ret;

TEST_ERR:
    write_reg( 0x06, 0x00 );
    sys_delay(20);
    write_reg( REG_8736_LCD_NOISE_START, 0x00 );
    * test_result = false;
    FTS_TEST_SAVE_INFO(" LCD Noise Test is NG. ");

    return ret;
}

/************************************************************************
* Name: sqrt_new
* Brief:  calculate sqrt of input.
* Input: unsigned int n
* Output: none
* Return: sqrt of n.
***********************************************************************/
static u32 sqrt_new(u32 n)
{
    unsigned int  val = 0, last = 0;
    unsigned char i = 0;;

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

static int ft8736_get_key_num(void)
{
    int ret = 0;
    int i = 0;
    u8 keyval = 0;

    test_data.screen_param.key_num = 0;
    for (i = 0; i < 3; i++) {
        ret = read_reg( FT8736_LEFT_KEY_REG, &keyval );
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
        ret = read_reg( FT8736_RIGHT_KEY_REG, &keyval );
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

void init_testitem_ft8736(char *strIniFile)
{
    char str[512];
    FTS_TEST_FUNC_ENTER();

    /////////////////////////////////// RAWDATA_TEST
    GetPrivateProfileString("TestItem", "RAWDATA_TEST", "1", str, strIniFile);
    ft8736_item.rawdata_test = fts_atoi(str);

    /////////////////////////////////// CB_TEST
    GetPrivateProfileString("TestItem", "CB_TEST", "1", str, strIniFile);
    ft8736_item.cb_test = fts_atoi(str);

    /////////////////////////////////// SHORT_CIRCUIT_TEST
    GetPrivateProfileString("TestItem", "SHORT_CIRCUIT_TEST", "1", str, strIniFile);
    ft8736_item.short_test = fts_atoi(str);

    /////////////////////////////////// OPEN_TEST
    GetPrivateProfileString("TestItem", "OPEN_TEST", "0", str, strIniFile);
    ft8736_item.open_test = fts_atoi(str);

    /////////////////////////////////// LCD_NOISE_TEST
    GetPrivateProfileString("TestItem", "lcd_noise_test", "0", str, strIniFile);
    ft8736_item.lcd_noise_test = fts_atoi(str);

    FTS_TEST_FUNC_EXIT();
}

void init_basicthreshold_ft8736(char *strIniFile)
{
    char str[512];

    FTS_TEST_FUNC_ENTER();

    //////////////////////////////////////////////////////////// RawData Test
    GetPrivateProfileString("Basic_Threshold", "RawDataTest_VA_Check", "1", str, strIniFile);
    ft8736_basic_thr.rawdata_test_va_check = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "RawDataTest_Min", "5000", str, strIniFile);
    ft8736_basic_thr.rawdata_test_min = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "RawDataTest_Max", "11000", str, strIniFile);
    ft8736_basic_thr.rawdata_test_max = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "RawDataTest_VKey_Check", "1", str, strIniFile);
    ft8736_basic_thr.rawdata_test_vk_check = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "RawDataTest_Min_VKey", "5000", str, strIniFile);
    ft8736_basic_thr.rawdata_test_min_vk = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "RawDataTest_Max_VKey", "11000", str, strIniFile);
    ft8736_basic_thr.rawdata_test_max_vk = fts_atoi(str);

    //////////////////////////////////////////////////////////// CB Test
    GetPrivateProfileString("Basic_Threshold", "CBTest_VA_Check", "1", str, strIniFile);
    ft8736_basic_thr.cb_test_va_check = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "CBTest_Min", "3", str, strIniFile);
    ft8736_basic_thr.cb_test_min = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "CBTest_Max", "100", str, strIniFile);
    ft8736_basic_thr.cb_test_max = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "CBTest_VKey_Check", "1", str, strIniFile);
    ft8736_basic_thr.cb_test_vk_check = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "CBTest_Min_Vkey", "3", str, strIniFile);
    ft8736_basic_thr.cb_test_min_vk = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "CBTest_Max_Vkey", "100", str, strIniFile);
    ft8736_basic_thr.cb_test_max_vk = fts_atoi(str);

    //////////////////////////////////////////////////////////// Short Circuit Test
    GetPrivateProfileString("Basic_Threshold", "ShortCircuit_ResMin", "1200", str, strIniFile);
    ft8736_basic_thr.short_res_min = fts_atoi(str);

    ////////////////////////////////////////////////////////////LCDNoiseTest
    GetPrivateProfileString("Basic_Threshold", "LCDNoiseTest_FrameNum", "200", str, strIniFile);
    ft8736_basic_thr.lcd_noise_test_frame_num = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "LCDNoiseTest_Coefficient", "60", str, strIniFile);
    ft8736_basic_thr.lcd_noise_test_coefficient = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "LCDNoiseTest_NoiseMode", "0", str, strIniFile);
    ft8736_basic_thr.lcd_noise_test_noise_mode = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "LCDNoiseTest_Coefficient_Key", "60", str, strIniFile);
    ft8736_basic_thr.lcd_noise_test_coefficient_key = fts_atoi(str);

    ////////////////////////////////////////////////////////////open test
    GetPrivateProfileString("Basic_Threshold", "OpenTest_CBMin", "100", str, strIniFile);
    ft8736_basic_thr.open_test_cb_min = fts_atoi(str);
    FTS_TEST_FUNC_EXIT();
}

void init_detailthreshold_ft8736(char *ini)
{
    FTS_TEST_FUNC_ENTER();

    OnInit_InvalidNode(ini);
    OnInit_DThreshold_CBTest(ini);
    OnThreshold_VkAndVaRawDataSeparateTest(ini);

    FTS_TEST_FUNC_EXIT();
}

void set_testitem_sequence_ft8736(void)
{
    test_data.test_num = 0;

    FTS_TEST_FUNC_ENTER();
    //////////////////////////////////////////////////Enter Factory Mode
    fts_set_testitem(CODE_FT8736_ENTER_FACTORY_MODE);

    //////////////////////////////////////////////////Short Test
    if ( ft8736_item.short_test == 1) {
        fts_set_testitem(CODE_FT8736_SHORT_CIRCUIT_TEST);
    }

    //////////////////////////////////////////////////cb_test
    if ( ft8736_item.cb_test == 1) {
        fts_set_testitem(CODE_FT8736_CB_TEST);
    }

    //////////////////////////////////////////////////RawData Test
    if ( ft8736_item.rawdata_test == 1) {
        fts_set_testitem(CODE_FT8736_RAWDATA_TEST);
    }


    //////////////////////////////////////////////////lcd_noise_test
    if ( ft8736_item.lcd_noise_test == 1) {
        fts_set_testitem(CODE_FT8736_LCD_NOISE_TEST);
    }

    //////////////////////////////////////////////////open_test
    if ( ft8736_item.open_test == 1) {
        fts_set_testitem(CODE_FT8736_OPEN_TEST);
    }

    FTS_TEST_FUNC_EXIT();
}

struct test_funcs test_func_ft8736 = {
    .ic_series = TEST_ICSERIES(IC_FT8736),
    .init_testitem = init_testitem_ft8736,
    .init_basicthreshold = init_basicthreshold_ft8736,
    .init_detailthreshold = init_detailthreshold_ft8736,
    .set_testitem_sequence  = set_testitem_sequence_ft8736,
    .start_test = start_test_ft8736,
};
