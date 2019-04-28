/* This file is free software; you can redistribute it and/or modify
 * it under the terms of version 2 of the GNU General Public License
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301, USA.
*/
/**
 *
 * @file    mstar_drv_platform_porting_layer.c
 *
 * @brief   This file defines the interface of touch screen
 *
 *
 */
 
/*=============================================================*/
// INCLUDE FILE
/*=============================================================*/

#include "mstar_drv_platform_porting_layer.h"
#include "mstar_drv_ic_fw_porting_layer.h"
#include "mstar_drv_platform_interface.h"
#include "mstar_drv_utility_adaption.h"
#include "mstar_drv_main.h"

#define CTP_RST    0
#define CTP_INT    1
unsigned int Msg2338A_touch_irq = 0;

#ifdef CONFIG_ENABLE_HOTKNOT
#include "mstar_drv_hotknot_queue.h"
#endif //CONFIG_ENABLE_HOTKNOT

#ifdef CONFIG_ENABLE_JNI_INTERFACE
#include "mstar_drv_jni_interface.h"
#endif //CONFIG_ENABLE_JNI_INTERFACE

#ifdef CONFIG_ONTIM_DSM	

#include <ontim/ontim_dsm.h>

extern struct dsm_client *msg2238a_dsm_client;
extern char msg2238a_vendor_name[];
extern u32  msg2238a_irq_count;
extern u32  msg2238a_irq_run_count;
#endif

/*=============================================================*/
// EXTREN VARIABLE DECLARATION
/*=============================================================*/

extern struct i2c_client *g_I2cClient;

extern struct kset *g_TouchKSet;
extern struct kobject *g_TouchKObj;

#ifdef CONFIG_ENABLE_GESTURE_WAKEUP
#ifdef CONFIG_ENABLE_GESTURE_DEBUG_MODE
extern struct kset *g_GestureKSet;
extern struct kobject *g_GestureKObj;
#endif //CONFIG_ENABLE_GESTURE_DEBUG_MODE
#endif //CONFIG_ENABLE_GESTURE_WAKEUP

#ifdef CONFIG_ENABLE_PROXIMITY_DETECTION
extern u8 g_FaceClosingTp;
extern u8 g_EnableTpProximity;
#endif //CONFIG_ENABLE_PROXIMITY_DETECTION

#ifdef CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM
extern struct tpd_device *tpd;
#endif //CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM

#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM) || defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM)
#ifdef CONFIG_ENABLE_REGULATOR_POWER_ON
extern struct regulator *g_ReguVdd;
extern struct regulator *g_ReguVcc_i2c;
#endif //CONFIG_ENABLE_REGULATOR_POWER_ON
#endif

#ifdef CONFIG_ENABLE_HOTKNOT
extern struct miscdevice hotknot_miscdevice;
extern u8 g_HotKnotState;
#endif //CONFIG_ENABLE_HOTKNOT

extern u8 IS_FIRMWARE_DATA_LOG_ENABLED;

/*=============================================================*/
// LOCAL VARIABLE DEFINITION
/*=============================================================*/

struct mutex g_Mutex;
spinlock_t _gIrqLock;

//static struct work_struct _gFingerTouchWork;
static int _gInterruptFlag = 0;

#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM) || defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM)
#ifdef CONFIG_ENABLE_NOTIFIER_FB
static struct notifier_block _gFbNotifier;
#else
static struct early_suspend _gEarlySuspend;
#endif //CONFIG_ENABLE_NOTIFIER_FB

#ifdef CONFIG_ENABLE_TOUCH_PIN_CONTROL
static int _gGpioReset = 0;
static int _gGpioIrq = 0;
static int MS_TS_MSG_IC_GPIO_RST = 0;
static int MS_TS_MSG_IC_GPIO_INT = 0;

static struct pinctrl *_gTsPinCtrl = NULL;
static struct pinctrl_state *_gPinCtrlStateActive = NULL;
static struct pinctrl_state *_gPinCtrlStateSuspend = NULL;
static struct pinctrl_state *_gPinCtrlStateRelease = NULL;
#endif //CONFIG_ENABLE_TOUCH_PIN_CONTROL
#endif //CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM || CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM

#ifdef CONFIG_ENABLE_PROXIMITY_DETECTION
#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM)
static atomic_t _gPsFlag;

#elif defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM)
static struct sensors_classdev _gProximityCdev;

static struct sensors_classdev sensors_proximity_cdev = {
    .name = "msg2xxx-proximity",
    .vendor = "MStar",
    .version = 1,
    .handle = SENSORS_PROXIMITY_HANDLE,
    .type = SENSOR_TYPE_PROXIMITY,
    .max_range = "5.0",
    .resolution = "5.0",
    .sensor_power = "0.1",
    .min_delay = 0,
    .fifo_reserved_event_count = 0,
    .fifo_max_event_count = 0,
    .enabled = 0,
    .delay_msec = 200,
    .sensors_enable = NULL,
    .sensors_poll_delay = NULL,
};
#endif 
#endif //CONFIG_ENABLE_PROXIMITY_DETECTION

#ifdef CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM
#ifndef CONFIG_USE_IRQ_INTERRUPT_FOR_MTK_PLATFORM
static DECLARE_WAIT_QUEUE_HEAD(_gWaiter);
static struct task_struct *_gThread = NULL;
static int _gTpdFlag = 0;
#endif //CONFIG_USE_IRQ_INTERRUPT_FOR_MTK_PLATFORM
#endif //CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM

/*=============================================================*/
// GLOBAL VARIABLE DEFINITION
/*=============================================================*/

#ifdef CONFIG_TP_HAVE_KEY
const int g_TpVirtualKey[] = {TOUCH_KEY_BACK, TOUCH_KEY_MENU, TOUCH_KEY_HOME};//TOUCH_KEY_SEARCH,

#ifdef CONFIG_ENABLE_REPORT_KEY_WITH_COORDINATE
#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM) || defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM)
struct kobject *g_PropertiesKObj = NULL;
#elif defined(CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM)
#define BUTTON_W (100)
#define BUTTON_H (100)

const int g_TpVirtualKeyDimLocal[MAX_KEY_NUM][4] = {{BUTTON_W/2*1,TOUCH_SCREEN_Y_MAX+BUTTON_H/2,BUTTON_W,BUTTON_H},
                                                    {BUTTON_W/2*3,TOUCH_SCREEN_Y_MAX+BUTTON_H/2,BUTTON_W,BUTTON_H},
                                                    {BUTTON_W/2*5,TOUCH_SCREEN_Y_MAX+BUTTON_H/2,BUTTON_W,BUTTON_H}};
                                                    //{BUTTON_W/2*7,TOUCH_SCREEN_Y_MAX+BUTTON_H/2,BUTTON_W,BUTTON_H}};
#endif 
#endif //CONFIG_ENABLE_REPORT_KEY_WITH_COORDINATE
#endif //CONFIG_TP_HAVE_KEY

#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM) || defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM)
#ifdef CONFIG_ENABLE_PROXIMITY_DETECTION
struct input_dev *g_ProximityInputDevice = NULL;
#endif //CONFIG_ENABLE_PROXIMITY_DETECTION
#endif //CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM || CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM

struct input_dev *g_InputDevice = NULL;
//static int _gIrq = -1;

/*=============================================================*/
// GLOBAL FUNCTION DECLARATION
/*=============================================================*/

#ifdef CONFIG_ENABLE_PROXIMITY_DETECTION
#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM)
void DrvPlatformLyrTpPsEnable(int nEnable);
#elif defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM)
int DrvPlatformLyrTpPsEnable(struct sensors_classdev* pProximityCdev, unsigned int nEnable);
#endif
#endif //CONFIG_ENABLE_PROXIMITY_DETECTION

/*=============================================================*/
// LOCAL FUNCTION DEFINITION
/*=============================================================*/

/* read data through I2C then report data to input sub-system when interrupt occurred */
#if 0//hank mark
static void _DrvPlatformLyrFingerTouchDoWork(struct work_struct *pWork)
{
    unsigned long nIrqFlag;

    DBG("*** %s() ***\n", __func__);

    DrvIcFwLyrHandleFingerTouch(NULL, 0);

    DBG("*** %s() _gInterruptFlag = %d ***\n", __func__, _gInterruptFlag);  // add for debug

    spin_lock_irqsave(&_gIrqLock, nIrqFlag);

#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM) || defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM)

    if (_gInterruptFlag == 0) 
    {
        enable_irq(_gIrq);

        _gInterruptFlag = 1;
    } 
        
#elif defined(CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM)

    if (_gInterruptFlag == 0) 
    {
        //mt_eint_unmask(CUST_EINT_TOUCH_PANEL_NUM);
	enable_irq(Msg2338A_touch_irq);

        _gInterruptFlag = 1;
    }
#endif


#ifdef CONFIG_ENABLE_ESD_CHECK
esd_check_enable();
#endif

    spin_unlock_irqrestore(&_gIrqLock, nIrqFlag);
}
#endif

#ifdef CONFIG_ENABLE_PROXIMITY_DETECTION
#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM)

static int _DrvPlatformLyrProximityOpen(struct inode *inode, struct file *file)
{
    int nRetVal = 0;

    DBG("*** %s() ***\n", __func__);

    nRetVal = nonseekable_open(inode, file);
    if (nRetVal < 0)
    {
        return nRetVal;
    }

    file->private_data = i2c_get_clientdata(g_I2cClient);
    
    return 0;
}

static int _DrvPlatformLyrProximityRelease(struct inode *inode, struct file *file)
{
    DBG("*** %s() ***\n", __func__);

    return 0;
}

static long _DrvPlatformLyrProximityIoctl(struct file *file, unsigned int cmd, unsigned long arg)
{
#if 0
    DBG("*** %s() *** cmd = %d\n", __func__, _IOC_NR(cmd));

    switch (cmd)
    {
        case GTP_IOCTL_PROX_ON:
            DrvPlatformLyrTpPsEnable(1);
            break;
        case GTP_IOCTL_PROX_OFF:
            DrvPlatformLyrTpPsEnable(0);
            break;
        default:
            return -EINVAL;
    }
#else
    void __user *argp = (void __user *)arg;
    int flag;
    unsigned char data;
    
    DBG("*** %s() *** cmd = %d\n", __func__, _IOC_NR(cmd));

    switch (cmd)
    {
        case LTR_IOCTL_SET_PFLAG:
            if (copy_from_user(&flag, argp, sizeof(flag)))
            {
                return -EFAULT;
            }
		
            if (flag < 0 || flag > 1)
            {
                return -EINVAL;
            }
            DBG("flag = %d", flag);
            
            atomic_set(&_gPsFlag, flag);	
            
            if (flag == 1)
            {
                DrvPlatformLyrTpPsEnable(1);
            }
            else if (flag == 0)
            {
                DrvPlatformLyrTpPsEnable(0);
            }		
            break;
		
        case LTR_IOCTL_GET_PFLAG:
            flag = atomic_read(&_gPsFlag);
            
            if (copy_to_user(argp, &flag, sizeof(flag))) 
            {
                return -EFAULT;
            }
            DBG("flag = %d", flag);
            break;

        case LTR_IOCTL_GET_DATA:
            if (copy_to_user(argp, &data, sizeof(data)))
            {
                return -EFAULT;
            }
            DBG("flag = %d", flag);
            break;

        case GTP_IOCTL_PROX_ON:
            DrvPlatformLyrTpPsEnable(1);
            break;
        
        case GTP_IOCTL_PROX_OFF:
            DrvPlatformLyrTpPsEnable(0);
            break;

        default:
            DBG("*** %s() *** Invalid cmd = %d\n", __func__, _IOC_NR(cmd));
            return -EINVAL;
        } 
#endif

    return 0;
}

static const struct file_operations gtp_proximity_fops = {
    .owner = THIS_MODULE,
    .open = _DrvPlatformLyrProximityOpen,
    .release = NULL, //_DrvPlatformLyrProximityRelease,
    .unlocked_ioctl = _DrvPlatformLyrProximityIoctl,
};

static struct miscdevice gtp_proximity_misc = {
    .minor = MISC_DYNAMIC_MINOR,
    .name = "ltr_558als", // Match the hal's name
    .fops = &gtp_proximity_fops,
};

static int _DrvPlatformLyrProximityInputDeviceInit(struct i2c_client *pClient)
{
    int nRetVal = 0;
    
    DBG("*** %s() ***\n", __func__);

    nRetVal = misc_register(&gtp_proximity_misc);
    if (nRetVal)
    {
        DBG("*** Failed to misc_register() for proximity *** nRetVal=%d\n", nRetVal);
        goto ERROR_MISC_REGISTER_FAILED;
    }

    g_ProximityInputDevice = input_allocate_device();
    if (g_ProximityInputDevice == NULL)
    {
        DBG("*** Failed to allocate proximity input device ***\n");
        nRetVal = -ENOMEM;
        goto ERROR_INPUT_DEVICE_ALLOCATE_FAILED;
    }

    g_ProximityInputDevice->name = "alps_pxy";
    g_ProximityInputDevice->phys  = "alps_pxy";
    g_ProximityInputDevice->id.bustype = BUS_I2C;
    g_ProximityInputDevice->dev.parent = &pClient->dev;
    g_ProximityInputDevice->id.vendor = 0x0001;
    g_ProximityInputDevice->id.product = 0x0001;
    g_ProximityInputDevice->id.version = 0x0010;

    set_bit(EV_ABS, g_ProximityInputDevice->evbit);
	
    input_set_abs_params(g_ProximityInputDevice, ABS_DISTANCE, 0, 1, 0, 0);

    nRetVal = input_register_device(g_ProximityInputDevice);
    if (nRetVal < 0) {
        DBG("*** Unable to register proximity input device *** nRetVal=%d\n", nRetVal);
        goto ERROR_INPUT_DEVICE_REGISTER_FAILED;
    }
    
    return 0;

ERROR_INPUT_DEVICE_REGISTER_FAILED:
    if (g_ProximityInputDevice)
    {
        input_free_device(g_ProximityInputDevice);
        g_ProximityInputDevice = NULL;
    }
ERROR_INPUT_DEVICE_ALLOCATE_FAILED:
    misc_deregister(&gtp_proximity_misc);
ERROR_MISC_REGISTER_FAILED:

    return nRetVal;
}

static int _DrvPlatformLyrProximityInputDeviceUnInit(void)
{
    DBG("*** %s() ***\n", __func__);

    misc_deregister(&gtp_proximity_misc);

    if (g_ProximityInputDevice)
    {
        input_unregister_device(g_ProximityInputDevice);
        g_ProximityInputDevice = NULL;
    }
    
    return 0;
}

#elif defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM)

static ssize_t _DrvPlatformLyrProximityDetectionShow(struct device *dev, struct device_attribute *attr, char *buf)
{
    DBG("*** %s() ***\n", __func__);

    DBG("*** Tp Proximity State = %s ***\n", g_EnableTpProximity ? "open" : "close");
    
    return sprintf(buf, "%s\n", g_EnableTpProximity ? "open" : "close");
}

static ssize_t _DrvPlatformLyrProximityDetectionStore(struct device *dev, struct device_attribute *attr, const char *buf, size_t size)
{
    DBG("*** %s() ***\n", __func__);

    if (buf != NULL)
    {
        if (sysfs_streq(buf, "1"))
        {
            DrvPlatformLyrTpPsEnable(&_gProximityCdev, 1);
        }
        else if (sysfs_streq(buf, "0"))
        {
            DrvPlatformLyrTpPsEnable(&_gProximityCdev, 0);
        }
    }

    return size;
}

static struct device_attribute proximity_attribute = __ATTR(proximity, 0666/*0664*/, _DrvPlatformLyrProximityDetectionShow, _DrvPlatformLyrProximityDetectionStore);

static struct attribute *proximity_detection_attrs[] =
{
    &proximity_attribute.attr,
    NULL
};

static struct attribute_group proximity_detection_attribute_group = {
    .name = "Driver",
    .attrs = proximity_detection_attrs,
};

static int _DrvPlatformLyrProximityInputDeviceInit(struct i2c_client *pClient)
{
    int nRetVal = 0;
    
    DBG("*** %s() ***\n", __func__);

    g_ProximityInputDevice = input_allocate_device();
    if (g_ProximityInputDevice == NULL)
    {
        DBG("*** Failed to allocate proximity input device ***\n");
        nRetVal = -ENOMEM;
        goto ERROR_INPUT_DEVICE_ALLOCATE_FAILED;
    }

    g_ProximityInputDevice->name = "msg2xxx-ps";
    g_ProximityInputDevice->phys = "I2C";
    g_ProximityInputDevice->dev.parent = &pClient->dev;
    g_ProximityInputDevice->id.bustype = BUS_I2C;

    set_bit(EV_ABS, g_ProximityInputDevice->evbit);

    input_set_abs_params(g_ProximityInputDevice, ABS_DISTANCE, 0, 1, 0, 0);
    
    nRetVal = input_register_device(g_ProximityInputDevice);
    if (nRetVal < 0) {
        DBG("*** Unable to register proximity input device *** nRetVal=%d\n", nRetVal);
        goto ERROR_INPUT_DEVICE_REGISTER_FAILED;
    }

    mdelay(10);

    nRetVal = sysfs_create_group(&g_ProximityInputDevice->dev.kobj, &proximity_detection_attribute_group);
    if (nRetVal < 0)
    {
        DBG("*** Failed to sysfs_create_group() for proximity *** nRetVal=%d\n", nRetVal);
        goto ERROR_SYSFS_CREATE_GROUP_FAILED;
    }

    input_set_drvdata(g_ProximityInputDevice, NULL);

    sensors_proximity_cdev.sensors_enable = DrvPlatformLyrTpPsEnable;
    nRetVal = sensors_classdev_register(&pClient->dev, &sensors_proximity_cdev);
    if (nRetVal < 0) {
        DBG("*** Failed to sensors_classdev_register() for proximity *** nRetVal=%d\n", nRetVal);
        goto ERROR_SENSORS_CLASSDEV_REGISTER_FAILED;
    }

    return 0;

ERROR_SENSORS_CLASSDEV_REGISTER_FAILED:
ERROR_SYSFS_CREATE_GROUP_FAILED:
    if (g_ProximityInputDevice)
    {
        input_unregister_device(g_ProximityInputDevice);
        g_ProximityInputDevice = NULL;
    }
ERROR_INPUT_DEVICE_REGISTER_FAILED:
    if (g_ProximityInputDevice)
    {
        input_free_device(g_ProximityInputDevice);
        g_ProximityInputDevice = NULL;
    }
ERROR_INPUT_DEVICE_ALLOCATE_FAILED:

    return nRetVal;
}

static int _DrvPlatformLyrProximityInputDeviceUnInit(void)
{
    DBG("*** %s() ***\n", __func__);

    if (g_ProximityInputDevice)
    {
        input_unregister_device(g_ProximityInputDevice);
        g_ProximityInputDevice = NULL;
    }
    
    return 0;
}

#endif
#endif //CONFIG_ENABLE_PROXIMITY_DETECTION

#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM) || defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM)

#ifdef CONFIG_TP_HAVE_KEY
#ifdef CONFIG_ENABLE_REPORT_KEY_WITH_COORDINATE
static ssize_t _DrvPlatformLyrVirtualKeysShow(struct kobject *kobj, struct kobj_attribute *attr, char *buf)
{
    DBG("*** %s() ***\n", __func__);

    return sprintf(buf,
        __stringify(EV_KEY) ":" __stringify(KEY_HOMEPAGE) ":50:1330:100:100"
        ":" __stringify(EV_KEY) ":" __stringify(KEY_MENU) ":150:1330:100:100"
        ":" __stringify(EV_KEY) ":" __stringify(KEY_BACK) ":250:1330:100:100"
        ":" __stringify(EV_KEY) ":" __stringify(KEY_SEARCH) ":350:1330:100:100"
        "\n");
}

static struct kobj_attribute virtual_keys_attr = {
    .attr = {
        .name = "virtualkeys.msg2xxx_ts",
        .mode = S_IRUGO,
    },
    .show = &_DrvPlatformLyrVirtualKeysShow,
};

static struct attribute *properties_attrs[] = {
    &virtual_keys_attr.attr,
    NULL
};

static struct attribute_group properties_attr_group = {
    .attrs = properties_attrs,
};

static void _DrvPlatformLyrVirtualKeysInit(void)
{
    s32 nRetVal = 0;

    DBG("*** %s() ***\n", __func__);

    g_PropertiesKObj = kobject_create_and_add("board_properties", NULL);
    if (g_PropertiesKObj == NULL)
    {
        DBG("*** Failed to kobject_create_and_add() for virtual keys *** nRetVal=%d\n", nRetVal);
        return;
    }
    
    nRetVal = sysfs_create_group(g_PropertiesKObj, &properties_attr_group);
    if (nRetVal < 0)
    {
        DBG("*** Failed to sysfs_create_group() for virtual keys *** nRetVal=%d\n", nRetVal);

        kobject_put(g_PropertiesKObj);
        g_PropertiesKObj = NULL;
    }
}

static void _DrvPlatformLyrVirtualKeysUnInit(void)
{
    DBG("*** %s() ***\n", __func__);

    if (g_PropertiesKObj)
    {
        kobject_put(g_PropertiesKObj);
        g_PropertiesKObj = NULL;
    }
}
#endif //CONFIG_ENABLE_REPORT_KEY_WITH_COORDINATE
#endif //CONFIG_TP_HAVE_KEY

#ifdef CONFIG_ENABLE_TOUCH_PIN_CONTROL
static s32 _DrvPlatformLyrTouchPinCtrlInit(struct i2c_client *pClient)
{
    s32 nRetVal = 0;
    u32 nFlag = 0;
    struct device_node *pDeviceNode = pClient->dev.of_node;
	
    DBG("*** %s() ***\n", __func__);
    
    _gGpioReset = of_get_named_gpio_flags(pDeviceNode, "mstar,reset-gpio",	0, &nFlag);
    
    MS_TS_MSG_IC_GPIO_RST = _gGpioReset;
    
    if (_gGpioReset < 0)
    {
        return _gGpioReset;
    }

    _gGpioIrq = of_get_named_gpio_flags(pDeviceNode, "mstar,irq-gpio",	0, &nFlag);
    
    MS_TS_MSG_IC_GPIO_INT = _gGpioIrq;
	
    DBG("_gGpioReset = %d, _gGpioIrq = %d\n", _gGpioReset, _gGpioIrq);
    
    if (_gGpioIrq < 0)
    {
        return _gGpioIrq;
    }
	
    /* Get pinctrl if target uses pinctrl */
    _gTsPinCtrl = devm_pinctrl_get(&(pClient->dev));
    if (IS_ERR_OR_NULL(_gTsPinCtrl)) 
    {
        nRetVal = PTR_ERR(_gTsPinCtrl);
        DBG("Target does not use pinctrl nRetVal=%d\n", nRetVal);
        goto ERROR_PINCTRL_GET;
    }

    _gPinCtrlStateActive = pinctrl_lookup_state(_gTsPinCtrl, PINCTRL_STATE_ACTIVE);
    if (IS_ERR_OR_NULL(_gPinCtrlStateActive)) 
    {
        nRetVal = PTR_ERR(_gPinCtrlStateActive);
        DBG("Can not lookup %s pinstate nRetVal=%d\n", PINCTRL_STATE_ACTIVE, nRetVal);
        goto ERROR_PINCTRL_LOOKUP;
    }

    _gPinCtrlStateSuspend = pinctrl_lookup_state(_gTsPinCtrl, PINCTRL_STATE_SUSPEND);
    if (IS_ERR_OR_NULL(_gPinCtrlStateSuspend)) 
    {
        nRetVal = PTR_ERR(_gPinCtrlStateSuspend);
        DBG("Can not lookup %s pinstate nRetVal=%d\n", PINCTRL_STATE_SUSPEND, nRetVal);
        goto ERROR_PINCTRL_LOOKUP;
    }

    _gPinCtrlStateRelease = pinctrl_lookup_state(_gTsPinCtrl, PINCTRL_STATE_RELEASE);
    if (IS_ERR_OR_NULL(_gPinCtrlStateRelease)) 
    {
        nRetVal = PTR_ERR(_gPinCtrlStateRelease);
        DBG("Can not lookup %s pinstate nRetVal=%d\n", PINCTRL_STATE_RELEASE, nRetVal);
    }
    
    pinctrl_select_state(_gTsPinCtrl, _gPinCtrlStateActive);
    
    return 0;

ERROR_PINCTRL_LOOKUP:
    devm_pinctrl_put(_gTsPinCtrl);
ERROR_PINCTRL_GET:
    _gTsPinCtrl = NULL;
	
    return nRetVal;
}

static void _DrvPlatformLyrTouchPinCtrlUnInit(void)
{
    DBG("*** %s() ***\n", __func__);

    if (_gTsPinCtrl)
    {
        devm_pinctrl_put(_gTsPinCtrl);
        _gTsPinCtrl = NULL;
    }
}
#endif //CONFIG_ENABLE_TOUCH_PIN_CONTROL

/* The interrupt service routine will be triggered when interrupt occurred */
static irqreturn_t _DrvPlatformLyrFingerTouchInterruptHandler(s32 nIrq, void *pDeviceId)
{
    unsigned long nIrqFlag;

    DBG("*** %s() ***\n", __func__);

    DBG("*** %s() _gInterruptFlag = %d ***\n", __func__, _gInterruptFlag);  // add for debug

    spin_lock_irqsave(&_gIrqLock, nIrqFlag);

    	#ifdef CONFIG_ENABLE_ESD_CHECK
	esd_check_disable();
	#endif	


    if (_gInterruptFlag == 1) 
    {
        disable_irq_nosync(_gIrq);

        _gInterruptFlag = 0;

        schedule_work(&_gFingerTouchWork);
    }

    spin_unlock_irqrestore(&_gIrqLock, nIrqFlag);
    
    return IRQ_HANDLED;
}
#elif defined(CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM)
static void _DrvPlatformLyrFingerTouchInterruptHandler(void)
{
    unsigned long nIrqFlag;

    DBG("*** %s() ***\n", __func__);  

   // printk(KERN_ERR "mtk_tpd: %s() _gInterruptFlag = %d ***\n", __func__, _gInterruptFlag);  // add for debug

    spin_lock_irqsave(&_gIrqLock, nIrqFlag);

#ifdef CONFIG_USE_IRQ_INTERRUPT_FOR_MTK_PLATFORM

    if (_gInterruptFlag == 1)
    {
        //mt_eint_mask(CUST_EINT_TOUCH_PANEL_NUM);
	disable_irq(Msg2338A_touch_irq);

        _gInterruptFlag = 0;
#ifdef CONFIG_ENABLE_ESD_CHECK
	esd_check_disable();
#endif

        schedule_work(&_gFingerTouchWork);
    }

#else    

    if (_gInterruptFlag == 1) 
    {    
        _gInterruptFlag = 0;
        //mt_eint_mask(CUST_EINT_TOUCH_PANEL_NUM);
	disable_irq_nosync(Msg2338A_touch_irq);

        _gTpdFlag = 1;
        wake_up_interruptible(&_gWaiter);
    }        
#endif //CONFIG_USE_IRQ_INTERRUPT_FOR_MTK_PLATFORM
//+add by hzb for dsm
#ifdef CONFIG_ONTIM_DSM	
	 msg2238a_irq_count++;
#endif
//-add by hzb for dsm

   // printk(KERN_ERR "mtk_tpd: %s() _gInterruptFlag = %d ***END\n", __func__, _gInterruptFlag);  // add for debug
    spin_unlock_irqrestore(&_gIrqLock, nIrqFlag);
    //return IRQ_HANDLED;
}

#ifndef CONFIG_USE_IRQ_INTERRUPT_FOR_MTK_PLATFORM
static int _DrvPlatformLyrFingerTouchHandler(void *pUnUsed)
{
    unsigned long nIrqFlag;
    struct sched_param param = { .sched_priority = RTPM_PRIO_TPD };
    sched_setscheduler(current, SCHED_RR, &param);

    DBG("*** %s() ***\n", __func__);  
	
    do
    {
        set_current_state(TASK_INTERRUPTIBLE);
        wait_event_interruptible(_gWaiter, _gTpdFlag != 0);
        _gTpdFlag = 0;
        
        set_current_state(TASK_RUNNING);

    //printk(KERN_ERR "mtk_tpd: %s() _gInterruptFlag = %d\n", __func__, _gInterruptFlag);  // add for debug
    
#ifdef CONFIG_ENABLE_ESD_CHECK
	esd_check_disable();
#endif

        DrvIcFwLyrHandleFingerTouch(NULL, 0);

        DBG("*** %s() _gInterruptFlag = %d ***\n", __func__, _gInterruptFlag);  // add for debug

        spin_lock_irqsave(&_gIrqLock, nIrqFlag);

        if (_gInterruptFlag == 0)        
        {
            //mt_eint_unmask(CUST_EINT_TOUCH_PANEL_NUM);
	enable_irq(Msg2338A_touch_irq);

            _gInterruptFlag = 1;
        } 
//+add by hzb for dsm
#ifdef CONFIG_ONTIM_DSM	
		 msg2238a_irq_run_count++;
#endif
//-add by hzb for dsm
#ifdef CONFIG_ENABLE_ESD_CHECK
	esd_check_enable();
#endif
        spin_unlock_irqrestore(&_gIrqLock, nIrqFlag);
		
    //printk(KERN_ERR "mtk_tpd: %s() _gInterruptFlag = %d  END\n", __func__, _gInterruptFlag);  // add for debug
    } while (!kthread_should_stop());
	
    return 0;
}
#endif //CONFIG_USE_IRQ_INTERRUPT_FOR_MTK_PLATFORM
#endif

/*=============================================================*/
// GLOBAL FUNCTION DEFINITION
/*=============================================================*/
#ifdef CONFIG_ENABLE_ESD_CHECK
#if 1
#define ESD_CHECK_LOG(fmt,arg...)          printk("ESD_CHECK : " fmt"\n",##arg);
#else
#define ESD_CHECK_LOG(fmt,arg...)   
#endif
#define ESD_CHECK_CIRCLE        300
 static struct delayed_work esd_check_delayed_work;
 static struct workqueue_struct *esd_check_workqueue = NULL;
 static u8 bInitEsdCheck = 0;
 static u8 bEnableEsdCheck = 0;


void esd_check_enable(void)
{
	if(bInitEsdCheck)
	{
		//ESD_CHECK_LOG("enable.");
		queue_delayed_work(esd_check_workqueue, &esd_check_delayed_work, ESD_CHECK_CIRCLE);
		bEnableEsdCheck = 1;
	}
	else
	{
		ESD_CHECK_LOG("enable fail bInitEsdCheck=%d.",bInitEsdCheck);
	}
}

void esd_check_disable(void)
{
	if(bInitEsdCheck)
	{
		//ESD_CHECK_LOG("disable.");
		bEnableEsdCheck = 0;
		cancel_delayed_work(&esd_check_delayed_work);
	}
	else
	{
		ESD_CHECK_LOG("disable fail bInitEsdCheck=%d.",bInitEsdCheck);
	}
}

extern u32 SLAVE_I2C_ID_DWI2C;

static void esd_check_func(struct work_struct *work)
{
	int ret = -1;
	unsigned char data[8] ;

	ESD_CHECK_LOG(" esd check start. bEnableEsdCheck = %d.\n", bEnableEsdCheck);
	if(!bEnableEsdCheck)
	{
		return;
	}

	//extern s32 IicReadData(u8 nSlaveId, u8* pBuf, u16 nSize);

	mutex_lock(&g_Mutex);
	ret = IicReadData(SLAVE_I2C_ID_DWI2C,data , 8);
	//ESD_CHECK_LOG("ret=%d; data[0]=%x; data[1]=%x; data[2]=%x; data[3]=%x; data[4]=%x; data[5]=%x; data[6]=%x; data[7]=%x\n", ret,data[0], data[1], data[2], data[3],data[4], data[5], data[6], data[7]);

	mutex_unlock(&g_Mutex);
	
	if(ret > 0 )			
	{
		
		if(data[0]==0||data[0]==0x52)
		{
			ESD_CHECK_LOG("esd check ok." );
		}
		else
		{
			ESD_CHECK_LOG("esd check fail. case1.");
			DrvPlatformLyrTouchDeviceResetHw();
		}
	}
	else 
	{
		ESD_CHECK_LOG("esd check fail. case2." );
		DrvPlatformLyrTouchDeviceResetHw();
	}

	esd_check_enable();
}

void esd_check_init(void)
{
	ESD_CHECK_LOG("esd check init. ");
	INIT_DELAYED_WORK(&esd_check_delayed_work, esd_check_func);
	esd_check_workqueue = create_singlethread_workqueue("esd_check");
	bInitEsdCheck = 1;
}

#endif



#ifdef CONFIG_ENABLE_REGULATOR_POWER_ON
void DrvPlatformLyrTouchDeviceRegulatorPowerOn(bool nFlag)
{
#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM) || defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM)
    s32 nRetVal = 0;

    DBG("*** %s() ***\n", __func__);

    if (nFlag == true)
    {
        nRetVal = regulator_enable(g_ReguVdd);
        if (nRetVal)
        {
            DBG("regulator_enable(g_ReguVdd) failed. nRetVal=%d\n", nRetVal);
        }
        mdelay(20);

        nRetVal = regulator_enable(g_ReguVcc_i2c);
        if (nRetVal)
        {
            DBG("regulator_enable(g_ReguVcc_i2c) failed. nRetVal=%d\n", nRetVal);
        }
        mdelay(20);
    }
    else
    {
        nRetVal = regulator_disable(g_ReguVdd);
        if (nRetVal)
        {
            DBG("regulator_disable(g_ReguVdd) failed. nRetVal=%d\n", nRetVal);
        }
        mdelay(20);

        nRetVal = regulator_disable(g_ReguVcc_i2c);
        if (nRetVal)
        {
            DBG("regulator_disable(g_ReguVcc_i2c) failed. nRetVal=%d\n", nRetVal);
        }
        mdelay(20);
    }
#elif defined(CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM)
    s32 nRetVal = 0;
    //hwPowerOn(MT6323_POWER_LDO_VGP1, VOL_2800, "TP"); // For specific MTK BB chip(ex. MT6582), need to enable this function call for correctly power on Touch IC.
    //hwPowerOn(PMIC_APP_CAP_TOUCH_VDD, VOL_2800, "TP"); // For specific MTK BB chip(ex. MT6735), need to enable this function call for correctly power on Touch IC.
	nRetVal = regulator_enable(tpd->reg);
	if (nRetVal != 0)
		TPD_DMESG("Failed to enable reg-vgp6: %d\n", nRetVal);
#endif
}
#endif //CONFIG_ENABLE_REGULATOR_POWER_ON

void DrvPlatformLyrTouchDevicePowerOn(void)
{
    DBG("*** %s() ***\n", __func__);
    
#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM) || defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM)
    gpio_direction_output(MS_TS_MSG_IC_GPIO_RST, 1);
//    gpio_set_value(MS_TS_MSG_IC_GPIO_RST, 1); 
    udelay(100); 
    gpio_set_value(MS_TS_MSG_IC_GPIO_RST, 0);
    udelay(100); 
    gpio_set_value(MS_TS_MSG_IC_GPIO_RST, 1);
    mdelay(25); 
#elif defined(CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM)
#if 0
    mt_set_gpio_mode(MS_TS_MSG_IC_GPIO_RST, GPIO_CTP_RST_PIN_M_GPIO);
    mt_set_gpio_dir(MS_TS_MSG_IC_GPIO_RST, GPIO_DIR_OUT);
    mt_set_gpio_out(MS_TS_MSG_IC_GPIO_RST, GPIO_OUT_ONE);  
    udelay(100); 

    mt_set_gpio_mode(MS_TS_MSG_IC_GPIO_RST, GPIO_CTP_RST_PIN_M_GPIO);
    mt_set_gpio_dir(MS_TS_MSG_IC_GPIO_RST, GPIO_DIR_OUT);
    mt_set_gpio_out(MS_TS_MSG_IC_GPIO_RST, GPIO_OUT_ZERO);  
    udelay(100); 
#else
    tpd_gpio_output(CTP_RST,1);
    udelay(100); 
    tpd_gpio_output(CTP_RST,0);
    udelay(100); 
#endif

#ifdef TPD_CLOSE_POWER_IN_SLEEP
    hwPowerDown(TPD_POWER_SOURCE, "TP"); 
    mdelay(100);
    hwPowerOn(TPD_POWER_SOURCE, VOL_2800, "TP"); 
    mdelay(10);  // reset pulse
#endif //TPD_CLOSE_POWER_IN_SLEEP
#if 0
    mt_set_gpio_mode(MS_TS_MSG_IC_GPIO_RST, GPIO_CTP_RST_PIN_M_GPIO);
    mt_set_gpio_dir(MS_TS_MSG_IC_GPIO_RST, GPIO_DIR_OUT);
    mt_set_gpio_out(MS_TS_MSG_IC_GPIO_RST, GPIO_OUT_ONE);
#else
    tpd_gpio_output(CTP_RST,1);
#endif
    mdelay(25); 
#endif
}

void DrvPlatformLyrTouchDevicePowerOff(void)
{
    DBG("*** %s() ***\n", __func__);
    
    DrvIcFwLyrOptimizeCurrentConsumption();

#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM) || defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM)
//    gpio_direction_output(MS_TS_MSG_IC_GPIO_RST, 0);
    gpio_set_value(MS_TS_MSG_IC_GPIO_RST, 0);
#elif defined(CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM)
#if 0
    mt_set_gpio_mode(MS_TS_MSG_IC_GPIO_RST, GPIO_CTP_RST_PIN_M_GPIO);
    mt_set_gpio_dir(MS_TS_MSG_IC_GPIO_RST, GPIO_DIR_OUT);
    mt_set_gpio_out(MS_TS_MSG_IC_GPIO_RST, GPIO_OUT_ZERO);  
#else
    tpd_gpio_output(CTP_RST,0);
#endif
#ifdef TPD_CLOSE_POWER_IN_SLEEP
    hwPowerDown(TPD_POWER_SOURCE, "TP");
#endif //TPD_CLOSE_POWER_IN_SLEEP
#endif    
}

void DrvPlatformLyrTouchDeviceResetHw(void)
{
    DBG("*** %s() ***\n", __func__);
    
#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM) || defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM)
    gpio_direction_output(MS_TS_MSG_IC_GPIO_RST, 1);
//    gpio_set_value(MS_TS_MSG_IC_GPIO_RST, 1); 
    udelay(100); 
    gpio_set_value(MS_TS_MSG_IC_GPIO_RST, 0);
    udelay(100); 
    gpio_set_value(MS_TS_MSG_IC_GPIO_RST, 1);
    mdelay(25); 
#elif defined(CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM)
#if 0
    mt_set_gpio_mode(MS_TS_MSG_IC_GPIO_RST, GPIO_CTP_RST_PIN_M_GPIO);
    mt_set_gpio_dir(MS_TS_MSG_IC_GPIO_RST, GPIO_DIR_OUT);
    mt_set_gpio_out(MS_TS_MSG_IC_GPIO_RST, GPIO_OUT_ONE);
    udelay(100); 
    mt_set_gpio_mode(MS_TS_MSG_IC_GPIO_RST, GPIO_CTP_RST_PIN_M_GPIO);
    mt_set_gpio_dir(MS_TS_MSG_IC_GPIO_RST, GPIO_DIR_OUT);
    mt_set_gpio_out(MS_TS_MSG_IC_GPIO_RST, GPIO_OUT_ZERO);  
    udelay(100); 
    mt_set_gpio_mode(MS_TS_MSG_IC_GPIO_RST, GPIO_CTP_RST_PIN_M_GPIO);
    mt_set_gpio_dir(MS_TS_MSG_IC_GPIO_RST, GPIO_DIR_OUT);
    mt_set_gpio_out(MS_TS_MSG_IC_GPIO_RST, GPIO_OUT_ONE);
#else
    tpd_gpio_output(CTP_RST,1);
    udelay(100); 
    tpd_gpio_output(CTP_RST,0);
    udelay(100); 
    tpd_gpio_output(CTP_RST,1);
#endif
    mdelay(25); 
#endif
}

void DrvPlatformLyrDisableFingerTouchReport(void)
{
    unsigned long nIrqFlag;

    DBG("*** %s() ***\n", __func__);

    DBG("*** %s() _gInterruptFlag = %d ***\n", __func__, _gInterruptFlag);  // add for debug

    spin_lock_irqsave(&_gIrqLock, nIrqFlag);

#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM) || defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM)

#ifdef CONFIG_ENABLE_HOTKNOT
    if (g_HotKnotState != HOTKNOT_TRANS_STATE && g_HotKnotState != HOTKNOT_BEFORE_TRANS_STATE)
#endif //CONFIG_ENABLE_HOTKNOT
    {
        if (_gInterruptFlag == 1)  
        {
            disable_irq(_gIrq);

            _gInterruptFlag = 0;
        }
    }
#elif defined(CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM)

#ifdef CONFIG_ENABLE_HOTKNOT
    if (g_HotKnotState != HOTKNOT_TRANS_STATE && g_HotKnotState != HOTKNOT_BEFORE_TRANS_STATE)
#endif //CONFIG_ENABLE_HOTKNOT           
    {
        if (_gInterruptFlag == 1) 
        {
            //mt_eint_mask(CUST_EINT_TOUCH_PANEL_NUM);
	disable_irq(Msg2338A_touch_irq);

            _gInterruptFlag = 0;
        }
    }
#endif

    spin_unlock_irqrestore(&_gIrqLock, nIrqFlag);
#ifdef CONFIG_ENABLE_ESD_CHECK
	esd_check_disable();
#endif
}

void DrvPlatformLyrEnableFingerTouchReport(void)
{
    unsigned long nIrqFlag;

    DBG("*** %s() ***\n", __func__);

    DBG("*** %s() _gInterruptFlag = %d ***\n", __func__, _gInterruptFlag);  // add for debug

    spin_lock_irqsave(&_gIrqLock, nIrqFlag);

#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM) || defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM)

    if (_gInterruptFlag == 0) 
    {
        enable_irq(_gIrq);

        _gInterruptFlag = 1;        
    }

#elif defined(CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM)

    if (_gInterruptFlag == 0) 
    {
        //mt_eint_unmask(CUST_EINT_TOUCH_PANEL_NUM);
	enable_irq(Msg2338A_touch_irq);

        _gInterruptFlag = 1;        
    }

#endif

    spin_unlock_irqrestore(&_gIrqLock, nIrqFlag);
}

void DrvPlatformLyrFingerTouchPressed(s32 nX, s32 nY, s32 nPressure, s32 nId)
{
    DBG("*** %s() ***\n", __func__);
    DBG("point touch pressed\n");
    
#ifdef CONFIG_ENABLE_TYPE_B_PROTOCOL // TYPE B PROTOCOL
    input_mt_slot(g_InputDevice, nId);
    input_mt_report_slot_state(g_InputDevice, MT_TOOL_FINGER, true);
    input_report_abs(g_InputDevice, ABS_MT_TOUCH_MAJOR, 1);
    input_report_abs(g_InputDevice, ABS_MT_WIDTH_MAJOR, 1);
    input_report_abs(g_InputDevice, ABS_MT_POSITION_X, nX);
    input_report_abs(g_InputDevice, ABS_MT_POSITION_Y, nY);
#ifdef CONFIG_ENABLE_FORCE_TOUCH
    input_report_abs(g_InputDevice, ABS_MT_PRESSURE, nPressure);
#endif //CONFIG_ENABLE_FORCE_TOUCH

//    DBG("nId=%d, nX=%d, nY=%d\n", nId, nX, nY); // TODO : add for debug
#else // TYPE A PROTOCOL
    input_report_key(g_InputDevice, BTN_TOUCH, 1);
#if defined(CONFIG_ENABLE_TOUCH_DRIVER_FOR_MUTUAL_IC)
    input_report_abs(g_InputDevice, ABS_MT_TRACKING_ID, nId);
#endif //CONFIG_ENABLE_TOUCH_DRIVER_FOR_MUTUAL_IC
    
    input_report_abs(g_InputDevice, ABS_MT_TOUCH_MAJOR, 1);
    input_report_abs(g_InputDevice, ABS_MT_WIDTH_MAJOR, 1);
    input_report_abs(g_InputDevice, ABS_MT_POSITION_X, nX);
    input_report_abs(g_InputDevice, ABS_MT_POSITION_Y, nY);
#ifdef CONFIG_ENABLE_FORCE_TOUCH
    input_report_abs(g_InputDevice, ABS_MT_PRESSURE, nPressure);
#endif //CONFIG_ENABLE_FORCE_TOUCH

    input_mt_sync(g_InputDevice);
#endif //CONFIG_ENABLE_TYPE_B_PROTOCOL


#ifdef CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM
#ifdef CONFIG_TP_HAVE_KEY    
    if (FACTORY_BOOT == get_boot_mode() || RECOVERY_BOOT == get_boot_mode())
    {   
        tpd_button(nX, nY, 1);  
    }
#endif //CONFIG_TP_HAVE_KEY

    TPD_EM_PRINT(nX, nY, nX, nY, nId, 1);
#endif //CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM
}

void DrvPlatformLyrFingerTouchReleased(s32 nX, s32 nY, s32 nId)
{
    DBG("*** %s() ***\n", __func__);
    DBG("point touch released\n");

#ifdef CONFIG_ENABLE_TYPE_B_PROTOCOL // TYPE B PROTOCOL
    input_mt_slot(g_InputDevice, nId);
    input_mt_report_slot_state(g_InputDevice, MT_TOOL_FINGER, false);

//    DBG("nId=%d\n", nId); // TODO : add for debug
#else // TYPE A PROTOCOL
    input_report_key(g_InputDevice, BTN_TOUCH, 0);
    input_mt_sync(g_InputDevice);
#endif //CONFIG_ENABLE_TYPE_B_PROTOCOL


#ifdef CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM
#ifdef CONFIG_TP_HAVE_KEY 
    if (FACTORY_BOOT == get_boot_mode() || RECOVERY_BOOT == get_boot_mode())
    {   
       tpd_button(nX, nY, 0); 
    }            
#endif //CONFIG_TP_HAVE_KEY    

    TPD_EM_PRINT(nX, nY, nX, nY, 0, 0);
#endif //CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM
}

s32 DrvPlatformLyrInputDeviceInitialize(struct i2c_client *pClient)
{
    s32 nRetVal = 0;

    DBG("*** %s() ***\n", __func__);

    mutex_init(&g_Mutex);
    spin_lock_init(&_gIrqLock);

#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM) || defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM)
    /* allocate an input device */
    g_InputDevice = input_allocate_device();
    if (g_InputDevice == NULL)
    {
        DBG("*** Failed to allocate touch input device ***\n");
        return -ENOMEM;
    }

    g_InputDevice->name = pClient->name;
    g_InputDevice->phys = "I2C";
    g_InputDevice->dev.parent = &pClient->dev;
    g_InputDevice->id.bustype = BUS_I2C;
    
    /* set the supported event type for input device */
    set_bit(EV_ABS, g_InputDevice->evbit);
    set_bit(EV_SYN, g_InputDevice->evbit);
    set_bit(EV_KEY, g_InputDevice->evbit);
    set_bit(BTN_TOUCH, g_InputDevice->keybit);
    set_bit(INPUT_PROP_DIRECT, g_InputDevice->propbit);

#ifdef CONFIG_TP_HAVE_KEY
    // Method 1.
    { 
        u32 i;
        for (i = 0; i < MAX_KEY_NUM; i ++)
        {
            input_set_capability(g_InputDevice, EV_KEY, g_TpVirtualKey[i]);
        }
    }

#ifdef CONFIG_ENABLE_REPORT_KEY_WITH_COORDINATE
    _DrvPlatformLyrVirtualKeysInit(); // Initialize virtual keys for specific SPRC/QCOM platform.
#endif //CONFIG_ENABLE_REPORT_KEY_WITH_COORDINATE
#endif //CONFIG_TP_HAVE_KEY

/*  
#ifdef CONFIG_TP_HAVE_KEY
    // Method 2.
    set_bit(TOUCH_KEY_MENU, g_InputDevice->keybit); //Menu
    set_bit(TOUCH_KEY_HOME, g_InputDevice->keybit); //Home
    set_bit(TOUCH_KEY_BACK, g_InputDevice->keybit); //Back
    set_bit(TOUCH_KEY_SEARCH, g_InputDevice->keybit); //Search
#endif
*/

#ifdef CONFIG_ENABLE_GESTURE_WAKEUP
    input_set_capability(g_InputDevice, EV_KEY, KEY_POWER);
    input_set_capability(g_InputDevice, EV_KEY, KEY_UP);
    input_set_capability(g_InputDevice, EV_KEY, KEY_DOWN);
    input_set_capability(g_InputDevice, EV_KEY, KEY_LEFT);
    input_set_capability(g_InputDevice, EV_KEY, KEY_RIGHT);
    input_set_capability(g_InputDevice, EV_KEY, KEY_W);
    input_set_capability(g_InputDevice, EV_KEY, KEY_Z);
    input_set_capability(g_InputDevice, EV_KEY, KEY_V);
    input_set_capability(g_InputDevice, EV_KEY, KEY_O);
    input_set_capability(g_InputDevice, EV_KEY, KEY_M);
    input_set_capability(g_InputDevice, EV_KEY, KEY_C);
    input_set_capability(g_InputDevice, EV_KEY, KEY_E);
    input_set_capability(g_InputDevice, EV_KEY, KEY_S);
#endif //CONFIG_ENABLE_GESTURE_WAKEUP


#if defined(CONFIG_ENABLE_TOUCH_DRIVER_FOR_MUTUAL_IC)
    input_set_abs_params(g_InputDevice, ABS_MT_TRACKING_ID, 0, (MAX_TOUCH_NUM-1), 0, 0);
#endif //CONFIG_ENABLE_TOUCH_DRIVER_FOR_MUTUAL_IC
    input_set_abs_params(g_InputDevice, ABS_MT_TOUCH_MAJOR, 0, 255, 0, 0);
    input_set_abs_params(g_InputDevice, ABS_MT_WIDTH_MAJOR, 0, 15, 0, 0);
    input_set_abs_params(g_InputDevice, ABS_MT_POSITION_X, TOUCH_SCREEN_X_MIN, TOUCH_SCREEN_X_MAX, 0, 0);
    input_set_abs_params(g_InputDevice, ABS_MT_POSITION_Y, TOUCH_SCREEN_Y_MIN, TOUCH_SCREEN_Y_MAX, 0, 0);
#ifdef CONFIG_ENABLE_FORCE_TOUCH
    input_set_abs_params(g_InputDevice, ABS_MT_PRESSURE, 0, 255, 0, 0);
#endif //CONFIG_ENABLE_FORCE_TOUCH

#ifdef CONFIG_ENABLE_TYPE_B_PROTOCOL
    input_mt_init_slots(g_InputDevice, MAX_TOUCH_NUM, INPUT_MT_DIRECT | INPUT_MT_DROP_UNUSED);
#endif //CONFIG_ENABLE_TYPE_B_PROTOCOL

    /* register the input device to input sub-system */
    nRetVal = input_register_device(g_InputDevice);
    if (nRetVal < 0)
    {
        DBG("*** Unable to register touch input device *** nRetVal=%d\n", nRetVal);
        return nRetVal;
    }

#ifdef CONFIG_ENABLE_PROXIMITY_DETECTION
    nRetVal = _DrvPlatformLyrProximityInputDeviceInit(pClient);
#endif //CONFIG_ENABLE_PROXIMITY_DETECTION

#elif defined(CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM)
    g_InputDevice = tpd->dev;
/*
    g_InputDevice->phys = "I2C";
    g_InputDevice->dev.parent = &pClient->dev;
    g_InputDevice->id.bustype = BUS_I2C;
    
    // set the supported event type for input device 
    set_bit(EV_ABS, g_InputDevice->evbit);
    set_bit(EV_SYN, g_InputDevice->evbit);
    set_bit(EV_KEY, g_InputDevice->evbit);
    set_bit(BTN_TOUCH, g_InputDevice->keybit);
    set_bit(INPUT_PROP_DIRECT, g_InputDevice->propbit);
*/

#ifdef CONFIG_TP_HAVE_KEY
    {
        u32 i;
        for (i = 0; i < MAX_KEY_NUM; i ++)
        {
            input_set_capability(g_InputDevice, EV_KEY, g_TpVirtualKey[i]);
        }
    }
#endif

#ifdef CONFIG_ENABLE_GESTURE_WAKEUP
    input_set_capability(g_InputDevice, EV_KEY, KEY_POWER);
    input_set_capability(g_InputDevice, EV_KEY, KEY_U);
    input_set_capability(g_InputDevice, EV_KEY, KEY_UP);
    input_set_capability(g_InputDevice, EV_KEY, KEY_DOWN);
    input_set_capability(g_InputDevice, EV_KEY, KEY_LEFT);
    input_set_capability(g_InputDevice, EV_KEY, KEY_RIGHT);
    input_set_capability(g_InputDevice, EV_KEY, KEY_W);
    input_set_capability(g_InputDevice, EV_KEY, KEY_Z);
    input_set_capability(g_InputDevice, EV_KEY, KEY_V);
    input_set_capability(g_InputDevice, EV_KEY, KEY_O);
    input_set_capability(g_InputDevice, EV_KEY, KEY_M);
    input_set_capability(g_InputDevice, EV_KEY, KEY_C);
    input_set_capability(g_InputDevice, EV_KEY, KEY_E);
    input_set_capability(g_InputDevice, EV_KEY, KEY_S);
#endif //CONFIG_ENABLE_GESTURE_WAKEUP


#if defined(CONFIG_ENABLE_TOUCH_DRIVER_FOR_MUTUAL_IC)
    input_set_abs_params(g_InputDevice, ABS_MT_TRACKING_ID, 0, (MAX_TOUCH_NUM-1), 0, 0);
#endif //CONFIG_ENABLE_TOUCH_DRIVER_FOR_MUTUAL_IC
#ifdef CONFIG_ENABLE_FORCE_TOUCH
    input_set_abs_params(g_InputDevice, ABS_MT_PRESSURE, 0, 255, 0, 0);
#endif //CONFIG_ENABLE_FORCE_TOUCH

/*
    input_set_abs_params(g_InputDevice, ABS_MT_TOUCH_MAJOR, 0, 255, 0, 0);
    input_set_abs_params(g_InputDevice, ABS_MT_WIDTH_MAJOR, 0, 15, 0, 0);
    input_set_abs_params(g_InputDevice, ABS_MT_POSITION_X, TOUCH_SCREEN_X_MIN, TOUCH_SCREEN_X_MAX, 0, 0);
    input_set_abs_params(g_InputDevice, ABS_MT_POSITION_Y, TOUCH_SCREEN_Y_MIN, TOUCH_SCREEN_Y_MAX, 0, 0);
*/

#ifdef CONFIG_ENABLE_TYPE_B_PROTOCOL
    input_mt_init_slots(g_InputDevice, MAX_TOUCH_NUM, INPUT_MT_DIRECT | INPUT_MT_DROP_UNUSED);
#endif //CONFIG_ENABLE_TYPE_B_PROTOCOL

#endif

    return nRetVal;    
}

s32 DrvPlatformLyrTouchDeviceRequestGPIO(struct i2c_client *pClient)
{
    s32 nRetVal = 0;

    DBG("*** %s() ***\n", __func__);
    
#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM) || defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM)

#ifdef CONFIG_ENABLE_TOUCH_PIN_CONTROL
    _DrvPlatformLyrTouchPinCtrlInit(pClient);
#endif //CONFIG_ENABLE_TOUCH_PIN_CONTROL

    nRetVal = gpio_request(MS_TS_MSG_IC_GPIO_RST, "C_TP_RST");     
    if (nRetVal < 0)
    {
        DBG("*** Failed to request GPIO %d, error %d ***\n", MS_TS_MSG_IC_GPIO_RST, nRetVal);
    }

    nRetVal = gpio_request(MS_TS_MSG_IC_GPIO_INT, "C_TP_INT");    
    if (nRetVal < 0)
    {
        DBG("*** Failed to request GPIO %d, error %d ***\n", MS_TS_MSG_IC_GPIO_INT, nRetVal);
    }
#endif

    return nRetVal;    
}

static int tpd_irq_registration(void)
{
	struct device_node *node = NULL;
	int ret = 0;

	//node = of_find_compatible_node(NULL, NULL, "mediatek,cap_touch");
	node = of_find_matching_node(node, touch_of_match);
	if (node) {
		/*Msg2338A_touch_irq = gpio_to_irq(tpd_int_gpio_number);*/
		Msg2338A_touch_irq = irq_of_parse_and_map(node, 0);
		ret = request_irq(Msg2338A_touch_irq, (irq_handler_t)_DrvPlatformLyrFingerTouchInterruptHandler,
					IRQF_TRIGGER_RISING | IRQF_ONESHOT, TPD_DEVICE, NULL);
			if (ret > 0)
				TPD_DMESG("tpd request_irq IRQ LINE NOT AVAILABLE!.");
	} else {
		TPD_DMESG("[%s] tpd request_irq can not find touch eint device node!.", __func__);
	}
	TPD_DMESG("tpd request_irq %d.\n",Msg2338A_touch_irq);
	return 0;
}

s32 DrvPlatformLyrTouchDeviceRegisterFingerTouchInterruptHandler(void)
{
    s32 nRetVal = 0;

    DBG("*** %s() ***\n", __func__);

    if (DrvIcFwLyrIsRegisterFingerTouchInterruptHandler())
    {    	
#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM) || defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM)
        /* initialize the finger touch work queue */ 
        INIT_WORK(&_gFingerTouchWork, _DrvPlatformLyrFingerTouchDoWork);

        _gIrq = gpio_to_irq(MS_TS_MSG_IC_GPIO_INT);

        /* request an irq and register the isr */
        nRetVal = request_threaded_irq(_gIrq/*MS_TS_MSG_IC_GPIO_INT*/, NULL, _DrvPlatformLyrFingerTouchInterruptHandler,
                      IRQF_TRIGGER_RISING | IRQF_ONESHOT/* | IRQF_NO_SUSPEND *//* IRQF_TRIGGER_FALLING */,
                      "msg2xxx", NULL); 

//        nRetVal = request_irq(_gIrq/*MS_TS_MSG_IC_GPIO_INT*/, _DrvPlatformLyrFingerTouchInterruptHandler,
//                      IRQF_TRIGGER_RISING /* | IRQF_NO_SUSPEND *//* IRQF_TRIGGER_FALLING */,
//                      "msg2xxx", NULL); 

        _gInterruptFlag = 1;
        
        if (nRetVal != 0)
        {
            DBG("*** Unable to claim irq %d; error %d ***\n", MS_TS_MSG_IC_GPIO_INT, nRetVal);
        }
#elif defined(CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM)
#if 0
        mt_set_gpio_mode(MS_TS_MSG_IC_GPIO_INT, GPIO_CTP_EINT_PIN_M_EINT);
        mt_set_gpio_dir(MS_TS_MSG_IC_GPIO_INT, GPIO_DIR_IN);
        mt_set_gpio_pull_enable(MS_TS_MSG_IC_GPIO_INT, GPIO_PULL_ENABLE);
        mt_set_gpio_pull_select(MS_TS_MSG_IC_GPIO_INT, GPIO_PULL_UP);

        mt_eint_set_hw_debounce(CUST_EINT_TOUCH_PANEL_NUM, CUST_EINT_TOUCH_PANEL_DEBOUNCE_CN);
        mt_eint_registration(CUST_EINT_TOUCH_PANEL_NUM, CUST_EINT_TOUCH_PANEL_TYPE/* EINTF_TRIGGER_RISING */, _DrvPlatformLyrFingerTouchInterruptHandler, 1);

        mt_eint_unmask(CUST_EINT_TOUCH_PANEL_NUM);
#else
        tpd_gpio_as_int(CTP_INT);
		
	tpd_irq_registration();

	//enable_irq(Msg2338A_touch_irq);
	//disable_irq(Msg2338A_touch_irq);

#endif
        _gInterruptFlag = 1;

#ifdef CONFIG_USE_IRQ_INTERRUPT_FOR_MTK_PLATFORM
        /* initialize the finger touch work queue */ 
        INIT_WORK(&_gFingerTouchWork, _DrvPlatformLyrFingerTouchDoWork);
#else
        _gThread = kthread_run(_DrvPlatformLyrFingerTouchHandler, 0, TPD_DEVICE);
        if (IS_ERR(_gThread))
        { 
            nRetVal = PTR_ERR(_gThread);
            DBG("Failed to create kernel thread: %d\n", nRetVal);
        }
#endif //CONFIG_USE_IRQ_INTERRUPT_FOR_MTK_PLATFORM
#endif
    }
    
    return nRetVal;    
}	

void DrvPlatformLyrTouchDeviceRegisterEarlySuspend(void)
{
    DBG("*** %s() ***\n", __func__);

#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM) || defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM)
#ifdef CONFIG_ENABLE_NOTIFIER_FB
    _gFbNotifier.notifier_call = MsDrvInterfaceTouchDeviceFbNotifierCallback;
    fb_register_client(&_gFbNotifier);
#else
    _gEarlySuspend.level = EARLY_SUSPEND_LEVEL_BLANK_SCREEN;
    _gEarlySuspend.suspend = MsDrvInterfaceTouchDeviceSuspend;
    _gEarlySuspend.resume = MsDrvInterfaceTouchDeviceResume;
    register_early_suspend(&_gEarlySuspend);
#endif //CONFIG_ENABLE_NOTIFIER_FB   
#endif //CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM || CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM
}

/* remove function is triggered when the input device is removed from input sub-system */
s32 DrvPlatformLyrTouchDeviceRemove(struct i2c_client *pClient)
{
    DBG("*** %s() ***\n", __func__);

#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM) || defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM)
    free_irq(_gIrq, g_InputDevice);
    gpio_free(MS_TS_MSG_IC_GPIO_INT);
    gpio_free(MS_TS_MSG_IC_GPIO_RST);
    
    if (g_InputDevice)
    {
        input_unregister_device(g_InputDevice);
        g_InputDevice = NULL;
    }

#ifdef CONFIG_ENABLE_TOUCH_PIN_CONTROL
    _DrvPlatformLyrTouchPinCtrlUnInit();
#endif //CONFIG_ENABLE_TOUCH_PIN_CONTROL

#ifdef CONFIG_TP_HAVE_KEY
#ifdef CONFIG_ENABLE_REPORT_KEY_WITH_COORDINATE
    _DrvPlatformLyrVirtualKeysUnInit();
#endif //CONFIG_ENABLE_REPORT_KEY_WITH_COORDINATE
#endif //CONFIG_TP_HAVE_KEY

#ifdef CONFIG_ENABLE_PROXIMITY_DETECTION
    _DrvPlatformLyrProximityInputDeviceUnInit();
#endif //CONFIG_ENABLE_PROXIMITY_DETECTION   
#endif //CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM || CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM   

    if (IS_FIRMWARE_DATA_LOG_ENABLED)
    {    	
        if (g_TouchKSet)
        {
            kset_unregister(g_TouchKSet);
            g_TouchKSet = NULL;
        }
    
        if (g_TouchKObj)
        {
            kobject_put(g_TouchKObj);
            g_TouchKObj = NULL;
        }
    } //IS_FIRMWARE_DATA_LOG_ENABLED

#ifdef CONFIG_ENABLE_GESTURE_WAKEUP
#ifdef CONFIG_ENABLE_GESTURE_DEBUG_MODE
    if (g_GestureKSet)
    {
        kset_unregister(g_GestureKSet);
        g_GestureKSet = NULL;
    }
    
    if (g_GestureKObj)
    {
        kobject_put(g_GestureKObj);
        g_GestureKObj = NULL;
    }
#endif //CONFIG_ENABLE_GESTURE_DEBUG_MODE
#endif //CONFIG_ENABLE_GESTURE_WAKEUP

    DrvMainRemoveProcfsDirEntry();

#ifdef CONFIG_ENABLE_HOTKNOT
    DeleteQueue();
    DeleteHotKnotMem();
    DBG("Deregister hotknot misc device.\n");
    misc_deregister(&hotknot_miscdevice);   
#endif //CONFIG_ENABLE_HOTKNOT

#ifdef CONFIG_ENABLE_JNI_INTERFACE
    DeleteMsgToolMem();
#endif //CONFIG_ENABLE_JNI_INTERFACE

#ifdef CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM
#ifdef CONFIG_ENABLE_DMA_IIC
    DmaFree();
#endif //CONFIG_ENABLE_DMA_IIC
#endif //CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM

    return 0;
}

void DrvPlatformLyrSetIicDataRate(struct i2c_client *pClient, u32 nIicDataRate)
{
    DBG("*** %s() nIicDataRate = %d ***\n", __func__, nIicDataRate);

#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM)
    // TODO : Please FAE colleague to confirm with customer device driver engineer for how to set i2c data rate on SPRD platform
    sprd_i2c_ctl_chg_clk(pClient->adapter->nr, nIicDataRate); 
    mdelay(100);
#elif defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM)
    // TODO : Please FAE colleague to confirm with customer device driver engineer for how to set i2c data rate on QCOM platform
#elif defined(CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM)
    pClient->timing = nIicDataRate/1000;
#endif
}

//------------------------------------------------------------------------------//

#ifdef CONFIG_ENABLE_PROXIMITY_DETECTION

int DrvPlatformLyrGetTpPsData(void)
{
    DBG("*** %s() g_FaceClosingTp = %d ***\n", __func__, g_FaceClosingTp);
	
    return g_FaceClosingTp;
}

#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM)
void DrvPlatformLyrTpPsEnable(int nEnable)
{
    DBG("*** %s() nEnable = %d ***\n", __func__, nEnable);

    if (nEnable)
    {
        DrvIcFwLyrEnableProximity();
    }
    else
    {
        DrvIcFwLyrDisableProximity();
    }
}
#elif defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM)
int DrvPlatformLyrTpPsEnable(struct sensors_classdev* pProximityCdev, unsigned int nEnable)
{
    DBG("*** %s() nEnable = %d ***\n", __func__, nEnable);

    if (nEnable)
    {
        DrvIcFwLyrEnableProximity();
    }
    else
    {
        DrvIcFwLyrDisableProximity();
    }
    
    return 0;
}
#elif defined(CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM)
int DrvPlatformLyrTpPsOperate(void* pSelf, u32 nCommand, void* pBuffIn, int nSizeIn,
				   void* pBuffOut, int nSizeOut, int* pActualOut)
{
    int nErr = 0;
    int nValue;
    hwm_sensor_data *pSensorData;

    switch (nCommand)
    {
        case SENSOR_DELAY:
            if ((pBuffIn == NULL) || (nSizeIn < sizeof(int)))
            {
                nErr = -EINVAL;
            }
            // Do nothing
            break;

        case SENSOR_ENABLE:
            if ((pBuffIn == NULL) || (nSizeIn < sizeof(int)))
            {
                nErr = -EINVAL;
            }
            else
            {
                nValue = *(int *)pBuffIn;
                if (nValue)
                {
                    if (DrvIcFwLyrEnableProximity() < 0)
                    {
                        DBG("Enable ps fail: %d\n", nErr);
                        return -1;
                    }
                }
                else
                {
                    if (DrvIcFwLyrDisableProximity() < 0)
                    {
                        DBG("Disable ps fail: %d\n", nErr);
                        return -1;
                    }
                }
            }
            break;

        case SENSOR_GET_DATA:
            if ((pBuffOut == NULL) || (nSizeOut < sizeof(hwm_sensor_data)))
            {
                DBG("Get sensor data parameter error!\n");
                nErr = -EINVAL;
            }
            else
            {
                pSensorData = (hwm_sensor_data *)pBuffOut;

                pSensorData->values[0] = DrvPlatformLyrGetTpPsData();
                pSensorData->value_divide = 1;
                pSensorData->status = SENSOR_STATUS_ACCURACY_MEDIUM;
            }
            break;

       default:
           DBG("Un-recognized parameter %d!\n", nCommand);
           nErr = -1;
           break;
    }

    return nErr;
}
#endif

#endif //CONFIG_ENABLE_PROXIMITY_DETECTION

//------------------------------------------------------------------------------//

