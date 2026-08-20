package com.example.protocol

/**
 * MediaTek BootROM Stage 1 Payload Target Catalog
 * Faithfully ported from mtkclient stage1 payload header definitions (.h files)
 */

data class MtkStage1Target(
    val socName: String,
    val sendUsbResponse: Long,
    val usbdlPtr: Long,
    val mode: Int,
    val secReg: Long,
    val secReg2: Long,
    val secOffset: Long,
    val bladdr: Long,
    val bladdr2: Long,
    val uartReg0: Long,
    val uartReg1: Long,
    val cmdHandler: Long,
    val payloadFileName: String = "${socName}_payload.bin"
)

object MtkStage1TargetCatalog {

    val TARGETS: Map<String, MtkStage1Target> = listOf(
        // 1. mt2601
        MtkStage1Target(
            socName = "mt2601",
            sendUsbResponse = 0x406AC9L,
            usbdlPtr = 0x40BA68L,
            mode = 0,
            secReg = 0x11141E80L,
            secReg2 = 0x0L,
            secOffset = 0x40L,
            bladdr = 0x11141F0CL,
            bladdr2 = 0x11144BC4L,
            uartReg0 = 0x11005014L,
            uartReg1 = 0x11005000L,
            cmdHandler = 0x40C5AFL
        ),
        // 2. mt6261
        MtkStage1Target(
            socName = "mt6261",
            sendUsbResponse = 0x700016B1L,
            usbdlPtr = 0x700058ECL,
            mode = 0,
            secReg = 0x700041E4L,
            secReg2 = 0x0L,
            secOffset = 0x0L,
            bladdr = 0x182800C0L,
            bladdr2 = 0x0L,
            uartReg0 = 0xA0080014L,
            uartReg1 = 0xA0080000L,
            cmdHandler = 0x700061F6L
        ),
        // 3. mt6572
        MtkStage1Target(
            socName = "mt6572",
            sendUsbResponse = 0x406AC9L,
            usbdlPtr = 0x40BA68L,
            mode = 0,
            secReg = 0x11141E80L,
            secReg2 = 0x0L,
            secOffset = 0x40L,
            bladdr = 0x11141F0CL,
            bladdr2 = 0x11144BC4L,
            uartReg0 = 0x11005014L,
            uartReg1 = 0x11005000L,
            cmdHandler = 0x40C5AFL
        ),
        // 4. mt6575
        MtkStage1Target(
            socName = "mt6575",
            sendUsbResponse = 0xFFFF4E2BL,
            usbdlPtr = 0xFFFFA0A0L,
            mode = 0,
            secReg = 0xF0002538L,
            secReg2 = 0x0L,
            secOffset = 0x40L,
            bladdr = 0xF00025C4L,
            bladdr2 = 0xF00051E4L,
            uartReg0 = 0xC1009014L,
            uartReg1 = 0xC1009000L,
            cmdHandler = 0xFFFFAD5DL
        ),
        // 5. mt6580
        MtkStage1Target(
            socName = "mt6580",
            sendUsbResponse = 0x62E5L,
            usbdlPtr = 0xB60CL,
            mode = 0,
            secReg = 0x1026D8L,
            secReg2 = 0x0L,
            secOffset = 0x40L,
            bladdr = 0x102764L,
            bladdr2 = 0x1071D4L,
            uartReg0 = 0x11005014L,
            uartReg1 = 0x11005000L,
            cmdHandler = 0xC113L
        ),
        // 6. mt6582
        MtkStage1Target(
            socName = "mt6582",
            sendUsbResponse = 0x568DL,
            usbdlPtr = 0xA5FCL,
            mode = 0,
            secReg = 0x1026FCL,
            secReg2 = 0x0L,
            secOffset = 0x40L,
            bladdr = 0x102788L,
            bladdr2 = 0x105BE4L,
            uartReg0 = 0x11002014L,
            uartReg1 = 0x11002000L,
            cmdHandler = 0xB2E7L
        ),
        // 7. mt6592
        MtkStage1Target(
            socName = "mt6592",
            sendUsbResponse = 0x535DL,
            usbdlPtr = 0xA564L,
            mode = 0,
            secReg = 0x1026D8L,
            secReg2 = 0x0L,
            secOffset = 0x40L,
            bladdr = 0x102764L,
            bladdr2 = 0x105BF0L,
            uartReg0 = 0x11002014L,
            uartReg1 = 0x11002000L,
            cmdHandler = 0xB09FL
        ),
        // 8. mt6595
        MtkStage1Target(
            socName = "mt6595",
            sendUsbResponse = 0x5E2BL,
            usbdlPtr = 0xB218L,
            mode = 0,
            secReg = 0x1026DCL,
            secReg2 = 0x0L,
            secOffset = 0x40L,
            bladdr = 0x102768L,
            bladdr2 = 0x106C88L,
            uartReg0 = 0x11002014L,
            uartReg1 = 0x11002000L,
            cmdHandler = 0xBD53L
        ),
        // 9. mt6735
        MtkStage1Target(
            socName = "mt6735",
            sendUsbResponse = 0x4293L,
            usbdlPtr = 0x95F8L,
            mode = 0,
            secReg = 0x1026D4L,
            secReg2 = 0x0L,
            secOffset = 0x40L,
            bladdr = 0x102760L,
            bladdr2 = 0x105704L,
            uartReg0 = 0x11002014L,
            uartReg1 = 0x11002000L,
            cmdHandler = 0xA17FL
        ),
        // 10. mt6737
        MtkStage1Target(
            socName = "mt6737",
            sendUsbResponse = 0x42A3L,
            usbdlPtr = 0x9608L,
            mode = 0,
            secReg = 0x1026D4L,
            secReg2 = 0x0L,
            secOffset = 0x40L,
            bladdr = 0x102760L,
            bladdr2 = 0x105704L,
            uartReg0 = 0x11002014L,
            uartReg1 = 0x11002000L,
            cmdHandler = 0xA18FL
        ),
        // 11. mt6739
        MtkStage1Target(
            socName = "mt6739",
            sendUsbResponse = 0x508BL,
            usbdlPtr = 0xDF1CL,
            mode = 1,
            secReg = 0x102A8CL,
            secReg2 = 0x1027A8L,
            secOffset = 0x28L,
            bladdr = 0x10282CL,
            bladdr2 = 0x1076ACL,
            uartReg0 = 0x11002014L,
            uartReg1 = 0x11002000L,
            cmdHandler = 0xEC49L
        ),
        // 12. mt6752
        MtkStage1Target(
            socName = "mt6752",
            sendUsbResponse = 0x450FL,
            usbdlPtr = 0x990CL,
            mode = 0,
            secReg = 0x1026D8L,
            secReg2 = 0x0L,
            secOffset = 0x40L,
            bladdr = 0x102764L,
            bladdr2 = 0x105704L,
            uartReg0 = 0x11002014L,
            uartReg1 = 0x11002000L,
            cmdHandler = 0xA493L
        ),
        // 13. mt6753
        MtkStage1Target(
            socName = "mt6753",
            sendUsbResponse = 0x42A3L,
            usbdlPtr = 0x9668L,
            mode = 0,
            secReg = 0x1026D4L,
            secReg2 = 0x0L,
            secOffset = 0x40L,
            bladdr = 0x102760L,
            bladdr2 = 0x105704L,
            uartReg0 = 0x11002014L,
            uartReg1 = 0x11002000L,
            cmdHandler = 0xA1EFL
        ),
        // 14. mt6755
        MtkStage1Target(
            socName = "mt6755",
            sendUsbResponse = 0x449FL,
            usbdlPtr = 0x9A6CL,
            mode = 0,
            secReg = 0x1026DCL,
            secReg2 = 0x0L,
            secOffset = 0x40L,
            bladdr = 0x10276CL,
            bladdr2 = 0x105704L,
            uartReg0 = 0x11002014L,
            uartReg1 = 0x11002000L,
            cmdHandler = 0xA5FFL
        ),
        // 15. mt6757
        MtkStage1Target(
            socName = "mt6757",
            sendUsbResponse = 0x455FL,
            usbdlPtr = 0x9C2CL,
            mode = 0,
            secReg = 0x1026E4L,
            secReg2 = 0x0L,
            secOffset = 0x40L,
            bladdr = 0x102774L,
            bladdr2 = 0x105704L,
            uartReg0 = 0x11002014L,
            uartReg1 = 0x11002000L,
            cmdHandler = 0xA8FBL
        ),
        // 16. mt6758
        MtkStage1Target(
            socName = "mt6758",
            sendUsbResponse = 0x4937L,
            usbdlPtr = 0xD860L,
            mode = 1,
            secReg = 0x102B8CL,
            secReg2 = 0x1027ACL,
            secOffset = 0x28L,
            bladdr = 0x102830L,
            bladdr2 = 0x106A60L,
            uartReg0 = 0x11020014L,
            uartReg1 = 0x11020000L,
            cmdHandler = 0xE58DL
        ),
        // 17. mt6761
        MtkStage1Target(
            socName = "mt6761",
            sendUsbResponse = 0x2CDFL,
            usbdlPtr = 0xBC8CL,
            mode = 1,
            secReg = 0x102A8CL,
            secReg2 = 0x1027A4L,
            secOffset = 0x28L,
            bladdr = 0x102828L,
            bladdr2 = 0x105994L,
            uartReg0 = 0x11002014L,
            uartReg1 = 0x11002000L,
            cmdHandler = 0xC9B9L
        ),
        // 18. mt6763
        MtkStage1Target(
            socName = "mt6763",
            sendUsbResponse = 0x4877L,
            usbdlPtr = 0xD66CL,
            mode = 1,
            secReg = 0x102B0CL,
            secReg2 = 0x1027B0L,
            secOffset = 0x28L,
            bladdr = 0x102834L,
            bladdr2 = 0x106CA4L,
            uartReg0 = 0x11002014L,
            uartReg1 = 0x11002000L,
            cmdHandler = 0xE383L
        ),
        // 19. mt6765
        MtkStage1Target(
            socName = "mt6765",
            sendUsbResponse = 0x2D2BL,
            usbdlPtr = 0xBDC0L,
            mode = 1,
            secReg = 0x102A8CL,
            secReg2 = 0x1027A4L,
            secOffset = 0x28L,
            bladdr = 0x102828L,
            bladdr2 = 0x105994L,
            uartReg0 = 0x11002014L,
            uartReg1 = 0x11002000L,
            cmdHandler = 0xCAEDL
        ),
        // 20. mt6768
        MtkStage1Target(
            socName = "mt6768",
            sendUsbResponse = 0x2C2FL,
            usbdlPtr = 0xC190L,
            mode = 1,
            secReg = 0x102A8CL,
            secReg2 = 0x1027A4L,
            secOffset = 0x28L,
            bladdr = 0x10282CL,
            bladdr2 = 0x105994L,
            uartReg0 = 0x11002014L,
            uartReg1 = 0x11002000L,
            cmdHandler = 0xCF15L
        ),
        // 21. mt6771
        MtkStage1Target(
            socName = "mt6771",
            sendUsbResponse = 0x4DAFL,
            usbdlPtr = 0xDEBCL,
            mode = 1,
            secReg = 0x102ACCL,
            secReg2 = 0x1027B0L,
            secOffset = 0x28L,
            bladdr = 0x102834L,
            bladdr2 = 0x106A60L,
            uartReg0 = 0x11002014L,
            uartReg1 = 0x11002000L,
            cmdHandler = 0xEBE9L
        ),
        // 22. mt6779
        MtkStage1Target(
            socName = "mt6779",
            sendUsbResponse = 0x4CDBL,
            usbdlPtr = 0xE04CL,
            mode = 1,
            secReg = 0x102ACCL,
            secReg2 = 0x1027B0L,
            secOffset = 0x28L,
            bladdr = 0x102838L,
            bladdr2 = 0x106A60L,
            uartReg0 = 0x11002014L,
            uartReg1 = 0x11002000L,
            cmdHandler = 0xED6DL
        ),
        // 23. mt6781 (socName = "brom_1")
        MtkStage1Target(
            socName = "brom_1",
            sendUsbResponse = 0x4AA7L,
            usbdlPtr = 0xE5D8L,
            mode = 1,
            secReg = 0x102B0CL,
            secReg2 = 0x1027BCL,
            secOffset = 0x28L,
            bladdr = 0x10284CL,
            bladdr2 = 0x106B54L,
            uartReg0 = 0x11002014L,
            uartReg1 = 0x11002000L,
            cmdHandler = 0xF3C1L
        ),
        // 24. mt6785
        MtkStage1Target(
            socName = "mt6785",
            sendUsbResponse = 0x4C8FL,
            usbdlPtr = 0xE2A4L,
            mode = 1,
            secReg = 0x102ACCL,
            secReg2 = 0x1027B0L,
            secOffset = 0x28L,
            bladdr = 0x102838L,
            bladdr2 = 0x106A60L,
            uartReg0 = 0x11002014L,
            uartReg1 = 0x11002000L,
            cmdHandler = 0xF029L
        ),
        // 25. mt6795
        MtkStage1Target(
            socName = "mt6795",
            sendUsbResponse = 0x4347L,
            usbdlPtr = 0x978CL,
            mode = 0,
            secReg = 0x1026D8L,
            secReg2 = 0x0L,
            secOffset = 0x40L,
            bladdr = 0x102764L,
            bladdr2 = 0x105704L,
            uartReg0 = 0x11002014L,
            uartReg1 = 0x11002000L,
            cmdHandler = 0xA313L
        ),
        // 26. mt6797
        MtkStage1Target(
            socName = "mt6797",
            sendUsbResponse = 0x4807L,
            usbdlPtr = 0x9EACL,
            mode = 0,
            secReg = 0x1026DCL,
            secReg2 = 0x0L,
            secOffset = 0x40L,
            bladdr = 0x10276CL,
            bladdr2 = 0x105704L,
            uartReg0 = 0x11002014L,
            uartReg1 = 0x11002000L,
            cmdHandler = 0xAA3FL
        ),
        // 27. mt6799
        MtkStage1Target(
            socName = "mt6799",
            sendUsbResponse = 0x66AFL,
            usbdlPtr = 0xF5ACL,
            mode = 1,
            secReg = 0x10334CL,
            secReg2 = 0x1027ECL,
            secOffset = 0x28L,
            bladdr = 0x102870L,
            bladdr2 = 0x107070L,
            uartReg0 = 0x11020014L,
            uartReg1 = 0x11020000L,
            cmdHandler = 0x102C3L
        ),
        // 28. mt6833
        MtkStage1Target(
            socName = "mt6833",
            sendUsbResponse = 0x48F3L,
            usbdlPtr = 0xDFE0L,
            mode = 1,
            secReg = 0x102B0CL,
            secReg2 = 0x1027BCL,
            secOffset = 0x28L,
            bladdr = 0x102844L,
            bladdr2 = 0x106B54L,
            uartReg0 = 0x11002014L,
            uartReg1 = 0x11002000L,
            cmdHandler = 0xEDADL
        ),
        // 29. mt6853
        MtkStage1Target(
            socName = "mt6853",
            sendUsbResponse = 0x538FL,
            usbdlPtr = 0xEA64L,
            mode = 1,
            secReg = 0x102B0CL,
            secReg2 = 0x1027C4L,
            secOffset = 0x28L,
            bladdr = 0x10284CL,
            bladdr2 = 0x106B60L,
            uartReg0 = 0x11002014L,
            uartReg1 = 0x11002000L,
            cmdHandler = 0xF831L
        ),
        // 30. mt6873
        MtkStage1Target(
            socName = "mt6873",
            sendUsbResponse = 0x53AFL,
            usbdlPtr = 0xEA78L,
            mode = 1,
            secReg = 0x102B0CL,
            secReg2 = 0x1027C4L,
            secOffset = 0x28L,
            bladdr = 0x10284CL,
            bladdr2 = 0x106B60L,
            uartReg0 = 0x11002014L,
            uartReg1 = 0x11002000L,
            cmdHandler = 0xF7FDL
        ),
        // 31. mt6877
        MtkStage1Target(
            socName = "mt6877",
            sendUsbResponse = 0x5187L,
            usbdlPtr = 0xE8D0L,
            mode = 1,
            secReg = 0x102B0CL,
            secReg2 = 0x1027C0L,
            secOffset = 0x28L,
            bladdr = 0x102848L,
            bladdr2 = 0x106B60L,
            uartReg0 = 0x11002014L,
            uartReg1 = 0x11002000L,
            cmdHandler = 0xF69DL
        ),
        // 32. mt6885
        MtkStage1Target(
            socName = "mt6885",
            sendUsbResponse = 0x505BL,
            usbdlPtr = 0xE6FCL,
            mode = 1,
            secReg = 0x102B0CL,
            secReg2 = 0x1027C0L,
            secOffset = 0x28L,
            bladdr = 0x102848L,
            bladdr2 = 0x106B60L,
            uartReg0 = 0x11002014L,
            uartReg1 = 0x11002000L,
            cmdHandler = 0xF481L
        ),
        // 33. mt6893
        MtkStage1Target(
            socName = "mt6893",
            sendUsbResponse = 0x501FL,
            usbdlPtr = 0xE79CL,
            mode = 1,
            secReg = 0x102B0CL,
            secReg2 = 0x1027C0L,
            secOffset = 0x28L,
            bladdr = 0x102848L,
            bladdr2 = 0x106B60L,
            uartReg0 = 0x11002014L,
            uartReg1 = 0x11002000L,
            cmdHandler = 0xF569L
        ),
        // 34. mt8127
        MtkStage1Target(
            socName = "mt8127",
            sendUsbResponse = 0x62A1L,
            usbdlPtr = 0xB2B8L,
            mode = 0,
            secReg = 0x1027E4L,
            secReg2 = 0x0L,
            secOffset = 0x40L,
            bladdr = 0x102870L,
            bladdr2 = 0x106C7CL,
            uartReg0 = 0x11002014L,
            uartReg1 = 0x11002000L,
            cmdHandler = 0xBDF3L
        ),
        // 35. mt8163
        MtkStage1Target(
            socName = "mt8163",
            sendUsbResponse = 0x6D6FL,
            usbdlPtr = 0xC12CL,
            mode = 0,
            secReg = 0x1027DCL,
            secReg2 = 0x0L,
            secOffset = 0x40L,
            bladdr = 0x102868L,
            bladdr2 = 0x1072DCL,
            uartReg0 = 0x11002014L,
            uartReg1 = 0x11002000L,
            cmdHandler = 0xCCB3L
        ),
        // 36. mt8167
        MtkStage1Target(
            socName = "mt8167",
            sendUsbResponse = 0x6C7DL,
            usbdlPtr = 0xD2E4L,
            mode = 1,
            secReg = 0x10340CL,
            secReg2 = 0x1028E4L,
            secOffset = 0x28L,
            bladdr = 0x102968L,
            bladdr2 = 0x107954L,
            uartReg0 = 0x11005014L,
            uartReg1 = 0x11005000L,
            cmdHandler = 0xDFF7L
        ),
        // 37. mt8168
        MtkStage1Target(
            socName = "mt8168",
            sendUsbResponse = 0xD0F3L,
            usbdlPtr = 0x13834L,
            mode = 0,
            secReg = 0x1063CCL,
            secReg2 = 0x0L,
            secOffset = 0x8L,
            bladdr = 0x10303CL,
            bladdr2 = 0x10A540L,
            uartReg0 = 0x11002014L,
            uartReg1 = 0x11002000L,
            cmdHandler = 0x1436FL
        ),
        // 38. mt8173
        MtkStage1Target(
            socName = "mt8173",
            sendUsbResponse = 0x4C5FL,
            usbdlPtr = 0xA0E4L,
            mode = 0,
            secReg = 0x1226E8L,
            secReg2 = 0x0L,
            secOffset = 0x40L,
            bladdr = 0x122774L,
            bladdr2 = 0x125904L,
            uartReg0 = 0x11002014L,
            uartReg1 = 0x11002000L,
            cmdHandler = 0xAC6BL
        ),
        // 39. mt8176
        MtkStage1Target(
            socName = "mt8176",
            sendUsbResponse = 0x4C5FL,
            usbdlPtr = 0xA0E4L,
            mode = 0,
            secReg = 0x1226E8L,
            secReg2 = 0x0L,
            secOffset = 0x40L,
            bladdr = 0x122774L,
            bladdr2 = 0x125904L,
            uartReg0 = 0x11002014L,
            uartReg1 = 0x11002000L,
            cmdHandler = 0xAC6BL
        ),
        // 40. mt8512
        MtkStage1Target(
            socName = "mt8512",
            sendUsbResponse = 0x6697L,
            usbdlPtr = 0xCC44L,
            mode = 1,
            secReg = 0x1045CCL,
            secReg2 = 0x104178L,
            secOffset = 0x28L,
            bladdr = 0x1041E4L,
            bladdr2 = 0x10AA84L,
            uartReg0 = 0x11002014L,
            uartReg1 = 0x11002000L,
            cmdHandler = 0xD7ABL
        ),
        // 41. mt8590
        MtkStage1Target(
            socName = "mt8590",
            sendUsbResponse = 0x6BC9L,
            usbdlPtr = 0xBBE4L,
            mode = 0,
            secReg = 0x1027E4L,
            secReg2 = 0x0L,
            secOffset = 0x40L,
            bladdr = 0x102870L,
            bladdr2 = 0x106C7CL,
            uartReg0 = 0x11002014L,
            uartReg1 = 0x11002000L,
            cmdHandler = 0xC71FL
        ),
        // 42. mt8695
        MtkStage1Target(
            socName = "mt8695",
            sendUsbResponse = 0x55BBL,
            usbdlPtr = 0xBEECL,
            mode = 0,
            secReg = 0x102FBCL,
            secReg2 = 0x0L,
            secOffset = 0x40L,
            bladdr = 0x103048L,
            bladdr2 = 0x106EC4L,
            uartReg0 = 0x11003014L,
            uartReg1 = 0x11003000L,
            cmdHandler = 0xCAA7L
        )
    ).associateBy { it.socName.lowercase() }

    fun findTarget(chipCode: String): MtkStage1Target? {
        val clean = chipCode.lowercase().replace("mt", "").replace("helio", "").trim()
        return TARGETS["mt$clean"] ?: TARGETS[chipCode.lowercase()]
    }

    fun getAllSocNames(): List<String> = TARGETS.keys.toList()
}
