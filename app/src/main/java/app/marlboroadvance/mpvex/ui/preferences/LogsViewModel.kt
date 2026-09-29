package app.marlboroadvance.mpvex.ui.preferences

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.marlboroadvance.mpvex.utils.logging.InMemoryLogBuffer
import app.marlboroadvance.mpvex.utils.logging.LogRecord
import app.marlboroadvance.mpvex.utils.logging.LogSource
import app.marlboroadvance.mpvex.utils.logging.renderLogRecords
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Minimum log level to show, in `android.util.Log` terms. */
private val DEFAULT_MINIMUM_PRIORITY = Log.DEBUG

/**
 * Filters for the Logs screen.
 *
 * The records themselves come from the in-memory buffer, so the screen shows
 * history without touching the file. Filtering runs on a snapshot of the buffer
 * each time the buffer publishes, which keeps the list bounded.
 */
class LogsViewModel : ViewModel() {
  private val _search = MutableStateFlow("")
  val search: StateFlow<String> = _search.asStateFlow()

  private val _tagFilter = MutableStateFlow("")
  val tagFilter: StateFlow<String> = _tagFilter.asStateFlow()

  private val _minimumPriority = MutableStateFlow(DEFAULT_MINIMUM_PRIORITY)
  val minimumPriority: StateFlow<Int> = _minimumPriority.asStateFlow()

  private val _sourceFilter = MutableStateFlow<LogSource?>(null)
  val sourceFilter: StateFlow<LogSource?> = _sourceFilter.asStateFlow()

  private val _follow = MutableStateFlow(true)
  val follow: StateFlow<Boolean> = _follow.asStateFlow()

  private val _paused = MutableStateFlow(false)
  val paused: StateFlow<Boolean> = _paused.asStateFlow()

  /** Frozen copy of the buffer, used while the tail is paused. */
  private val _frozen = MutableStateFlow<List<LogRecord>?>(null)

  private val filters =
    combine(_search, _tagFilter, _minimumPriority, _sourceFilter) { search, tag, priority, source ->
      Filters(search = search, tag = tag, minimumPriority = priority, source = source)
    }

  private val source = combine(InMemoryLogBuffer.lines, _paused) { records, paused ->
    if (paused) null else records
  }

  val records: StateFlow<List<LogRecord>> =
    combine(source, _frozen, filters) { live, frozen, filters ->
      (live ?: frozen ?: emptyList()).filter(filters::matches)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  fun setSearch(value: String) {
    _search.value = value
  }

  fun setTagFilter(value: String) {
    _tagFilter.value = value
  }

  fun setMinimumPriority(priority: Int) {
    _minimumPriority.value = priority
  }

  fun setSource(source: LogSource?) {
    _sourceFilter.value = source
  }

  fun setFollow(value: Boolean) {
    _follow.value = value
  }

  fun togglePause() {
    val next = !_paused.value
    _paused.value = next
    _frozen.value = if (next) InMemoryLogBuffer.snapshot() else null
  }

  /** The visible lines as plain text, for copy and share. */
  fun renderVisible(): String = renderLogRecords(records.value)

  fun clear() {
    InMemoryLogBuffer.clear()
    _frozen.value = null
    viewModelScope.launch { InMemoryLogBuffer.publish() }
  }

  private data class Filters(
    val search: String,
    val tag: String,
    val minimumPriority: Int,
    val source: LogSource?,
  ) {
    fun matches(record: LogRecord): Boolean {
      if (record.priority < minimumPriority) return false
      if (source != null && record.source != source) return false
      if (tag.isNotBlank() && !record.tag.contains(tag, ignoreCase = true)) return false
      if (search.isNotBlank() && !record.message.contains(search, ignoreCase = true)) return false
      return true
    }
  }

  companion object {
    /** The levels the filter chip offers, least to most severe. */
    val LEVEL_OPTIONS = listOf(Log.VERBOSE, Log.DEBUG, Log.INFO, Log.WARN, Log.ERROR)

    val Factory: ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = LogsViewModel() as T
      }
  }
}
