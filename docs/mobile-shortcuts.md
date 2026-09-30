# Mobile buttons for mpv input.conf shortcuts

mpvExtended already loads `input.conf` when the player starts. You can edit it at
**Settings → Advanced → Edit input.conf**, or keep it in your selected MPV
configuration directory.

For example, add this line to `input.conf`:

```conf
F9 screenshot
```

Then open **Settings → Control layout → Custom shortcuts**. Set the shortcut's
mpv key to `F9` and give it a name such as `Screenshot`. Open a video again so
mpv loads the updated `input.conf`.

The list has no fixed length: add, reorder (drag the handle, or use the up and
down arrows) and delete shortcuts freely. Configured shortcuts appear in the
player's **More options** menu. You can also add **Shortcuts** to the control
layout to open a side panel, or add the numbered **Shortcut 1–4** control
buttons. Those numbered buttons follow the list order — button *N* uses the
*N*th shortcut — so they only appear when the list is at least that long and
that entry has a key. Shortcuts past the first four are still reachable from
the side panel and the More menu, neither of which has a limit. The side panel
uses the right edge in landscape and stays reachable in portrait.

Each button sends its configured key with mpv's `keypress` command. The key
field accepts one mpv key name, including combinations such as `Ctrl+Alt+s`.
Leave the key empty to hide that shortcut. Any command supported by
`input.conf`, including commands registered by a loaded plugin, can be bound to
that key.
