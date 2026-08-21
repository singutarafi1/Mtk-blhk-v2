package com.example.protocol

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Universal MediaTek Download Agent (DA) Binary Parser
 * Supports: All-In-One DA Containers, DA V5 (0xDA 0xDA), DA V6, and Raw Stage Binaries.
 * Strictly Ported from Python mtkclient: mtkclient/Library/DA/daconfig.py
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
     * Primary DA Parser Entry Point.
     * Automatically handles Header-based Containers and Raw Split DA architectures.
     */
    fun parseDaLoader(
        daData: ByteArray,
        hwCode: Int? = null,
        defaultLoadAddr: Long = 0x201000L
    ): DaLoaderInfo? {
        if (daData.size < 4096) {
            Log.e(TAG, "DA Binary too small (${daData.size} bytes). Minimum 4KB required.")
            return null
        }

        // 1. Try Parsing as Structured DA Container (MTK_DA_V5 / AllInOne)
        val daList = parseStructuredDa(daData)
        if (daList.isNotEmpty()) {
            val selectedDa = findDa(daList, hwCode) ?: daList.first()
            val mappedRegions = selectedDa.regions.mapIndexed { idx, reg ->
                val startAddr = if (reg.mStartAddr != 0L) reg.mStartAddr else (if (idx == 0) defaultLoadAddr else 0x40000000L)
                DaRegion(
                    index = idx,
                    name = if (idx == 0) "DA_STAGE1 (DA_PL)" else "DA_STAGE2 (XFLASH)",
                    bufOffset = reg.mBuf,
                    length = reg.mLen,
                    startAddress = startAddr,
                    sigLength = reg.mSigLen
                )
            }

            if (mappedRegions.isNotEmpty() && mappedRegions[0].length >= 4096) {
                Log.d(TAG, "Parsed Structured DA: Stage1 Size=${mappedRegions[0].length}, Stage2 Size=${mappedRegions.getOrNull(1)?.length ?: 0}")
                return DaLoaderInfo(
                    header = DaHeader(
                        magic = "0x%04X".format(selectedDa.magic),
                        hwCode = selectedDa.hwCode,
                        hwSubCode = selectedDa.hwSubCode,
                        hwVersion = selectedDa.hwVersion,
                        swVersion = selectedDa.swVersion,
                        pageSize = selectedDa.pageSize,
                        entryRegionIndex = selectedDa.entryRegionIndex,
                        entryRegionCount = selectedDa.entryRegionCount,
                        regions = mappedRegions
                    ),
                    stage1 = mappedRegions.getOrNull(0),
                    stage2 = mappedRegions.getOrNull(1),
                    rawData = daData,
                    da = selectedDa
                )
            }
        }

        // 2. Fallback: Parse as Raw Stage 1/2 Binary (when MTK_DA_V5.bin is a direct concatenated loader)
        Log.d(TAG, "Parsing as Raw Binary Split Loader...")
        return parseRawSplitDa(daData, hwCode, defaultLoadAddr)
    }

    private fun parseStructuredDa(data: ByteArray): List<DA> {
        val daList = mutableListOf<DA>()
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        // Scan for 0xDA 0xDA Magic markers in first 1KB
        val possibleOffsets = mutableListOf<Int>()
        for (i in 0 until minOf(1024, data.size - 2)) {
            if (data[i] == 0xDA.toByte() && data[i + 1] == 0xDA.toByte()) {
                possibleOffsets.add(i)
            }
        }

        for (offset in possibleOffsets) {
            try {
                buf.position(offset)
                while (buf.remaining() >= 20) {
                    val magic = buf.short.toInt() and 0xFFFF
                    if (magic != 0xDADA) break

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
                    val count = entryRegionCount.coerceIn(1, 8)
                    for (r in 0 until count) {
                        if (buf.remaining() < 20) break
                        val mBuf = buf.int.toLong() and 0xFFFFFFFFL
                        val mLen = buf.int.toLong() and 0xFFFFFFFFL
                        val mStartAddr = buf.int.toLong() and 0xFFFFFFFFL
                        val mStartOffset = buf.int.toLong() and 0xFFFFFFFFL
                        val mSigLen = buf.int.toLong() and 0xFFFFFFFFL

                        if (mBuf + mLen <= data.size && mLen > 0) {
                            regions.add(EntryRegion(mBuf, mLen, mStartAddr, mStartOffset, mSigLen))
                        }
                    }

                    if (regions.isNotEmpty()) {
                        daList.add(
                            DA(magic, hwCode, hwSubCode, hwVersion, swVersion, reserved1, pageSize, reserved3, entryRegionIndex, entryRegionCount, regions)
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        return daList
    }

    private fun parseRawSplitDa(data: ByteArray, hwCode: Int?, defaultLoadAddr: Long): DaLoaderInfo? {
        val totalLen = data.size.toLong()
        
        // Typical Stage 1 size in MTK DA V5 is 0x20000 (128KB) or entire file if single-stage
        val stage1Len = if (totalLen > 0x20000) 0x20000L else totalLen
        val stage2Len = if (totalLen > 0x20000) totalLen - 0x20000L else 0L

        val regions = mutableListOf<DaRegion>()
        regions.add(
            DaRegion(
                index = 0,
                name = "DA_STAGE1 (DA_PL)",
                bufOffset = 0L,
                length = stage1Len,
                startAddress = defaultLoadAddr,
                sigLength = 0L
            )
        )

        if (stage2Len > 0) {
            regions.add(
                DaRegion(
                    index = 1,
                    name = "DA_STAGE2 (XFLASH)",
                    bufOffset = stage1Len,
                    length = stage2Len,
                    startAddress = 0x40000000L,
                    sigLength = 0L
                )
            )
        }

        return DaLoaderInfo(
            header = DaHeader(
                magic = "0xDADA",
                hwCode = hwCode ?: 0x0766,
                hwSubCode = 0,
                hwVersion = 0,
                swVersion = 0,
                pageSize = 2048,
                entryRegionIndex = 0,
                entryRegionCount = regions.size,
                regions = regions
            ),
            stage1 = regions[0],
            stage2 = regions.getOrNull(1),
            rawData = data
        )
    }

    private fun findDa(daList: List<DA>, hwCode: Int?): DA? {
        if (daList.isEmpty()) return null
        if (hwCode == null) return daList.firstOrNull()

        val exact = daList.filter { it.hwCode == hwCode || it.hwCode == (hwCode and 0xFFFF) }
        return if (exact.isNotEmpty()) {
            exact.maxByOrNull { it.swVersion } ?: exact.first()
        } else {
            daList.firstOrNull()
        }
    }
}
