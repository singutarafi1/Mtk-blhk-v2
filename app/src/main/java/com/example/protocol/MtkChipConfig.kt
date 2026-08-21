package com.example.protocol

/**
 * MediaTek Chipset Configuration Database
 * Faithfully ported from Python mtkclient: mtkclient/config/brom_config.py
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
    val var1: Int = 0,
    val watchdog: Long = 0x10007000L,
    val uart: Long = 0x11002000L,
    val bromPayloadAddr: Long = 0x100A00L,
    val daPayloadAddr: Long = 0x201000L,
    val plPayloadAddr: Long = 0x40200000L,
    val gcpuBase: Long? = 0x10050000L,
    val sejBase: Long? = 0x1000A000L,
    val dxccBase: Long? = 0x10210000L,
    val cqdmaBase: Long? = 0x10212000L,
    val apDmaMem: Long? = 0x11000000L + 0x1A0L,
    val blacklist: List<BlacklistEntry> = emptyList(),
    val dacode: Int = hwCode,
    val damode: DaMode = DaMode.XFLASH,
    val loader: String = "",
    val miscLock: Long? = null,
    val efuseAddr: Long? = null,
    val meidAddr: Long? = null,
    val socidAddr: Long? = null,
    val provAddr: Long? = null,
    val ssrBase: Long? = null,
    val ssrClkBase: Long? = null,
    val has64Bit: Boolean = false,
    val isIot: Boolean = false
)

object MtkChipConfigDatabase {

    private val configs = mutableMapOf<Int, ChipConfig>()

    init {
        // 1. MT6765 - hwCode = 0x0766
        register(
            ChipConfig(
                hwCode = 0x0766,
                name = "MT6765",
                description = "Helio P35 / G35 (Redmi 9A, Redmi 9C, Honor Play, Galaxy A03s)",
                var1 = 0x25,
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
                    BlacklistEntry(0x102828L, 0x0L),
                    BlacklistEntry(0x00105994L, 0x0L)
                ),
                dacode = 0x6765,
                damode = DaMode.XFLASH,
                loader = "mt6765_payload.bin",
                miscLock = 0x1001A100L,
                efuseAddr = 0x11C50000L,
                meidAddr = 0x102AF8L,
                socidAddr = 0x102B08L,
                provAddr = 0x1054F4L
            )
        )

        // 2. MT6833 - hwCode = 0x0989
        register(
            ChipConfig(
                hwCode = 0x0989,
                name = "MT6833",
                description = "Dimensity 700 / 810 5G (Redmi Note 10 5G, Poco M3 Pro 5G, Oppo A55 5G)",
                var1 = 0x73,
                watchdog = 0x10007000L,
                uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L,
                daPayloadAddr = 0x201000L,
                plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L,
                sejBase = 0x1000A000L,
                dxccBase = 0x10210000L,
                cqdmaBase = 0x10212000L,
                apDmaMem = 0x10217A80L + 0x1A0L,
                blacklist = listOf(
                    BlacklistEntry(0x00102844L, 0x0L),
                    BlacklistEntry(0x00106B54L, 0x0L)
                ),
                dacode = 0x6833,
                damode = DaMode.XFLASH,
                loader = "mt6833_payload.bin",
                meidAddr = 0x102B98L,
                socidAddr = 0x102BA8L,
                provAddr = 0x1066B4L,
                efuseAddr = 0x11C10000L,
                has64Bit = true
            )
        )

        // 3. MT6768 - hwCode = 0x0707
        register(
            ChipConfig(
                hwCode = 0x0707,
                name = "MT6768",
                description = "Helio G80 / G85 (Redmi 9, Redmi Note 9, Realme 6i)",
                var1 = 0x25,
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
                dacode = 0x6768,
                damode = DaMode.XFLASH,
                loader = "mt6768_payload.bin",
                miscLock = 0x1001A100L,
                efuseAddr = 0x11CE0000L,
                meidAddr = 0x102AF8L,
                socidAddr = 0x102B08L,
                provAddr = 0x1054F4L
            )
        )

        // 4. MT6785 - hwCode = 0x0813
        register(
            ChipConfig(
                hwCode = 0x0813,
                name = "MT6785",
                description = "Helio G90 / G90T / G95 (Redmi Note 8 Pro, Realme 6, Realme 7)",
                var1 = 0x0A,
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
                dacode = 0x6785,
                damode = DaMode.XFLASH,
                loader = "mt6785_payload.bin",
                miscLock = 0x1001A100L,
                efuseAddr = 0x11C10000L,
                meidAddr = 0x102B38L,
                socidAddr = 0x102B48L,
                provAddr = 0x1065C0L,
                has64Bit = true
            )
        )

        // 5. MT6771 - hwCode = 0x0788
        register(
            ChipConfig(
                hwCode = 0x0788,
                name = "MT6771",
                description = "Helio P60 / P70 (Oppo F7, Oppo F9, Realme 1, Realme 3)",
                var1 = 0x0A,
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
                dacode = 0x6771,
                damode = DaMode.XFLASH,
                loader = "mt6771_payload.bin",
                miscLock = 0x1001A100L,
                efuseAddr = 0x11F10000L,
                meidAddr = 0x102B38L,
                socidAddr = 0x102B48L,
                provAddr = 0x1065C0L
            )
        )

        // 6. MT6739 - hwCode = 0x0699
        register(
            ChipConfig(
                hwCode = 0x0699,
                name = "MT6739",
                description = "Quad-Core Entry SoC",
                var1 = 0xB4,
                watchdog = 0x10007000L,
                uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L,
                daPayloadAddr = 0x201000L,
                plPayloadAddr = 0x40200000L,
                cqdmaBase = 0x10212000L,
                apDmaMem = 0x11000000L + 0x1A0L,
                blacklist = listOf(
                    BlacklistEntry(0x10282CL, 0x0L),
                    BlacklistEntry(0x001076ACL, 0x0L)
                ),
                dacode = 0x6739,
                damode = DaMode.XFLASH,
                loader = "mt6739_payload.bin",
                miscLock = 0x1001A100L,
                efuseAddr = 0x11C00000L,
                meidAddr = 0x102AF8L,
                socidAddr = 0x102B08L,
                provAddr = 0x10720CL
            )
        )

        // 7. MT6877 - hwCode = 0x0959
        register(
            ChipConfig(
                hwCode = 0x0959,
                name = "MT6877",
                description = "Dimensity 900 / 920 5G (Redmi Note 11 Pro+ 5G)",
                var1 = 0x0A,
                watchdog = 0x10007000L,
                uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L,
                daPayloadAddr = 0x201000L,
                plPayloadAddr = 0x40200000L,
                cqdmaBase = 0x10212000L,
                apDmaMem = 0x10217A80L + 0x1A0L,
                blacklist = listOf(
                    BlacklistEntry(0x102848L, 0x0L),
                    BlacklistEntry(0x00106B60L, 0x0L)
                ),
                dacode = 0x6877,
                damode = DaMode.XFLASH,
                loader = "mt6877_payload.bin",
                efuseAddr = 0x11F10000L,
                meidAddr = 0x102B98L,
                socidAddr = 0x102BA8L,
                provAddr = 0x1066C0L,
                has64Bit = true
            )
        )

        // 8. MT6893 - hwCode = 0x0950
        register(
            ChipConfig(
                hwCode = 0x0950,
                name = "MT6893",
                description = "Dimensity 1200 / 1100 5G (Poco F3 GT, Redmi K40 Gaming)",
                var1 = 0x0A,
                watchdog = 0x10007000L,
                uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L,
                daPayloadAddr = 0x201000L,
                plPayloadAddr = 0x40200000L,
                cqdmaBase = 0x10212000L,
                apDmaMem = 0x11000A80L + 0x1A0L,
                blacklist = listOf(
                    BlacklistEntry(0x102848L, 0x0L),
                    BlacklistEntry(0x00106B60L, 0x0L)
                ),
                dacode = 0x6893,
                damode = DaMode.XFLASH,
                loader = "mt6893_payload.bin",
                efuseAddr = 0x11C10000L,
                meidAddr = 0x102B98L,
                socidAddr = 0x102BA8L,
                provAddr = 0x1066C0L,
                has64Bit = true
            )
        )

        // 9. MT6885 - hwCode = 0x0816
        register(
            ChipConfig(
                hwCode = 0x0816,
                name = "MT6885",
                description = "Dimensity 1000L / 1000+ 5G",
                var1 = 0x0A,
                watchdog = 0x10007000L,
                uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L,
                daPayloadAddr = 0x201000L,
                plPayloadAddr = 0x40200000L,
                cqdmaBase = 0x10212000L,
                apDmaMem = 0x11000A80L + 0x1A0L,
                blacklist = listOf(
                    BlacklistEntry(0x102848L, 0x0L),
                    BlacklistEntry(0x00106B60L, 0x0L)
                ),
                dacode = 0x6885,
                damode = DaMode.XFLASH,
                loader = "mt6885_payload.bin",
                miscLock = 0x1001A100L,
                efuseAddr = 0x11C10000L,
                meidAddr = 0x102B78L,
                socidAddr = 0x102B88L,
                provAddr = 0x1066C0L,
                has64Bit = true
            )
        )

        // 10. MT6580 - hwCode = 0x6580
        register(
            ChipConfig(
                hwCode = 0x6580,
                name = "MT6580",
                description = "Quad-Core 32-bit Legacy SoC",
                var1 = 0xAC,
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
                dacode = 0x6580,
                damode = DaMode.LEGACY,
                loader = "mt6580_payload.bin",
                miscLock = 0x10001838L,
                efuseAddr = 0x10009000L,
                meidAddr = 0x1030B4L
            )
        )

        // 11. MT6761/MT6762 - hwCode = 0x0717
        register(
            ChipConfig(
                hwCode = 0x0717,
                name = "MT6761/MT6762",
                description = "Helio A22 / P22 / G25",
                var1 = 0x25,
                watchdog = 0x10007000L,
                uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L,
                daPayloadAddr = 0x201000L,
                plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L,
                sejBase = 0x1000A000L,
                dxccBase = 0x10210000L,
                cqdmaBase = 0x10212000L,
                apDmaMem = 0x11000A80L + 0x1A0L,
                blacklist = listOf(
                    BlacklistEntry(0x102828L, 0x0L),
                    BlacklistEntry(0x00105994L, 0x0L)
                ),
                dacode = 0x6761,
                damode = DaMode.XFLASH,
                loader = "mt6761_payload.bin",
                miscLock = 0x1001A100L,
                efuseAddr = 0x11C50000L,
                meidAddr = 0x102AF8L,
                socidAddr = 0x102B08L,
                provAddr = 0x1054F4L
            )
        )
    }

    private fun register(config: ChipConfig) {
        configs[config.hwCode] = config
    }

    fun findConfig(hwCode: Int): ChipConfig? {
        return configs[hwCode] ?: configs[hwCode and 0xFFFF]
    }

    fun findConfigByName(name: String): ChipConfig? {
        val cleanName = name.replace("MT", "").replace("/", "").lowercase()
        return configs.values.firstOrNull {
            val cfgName = it.name.replace("MT", "").replace("/", "").lowercase()
            cfgName.contains(cleanName) || cleanName.contains(cfgName)
        }
    }

    fun getAllConfigs(): List<ChipConfig> = configs.values.toList()
}
