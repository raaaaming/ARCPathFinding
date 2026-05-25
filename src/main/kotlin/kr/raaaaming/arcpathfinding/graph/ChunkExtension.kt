package kr.raaaaming.arcpathfinding.graph

import kr.raaaaming.arcpathfinding.chunk.NavChunk
import kr.raaaaming.arcpathfinding.policy.MovePolicy
import kr.raaaaming.arcpathfinding.util.IntArrayList

private fun keyXZ(x: Int, z: Int): Long = (x.toLong() shl 32) xor (z.toLong() and 0xFFFFFFFFL)

fun mergeChunks(chunks: List<NavChunk>, policy: MovePolicy): NavWorldGraph {

    fun selectCompatibleGate(
        uy: Int, cand: IntArrayList, coords: IntArray, stepUp: Int, maxDrop: Int
    ): Int {
        var bestUpNode = -1; var bestUpDelta = Int.MAX_VALUE
        var bestDownNode = -1; var bestDownDelta = Int.MAX_VALUE
        var i = 0
        while (i < cand.size()) {
            val vLocal = cand[i]; val vy = coords[vLocal * 3 + 1]
            if (vy == uy) return vLocal
            if (vy > uy) {
                val diff = vy - uy
                if (diff <= stepUp && diff < bestUpDelta) { bestUpDelta = diff; bestUpNode = vLocal }
            } else {
                val diff = uy - vy
                if (diff <= maxDrop && diff < bestDownDelta) { bestDownDelta = diff; bestDownNode = vLocal }
            }
            i++
        }
        return if (bestUpNode != -1) bestUpNode else bestDownNode
    }

    if (chunks.isEmpty()) return NavWorldGraph(0, IntArray(0), IntArray(0), IntArray(1) { 0 }, IntArray(0))

    data class C(val cx: Int, val cz: Int)
    val idxOf = HashMap<C, Int>(chunks.size)
    chunks.forEachIndexed { i, ch -> idxOf[C(ch.pos.cx, ch.pos.cz)] = i }

    val offsets = IntArray(chunks.size + 1)
    for (i in chunks.indices) offsets[i + 1] = offsets[i] + chunks[i].nodeCount
    val N = offsets.last()

    val xyzt = IntArray(N * 3)
    val chunkOf = IntArray(N)
    for (i in chunks.indices) {
        val ch = chunks[i]; val base = offsets[i]
        System.arraycopy(ch.coords, 0, xyzt, base * 3, ch.coords.size)
        java.util.Arrays.fill(chunkOf, base, base + ch.nodeCount, i)
    }

    val nbr = Array(N) { IntArrayList() }

    for (i in chunks.indices) {
        val ch = chunks[i]; val base = offsets[i]
        val off = ch.adjOff; val to = ch.adjTo
        for (uLocal in 0 until ch.nodeCount) {
            val u = base + uLocal
            val s = off[uLocal]; val e = off[uLocal + 1]
            var j = s
            while (j < e) { nbr[u].add(base + to[j]); j++ }
        }
    }

    data class GateIndex(val byXZ: MutableMap<Long, IntArrayList>)
    fun buildGateIndex(ch: NavChunk, which: Char): GateIndex {
        val map = HashMap<Long, IntArrayList>()
        val nodes = when (which) {
            'N' -> ch.gateN; 'S' -> ch.gateS; 'W' -> ch.gateW; else -> ch.gateE
        }
        for (uLocal in nodes) {
            val x = ch.coords[uLocal * 3]; val z = ch.coords[uLocal * 3 + 2]
            map.computeIfAbsent(keyXZ(x, z)) { IntArrayList() }.add(uLocal)
        }
        return GateIndex(map)
    }
    val gateCache = HashMap<Pair<Int, Char>, GateIndex>()
    fun gateIndexOf(chunkIdx: Int, side: Char) =
        gateCache.getOrPut(chunkIdx to side) { buildGateIndex(chunks[chunkIdx], side) }

    val dxCand = if (policy.allowDiag) intArrayOf(-1, 0, 1) else intArrayOf(0)
    val dzCand = dxCand

    for (i in chunks.indices) {
        val A = chunks[i]; val baseA = offsets[i]

        idxOf[C(A.pos.cx, A.pos.cz - 1)]?.let { idxB ->
            val B = chunks[idxB]; val baseB = offsets[idxB]
            val gB = gateIndexOf(idxB, 'S')
            for (uLocal in A.gateN) {
                val ux = A.coords[uLocal * 3]; val uy = A.coords[uLocal * 3 + 1]
                val uz = A.coords[uLocal * 3 + 2]; val tz = uz - 1
                for (dx in dxCand) {
                    val cand = gB.byXZ[keyXZ(ux + dx, tz)] ?: continue
                    val vLocal = selectCompatibleGate(uy, cand, B.coords, policy.stepUp, policy.maxDrop)
                    if (vLocal < 0) continue
                    val u = baseA + uLocal; val v = baseB + vLocal
                    nbr[u].add(v); nbr[v].add(u)
                }
            }
        }

        idxOf[C(A.pos.cx, A.pos.cz + 1)]?.let { idxB ->
            val B = chunks[idxB]; val baseB = offsets[idxB]
            val gB = gateIndexOf(idxB, 'N')
            for (uLocal in A.gateS) {
                val ux = A.coords[uLocal * 3]; val uy = A.coords[uLocal * 3 + 1]
                val uz = A.coords[uLocal * 3 + 2]; val tz = uz + 1
                for (dx in dxCand) {
                    val cand = gB.byXZ[keyXZ(ux + dx, tz)] ?: continue
                    val vLocal = selectCompatibleGate(uy, cand, B.coords, policy.stepUp, policy.maxDrop)
                    if (vLocal < 0) continue
                    val u = baseA + uLocal; val v = baseB + vLocal
                    nbr[u].add(v); nbr[v].add(u)
                }
            }
        }

        idxOf[C(A.pos.cx - 1, A.pos.cz)]?.let { idxB ->
            val B = chunks[idxB]; val baseB = offsets[idxB]
            val gB = gateIndexOf(idxB, 'E')
            for (uLocal in A.gateW) {
                val ux = A.coords[uLocal * 3]; val uy = A.coords[uLocal * 3 + 1]
                val uz = A.coords[uLocal * 3 + 2]; val tx = ux - 1
                for (dz in dzCand) {
                    val cand = gB.byXZ[keyXZ(tx, uz + dz)] ?: continue
                    val vLocal = selectCompatibleGate(uy, cand, B.coords, policy.stepUp, policy.maxDrop)
                    if (vLocal < 0) continue
                    val u = baseA + uLocal; val v = baseB + vLocal
                    nbr[u].add(v); nbr[v].add(u)
                }
            }
        }

        idxOf[C(A.pos.cx + 1, A.pos.cz)]?.let { idxB ->
            val B = chunks[idxB]; val baseB = offsets[idxB]
            val gB = gateIndexOf(idxB, 'W')
            for (uLocal in A.gateE) {
                val ux = A.coords[uLocal * 3]; val uy = A.coords[uLocal * 3 + 1]
                val uz = A.coords[uLocal * 3 + 2]; val tx = ux + 1
                for (dz in dzCand) {
                    val cand = gB.byXZ[keyXZ(tx, uz + dz)] ?: continue
                    val vLocal = selectCompatibleGate(uy, cand, B.coords, policy.stepUp, policy.maxDrop)
                    if (vLocal < 0) continue
                    val u = baseA + uLocal; val v = baseB + vLocal
                    nbr[u].add(v); nbr[v].add(u)
                }
            }
        }
    }

    for (u in 0 until N) {
        val list = nbr[u]
        if (list.size() <= 1) continue
        val arr = list.toIntArray()
        java.util.Arrays.sort(arr)
        var m = 0
        for (i in arr.indices) { if (i == 0 || arr[i] != arr[i - 1]) arr[m++] = arr[i] }
        val nl = IntArrayList(m)
        var i = 0; while (i < m) { nl.add(arr[i]); i++ }
        nbr[u] = nl
    }

    val off = IntArray(N + 1); var M = 0
    for (u in 0 until N) { off[u] = M; M += nbr[u].size() }
    off[N] = M
    val to = IntArray(M)
    for (u in 0 until N) {
        val base = off[u]; val list = nbr[u]
        var j = 0; while (j < list.size()) { to[base + j] = list[j]; j++ }
    }

    return NavWorldGraph(nodeCount = N, xyzt = xyzt, chunkOf = chunkOf, csrOff = off, csrTo = to)
}
