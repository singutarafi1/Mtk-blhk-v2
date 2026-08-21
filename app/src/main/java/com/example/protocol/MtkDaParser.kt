package com.example.protocol

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MediaTek Download Agent (DA) Binary & Header Parser
 * Direct, faithful port of Python mtkclient:
 * - mtkclient/Library/DA/daconfig.py
 * NO FAKE DATA - Real binary parsing only.
 */
object MtkDaParser {

    private const val TAG = "MtkDaParser"

    data class EntryRegion(
        val mBuf: Long,          // Buffer offset in binary
        val mLen: Long,          // Length of payload region in bytes
        val mStartAddr: Long,    // Target execution/load address in RAM (SRAM/DRAM)
        val mStartOffset: Long,  // Start offset
        val mSigLen: Long        // Signature length
    )

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
     * Parses all DA structures inside an MTK Download Agent container (e.g. MTK_DA_V5.bin, MTK_DA_V6.bin).
     * Strictly mirrors mtkclient/Library/DA/daconfig.py parse_da_loader.
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

        // Determine offset for first DA structure (matching mtkclient offset heuristics 0xD8, 0xDC, 0x6C)
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

    fun findDa(
        daList: List<DA>,
        hwCode: Int?,
        hwSubCode: Int? = null,
        hwVersion: Int? = null,
        swVersion: Int? = null
    ): DA? {
        if (daList.isEmpty()) return null
        if (hwCode == null) return daList.firstOrNull()

        val exact = daList.filter { it.hwCode == hwCode || it.hwCode == (hwCode and 0xFFFF) }
        if (exact.isEmpty()) return daList.firstOrNull()

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
     * STRICTLY requires valid DA container matching or loader structure.
     * Prevents false fallback to small payloads (> 4KB verification).
     */
    fun parseDaLoader(
        daData: ByteArray,
        hwCode: Int? = null,
        hwSubCode: Int? = null,
        hwVersion: Int? = null,
        swVersion: Int? = null,
        defaultLoadAddr: Long = 0x201000L
    ): DaLoaderInfo? {
        if (daData.size < 4096) {
            Log.e(TAG, "DA Binary too small (${daData.size} bytes). Must be a full container.")
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

        Log.e(TAG, "Failed to parse DA container partitions.")
        return null
    }
}
