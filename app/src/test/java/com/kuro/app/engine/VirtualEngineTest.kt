package com.kuro.app.engine

import com.kuro.app.ui.screens.addclone.AddCloneViewModel
import com.kuro.engine.CloneHandle
import com.kuro.engine.EngineStatus
import com.kuro.engine.NoOpEngine
import com.kuro.engine.allocateUserSlot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualEngineTest {

    @Test
    fun `creation gated on Ready plus selection`() {
        assertTrue(AddCloneViewModel.canStartCreation(EngineStatus.Ready, "com.example.app"))
        assertFalse(AddCloneViewModel.canStartCreation(EngineStatus.Ready, null))
        assertFalse(AddCloneViewModel.canStartCreation(EngineStatus.Uninitialized, "com.example.app"))
        assertFalse(
            AddCloneViewModel.canStartCreation(
                EngineStatus.Incompatible(21, "Engine not compatible with Android 21 on this device"),
                "com.example.app"
            )
        )
        assertFalse(
            AddCloneViewModel.canStartCreation(
                EngineStatus.Error("boom"),
                "com.example.app"
            )
        )
    }

    @Test
    fun `slot allocator skips owner slot and used slots`() {
        assertEquals(1, allocateUserSlot(emptySet()))
        assertEquals(2, allocateUserSlot(setOf(0, 1)))
        assertEquals(1, allocateUserSlot(setOf(0, 2, 3)))
        assertEquals(4, allocateUserSlot(setOf(0, 1, 2, 3)))
    }

    @Test
    fun `NoOpEngine never crashes and reports uninitialized`() = runBlocking {
        val engine = NoOpEngine("stub")
        assertEquals(EngineStatus.Uninitialized, engine.init())
        assertTrue(engine.listSpacePackages().isEmpty())
        val install = engine.installIntoSpace(
            com.kuro.engine.CloneSource.InstalledApp("com.example.app"),
            "Demo",
            0L
        ) { }
        assertTrue(install.isFailure)
        val launch = engine.launch(CloneHandle("u1", "com.example.app", 1))
        assertTrue(launch is com.kuro.engine.LaunchResult.NotReady)
        assertTrue(engine.remove(CloneHandle("u1", "com.example.app", 1)).isFailure)
    }

    @Test
    fun `NoOpEngine honors fixed status`() = runBlocking {
        val engine = NoOpEngine(
            reason = "stub",
            fixedStatus = EngineStatus.Incompatible(35, "stub")
        )
        assertEquals(EngineStatus.Incompatible(35, "stub"), engine.init())
    }
}
