package com.leadme.app.model

/** 仅存在内存，不进入 Repository，也不参加导出。 */
object Example {
    val home = HomeMap(
        name = "家的周围 · 示例",
        roads = listOf(
            Road(startX = -160.0, startY = 100.0, endX = 160.0, endY = 100.0),
            Road(startX = -65.0, startY = -170.0, endX = -65.0, endY = 200.0),
        ),
        stations = listOf(MetroStation(name = "春日路站", exits = listOf(
            MetroExit(code = "A", x = -110.0, y = 145.0),
            MetroExit(code = "C", x = -110.0, y = 55.0, note = "靠近家这一侧"),
            MetroExit(code = "E", x = -20.0, y = 165.0),
        ))),
        busPlatforms = listOf(
            BusPlatform(name = "春日路", directionLabel = "往市中心", x = 95.0, y = 145.0),
            BusPlatform(name = "春日路", directionLabel = "回程", x = 95.0, y = 55.0),
        ),
    )
}
