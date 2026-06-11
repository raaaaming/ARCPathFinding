package kr.acda.arcpathfinding.mask

import java.util.BitSet
import java.util.zip.CRC32

class StandableMask(val yMin: Int, val yMax: Int) {
    private val height = yMax - yMin + 1
    private val bits = BitSet(16 * 16 * height)

    private fun idx(lx: Int, y: Int, lz: Int) = ((y - yMin) * 16 + lz) * 16 + lx

    fun getLocal(lx: Int, y: Int, lz: Int): Boolean {
        if (lx !in 0..15 || lz !in 0..15 || y !in yMin..yMax) return false
        return bits[idx(lx, y, lz)]
    }

    fun setLocal(lx: Int, y: Int, lz: Int, value: Boolean) {
        if (lx !in 0..15 || lz !in 0..15 || y !in yMin..yMax) return
        bits[idx(lx, y, lz)] = value
    }

    fun getWorld(x: Int, y: Int, z: Int): Boolean = getLocal(x and 15, y, z and 15)
    fun setWorld(x: Int, y: Int, z: Int, value: Boolean) = setLocal(x and 15, y, z and 15, value)

    fun crc32(): Int {
        val crc = CRC32()
        crc.update(bits.toByteArray())
        return crc.value.toInt()
    }

    fun yRange() = yMin..yMax
}
