package app.marlboroadvance.mpvex.preferences

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Segment
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Segment
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Headset
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.LooksOne
import androidx.compose.material.icons.outlined.LooksTwo
import androidx.compose.material.icons.outlined.Looks3
import androidx.compose.material.icons.outlined.Looks4
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import app.marlboroadvance.mpvex.R

/**
 * Represents a customizable button in the player controls.
 * Now includes an icon for the preference UI.
 */
enum class PlayerButton(
  val icon: ImageVector,
) {
  BACK_ARROW(Icons.AutoMirrored.Outlined.ArrowBack),
  VIDEO_TITLE(Icons.Outlined.Title),
  BOOKMARKS_CHAPTERS(Icons.Outlined.Bookmarks),
  PLAYBACK_SPEED(Icons.Outlined.Speed),
  DECODER(Icons.Outlined.Memory),
  SCREEN_ROTATION(Icons.Outlined.ScreenRotation),
  FRAME_NAVIGATION(Icons.Outlined.Camera),
  VIDEO_ZOOM(Icons.Outlined.ZoomIn),
  PICTURE_IN_PICTURE(Icons.Outlined.PictureInPictureAlt),
  ASPECT_RATIO(Icons.Outlined.AspectRatio),
  LOCK_CONTROLS(Icons.Outlined.LockOpen),
  AUDIO_TRACK(Icons.Outlined.Audiotrack),
  SUBTITLES(Icons.Outlined.Subtitles),
  MORE_OPTIONS(Icons.Outlined.MoreVert),
  CURRENT_CHAPTER(Icons.Outlined.Bookmarks), // <-- CHANGED ICON
  REPEAT_MODE(Icons.Outlined.Repeat),
  SHUFFLE(Icons.Outlined.Shuffle),
  MIRROR(Icons.Outlined.Flip),
  VERTICAL_FLIP(Icons.Outlined.Flip),
  AB_LOOP(Icons.Outlined.Autorenew),
  CUSTOM_SKIP(Icons.Outlined.FastForward),
  BACKGROUND_PLAYBACK(Icons.Outlined.Headset),
  SHORTCUTS(Icons.Outlined.Keyboard),
  SHORTCUT_1(Icons.Outlined.LooksOne),
  SHORTCUT_2(Icons.Outlined.LooksTwo),
  SHORTCUT_3(Icons.Outlined.Looks3),
  SHORTCUT_4(Icons.Outlined.Looks4),
  NONE(Icons.Outlined.Bookmarks),
}

/**
 * A list of all buttons that the user can choose from in the customization menu.
 * Excludes NONE (placeholder) and constant buttons (BACK_ARROW, VIDEO_TITLE).
 */
val allPlayerButtons =
  PlayerButton.values().filter {
    it != PlayerButton.NONE &&
      it != PlayerButton.BACK_ARROW &&
      it != PlayerButton.VIDEO_TITLE
  }

/** Gets the localized label for a player button. */
@Composable
fun getPlayerButtonLabel(button: PlayerButton): String =
  stringResource(
    when (button) {
      PlayerButton.BACK_ARROW -> R.string.button_back_arrow
      PlayerButton.VIDEO_TITLE -> R.string.ui_video_title
      PlayerButton.BOOKMARKS_CHAPTERS -> R.string.button_chapters_bookmarks
      PlayerButton.PLAYBACK_SPEED -> R.string.ui_playback_speed
      PlayerButton.DECODER -> R.string.pref_decoder
      PlayerButton.SCREEN_ROTATION -> R.string.button_screen_rotation
      PlayerButton.FRAME_NAVIGATION -> R.string.player_sheets_frame_navigation_title
      PlayerButton.VIDEO_ZOOM -> R.string.player_sheets_zoom_slider_label
      PlayerButton.PICTURE_IN_PICTURE -> R.string.button_picture_in_picture
      PlayerButton.ASPECT_RATIO -> R.string.ui_aspect_ratio
      PlayerButton.LOCK_CONTROLS -> R.string.button_lock_controls
      PlayerButton.AUDIO_TRACK -> R.string.button_audio_track
      PlayerButton.SUBTITLES -> R.string.pref_subtitles
      PlayerButton.MORE_OPTIONS -> R.string.button_more_options
      PlayerButton.CURRENT_CHAPTER -> R.string.button_current_chapter
      PlayerButton.REPEAT_MODE -> R.string.button_repeat_mode
      PlayerButton.SHUFFLE -> R.string.button_shuffle
      PlayerButton.MIRROR -> R.string.button_horizontal_flip
      PlayerButton.VERTICAL_FLIP -> R.string.ui_vertical_flip
      PlayerButton.AB_LOOP -> R.string.button_ab_loop
      PlayerButton.CUSTOM_SKIP -> R.string.button_custom_skip
      PlayerButton.BACKGROUND_PLAYBACK -> R.string.button_background_playback
      PlayerButton.SHORTCUTS -> R.string.ui_shortcuts
      PlayerButton.SHORTCUT_1 -> R.string.button_shortcut_1
      PlayerButton.SHORTCUT_2 -> R.string.button_shortcut_2
      PlayerButton.SHORTCUT_3 -> R.string.button_shortcut_3
      PlayerButton.SHORTCUT_4 -> R.string.button_shortcut_4
      PlayerButton.NONE -> R.string.filter_preset_none_title
    },
  )
