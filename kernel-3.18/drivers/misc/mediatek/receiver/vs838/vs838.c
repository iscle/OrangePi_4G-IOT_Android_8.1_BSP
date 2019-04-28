#include <asm/uaccess.h>
#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/init.h>
#include <linux/platform_device.h>
#include <linux/poll.h>
#include <linux/slab.h>
#include <linux/miscdevice.h>
#include <linux/string.h>
#include <linux/fb.h>
#include <linux/miscdevice.h>
#include <linux/delay.h>
#include <linux/timer.h> 
#include <linux/jiffies.h>
#include <linux/cdev.h>
#include <linux/of.h>
#include <linux/of_irq.h>
#include <linux/gpio.h>
#include <linux/fs.h>
#include <linux/cdev.h>
#include <linux/gpio.h>
#include <linux/delay.h>
#include <linux/interrupt.h>
#include <linux/wait.h>
#include <linux/kthread.h>
#include <linux/poll.h>
#include <linux/types.h>
#include <linux/version.h>
#include <linux/module.h>
#include <linux/i2c.h>
#include <linux/platform_device.h>
#include <linux/uaccess.h>
#include <linux/fs.h>
#include <asm/atomic.h>
#include <linux/input.h>

#ifdef CONFIG_COMPAT
#include <linux/compat.h>
#endif

#define PLATFORM_DRIVER_NAME   "irda_vs838"
#define IR_GPIO   71
#define IR_LIGHT_TEST

struct input_dev *ir_input_dev;
static const u16 ir_keymap[] = {
	KEY_HOMEPAGE,KEY_0,KEY_BACK,
	KEY_1,KEY_2,KEY_3,
	KEY_4,KEY_5,KEY_6,
	KEY_7,KEY_8,KEY_9,
	KEY_UP,KEY_DOWN,KEY_LEFT,KEY_RIGHT,
	KEY_OK,KEY_MENU,KEY_VOLUMEUP,KEY_VOLUMEDOWN,
};



/* ir eint relate define */
int ir_ret;
u32 ir_ints[2] = { 0, 0 };
u32 ir_ints1[2] = { 0, 0 };
int ir_irq;
unsigned int ir_gpiopin, ir_debounce;
unsigned int ir_eint_type;
struct device_node *ir_node = NULL;

static const struct of_device_id ir_of_match[] = {
	{.compatible = "mediatek,irda_vs838"},
	{},
};

/* ir receive data relate define */
unsigned char ir_flag = 0; //表示数据帧的开始
unsigned char num = 0; //表示数据帧里的第几位数据
static long long prev = 0; //记录上次的时间
unsigned int times[40]; //记录每位数据的时间
char buf_ir[3];//usercode[msb+lsb]+datacode+inverse_datacode

#if defined(IR_LIGHT_TEST)
static int onoff_flag = 0;
enum mt65xx_led_pmic {
	MT65XX_LED_PMIC_LCD_ISINK = 0,
	MT65XX_LED_PMIC_NLED_ISINK0,
	MT65XX_LED_PMIC_NLED_ISINK1,
	MT65XX_LED_PMIC_NLED_ISINK2,
	MT65XX_LED_PMIC_NLED_ISINK3
};
extern int runyee_isink_set_pmic(enum mt65xx_led_pmic pmic_type, int level);
#endif

static void ir_check_code(struct input_dev *input_dev,int ir_code)
{
	printk("[runyee_timer] ir_code == 0x%x\n ",ir_code);

#if defined(IR_LIGHT_TEST)
	if(ir_code > 0 && ir_code < 0xff)
	{
		if(onoff_flag)
		{
			runyee_isink_set_pmic(MT65XX_LED_PMIC_NLED_ISINK0,1); 
			onoff_flag = 0;
		}
		else
		{
			runyee_isink_set_pmic(MT65XX_LED_PMIC_NLED_ISINK0,0); 
			onoff_flag = 1;
		}
	}
#endif	
	
	switch(ir_code)
	{
	    case 0x1C:
	        input_report_key(input_dev, KEY_LEFT, 1);
	        input_sync(input_dev);
	        input_report_key(input_dev, KEY_LEFT, 0);
	        input_sync(input_dev);
	        break;
	    case 0x48:
	        input_report_key(input_dev, KEY_RIGHT, 1);
	        input_sync(input_dev);
	        input_report_key(input_dev, KEY_RIGHT, 0);
	        input_sync(input_dev);
		    break;
	    case 0x44:
	        input_report_key(input_dev, KEY_UP, 1);
	        input_sync(input_dev);
	        input_report_key(input_dev, KEY_UP, 0);
	        input_sync(input_dev);                                  
	        break;
	    case 0x1D:
	        input_report_key(input_dev, KEY_DOWN, 1);
	        input_sync(input_dev);
	        input_report_key(input_dev, KEY_DOWN, 0);
	        input_sync(input_dev);
	        break;
	    case 0x5C:
	        input_report_key(input_dev, KEY_OK, 1);
	        input_sync(input_dev);
	        input_report_key(input_dev, KEY_OK, 0);
	        input_sync(input_dev);
	        break;
	    case 0x1F:
	        input_report_key(input_dev, KEY_HOMEPAGE, 1);
	        input_sync(input_dev);
	        input_report_key(input_dev, KEY_HOMEPAGE, 0);
	        input_sync(input_dev);
	        break;
	    case 0x0A:
	        input_report_key(input_dev, KEY_BACK, 1);
	        input_sync(input_dev);
	        input_report_key(input_dev, KEY_BACK, 0);
	        input_sync(input_dev);
	        break;
	    case 0x5D:
	        input_report_key(input_dev, KEY_MENU, 1);
	        input_sync(input_dev);
	        input_report_key(input_dev, KEY_MENU, 0);
	        input_sync(input_dev);
	        break;
	    case 0x24:
	        input_report_key(input_dev, KEY_VOLUMEDOWN, 1);
	        input_sync(input_dev);
	        input_report_key(input_dev, KEY_VOLUMEDOWN, 0);
	        input_sync(input_dev);
	        break;
	    case 0x25:
	        input_report_key(input_dev, KEY_VOLUMEUP, 1);
	        input_sync(input_dev);
	        input_report_key(input_dev, KEY_VOLUMEUP, 0);
	        input_sync(input_dev);
	        break;
	    case 0X47:
	        input_report_key(input_dev, KEY_0, 1);
	        input_sync(input_dev);
	        input_report_key(input_dev, KEY_0, 0);
	        input_sync(input_dev);
	        break;
	    case 0x13:
	        input_report_key(input_dev, KEY_1, 1);
	        input_sync(input_dev);
	        input_report_key(input_dev, KEY_1, 0);
	        input_sync(input_dev);
	        break;
	    case 0x10: 
	        input_report_key(input_dev, KEY_2, 1);
	        input_sync(input_dev);
	        input_report_key(input_dev, KEY_2, 0);
	        input_sync(input_dev);
	        break;
	    case 0x11: 
	        input_report_key(input_dev, KEY_3, 1);
	        input_sync(input_dev);
	        input_report_key(input_dev, KEY_3, 0);
	        input_sync(input_dev);
	        break;
	    case 0x0F:
	        input_report_key(input_dev, KEY_4, 1);
	        input_sync(input_dev);
	        input_report_key(input_dev, KEY_4, 0);
	        input_sync(input_dev);
	        break;
	    case 0x0C: 
	        input_report_key(input_dev, KEY_5, 1);
	        input_sync(input_dev);
	        input_report_key(input_dev, KEY_5, 0);
	        input_sync(input_dev);
	        break;
	    case 0x0D: 
	        input_report_key(input_dev, KEY_6, 1);
	        input_sync(input_dev);
	        input_report_key(input_dev, KEY_6, 0);
	        input_sync(input_dev);
	        break;
	    case 0x0B:
	        input_report_key(input_dev, KEY_7, 1);
	        input_sync(input_dev);
	        input_report_key(input_dev, KEY_7, 0);
	        input_sync(input_dev);
	        break;
	    case 0x08: 
	        input_report_key(input_dev, KEY_8, 1);
	        input_sync(input_dev);
	        input_report_key(input_dev, KEY_8, 0);
	        input_sync(input_dev);
	        break;
	    case 0x09: 
	        input_report_key(input_dev, KEY_9, 1);
	        input_sync(input_dev);
	        input_report_key(input_dev, KEY_9, 0);
	        input_sync(input_dev);
	        break;			
	    default:
	        break;
	}

}


static irqreturn_t ir_eint_func(int irq, void *dev_id)
{
    long long now = ktime_to_us(ktime_get());
    unsigned int offset;
    int i, j, tmp;
	int bst = 0;

    if (!ir_flag) //数据开始
    {
    	printk("[runyee_ir 1] %s\n", __func__);
        ir_flag = 1;
        prev = now;
        return IRQ_HANDLED;
    }

    offset = now - prev;
    prev = now;
	printk("[runyee_ir 2] %s, offset = %d\n", __func__, offset);
    if ((offset > 13000) && (offset < 14000)) //判断是否收到引导码，引导码13.5ms
    {
    	printk("[runyee_ir] ir data is valid, headcode_time=%d\n", offset);
        num = 0;
        return IRQ_HANDLED;
    }

    //不是引导码时间，数据位时间
    if (num < 32)
    {
        times[num++] = offset;
    }

    if (num >= 32)
    {
    	bst = 0;
    	//printk("[runyee_ir 4] data reveive:  ");
        for (i = 0; i < 4; i++) //共4个字节
        {
            tmp = 0;
            for (j = 0; j < 8; j++) //每字节8位
            {
            	bst =  i*8+j;
				if(i==3)
				{
            		printk("time of bit_%d is: %d \n", bst,times[bst]);
				}
                if (times[i*8+j] > 2000) //如果数据位的信号周期大于2ms, 则是二进制数据1
                    tmp |= 1<<j;
            }
            buf_ir[i] = tmp;
        }
		
		//printk("%s: 0x%02x, 0x%02x, 0x%02x, 0x%02x \n", __func__,buf_ir[0], buf_ir[1],buf_ir[2],buf_ir[3]);

		ir_check_code(ir_input_dev, buf_ir[2]);
		
        ir_flag = 0; //重新开始帧
    }
    return IRQ_HANDLED;

}
	 
 
static int IR_probe(struct platform_device *pdev)
{
	unsigned char i = 0;
	
	ir_node = of_find_matching_node(ir_node, ir_of_match);
	
	if (ir_node)
	{
		of_property_read_u32_array(ir_node, "debounce", ir_ints, ARRAY_SIZE(ir_ints));
		of_property_read_u32_array(ir_node, "interrupts", ir_ints1, ARRAY_SIZE(ir_ints1));
		ir_gpiopin = ir_ints[0];
		ir_debounce = ir_ints[1];
		ir_eint_type = ir_ints1[1];
		gpio_set_debounce(ir_gpiopin, ir_debounce);
		
		ir_irq = irq_of_parse_and_map(ir_node, 0);
		#if 1
		ir_ret = request_irq(ir_irq, ir_eint_func, IRQF_TRIGGER_FALLING, "ir_irq-eint", NULL);
		#else
		ir_ret = request_irq(ir_irq, ir_eint_func, IRQF_TRIGGER_LOW, "ir_irq-eint", NULL);
		#endif
		if (ir_ret != 0)
		{
			printk("[runyee_ir]EINT IRQ LINE NOT AVAILABLE\n");
		}
		else
		{
			printk("[runyee_ir]ir_irq set EINT finished, irq=%d, debounce=%d, type=%d\n",ir_irq, ir_debounce, ir_eint_type);
		}
	}
	else
	{
		printk("[runyee_ir]%s can't find compatible node\n", __func__);
	}

	/* initialize and register input device (/dev/input/eventX) */
	ir_input_dev = input_allocate_device();
	if (!ir_input_dev) {
		printk("[runyee_ir] input allocate device fail.\n");
		return -ENOMEM;
	}

	ir_input_dev->name = "irda_vs838";
	ir_input_dev->id.bustype = BUS_HOST;
	ir_input_dev->id.vendor = 0x0001;
	ir_input_dev->id.product = 0x0005;
	ir_input_dev->id.version = 0x0010;

	__set_bit(EV_KEY, ir_input_dev->evbit);

    for (i = 0; i < ARRAY_SIZE(ir_keymap); i++)
    {
		__set_bit(ir_keymap[i], ir_input_dev->keybit);	
    }

	ir_ret = input_register_device(ir_input_dev);
	if (ir_ret) {
		printk("[runyee_ir] register input device failed (%d)\n", ir_ret);
		input_free_device(ir_input_dev);
		return ir_ret;
	}	
	
	return ir_ret;
}
 
static int IR_remove(struct platform_device *pdev)
{
	 return 0;
}

static int IR_suspend(struct platform_device *pdev, pm_message_t mesg)
{
	return 0;
}

static int IR_resume(struct platform_device *pdev)
{
	return 0;
}
/* platform structure */
static struct platform_driver g_stIR_Driver = {
	.probe = IR_probe,
	.remove = IR_remove,
	.suspend = IR_suspend,
	.resume = IR_resume,
	.driver = {
		   .name = PLATFORM_DRIVER_NAME,
		   .owner = THIS_MODULE,
		   }
};

static struct platform_device g_stIR_device = {
	.name = PLATFORM_DRIVER_NAME,
	.id = 0,
	.dev = {}
};

struct class *ir_receiver_class;
struct device *ir_receiver_dev;
static ssize_t ir_receiver_show(struct device *dev, struct device_attribute *attr, char *buf)
{       
	int i;
	int len = 0;

	for(i=0;i<4;i++)
	{
		len += sprintf(buf + len, "0x%02x,", buf_ir[i]);
	}	
	return  len;
}
static DEVICE_ATTR(ir_receiver,     0664, ir_receiver_show, NULL);

static int __init ir_init(void)
{

	ir_receiver_class = class_create(THIS_MODULE, "ir_receiver");
	ir_receiver_dev = device_create(ir_receiver_class,NULL, 0, NULL,  "ir_receiver");
  	device_create_file(ir_receiver_dev, &dev_attr_ir_receiver);
    	
	if (platform_device_register(&g_stIR_device))
	{
		printk("failed to register IR driver\n"); 
		return -ENODEV;
	}

	if (platform_driver_register(&g_stIR_Driver))
	{
		printk("Failed to register IR driver\n");
		return -ENODEV;
	}

	return 0;
}

static void __exit ir_exit(void)
{
	platform_driver_unregister(&g_stIR_Driver);
}

//module_init(ir_init);
late_initcall(ir_init);
module_exit(ir_exit);

MODULE_DESCRIPTION("runyee irda receiver driver");
MODULE_AUTHOR("jiangshitian <aren.jiang@runyee.com.cn>");
MODULE_LICENSE("GPL");
