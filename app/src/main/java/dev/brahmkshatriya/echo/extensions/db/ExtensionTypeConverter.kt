package dev.brahmkshatriya.echo.extensions.db

import androidx.room.TypeConverter
import dev.brahmkshatriya.echo.common.models.ExtensionType

/**
 * Store [ExtensionType] by name. Besides matching Room's default enum
 * representation, an explicit converter keeps Room 2.6.1 from having to read
 * Kotlin metadata of the `common` module, which is compiled with a newer
 * metadata version than Room 2.6.1 understands.
 */
class ExtensionTypeConverter {
    @TypeConverter
    fun fromExtensionType(value: ExtensionType): String = value.name

    @TypeConverter
    fun toExtensionType(value: String): ExtensionType = ExtensionType.valueOf(value)
}
