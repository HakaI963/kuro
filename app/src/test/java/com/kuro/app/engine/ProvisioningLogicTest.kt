package com.kuro.app.engine

import com.kuro.app.domain.models.ProvisionReason
import com.kuro.app.domain.models.ProvisioningState
import com.kuro.app.ui.screens.addclone.AddCloneViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvisioningLogicTest {

    @Test
    fun `RESULT_OK maps to Active`() {
        val state = ProvisioningManager.mapActivityResult(android.app.Activity.RESULT_OK)
        assertEquals(ProvisioningState.Active, state)
    }

    @Test
    fun `cancelled result maps to Blocked USER_CANCELLED`() {
        val state = ProvisioningManager.mapActivityResult(android.app.Activity.RESULT_CANCELED)
        assertEquals(ProvisioningState.Blocked(ProvisionReason.USER_CANCELLED), state)
    }

    @Test
    fun `mapFailure preserves reason`() {
        assertEquals(
            ProvisioningState.Blocked(ProvisionReason.ALREADY_OWNED_BY_ANOTHER_APP),
            ProvisioningManager.mapFailure(ProvisionReason.ALREADY_OWNED_BY_ANOTHER_APP)
        )
        assertEquals(
            ProvisioningState.Blocked(ProvisionReason.POLICY_BLOCKED),
            ProvisioningManager.mapFailure(ProvisionReason.POLICY_BLOCKED)
        )
    }

    @Test
    fun `creation gated on Active plus selection`() {
        assertTrue(AddCloneViewModel.canStartCreation(ProvisioningState.Active, "com.example.app"))
        assertFalse(AddCloneViewModel.canStartCreation(ProvisioningState.Active, null))
        assertFalse(AddCloneViewModel.canStartCreation(ProvisioningState.NotSetup, "com.example.app"))
        assertFalse(
            AddCloneViewModel.canStartCreation(
                ProvisioningState.Blocked(ProvisionReason.USER_CANCELLED),
                "com.example.app"
            )
        )
        assertFalse(AddCloneViewModel.canStartCreation(ProvisioningState.Checking, "com.example.app"))
        assertFalse(
            AddCloneViewModel.canStartCreation(ProvisioningState.ProfileExistsNotOwner, "com.example.app")
        )
    }

    @Test
    fun `mapProvisioning helper mirrors engine availability`() {
        assertEquals(ProvisioningState.Active, AddCloneViewModel.mapProvisioning(true))
        assertEquals(ProvisioningState.NotSetup, AddCloneViewModel.mapProvisioning(false))
    }
}
