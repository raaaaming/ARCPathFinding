package kr.raaaaming.arcpathfinding.path

import kr.raaaaming.arcpathfinding.ARCPathFindingModule

class PathServiceImpl : PathService {
    override fun getPath(worldName: String, sx: Int, sy: Int, sz: Int, tx: Int, ty: Int, tz: Int): List<PathNode> {
        val engine = ARCPathFindingModule.engines[worldName] ?: return emptyList()
        return engine.routeFast(sx, sy, sz, tx, ty, tz).map { c -> PathNode(c[0], c[1], c[2]) }
    }
}
