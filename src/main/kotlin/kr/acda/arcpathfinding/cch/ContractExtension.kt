package kr.acda.arcpathfinding.cch

import kr.acda.arcpathfinding.graph.NavWorldGraph
import kr.acda.arcpathfinding.policy.MovePolicy
import kr.acda.arcpathfinding.util.IntArrayList
import kr.acda.arcpathfinding.util.PairArrayList
import kotlin.math.abs
import kotlin.math.max

fun contract(g: NavWorldGraph, order: IntArray, policy: MovePolicy): CCHIndex {
    val n = g.nodeCount
    if (n == 0) return CCHIndex(IntArray(0), IntArray(1) { 0 }, IntArray(0), IntArray(0), IntArray(0))

    val rank = IntArray(n)
    for (r in order.indices) rank[order[r]] = r

    val upTmp = Array(n) { PairArrayList() }

    for (u in 0 until n) {
        val s = g.csrOff[u]; val e = g.csrOff[u + 1]
        var j = s
        while (j < e) {
            val v = g.csrTo[j]
            if (rank[u] < rank[v]) upTmp[u].add(v, -1)
            j++
        }
    }

    val tmpH = IntArray(256)
    for (r in order.indices) {
        val v = order[r]
        val startV = g.csrOff[v]; val endV = g.csrOff[v + 1]
        val H = IntArrayList()
        var j = startV; while (j < endV) { val h = g.csrTo[j]; if (rank[h] > r) H.add(h); j++ }

        for (ix in 0 until H.size()) {
            val a = H[ix]
            var pA = g.csrOff[a]; val eA = g.csrOff[a + 1]
            while (pA < eA) {
                val b = g.csrTo[pA]
                if (rank[b] > r) {
                    if (rank[a] < rank[b]) upTmp[a].add(b, v) else upTmp[b].add(a, v)
                }
                pA++
            }
        }
    }

    val upOff = IntArray(n + 1); var m = 0
    val cleanedTo = Array(n) { IntArrayList() }
    val cleanedMid = Array(n) { IntArrayList() }

    fun preferMid(currentMid: Int, candidateMid: Int): Int {
        if (currentMid == -1 || candidateMid == -1) return -1
        return if (rank[candidateMid] < rank[currentMid]) candidateMid else currentMid
    }

    for (u in 0 until n) {
        val (toArr0, midArr0) = upTmp[u].toArrays()
        if (toArr0.isEmpty()) continue
        val idx = (0 until toArr0.size).toMutableList()
        idx.sortBy { toArr0[it] }
        var lastTo = -1; var chosenMid = Int.MAX_VALUE; var cnt = 0
        for (p in idx) {
            val t = toArr0[p]; val mMid = midArr0[p]
            if (t != lastTo) {
                if (lastTo != -1) {
                    cleanedTo[u].add(lastTo)
                    cleanedMid[u].add(if (chosenMid == Int.MAX_VALUE) -1 else chosenMid)
                    cnt++
                }
                lastTo = t; chosenMid = mMid
            } else {
                chosenMid = preferMid(chosenMid, mMid)
            }
        }
        if (lastTo != -1) {
            cleanedTo[u].add(lastTo)
            cleanedMid[u].add(if (chosenMid == Int.MAX_VALUE) -1 else chosenMid)
            cnt++
        }
        upOff[u] = m; m += cnt
    }
    upOff[n] = m

    val upTo = IntArray(m); val upMid = IntArray(m); var ptr = 0
    for (u in 0 until n) {
        val toL = cleanedTo[u]; val midL = cleanedMid[u]
        var i = 0
        while (i < toL.size()) { upTo[ptr] = toL[i]; upMid[ptr] = midL[i]; ptr++; i++ }
    }

    val upW = IntArray(m) { 0 }
    return CCHIndex(rank = rank, upOff = upOff, upTo = upTo, upMid = upMid, upW = upW)
}

fun defaultWeightFn(g: NavWorldGraph, u: Int, v: Int): Int {
    val ux = g.xyzt[u * 3]; val uy = g.xyzt[u * 3 + 1]; val uz = g.xyzt[u * 3 + 2]
    val vx = g.xyzt[v * 3]; val vy = g.xyzt[v * 3 + 1]; val vz = g.xyzt[v * 3 + 2]
    val dx = abs(vx - ux); val dz = abs(vz - uz); val dy = vy - uy
    var w = 10
    if (dx == 1 && dz == 1) w += 4
    if (dy == 1) w += 4
    if (dy < 0) w += 1 * abs(dy)
    return w
}

fun edgeIndex(idx: CCHIndex, u: Int, v: Int): Int {
    val off = idx.upOff; val to = idx.upTo
    var lo = off[u]; var hi = off[u + 1] - 1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1; val t = to[mid]
        if (t == v) return mid
        if (t < v) lo = mid + 1 else hi = mid - 1
    }
    return -1
}

fun contractFast(g: NavWorldGraph, order: IntArray, policy: MovePolicy): CCHIndex {
    val n = g.nodeCount
    if (n == 0) return CCHIndex(IntArray(0), IntArray(1) { 0 }, IntArray(0), IntArray(0), IntArray(0))

    val rank = IntArray(n); for (r in order.indices) rank[order[r]] = r
    val upTmp = Array(n) { PairArrayList() }

    for (u in 0 until n) {
        var i = g.csrOff[u]; val e = g.csrOff[u + 1]
        while (i < e) { val v = g.csrTo[i]; if (rank[u] < rank[v]) upTmp[u].add(v, -1); i++ }
    }

    val inH = BooleanArray(n)
    for (r in order.indices) {
        val v = order[r]
        val H = IntArrayList()
        run {
            var i = g.csrOff[v]; val e = g.csrOff[v + 1]
            while (i < e) { val h = g.csrTo[i]; if (rank[h] > r) { H.add(h); inH[h] = true }; i++ }
        }
        var ix = 0
        while (ix < H.size()) {
            val a = H[ix]
            var i = g.csrOff[a]; val e = g.csrOff[a + 1]
            while (i < e) {
                val b = g.csrTo[i]
                if (b != a && inH[b]) {
                    if (rank[a] < rank[b]) upTmp[a].add(b, v) else upTmp[b].add(a, v)
                }
                i++
            }
            ix++
        }
        ix = 0; while (ix < H.size()) { inH[H[ix]] = false; ix++ }
    }

    val upOff = IntArray(n + 1); var M = 0
    val cleanedTo = Array(n) { IntArrayList() }
    val cleanedMid = Array(n) { IntArrayList() }
    fun prefer(cur: Int, cand: Int, rank: IntArray): Int {
        if (cur == -1 || cand == -1) return -1
        return if (rank[cand] < rank[cur]) cand else cur
    }
    for (u in 0 until n) {
        val (to0, mid0) = upTmp[u].toArrays()
        if (to0.isEmpty()) { upOff[u] = M; continue }
        val idx = (to0.indices).toMutableList(); idx.sortBy { to0[it] }
        var last = -1; var chosen = Int.MAX_VALUE; var cnt = 0
        for (p in idx) {
            val t = to0[p]; val m = mid0[p]
            if (t != last) {
                if (last != -1) { cleanedTo[u].add(last); cleanedMid[u].add(if (chosen == Int.MAX_VALUE) -1 else chosen); cnt++ }
                last = t; chosen = m
            } else { chosen = prefer(chosen, m, rank) }
        }
        if (last != -1) { cleanedTo[u].add(last); cleanedMid[u].add(if (chosen == Int.MAX_VALUE) -1 else chosen); cnt++ }
        upOff[u] = M; M += cnt
    }
    upOff[n] = M
    val upTo = IntArray(M); val upMid = IntArray(M); var p = 0
    for (u in 0 until n) {
        val toL = cleanedTo[u]; val midL = cleanedMid[u]
        var i = 0; while (i < toL.size()) { upTo[p] = toL[i]; upMid[p] = midL[i]; p++; i++ }
    }
    val upW = IntArray(M) { 0 }
    return CCHIndex(rank, upOff, upTo, upMid, upW)
}

private class PairBuf(cap: Int = 8) {
    var to = IntArray(cap); var mid = IntArray(cap); var n = 0
    fun add(t: Int, m: Int) {
        if (n == to.size) { val nc = max(8, to.size * 2); to = to.copyOf(nc); mid = mid.copyOf(nc) }
        to[n] = t; mid[n] = m; n++
    }
}

private fun sortPairsByFirst(to: IntArray, mid: IntArray, len: Int) {
    fun swap(i: Int, j: Int) { val a = to[i]; to[i] = to[j]; to[j] = a; val b = mid[i]; mid[i] = mid[j]; mid[j] = b }
    fun q(l: Int, r: Int) {
        var i = l; var j = r; val p = to[(l + r) ushr 1]
        while (i <= j) {
            while (to[i] < p) i++; while (to[j] > p) j--
            if (i <= j) { swap(i, j); i++; j-- }
        }
        if (l < j) q(l, j); if (i < r) q(i, r)
    }
    if (len > 1) q(0, len - 1)
}

fun contractUltra(g: NavWorldGraph, order: IntArray, policy: MovePolicy): CCHIndex {
    val n = g.nodeCount
    if (n == 0) return CCHIndex(IntArray(0), IntArray(1) { 0 }, IntArray(0), IntArray(0), IntArray(0))

    val rank = IntArray(n); for (r in order.indices) rank[order[r]] = r

    val upCnt = IntArray(n + 1)
    for (u in 0 until n) {
        var i = g.csrOff[u]; val e = g.csrOff[u + 1]
        while (i < e) { val v = g.csrTo[i]; if (rank[u] < rank[v]) upCnt[u + 1]++; i++ }
    }
    for (i in 0 until n) upCnt[i + 1] += upCnt[i]
    val upToInit = IntArray(upCnt[n])
    run {
        val cur = upCnt.clone()
        for (u in 0 until n) {
            var i = g.csrOff[u]; val e = g.csrOff[u + 1]
            while (i < e) { val v = g.csrTo[i]; if (rank[u] < rank[v]) upToInit[cur[u]++] = v; i++ }
        }
    }

    val buf = Array(n) { PairBuf() }
    for (u in 0 until n) {
        var i = upCnt[u]; val e = upCnt[u + 1]
        while (i < e) { buf[u].add(upToInit[i], -1); i++ }
    }

    val inH = BooleanArray(n)
    for (r in order.indices) {
        val v = order[r]
        var s = upCnt[v]; val t = upCnt[v + 1]
        var i = s; while (i < t) { inH[upToInit[i]] = true; i++ }
        i = s
        while (i < t) {
            val a = upToInit[i]
            var p = upCnt[a]; val q = upCnt[a + 1]
            while (p < q) {
                val b = upToInit[p]
                if (inH[b]) {
                    if (rank[a] < rank[b]) buf[a].add(b, v) else buf[b].add(a, v)
                }
                p++
            }
            i++
        }
        i = s; while (i < t) { inH[upToInit[i]] = false; i++ }
    }

    val off = IntArray(n + 1); var M = 0
    val outTo = IntArrayList(); val outMid = IntArrayList()
    fun prefer(cur: Int, cand: Int): Int {
        if (cur == -1 || cand == -1) return -1
        return if (rank[cand] < rank[cur]) cand else cur
    }
    for (u in 0 until n) {
        val b = buf[u]
        sortPairsByFirst(b.to, b.mid, b.n)
        var last = Int.MIN_VALUE; var chosen = Int.MAX_VALUE; var k = 0
        while (k < b.n) {
            val dest = b.to[k]; val m = b.mid[k]
            if (dest != last) {
                if (last != Int.MIN_VALUE) { outTo.add(last); outMid.add(if (chosen == Int.MAX_VALUE) -1 else chosen) }
                last = dest; chosen = m
            } else { chosen = prefer(chosen, m) }
            k++
        }
        if (last != Int.MIN_VALUE) { outTo.add(last); outMid.add(if (chosen == Int.MAX_VALUE) -1 else chosen) }
        off[u] = M; M = outTo.size()
    }
    off[n] = M

    val upToFinal = outTo.toIntArray()
    val upMidFinal = outMid.toIntArray()
    val upW = IntArray(M) { 0 }

    return CCHIndex(rank, off, upToFinal, upMidFinal, upW)
}
