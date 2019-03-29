#ifndef __MT65XX_SYNC_WRITE_H__
#define __MT65XX_SYNC_WRITE_H__

#define mt_reg_sync_writel(v, a)        mt65xx_reg_sync_writel(v, a)
#define mt_reg_sync_writew(v, a)        mt65xx_reg_sync_writew(v, a)
#define mt_reg_sync_writeb(v, a)        mt65xx_reg_sync_writeb(v, a)

#if defined(__KERNEL__)

#include <linux/io.h>
#include <asm/cacheflush.h>
#include <asm/system.h>

/*
 * Define macros.
 */

#define mt65xx_reg_sync_writel(v, a) \
        do {    \
            writel((v), (a));   \
            dsb();  \
        } while (0)

#define mt65xx_reg_sync_writew(v, a) \
        do {    \
            writew((v), (a));   \
            dsb();  \
        } while (0)

#define mt65xx_reg_sync_writeb(v, a) \
        do {    \
            writeb((v), (a));   \
            dsb();  \
        } while (0)

#else   /* __KERNEL__ */

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <string.h>

#define dsb()   \
        do {    \
            __asm__ __volatile__ ("dsb sy" : : : "memory"); \
        } while (0)

#define outer_sync()    \
        do {    \
            int fd; \
            char buf[] = "1";   \
            fd = open("/sys/bus/platform/drivers/outercache/outer_sync", O_WRONLY); \
            if (fd != -1) {  \
                write(fd, buf, strlen(buf)); \
                close(fd); \
            }   \
        } while (0)

#define mt65xx_reg_sync_writel(v, a) \
        do {    \
            *(volatile unsigned int *)(a) = (v);    \
            dsb(); \
        } while (0)

#define mt65xx_reg_sync_writew(v, a) \
        do {    \
            *(volatile unsigned short *)(a) = (v);    \
            dsb(); \
        } while (0)

#define mt65xx_reg_sync_writeb(v, a) \
        do {    \
            *(volatile unsigned char *)(a) = (v);    \
            dsb(); \
        } while (0)

#endif  /* __KERNEL__ */

#endif  /* !__MT65XX_SYNC_WRITE_H__ */
