package com.kuro.app.engine

import com.kuro.app.ui.screens.addclone.AddCloneViewModel
import com.kuro.engine.CloneHandle
import com.kuro.engine.EngineStatus
import com.kuro.engine.NoOpEngine
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
        val launch = engine.launch(CloneHandle("c1", "com.example.app"))
        assertTrue(launch is com.kuro.engine.LaunchResult.NotReady)
        assertTrue(engine.remove(CloneHandle("c1", "com.example.app")).isFailure)
    }
}
