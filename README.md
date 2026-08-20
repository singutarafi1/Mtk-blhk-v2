# 📱 MTK Bridge & Flasher Tool (Android Native)

[![Platform](https://img.shields.io/badge/Platform-Android%20OTG-green.svg)](https://developer.android.com)
[![Protocol](https://img.shields.io/badge/Protocol-MediaTek%20BROM%20%2F%20DA-blue.svg)](https://mediatek.com)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-purple.svg)](https://developer.android.com/jetpack/compose)
[![Architecture](https://img.shields.io/badge/Architecture-Kotlin%20%7C%20Coroutines%20%7C%20Flow-orange.svg)](https://kotlinlang.org)

**MTK Bridge & Flasher Tool** သည် ကွန်ပျူတာ (PC) မလိုဘဲ Android ဖုန်းတစ်လုံးမှ အခြား MediaTek (MTK) ဖုန်းတစ်လုံးသို့ **USB-OTG Cable** အသုံးပြု၍ တိုက်ရိုက် Flash ရေးသားခြင်း၊ Partition Backup ထုတ်ယူခြင်း၊ Factory Reset ချခြင်း၊ FRP Lock ဖြုတ်ခြင်းနှင့် Bootloader Unlock လုပ်ဆောင်နိုင်သော ပရော်ဖက်ရှင်နယ် **Mobile-to-Mobile GSM Service Tool** ဖြစ်ပါသည်။

---

## ✨ အဓိက လုပ်ဆောင်ချက်များ (Key Features)

### ⚡ 1. Direct MTK BROM Protocol (No PC Required)
- **USB-OTG Direct Low-Level Communication**: Android ၏ `android.hardware.usb` Bulk Endpoints မှတစ်ဆင့် MediaTek Bootrom နှင့် တိုက်ရိုက် အပြန်အလှန် ချိတ်ဆက်ခြင်း။
- **BROM Handshake Sync**: Byte-by-Byte Inverted Echo Handshake (0xA0 -> 0x5F, 0x0A -> 0xF5, 0x50 -> 0xAF, 0x05 -> 0xFA)။
- **Hardware Register Inspection**: Hardware Code (0xA1), Subcode (0xA2), HW Version (0xA3), SW Version (0xA4), MEID (0xE1), SOC ID (0xE2) ဖတ်ရှုခြင်း။
- **SLA / DAA Security Bypass Matrix**: Kamakiri SLA / DAA / SBC Watchdog USB Control Transfer Payload စနစ်။

### 💾 2. Firmware Flashing & GPT Partition Engine
- **Scatter File Parser**: Android MediaTek Scatter File (`MT67xx_Android_scatter.txt`) အား အပြည့်အဝ ဖတ်ရှုစစ်ဆေးခြင်း။
- **Real Storage GPT Table**: ဖုန်း၏ LBA 1..33 GUID Partition Table မှ Partition List ကို အလိုအလျောက် Live Parse ပြုလုပ်ခြင်း။
- **SHA-256 Checksum Verification**: ရေးသားပြီးတိုင်း Integrity ၁၀၀% ပြည့်မီမှု ရှိမရှိ Post-Write Verification ပြုလုပ်ခြင်း။
- **Safe NVRAM Protection**: Firmware မရေးမီ `nvram`, `nvdata`, `protect1`, `protect2`, `secro` Partition များကို Auto-Backup ပြုလုပ်ပေးခြင်း။

### 🛠️ 3. One-Click GSM Service Functions
- **Erase FRP**: Google Account Lock ကျနေသော FRP Partition ကို တိုက်ရိုက် Format / Wipe ပြုလုပ်ခြင်း။
- **Unlock Bootloader**: `seccfg` partition ထဲသို့ `SCFG` magic header (0x47464353) နှင့် Unlock status code ရေးသားပြီး ချက်ချင်း Bootloader Unlock လုပ်ခြင်း။
- **Relock Bootloader**: Device Security ကို မူလ Locked အခြေအနေသို့ ပြန်လည်ရောက်ရှိစေခြင်း။
- **Disable Mi Account**: Xiaomi ဖုန်းများအတွက် Cloud Lock authentication status ကို persist partition မှတစ်ဆင့် Reset ချခြင်း။
- **Factory Reset (Format Data)**: Userdata, Cache နှင့် Metadata များကို Clean Wipe ပြုလုပ်ခြင်း။
- **Hardware Memory Test**: RAM Data Pattern Test (0x55AA55AA) နှင့် eMMC / UFS CID/CSD register health check။

### 🎨 4. Modern PC-Class GSM Tool Interface
- **UnlockTool / Hydra Style Dark Interface**: Jetpack Compose Material 3 ဖြင့် ဖန်တီးထားသော ပရော်ဖက်ရှင်နယ် UI။
- **Live Terminal Log**: စက်၏ လုပ်ဆောင်ချက်အဆင့်ဆင့်နှင့် Debug Log များကို စက္ကန့်နှင့်အမျှ တိုက်ရိုက်ကြည့်ရှုနိုင်ခြင်း။
- **Dynamic Start / Stop Button**: လိုအပ်ပါက လုပ်ဆောင်ချက်ကို ချက်ချင်း ရပ်တန့်နိုင်သော Instant Cancel/Abort Control။
- **Audio Feedback**: USB Plug in/out၊ Operation Start၊ Stop နှင့် Done အသံ (Sound Effects) စနစ် ပါဝင်ခြင်း။
- **Gemini AI Diagnostic Advisor**: Error တက်ပါက AI ဖြင့် ပြဿနာရှာဖွေပြီး ဖြေရှင်းနည်းလမ်းညွှန်ပေးခြင်း။

---

## 📱 အသုံးပြုပုံ အဆင့်ဆင့် (How to Use)

### လိုအပ်ချက်များ:
1. Master Android Device (OTG Support ပါဝင်သော ဖုန်း)
2. USB-OTG Adapter သို့မဟုတ် Type-C to Type-C Cable
3. Target MediaTek Phone (Flash သို့မဟုတ် Unlock ပြုလုပ်မည့်ဖုန်း)

### အသုံးပြုနည်း:
1. **Connect Cable**: Master ဖုန်းတွင် OTG ချိတ်ပြီး USB ကြိုးကို Target ဖုန်းနှင့် ချိတ်ဆက်ရန် အသင့်ပြင်ပါ။
2. **Select Brand & Model**: App တွင် မိမိ ပြုလုပ်လိုသော ဖုန်းအမျိုးအစား (ဥပမာ - Xiaomi, Samsung, Oppo, Vivo, Infinix) နှင့် Chipset ကို ရွေးချယ်ပါ။
3. **Choose Operation**:
   - **Flash Tab**: Scatter ဖိုင်ရွေးချယ်ပြီး `START FLASHING` ကို နှိပ်ပါ။
   - **Service Tab**: FRP ဖြုတ်ရန် `Erase FRP` သို့မဟုတ် Bootloader Unlock လုပ်ရန် `Unlock Bootloader` ကို နှိပ်ပါ။
4. **BROM Mode Connection**:
   - Target ဖုန်းကို ပါဝါပိတ်ပါ။
   - **Volume Up + Volume Down** ခလုတ် ၂ ခုလုံးကို ဖိထားပြီး USB ကြိုး ထိုးသွင်းပါ။
5. **Done**: Tool မှ စက်ကို အလိုအလျောက် သိရှိပြီး ၁၀၀% ပြီးဆုံးသည်အထိ အလိုအလျောက် လုပ်ဆောင်ပေးပါမည်။

---

## 🏗️ စနစ်တည်ဆောက်ပုံ (Project Structure)

```
app/src/main/java/com/example/
├── MainActivity.kt                      # Main App Entry & WindowInsets
├── protocol/
│   ├── TargetPhoneUsbManager.kt        # Direct USB-OTG Driver & Raw Bulk I/O
│   └── MtkBromProtocolEngine.kt        # MTK BROM Handshake, DAA Bypass & Flash Engine
├── parser/
│   ├── ScatterParser.kt                # MediaTek Scatter File Syntax Parser
│   └── GptParser.kt                    # Live GPT (GUID Partition Table) Storage Reader
├── storage/
│   └── BackupStorageManager.kt         # Secure On-Device Firmware & NVRAM Backup
├── ai/
│   └── GeminiDiagnosticAdvisor.kt      # AI-Powered Error Troubleshooting
├── audio/
│   └── ToolSoundManager.kt             # Professional GSM Tool Sound Engine
├── ui/
│   ├── screens/
│   │   └── UnlockToolFlashScreen.kt    # Main GSM Flasher Dashboard Screen
│   └── components/                     # Reusable Material 3 UI Components
└── viewmodel/
    └── MtkBridgeViewModel.kt           # StateFlow & Protocol Lifecycle Coordinator
```

---

## 🛠️ Build & Development

### Requirements:
- Android Studio Ladybug | 2024.2.1 သို့မဟုတ် နောက်ဆုံးထွက်ဗားရှင်း
- Minimum SDK: `26` (Android 8.0 Oreo)
- Target SDK: `34` (Android 14)
- Language: `Kotlin 2.0+`
- UI Framework: `Jetpack Compose`

### Build Command:
```bash
# Debug APK တည်ဆောက်ရန်
./gradlew assembleDebug

# Release APK တည်ဆောက်ရန်
./gradlew assembleRelease
```

---

## ⚠️ ငြင်းချက် (Disclaimer)

ဤဆော့ဖ်ဝဲလ်သည် ပညာရေးနှင့် တရားဝင် ဖုန်းပြုပြင်ထိန်းသိမ်းမှု (Educational & Legitimate Device Servicing) ရည်ရွယ်ချက်များအတွက်သာ ဖြစ်ပါသည်။ အသုံးပြုသူ၏ လွဲမှားစွာ အသုံးပြုမှုကြောင့် ဖုန်းပျက်စီးခြင်း သို့မဟုတ် Data ပျက်စီးဆုံးရှုံးမှုများအတွက် တာဝန်မယူပါ။ Firmware ရေးသားခြင်း မပြုမီ မူရင်း NVRAM Data များကို အမြဲ Backup ပြုလုပ်ထားရန် အကြံပြုအပ်ပါသည်။

---

## 📄 လိုင်စင် (License)
Open Source project under the **MIT License**.
