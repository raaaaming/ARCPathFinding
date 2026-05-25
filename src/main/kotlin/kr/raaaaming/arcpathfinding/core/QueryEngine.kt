package kr.raaaaming.arcpathfinding.core

import kr.raaaaming.arcpathfinding.cch.CCHIndex
import kr.raaaaming.arcpathfinding.graph.NavWorldGraph
import java.util.PriorityQueue

open class QueryEngine(
    open val g: NavWorldGraph,
    open val idx: CCHIndex
) {
    fun nearestNode(x: Int, y: Int, z: Int): Int {
        var best = -1; var bestD = Long.MAX_VALUE
        val a = g.xyzt
        for (u in 0 until g.nodeCount) {
            val dx = (a[u * 3] - x).toLong()
            val dy = (a[u * 3 + 1] - y).toLong()
            val dz = (a[u * 3 + 2] - z).toLong()
            val d2 = dx * dx + dy * dy + dz * dz
            if (d2 < bestD) { bestD = d2; best = u }
        }
        return best
    }

    fun edgeIndex(u: Int, v: Int): Int {
        val off = idx.upOff; val to = idx.upTo
        var lo = off[u]; var hi = off[u + 1] - 1
        while (lo <= hi) {
            val m = (lo + hi) ushr 1; val t = to[m]
            if (t == v) return m
            if (t < v) lo = m + 1 else hi = m - 1
        }
        return -1
    }

    private fun unpackInclusive(lo: Int, hi: Int): IntArray {
        val eid = edgeIndex(lo, hi)
        require(eid >= 0) { "Upward edge ($lo->$hi) not found for unpack" }
        val mid = idx.upMid[eid]
        if (mid < 0) return intArrayOf(lo, hi)
        val left = unpackInclusive(minOf(mid, lo), maxOf(mid, lo))
        val right = unpackInclusive(minOf(mid, hi), maxOf(mid, hi))
        val out = IntArray(left.size + right.size - 1)
        System.arraycopy(left, 0, out, 0, left.size)
        System.arraycopy(right, 1, out, left.size, right.size - 1)
        return out
    }

    private fun routeByNodeIds(s: Int, t: Int): List<IntArray> {
        if (s == t) return listOf(intArrayOf(g.xyzt[s * 3], g.xyzt[s * 3 + 1], g.xyzt[s * 3 + 2]))

        val n = g.nodeCount; val INF = Int.MAX_VALUE / 4
        data class QN(val d: Int, val u: Int)

        val distF = IntArray(n) { INF }; val distB = IntArray(n) { INF }
        val prevF = IntArray(n) { -1 }; val prevB = IntArray(n) { -1 }
        val pqF = PriorityQueue<QN>(compareBy { it.d })
        val pqB = PriorityQueue<QN>(compareBy { it.d })
        distF[s] = 0; pqF.add(QN(0, s))
        distB[t] = 0; pqB.add(QN(0, t))
        var best = INF; var meet = -1

        while (pqF.isNotEmpty() || pqB.isNotEmpty()) {
            if (pqF.isNotEmpty()) {
                val cur = pqF.poll()
                if (cur.d == distF[cur.u]) {
                    val u = cur.u
                    if (distF[u] + distB[u] < best) { best = distF[u] + distB[u]; meet = u }
                    var i = idx.upOff[u]; val e = idx.upOff[u + 1]
                    while (i < e) {
                        val v = idx.upTo[i]; val w = idx.upW[i]
                        if (w < INF && distF[v] > distF[u] + w) {
                            distF[v] = distF[u] + w; prevF[v] = u; pqF.add(QN(distF[v], v))
                        }
                        i++
                    }
                }
            }
            if (pqB.isNotEmpty()) {
                val cur = pqB.poll()
                if (cur.d == distB[cur.u]) {
                    val u = cur.u
                    if (distF[u] + distB[u] < best) { best = distF[u] + distB[u]; meet = u }
                    var i = idx.upOff[u]; val e = idx.upOff[u + 1]
                    while (i < e) {
                        val v = idx.upTo[i]; val w = idx.upW[i]
                        if (w < INF && distB[v] > distB[u] + w) {
                            distB[v] = distB[u] + w; prevB[v] = u; pqB.add(QN(distB[v], v))
                        }
                        i++
                    }
                }
            }
        }

        if (meet == -1) return emptyList()

        val upPath = ArrayList<Int>()
        run {
            val stack = ArrayList<Int>(); var u = meet
            while (u != -1) { stack.add(u); u = prevF[u] }
            for (i in stack.size - 1 downTo 0) upPath.add(stack[i])
        }
        run {
            var u = prevB[meet]
            while (u != -1) { upPath.add(u); u = prevB[u] }
        }

        val seq = ArrayList<Int>()
        if (upPath.isNotEmpty()) {
            seq.add(upPath[0])
            for (i in 0 until upPath.size - 1) {
                val a = upPath[i]; val b = upPath[i + 1]
                val ra = idx.rank[a]; val rb = idx.rank[b]
                val lo = if (ra < rb) a else b; val hi = if (ra < rb) b else a
                val expanded = unpackInclusive(lo, hi)
                if (ra < rb) {
                    for (k in 1 until expanded.size) seq.add(expanded[k])
                } else {
                    for (k in expanded.size - 2 downTo 0) seq.add(expanded[k])
                }
            }
        }

        val out = ArrayList<IntArray>(seq.size)
        for (u in seq) out.add(intArrayOf(g.xyzt[u * 3], g.xyzt[u * 3 + 1], g.xyzt[u * 3 + 2]))
        return out
    }

    fun route(sx: Int, sy: Int, sz: Int, tx: Int, ty: Int, tz: Int): List<IntArray> {
        val s = nearestNode(sx, sy, sz); val t = nearestNode(tx, ty, tz)
        if (s < 0 || t < 0) return emptyList()
        return routeByNodeIds(s, t)
    }

    fun routeWithSnap(
        snapFn: (Int, Int, Int) -> Int,
        sx: Int, sy: Int, sz: Int,
        tx: Int, ty: Int, tz: Int
    ): List<IntArray> {
        val s = snapFn(sx, sy, sz); val t = snapFn(tx, ty, tz)
        if (s < 0 || t < 0) return emptyList()
        return routeByNodeIds(s, t)
    }
}
