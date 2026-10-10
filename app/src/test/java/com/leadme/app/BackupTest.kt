package com.leadme.app

import com.leadme.app.data.backup.*
import com.leadme.app.model.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class BackupTest {
    @Test fun backupRoundTripPreservesMapsAndArchiveState() {
        val current = Example.home
        val archived = HomeMap(name = "旧家", archivedAt = 12345L)
        val document = BackupDocument(currentHomeId = current.id, homes = listOf(current, archived))
        assertEquals(document, BackupCodec.decode(BackupCodec.encode(document)))
    }

    @Test fun missingIdsAreRejectedInsteadOfGenerated() {
        val document = BackupDocument(homes = listOf(HomeMap(name = "家")))
        val root = Json.parseToJsonElement(BackupCodec.encode(document)).jsonObject
        val missingId = JsonObject(root + ("homes" to JsonArray(listOf(JsonObject(root.getValue("homes").jsonArray[0].jsonObject - "id")))))
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(missingId.toString()) }
    }

    @Test fun unsupportedVersionAndBrokenCurrentHomeAreRejected() {
        val document = BackupDocument(homes = emptyList())
        val unsupported = BackupCodec.encode(document).replace("\"schemaVersion\": 1", "\"schemaVersion\": 99")
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(unsupported) }
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.validate(document.copy(currentHomeId = newId())) }
    }

    @Test fun globallyDuplicatedIdsAndNonFiniteCoordinatesAreRejected() {
        val home = HomeMap(name = "家")
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.validate(BackupDocument(homes = listOf(home, home.copy(name = "另一个家"))))
        }
        val invalid = home.copy(roads = listOf(Road(startX = Double.NaN, startY = 0.0, endX = 0.0, endY = 1.0)))
        assertThrows(IllegalArgumentException::class.java) { MapValidation.validate(invalid) }
    }
}
