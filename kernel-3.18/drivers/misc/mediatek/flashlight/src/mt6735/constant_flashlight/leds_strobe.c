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
#include "kd_flashlight.h"
#include <asm/io.h>
#include <asm/uaccess.h>
#include "kd_camera_typedef.h"
#include <linux/hrtimer.h>
#include <linux/ktime.h>
#include <linux/version.h>
#include <linux/mutex.h>
#include <linux/i2c.h>
#include <linux/leds.h>
#include <linux/gpio.h>
#include <linux/of_gpio.h>

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

#define TAG_NAME "[leds_strobe.c]"
#define PK_DBG_NONE(fmt, arg...)    do {} while (0)
#define PK_DBG_FUNC(fmt, arg...)    pr_debug(TAG_NAME "%s: " fmt, __func__ , ##arg)

/*#define DEBUG_LEDS_STROBE*/
#ifdef DEBUG_LEDS_STROBE
#define PK_DBG PK_DBG_FUNC
#else
#define PK_DBG(a, ...)
#endif

/******************************************************************************
 * local variables
******************************************************************************/

static DEFINE_SPINLOCK(g_strobeSMPLock);	/* cotta-- SMP proection */


static u32 strobe_Res;
static u32 strobe_Timeus;
static BOOL g_strobe_On;

static int g_duty = -1;
static int g_timeOutTimeMs;

static DEFINE_MUTEX(g_strobeSem);


#define STROBE_DEVICE_ID 0xC6

static struct work_struct workTimeOut;

#if defined(CONFIG_MTK_MAIN_TRUE_FLASHLIGHT)
  #define FLASH_PINCTRL  1  //flash mode pinctrl
#else
  #define FLASH_PINCTRL  0  //flash mode pmic
#endif

#if FLASH_PINCTRL
    int flash_en_gpio;
    int flash_mo_gpio; 
    struct device_node	*flash_of_node;       
#else 
enum mt65xx_led_pmic {
	MT65XX_LED_PMIC_LCD_ISINK = 0,
	MT65XX_LED_PMIC_NLED_ISINK0,
	MT65XX_LED_PMIC_NLED_ISINK1,
	MT65XX_LED_PMIC_NLED_ISINK2,  //mt6737 isink2/3 none 
	MT65XX_LED_PMIC_NLED_ISINK3
};
extern int runyee_isink_set_pmic(enum mt65xx_led_pmic pmic_type, int level);
#endif

//static int g_bLtVersion;

/*****************************************************************************
Functions
*****************************************************************************/
static void work_timeOutFunc(struct work_struct *data);

#define LEDS_TORCH_MODE 		0
#define LEDS_FLASH_MODE 		1

int FL_Enable(void)
{

#if FLASH_PINCTRL
	gpio_set_value(flash_en_gpio,1);
	mdelay(10);	
#else
	runyee_isink_set_pmic(MT65XX_LED_PMIC_NLED_ISINK0,1); 
	runyee_isink_set_pmic(MT65XX_LED_PMIC_NLED_ISINK1,1);
	mdelay(10);	
#endif
	
	PK_DBG(" FL_Enable line=%d\n",__LINE__);
	
	return 0;
}



int FL_Disable(void)
{
#if FLASH_PINCTRL
  gpio_set_value(flash_en_gpio,0); 
	mdelay(10);
#else
	runyee_isink_set_pmic(MT65XX_LED_PMIC_NLED_ISINK0,0);
	runyee_isink_set_pmic(MT65XX_LED_PMIC_NLED_ISINK1,0);
	mdelay(10);	
#endif
	
	PK_DBG(" FL_Disable line=%d\n",__LINE__);
	
	return 0;
}

int FL_dim_duty(kal_uint32 duty)
{
  
#if FLASH_PINCTRL
/*************************************************************/
/***time 220ms val: 1->100%, 2->90%, 3->80%, 4->70%***********/
/***	              5->60%,  6->50%, 7->40%, 8->30%***********/
/***time 1.3s val:  9->100%, 10->90%, 11->80%, 12->70%********/
/***	              13->60%, 14->50%, 15->40%, 16->30%********/
/*************************************************************/
	                  
   int count=15;  //PWM count 1~8 220ms led flash mode ,9~16 1.3s led flash mode  
   int i=0;  	
	 g_duty = duty;
#if   CONFIG_RUNYEE_MAIN_TRUE_FLASHLIGHT_CURRENT==1
      count=1; 
#elif CONFIG_RUNYEE_MAIN_TRUE_FLASHLIGHT_CURRENT==2
      count=2; 
#elif CONFIG_RUNYEE_MAIN_TRUE_FLASHLIGHT_CURRENT==3
      count=3; 
#elif CONFIG_RUNYEE_MAIN_TRUE_FLASHLIGHT_CURRENT==4
      count=4; 
#elif CONFIG_RUNYEE_MAIN_TRUE_FLASHLIGHT_CURRENT==5
      count=5; 
#elif CONFIG_RUNYEE_MAIN_TRUE_FLASHLIGHT_CURRENT==6
      count=6; 
#elif CONFIG_RUNYEE_MAIN_TRUE_FLASHLIGHT_CURRENT==7
      count=7; 
#elif CONFIG_RUNYEE_MAIN_TRUE_FLASHLIGHT_CURRENT==8
      count=8; 
#elif CONFIG_RUNYEE_MAIN_TRUE_FLASHLIGHT_CURRENT==9
      count=9; 
#elif CONFIG_RUNYEE_MAIN_TRUE_FLASHLIGHT_CURRENT==10
      count=10; 
#elif CONFIG_RUNYEE_MAIN_TRUE_FLASHLIGHT_CURRENT==11
      count=11; 
#elif CONFIG_RUNYEE_MAIN_TRUE_FLASHLIGHT_CURRENT==12
      count=12; 
#elif CONFIG_RUNYEE_MAIN_TRUE_FLASHLIGHT_CURRENT==13
      count=13; 
#elif CONFIG_RUNYEE_MAIN_TRUE_FLASHLIGHT_CURRENT==14
      count=14; 
#elif CONFIG_RUNYEE_MAIN_TRUE_FLASHLIGHT_CURRENT==15
      count=15; 
#elif CONFIG_RUNYEE_MAIN_TRUE_FLASHLIGHT_CURRENT==16
      count=16;
#else  //default value
      count=15; 
#endif         
	if(duty <=1){
		  gpio_set_value(flash_mo_gpio,0); 
	    gpio_set_value(flash_en_gpio,1);

			printk(" duty===0 LEDS_TORCH_MODE\n");			
	}else{
			gpio_set_value(flash_mo_gpio,1);	
      for(i=0;i<count;i++)  
      {	    
	        gpio_set_value(flash_en_gpio,0);
	        udelay(2); //delay 0.75us-10us
	        
          gpio_set_value(flash_en_gpio,1);
	        udelay(2); //delay 0.75us-10us 
	    }
		  //mdelay(2000);  
     	printk(" duty===1 LEDS_FLASH_MODE\n");
  }	

#else
	runyee_isink_set_pmic(MT65XX_LED_PMIC_NLED_ISINK0,1);
	runyee_isink_set_pmic(MT65XX_LED_PMIC_NLED_ISINK1,1);
	mdelay(10);	
	
#endif

  printk(" FL_dim_duty line=%d  g_duty=%d\n",__LINE__, g_duty);
  
  return 0;
}




int FL_Init(void)
{
#if FLASH_PINCTRL
	gpio_set_value(flash_mo_gpio,0);
	gpio_set_value(flash_en_gpio,0);
#else
	runyee_isink_set_pmic(MT65XX_LED_PMIC_NLED_ISINK0,0);
	runyee_isink_set_pmic(MT65XX_LED_PMIC_NLED_ISINK1,0);
#endif

	INIT_WORK(&workTimeOut, work_timeOutFunc);
	
    PK_DBG(" FL_Init line=%d\n",__LINE__);
  
  return 0;
}


int FL_Uninit(void)
{
	FL_Disable();
	return 0;
}

/*****************************************************************************
User interface
*****************************************************************************/

static void work_timeOutFunc(struct work_struct *data)
{
	FL_Disable();
	
	PK_DBG("work_timeOutFunc......\n");
}



enum hrtimer_restart ledTimeOutCallback(struct hrtimer *timer)
{
	schedule_work(&workTimeOut);

	PK_DBG("ledTimeOutCallback......\n");
	
	return HRTIMER_NORESTART;
}

static struct hrtimer g_timeOutTimer;
void timerInit(void)
{
	g_timeOutTimeMs=1000; //1s
	hrtimer_init( &g_timeOutTimer, CLOCK_MONOTONIC, HRTIMER_MODE_REL );
	g_timeOutTimer.function=ledTimeOutCallback;

	PK_DBG("timerInit......\n");
}



static int constant_flashlight_ioctl(unsigned int cmd, unsigned long arg)
{
	int i4RetValue = 0;
	int ior_shift;
	int iow_shift;
	int iowr_shift;
	ior_shift = cmd - (_IOR(FLASHLIGHT_MAGIC,0, int));
	iow_shift = cmd - (_IOW(FLASHLIGHT_MAGIC,0, int));
	iowr_shift = cmd - (_IOWR(FLASHLIGHT_MAGIC,0, int));
	
	PK_DBG("constant_flashlight_ioctl() line=%d ior_shift=%d, iow_shift=%d iowr_shift=%d arg=%d\n",__LINE__, ior_shift, iow_shift, iowr_shift,(int)arg);
    switch(cmd)
    {

		case FLASH_IOC_SET_TIME_OUT_TIME_MS:
			PK_DBG("FLASH_IOC_SET_TIME_OUT_TIME_MS: %d\n",(int)arg);
			g_timeOutTimeMs=arg;
		break;


    	case FLASH_IOC_SET_DUTY :
    		PK_DBG("FLASHLIGHT_DUTY: %d\n",(int)arg);
    		FL_dim_duty(arg);
    		break;


    	case FLASH_IOC_SET_STEP:
    		PK_DBG("FLASH_IOC_SET_STEP: %d\n",(int)arg);

    		break;

    	case FLASH_IOC_SET_ONOFF :
    		PK_DBG("FLASHLIGHT_ONOFF: %d\n",(int)arg);
    		if(arg==1)
    		{

    		    int s;
    		    int ms;
    		    
    		    if(g_timeOutTimeMs>1000)
          	{
          		s = g_timeOutTimeMs/1000;
          		ms = g_timeOutTimeMs - s*1000;
          	}
          	else
          	{
          		s = 0;
          		ms = g_timeOutTimeMs;
          	}

						PK_DBG("constant_flashlight_ioctl() line=%d, g_timeOutTimeMs=%d \n",__LINE__, g_timeOutTimeMs);
						if(g_timeOutTimeMs!=0)
            {
							ktime_t ktime;
							ktime = ktime_set( s, ms*1000000 );
							hrtimer_start( &g_timeOutTimer, ktime, HRTIMER_MODE_REL );
            }
            
  					FL_Enable();
    		}
    		else
    		{
					FL_Disable();
					hrtimer_cancel( &g_timeOutTimer );
    		}
    		break;
		default :
    		PK_DBG("constant_flashlight_ioctl() line=%d, No such command...... \n",__LINE__);
    		i4RetValue = -EPERM;
    		break;
    }
    return i4RetValue;
}




static int constant_flashlight_open(void *pArg)
{
	int i4RetValue = 0;
	PK_DBG("constant_flashlight_open line=%d\n", __LINE__);
	
	if (0 == strobe_Res)
	{
		FL_Init();
		timerInit();
	}
	
	PK_DBG("constant_flashlight_open line=%d, strobe_Res=%d\n", __LINE__, strobe_Res);
	spin_lock_irq(&g_strobeSMPLock);


  if(strobe_Res)
  {
      i4RetValue = -EBUSY;
  }
  else
  {
      strobe_Res += 1;
  }


  spin_unlock_irq(&g_strobeSMPLock);
  PK_DBG("constant_flashlight_open line=%d\n", __LINE__);

  return i4RetValue;

}


static int constant_flashlight_release(void *pArg)
{
    PK_DBG(" constant_flashlight_release begin......\n");

    if (strobe_Res)
    {
        spin_lock_irq(&g_strobeSMPLock);

        strobe_Res = 0;
        strobe_Timeus = 0;

        /* LED On Status */
        g_strobe_On = FALSE;

        spin_unlock_irq(&g_strobeSMPLock);

    	FL_Uninit();
    }
    PK_DBG(" constant_flashlight_release end......\n");
    
	 //FL_Disable();
#if FLASH_PINCTRL
 	   	gpio_set_value(flash_mo_gpio,0);
	   	gpio_set_value(flash_en_gpio,0); 	   	     	 
#else
	runyee_isink_set_pmic(MT65XX_LED_PMIC_NLED_ISINK0,0);
	runyee_isink_set_pmic(MT65XX_LED_PMIC_NLED_ISINK1,0);
#endif	
  	    
    return 0;
}


FLASHLIGHT_FUNCTION_STRUCT	constantFlashlightFunc=
{
	constant_flashlight_open,
	constant_flashlight_release,
	constant_flashlight_ioctl
};


MUINT32 constantFlashlightInit(PFLASHLIGHT_FUNCTION_STRUCT *pfFunc)
{
    if (pfFunc != NULL)
    {
        *pfFunc = &constantFlashlightFunc;
    }
    return 0;
}



/* LED flash control for high current capture mode*/
ssize_t strobe_VDIrq(void)
{

    return 0;
}

EXPORT_SYMBOL(strobe_VDIrq);


#if 1//add by zhou for test start
struct class *main_flashlight_class;
struct device *main_flashlight_dev;
static ssize_t main_flashlight_enable_store(struct device *dev,struct device_attribute *attr, const char *buf, size_t size)
{
    int enable = 0;
    if(buf != NULL && size != 0)
    {
        enable = (int)simple_strtoul(buf, NULL, 0);
    }
    if (enable)
    {
        FL_Init();
        mdelay(10);
        FL_Enable();
    }
    else
    {
        FL_Disable();
    }
    return size;
}
static DEVICE_ATTR(main_flashlight_enable, 0644, NULL, main_flashlight_enable_store);
static int __init main_flashlight_init(void)  
{		
	main_flashlight_class = class_create(THIS_MODULE, "main_flashlight");
	main_flashlight_dev = device_create(main_flashlight_class,NULL, 0, NULL,  "main_flashlight");
    device_create_file(main_flashlight_dev, &dev_attr_main_flashlight_enable);
    
  //get flashlight dts gpio pin number
#if FLASH_PINCTRL
  flash_of_node=of_find_compatible_node(NULL, NULL, "mediatek,flashlight");
  flash_en_gpio=of_get_named_gpio(flash_of_node, "flash_en_gpio", 0);
  flash_mo_gpio=of_get_named_gpio(flash_of_node, "flash_mo_gpio", 0);  
  
  gpio_request(flash_en_gpio, "flash_en"); 
	gpio_direction_output(flash_en_gpio,0);	
	gpio_request(flash_mo_gpio, "flash_mo"); 
	gpio_direction_output(flash_mo_gpio,0);	
#endif
    
	return 0;
}
static void __exit main_flashlight_exit(void)
{
	return;
}
module_init(main_flashlight_init);
module_exit(main_flashlight_exit);
MODULE_LICENSE("GPL");
MODULE_DESCRIPTION("main_flashlight");
MODULE_AUTHOR("jst <aren.jiang@runyee.com.cn>");
#endif//add by zhou for test end
