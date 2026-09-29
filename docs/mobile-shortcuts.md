# Mobile buttons for mpv input.conf shortcuts

mpvExtended already loads `input.conf` when the player starts. You can edit it at
**Settings → Advanced → Edit input.conf**, or keep it in your selected MPV
configuration directory.

For example, add this line to `input.conf`:

```conf
F9 screenshot
```

Then open **Settings → Control layout → Custom shortcuts**. Set the first
button's mpv key to `F9` and give it a name such as `Screenshot`. Open a video
again so mpv loads the updated `input.conf`.

Configured shortcuts appear in the player's **More options** menu. You can also
add **Shortcuts** to the control layout to open a side panel, or add **Shortcut
1–4** as individual control buttons. The side panel uses the right edge in
landscape and stays reachable in portrait.

Each button sends its configured key with mpv's `keypress` command. The key
field accepts one mpv key name, including combinations such as `Ctrl+Alt+s`.
Leave the key empty to hide that shortcut. Any command supported by
`input.conf`, including commands registered by a loaded plugin, can be bound to
that key.
