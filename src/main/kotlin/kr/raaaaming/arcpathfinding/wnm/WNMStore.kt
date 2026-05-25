package kr.raaaaming.arcpathfinding.wnm

import kr.raaaaming.arcpathfinding.chunk.ChunkPos
import kr.raaaaming.arcpathfinding.chunk.NavChunk
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

private const val WNM_MAGIC = 0x574E4D31

class WNMStore {

    fun writeNew(path: Path, header: WNMHeader, chunks: List<NavChunk>) {
        Files.newOutputStream(path).use { raw ->
            GZIPOutputStream(raw).use { gz ->
                DataOutputStream(gz).use { out ->
                    out.writeInt(WNM_MAGIC)
                    out.writeInt(header.version)
                    out.writeInt(header.policyHash)
                    out.writeLong(header.worldUUID.mostSignificantBits)
                    out.writeLong(header.worldUUID.leastSignificantBits)
                    out.writeInt(header.yMin); out.writeInt(header.yMax)
                    out.writeLong(header.createdAt)
                    out.writeInt(chunks.size)
                    for (ch in chunks) writeChunk(out, ch)
                }
            }
        }
    }

    fun loadHeader(path: Path): WNMHeader {
        Files.newInputStream(path).use { raw ->
            GZIPInputStream(raw).use { gz ->
                DataInputStream(gz).use { `in` ->
                    val magic = `in`.readInt(); require(magic == WNM_MAGIC) { "Bad WNM magic" }
                    val ver = `in`.readInt(); val ph = `in`.readInt()
                    val msb = `in`.readLong(); val lsb = `in`.readLong()
                    val yMin = `in`.readInt(); val yMax = `in`.readInt()
                    val created = `in`.readLong()
                    `in`.readInt()
                    return WNMHeader(ver, ph, UUID(msb, lsb), yMin, yMax, created)
                }
            }
        }
    }

    fun readAll(path: Path): Pair<WNMHeader, List<NavChunk>> {
        Files.newInputStream(path).use { raw ->
            GZIPInputStream(raw).use { gz ->
                DataInputStream(gz).use { `in` ->
                    val magic = `in`.readInt(); require(magic == WNM_MAGIC) { "Bad WNM magic" }
                    val ver = `in`.readInt(); val ph = `in`.readInt()
                    val msb = `in`.readLong(); val lsb = `in`.readLong()
                    val yMin = `in`.readInt(); val yMax = `in`.readInt()
                    val created = `in`.readLong()
                    val cnt = `in`.readInt()
                    val header = WNMHeader(ver, ph, UUID(msb, lsb), yMin, yMax, created)
                    val list = ArrayList<NavChunk>(cnt)
                    repeat(cnt) { list.add(readChunk(`in`)) }
                    return header to list
                }
            }
        }
    }

    fun appendOrPatch(path: Path, header: WNMHeader, updated: List<NavChunk>) {
        val (oldHeader, oldChunks) =
            if (Files.exists(path)) readAll(path) else header to emptyList()

        require(oldChunks.isEmpty() || (oldHeader.policyHash == header.policyHash &&
                oldHeader.worldUUID == header.worldUUID &&
                oldHeader.yMin == header.yMin && oldHeader.yMax == header.yMax)) {
            "Header mismatch: rebuild WNM from scratch"
        }

        val map = HashMap<Long, NavChunk>(oldChunks.size + updated.size)
        fun key(cx: Int, cz: Int) = (cx.toLong() shl 32) xor (cz.toLong() and 0xFFFFFFFFL)
        for (ch in oldChunks) map[key(ch.pos.cx, ch.pos.cz)] = ch
        for (ch in updated) map[key(ch.pos.cx, ch.pos.cz)] = ch

        val merged = map.values.sortedWith(compareBy({ it.pos.cx }, { it.pos.cz }))

        val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")
        writeNew(tmp, header.copy(createdAt = System.currentTimeMillis()), merged)
        Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
    }

    private fun writeChunk(out: DataOutputStream, ch: NavChunk) {
        out.writeInt(ch.pos.cx); out.writeInt(ch.pos.cz)
        out.writeInt(ch.standableCrc)
        out.writeInt(ch.coords.size); for (v in ch.coords) out.writeInt(v)
        out.writeInt(ch.adjOff.size); for (v in ch.adjOff) out.writeInt(v)
        out.writeInt(ch.adjTo.size); for (v in ch.adjTo) out.writeInt(v)
        out.writeInt(ch.adjTag.size); for (v in ch.adjTag) out.writeShort(v.toInt())
        fun wIntArr(a: IntArray) { out.writeInt(a.size); for (v in a) out.writeInt(v) }
        wIntArr(ch.gateN); wIntArr(ch.gateS); wIntArr(ch.gateW); wIntArr(ch.gateE)
    }

    private fun readChunk(`in`: DataInputStream): NavChunk {
        val cx = `in`.readInt(); val cz = `in`.readInt()
        val crc = `in`.readInt()
        fun rIntArr(): IntArray { val n = `in`.readInt(); val a = IntArray(n); for (i in 0 until n) a[i] = `in`.readInt(); return a }
        fun rShortArr(): ShortArray { val n = `in`.readInt(); val a = ShortArray(n); for (i in 0 until n) a[i] = `in`.readShort(); return a }
        val coords = rIntArr(); val adjOff = rIntArr(); val adjTo = rIntArr(); val adjTag = rShortArr()
        val gN = rIntArr(); val gS = rIntArr(); val gW = rIntArr(); val gE = rIntArr()
        return NavChunk(ChunkPos(cx, cz), coords, adjOff, adjTo, adjTag, gN, gS, gW, gE, crc)
    }
}
