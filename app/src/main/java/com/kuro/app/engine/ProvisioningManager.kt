package com.kuro.app.engine

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import com.kuro.app.admin.KuroDeviceAdminReceiver
import com.kuro.app.domain.models.ProvisionReason
import com.kuro.app.domain.models.ProvisioningState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ProvisioningManager {

    fun adminComponent(context: Context): ComponentName =
        ComponentName(context, KuroDeviceAdminReceiver::class.java)

    suspend fun currentState(context: Context): ProvisioningState = withContext(Dispatchers.IO) {
        try {
            val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return@withContext ProvisioningState.Blocked(ProvisionReason.UNKNOWN)
            if (dpm.isProfileOwnerApp(context.packageName)) {
                return@withContext ProvisioningState.Active
            }
            val um = context.getSystemService(UserManager::class.java)
            val hasManagedProfile = try {
                um?.userProfiles?.size?.let { it > 1 } == true
            } catch (_: Exception) {
                false
            }
            if (hasManagedProfile) {
                ProvisioningState.ProfileExistsNotOwner
            } else {
                ProvisioningState.NotSetup
            }
        } catch (_: Exception) {
            ProvisioningState.Blocked(ProvisionReason.UNKNOWN)
        }
    }

    fun canProvision(context: Context): Boolean {
        return try {
            val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
            if (dpm.isProfileOwnerApp(context.packageName)) return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
            true
        } catch (_: Exception) {
            false
        }
    }

    fun buildProvisionIntent(context: Context): Intent? {
        if (!canProvision(context)) return null
        return Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).apply {
            putExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                adminComponent(context)
            )
        }
    }

    fun mapActivityResult(resultCode: Int): ProvisioningState {
        return if (resultCode == Activity.RESULT_OK) {
            ProvisioningState.Active
        } else {
            ProvisioningState.Blocked(ProvisionReason.USER_CANCELLED)
        }
    }

    fun mapFailure(reason: ProvisionReason): ProvisioningState =
        ProvisioningState.Blocked(reason)
}
