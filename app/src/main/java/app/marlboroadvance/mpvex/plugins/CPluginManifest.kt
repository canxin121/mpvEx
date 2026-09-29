package app.marlboroadvance.mpvex.plugins

import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.environment.MpvExEnvironment

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

@Serializable
data class CPluginManifest(
  val schemaVersion: Int = SUPPORTED_SCHEMA_VERSION,
  val id: String,
  val name: String,
  val version: String? = null,
  val entry: String,
  val config: List<CPluginConfigField> = emptyList(),
) {
  companion object {
    const val SUPPORTED_SCHEMA_VERSION = 1
  }
}

@Serializable
data class CPluginConfigField(
  val id: String,
  val title: String,
  val description: String? = null,
  val type: String,
  val required: Boolean = false,
  @SerialName("default")
  val defaultValue: JsonElement? = null,
  val minimum: Double? = null,
  val maximum: Double? = null,
  val pattern: String? = null,
  val sensitive: Boolean = false,
  val options: List<CPluginConfigOption> = emptyList(),
  val binding: CPluginConfigBinding,
)

@Serializable
data class CPluginConfigOption(
  val value: String,
  val label: String,
)

@Serializable
data class CPluginConfigBinding(
  val type: String,
  val name: String,
)

data class CPluginDescriptor(
  val fileName: String,
  val manifest: CPluginManifest? = null,
  val manifestFileName: String? = null,
  val manifestError: String? = null,
) {
  val hasManifest: Boolean
    get() = manifestFileName != null
}

data class CPluginValueValidation(
  val normalizedValue: String? = null,
  val error: String? = null,
) {
  val isValid: Boolean
    get() = error == null
}

object CPluginManifestParser {
  private val json =
    Json {
      ignoreUnknownKeys = true
      explicitNulls = false
    }

  private val pluginIdPattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
  private val fieldIdPattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")

  fun parse(
    content: String,
    pluginFileName: String,
    messages: CPluginMessages = AndroidCPluginMessages,
  ): CPluginManifest {
    val manifest = json.decodeFromString<CPluginManifest>(content)
    validate(manifest, pluginFileName, messages)
    return manifest
  }

  fun validate(
    manifest: CPluginManifest,
    pluginFileName: String,
    messages: CPluginMessages = AndroidCPluginMessages,
  ) {
    val errors = mutableListOf<String>()

    if (!isSafeCPluginFileName(pluginFileName)) {
      errors += messages.get(R.string.plugin_invalid_filename, pluginFileName)
    }
    if (manifest.schemaVersion != CPluginManifest.SUPPORTED_SCHEMA_VERSION) {
      errors +=
        messages.get(R.string.plugin_unsupported_schema_version, manifest.schemaVersion, CPluginManifest.SUPPORTED_SCHEMA_VERSION)
    }
    if (!pluginIdPattern.matches(manifest.id)) {
      errors += messages.get(R.string.plugin_invalid_id, manifest.id)
    }
    if (manifest.name.isBlank()) {
      errors += messages.get(R.string.plugin_name_required)
    }
    if (manifest.entry != pluginFileName) {
      errors += messages.get(R.string.plugin_entry_mismatch, manifest.entry, pluginFileName)
    }

    val duplicateFieldIds = manifest.config.groupBy { it.id }.filterValues { it.size > 1 }.keys
    if (duplicateFieldIds.isNotEmpty()) {
      errors += messages.get(R.string.plugin_duplicate_field_ids, duplicateFieldIds.sorted().joinToString())
    }

    val duplicateEnvironmentNames =
      manifest.config
        .groupBy { it.binding.name }
        .filterValues { it.size > 1 }
        .keys
    if (duplicateEnvironmentNames.isNotEmpty()) {
      errors += messages.get(R.string.plugin_duplicate_environment_names, duplicateEnvironmentNames.sorted().joinToString())
    }

    manifest.config.forEach { field ->
      if (!fieldIdPattern.matches(field.id)) {
        errors += messages.get(R.string.plugin_invalid_field_id, field.id)
      }
      if (field.title.isBlank()) {
        errors += messages.get(R.string.plugin_field_title_required, field.id)
      }
      if (field.type !in CPluginFieldType.supported) {
        errors += messages.get(R.string.plugin_field_unsupported_type, field.id, field.type)
      }
      if (field.binding.type != CPluginBindingType.ENVIRONMENT) {
        errors += messages.get(R.string.plugin_field_unsupported_binding, field.id, field.binding.type)
      }
      if (!EnvironmentVariablesCodec.isValidName(field.binding.name)) {
        errors += messages.get(R.string.plugin_field_invalid_environment, field.id, field.binding.name)
      } else if (field.binding.name in MpvExEnvironment.reservedNames) {
        errors += messages.get(R.string.plugin_field_reserved_environment, field.id, field.binding.name)
      }
      if (field.minimum != null && field.maximum != null && field.minimum > field.maximum) {
        errors += messages.get(R.string.plugin_field_min_exceeds_max, field.id)
      }
      if (
        (field.minimum != null || field.maximum != null) &&
        field.type !in setOf(CPluginFieldType.INTEGER, CPluginFieldType.NUMBER)
      ) {
        errors += messages.get(R.string.plugin_field_bounds_wrong_type, field.id)
      }
      if (field.pattern != null && field.type !in setOf(CPluginFieldType.STRING, CPluginFieldType.PATH)) {
        errors += messages.get(R.string.plugin_field_pattern_wrong_type, field.id)
      }
      if (field.options.isNotEmpty() && field.type != CPluginFieldType.ENUM) {
        errors += messages.get(R.string.plugin_field_options_wrong_type, field.id)
      }
      if (field.type == CPluginFieldType.ENUM) {
        if (field.options.isEmpty()) {
          errors += messages.get(R.string.plugin_enum_options_required, field.id)
        }
        val duplicateOptions = field.options.groupBy { it.value }.filterValues { it.size > 1 }.keys
        if (duplicateOptions.isNotEmpty()) {
          errors += messages.get(R.string.plugin_enum_duplicate_options, field.id)
        }
        if (field.options.any { it.value.isBlank() || it.label.isBlank() }) {
          errors += messages.get(R.string.plugin_enum_blank_option, field.id)
        }
      }
      field.pattern?.let { pattern ->
        runCatching { Regex(pattern) }
          .onFailure { errors += messages.get(R.string.plugin_field_invalid_regex, field.id) }
      }

      if (field.defaultValue != null && field.defaultValue !is JsonNull) {
        val defaultString = field.defaultValueAsString()
        if (defaultString == null) {
          errors += messages.get(R.string.plugin_field_invalid_default, field.id)
        } else {
          val result = CPluginValueValidator.validate(field, defaultString, messages)
          result.error?.let { errors += messages.get(R.string.plugin_field_default_error, field.id, it) }
        }
      }
    }

    require(errors.isEmpty()) { errors.joinToString("; ") }
  }
}

object CPluginValueValidator {
  fun validate(
    field: CPluginConfigField,
    rawValue: String?,
    messages: CPluginMessages = AndroidCPluginMessages,
  ): CPluginValueValidation {
    if (rawValue == null || rawValue.isBlank()) {
      return if (field.required) {
        CPluginValueValidation(error = messages.get(R.string.plugin_value_required))
      } else {
        CPluginValueValidation(normalizedValue = null)
      }
    }

    return when (field.type) {
      CPluginFieldType.STRING,
      CPluginFieldType.PATH,
        -> validateString(field, rawValue, messages)
      CPluginFieldType.BOOLEAN -> validateBoolean(rawValue, messages)
      CPluginFieldType.INTEGER -> validateInteger(field, rawValue, messages)
      CPluginFieldType.NUMBER -> validateNumber(field, rawValue, messages)
      CPluginFieldType.ENUM -> validateEnum(field, rawValue, messages)
      else -> CPluginValueValidation(error = messages.get(R.string.plugin_unsupported_type, field.type))
    }
  }

  private fun validateString(
    field: CPluginConfigField,
    rawValue: String,
    messages: CPluginMessages,
  ): CPluginValueValidation {
    val pattern = field.pattern ?: return CPluginValueValidation(normalizedValue = rawValue)
    val matches = runCatching { Regex(pattern).matches(rawValue) }.getOrDefault(false)
    return if (matches) {
      CPluginValueValidation(normalizedValue = rawValue)
    } else {
      CPluginValueValidation(error = messages.get(R.string.plugin_value_pattern_mismatch))
    }
  }

  private fun validateBoolean(rawValue: String, messages: CPluginMessages): CPluginValueValidation =
    when (rawValue.trim().lowercase()) {
      "true" -> CPluginValueValidation(normalizedValue = "true")
      "false" -> CPluginValueValidation(normalizedValue = "false")
      else -> CPluginValueValidation(error = messages.get(R.string.plugin_expected_boolean))
    }

  private fun validateInteger(
    field: CPluginConfigField,
    rawValue: String,
    messages: CPluginMessages,
  ): CPluginValueValidation {
    val value = rawValue.trim().toLongOrNull()
      ?: return CPluginValueValidation(error = messages.get(R.string.plugin_expected_integer))
    return validateRange(field, value.toDouble(), value.toString(), messages)
  }

  private fun validateNumber(
    field: CPluginConfigField,
    rawValue: String,
    messages: CPluginMessages,
  ): CPluginValueValidation {
    val value = rawValue.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
      ?: return CPluginValueValidation(error = messages.get(R.string.plugin_expected_number))
    return validateRange(field, value, rawValue.trim(), messages)
  }

  private fun validateRange(
    field: CPluginConfigField,
    numericValue: Double,
    normalizedValue: String,
    messages: CPluginMessages,
  ): CPluginValueValidation {
    if (field.minimum != null && numericValue < field.minimum) {
      return CPluginValueValidation(error = messages.get(R.string.plugin_value_minimum, field.minimum.formatForMessage()))
    }
    if (field.maximum != null && numericValue > field.maximum) {
      return CPluginValueValidation(error = messages.get(R.string.plugin_value_maximum, field.maximum.formatForMessage()))
    }
    return CPluginValueValidation(normalizedValue = normalizedValue)
  }

  private fun validateEnum(
    field: CPluginConfigField,
    rawValue: String,
    messages: CPluginMessages,
  ): CPluginValueValidation =
    if (field.options.any { it.value == rawValue }) {
      CPluginValueValidation(normalizedValue = rawValue)
    } else {
      CPluginValueValidation(error = messages.get(R.string.plugin_select_available_value))
    }
}

object CPluginConfigurationCodec {
  private val json =
    Json {
      ignoreUnknownKeys = true
      explicitNulls = false
    }

  fun decode(content: String): Map<String, Map<String, String>> =
    runCatching {
      if (content.isBlank()) emptyMap() else json.decodeFromString<Map<String, Map<String, String>>>(content)
    }.getOrDefault(emptyMap())

  fun encode(values: Map<String, Map<String, String>>): String = json.encodeToString(values)

  fun updatePlugin(
    content: String,
    pluginId: String,
    pluginValues: Map<String, String>,
  ): String = encode(decode(content) + (pluginId to pluginValues))
}

object CPluginFieldType {
  const val STRING = "string"
  const val BOOLEAN = "boolean"
  const val INTEGER = "integer"
  const val NUMBER = "number"
  const val ENUM = "enum"
  const val PATH = "path"

  val supported = setOf(STRING, BOOLEAN, INTEGER, NUMBER, ENUM, PATH)
}

object CPluginBindingType {
  const val ENVIRONMENT = "environment"
}

fun CPluginConfigField.defaultValueAsString(): String? {
  val value = defaultValue ?: return null
  if (value is JsonNull) return null
  val primitive = value as? JsonPrimitive ?: return null

  return when (type) {
    CPluginFieldType.BOOLEAN ->
      if (primitive.isString) null else primitive.booleanOrNull?.toString()
    CPluginFieldType.INTEGER ->
      if (primitive.isString) null else primitive.longOrNull?.toString()
    CPluginFieldType.NUMBER ->
      if (primitive.isString) null else primitive.doubleOrNull?.let { primitive.content }
    CPluginFieldType.STRING,
    CPluginFieldType.PATH,
    CPluginFieldType.ENUM,
      -> if (primitive.isString) primitive.content else null
    else -> null
  }
}

private fun Double.formatForMessage(): String =
  if (this % 1.0 == 0.0) toLong().toString() else toString()

fun isSafeCPluginFileName(fileName: String): Boolean =
  fileName.isNotBlank() &&
    fileName.endsWith(".so", ignoreCase = true) &&
    '/' !in fileName &&
    '\\' !in fileName &&
    fileName !in setOf(".", "..")
