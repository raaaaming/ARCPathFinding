package kr.raaaaming.arcpathfinding.path

interface PathService {
    fun getPath(worldName: String, sx: Int, sy: Int, sz: Int, tx: Int, ty: Int, tz: Int): List<PathNode>
}
