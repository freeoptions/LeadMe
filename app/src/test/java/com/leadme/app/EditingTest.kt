package com.leadme.app

import com.leadme.app.model.*
import org.junit.Assert.*
import org.junit.Test

class EditingTest {
    @Test fun batchAddAndDeleteLastExitUndoRestoresRelations() {
        val original = HomeMap(name = "家")
        val station = MetroStation(name = "春日路", exits = listOf(MetroExit(code = "A", x = 1.0, y = 2.0)))
        val added = EditSession(original).apply(original.copy(stations = listOf(station)))
        val removed = added.apply(added.draft.removeObject(station.exits.single().id))
        assertTrue(removed.draft.stations.isEmpty())
        assertEquals(listOf(station), removed.undo().draft.stations)
        assertEquals(original, removed.undo().undo().draft)
        assertTrue(removed.undo().undo().undoStack.isEmpty())
        assertTrue(original.stations.isEmpty())
    }

    @Test fun deletingWholeStationCanBeUndoneInOneStep() {
        val original = Example.home
        val station = original.stations.single()
        val removed = EditSession(original).apply(original.removeObject(station.id))
        assertTrue(removed.draft.stations.isEmpty())
        assertEquals(original.busPlatforms, removed.draft.busPlatforms)
        assertEquals(original, removed.undo().draft)
    }

    @Test fun dragFramesProduceOneUndoRecordAndNoOpProducesNone() {
        val original = Example.home
        val exit = original.stations.single().exits.first()
        var session = EditSession(original)
        repeat(20) { frame -> session = session.copy(draft = session.draft.movePoint(exit.id, Point(frame.toDouble(), 12.0))) }
        assertTrue(session.undoStack.isEmpty())
        session = session.commitDrag(original)
        assertEquals(1, session.undoStack.size)
        assertEquals(original, session.undo().draft)
        assertEquals(0, EditSession(original).apply(original).undoStack.size)
    }

    @Test fun canonicalExitNumbersRejectDuplicates() {
        assertEquals("A1", exitCode(" a1口 "))
        val original = HomeMap(name = "家", stations = listOf(MetroStation(name = "站", exits = listOf(
            MetroExit(code = "A", x = 0.0, y = 1.0), MetroExit(code = "A", x = 1.0, y = 0.0)))))
        assertThrows(IllegalArgumentException::class.java) { MapValidation.validate(original) }
    }
}
