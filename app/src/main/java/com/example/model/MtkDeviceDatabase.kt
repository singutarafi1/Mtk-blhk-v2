package com.example.model

data class MtkDeviceModel(
    val modelName: String,
    val chipset: String,
    val chipCode: String,
    val bromInstruction: String
)

data class MtkBrand(
    val brandName: String,
    val iconName: String,
    val models: List<MtkDeviceModel>
)

object MtkDeviceDatabase {

    val brands: List<MtkBrand> = listOf(
        MtkBrand(
            brandName = "Auto Detect (Generic)",
            iconName = "generic",
            models = listOf(
                MtkDeviceModel("Auto Detect (Universal)", "Auto (Helio/Dimensity)", "MT6765", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Helio A22 (MT6761)", "MT6761 (Helio A22)", "MT6761", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Helio P22 (MT6762)", "MT6762 (Helio P22)", "MT6762", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Helio P35 / G25 / G35 (MT6765)", "MT6765 (P35/G35)", "MT6765", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Helio G70 / G80 / G85 (MT6768)", "MT6768 (G80/G85)", "MT6768", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Helio P60 / P70 (MT6771)", "MT6771 (P60/P70)", "MT6771", "Hold Vol+ & Vol- / TestPoint"),
                MtkDeviceModel("Helio P90 / P95 (MT6779)", "MT6779 (P90/P95)", "MT6779", "Hold Vol+ & Vol- / TestPoint"),
                MtkDeviceModel("Helio G96 (MT6781)", "MT6781 (Helio G96)", "MT6781", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Helio G90T / G95 (MT6785)", "MT6785 (G90T/G95)", "MT6785", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Dimensity 700 / 720 (MT6833/MT6853)", "MT6833 (Dimensity 700)", "MT6833", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Dimensity 900 (MT6877)", "MT6877 (Dimensity 900)", "MT6877", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Dimensity 1000 / 1200 (MT6885/MT6893)", "MT6893 (Dimensity 1200)", "MT6893", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("MT6580 / MT6572 / MT6582", "MT6580 (Legacy 32-bit)", "MT6580", "Hold Vol Down -> Insert USB"),
                MtkDeviceModel("MT6735 / MT6737 / MT6739", "MT6739 (Legacy 64-bit)", "MT6739", "Hold Vol+ & Vol- -> Insert USB")
            )
        ),
        MtkBrand(
            brandName = "Xiaomi / Redmi / Poco",
            iconName = "xiaomi",
            models = listOf(
                MtkDeviceModel("Redmi 6A", "MT6761 (Helio A22)", "MT6761", "Hold Vol+ & Vol- -> Insert USB (No TestPoint)"),
                MtkDeviceModel("Redmi 6", "MT6762 (Helio P22)", "MT6762", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Redmi 9A", "MT6765 (Helio G25)", "MT6765", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Redmi 9C", "MT6765 (Helio G35)", "MT6765", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Redmi 10A", "MT6765 (Helio G25)", "MT6765", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Poco C3", "MT6765 (Helio G35)", "MT6765", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Poco C31", "MT6765 (Helio G35)", "MT6765", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Redmi 9", "MT6768 (Helio G80)", "MT6768", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Redmi Note 9", "MT6768 (Helio G85)", "MT6768", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Redmi 12C", "MT6768 (Helio G85)", "MT6768", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Poco M2", "MT6768 (Helio G80)", "MT6768", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Redmi Note 8 Pro", "MT6785 (Helio G90T)", "MT6785", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Redmi Note 10S", "MT6785 (Helio G95)", "MT6785", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Redmi Note 11S", "MT6781 (Helio G96)", "MT6781", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Redmi Note 11 Pro (4G)", "MT6781 (Helio G96)", "MT6781", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Poco M4 Pro (4G)", "MT6781 (Helio G96)", "MT6781", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Redmi Note 10 5G", "MT6833 (Dimensity 700)", "MT6833", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Poco M3 Pro 5G", "MT6833 (Dimensity 700)", "MT6833", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Xiaomi 11T", "MT6893 (Dimensity 1200)", "MT6893", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Poco F3 GT", "MT6893 (Dimensity 1200)", "MT6893", "Hold Vol+ & Vol- -> Insert USB")
            )
        ),
        MtkBrand(
            brandName = "Vivo",
            iconName = "vivo",
            models = listOf(
                MtkDeviceModel("Vivo Y81 / Y81i", "MT6762 (Helio P22)", "MT6762", "TestPoint (CMD to GND) or Vol Keys"),
                MtkDeviceModel("Vivo Y83", "MT6762 (Helio P22)", "MT6762", "TestPoint (CMD to GND)"),
                MtkDeviceModel("Vivo Y91 / Y91c / Y93", "MT6762 (Helio P22)", "MT6762", "TestPoint (CMD to GND)"),
                MtkDeviceModel("Vivo Y12 / Y15 / Y17", "MT6765 (Helio P35)", "MT6765", "TestPoint (CMD to GND)"),
                MtkDeviceModel("Vivo Y21 / Y30", "MT6765 (Helio P35)", "MT6765", "TestPoint (CMD to GND)"),
                MtkDeviceModel("Vivo Y1s / Y3s", "MT6765 (Helio P35)", "MT6765", "TestPoint (CMD to GND)"),
                MtkDeviceModel("Vivo Y19 / S1", "MT6768 (Helio P65)", "MT6768", "TestPoint (CMD to GND)"),
                MtkDeviceModel("Vivo V11i", "MT6771 (Helio P60)", "MT6771", "TestPoint (CMD to GND)"),
                MtkDeviceModel("Vivo V15", "MT6771 (Helio P70)", "MT6771", "TestPoint (CMD to GND)"),
                MtkDeviceModel("Vivo V21 5G / V21e 5G", "MT6853 (Dimensity 800U)", "MT6853", "TestPoint (CMD to GND)"),
                MtkDeviceModel("Vivo Y72 5G", "MT6833 (Dimensity 700)", "MT6833", "TestPoint (CMD to GND)")
            )
        ),
        MtkBrand(
            brandName = "Oppo",
            iconName = "oppo",
            models = listOf(
                MtkDeviceModel("Oppo A1k / A11k", "MT6762 (Helio P22)", "MT6762", "Hold Vol+ & Vol- or TestPoint"),
                MtkDeviceModel("Oppo A5s / A12", "MT6765 (Helio P35)", "MT6765", "Hold Vol+ & Vol- or TestPoint"),
                MtkDeviceModel("Oppo A15 / A31", "MT6765 (Helio P35)", "MT6765", "Hold Vol+ & Vol- or TestPoint"),
                MtkDeviceModel("Oppo A16", "MT6765 (Helio G35)", "MT6765", "TestPoint (CMD to GND)"),
                MtkDeviceModel("Oppo F9 / F11 / F11 Pro", "MT6771 (Helio P60/P70)", "MT6771", "Hold Vol+ & Vol- or TestPoint"),
                MtkDeviceModel("Oppo A9 (MTK)", "MT6771 (Helio P70)", "MT6771", "Hold Vol+ & Vol- or TestPoint"),
                MtkDeviceModel("Oppo Reno 2F", "MT6771 (Helio P70)", "MT6771", "Hold Vol+ & Vol- or TestPoint"),
                MtkDeviceModel("Oppo Reno 2Z / Reno 3", "MT6779 (Helio P90)", "MT6779", "Hold Vol+ & Vol- or TestPoint"),
                MtkDeviceModel("Oppo F17 Pro / A93", "MT6779 (Helio P95)", "MT6779", "Hold Vol+ & Vol- or TestPoint"),
                MtkDeviceModel("Oppo Reno 4 Z 5G", "MT6873 (Dimensity 800)", "MT6873", "TestPoint (CMD to GND)"),
                MtkDeviceModel("Oppo Reno 6 5G / 7 5G", "MT6877 (Dimensity 900)", "MT6877", "Hold Vol+ & Vol- or TestPoint")
            )
        ),
        MtkBrand(
            brandName = "Infinix",
            iconName = "infinix",
            models = listOf(
                MtkDeviceModel("Infinix Smart 4 / Smart 5", "MT6761 (Helio A22)", "MT6761", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Infinix Hot 8", "MT6761 (Helio A22)", "MT6761", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Infinix Hot 9", "MT6765 (Helio A25/G35)", "MT6765", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Infinix Hot 10 Play / 11 Play", "MT6765 (Helio G25/G35)", "MT6765", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Infinix Hot 10", "MT6768 (Helio G70)", "MT6768", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Infinix Note 7 / Note 10", "MT6768 (Helio G70/G85)", "MT6768", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Infinix Zero 8", "MT6785 (Helio G90T)", "MT6785", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Infinix Note 11 Pro", "MT6781 (Helio G96)", "MT6781", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Infinix Note 12 VIP", "MT6781 (Helio G96)", "MT6781", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Infinix Zero 5G", "MT6877 (Dimensity 900)", "MT6877", "Hold Vol+ & Vol- -> Insert USB")
            )
        ),
        MtkBrand(
            brandName = "Itel",
            iconName = "itel",
            models = listOf(
                MtkDeviceModel("Itel A16 / A17", "MT6580 (Legacy 32-bit)", "MT6580", "Hold Vol Down -> Insert USB"),
                MtkDeviceModel("Itel S15 / P33 Plus", "MT6580 (Legacy 32-bit)", "MT6580", "Hold Vol Down -> Insert USB"),
                MtkDeviceModel("Itel Vision 1 Pro", "MT6761 (Helio A22)", "MT6761", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Itel A48 (MTK)", "MT6761 (Helio A22)", "MT6761", "Hold Vol+ & Vol- -> Insert USB"),
                MtkDeviceModel("Itel Vision 2s", "MT6762 (Helio P22)", "MT6762", "Hold Vol+ & Vol- -> Insert USB")
            )
        )
    )

    fun getDefaultBrand(): MtkBrand = brands.first()
    fun getDefaultModel(): MtkDeviceModel = brands.first().models.first()
}
