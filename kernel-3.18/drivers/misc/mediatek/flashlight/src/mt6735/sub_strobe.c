
#include <linux/kernel.h>
#include <linux/module.h>
#include <linux/init.h>
#include <linux/types.h>
#include <linux/wait.h>
#include <linux/slab.h>
#include <linux/fs.h>
#include <linux/sched.h>
#include <linux/poll.h>
#include <linux/device.h>
#include <linux/interrupt.h>
#include <linux/delay.h>
#include <linux/platform_device.h>
#include <linux/cdev.h>
#include <linux/errno.h>
#include <linux/time.h>
#include <asm/io.h>
#include <asm/uaccess.h>
#include "kd_camera_typedef.h"
#include <linux/hrtimer.h>
#include <linux/ktime.h>
#include <linux/version.h>
#include <linux/gpio.h>
#include <linux/of_gpio.h>

#ifdef CONFIG_COMPAT
#include <linux/fs.h>
#include <linux/compat.h>
#endif
#include "kd_flashlight.h"
/******************************************************************************
 * Debug configuration
******************************************************************************/
/* availible parameter */
/* ANDROID_LOG_ASSERT */
/* ANDROID_LOG_ERROR */
/* ANDROID_LOG_WARNING */
/* ANDROID_LOG_INFO */
/* ANDROID_LOG_DEBUG */
/* ANDROID_LOG_VERBOSE */ 
#define TAG_NAME "[sub_strobe.c]"
#define PK_DBG_NONE(fmt, arg...)    do {} while (0)
#define PK_DBG_FUNC(fmt, arg...)    pr_debug(TAG_NAME "%s: " fmt, __func__ , ##arg)
#define PK_WARN(fmt, arg...)        pr_warn(TAG_NAME "%s: " fmt, __func__ , ##arg)
#define PK_NOTICE(fmt, arg...)      pr_notice(TAG_NAME "%s: " fmt, __func__ , ##arg)
#define PK_INFO(fmt, arg...)        pr_info(TAG_NAME "%s: " fmt, __func__ , ##arg)
#define PK_TRC_FUNC(f)              pr_debug(TAG_NAME "<%s>\n", __func__)
#define PK_TRC_VERBOSE(fmt, arg...) pr_debug(TAG_NAME fmt, ##arg)
#define PK_ERROR(fmt, arg...)       pr_err(TAG_NAME "%s: " fmt, __func__ , ##arg)

#define DEBUG_LEDS_STROBE
#ifdef DEBUG_LEDS_STROBE
#define PK_DBG PK_DBG_FUNC
#define PK_VER PK_TRC_VERBOSE
#define PK_ERR PK_ERROR
#else
#define PK_DBG(a, ...)
#define PK_VER(a, ...)
#define PK_ERR(a, ...)
#endif 



/******************************************************************************
 * local variables
******************************************************************************/
static DEFINE_SPINLOCK(g_substrobeSMPLock); /* cotta-- SMP proection */

static u32 substrobe_Res = 0;
static u32 substrobe_Timeus = 0;
static int g_substrobe_On = 0;

static int g_subduty=-1;
static int g_substep=-1;
static int g_subtimeOutTimeMs=0;

#if (LINUX_VERSION_CODE >= KERNEL_VERSION(2,6,37))
static DEFINE_MUTEX(g_substrobeSem);
#else
static DECLARE_MUTEX(g_substrobeSem);
#endif

static struct work_struct subworkTimeOut;

static void subwork_timeOutFunc(struct work_struct *data);



#if defined(CONFIG_MTK_SUB_TRUE_FLASHLIGHT)
  #define FLASH_PINCTRL  1  //flash mode pinctrl
#else
  #define FLASH_PINCTRL  0  //flash mode pmic
#endif

#if FLASH_PINCTRL
    int sub_flash_en_gpio;
    struct device_node	*sub_flash_of_node;   
#else 
enum mt65xx_led_pmic {
	MT65XX_LED_PMIC_LCD_ISINK = 0,
	MT65XX_LED_PMIC_NLED_ISINK0,
	MT65XX_LED_PMIC_NLED_ISINK1,
	MT65XX_LED_PMIC_NLED_ISINK2,
	MT65XX_LED_PMIC_NLED_ISINK3
};
extern int runyee_isink_set_pmic(enum mt65xx_led_pmic pmic_type, int level);
#endif


int SUBFL_Enable(void)
{

#if FLASH_PINCTRL
	gpio_set_value(sub_flash_en_gpio,1);
	mdelay(10);	
#else
	runyee_isink_set_pmic(MT65XX_LED_PMIC_NLED_ISINK0,1); 
	runyee_isink_set_pmic(MT65XX_LED_PMIC_NLED_ISINK1,1);
	mdelay(10);	
#endif
	
	printk(" FL_Enable line=%d\n",__LINE__);
	
	return 0;
}


int SUBFL_Disable(void) 
{
#if FLASH_PINCTRL
	gpio_set_value(sub_flash_en_gpio,0);
	mdelay(10);	
#else
	runyee_isink_set_pmic(MT65XX_LED_PMIC_NLED_ISINK0,0); 
	runyee_isink_set_pmic(MT65XX_LED_PMIC_NLED_ISINK1,0);
	mdelay(10);	
#endif
	
	printk(" FL_Enable line=%d\n",__LINE__);
	
	return 0;
}


int SUBFL_dim_duty(u32 duty)
{
		//PK_DBG(" FL_dim_duty line=%d\n",__LINE__);
		g_subduty = duty;
		
		return 0;
}

int SUBFL_step(u32 step)
{

    return 0;
}

int SUBFL_init(void)
{
		SUBFL_Disable();
    INIT_WORK(&subworkTimeOut, subwork_timeOutFunc);
   // PK_DBG(" FL_Init line=%d\n",__LINE__);
    
    return 0;
}


int SUBFL_Uninit(void)
{
		SUBFL_Disable();
		
    return 0;
}

/*****************************************************************************
User interface
*****************************************************************************/

static void subwork_timeOutFunc(struct work_struct *data)
{
    SUBFL_Disable();
    //PK_DBG("ledTimeOut_callback\n");
    //printk(KERN_ALERT "work handler function./n");
}



enum hrtimer_restart subledTimeOutCallback(struct hrtimer *timer)
{
    schedule_work(&subworkTimeOut);
    return HRTIMER_NORESTART;
}
static struct hrtimer g_subtimeOutTimer;
void subtimerInit(void)
{
	g_subtimeOutTimeMs=1000; //1s
	hrtimer_init( &g_subtimeOutTimer, CLOCK_MONOTONIC, HRTIMER_MODE_REL );
	g_subtimeOutTimer.function=subledTimeOutCallback;

}

static int sub_strobe_ioctl(unsigned int cmd, unsigned long arg)
{
		int i4RetValue = 0;
		int iFlashType = (int)FLASHLIGHT_NONE;
		int ior_shift;
		int iow_shift;
		int iowr_shift;
		ior_shift = cmd - (_IOR(FLASHLIGHT_MAGIC,0, int));
		iow_shift = cmd - (_IOW(FLASHLIGHT_MAGIC,0, int));
		iowr_shift = cmd - (_IOWR(FLASHLIGHT_MAGIC,0, int));
		//PK_DBG("constant_flashlight_ioctl() line=%d ior_shift=%d, iow_shift=%d iowr_shift=%d arg=%d\n",__LINE__, ior_shift, iow_shift, iowr_shift, arg);

    switch(cmd)
    {

		case FLASH_IOC_SET_TIME_OUT_TIME_MS:
		//	PK_DBG("FLASH_IOC_SET_TIME_OUT_TIME_MS: %d\n",(int)arg);
			g_subtimeOutTimeMs=arg;
		break;


    	case FLASH_IOC_SET_DUTY :
    	//	PK_DBG("FLASHLIGHT_DUTY: %d\n",(int)arg);
    		g_subduty=arg;
    		SUBFL_dim_duty(arg);
    		break;


    	case FLASH_IOC_SET_STEP:
    	//	PK_DBG("FLASH_IOC_SET_STEP: %d\n",(int)arg);
    		g_substep=arg;
    		SUBFL_step(arg);
    		break;

    	case FLASH_IOC_SET_ONOFF :
    		printk("sub_strobe_ioctl %d\n",(int)arg);
    		if(arg==1)
    		{

    		 /*   int s;
    		    int ms;
    		    if(g_subtimeOutTimeMs>1000)
            	{
            		s = g_subtimeOutTimeMs/1000;
            		ms = g_subtimeOutTimeMs - s*1000;
            	}
            	else
            	{
            		s = 0;
            		ms = g_subtimeOutTimeMs;
            	}
				*/
				if(g_subtimeOutTimeMs!=0)
	            {
	            	ktime_t ktime;
								ktime = ktime_set( 0, g_subtimeOutTimeMs*1000000 );
								hrtimer_start( &g_subtimeOutTimer, ktime, HRTIMER_MODE_REL );
	            }
    				SUBFL_Enable();
    				g_substrobe_On=1;
    		}
    		else
    		{
    			SUBFL_Disable();
				hrtimer_cancel( &g_subtimeOutTimer );
				g_substrobe_On=0;
    		}
    		break;
		case FLASHLIGHTIOC_G_FLASHTYPE:
            iFlashType = FLASHLIGHT_LED_CONSTANT;
            if(copy_to_user((void __user *) arg , (void*)&iFlashType , _IOC_SIZE(cmd)))
            {
               // PK_DBG("[strobe_ioctl] ioctl copy to user failed\n");
                return -EFAULT;
            }
            break;
		default :
    		//PK_DBG(" No such command \n");
    		i4RetValue = -EPERM;
    		break;
    }
    return i4RetValue;


}

static int sub_strobe_open(void *pArg)
{
	  int i4RetValue = 0;
    //PK_DBG("constant_flashlight_open line=%d\n", __LINE__);

	  if (0 == substrobe_Res)
	  {
	    SUBFL_init();
			subtimerInit();
		}
		
		//PK_DBG("constant_flashlight_open line=%d\n", __LINE__);
		spin_lock_irq(&g_substrobeSMPLock);


    if(substrobe_Res)
    {
        //PK_ERR(" busy!\n");
        i4RetValue = -EBUSY;
    }
    else
    {
        substrobe_Res += 1;
    }


    spin_unlock_irq(&g_substrobeSMPLock);
    //PK_DBG("subconstant_flashlight_open line=%d\n", __LINE__);

    return i4RetValue;


    //PK_DBG("sub dummy open");
    return 0;

}

static int sub_strobe_release(void *pArg)
{
    //PK_DBG(" constant_flashlight_release\n");

    if (substrobe_Res)
    {
        spin_lock_irq(&g_substrobeSMPLock);

        substrobe_Res = 0;
        substrobe_Timeus = 0;

        /* LED On Status */
        g_substrobe_On = 0;

        spin_unlock_irq(&g_substrobeSMPLock);

    	  SUBFL_Uninit();
    }

    //PK_DBG(" Done\n");

    return 0;

}

FLASHLIGHT_FUNCTION_STRUCT	subStrobeFunc=
{
		sub_strobe_open,
		sub_strobe_release,
		sub_strobe_ioctl
};


MUINT32 subStrobeInit(PFLASHLIGHT_FUNCTION_STRUCT *pfFunc)
{
    if (pfFunc != NULL)
    {
        *pfFunc = &subStrobeFunc;
    }
    return 0;
}

#if 1//add by zhou for test start
struct class *sub_flashlight_class;
struct device *sub_flashlight_dev;
static ssize_t sub_flashlight_enable_store(struct device *dev,struct device_attribute *attr, const char *buf, size_t size)
{
    int enable = 0;
    if(buf != NULL && size != 0)
    {
        enable = (int)simple_strtoul(buf, NULL, 0);
    }
    if (enable)
    {
        SUBFL_init();
        mdelay(10);
        SUBFL_Enable();
    }
    else
    {
        SUBFL_Disable();
    }
    return size;
}
static DEVICE_ATTR(sub_flashlight_enable, 0644, NULL, sub_flashlight_enable_store);
static int __init sub_flashlight_init(void)  
{		
		sub_flashlight_class = class_create(THIS_MODULE, "sub_flashlight");
		sub_flashlight_dev = device_create(sub_flashlight_class,NULL, 0, NULL,  "sub_flashlight");
		device_create_file(sub_flashlight_dev, &dev_attr_sub_flashlight_enable);
		
#if FLASH_PINCTRL
  sub_flash_of_node=of_find_compatible_node(NULL, NULL, "mediatek,flashlight");
  sub_flash_en_gpio=of_get_named_gpio(sub_flash_of_node, "sub_flash_en_gpio", 0);
 
  gpio_request(sub_flash_en_gpio, "sub_flash_en"); 
	gpio_direction_output(sub_flash_en_gpio,0);	
#endif		
		return 0;
}
static void __exit sub_flashlight_exit(void)
{
		return;
}
module_init(sub_flashlight_init);
module_exit(sub_flashlight_exit);
MODULE_LICENSE("GPL");
MODULE_DESCRIPTION("sub_flashlight");
MODULE_AUTHOR("jst <aren.jiang@runyee.com.cn>");
#endif//add by zhou for test end 
