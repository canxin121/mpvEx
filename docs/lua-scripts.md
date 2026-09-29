# Lua scripts

1. In **Settings → Advanced**, choose an MPV configuration directory. Put your
   `.lua` files in its `scripts/` folder. The app also recognizes `.js` files.
2. Turn on **Enable Lua Scripts**, open **Manage Lua Scripts**, and select the
   scripts you want to load. You can create, edit, share, and delete scripts there.
3. Open the player again. mpv loads selected scripts when the player starts.
   Disabling Lua scripts or deselecting a file removes its private copy on the
   next player start. C plugins in the same `scripts/` folder remain separate.

You can put script options in `script-opts/` and shaders in `shaders/` under the
same configuration directory. The app copies these into mpv's private
configuration directory before initialization.

## Run a script action from a phone button

For example, save this as `scripts/hello.lua` and select it:

```lua
mp.add_key_binding(nil, "greet", function()
    mp.osd_message("Hello from Lua")
end)
```

Add a binding to the configuration directory's `input.conf`:

```conf
F9 script-binding hello/greet
```

In **Settings → Control layout → Custom shortcuts**, set a button's mpv key to
`F9`. Open the player again after editing `input.conf`. The button can then run
the script action from the player's **More options** menu, the **Shortcuts** side
panel, or an individual shortcut control button. See [Mobile shortcuts](mobile-shortcuts.md)
for the available placements.
