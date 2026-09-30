package app.marlboroadvance.mpvex.presentation

import android.content.Context
import androidx.activity.ComponentActivity
import app.marlboroadvance.mpvex.i18n.AppLocale

/**
 * A [ComponentActivity] that resolves its resources in the interface language the user chose.
 *
 * Language is applied at `attachBaseContext`, because that is the earliest point the activity
 * can influence its own resources and it happens before the content view is inflated or any
 * Compose composition starts.
 */
open class BaseActivity : ComponentActivity() {
  override fun attachBaseContext(newBase: Context) {
    super.attachBaseContext(AppLocale.wrap(newBase))
  }
}
