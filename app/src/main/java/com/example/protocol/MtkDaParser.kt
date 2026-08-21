package com.example.protocol

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MediaTek Download Agent (DA) Binary & Header Parser
 * Direct, faithful port of Python mtkclient:
 * - mtkclient/Library/DA/daconfig.py
 */
object MtkDaParser {

    private const val TAG = "MtkDaParser"

    /**
     * EntryRegion structure (32-bit dwords: <IIIII - 20 bytes)
     * Fields: m_buf, m_len, m_start_addr, m_start_offset, m_sig_len
     */
    data class EntryRegion(
        val mBuf: Long,          // Buffer offset in binary
        val mLen: Long,          // Length of payload region in bytes
        val mStartAddr: Long,    // Target execution/load address in RAM (SRAM/DRAM)
        val mStartOffset: Long,  // Start offset
        val mSigLen: Long        // Signature length
    )

    /**
     * DA structure (Header: <10H - 20 bytes, followed by EntryRegion list)
     * Fields: magic, hw_code, hw_sub_code, hw_version, sw_version, reserved1,
     *         pagesize, reserved3, entry_region_index, entry_region_count, regions
     */
    data class DA(
        val magic: Int,
        val hwCode: Int,
        val hwSubCode: Int,
        val hwVersion: Int,
        val swVersion: Int,
        val reserved1: Int,
        val pageSize: Int,
        val reserved3: Int,
        val entryRegionIndex: Int,
        val entryRegionCount: Int,
        val regions: List<EntryRegion>
    ) {
        val stage1Region: EntryRegion? get() = regions.getOrNull(0)
        val stage2Region: EntryRegion? get() = regions.getOrNull(1)
    }

    // High-level wrapper for compatibility
    data class DaRegion(
        val index: Int,
        val name: String,
        val bufOffset: Long,
        val length: Long,
        val startAddress: Long,
        val sigLength: Long
    )

    data class DaHeader(
        val magic: String,
        val hwCode: Int,
        val hwSubCode: Int,
        val hwVersion: Int,
        val swVersion: Int,
        val pageSize: Int,
        val entryRegionIndex: Int,
        val entryRegionCount: Int,
        val regions: List<DaRegion>
    )

    data class DaLoaderInfo(
        val header: DaHeader,
        val stage1: DaRegion?,
        val stage2: DaRegion?,
        val rawData: ByteArray,
        val da: DA? = null
    ) {
        fun getStage1Bytes(): ByteArray? {
            val region = stage1 ?: return null
            if (region.bufOffset + region.length > rawData.size || region.length <= 0) return null
            return rawData.copyOfRange(region.bufOffset.toInt(), (region.bufOffset + region.length).toInt())
        }

        fun getStage2Bytes(): ByteArray? {
            val region = stage2 ?: return null
            if (region.bufOffset + region.length > rawData.size || region.length <= 0) return null
            return rawData.copyOfRange(region.bufOffset.toInt(), (region.bufOffset + region.length).toInt())
        }
    }

    /**
     * Parses all DA structures inside an MTK Download Agent binary (e.g. MTK_AllInOne_DA.bin).
     * Ported directly from daconfig.py: parse_da_loader
     */
    fun parseAllDa(data: ByteArray): List<DA> {
        if (data.size < 0x6C) {
            Log.e(TAG, "DA Binary too small (${data.size} bytes < 0x6C)")
            return emptyList()
        }

        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        // Read count_da at offset 0x68 (4 bytes little-endian)
        val countDa = buf.getInt(0x68).toLong() and 0xFFFFFFFFL
        if (countDa <= 0 || countDa > 256) {
            Log.w(TAG, "Invalid count_da: $countDa at 0x68")
            return emptyList()
        }

        // Determine offset for first DA structure
        var offset = 0x6C
        if (0xD8 + 2 <= data.size && data[0xD8] == 0xDA.toByte() && data[0xD9] == 0xDA.toByte()) {
            offset = 0xD8
        } else if (0xDC + 2 <= data.size && data[0xDC] == 0xDA.toByte() && data[0xDD] == 0xDA.toByte()) {
            offset = 0xDC
        } else if (0x6C + 2 <= data.size && data[0x6C] == 0xDA.toByte() && data[0x6D] == 0xDA.toByte()) {
            offset = 0x6C
        } else if (0x6C + 0xD8 + 2 <= data.size && data[0x6C + 0xD8] == 0xDA.toByte() && data[0x6C + 0xD9] == 0xDA.toByte()) {
            offset = 0x6C + 0xD8
        }

        val daList = mutableListOf<DA>()
        try {
            buf.position(offset)
            for (i in 0 until countDa.toInt()) {
                if (buf.remaining() < 20) break

                // DA Header: 10 unsigned shorts (20 bytes)
                val magic = buf.short.toInt() and 0xFFFF
                val hwCode = buf.short.toInt() and 0xFFFF
                val hwSubCode = buf.short.toInt() and 0xFFFF
                val hwVersion = buf.short.toInt() and 0xFFFF
                val swVersion = buf.short.toInt() and 0xFFFF
                val reserved1 = buf.short.toInt() and 0xFFFF
                val pageSize = buf.short.toInt() and 0xFFFF
                val reserved3 = buf.short.toInt() and 0xFFFF
                val entryRegionIndex = buf.short.toInt() and 0xFFFF
                val entryRegionCount = buf.short.toInt() and 0xFFFF

                val regions = mutableListOf<EntryRegion>()
                val regionCount = entryRegionCount.coerceIn(0, 16)
                for (r in 0 until regionCount) {
                    if (buf.remaining() < 20) break
                    val mBuf = buf.int.toLong() and 0xFFFFFFFFL
                    val mLen = buf.int.toLong() and 0xFFFFFFFFL
                    val mStartAddr = buf.int.toLong() and 0xFFFFFFFFL
                    val mStartOffset = buf.int.toLong() and 0xFFFFFFFFL
                    val mSigLen = buf.int.toLong() and 0xFFFFFFFFL

                    regions.add(
                        EntryRegion(
                            mBuf = mBuf,
                            mLen = mLen,
                            mStartAddr = mStartAddr,
                            mStartOffset = mStartOffset,
                            mSigLen = mSigLen
                        )
                    )
                }

                daList.add(
                    DA(
                        magic = magic,
                        hwCode = hwCode,
                        hwSubCode = hwSubCode,
                        hwVersion = hwVersion,
                        swVersion = swVersion,
                        reserved1 = reserved1,
                        pageSize = pageSize,
                        reserved3 = reserved3,
                        entryRegionIndex = entryRegionIndex,
                        entryRegionCount = entryRegionCount,
                        regions = regions
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing DA list: ${e.message}")
        }

        return daList
    }

    /**
     * Filters and finds the best matching DA entry for a specific chipset target.
     * Matches logic from mtkclient/Library/DA/daconfig.py
     */
    fun findDa(
        daList: List<DA>,
        hwCode: Int?,
        hwSubCode: Int? = null,
        hwVersion: Int? = null,
        swVersion: Int? = null
    ): DA? {
        if (daList.isEmpty()) return null
        if (hwCode == null) return daList.firstOrNull()

        // 1. Exact match on hwCode, hwSubCode, hwVersion, swVersion
        val exact = daList.filter { it.hwCode == hwCode }
        if (exact.isEmpty()) return null

        var candidates = exact
        if (hwSubCode != null) {
            val subMatch = candidates.filter { it.hwSubCode == hwSubCode }
            if (subMatch.isNotEmpty()) candidates = subMatch
        }
        if (hwVersion != null) {
            val verMatch = candidates.filter { it.hwVersion == hwVersion }
            if (verMatch.isNotEmpty()) candidates = verMatch
        }
        if (swVersion != null) {
            val swMatch = candidates.filter { it.swVersion <= swVersion }
            if (swMatch.isNotEmpty()) {
                return swMatch.maxByOrNull { it.swVersion }
            }
        }

        return candidates.maxByOrNull { it.swVersion } ?: candidates.firstOrNull()
    }

    /**
     * Primary DA loader entry point.
     * Parses DA container or raw payload and resolves Stage 1 / Stage 2 regions.
     */
    fun parseDaLoader(
        daData: ByteArray,
        hwCode: Int? = null,
        hwSubCode: Int? = null,
        hwVersion: Int? = null,
        swVersion: Int? = null,
        defaultLoadAddr: Long = 0x201000L
    ): DaLoaderInfo? {
        if (daData.size < 32) {
            Log.e(TAG, "DA Binary too small (${daData.size} bytes)")
            return null
        }

        // 1. Parse standard DA container
        val daList = parseAllDa(daData)
        if (daList.isNotEmpty()) {
            val selectedDa = findDa(daList, hwCode, hwSubCode, hwVersion, swVersion) ?: daList.first()
            val mappedRegions = selectedDa.regions.mapIndexed { idx, reg ->
                val name = when (idx) {
                    0 -> "DA_STAGE1 (DA_PL)"
                    1 -> "DA_STAGE2 (XFLASH/EXT)"
                    else -> "DA_REGION_$idx"
                }
                DaRegion(
                    index = idx,
                    name = name,
                    bufOffset = reg.mBuf,
                    length = reg.mLen,
                    startAddress = reg.mStartAddr,
                    sigLength = reg.mSigLen
                )
            }

            val header = DaHeader(
                magic = "0x%04X".format(selectedDa.magic),
                hwCode = selectedDa.hwCode,
                hwSubCode = selectedDa.hwSubCode,
                hwVersion = selectedDa.hwVersion,
                swVersion = selectedDa.swVersion,
                pageSize = selectedDa.pageSize,
                entryRegionIndex = selectedDa.entryRegionIndex,
                entryRegionCount = selectedDa.entryRegionCount,
                regions = mappedRegions
            )

            return DaLoaderInfo(
                header = header,
                stage1 = mappedRegions.getOrNull(0),
                stage2 = mappedRegions.getOrNull(1),
                rawData = daData,
                da = selectedDa
            )
        }

        // 2. Fallback: Parse binary struct header (<16s 10H)
        try {
            val buf = ByteBuffer.wrap(daData).order(ByteOrder.LITTLE_ENDIAN)
            val headerString = String(daData.take(16).toByteArray(), Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }

            buf.position(16)
            val magic = buf.short.toInt() and 0xFFFF
            val parsedHwCode = buf.short.toInt() and 0xFFFF
            val parsedHwSubCode = buf.short.toInt() and 0xFFFF
            val parsedHwVersion = buf.short.toInt() and 0xFFFF
            val parsedSwVersion = buf.short.toInt() and 0xFFFF
            val reserved1 = buf.short.toInt() and 0xFFFF
            val pageSize = buf.short.toInt() and 0xFFFF
            val reserved3 = buf.short.toInt() and 0xFFFF
            val entryRegionIndex = buf.short.toInt() and 0xFFFF
            val entryRegionCount = buf.short.toInt() and 0xFFFF

            if (entryRegionCount in 1..16 && pageSize in 512..65536) {
                val regions = mutableListOf<EntryRegion>()
                val mappedRegions = mutableListOf<DaRegion>()
                for (i in 0 until entryRegionCount) {
                    val mBuf = buf.int.toLong() and 0xFFFFFFFFL
                    val mLen = buf.int.toLong() and 0xFFFFFFFFL
                    val mStartAddr = buf.int.toLong() and 0xFFFFFFFFL
                    val mStartOffset = buf.int.toLong() and 0xFFFFFFFFL
                    val mSigLen = buf.int.toLong() and 0xFFFFFFFFL

                    regions.add(EntryRegion(mBuf, mLen, mStartAddr, mStartOffset, mSigLen))
                    val name = if (i == 0) "DA_STAGE1 (DA_PL)" else if (i == 1) "DA_STAGE2 (XFLASH/EXT)" else "DA_REGION_$i"
                    mappedRegions.add(DaRegion(i, name, mBuf, mLen, mStartAddr, mSigLen))
                }

                val da = DA(
                    magic = magic,
                    hwCode = parsedHwCode,
                    hwSubCode = parsedHwSubCode,
                    hwVersion = parsedHwVersion,
                    swVersion = parsedSwVersion,
                    reserved1 = reserved1,
                    pageSize = pageSize,
                    reserved3 = reserved3,
                    entryRegionIndex = entryRegionIndex,
                    entryRegionCount = entryRegionCount,
                    regions = regions
                )

                val header = DaHeader(
                    magic = headerString.ifEmpty { "0x%04X".format(magic) },
                    hwCode = parsedHwCode,
                    hwSubCode = parsedHwSubCode,
                    hwVersion = parsedHwVersion,
                    swVersion = parsedSwVersion,
                    pageSize = pageSize,
                    entryRegionIndex = entryRegionIndex,
                    entryRegionCount = entryRegionCount,
                    regions = mappedRegions
                )

                return DaLoaderInfo(header, mappedRegions.getOrNull(0), mappedRegions.getOrNull(1), daData, da)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Secondary DA header parse skipped: ${e.message}")
        }

        // 3. Fallback: Raw flat payload binary (e.g. mt6765_payload.bin)
        val flatEntryRegion = EntryRegion(
            mBuf = 0L,
            mLen = daData.size.toLong(),
            mStartAddr = defaultLoadAddr,
            mStartOffset = 0L,
            mSigLen = 0L
        )
        val flatRegion = DaRegion(
            index = 0,
            name = "RAW_STAGE1_PAYLOAD",
            bufOffset = 0L,
            length = daData.size.toLong(),
            startAddress = defaultLoadAddr,
            sigLength = 0L
        )
        val flatDa = DA(
            magic = 0,
            hwCode = hwCode ?: 0,
            hwSubCode = hwSubCode ?: 0,
            hwVersion = hwVersion ?: 0,
            swVersion = swVersion ?: 0,
            reserved1 = 0,
            pageSize = 4096,
            reserved3 = 0,
            entryRegionIndex = 0,
            entryRegionCount = 1,
            regions = listOf(flatEntryRegion)
        )
        val flatHeader = DaHeader(
            magic = "RAW_BINARY",
            hwCode = hwCode ?: 0,
            hwSubCode = hwSubCode ?: 0,
            hwVersion = hwVersion ?: 0,
            swVersion = swVersion ?: 0,
            pageSize = 4096,
            entryRegionIndex = 0,
            entryRegionCount = 1,
            regions = listOf(flatRegion)
        )
        return DaLoaderInfo(flatHeader, flatRegion, null, daData, flatDa)
    }
}
