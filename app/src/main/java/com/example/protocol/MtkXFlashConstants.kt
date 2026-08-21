package com.example.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MediaTek XFlash Protocol Constants, Command Codes, and Data Structures
 * Direct, faithful port of Python mtkclient:
 * - mtkclient/Library/DA/xflash/xflash_param.py
 * - mtkclient/Library/DA/storage.py
 */
object MtkXFlashConstants {

    const val MAGIC: Int = 0xFEEEEEEF.toInt()
    const val XFLASH_MAGIC_INT: Int = 0xFEEEEEEF.toInt()
    const val XFLASH_MAGIC: Long = 0xFEEEEEEFL
    const val SYNC_SIGNAL: Int = 0x434E5953

    object Cmd {
        const val MAGIC: Int = 0xFEEEEEEF.toInt()
        const val SYNC_SIGNAL: Int = 0x434E5953

        // Standard Protocol Commands
        const val FORMAT = 0x010003
        const val WRITE_DATA = 0x010004
        const val READ_DATA = 0x010005
        const val SHUTDOWN = 0x010007
        const val BOOT_TO = 0x010008
        const val DEVICE_CTRL = 0x010009
        const val INIT_EXT_RAM = 0x01000A
        const val SWITCH_USB_SPEED = 0x01000B
        const val READ_OTP_ZONE = 0x01000C
        const val WRITE_OTP_ZONE = 0x01000D
        const val WRITE_EFUSE = 0x01000E
        const val READ_EFUSE = 0x01000F
        const val NAND_BMT_REMARK = 0x010010
        const val SETUP_ENVIRONMENT = 0x010100
        const val SETUP_HW_INIT_PARAMS = 0x010101

        // Device Control Configuration Commands
        const val SET_HOST_INFO = 0x020001
        const val SET_SECURITY_LEVEL = 0x020002
        const val SET_CHECKSUM_LEVEL = 0x020003
        const val SET_RESET_KEY = 0x020004
        const val SET_BATTERY_OPT = 0x020005
        const val SET_EMMC_HW_RESET_PIN = 0x020006
        const val SET_GEN_CHKSUM_BLOCK_SZ = 0x020007

        // Information Query Commands
        const val GET_EMMC_INFO = 0x040001
        const val GET_NAND_INFO = 0x040002
        const val GET_NOR_INFO = 0x040003
        const val GET_UFS_INFO = 0x040004
        const val GET_EX_STORAGE_INFO = 0x040005
        const val GET_DA_VERSION = 0x040006
        const val GET_PACKET_LENGTH = 0x040007
        const val GET_RANDOM_DATA = 0x040008
        const val GET_SECURITY_INFO = 0x040009
        const val GET_CONNECTION_AGENT = 0x04000A
        const val GET_BATTERY_VOLTAGE = 0x04000B
        const val GET_DRAM_TYPE = 0x04000C
        const val GET_CHIP_ID = 0x04000D
        const val GET_STAGE_STATUS = 0x04000E

        // Custom Control Commands
        const val CC_GET_USB_SPEED = 0x080001
        const val CC_SWITCH_USB_SPEED = 0x080002
        const val CC_READ_REGISTER = 0x080003
        const val CC_WRITE_REGISTER = 0x080004
        const val CC_OPTIONAL_DOWNLOAD_ACT = 0x080005
        const val CC_REBOOT = 0x080006
    }

    object DataType {
        const val DT_PROTOCOL_FLOW = 1
        const val DT_MESSAGE = 2
    }

    object DevCtrl {
        const val GET_CHIP_ID = Cmd.GET_CHIP_ID
        const val REBOOT = Cmd.CC_REBOOT
        const val GET_STORAGE_INFO = Cmd.GET_EX_STORAGE_INFO
        const val GET_BATTERY_VOLTAGE = Cmd.GET_BATTERY_VOLTAGE
        const val GET_PACKET_LENGTH = Cmd.GET_PACKET_LENGTH
        const val GET_DRAM_TYPE = Cmd.GET_DRAM_TYPE
        const val GET_STAGE_STATUS = Cmd.GET_STAGE_STATUS
        const val SWITCH_USB_SPEED = Cmd.CC_SWITCH_USB_SPEED
        const val GET_DA_VERSION = Cmd.GET_DA_VERSION
        const val GET_EMMC_INFO = Cmd.GET_EMMC_INFO
        const val GET_UFS_INFO = Cmd.GET_UFS_INFO
        const val GET_NAND_INFO = Cmd.GET_NAND_INFO
        const val GET_NOR_INFO = Cmd.GET_NOR_INFO
    }

    object DaStorage {
        const val NONE = 0x00
        const val MTK_DA_STORAGE_EMMC = 0x1
        const val MTK_DA_STORAGE_SDMMC = 0x2
        const val MTK_DA_STORAGE_NAND = 0x10
        const val MTK_DA_STORAGE_NOR = 0x20
        const val MTK_DA_STORAGE_UFS = 0x30
        const val MTK_DA_STORAGE_RAM = 0x40

        const val EMMC = MTK_DA_STORAGE_EMMC
        const val SDMMC = MTK_DA_STORAGE_SDMMC
        const val NAND = MTK_DA_STORAGE_NAND
        const val NOR = MTK_DA_STORAGE_NOR
        const val UFS = MTK_DA_STORAGE_UFS
        const val RAM = MTK_DA_STORAGE_RAM
    }

    object EmmcPartitionType {
        const val NONE = 0
        const val MTK_DA_EMMC_PART_BOOT1 = 1
        const val MTK_DA_EMMC_PART_BOOT2 = 2
        const val MTK_DA_EMMC_PART_RPMB = 3
        const val MTK_DA_EMMC_PART_GP1 = 4
        const val MTK_DA_EMMC_PART_GP2 = 5
        const val MTK_DA_EMMC_PART_GP3 = 6
        const val MTK_DA_EMMC_PART_GP4 = 7
        const val MTK_DA_EMMC_PART_USER = 8

        const val BOOT1 = MTK_DA_EMMC_PART_BOOT1
        const val BOOT2 = MTK_DA_EMMC_PART_BOOT2
        const val RPMB = MTK_DA_EMMC_PART_RPMB
        const val GP1 = MTK_DA_EMMC_PART_GP1
        const val GP2 = MTK_DA_EMMC_PART_GP2
        const val GP3 = MTK_DA_EMMC_PART_GP3
        const val GP4 = MTK_DA_EMMC_PART_GP4
        const val USER = MTK_DA_EMMC_PART_USER
    }

    object UFSPartitionType {
        const val NONE = 0
        const val BOOT1 = 1
        const val BOOT2 = 2
        const val USER = 3
        const val RPMB = 4

        const val LU0 = USER
        const val LU1 = BOOT1
        const val LU2 = BOOT2
    }

    val UfsPartitionType = UFSPartitionType

    object StatusCode {
        const val STATUS_OK = 0x00L
        const val STATUS_CONTINUE = 0x01L
        const val STATUS_ACK = 0x02L
        const val STATUS_NACK = 0x03L
        const val STATUS_ERROR = 0xFFFFFFFFL
    }
}

/**
 * XFlash NandExtension parameter structure (<IIIIIIII - 32 bytes)
 * Order: cell_usage, addr_type, bin_type, region, format_level, sys_slc, usr_slc, max_size
 */
data class NandExtension(
    val cellUsage: Int = 0,
    val addrType: Int = 0,
    val binType: Int = 0,
    val region: Int = 0,
    val formatLevel: Int = 0,
    val sysSlc: Int = 0,
    val usrSlc: Int = 0,
    val maxSize: Int = 0
) {
    fun toBytes(): ByteArray {
        val buf = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(cellUsage)
        buf.putInt(addrType)
        buf.putInt(binType)
        buf.putInt(region)
        buf.putInt(formatLevel)
        buf.putInt(sysSlc)
        buf.putInt(usrSlc)
        buf.putInt(maxSize)
        return buf.array()
    }
}

data class XFlashStatus(
    val status: Long,
    val msg: String
) {
    val isOk: Boolean get() = (status == MtkXFlashConstants.StatusCode.STATUS_OK)
}

data class ChipIdInfo(
    val hwCode: Int,
    val hwSubCode: Int,
    val hwVersion: Int,
    val swVersion: Int,
    val chipId: String
)

data class DaVersionInfo(
    val version: String,
    val buildDate: String
)

data class EmmcInfo(
    val type: Int,
    val blockSize: Long,
    val boot1Size: Long,
    val boot2Size: Long,
    val rpmbSize: Long,
    val gp1Size: Long,
    val gp2Size: Long,
    val gp3Size: Long,
    val gp4Size: Long,
    val userSize: Long,
    val cid: String
)

data class UfsInfo(
    val type: Int,
    val blockSize: Long,
    val lu0Size: Long,
    val lu1Size: Long,
    val lu2Size: Long,
    val rpmbSize: Long,
    val cid: String
)