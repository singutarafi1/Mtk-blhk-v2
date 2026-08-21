package com.example.protocol

/**
 * Complete MediaTek BROM, DA, Preloader, and XFlash Hardware Error Code Decoder
 * Ported from mtkclient error.py
 */
object MtkErrorCodes {

    private val ERROR_MAP = mapOf(
        0x0 to "OK (Operation Successful)",
        0x3E8 to "STOP (Operation Cancelled)",
        0x3EA to "INVALID_ARGUMENTS",
        0x3F3 to "NOT_ENOUGH_STORAGE_SPACE",
        0x3F4 to "NOT_ENOUGH_MEMORY",
        0x3F5 to "COM_PORT_OPEN_FAIL",
        0x3FC to "UNSUPPORTED_VER_OF_BOOT_ROM",
        0x3FD to "UNSUPPORTED_VER_OF_BOOTLOADER",
        0x3FE to "UNSUPPORTED_VER_OF_DA",
        0x401 to "SEC_INFO_NOT_FOUND",
        0x40D to "PART_NO_VALID_TABLE",
        0x40F to "UNSUPPORTED_VER_OF_SEC_CFG",
        0x411 to "CHKSUM_ERROR",
        0x412 to "TIMEOUT",
        0x7D0 to "SET_META_REG_FAIL",
        0x7D3 to "SET_EMI_FAIL",
        0x7D4 to "DOWNLOAD_DA_FAIL",
        0x7D7 to "CMD_JUMP_FAIL",
        0x7F1 to "CMD_SEND_DA_FAIL",
        0x7F3 to "CMD_JUMP_DA_FAIL",
        0x7FB to "SEC_VER_FAIL",
        0x7FC to "PL_SEC_VER_FAIL",
        0xBB8 to "INT_RAM_ERROR",
        0xBB9 to "EXT_RAM_ERROR",
        0xBBA to "SETUP_DRAM_FAIL",
        0xBC4 to "DEVICE_NOT_FOUND",
        0xBC7 to "NOR_FLASH_NOT_FOUND",
        0xBC8 to "NAND_FLASH_NOT_FOUND",
        0xBC9 to "SOC_CHECK_FAIL",
        0xC4D to "EMMC_FLASH_NOT_FOUND",
        0xC57 to "UFS_FLASH_NOT_FOUND",
        0xC62 to "HANDSET_SEC_CFG_NOT_FOUND",
        0x1000 to "CRYPTO_INIT_FAIL",
        0x1770 to "CALLBACK_SLA_CHALLENGE_FAIL",
        0x1771 to "SLA_WRONG_AUTH_FILE",
        0x1773 to "SLA_CHALLENGE_FAIL",
        0x1774 to "SLA_FAIL",
        0x1775 to "DAA_FAIL",
        0x1776 to "SBC_FAIL",
        0x1787 to "BOOTLOADER_IMAGE_SIGNATURE_FAIL",
        0x1789 to "BOOTLOADER_IMAGE_NO_SIGNATURE",
        0x178C to "SEND_CERT_FAIL",
        0x178D to "SEND_AUTH_FAIL",
        0x178E to "GET_SEC_CONFIG_FAIL",
        0x178F to "GET_ME_ID_FAIL",
        0x1790 to "GET_HW_SW_VER_FAIL",
        0x1791 to "GET_HW_CODE_FAIL",
        0x1796 to "SECURE_USB_DL_FAIL",
        0x17F5 to "SEC_CFG_NOT_EXIST",
        0x17F7 to "SEC_CFG_WRONG_MAGIC_NUMBER",
        0x17F8 to "SEC_CFG_IS_FULL",
        0x1D0C to "NO_AUTH_NEEDED",
        0x1D0D to "SLA_ERROR",
        0x1D0E to "DA_OVERLAP",
        0x1D0F to "DA_INVALID_JUMP_ADDR",
        0x1D14 to "DA_SIG_LEN_EXCEED_DA_LEN",
        0x1D15 to "DA_SIG_LEN_IS_ZERO_AND_DAA_ACTIVE",
        0x1D16 to "DA_NOT_EXIST",
        0x2001 to "DA_IMAGE_SIG_VERIFY_FAIL",
        0x3000 to "LIB_SEC_CFG_NOT_EXIST",
        0x5A5B to "DA_IN_BLACKLIST",
        0x6000 to "SBC_KEY_NOT_FOUND",
        0x6001 to "BR_SEC_CFG_NOT_FOUND",
        0x7015 to "DAA_Security_Error_Signature",
        0x7017 to "DAA_Security_Error",
        0x7024 to "DAA_SIG_VERIFY_FAILED"
    )

    fun decode(code: Int): String {
        return ERROR_MAP[code] ?: "Unknown MediaTek Hardware Status (0x%04X)".format(code)
    }
}
