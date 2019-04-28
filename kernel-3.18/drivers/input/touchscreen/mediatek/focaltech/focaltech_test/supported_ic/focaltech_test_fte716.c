/************************************************************************
* Copyright (C) 2012-2017, Focaltech Systems (R)£¬All Rights Reserved.
*
* File Name: focaltech_test_ftE716.c
*
* Author: Software Development
*
* Created: 2016-08-01
*
* Abstract: test item for FTE716
*
************************************************************************/

/*******************************************************************************
* Included header files
*******************************************************************************/
#include "../focaltech_test.h"

/*******************************************************************************
* Private constant and macro definitions using #define
*******************************************************************************/
/////////////////////////////////////////////////Reg FTE716
#define DEVIDE_MODE_ADDR                0x00
#define REG_LINE_NUM                    0x01
#define REG_TX_NUM                      0x02
#define REG_RX_NUM                      0x03
#define FTE716_LEFT_KEY_REG             0X1E
#define FTE716_RIGHT_KEY_REG            0X1F
#define REG_CbAddrH                     0x18
#define REG_CbAddrL                     0x19
#define REG_OrderAddrH                  0x1A
#define REG_OrderAddrL                  0x1B
#define REG_RawBuf0                     0x6A
#define REG_RawBuf1                     0x6B
#define REG_OrderBuf0                   0x6C
#define REG_CbBuf0                      0x6E
#define REG_K1Delay                     0x31
#define REG_K2Delay                     0x32
#define REG_SCChannelCf                 0x34
#define REG_CLB                          0x04

/*******************************************************************************
* Private enumerations, structures and unions using typedef
*******************************************************************************/

struct fte716_test_item {
    bool rawdata_test;
    bool cb_test;
    bool short_test;
    bool lcd_noise_test;
    bool open_test;
};
struct fte716_basic_threshold {
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
    u8 lcd_noise_test_noise_mode;
    int open_test_cb_min;
};

enum test_item_fte716 {
    CODE_FTE716_ENTER_FACTORY_MODE = 0,//All IC are required to test items
    CODE_FTE716_RAWDATA_TEST = 7,
    CODE_FTE716_CB_TEST = 12,
    CODE_FTE716_SHORT_CIRCUIT_TEST = 14,
    CODE_FTE716_OPEN_TEST = 15,
    CODE_FTE716_LCD_NOISE_TEST = 19,
};

/*******************************************************************************
* Static variables
*******************************************************************************/


/*******************************************************************************
* Global variable or extern global variabls/functions
*******************************************************************************/
struct fte716_basic_threshold fte716_basic_thr;
struct fte716_test_item fte716_item;
/*******************************************************************************
* Static function prototypes
*******************************************************************************/

static int fte716_get_key_num(void);
static int fte716_enter_factory_mode(void);
static int fte716_raw_data_test(bool *test_result);
static int fte716_cb_test(bool *test_result);

/************************************************************************
* Name: start_test_fte716
* Brief:  Test entry. Determine which test item to test
* Input: none
* Output: none
* Return: Test Result, PASS or FAIL
***********************************************************************/

bool start_test_fte716(void)
{
    bool test_result = true, tmp_result = 1;
    int ret;
    int item_count = 0;

    FTS_TEST_FUNC_ENTER();

    //--------------2. test item
    if (0 == test_data.test_num)
        test_result = false;

    ////////Testing process, the order of the test_item structure of the test items
    for (item_count = 0; item_count < test_data.test_num; item_count++) {
        test_data.test_item_code = test_data.test_item[item_count].itemcode;

        ///////////////////////////////////////////////////////FTE716_ENTER_FACTORY_MODE
        if (CODE_FTE716_ENTER_FACTORY_MODE == test_data.test_item[item_count].itemcode) {
            ret = fte716_enter_factory_mode();
            if (ERROR_CODE_OK != ret || (!tmp_result)) {
                test_result = false;
                test_data.test_item[item_count].testresult = RESULT_NG;
                break;//if this item FAIL, no longer test.
            } else
                test_data.test_item[item_count].testresult = RESULT_PASS;
        }

        ///////////////////////////////////////////////////////FTE716_RAWDATA_TEST
        if (CODE_FTE716_RAWDATA_TEST == test_data.test_item[item_count].itemcode) {
            ret = fte716_raw_data_test(&tmp_result);
            if (ERROR_CODE_OK != ret || (!tmp_result)) {
                test_result = false;
                test_data.test_item[item_count].testresult = RESULT_NG;
            } else
                test_data.test_item[item_count].testresult = RESULT_PASS;
        }

        ///////////////////////////////////////////////////////FTE716_CB_TEST
        if (CODE_FTE716_CB_TEST == test_data.test_item[item_count].itemcode) {
            ret = fte716_cb_test(&tmp_result);
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
* Name: fte716_enter_factory_mode
* Brief:  Check whether TP can enter Factory Mode, and do some thing
* Input: none
* Output: none
* Return: Comm Code. Code = 0x00 is OK, else fail.
***********************************************************************/
static int fte716_enter_factory_mode(void)
{

    int ret = 0;

    ret = enter_factory_mode();
    if (ret < 0) {
        FTS_TEST_SAVE_INFO("enter factory mode fail, can't get tx/rx num");
        return ret;
    }
    ret = fte716_get_key_num();
    if (ret < 0) {
        FTS_TEST_SAVE_INFO("get key num fail");
        return ret;
    }
    return ret;
}

/************************************************************************
* Name: fte716_raw_data_test
* Brief:  TestItem: RawDataTest. Check if MCAP RawData is within the range.
* Input: test_result
* Output: test_result, PASS or FAIL
* Return: Comm Code. Code = 0x00 is OK, else fail.
***********************************************************************/
static int fte716_raw_data_test(bool *test_result)
{
    int ret = ERROR_CODE_OK;
    bool tmp_result = true;
    int i = 0;
    bool include_key = false;
    int *rawdata = NULL;

    FTS_TEST_SAVE_INFO("\n\n==============================Test Item: -------- Raw Data Test\n");

    include_key = fte716_basic_thr.rawdata_test_vk_check;

    memset(test_data.buffer, 0, ((test_data.screen_param.tx_num + 1) * test_data.screen_param.rx_num) * sizeof(int));
    rawdata = test_data.buffer;

    ret = enter_factory_mode();
    if (ERROR_CODE_OK != ret) {
        FTS_TEST_SAVE_INFO("\n Failed to Enter factory mode. Error Code: %d", ret);
        return ret;
    }

    //----------------------------------------------------------Read RawData
    for (i = 0 ; i < 3; i++) { //Lost 3 Frames, In order to obtain stable data
        ret = get_rawdata_incell(rawdata);
    }

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
* Name: fte716_cb_test
* Brief:  TestItem: Cb Test. Check if Cb is within the range.
* Input: none
* Output: test_result, PASS or FAIL
* Return: Comm Code. Code = 0x00 is OK, else fail.
***********************************************************************/
static int fte716_cb_test(bool *test_result)
{
    bool tmp_result = true;
    int ret = ERROR_CODE_OK;
    int col = 0;
    int i = 0;
    bool include_key = false;
    u8 uc_bits = 0;
    int read_key_len = test_data.screen_param.key_num_total;
    int *cbdata = NULL;

    include_key = fte716_basic_thr.cb_test_vk_check;

    FTS_TEST_SAVE_INFO("\n\n==============================Test Item: --------  CB Test\n");

    memset(test_data.buffer, 0, ((test_data.screen_param.tx_num + 1) * test_data.screen_param.rx_num) * sizeof(int));
    cbdata = test_data.buffer;

    ret = enter_factory_mode();
    if (ERROR_CODE_OK != ret) {
        FTS_TEST_SAVE_INFO("\n Failed to Enter factory mode. Error Code: %d", ret);
        tmp_result = false;
    }

    for ( i = 0; i < 10; i++) {
        FTS_TEST_SAVE_INFO("\n start chipclb times:%d. ", i);
        //auto clb
        ret = chip_clb_incell();
        sys_delay(50);
        if ( ret != ERROR_CODE_OK) {
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
        FTS_TEST_SAVE_INFO("\r\n//=========  Read Reg 0x0B Failed!");
    }

    read_key_len = test_data.screen_param.key_num_total;
    if (uc_bits != 0) {
        read_key_len = test_data.screen_param.key_num_total * 2;
    }

    ret = get_cb_incell( 0, test_data.screen_param.tx_num * test_data.screen_param.rx_num  + read_key_len, cbdata );
    if ( ERROR_CODE_OK != ret ) {
        tmp_result = false;
        FTS_TEST_SAVE_INFO("\nFailed to get CB value...\n");
        goto TEST_ERR;
    }

    // KEY
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
    FTS_TEST_SAVE_INFO("\n\n/==========CB Test is NG!");

    return ret;
}
static int fte716_get_key_num(void)
{
    int ret = 0;
    int i = 0;
    u8 keyval = 0;

    test_data.screen_param.key_num = 0;
    for (i = 0; i < 3; i++) {
        ret = read_reg( FTE716_LEFT_KEY_REG, &keyval );
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
        ret = read_reg( FTE716_RIGHT_KEY_REG, &keyval );
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

void init_testitem_fte716(char *strIniFile)
{
    char str[512];

    FTS_TEST_FUNC_ENTER();

    /////////////////////////////////// RAWDATA_TEST
    GetPrivateProfileString("TestItem", "RAWDATA_TEST", "1", str, strIniFile);
    fte716_item.rawdata_test = fts_atoi(str);

    /////////////////////////////////// CB_TEST
    GetPrivateProfileString("TestItem", "CB_TEST", "1", str, strIniFile);
    fte716_item.cb_test = fts_atoi(str);

    /////////////////////////////////// SHORT_CIRCUIT_TEST
    GetPrivateProfileString("TestItem", "SHORT_CIRCUIT_TEST", "1", str, strIniFile);
    fte716_item.short_test = fts_atoi(str);

    /////////////////////////////////// OPEN_TEST
    GetPrivateProfileString("TestItem", "OPEN_TEST", "0", str, strIniFile);
    fte716_item.open_test = fts_atoi(str);

    /////////////////////////////////// LCD_NOISE_TEST
    GetPrivateProfileString("TestItem", "lcd_noise_test", "0", str, strIniFile);
    fte716_item.lcd_noise_test = fts_atoi(str);

    FTS_TEST_FUNC_EXIT();
}

void init_basicthreshold_fte716(char *strIniFile)
{
    char str[512];

    FTS_TEST_FUNC_ENTER();
    //////////////////////////////////////////////////////////// RawData Test
    GetPrivateProfileString("Basic_Threshold", "RawDataTest_VA_Check", "1", str, strIniFile);
    fte716_basic_thr.rawdata_test_va_check = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "RawDataTest_Min", "5000", str, strIniFile);
    fte716_basic_thr.rawdata_test_min = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "RawDataTest_Max", "11000", str, strIniFile);
    fte716_basic_thr.rawdata_test_max = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "RawDataTest_VKey_Check", "1", str, strIniFile);
    fte716_basic_thr.rawdata_test_vk_check = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "RawDataTest_Min_VKey", "5000", str, strIniFile);
    fte716_basic_thr.rawdata_test_min_vk = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "RawDataTest_Max_VKey", "11000", str, strIniFile);
    fte716_basic_thr.rawdata_test_max_vk = fts_atoi(str);
    //////////////////////////////////////////////////////////// CB Test
    GetPrivateProfileString("Basic_Threshold", "CBTest_VA_Check", "1", str, strIniFile);
    fte716_basic_thr.cb_test_va_check = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "CBTest_Min", "3", str, strIniFile);
    fte716_basic_thr.cb_test_min = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "CBTest_Max", "100", str, strIniFile);
    fte716_basic_thr.cb_test_max = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "CBTest_VKey_Check", "1", str, strIniFile);
    fte716_basic_thr.cb_test_vk_check = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "CBTest_Min_Vkey", "3", str, strIniFile);
    fte716_basic_thr.cb_test_min_vk = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "CBTest_Max_Vkey", "100", str, strIniFile);
    fte716_basic_thr.cb_test_max_vk = fts_atoi(str);

    //////////////////////////////////////////////////////////// Short Circuit Test
    GetPrivateProfileString("Basic_Threshold", "ShortCircuit_ResMin", "1200", str, strIniFile);
    fte716_basic_thr.short_res_min = fts_atoi(str);

    ////////////////////////////////////////////////////////////LCDNoiseTest
    GetPrivateProfileString("Basic_Threshold", "LCDNoiseTest_FrameNum", "200", str, strIniFile);
    fte716_basic_thr.lcd_noise_test_frame_num = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "LCDNoiseTest_Coefficient", "60", str, strIniFile);
    fte716_basic_thr.lcd_noise_test_coefficient = fts_atoi(str);
    GetPrivateProfileString("Basic_Threshold", "LCDNoiseTest_NoiseMode", "0", str, strIniFile);
    fte716_basic_thr.lcd_noise_test_noise_mode = fts_atoi(str);


    ////////////////////////////////////////////////////////////open test
    GetPrivateProfileString("Basic_Threshold", "OpenTest_CBMin", "100", str, strIniFile);
    fte716_basic_thr.open_test_cb_min = fts_atoi(str);

    FTS_TEST_FUNC_EXIT();

}

void init_detailthreshold_fte716(char *ini)
{
    FTS_TEST_FUNC_ENTER();

    OnInit_InvalidNode(ini);
    OnInit_DThreshold_CBTest(ini);
    OnThreshold_VkAndVaRawDataSeparateTest(ini);

    FTS_TEST_FUNC_EXIT();
}

void set_testitem_sequence_fte716(void)
{
    test_data.test_num = 0;

    FTS_TEST_FUNC_ENTER();

    //////////////////////////////////////////////////Enter Factory Mode
    fts_set_testitem(CODE_FTE716_ENTER_FACTORY_MODE);

    //////////////////////////////////////////////////CB_TEST
    if ( fte716_item.cb_test == 1) {
        fts_set_testitem(CODE_FTE716_CB_TEST);
    }

    //////////////////////////////////////////////////RawData Test
    if ( fte716_item.rawdata_test == 1) {
        fts_set_testitem(CODE_FTE716_RAWDATA_TEST);
    }

    //////////////////////////////////////////////////OPEN_TEST
    if ( fte716_item.open_test == 1) {
        fts_set_testitem(CODE_FTE716_OPEN_TEST);
    }

    //////////////////////////////////////////////////SHORT_CIRCUIT_TEST
    if ( fte716_item.short_test == 1) {
        fts_set_testitem(CODE_FTE716_SHORT_CIRCUIT_TEST) ;
    }

    FTS_TEST_FUNC_EXIT();
}

struct test_funcs test_func_fte716 = {
    .ic_series = TEST_ICSERIES(IC_FTE716),
    .init_testitem = init_testitem_fte716,
    .init_basicthreshold = init_basicthreshold_fte716,
    .init_detailthreshold = init_detailthreshold_fte716,
    .set_testitem_sequence  = set_testitem_sequence_fte716,
    .start_test = start_test_fte716,
};
