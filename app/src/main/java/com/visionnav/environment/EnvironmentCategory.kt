package com.visionnav.environment

import com.visionnav.vision.DetectionClass

/**
 * Environmental object classification taxonomy for pedestrian navigation.
 *
 * Distinguishes between classes currently supported by the on-device vision model
 * versus potential future structural classes, ensuring the system never pretends
 * unsupported classes exist.
 */
enum class EnvironmentCategory(val label: String, val isCurrentlySupported: Boolean) {
    // Currently supported active vision model classes
    PERSON("person", true),
    BICYCLE("bicycle", true),
    MOTORCYCLE("motorcycle", true),
    CAR("car", true),
    BUS("bus", true),
    TRUCK("truck", true),

    // Potential future structural/infrastructure categories (explicitly unsupported currently)
    POSSIBLE_DOOR("door", false),
    POSSIBLE_STAIRS("stairs", false),
    POSSIBLE_CROSSWALK("crosswalk", false),
    POSSIBLE_TRAFFIC_INFRASTRUCTURE("traffic infrastructure", false);

    val isVehicle: Boolean
        get() = this == CAR || this == BUS || this == TRUCK || this == MOTORCYCLE || this == BICYCLE

    val isHeavyVehicle: Boolean
        get() = this == CAR || this == BUS || this == TRUCK

    val isPedestrian: Boolean
        get() = this == PERSON

    companion object {
        fun fromDetectionClass(clazz: DetectionClass): EnvironmentCategory {
            return when (clazz) {
                DetectionClass.PERSON -> PERSON
                DetectionClass.BICYCLE -> BICYCLE
                DetectionClass.MOTORCYCLE -> MOTORCYCLE
                DetectionClass.CAR -> CAR
                DetectionClass.BUS -> BUS
                DetectionClass.TRUCK -> TRUCK
            }
        }
    }
}

