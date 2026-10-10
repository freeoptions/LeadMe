package com.leadme.app

import com.leadme.app.geometry.*
import com.leadme.app.model.*
import org.junit.Assert.*
import org.junit.Test

class GeometryTest {
    @Test fun coordinatesRoundTripAfterZoomAndPan() {
        val viewport = Viewport(393.0, 720.0, 1.7, Point(-38.0, 69.0))
        val world = Point(172.25, -83.75)
        val restored = viewport.world(viewport.screen(world))
        assertEquals(world.x, restored.x, 1e-9)
        assertEquals(world.y, restored.y, 1e-9)
    }

    @Test fun fitUsesSymmetricBoundsAndKeepsHomeAtCenter() {
        val points = listOf(Point.Origin, Point(400.0, 180.0), Point(-20.0, -50.0))
        val viewport = Viewport(400.0, 700.0).fit(points)
        assertEquals(Point(200.0, 350.0), viewport.screen(Point.Origin))
        points.forEach {
            val screen = viewport.screen(it)
            assertTrue(screen.x in 80.0..320.0)
            assertTrue(screen.y in 85.0..615.0)
        }
    }

    @Test fun pinchKeepsWorldUnderMovingCentroid() {
        val viewport = Viewport(400.0, 700.0, .8, Point(18.0, -30.0))
        val before = Point(120.0, 180.0)
        val after = Point(145.0, 195.0)
        val world = viewport.world(before)
        val next = viewport.transform(before, after, 1.7)
        assertEquals(after.x, next.screen(world).x, 1e-9)
        assertEquals(after.y, next.screen(world).y, 1e-9)
    }

    @Test fun snappingMaintainsRoadSideAndScreenOffset() {
        val road = Road(startX = -200.0, startY = 0.0, endX = 200.0, endY = 0.0)
        val first = snapToRoad(Point(40.0, 10.0), listOf(road), 2.0, SnapMemory())
        assertEquals(14.0, first.point.y, 1e-9)
        val onLine = snapToRoad(Point(40.0, 0.0), listOf(road), 2.0, first.memory)
        assertEquals(14.0, onLine.point.y, 1e-9)
        val opposite = snapToRoad(Point(40.0, -5.0), listOf(road), 2.0, first.memory)
        assertEquals(-14.0, opposite.point.y, 1e-9)
        val unspecified = snapToRoad(Point(40.0, 0.0), listOf(road), 2.0, SnapMemory())
        assertEquals(0.0, unspecified.point.y, 0.0)
    }

    @Test fun snapCandidateDoesNotFlickerAtIntersection() {
        val horizontal = Road(startX = -100.0, startY = 0.0, endX = 100.0, endY = 0.0)
        val vertical = Road(startX = 0.0, startY = -100.0, endX = 0.0, endY = 100.0)
        val snap = snapToRoad(Point(18.0, 20.0), listOf(horizontal, vertical), 1.0, SnapMemory(horizontal.id, 1.0))
        assertEquals(horizontal.id, snap.memory.roadId)
        val switch = snapToRoad(Point(2.0, 25.0), listOf(horizontal, vertical), 1.0, snap.memory)
        assertEquals(vertical.id, switch.memory.roadId)
    }

    @Test fun hitAreaRemains48DpWhenZoomedOut() {
        val exit = MetroExit(code = "A", x = 300.0, y = 100.0)
        val map = HomeMap(name = "家", stations = listOf(MetroStation(name = "站", exits = listOf(exit))))
        val viewport = Viewport(400.0, 700.0, .2)
        val center = viewport.screen(exit.point)
        assertEquals(exit.id, hitTest(map, viewport, center + Point(23.0, 0.0), null)?.id)
        assertNull(hitTest(map, viewport, center + Point(25.0, 0.0), null))
    }
}
