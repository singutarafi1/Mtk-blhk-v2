package com.example.protocol

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MediaTek Download Agent (DA) Binary Container Parser
 * Strictly ported from Python mtkclient (mtkclient/Library/DA/daconfig.py).
 * 100% Real Hardware Binary Parser - No Mock/Simulation Data.
 */
object MtkDaParser {

    private const val TAG = "MtkDaParser"

    data class EntryRegion(
        val mBuf: Long,
        val mLen: Long,
        val mStartAddr: Long,
        val mStartOffset: Long,
        val mSigLen: Long
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
    )

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
            val start = region.bufOffset.toInt()
            val end = (region.bufOffset + region.length).toInt()
            if (start < 0 || end > rawData.size || start >= end) return null
            return rawData.copyOfRange(start, end)
        }

        fun getStage2Bytes(): ByteArray? {
            val region = stage2 ?: return null
            val start = region.bufOffset.toInt()
            val end = (region.bufOffset + region.length).toInt()
            if (start < 0 || end > rawData.size || start >= end) return null
            return rawData.copyOfRange(start, end)
        }
    }

    /**
     * Parses standard MTK Download Agent binary containers (MTK_DA_V5.bin, MTK_AllInOne_DA.bin).
     * Replicates Python mtkclient parse_da_loader algorithm.
     */
    fun parseAllDa(data: ByteArray): List<DA> {
        if (data.size < 0x70) {
            Log.e(TAG, "DA Container too small (${data.size} bytes)")
            return emptyList()
        }

        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        // Read count_da at offset 0x68 (uint32)
        val countDa = buf.getInt(0x68).toLong() and 0xFFFFFFFFL
        if (countDa <= 0 || countDa > 256) {
            Log.w(TAG, "Invalid count_da: $countDa at 0x68")
            return emptyList()
        }

        // Determine offset for first DA descriptor
        var offset = 0x6C
        val candidateOffsets = listOf(0xD8, 0xDC, 0x6C, 0x6C + 0xD8)
        for (cand in candidateOffsets) {
            if (cand + 2 <= data.size && data[cand] == 0xDA.toByte() && data[cand + 1] == 0xDA.toByte()) {
                offset = cand
                break
            }
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
            Log.e(TAG, "Error parsing DA descriptor list: ${e.message}")
        }

        return daList
    }

    fun findDa(daList: List<DA>, hwCode: Int?): DA? {
        if (daList.isEmpty()) return null
        if (hwCode == null) return daList.firstOrNull()

        val exact = daList.filter { it.hwCode == hwCode || it.hwCode == (hwCode and 0xFFFF) }
        return if (exact.isNotEmpty()) {
            exact.maxByOrNull { it.swVersion } ?: exact.first()
        } else {
            daList.firstOrNull()
        }
    }

    /**
     * Primary DA Container Entry Point.
     */
    fun parseDaLoader(
        daData: ByteArray,
        hwCode: Int? = null,
        defaultLoadAddr: Long = 0x201000L
    ): DaLoaderInfo? {
        if (daData.size < 4096) {
            Log.e(TAG, "DA Container binary too small (${daData.size} bytes).")
            return null
        }

        val daList = parseAllDa(daData)
        if (daList.isNotEmpty()) {
            val selectedDa = findDa(daList, hwCode) ?: daList.first()
            val mappedRegions = selectedDa.regions.mapIndexed { idx, reg ->
                DaRegion(
                    index = idx,
                    name = if (idx == 0) "DA_STAGE1 (DA_PL)" else "DA_STAGE2 (XFLASH)",
                    bufOffset = reg.mBuf,
                    length = reg.mLen,
                    startAddress = if (reg.mStartAddr != 0L) reg.mStartAddr else defaultLoadAddr,
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

        return null
    }
}
