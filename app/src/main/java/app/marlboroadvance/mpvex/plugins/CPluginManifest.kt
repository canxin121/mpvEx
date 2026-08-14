package app.marlboroadvance.mpvex.plugins

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
  private val environmentNamePattern = Regex("[A-Za-z_][A-Za-z0-9_]*")

  fun parse(
    content: String,
    pluginFileName: String,
  ): CPluginManifest {
    val manifest = json.decodeFromString<CPluginManifest>(content)
    validate(manifest, pluginFileName)
    return manifest
  }

  fun validate(
    manifest: CPluginManifest,
    pluginFileName: String,
  ) {
    val errors = mutableListOf<String>()

    if (!isSafeCPluginFileName(pluginFileName)) {
      errors += "Invalid plugin filename '$pluginFileName'"
    }
    if (manifest.schemaVersion != CPluginManifest.SUPPORTED_SCHEMA_VERSION) {
      errors +=
        "Unsupported schemaVersion ${manifest.schemaVersion}; " +
        "expected ${CPluginManifest.SUPPORTED_SCHEMA_VERSION}"
    }
    if (!pluginIdPattern.matches(manifest.id)) {
      errors += "Invalid plugin id '${manifest.id}'"
    }
    if (manifest.name.isBlank()) {
      errors += "Plugin name must not be blank"
    }
    if (manifest.entry != pluginFileName) {
      errors += "Manifest entry '${manifest.entry}' does not match '$pluginFileName'"
    }

    val duplicateFieldIds = manifest.config.groupBy { it.id }.filterValues { it.size > 1 }.keys
    if (duplicateFieldIds.isNotEmpty()) {
      errors += "Duplicate field ids: ${duplicateFieldIds.sorted().joinToString()}"
    }

    val duplicateEnvironmentNames =
      manifest.config
        .groupBy { it.binding.name }
        .filterValues { it.size > 1 }
        .keys
    if (duplicateEnvironmentNames.isNotEmpty()) {
      errors += "Duplicate environment names: ${duplicateEnvironmentNames.sorted().joinToString()}"
    }

    manifest.config.forEach { field ->
      if (!fieldIdPattern.matches(field.id)) {
        errors += "Invalid field id '${field.id}'"
      }
      if (field.title.isBlank()) {
        errors += "Field '${field.id}' has a blank title"
      }
      if (field.type !in CPluginFieldType.supported) {
        errors += "Field '${field.id}' has unsupported type '${field.type}'"
      }
      if (field.binding.type != CPluginBindingType.ENVIRONMENT) {
        errors += "Field '${field.id}' has unsupported binding type '${field.binding.type}'"
      }
      if (!environmentNamePattern.matches(field.binding.name)) {
        errors += "Field '${field.id}' has invalid environment name '${field.binding.name}'"
      }
      if (field.minimum != null && field.maximum != null && field.minimum > field.maximum) {
        errors += "Field '${field.id}' has minimum greater than maximum"
      }
      if (
        (field.minimum != null || field.maximum != null) &&
        field.type !in setOf(CPluginFieldType.INTEGER, CPluginFieldType.NUMBER)
      ) {
        errors += "Field '${field.id}' uses numeric bounds with a non-numeric type"
      }
      if (field.pattern != null && field.type !in setOf(CPluginFieldType.STRING, CPluginFieldType.PATH)) {
        errors += "Field '${field.id}' uses a pattern with a non-text type"
      }
      if (field.options.isNotEmpty() && field.type != CPluginFieldType.ENUM) {
        errors += "Field '${field.id}' defines options but is not an enum"
      }
      if (field.type == CPluginFieldType.ENUM) {
        if (field.options.isEmpty()) {
          errors += "Enum field '${field.id}' must define at least one option"
        }
        val duplicateOptions = field.options.groupBy { it.value }.filterValues { it.size > 1 }.keys
        if (duplicateOptions.isNotEmpty()) {
          errors += "Enum field '${field.id}' has duplicate option values"
        }
        if (field.options.any { it.value.isBlank() || it.label.isBlank() }) {
          errors += "Enum field '${field.id}' has a blank option value or label"
        }
      }
      field.pattern?.let { pattern ->
        runCatching { Regex(pattern) }
          .onFailure { errors += "Field '${field.id}' has an invalid regular expression" }
      }

      if (field.defaultValue != null && field.defaultValue !is JsonNull) {
        val defaultString = field.defaultValueAsString()
        if (defaultString == null) {
          errors += "Field '${field.id}' has an invalid default value"
        } else {
          val result = CPluginValueValidator.validate(field, defaultString)
          result.error?.let { errors += "Field '${field.id}' default: $it" }
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
  ): CPluginValueValidation {
    if (rawValue == null || rawValue.isBlank()) {
      return if (field.required) {
        CPluginValueValidation(error = "A value is required")
      } else {
        CPluginValueValidation(normalizedValue = null)
      }
    }

    return when (field.type) {
      CPluginFieldType.STRING,
      CPluginFieldType.PATH,
        -> validateString(field, rawValue)
      CPluginFieldType.BOOLEAN -> validateBoolean(rawValue)
      CPluginFieldType.INTEGER -> validateInteger(field, rawValue)
      CPluginFieldType.NUMBER -> validateNumber(field, rawValue)
      CPluginFieldType.ENUM -> validateEnum(field, rawValue)
      else -> CPluginValueValidation(error = "Unsupported field type '${field.type}'")
    }
  }

  private fun validateString(
    field: CPluginConfigField,
    rawValue: String,
  ): CPluginValueValidation {
    val pattern = field.pattern ?: return CPluginValueValidation(normalizedValue = rawValue)
    val matches = runCatching { Regex(pattern).matches(rawValue) }.getOrDefault(false)
    return if (matches) {
      CPluginValueValidation(normalizedValue = rawValue)
    } else {
      CPluginValueValidation(error = "Value does not match the required pattern")
    }
  }

  private fun validateBoolean(rawValue: String): CPluginValueValidation =
    when (rawValue.trim().lowercase()) {
      "true" -> CPluginValueValidation(normalizedValue = "true")
      "false" -> CPluginValueValidation(normalizedValue = "false")
      else -> CPluginValueValidation(error = "Expected true or false")
    }

  private fun validateInteger(
    field: CPluginConfigField,
    rawValue: String,
  ): CPluginValueValidation {
    val value = rawValue.trim().toLongOrNull()
      ?: return CPluginValueValidation(error = "Expected an integer")
    return validateRange(field, value.toDouble(), value.toString())
  }

  private fun validateNumber(
    field: CPluginConfigField,
    rawValue: String,
  ): CPluginValueValidation {
    val value = rawValue.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
      ?: return CPluginValueValidation(error = "Expected a finite number")
    return validateRange(field, value, rawValue.trim())
  }

  private fun validateRange(
    field: CPluginConfigField,
    numericValue: Double,
    normalizedValue: String,
  ): CPluginValueValidation {
    if (field.minimum != null && numericValue < field.minimum) {
      return CPluginValueValidation(error = "Value must be at least ${field.minimum.formatForMessage()}")
    }
    if (field.maximum != null && numericValue > field.maximum) {
      return CPluginValueValidation(error = "Value must be at most ${field.maximum.formatForMessage()}")
    }
    return CPluginValueValidation(normalizedValue = normalizedValue)
  }

  private fun validateEnum(
    field: CPluginConfigField,
    rawValue: String,
  ): CPluginValueValidation =
    if (field.options.any { it.value == rawValue }) {
      CPluginValueValidation(normalizedValue = rawValue)
    } else {
      CPluginValueValidation(error = "Select one of the available values")
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
