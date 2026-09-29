package app.marlboroadvance.mpvex.plugins

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** User-defined process environment, stored separately from plugin manifest values. */
object EnvironmentVariablesCodec {
  private val namePattern = Regex("[A-Za-z_][A-Za-z0-9_]*")

  fun isValidName(name: String): Boolean = namePattern.matches(name)

  fun isValidValue(value: String): Boolean = '\u0000' !in value

  fun decode(content: String): Map<String, String> =
    runCatching {
      if (content.isBlank()) emptyMap() else Json.decodeFromString<Map<String, String>>(content)
    }.getOrDefault(emptyMap()).filter { (name, value) -> isValidName(name) && isValidValue(value) }

  fun encode(values: Map<String, String>): String {
    require(values.all { (name, value) -> isValidName(name) && isValidValue(value) })
    return Json.encodeToString<Map<String, String>>(values.toSortedMap())
  }
}
