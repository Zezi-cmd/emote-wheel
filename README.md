# Emote Wheel

A quality-of-life plugin that transforms the emote tab into a customizable radial
**wheel**. Choose up to six favorite emotes (plus an optional **Random** slot), then
perform them using the original in-game emote widgets. Every click is a genuine click
on a genuine RuneScape widget, with no synthetic input.

<img width="640" alt="The Emote Wheel" src="images/wheel.png" />

## Features

- **Six favorite slots** arranged in a ring, configured from the side panel or by
  right-clicking any emote and choosing **Favorite -> Slot N**.
- **Drag to rearrange** - hold the rearrange key (default **Ctrl**) and drag an emote
  to reorder the wheel. Pick **Drag and Swap** (two emotes trade places) or **Drag and
  Slot** (drop into a slot, the rest shift) in the config. A plain click without the
  key always performs the emote.
- **Random slot** that cycles through your unlocked emotes like a slot machine.
  Clicking it performs whichever emote is currently showing.
- **Hotkey toggle** to instantly switch between the radial wheel and the normal emote
  grid, with an optional hold-to-show mode. The hotkey never fires while you are
  typing in chat.
- **Hover feedback** with smooth scaling and press animations that make the wheel feel
  responsive.
- **Spiral entrance animation** as the wheel expands into place, and smooth sliding
  when you rearrange.
- **Real widget interaction.** The plugin rearranges RuneScape's existing emote
  widgets instead of simulating clicks or input.
- **Persistent configuration.** Your wheel layout is remembered between sessions, and
  the original emote interface is restored whenever the plugin is disabled.

## Rearranging the wheel

Hold **Ctrl** (or your chosen rearrange key) with the wheel open and drag an emote to
move it. A short built-in tip walks you through it and then hides itself once you have
done a rearrange.

<img width="640" alt="Rearrange mode" src="images/rearrange.png" />

## Setting favorites

Right-click any emote in the tab and choose **Favorite -> Slot N**, or pick them in the
config panel. With the wheel on, right-click gives **Remove** to clear a slot.

<img width="640" alt="Favorite an emote" src="images/favorite.png" />

## Usage

1. Enable the plugin.
2. Assign a **Wheel hotkey** in the plugin configuration.
3. Open the emote tab and press the hotkey to open the wheel.
4. Fill your six slots from the config panel or by right-clicking emotes.
5. Set any slot to **Random** for a position that cycles through unlocked emotes.
6. Hold the **rearrange key** (default Ctrl) and drag an emote to reorder the wheel.

## Configuration

<img width="220" align="right" alt="Config panel" src="images/config.png" />

- **Wheel hotkey** - toggles the wheel on and off.
- **Hold to show** - hold the hotkey instead of toggling.
- **Rearrange key** - hold this and drag to reorder (default Ctrl).
- **Drag Mode** - Drag and Swap (default) or Drag and Slot.
- **Always show tip** - keep the rearrange tutorial tip showing (it auto-hides once
  you have rearranged once).
- **Slots 1-6** - choose an emote, **Random**, or **None** for each position.

## Notes

- Locked (dimmed) emotes can still be assigned to the wheel. Clicking them behaves
  exactly like the normal game interface and simply does nothing.
- The plugin only rearranges existing RuneScape widgets and stores its own
  configuration. It performs no automated game actions or synthetic input.
