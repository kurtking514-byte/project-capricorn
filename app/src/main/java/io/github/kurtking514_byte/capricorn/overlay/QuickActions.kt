package io.github.kurtking514_byte.capricorn.overlay

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.provider.MediaStore
import io.github.kurtking514_byte.capricorn.MainActivity

/** Small actions shared by the overlay's quick controls and the chatbox. */
object QuickActions {

    private var torchOn = false

    /** Toggles the flashlight. Returns the new state, or null if the phone has no flash. */
    fun toggleTorch(context: Context): Boolean? = try {
        val cm = context.getSystemService(CameraManager::class.java)
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        if (id == null) null else {
            torchOn = !torchOn
            cm.setTorchMode(id, torchOn)
            torchOn
        }
    } catch (e: Exception) {
        torchOn = false
        null
    }

    fun openCamera(context: Context): Boolean = start(context, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))

    /** Brings up Capri's home screen (the chatbox). */
    fun openHome(context: Context): Boolean = start(context, Intent(context, MainActivity::class.java))

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }
}
