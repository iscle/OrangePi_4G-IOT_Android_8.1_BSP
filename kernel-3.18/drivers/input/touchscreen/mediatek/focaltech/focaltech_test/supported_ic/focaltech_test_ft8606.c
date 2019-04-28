/************************************************************************
* Copyright (C) 2012-2017, Focaltech Systems (R)��All Rights Reserved.
*
* File Name: Focaltech_test_ft8606.c
*
* Author: Software Development Team, AE
*
* Created: 2016-08-01
*
* Abstract: test item for FT8606
*
************************************************************************/

/*******************************************************************************
* Included header files
*******************************************************************************/
#include "../focaltech_test.h"

/*******************************************************************************
* Private constant and macro definitions using #define
*******************************************************************************/
/////////////////////////////////////////////////Reg 8606
#define DEVIDE_MODE_ADDR    0x00
#define REG_LINE_NUM    0x01
#define REG_TX_NUM  0x02
#define REG_RX_NUM  0x03
#define FT_8606_LEFT_KEY_REG    0X1E
#define FT_8606_RIGHT_KEY_REG   0X1F
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
#define REG_CLB             0x04


/*******************************************************************************
* Private enumerations, structures and unions using typedef
*******************************************************************************/
struct ft8606_test_item {
    bool rawdata_test;
    bool cb_test;
    bool short_test;
    bool lcd_noise_test;
};
struct ft8606_basic_threshold {
    int rawdata_test_min;
    int rawdata_test_max;
    int cb_test_min;
    int cb_test_max;
    int short_test_max;
    int short_test_k2_value;
    bool short_test_tip;
    int lcd_noise_test_frame;
    int lcd_noise_test_max_screen;
    int lcd_noise_test_max_frame;
    int lcd_noise_coefficient;
};
enum test_item_ft8606 {
    CODE_FT8606_ENTER_FACTORY_MODE = 0,//All IC are required to test items
    CODE_FT8606_RAWDATA_TEST = 7,
    CODE_FT8606_CB_TEST = 12,
    CODE_FT8606_SHORT_CIRCUIT_TEST = 14,
    CODE_FT8606_LCD_NOISE_TEST = 15,

};

/*******************************************************************************
* Static variables
*******************************************************************************/

/*******************************************************************************
* Global variable or extern global variabls/functions
*******************************************************************************/
struct ft8606_test_item ft8606_item;
struct ft8606_basic_threshold ft8606_basic_thr;

/*******************************************************************************
* Static function prototypes
*******************************************************************************/
static int ft8606_get_key_num(void);
static int ft8606_enter_factory_mode(void);
static int ft8606_rawdata_test(bool *test_result);
static int ft8606_cb_test(bool *test_result);

/************************************************************************
* Name: start_test_ft8606
* Brief:  Test entry. Determine which test item to test
* Input: none
* Output: none
* Return: Test Result, PASS or FAIL
***********************************************************************/
bool start_test_ft8606(void)
{

    bool test_result = true, temp_result = 1;
    int ret;
    int item_count = 0;

    //--------------2. test item
    if (0 == test_data.test_num)
        test_result = false;

    ////////Testing process, the order of the test_data.test_item structure of the test items
    for (item_count = 0; item_count < test_data.test_num; item_count++) {
        test_data.test_item_code = test_data.test_item[item_count].itemcode;

        ///////////////////////////////////////////////////////FT8606_ENTER_FACTORY_MODE
        if (CODE_FT8606_ENTER_FACTORY_MODE == test_data.test_item[item_count].itemcode) {
            ret = ft8606_enter_factory_mode();
            if (ERROR_CODE_OK != ret || (!temp_result)) {
                test_result = false;
                test_data.test_item[item_count].testresult = RESULT_NG;
                break;//if this item FAIL, no longer test.
            } else
                test_data.test_item[item_count].testresult = RESULT_PASS;
        }

        ///////////////////////////////////////////////////////FT8606_RAWDATA_TEST

        if (CODE_FT8606_RAWDATA_TEST == test_data.test_item[item_count].itemcode) {
            ret = ft8606_rawdata_test(&temp_result);
            if (ERROR_CODE_OK != ret || (!temp_result)) {
                test_result = false;
                test_data.test_item[item_count].testresult = RESULT_NG;
            } else
                test_data.test_item[item_count].testresult = RESULT_PASS;
        }

        ///////////////////////////////////////////////////////FT8606_CB_TEST

        if (CODE_FT8606_CB_TEST == test_data.test_item[item_count].itemcode) {
            ret = ft8606_cb_test(&temp_result);
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
* Name: ft8606_enter_factory_mode
* Brief:  Check whether TP can enter Factory Mode, and do some thing
* Input: none
* Output: none
* Return: Comm Code. Code = 0x00 is OK, else fail.
***********************************************************************/
static int ft8606_enter_factory_mode(void)
{
    int ret = 0;

    ret = enter_factory_mode();
    if (ret < 0) {
        FTS_TEST_SAVE_INFO("enter factory mode fail, can't get tx/rx num");
        return ret;
    }
    ret = ft8606_get_key_num();
    if (ret < 0) {
        FTS_TEST_SAVE_INFO("get key num fail");
        return ret;
    }

    return ret;
}

/************************************************************************
* Name: ft8606_rawdata_test
* Brief:  TestItem: RawDataTest. Check if MCAP RawData is within the range.
* Input: test_result
* Output: test_result, PASS or FAIL
* Return: Comm Code. Code = 0x00 is OK, else fail.
***********************************************************************/
static int ft8606_rawdata_test(bool *test_result)
{
    int ret;
    bool tmp_result = true;
    int i = 0;
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
        FTS_TEST_SAVE_INFO("Failed to get Raw Data!! Error Code: %d",  ret);
        return ret;
    }
    //----------------------------------------------------------Show RawData
    show_data_incell(rawdata, true);


    //----------------------------------------------------------To Determine RawData if in Range or not
    tmp_result = compare_detailthreshold_data_incell(rawdata, test_data.incell_detail_thr.rawdata_test_min, test_data.incell_detail_thr.rawdata_test_max, true);

    //////////////////////////////Save Test Data
    save_testdata_incell(rawdata, "RawData Test", 0, test_data.screen_param.tx_num + 1, test_data.screen_param.rx_num, 1);
    //----------------------------------------------------------Return Result

    if (tmp_result) {
        * test_result = true;
        FTS_TEST_SAVE_INFO("\n==========RawData Test is OK!");
    } else {
        * test_result = false;
        FTS_TEST_SAVE_INFO("\n==========RawData Test is NG!");
    }
    return ret;
}

/************************************************************************
* Name: ft8606_cb_test
* Brief:  TestItem: Cb Test. Check if Cb is within the range.
* Input: none
* Output: test_result, PASS or FAIL
* Return: Comm Code. Code = 0x00 is OK, else fail.
***********************************************************************/
static int ft8606_cb_test(bool *test_result)
{
    bool tmp_result = true;
    int ret = ERROR_CODE_OK;
    int i = 0;
    int *cbdata = NULL;

    FTS_TEST_SAVE_INFO("\n\n==============================Test Item: --------  CB Test\n");

    memset(test_data.buffer, 0, ((test_data.screen_param.tx_num + 1) * test_data.screen_param.rx_num) * sizeof(int));
    cbdata = test_data.buffer;

    ret = enter_factory_mode();
    if (ERROR_CODE_OK != ret) {
        FTS_TEST_SAVE_INFO("\n Failed to Enter factory mode. Error Code: %d", ret);
        tmp_result = false;
        goto TEST_ERR;
    }

    for ( i = 0; i < 10; i++) {
        ret = chip_clb_incell();
        sys_delay(50);
        if ( ret == ERROR_CODE_OK) {
            break;
        }
    }
    if ( i == 10) {
        FTS_TEST_SAVE_INFO( "\r\nReCalib Failed\r\n" );
        tmp_result = false;
        goto TEST_ERR;
    }


    ret = get_cb_incell( 0, test_data.screen_param.tx_num * test_data.screen_param.rx_num + test_data.screen_param.key_num_total, cbdata );
    if ( ERROR_CODE_OK != ret ) {
        tmp_result = false;
        FTS_TEST_SAVE_INFO("Failed to get CB value...");
        goto TEST_ERR;
    }

    //------------------------------------------------Show CbData
    show_data_incell(cbdata, true);

    //------------------------------------------------Analysis
    tmp_result = compare_detailthreshold_data_incell(cbdata, test_data.incell_detail_thr.cb_test_min, test_data.incell_detail_thr.cb_test_max, true);

    //////////////////////////////Save Test Data
    save_testdata_incell(cbdata, "CB Test", 0, test_data.screen_param.tx_num + 1, test_data.screen_param.rx_num, 1);

    if (tmp_result) {
        * test_result = true;
        FTS_TEST_SAVE_INFO("\n==========CB Test is OK!");
    } else {
        * test_result = false;
        FTS_TEST_SAVE_INFO("\n==========CB Test is NG!");
    }

    return ret;

TEST_ERR:

    * test_result = false;
    FTS_TEST_SAVE_INFO("\n==========CB Test is NG!");
    return ret;
}
static int ft8606_get_key_num(void)
{
    int ret = 0;
    int i = 0;
    u8 keyval = 0;

    test_data.screen_param.key_num = 0;
    for (i = 0; i < 3; i++) {
        ret = read_reg( FT_8606_LEFT_KEY_REG, &keyval );
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
        ret = read_reg( FT_8606_RIGHT_KEY_REG, &keyval );
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
void init_basicthreshold_ft8606(char *strIniFile)
{

    char str[512];

    FTS_TEST_FUNC_ENTER();

    //////////////////////////////////////////////////////////// RawdataTest
    GetPrivateProfileString("Basic_Threshold", "RawDataTest_Min", "5000", str, strIniFile);
    ft8606_basic_thr.rawdata_test_min = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "RawDataTest_Max", "11000", str, strIniFile);
    ft8606_basic_thr.rawdata_test_max = fts_atoi(str);

    //////////////////////////////////////////////////////////// CBTest
    GetPrivateProfileString("Basic_Threshold", "CBTest_Min", "3", str, strIniFile);
    ft8606_basic_thr.cb_test_min = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "CBTest_Max", "100", str, strIniFile);
    ft8606_basic_thr.cb_test_max = fts_atoi(str);

    //////////////////////////////////////////////////////////// ShortCircuit
    GetPrivateProfileString("Basic_Threshold", "ShortCircuit_CBMax", "120", str, strIniFile);
    ft8606_basic_thr.short_test_max = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "ShortCircuit_K2Value", "150", str, strIniFile);
    ft8606_basic_thr.short_test_k2_value = fts_atoi(str);

    //////////////////////////////////////////////////////////// lcd_noise
    GetPrivateProfileString("Basic_Threshold", "LCD_NoiseTest_Frame", "50", str, strIniFile);
    ft8606_basic_thr.lcd_noise_test_frame = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "LCD_NoiseTest_Max_Screen", "32", str, strIniFile);
    ft8606_basic_thr.lcd_noise_test_max_screen = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "LCD_NoiseTest_Max_Frame", "32", str, strIniFile);
    ft8606_basic_thr.lcd_noise_test_max_frame = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "LCD_NoiseTest_Coefficient", "50", str, strIniFile);
    ft8606_basic_thr.lcd_noise_coefficient = fts_atoi(str);

    FTS_TEST_FUNC_EXIT();

}

void init_testitem_ft8606(char *strIniFile)
{
    char str[512];

    FTS_TEST_FUNC_ENTER();

    /////////////////////////////////// RawData Test
    GetPrivateProfileString("TestItem", "RAWDATA_TEST", "1", str, strIniFile);
    ft8606_item.rawdata_test = fts_atoi(str);

    /////////////////////////////////// CB_TEST
    GetPrivateProfileString("TestItem", "CB_TEST", "1", str, strIniFile);
    ft8606_item.cb_test = fts_atoi(str);

    /////////////////////////////////// SHORT_CIRCUIT_TEST
    GetPrivateProfileString("TestItem", "SHORT_CIRCUIT_TEST", "1", str, strIniFile);
    ft8606_item.short_test = fts_atoi(str);

    /////////////////////////////////// LCD_NOISE_TEST
    GetPrivateProfileString("TestItem", "LCD_NOISE_TEST", "0", str, strIniFile);
    ft8606_item.lcd_noise_test = fts_atoi(str);


    FTS_TEST_FUNC_EXIT();

}

void init_detailthreshold_ft8606(char *ini)
{
    FTS_TEST_FUNC_ENTER();

    OnInit_InvalidNode(ini);
    OnInit_DThreshold_RawDataTest(ini);
    OnInit_DThreshold_AllButtonCBTest(ini);

    FTS_TEST_FUNC_EXIT();
}

void set_testitem_sequence_ft8606(void)
{

    test_data.test_num = 0;

    FTS_TEST_FUNC_ENTER();


    //////////////////////////////////////////////////Enter Factory Mode
    fts_set_testitem(CODE_FT8606_ENTER_FACTORY_MODE);


    //////////////////////////////////////////////////Short Test
    if ( ft8606_item.short_test == 1) {

        fts_set_testitem(CODE_FT8606_SHORT_CIRCUIT_TEST);
    }

    //////////////////////////////////////////////////CB_TEST
    if ( ft8606_item.cb_test == 1) {

        fts_set_testitem(CODE_FT8606_CB_TEST);
    }

    //////////////////////////////////////////////////LCD_NOISE_TEST
    if ( ft8606_item.lcd_noise_test == 1) {

        fts_set_testitem(CODE_FT8606_LCD_NOISE_TEST);
    }

    //////////////////////////////////////////////////RawData Test
    if ( ft8606_item.rawdata_test == 1) {

        fts_set_testitem(CODE_FT8606_RAWDATA_TEST);
    }

    FTS_TEST_FUNC_EXIT();
}

struct test_funcs test_func_ft8606 = {
    .ic_series = TEST_ICSERIES(IC_FT8606),
    .init_testitem = init_testitem_ft8606,
    .init_basicthreshold = init_basicthreshold_ft8606,
    .init_detailthreshold = init_detailthreshold_ft8606,
    .set_testitem_sequence  = set_testitem_sequence_ft8606,
    .start_test = start_test_ft8606,
};
