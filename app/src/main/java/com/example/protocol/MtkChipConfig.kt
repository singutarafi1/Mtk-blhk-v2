package com.example.protocol

/**
 * MediaTek Chipset Configuration Database ported from mtkclient brom_config.py
 */
enum class DaMode(val modeCode: Int) {
    LEGACY(3),
    XFLASH(5),
    XML(6)
}

data class BlacklistEntry(
    val address: Long,
    val value: Long
)

data class ChipConfig(
    val hwCode: Int,
    val name: String,
    val description: String = "",
    val watchdog: Long? = 0x10007000L,
    val uart: Long? = 0x11002000L,
    val bromPayloadAddr: Long? = 0x100A00L,
    val daPayloadAddr: Long? = 0x201000L,
    val plPayloadAddr: Long? = 0x40200000L,
    val cqdmaBase: Long? = 0x10212000L,
    val apDmaMem: Long? = 0x11000000L + 0x1A0L,
    val sejBase: Long? = 0x1000A000L,
    val dxccBase: Long? = 0x10210000L,
    val gcpuBase: Long? = 0x10050000L,
    val ssrBase: Long? = null,
    val ssrClkBase: Long? = null,
    val efuseAddr: Long? = null,
    val meidAddr: Long? = null,
    val socidAddr: Long? = null,
    val provAddr: Long? = null,
    val miscLock: Long? = null,
    val damode: DaMode = DaMode.XFLASH,
    val dacode: Int = hwCode,
    val blacklist: List<BlacklistEntry> = emptyList(),
    val has64Bit: Boolean = false,
    val isIot: Boolean = false,
    val loader: String = ""
)

object MtkChipConfigDatabase {

    private val configs = mutableMapOf<Int, ChipConfig>()

    init {
        // MT6761 / MT6762 (Helio A20 / A22 / P22 / G25) - 0x0717
        register(
            ChipConfig(
                hwCode = 0x0717,
                name = "MT6761/MT6762",
                description = "Helio A22/P22/G25",
                watchdog = 0x10007000L,
                uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L,
                daPayloadAddr = 0x201000L,
                plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L,
                sejBase = 0x1000A000L,
                dxccBase = 0x10210000L,
                cqdmaBase = 0x10212000L,
                apDmaMem = 0x11000a80L + 0x1a0L,
                blacklist = listOf(
                    BlacklistEntry(0x102828L, 0x0L),
                    BlacklistEntry(0x00105994L, 0x0L)
                ),
                meidAddr = 0x102AF8L,
                socidAddr = 0x102b08L,
                provAddr = 0x1054F4L,
                miscLock = 0x1001a100L,
                efuseAddr = 0x11c50000L,
                damode = DaMode.XFLASH,
                dacode = 0x6761,
                loader = "mt6761_payload.bin"
            )
        )

        // MT6765 (Helio P35 / G35) - 0x0766
        register(
            ChipConfig(
                hwCode = 0x0766,
                name = "MT6765",
                description = "Helio P35/G35",
                watchdog = 0x10007000L,
                uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L,
                daPayloadAddr = 0x201000L,
                plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L,
                sejBase = 0x1000A000L,
                dxccBase = 0x10210000L,
                cqdmaBase = 0x10212000L,
                apDmaMem = 0x11000000L + 0x1a0L,
                blacklist = listOf(
                    BlacklistEntry(0x102828L, 0x0L),
                    BlacklistEntry(0x00105994L, 0x0L)
                ),
                meidAddr = 0x102AF8L,
                socidAddr = 0x102b08L,
                provAddr = 0x1054F4L,
                miscLock = 0x1001a100L,
                efuseAddr = 0x11c50000L,
                damode = DaMode.XFLASH,
                dacode = 0x6765,
                loader = "mt6765_payload.bin"
            )
        )

        // MT6768 (Helio P65 / G80 / G85) - 0x0707
        register(
            ChipConfig(
                hwCode = 0x0707,
                name = "MT6768/MT6769",
                description = "Helio G80/G85",
                watchdog = 0x10007000L,
                uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L,
                daPayloadAddr = 0x201000L,
                plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L,
                sejBase = 0x1000A000L,
                dxccBase = 0x10210000L,
                cqdmaBase = 0x10212000L,
                apDmaMem = 0x11000000L + 0x1A0L,
                blacklist = listOf(
                    BlacklistEntry(0x10282CL, 0x0L),
                    BlacklistEntry(0x00105994L, 0x0L)
                ),
                meidAddr = 0x102AF8L,
                socidAddr = 0x102b08L,
                provAddr = 0x1054F4L,
                miscLock = 0x1001a100L,
                efuseAddr = 0x11ce0000L,
                damode = DaMode.XFLASH,
                dacode = 0x6768,
                loader = "mt6768_payload.bin"
            )
        )

        // MT6771 (Helio P60 / P70) - 0x0788
        register(
            ChipConfig(
                hwCode = 0x0788,
                name = "MT6771",
                description = "Helio P60/P70",
                watchdog = 0x10007000L,
                uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L,
                daPayloadAddr = 0x201000L,
                plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L,
                sejBase = 0x1000A000L,
                dxccBase = 0x10210000L,
                cqdmaBase = 0x10212000L,
                apDmaMem = 0x11000000L + 0x158L,
                blacklist = listOf(
                    BlacklistEntry(0x00102834L, 0x0L),
                    BlacklistEntry(0x00106A60L, 0x0L)
                ),
                meidAddr = 0x102B38L,
                socidAddr = 0x102B48L,
                provAddr = 0x1065C0L,
                miscLock = 0x1001a100L,
                efuseAddr = 0x11f10000L,
                damode = DaMode.XFLASH,
                dacode = 0x6771,
                loader = "mt6771_payload.bin"
            )
        )

        // MT6779 (Helio P90) - 0x0725
        register(
            ChipConfig(
                hwCode = 0x0725,
                name = "MT6779",
                description = "Helio P90",
                watchdog = 0x10007000L,
                uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L,
                daPayloadAddr = 0x201000L,
                plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L,
                sejBase = 0x1000A000L,
                dxccBase = 0x10210000L,
                cqdmaBase = 0x10212000L,
                apDmaMem = 0x11000000L + 0x158L,
                blacklist = listOf(
                    BlacklistEntry(0x102838L, 0x0L),
                    BlacklistEntry(0x00106A60L, 0x0L)
                ),
                meidAddr = 0x102B38L,
                socidAddr = 0x102B48L,
                provAddr = 0x1065C0L,
                miscLock = 0x1001a100L,
                efuseAddr = 0x11c10000L,
                damode = DaMode.XFLASH,
                dacode = 0x6779,
                loader = "mt6779_payload.bin"
            )
        )

        // MT6781 (Helio G96) - 0x1066
        register(
            ChipConfig(
                hwCode = 0x1066,
                name = "MT6781",
                description = "Helio G96",
                watchdog = 0x10007000L,
                uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L,
                daPayloadAddr = 0x201000L,
                plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L,
                sejBase = 0x1000A000L,
                dxccBase = 0x10210000L,
                blacklist = listOf(
                    BlacklistEntry(0x10284CL, 0x106B54L)
                ),
                meidAddr = 0x102B98L,
                socidAddr = 0x102BA8L,
                efuseAddr = 0x11cb0000L,
                damode = DaMode.XFLASH,
                dacode = 0x6781,
                loader = "mt6781_payload.bin"
            )
        )

        // MT6785 (Helio G90T / G95) - 0x0813
        register(
            ChipConfig(
                hwCode = 0x0813,
                name = "MT6785",
                description = "Helio G90T/G95",
                watchdog = 0x10007000L,
                uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L,
                daPayloadAddr = 0x201000L,
                plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L,
                sejBase = 0x1000A000L,
                dxccBase = 0x10210000L,
                cqdmaBase = 0x10212000L,
                apDmaMem = 0x11000000L + 0x158L,
                blacklist = listOf(
                    BlacklistEntry(0x102838L, 0x0L),
                    BlacklistEntry(0x00106A60L, 0x0L)
                ),
                meidAddr = 0x102B38L,
                socidAddr = 0x102B48L,
                provAddr = 0x1065C0L,
                miscLock = 0x1001a100L,
                efuseAddr = 0x11c10000L,
                damode = DaMode.XFLASH,
                dacode = 0x6785,
                loader = "mt6785_payload.bin"
            )
        )

        // MT6789 (Helio G99) - 0x1208
        register(
            ChipConfig(
                hwCode = 0x1208,
                name = "MT6789",
                description = "Helio G99",
                watchdog = 0x10007000L,
                uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L,
                daPayloadAddr = 0x201000L,
                plPayloadAddr = 0x40200000L,
                dxccBase = 0x10210000L,
                sejBase = 0x1000A000L,
                blacklist = listOf(
                    BlacklistEntry(0x102d5cL, 0x0L)
                ),
                meidAddr = 0x1008ECL,
                socidAddr = 0x100934L,
                efuseAddr = 0x11C10000L,
                damode = DaMode.XML,
                dacode = 0x1208,
                loader = "mt6789_payload.bin"
            )
        )

        // MT6833 (Dimensity 700 5G) - 0x0989
        register(
            ChipConfig(
                hwCode = 0x0989,
                name = "MT6833",
                description = "Dimensity 700 5G",
                watchdog = 0x10007000L,
                uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L,
                daPayloadAddr = 0x201000L,
                plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L,
                dxccBase = 0x10210000L,
                sejBase = 0x1000A000L,
                cqdmaBase = 0x10212000L,
                apDmaMem = 0x10217a80L + 0x1a0L,
                blacklist = listOf(
                    BlacklistEntry(0x00102844L, 0x0L),
                    BlacklistEntry(0x00106B54L, 0x0L)
                ),
                meidAddr = 0x102b98L,
                socidAddr = 0x102ba8L,
                provAddr = 0x1066B4L,
                efuseAddr = 0x11c10000L,
                damode = DaMode.XFLASH,
                dacode = 0x6833,
                loader = "mt6833_payload.bin"
            )
        )

        // MT6853 (Dimensity 720 5G) - 0x0996
        register(
            ChipConfig(
                hwCode = 0x0996,
                name = "MT6853",
                description = "Dimensity 720 5G",
                watchdog = 0x10007000L,
                uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L,
                daPayloadAddr = 0x201000L,
                plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L,
                dxccBase = 0x10210000L,
                cqdmaBase = 0x10212000L,
                sejBase = 0x1000A000L,
                apDmaMem = 0x10217a80L + 0x1A0L,
                blacklist = listOf(
                    BlacklistEntry(0x10284CL, 0x0L),
                    BlacklistEntry(0x00106B60L, 0x0L)
                ),
                meidAddr = 0x102b78L,
                socidAddr = 0x102b88L,
                provAddr = 0x1066C0L,
                miscLock = 0x1001A100L,
                efuseAddr = 0x11c10000L,
                damode = DaMode.XFLASH,
                dacode = 0x6853,
                loader = "mt6853_payload.bin"
            )
        )

        // MT6877 (Dimensity 900 / 1080) - 0x0959
        register(
            ChipConfig(
                hwCode = 0x0959,
                name = "MT6877",
                description = "Dimensity 900/1080",
                watchdog = 0x10007000L,
                uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L,
                daPayloadAddr = 0x201000L,
                plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L,
                sejBase = 0x1000A000L,
                dxccBase = 0x10210000L,
                cqdmaBase = 0x10212000L,
                apDmaMem = 0x10217a80L + 0x1A0L,
                blacklist = listOf(
                    BlacklistEntry(0x102848L, 0x0L),
                    BlacklistEntry(0x00106B60L, 0x0L)
                ),
                meidAddr = 0x102b98L,
                socidAddr = 0x102ba8L,
                provAddr = 0x1066C0L,
                efuseAddr = 0x11f10000L,
                damode = DaMode.XFLASH,
                dacode = 0x6877,
                loader = "mt6877_payload.bin"
            )
        )

        // MT6885 (Dimensity 1000) - 0x0816
        register(
            ChipConfig(
                hwCode = 0x0816,
                name = "MT6885",
                description = "Dimensity 1000",
                watchdog = 0x10007000L,
                uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L,
                daPayloadAddr = 0x201000L,
                plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L,
                dxccBase = 0x10210000L,
                sejBase = 0x1000A000L,
                cqdmaBase = 0x10212000L,
                apDmaMem = 0x11000a80L + 0x1a0L,
                blacklist = listOf(
                    BlacklistEntry(0x102848L, 0x0L),
                    BlacklistEntry(0x00106B60L, 0x0L)
                ),
                meidAddr = 0x102B78L,
                socidAddr = 0x102B88L,
                provAddr = 0x1066C0L,
                miscLock = 0x1001A100L,
                efuseAddr = 0x11c10000L,
                damode = DaMode.XFLASH,
                dacode = 0x6885,
                loader = "mt6885_payload.bin"
            )
        )

        // MT6893 (Dimensity 1200) - 0x0950
        register(
            ChipConfig(
                hwCode = 0x0950,
                name = "MT6893",
                description = "Dimensity 1200",
                watchdog = 0x10007000L,
                uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L,
                daPayloadAddr = 0x201000L,
                plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L,
                dxccBase = 0x10210000L,
                sejBase = 0x1000A000L,
                cqdmaBase = 0x10212000L,
                apDmaMem = 0x11000a80L + 0x1a0L,
                blacklist = listOf(
                    BlacklistEntry(0x102848L, 0x0L),
                    BlacklistEntry(0x00106B60L, 0x0L)
                ),
                meidAddr = 0x102B98L,
                socidAddr = 0x102BA8L,
                provAddr = 0x1066C0L,
                efuseAddr = 0x11c10000L,
                damode = DaMode.XFLASH,
                dacode = 0x6893,
                loader = "mt6893_payload.bin"
            )
        )

        // MT6580 (Legacy 32-bit Quad Core) - 0x6580
        register(
            ChipConfig(
                hwCode = 0x6580,
                name = "MT6580",
                description = "MT6580 Legacy",
                watchdog = 0x10007000L,
                uart = 0x11005000L,
                bromPayloadAddr = 0x100A00L,
                daPayloadAddr = 0x201000L,
                plPayloadAddr = 0x80001000L,
                sejBase = 0x1000A000L,
                cqdmaBase = 0x1020AC00L,
                apDmaMem = 0x11000000L + 0x1A0L,
                blacklist = listOf(
                    BlacklistEntry(0x102764L, 0x0L),
                    BlacklistEntry(0x001071D4L, 0x0L)
                ),
                meidAddr = 0x1030B4L,
                miscLock = 0x10001838L,
                efuseAddr = 0x10009000L,
                damode = DaMode.LEGACY,
                dacode = 0x6580,
                loader = "mt6580_payload.bin"
            )
        )

        // MT6739 - 0x0699
        register(
            ChipConfig(
                hwCode = 0x0699,
                name = "MT6739",
                description = "Helio A22 Entry",
                watchdog = 0x10007000L,
                uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L,
                daPayloadAddr = 0x201000L,
                plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L,
                sejBase = 0x1000A000L,
                dxccBase = 0x10210000L,
                cqdmaBase = 0x10212000L,
                apDmaMem = 0x11000000L + 0x1a0L,
                blacklist = listOf(
                    BlacklistEntry(0x10282CL, 0x0L),
                    BlacklistEntry(0x001076ACL, 0x0L)
                ),
                meidAddr = 0x102AF8L,
                socidAddr = 0x102b08L,
                provAddr = 0x10720CL,
                miscLock = 0x1001a100L,
                efuseAddr = 0x11c00000L,
                damode = DaMode.XFLASH,
                dacode = 0x6739,
                loader = "mt6739_payload.bin"
            )
        )

        // MT6735 / MT6737 - 0x0321 / 0x0335
        register(
            ChipConfig(
                hwCode = 0x0321,
                name = "MT6735/MT6737",
                description = "MT6735 64-bit",
                watchdog = 0x10212000L,
                uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L,
                daPayloadAddr = 0x201000L,
                plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10216000L,
                sejBase = 0x10008000L,
                cqdmaBase = 0x10217C00L,
                apDmaMem = 0x11000000L + 0x1A0L,
                blacklist = listOf(
                    BlacklistEntry(0x00102760L, 0x0L),
                    BlacklistEntry(0x00105704L, 0x0L)
                ),
                meidAddr = 0x1030B0L,
                miscLock = 0x10001838L,
                efuseAddr = 0x11c50000L,
                damode = DaMode.LEGACY,
                dacode = 0x6735,
                loader = "mt6735_payload.bin"
            )
        )
    }

    private fun register(config: ChipConfig) {
        configs[config.hwCode] = config
    }

    fun getChipConfig(hwCode: Int): ChipConfig? {
        return configs[hwCode] ?: configs[hwCode and 0xFFFF]
    }

    fun getAllConfigs(): List<ChipConfig> = configs.values.toList()
}
