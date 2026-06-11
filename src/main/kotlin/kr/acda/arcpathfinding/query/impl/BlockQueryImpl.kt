package kr.acda.arcpathfinding.query.impl

import kr.acda.arcpathfinding.query.BlockQuery
import org.bukkit.Material
import org.bukkit.World

class BlockQueryImpl(private val world: World) : BlockQuery {
    private fun type(x: Int, y: Int, z: Int) = world.getBlockAt(x, y, z).type

    override fun solid(x: Int, y: Int, z: Int): Boolean = type(x, y, z).isSolid

    override fun passable(x: Int, y: Int, z: Int): Boolean {
        val t = type(x, y, z)
        return t.isAir || !t.isSolid
    }

    override fun liquid(x: Int, y: Int, z: Int): Boolean {
        val t = type(x, y, z)
        return t == Material.WATER || t == Material.LAVA
    }

    override fun ladder(x: Int, y: Int, z: Int): Boolean = type(x, y, z) == Material.LADDER
}
