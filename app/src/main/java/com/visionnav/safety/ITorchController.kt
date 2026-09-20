package com.visionnav.safety

/**
 * Abstraction for controlling physical device torch/flashlight hardware.
 * Decouples visibility assistance from direct CameraX/CameraManager hardware dependencies.
 */
interface ITorchController {
    /**
     * Whether the device has an available physical flashlight/torch unit.
     */
    val isAvailable: Boolean

    /**
     * Toggles the torch on or off.
     *
     * @param enabled True to turn torch ON, false to turn torch OFF.
     * @return True if the operation succeeded, false otherwise.
     */
    fun setTorch(enabled: Boolean): Boolean
}

