#ifndef _T12193_H_
#define _T12193_H_

#include "tl1d_custom_rf.h"
#include "tl1d_custom_mipi.h"
#include "tl1d_custom_drdi.h"

#if (defined(_T12193_C_) && !defined(__AST_TL1_TDD_RF_TIMESEQ_SUPPORT__)) \
	|| (defined(__AST_TL1_TDD_RF_TIMESEQ_SUPPORT__) && defined(__AST_TL1_TDD_RF_TIMESEQ_DEFINE__))

#define END       0xffff
// Control action name define for timing control  
#define DBB_BPI_AREA1_TX_ENABLE           1      
#define DBB_BPI_AREA1_TX_DISABLE          2      
#define DBB_BPI_AREA2_RX_ENABLE           3      
#define DBB_BPI_AREA2_RX_DISABLE          4          
#define DBB_BPI_AREA3_PA_ENABLE           5      
#define DBB_BPI_AREA3_PA_DISABLE          6      
#define DBB_BPI_AREA4_ENABLE              7      
#define DBB_BPI_AREA4_DISABLE             8      
#define DBB_BPI_AREA5_ENABLE              9      
#define DBB_BPI_AREA5_DISABLE             10           
#define ABB_TX_ENABLE                     11      
#define ABB_TX_DISABLE                    12      
#define RF_TXGAIN_CTRL                    13    
#define RF_RXGAIN_CTRL                    14     
#define DBB_TX_ENABLE                     15      
#define DBB_TX_DISABLE                    16      
#define RF_TX_ENABLE                      17     
#define RF_TX_DISABLE                     18     
#define ABB_RX_ENABLE                     19      
#define ABB_RX_DISABLE                    20     
#define RF_RX_ENABLE                      21     
#define RF_RX_DISABLE                     22     
#define DBB_RX_ENABLE                     23      
#define DBB_RX_DISABLE                    24     
#define DCO_CTRL                          25     
#define RF_RTX_SWITCH                     26   
#define RF_TRX_SWITCH                     27     
#define DBB_TXE_CTRL                      29       
#define ABB_APC_ENABLE                    33
#define ABB_APC_DISABLE                   34   
#define DFE_RXSRC_TCU_TOGGLE              35
#define DFE_TXSRC_TCU_TOGGLE              36
#define DFE_SRC_CFG                       37
#define DFE_WB_CFG                        38
#define DFE_GET_RSSI                      39
#define DFE_RX_ENABLE                     40
#define DFE_RX_DISABLE                    41
#define DFE_TX_ENABLE                     42
#define DFE_TX_DISABLE                    43
#define DFE_APC_ENABLE                    44
#define DFE_APC_DISABLE                   45
#define DFE_RXIQ_COMPEN                   46
#define DFE_TXIQ_COMPEN                   47  
#define DFE_RXCLK_ENABLE                  48
#define DFE_RXCLK_DISABLE                 49
#define DFE_TXCLK_ENABLE                  50
#define DFE_TXCLK_DISABLE                 51
#define VPA_ENABLE                        52
#define VPA_DISABLE                       53
#define TXCLPC_TRIGGER                    54
#define TXCLPC_READ                       55 
#define DBB_MIPI_DEVICE2_RX_ENABLE         56
#define DBB_MIPI_DEVICE2_RX_DISABLE        57
#define DBB_MIPI_DEVICE2_TX_ENABLE         58
#define DBB_MIPI_DEVICE2_TX_DISABLE        59

#define DBB_MIPI_DEVICE3_RX_ENABLE         60
#define DBB_MIPI_DEVICE3_RX_DISABLE        61
#define DBB_MIPI_DEVICE3_TX_ENABLE         62
#define DBB_MIPI_DEVICE3_TX_DISABLE        63 

 //////////////////////////////////////////////////////////////////////////////////////////////

//Timing advance setting rule:                                                                                 
//The time difference between RF_RX_ENABLE  and the follow SPI action(RF_? ) is at least 10.                   
//The time difference between RF_RX_DISABLE and the follow SPI action(RF_? ) is at least 20.                   
//The time difference between RF_TX_ENABLE  and the follow SPI action(RF_? ) is at least 10.                   
//The time difference between RF_TX_DISABLE and the follow SPI action(RF_? ) is at least 20.                   
//The time difference between RF_?   action and the follow BPI action(ABB_?/DBB_?/DCO_? ) is at least 7.       
//The time difference between ABB_?  action and the follow SPI action(ABB_? ) is at least 18.                  
//The time difference between ABB_?  action and the follow BPI action(RF_?/DBB_?/DCO_? ) is at least 7.        
//The time difference between ABB_RX_SCALE  action and the follow SPI action(ABB_? ) is at least 36.           
//The time difference between ABB_RX_SCALE  action and the follow BPI action(RF_?/DBB_?/DCO_? ) is at least 25.
//The time difference between DBB_?  action and the follow action is at least 5.                               
//The time difference between DCO_?  action and the follow action is at least 17.                              


#if ( defined (MT6572) || defined (MT6582) || defined (MT6290) || defined(MT6595) || defined(MT6752)|| defined(MT6735)) && defined(MT6169_RF)
#if defined (SKY77621) 
T_RF_PROG_SEQ_STRUCT   AST_TL1_SEQ_DEFAULT[] = {{
//control action timing based on the beginning of the timeslot must be sorted by an increasing order 
//timing advance range : -400 ~ 0
//ENABLE_DOWNLINK_TIMING_DEFAULT[60]
{
//    action                     , timing advance(unit:1/8 chip)
      DBB_MIPI_DEVICE2_RX_ENABLE       , -380, //no data now, for customer future setting
      DBB_MIPI_DEVICE3_RX_ENABLE       , -380, //no data now, for customer future setting
      DBB_BPI_AREA2_RX_ENABLE          , -380,
      DCO_CTRL                         , -350,
      DFE_SRC_CFG                      , -270,	
      RF_RX_ENABLE                     , -250,
      RF_RXGAIN_CTRL                   , -220,
      DBB_RX_ENABLE                    , -170,	 //advance > 16 chip 
      DFE_RXCLK_ENABLE                 , -156,
      DFE_RXIQ_COMPEN                  , -130,
      DFE_RX_ENABLE                    ,  -96,
      DFE_RXSRC_TCU_TOGGLE             ,  -8,
      END                              ,  END // This line means the end of the actions,so it must be follow the last action.
						
},


//control action timing based on the end of the timeslot must be sorted by an increasing order 
//timing advance range : -200 ~ +100
//DISABLE_DOWNLINK_TIMING_DEFAULT[60]= 
{
//    action                     , timing advance(unit:1/8 chip)
      RF_RX_DISABLE                    ,   -9,
      DFE_RX_DISABLE                   ,   18,
      DFE_RXCLK_DISABLE                ,   25,
      ABB_RX_DISABLE                   ,   32, 
      DBB_BPI_AREA2_RX_DISABLE         ,   39,
      DBB_MIPI_DEVICE2_RX_DISABLE      ,   39,//no data now, for customer future setting
      DBB_MIPI_DEVICE3_RX_DISABLE      ,   39,//no data now, for customer future setting 
      DBB_RX_DISABLE                   , 69,//DBB need to close after next TS header, i.e reference timing 0 + xx echip
      END                              ,END // This line means the end of the actions,so it must be follow the last action.							
},


//control action timing based on the beginning of the timeslot must be sorted by an increasing order 
//timing advance range : -332 ~ 0
//ENABLE_UPLINK_TIMING_DEFAULT[60]= 
{
//    action                     , timing advance(unit:1/8 chip)
      DBB_MIPI_DEVICE2_TX_ENABLE       , -300,//no data now, for customer future setting
      DBB_MIPI_DEVICE3_TX_ENABLE       , -300,//no data now, for customer future setting
      VPA_ENABLE                       , -300,	 
#if INTERNAL_SW 		 
      DFE_APC_ENABLE                   , -218,
#endif
      DFE_SRC_CFG                      , -208, //-28,	
      DFE_TXCLK_ENABLE                 , -188,		
      DFE_TXIQ_COMPEN                  , -179, 
      DBB_TXE_CTRL                     , -164,
      DFE_TX_ENABLE                    , -156,
      RF_TX_ENABLE                     , -148,
      RF_TXGAIN_CTRL                   , -140,	
      DBB_BPI_AREA1_TX_ENABLE          , -123,
      DBB_TX_ENABLE                    ,  -93,//fix timing advance,don't change it 
      DBB_BPI_AREA3_PA_ENABLE          ,  -85,
      DFE_TXSRC_TCU_TOGGLE             ,   -8 , //-8,      
      TXCLPC_TRIGGER                   ,   64,
      END                              ,  END // This line means the end of the actions,so it must be follow the last action.							
},


//control action timing based on the end of the timeslot must be sorted by an increasing order 
//timing advance range : -200 ~ +100
//DISABLE_UPLINK_TIMING_DEFAULT[60]= 
{
//    action                     , timing advance(unit:1/8 chip)
      ABB_RX_DISABLE                   ,  -98,
      DBB_TX_DISABLE                   ,  -91, 		
      DBB_BPI_AREA1_TX_DISABLE         ,  -75,
      DBB_BPI_AREA3_PA_DISABLE         ,  -45,
      RF_TX_DISABLE                    ,  -15, 			
      ABB_TX_DISABLE                   ,   10, 
      DFE_TX_DISABLE                   ,   18, 						
#if INTERNAL_SW	                          
      DFE_APC_DISABLE                  ,   26, 
#endif		                                
      DFE_TXCLK_DISABLE                ,   34, 	  
      VPA_DISABLE                      ,   44,
      DBB_MIPI_DEVICE2_TX_DISABLE      ,   44,//no data now, for customer future setting
      DBB_MIPI_DEVICE3_TX_DISABLE      ,   44,//no data now, for customer future setting
      TXCLPC_READ                      ,   60,
 	  END                          , END  // This line means the end of the actions,so it must be follow the last action.
},


//control action timing based on the beginning of the second timeslot must be sorted by an increasing order 
//timing advance range : -373 ~ 0
//DL_DL_GAP_CTRL_TIMING_DEFAULT[60]= 
{
//    action                     , timing advance(unit:1/8 chip)
      RF_RX_ENABLE                     , -285,  
      DCO_CTRL                         , -275,
      DFE_SRC_CFG                      , -192,	
      RF_RXGAIN_CTRL                   , -172,		
      DFE_RXIQ_COMPEN                  , -120,
      DBB_BPI_AREA2_RX_ENABLE          , - 98,
      DBB_MIPI_DEVICE2_RX_ENABLE       , - 98, //no data now, for customer future setting
      DBB_MIPI_DEVICE3_RX_ENABLE       , - 98, //no data now, for customer future setting        
      DFE_RXSRC_TCU_TOGGLE             , -8,         
      END                              ,  END // This line means the end of the actions,so it must be follow the last action.							
},


//control action timing based on the beginning of the second timeslot must be sorted by an increasing order 
//timing advance range : -250 ~ 0
//UL_UL_GAP_CTRL_TIMING_DEFAULT[60]= 
{
//    action                     , timing advance(unit:1/8 chip)
      //DBB_TXE_CTRL                     , -205,
      DFE_SRC_CFG                      , -180,	
      DFE_TXSRC_TCU_TOGGLE             , -160,   
      TXCLPC_READ                      , -140,			
      DFE_TXIQ_COMPEN                  , -123,
      DBB_TXE_CTRL                     , -108,
      DBB_TX_ENABLE                    ,  -93,//fix timing advance,don't change it             
#if INTERNAL_SW 		 
      DFE_APC_ENABLE                   ,  -78,
#endif 
      RF_TXGAIN_CTRL                   ,  -68,  
      DBB_BPI_AREA1_TX_ENABLE          ,  -43,
      DBB_BPI_AREA3_PA_ENABLE          ,  -13,											  
      VPA_ENABLE                       ,   37,
      TXCLPC_TRIGGER                   ,   49,
      END                              ,  END // This line means the end of the actions,so it must be follow the last action.							
},


//control action timing based on the beginning of downlink timeslot must be sorted by an increasing order 
//timing advance range : -400 ~ 0
//UL_DL_GAP_CTRL_TIMING_DEFAULT[60]= 
{
//    action                     , timing advance(unit:1/8 chip)
       TXCLPC_READ                    ,  -350,
       DBB_RX_ENABLE                  ,  -333,
       DCO_CTRL                       ,  -328,
       DFE_SRC_CFG                    ,  -144,	
       DBB_BPI_AREA1_TX_DISABLE       ,   -121,
       DBB_TX_DISABLE                 ,   -91,     //FIX DONOT CHANGE
       RF_RXGAIN_CTRL                 ,   -83, 
       RF_RX_ENABLE                   ,   -75,
       DFE_TX_DISABLE                 ,   -66,
       DFE_RXCLK_ENABLE               ,   -56, 
       DFE_RX_ENABLE                  ,   -50,
    	 DBB_BPI_AREA2_RX_ENABLE        ,   -44, 
       
       DFE_RXSRC_TCU_TOGGLE           ,   -8,  

       DFE_RXIQ_COMPEN                ,    22,
       DBB_BPI_AREA3_PA_DISABLE       ,    34,    
       ABB_TX_DISABLE                 ,    64, 
#if INTERNAL_SW 			                              
       DFE_APC_DISABLE                ,    80,  
#endif		                                       
       VPA_DISABLE                    ,    88,                                        
       END                            ,    END // This line means the end of the actions,so it must be follow the last action.							
 },
 
/**********************************Below is for B39*****************************************/
//control action timing based on the beginning of the timeslot must be sorted by an increasing order 
//timing advance range : -332 ~ 0
//ENABLE_UPLINK_TIMING_DEFAULT[60]= 
 {
//    action                     , timing advance(unit:1/8 chip)
      DBB_MIPI_DEVICE2_TX_ENABLE       , -300,//no data now, for customer future setting
      DBB_MIPI_DEVICE3_TX_ENABLE       , -300,//no data now, for customer future setting
      VPA_ENABLE                       , -300,	 
#if INTERNAL_SW 		 
      DFE_APC_ENABLE                   , -218,
#endif
      DFE_SRC_CFG                      , -208 , //-28,	
      DFE_TXCLK_ENABLE                 , -188,		
      DFE_TXIQ_COMPEN                  , -179, 
      DBB_TXE_CTRL                     , -164,
      DFE_TX_ENABLE                    , -156,
      RF_TX_ENABLE                     , -148,
      RF_TXGAIN_CTRL                   , -140,	
      DBB_BPI_AREA1_TX_ENABLE          , -123,
      DBB_TX_ENABLE                    ,  -93,//fix timing advance,don't change it 
      DBB_BPI_AREA3_PA_ENABLE          ,  -85,
      DFE_TXSRC_TCU_TOGGLE             ,  -8 , //-8,      
      TXCLPC_TRIGGER                   ,   64,
      END                              ,  END  // This line means the end of the actions,so it must be follow the last action.
						
},

//control action timing based on the end of the timeslot must be sorted by an increasing order 
//timing advance range : -200 ~ +100
//DISABLE_DOWNLINK_TIMING_DEFAULT[60]= 
{
//    action                     , timing advance(unit:1/8 chip)
      ABB_RX_DISABLE                   ,  -98,
      DBB_TX_DISABLE                   ,  -91, 		
      DBB_BPI_AREA1_TX_DISABLE         ,  -75,
      DBB_BPI_AREA3_PA_DISABLE         ,  -45,
      RF_TX_DISABLE                    ,  -15, 			
      ABB_TX_DISABLE                   ,   10, 
      DFE_TX_DISABLE                   ,   18, 						
#if INTERNAL_SW	                         
      DFE_APC_DISABLE                  ,   26, 
#endif		                               
      DFE_TXCLK_DISABLE                ,   34, 	  
      VPA_DISABLE                      ,   44,
      DBB_MIPI_DEVICE2_TX_DISABLE      ,   44,//no data now, for customer future setting
      DBB_MIPI_DEVICE3_TX_DISABLE      ,   44,//no data now, for customer future setting
      TXCLPC_READ                      ,   60,
 	  END                          , END  // This line means the end of the actions,so it must be follow the last action.		
},

/**********************************Below is for B40*****************************************/
//control action timing based on the beginning of the timeslot must be sorted by an increasing order 
//timing advance range : -332 ~ 0
//ENABLE_UPLINK_TIMING_DEFAULT[60]= 
{
//    action                     , timing advance(unit:1/8 chip)
      DBB_MIPI_DEVICE2_TX_ENABLE       , -300,//no data now, for customer future setting
      DBB_MIPI_DEVICE3_TX_ENABLE       , -300,//no data now, for customer future setting
      VPA_ENABLE                       , -300,	 
#if INTERNAL_SW 		 
      DFE_APC_ENABLE                   , -218,
#endif
      DFE_SRC_CFG                      , -208 , //-28,	
      DFE_TXCLK_ENABLE                 , -188,		
      DFE_TXIQ_COMPEN                  , -179, 
      DBB_TXE_CTRL                     , -164,
      DFE_TX_ENABLE                    , -156,
      RF_TX_ENABLE                     , -148,
      RF_TXGAIN_CTRL                   , -140,	
      DBB_BPI_AREA1_TX_ENABLE          , -123,
      DBB_TX_ENABLE                    ,  -93,//fix timing advance,don't change it 
      DBB_BPI_AREA3_PA_ENABLE          ,  -85,
      DFE_TXSRC_TCU_TOGGLE             ,  -8 , //-8,      
      TXCLPC_TRIGGER                   ,   64,
      END                              ,  END // This line means the end of the actions,so it must be follow the last action.						
},


//control action timing based on the end of the timeslot must be sorted by an increasing order 
//timing advance range : -200 ~ +100
//DISABLE_DOWNLINK_TIMING_DEFAULT[60]= 
{
//    action                     , timing advance(unit:1/8 chip)
      ABB_RX_DISABLE                   ,  -98,
      DBB_TX_DISABLE                   ,  -91, 		
      DBB_BPI_AREA1_TX_DISABLE         ,  -75,
      DBB_BPI_AREA3_PA_DISABLE         ,  -45,
      RF_TX_DISABLE                    ,  -15, 			
      ABB_TX_DISABLE                   ,   10, 
      DFE_TX_DISABLE                   ,   18, 						
#if INTERNAL_SW	                         
      DFE_APC_DISABLE                  ,   26, 
#endif		                               
      DFE_TXCLK_DISABLE                ,   34, 	  
      VPA_DISABLE                      ,   44,
      DBB_MIPI_DEVICE2_TX_DISABLE      ,   44,//no data now, for customer future setting
      DBB_MIPI_DEVICE3_TX_DISABLE      ,   44,//no data now, for customer future setting
      TXCLPC_READ                      ,   60,
 	    END                              ,  END  // This line means the end of the actions,so it must be follow the last action.
},
}};
#endif
#endif
#if ( defined (MT6572) || defined (MT6582) || defined (MT6290) || defined(MT6595) || defined(MT6752)|| defined(MT6735)) && defined(MT6169_RF)
#if defined (SKY77590) 
T_RF_PROG_SEQ_STRUCT   AST_TL1_SEQ_DEFAULT[] = {{
//control action timing based on the beginning of the timeslot must be sorted by an increasing order 
//timing advance range : -400 ~ 0
//ENABLE_DOWNLINK_TIMING_DEFAULT[60]
{
//    action                     , timing advance(unit:1/8 chip)
      DCO_CTRL                         , -350,
      RF_RX_ENABLE                     , -250,
      RF_RXGAIN_CTRL                   , -220,
      DBB_RX_ENABLE                    , -170,//advance > 16 chip 
      DFE_RXCLK_ENABLE                 , -156,
      DFE_RXIQ_COMPEN                  , -130,
      DBB_BPI_AREA2_RX_ENABLE          , -108,  
      DFE_RX_ENABLE                    ,  -96,
      DFE_SRC_CFG                      ,  -28, 
      DFE_RXSRC_TCU_TOGGLE             ,   -8,
      END                              ,  END // This line means the end of the actions,so it must be follow the last action.
},


//control action timing based on the end of the timeslot must be sorted by an increasing order 
//timing advance range : -200 ~ +100
//DISABLE_DOWNLINK_TIMING_DEFAULT[60]= 
{
//    action                     , timing advance(unit:1/8 chip)
      RF_RX_DISABLE                    , -9,
      DFE_RX_DISABLE                   , 18,
      DFE_RXCLK_DISABLE                , 25,
      ABB_RX_DISABLE                   , 32, 
      DBB_BPI_AREA2_RX_DISABLE         , 39, 
      DBB_RX_DISABLE                   , 54,//DBB need to close after next TS header, i.e reference timing 0 + xx echip
      END                              ,END // This line means the end of the actions,so it must be follow the last action.							
},


//control action timing based on the beginning of the timeslot must be sorted by an increasing order 
//timing advance range : -332 ~ 0
//ENABLE_UPLINK_TIMING_DEFAULT[60]= 
{
//    action                     , timing advance(unit:1/8 chip)
      VPA_ENABLE                       , -300,	
      //DBB_TXE_CTRL                     , -205, 
#if INTERNAL_SW 		 
      DFE_APC_ENABLE                   , -143,
#endif
      DFE_TXCLK_ENABLE                 , -133,		
      DFE_TXIQ_COMPEN                  , -124, 
      DBB_TXE_CTRL                     , -109,      
      DFE_TX_ENABLE                    , -101,	
      DBB_TX_ENABLE                    ,  -93,//fix timing advance,don't change it 
      RF_TX_ENABLE                     ,  -85,
      RF_TXGAIN_CTRL                   ,  -69,
      DBB_BPI_AREA1_TX_ENABLE          ,  -49,
      DBB_BPI_AREA3_PA_ENABLE          ,  -35,
      DFE_SRC_CFG                      ,  -30,
      DFE_TXSRC_TCU_TOGGLE             ,  -15,
      TXCLPC_TRIGGER                   ,   0 ,
      END                              ,  END // This line means the end of the actions,so it must be follow the last action.							
},


//control action timing based on the end of the timeslot must be sorted by an increasing order 
//timing advance range : -200 ~ +100
//DISABLE_UPLINK_TIMING_DEFAULT[60]= 
{
//    action                     , timing advance(unit:1/8 chip)
      ABB_RX_DISABLE                   , -98,
      DBB_TX_DISABLE                   , -91,
      DBB_BPI_AREA3_PA_DISABLE         , -55,  		
      DBB_BPI_AREA1_TX_DISABLE         , -50, 
      RF_TX_DISABLE                    , -35, 			
      ABB_TX_DISABLE                   , -10, 
      DFE_TX_DISABLE                   ,  -2, 						
      DFE_APC_DISABLE                  ,   6, 
#if INTERNAL_SW	  
      DFE_TXCLK_DISABLE                ,  14, 
#endif
      VPA_DISABLE                      ,  24,
      TXCLPC_READ                      ,  40,
      END                              , END  // This line means the end of the actions,so it must be follow the last action.
},


//control action timing based on the beginning of the second timeslot must be sorted by an increasing order 
//timing advance range : -373 ~ 0
//DL_DL_GAP_CTRL_TIMING_DEFAULT[60]= 
{
//    action                     , timing advance(unit:1/8 chip)
      RF_RX_ENABLE                     , -285,  
      DCO_CTRL                         , -275,
      RF_RXGAIN_CTRL                   , -172,		
      DFE_RXIQ_COMPEN                  , -120,
      DBB_BPI_AREA2_RX_ENABLE          , - 98,  
      DFE_SRC_CFG                      ,  -28,  
      DFE_RXSRC_TCU_TOGGLE             ,   -8, 
      END                              ,  END // This line means the end of the actions,so it must be follow the last action.							
},


//control action timing based on the beginning of the second timeslot must be sorted by an increasing order 
//timing advance range : -250 ~ 0
//UL_UL_GAP_CTRL_TIMING_DEFAULT[60]= 
{
//    action                     , timing advance(unit:1/8 chip)
      //DBB_TXE_CTRL                     , -205,
      TXCLPC_READ                      ,-140,			
      DFE_TXIQ_COMPEN                  ,-123,
      DBB_TXE_CTRL                     ,-108,	
      DBB_TX_ENABLE                    , -93,//fix timing advance,don't change it  
#if INTERNAL_SW 		 
      DFE_APC_ENABLE                   , -78,
#endif       
      RF_TXGAIN_CTRL                   , -68, 
      DFE_SRC_CFG                      , -43,  
      DFE_TXSRC_TCU_TOGGLE             , -28,
      DBB_BPI_AREA1_TX_ENABLE          , -20,											  
      DBB_BPI_AREA3_PA_ENABLE          ,  -2,
      VPA_ENABLE                       ,   8,
      TXCLPC_TRIGGER                   ,  20,
      END                              , END // This line means the end of the actions,so it must be follow the last action.							
},


//control action timing based on the beginning of downlink timeslot must be sorted by an increasing order 
//timing advance range : -400 ~ 0
//UL_DL_GAP_CTRL_TIMING_DEFAULT[60]= 
{
//    action                     , timing advance(unit:1/8 chip)
       TXCLPC_READ                   ,-350,
       DBB_RX_ENABLE                 ,-333,
       DCO_CTRL                      ,-328,
       DBB_TX_DISABLE                ,-91 ,
       RF_RXGAIN_CTRL                ,-83 , //reference timing, don't change!!!!!!!
       RF_RX_ENABLE                  ,-75 ,
       DFE_TX_DISABLE                ,-66 ,
       DFE_RXCLK_ENABLE              ,-56 ,	
       DFE_RX_ENABLE                 ,-50 ,
       DBB_BPI_AREA3_PA_DISABLE      ,-38 , 
       DBB_BPI_AREA1_TX_DISABLE      ,-33 ,		  
       DBB_BPI_AREA2_RX_ENABLE       ,-13 , 
       DFE_RXIQ_COMPEN               ,-2  ,	  
       ABB_TX_DISABLE                ,10   ,
       DFE_SRC_CFG                   ,20  ,
#if INTERNAL_SW                            
       DFE_APC_DISABLE 			         ,40  ,
#endif                                      
       VPA_DISABLE                   ,48  ,
       //DFE_RXSRC_TCU_TOGGLE          ,68  ,
       END                              ,  END// This line means the end of the actions,so it must be follow the last action.							
},

/**********************************Below is for B39*****************************************/
//control action timing based on the beginning of the timeslot must be sorted by an increasing order 
//timing advance range : -332 ~ 0
//ENABLE_UPLINK_TIMING_DEFAULT[60]= 
{
//    action                     , timing advance(unit:1/8 chip)
      VPA_ENABLE                       , -300,	
      //DBB_TXE_CTRL                     , -205, 
#if INTERNAL_SW 		 
      DFE_APC_ENABLE                   , -143,
#endif
      DFE_TXCLK_ENABLE                 , -133,		
      DFE_TXIQ_COMPEN                  , -124, 
      DBB_TXE_CTRL                     , -109,      
      DFE_TX_ENABLE                    , -101,	
      DBB_TX_ENABLE                    ,  -93,//fix timing advance,don't change it 
      RF_TX_ENABLE                     ,  -85,
      RF_TXGAIN_CTRL                   ,  -69,
      DBB_BPI_AREA1_TX_ENABLE          ,  -49,
      DBB_BPI_AREA3_PA_ENABLE          ,  -35,
      DFE_SRC_CFG                      ,  -30,
      DFE_TXSRC_TCU_TOGGLE             ,  -15,
      TXCLPC_TRIGGER                   ,   0,
      END                              ,  END // This line means the end of the actions,so it must be follow the last action.							
},


//control action timing based on the end of the timeslot must be sorted by an increasing order 
//timing advance range : -200 ~ +100
//DISABLE_UPLINK_TIMING_DEFAULT[60]= 
{
//    action                     , timing advance(unit:1/8 chip)
      ABB_RX_DISABLE                   , -98,
      DBB_TX_DISABLE                   , -91,
      DBB_BPI_AREA3_PA_DISABLE         , -55,  		
      DBB_BPI_AREA1_TX_DISABLE         , -50, 
      RF_TX_DISABLE                    , -35, 			
      ABB_TX_DISABLE                   , -10, 
      DFE_TX_DISABLE                   ,  -2, 						
      DFE_APC_DISABLE                  ,   6, 
#if INTERNAL_SW	  
      DFE_TXCLK_DISABLE                ,  14, 
#endif
      VPA_DISABLE                      ,  24,
      TXCLPC_READ                      ,  40,
      END                              , END  // This line means the end of the actions,so it must be follow the last action.
},
/**********************************Above is for B39*****************************************/

/**********************************Below is for B40*****************************************/
//control action timing based on the beginning of the timeslot must be sorted by an increasing order 
//timing advance range : -332 ~ 0
//ENABLE_UPLINK_TIMING_DEFAULT[60]= 
{
//    action                     , timing advance(unit:1/8 chip)
      VPA_ENABLE                       , -300,	
      //DBB_TXE_CTRL                     , -205, 
#if INTERNAL_SW 		 
      DFE_APC_ENABLE                   , -143,
#endif
      DFE_TXCLK_ENABLE                 , -133,		
      DFE_TXIQ_COMPEN                  , -124, 
      DBB_TXE_CTRL                     , -109,      
      DFE_TX_ENABLE                    , -101,	
      DBB_TX_ENABLE                    ,  -93,//fix timing advance,don't change it 
      RF_TX_ENABLE                     ,  -85,
      RF_TXGAIN_CTRL                   ,  -69,
      DBB_BPI_AREA1_TX_ENABLE          ,  -49,
      DBB_BPI_AREA3_PA_ENABLE          ,  -35,
      DFE_SRC_CFG                      ,  -30,
      DFE_TXSRC_TCU_TOGGLE             ,  -15,
      TXCLPC_TRIGGER                   ,   0,
      END                              ,  END // This line means the end of the actions,so it must be follow the last action.							
},


//control action timing based on the end of the timeslot must be sorted by an increasing order 
//timing advance range : -200 ~ +100
//DISABLE_UPLINK_TIMING_DEFAULT[60]= 
{
//    action                     , timing advance(unit:1/8 chip)
      ABB_RX_DISABLE                   , -98,
      DBB_TX_DISABLE                   , -91,
      DBB_BPI_AREA3_PA_DISABLE         , -55,  		
      DBB_BPI_AREA1_TX_DISABLE         , -50, 
      RF_TX_DISABLE                    , -35, 			
      ABB_TX_DISABLE                   , -10, 
      DFE_TX_DISABLE                   ,  -2, 						
      DFE_APC_DISABLE                  ,   6, 
#if INTERNAL_SW	  
      DFE_TXCLK_DISABLE                ,  14, 
#endif
      VPA_DISABLE                      ,  24,
      TXCLPC_READ                      ,  40,
      END                              , END  // This line means the end of the actions,so it must be follow the last action.
},
/**********************************Above is for B40*****************************************/
}};
#endif
#endif


#else
extern T_RF_PROG_SEQ_STRUCT  const AST_TL1_SEQ_DEFAULT[];
#endif

#endif
