BOARD_TYPE          := 0x03
BOARD_REVISION      := 0x01
BOOTLOADER_VERSION  := 0x01
HW_TYPE             := 0x01

MCU                 := cortex-m3
CHIP                := STM32F103CBT
BOARD               := STM32F103CB_ESC_Rev1
MODEL               := MD
MODEL_SUFFIX        := 

OPENOCD_JTAG_CONFIG := stlink-v2.cfg
OPENOCD_CONFIG      := stm32f1x.stlink.cfg

# Note: These must match the values in link_$(BOARD)_memory.ld
BL_BANK_BASE        := 0x08000000  # Start of bootloader flash
BL_BANK_SIZE        := 0x00003000  # Should include BD_INFO region
FW_BANK_BASE        := 0x08003000  # Start of firmware flash
FW_BANK_SIZE        := 0x0000CC00  # Should include FW_DESC_SIZE
EE_BANK_BASE        := 0x0800FC00  # EEPROM storage area
EE_BANK_SIZE        := 0x00000400  # Size of EEPROM storage area

FW_DESC_SIZE        := 0x00000064
