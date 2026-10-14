package app.marlboroadvance.mpvex.ui.player

import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode

class PlayerObserver(
  private val activity: PlayerActivity,
) : MPVLib.EventObserver {
  private fun dispatch(action: () -> Unit) {
    activity.runOnUiThread {
      // Recheck when the queued callback executes, not on the native event thread.
      if (activity.isMpvActive) action()
    }
  }

  override fun eventProperty(property: String) {
    // No property without a value is currently observed.
  }

  override fun eventProperty(
    property: String,
    value: Long,
  ) {
    dispatch { activity.onObserverEvent(property, value) }
  }

  override fun eventProperty(
    property: String,
    value: Boolean,
  ) {
    dispatch { activity.onObserverEvent(property, value) }
  }

  override fun eventProperty(
    property: String,
    value: String,
  ) {
    // No string property is currently observed.
  }

  override fun eventProperty(
    property: String,
    value: Double,
  ) {
    dispatch { activity.onObserverEvent(property, value) }
  }

  @Suppress("EmptyFunctionBlock")
  override fun eventProperty(
    property: String,
    value: MPVNode,
  ) {
    dispatch { activity.onObserverEvent(property, value) }
  }

  override fun event(eventId: Int, data: MPVNode) {
    dispatch { activity.event(eventId) }
  }
}
