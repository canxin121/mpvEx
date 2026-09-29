# C plugin configuration manifests

mpvExtended can discover configuration fields for an MPV C plugin without
executing the plugin. A plugin opts into discovery by placing a UTF-8 JSON
manifest next to its `.so` file.

## File layout

The preferred sidecar name includes the complete plugin filename:

```text
scripts/
├── danmaku.so
└── danmaku.so.mpvex.json
```

For compatibility, `danmaku.mpvex.json` is also recognized. If both files are
present, `danmaku.so.mpvex.json` wins. The plugin and its manifest may be in the
configured MPV directory root or its `scripts/` directory. A copy in `scripts/`
takes precedence over a same-named plugin in the root.

Manifests are limited to 256 KiB. A malformed, unsupported, or mismatched
manifest prevents that plugin from loading. A legacy `.so` with no manifest
continues to load normally but has no generated configuration UI.

## Version 1 manifest

```json
{
  "schemaVersion": 1,
  "id": "org.example.danmaku",
  "name": "Danmaku",
  "version": "1.2.0",
  "entry": "danmaku.so",
  "config": [
    {
      "id": "serverUrl",
      "title": "Danmaku server",
      "description": "Server used to retrieve danmaku data",
      "type": "string",
      "required": true,
      "default": "https://example.com",
      "pattern": "https://.+",
      "binding": {
        "type": "environment",
        "name": "MPVEX_ORG_EXAMPLE_DANMAKU_SERVER_URL"
      }
    },
    {
      "id": "timeout",
      "title": "Request timeout",
      "type": "integer",
      "default": 10,
      "minimum": 1,
      "maximum": 60,
      "binding": {
        "type": "environment",
        "name": "MPVEX_ORG_EXAMPLE_DANMAKU_TIMEOUT"
      }
    },
    {
      "id": "mode",
      "title": "Display mode",
      "type": "enum",
      "default": "normal",
      "options": [
        { "value": "normal", "label": "Normal" },
        { "value": "compact", "label": "Compact" }
      ],
      "binding": {
        "type": "environment",
        "name": "MPVEX_ORG_EXAMPLE_DANMAKU_MODE"
      }
    },
    {
      "id": "apiToken",
      "title": "API token",
      "type": "string",
      "sensitive": true,
      "binding": {
        "type": "environment",
        "name": "MPVEX_ORG_EXAMPLE_DANMAKU_API_TOKEN"
      }
    }
  ]
}
```

Top-level properties:

| Property | Required | Meaning |
|---|---:|---|
| `schemaVersion` | No | Manifest protocol version. Defaults to `1`; only version 1 is currently accepted. |
| `id` | Yes | Stable plugin identifier. Use a reverse-domain ID and do not derive saved settings from the filename. |
| `name` | Yes | Human-readable plugin name. |
| `version` | No | Human-readable plugin version. |
| `entry` | Yes | Exact `.so` filename, including case. It must match the adjacent plugin. |
| `config` | No | Array of configuration field declarations. |

Field properties:

| Property | Required | Meaning |
|---|---:|---|
| `id` | Yes | Stable field identifier, unique within the plugin. |
| `title` | Yes | Label shown in the settings UI. |
| `description` | No | Additional help text. |
| `type` | Yes | One of `string`, `boolean`, `integer`, `number`, `enum`, or `path`. |
| `required` | No | If `true`, MPV will skip this plugin until the value or a valid default is present. |
| `default` | No | Default value. Its JSON type must match the field type. |
| `minimum` | No | Inclusive lower bound for `integer` and `number`. |
| `maximum` | No | Inclusive upper bound for `integer` and `number`. |
| `pattern` | No | Kotlin regular expression that a `string` or `path` value must match in full. |
| `sensitive` | No | Masks the value in the UI. It does not isolate the value from other native plugins. |
| `options` | For `enum` | Non-empty array of unique `{ "value", "label" }` choices. |
| `binding` | Yes | Version 1 supports `{ "type": "environment", "name": "..." }`. |

Plugin IDs and field IDs may contain ASCII letters, digits, `.`, `_`, and `-`.
They must begin with a letter or digit and are limited to 128 characters.
Environment names must match `[A-Za-z_][A-Za-z0-9_]*`.
The built-in names `MPVEX_APP_DIR`, `MPVEX_CONFIG_DIR`, `MPVEX_CACHE_DIR`,
and `MPVEX_MEDIA_PATH` are reserved and cannot be used as manifest bindings.
Their current values are visible in **Settings → Advanced → Environment
variables**. `MPVEX_MEDIA_PATH` changes as playback switches files and is unset
when no media is loaded; it can be an `fd://` path for Android content URIs.

Use a plugin-specific environment prefix such as
`MPVEX_ORG_EXAMPLE_DANMAKU_`. mpvExtended rejects a selection when two enabled
plugins claim the same environment name.

## Plugin lifecycle

The player performs the following work before calling `mpv_initialize()`:

1. Scan selected `.so` files and parse their manifests.
2. Validate stored values, defaults, required fields, and cross-plugin
   environment-name conflicts.
3. Copy only valid selected `.so` files to the internal MPV `scripts/`
   directory.
4. Remove environment variables managed during a previous player session in
   the same Android process.
5. Set the environment variables only for plugins that were copied
   successfully.
6. Initialize libmpv, which loads the plugins.

Variables entered in **Settings → Advanced → Environment variables** are also
applied before libmpv starts. A selected plugin's manifest value takes
priority when both configurations use the same name. If the plugin is disabled
or cannot load, the user-defined value is used again.

The plugin can read a value normally:

```c
#include <stdlib.h>

const char *server_url =
    getenv("MPVEX_ORG_EXAMPLE_DANMAKU_SERVER_URL");
```

An optional field with no configured or default value is not exported. A
plugin should therefore retain sensible internal defaults when `getenv()`
returns `NULL`.

Configuration changes take effect the next time the player activity creates a
new MPV instance. Changing videos within an existing player does not reload the
plugin.

## Independent plugin settings panels

After a plugin is selected, Advanced settings lists that plugin as a separate
entry beneath **Manage C Plugins**. Opening the entry shows a full settings
panel generated only from that plugin's manifest. The panel displays the
plugin name, version, filename, and stable ID before its fields.

Each panel reads and updates only the map stored under its manifest `id`.
Saving one plugin therefore does not replace or reset values belonging to any
other plugin. The selection dialog retains a compact configuration dialog for
the first-time setup of required fields; later edits can use the independent
panel.

## Security boundary

Environment variables are process-wide. Every native library loaded into the
mpvExtended process can read every exported plugin value. `sensitive: true`
only hides the text on screen; it is not a security boundary. Do not use this
transport for high-value credentials when untrusted plugins can be loaded.
