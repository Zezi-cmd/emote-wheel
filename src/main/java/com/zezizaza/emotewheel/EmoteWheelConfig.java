/*
 * Copyright (c) 2026, ZeziZaZa
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.zezizaza.emotewheel;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;

@ConfigGroup(EmoteWheelConfig.GROUP)
public interface EmoteWheelConfig extends Config
{
	String GROUP = "emotewheel";

	@ConfigSection(name = "Input", description = "How the wheel is toggled", position = 0)
	String inputSection = "input";

	@ConfigSection(name = "Favorites panel", description = "The custom Favorites side panel", position = 1)
	String sidebarSection = "sidebar";

	@ConfigItem(
			keyName = "showUpdateMessage",
			name = "Show update messages",
			description = "Print a short changelog in the chat box the first time you log in "
					+ "after the plugin updates."
	)
	default boolean showUpdateMessage()
	{
		return true;
	}

	/** Internal: id of the last update changelog shown, so it appears only once. */
	@ConfigItem(keyName = "lastUpdateSeen", name = "", description = "", hidden = true)
	default String lastUpdateSeen()
	{
		return "";
	}


	// ------------------------------------------------------------------ input

	@ConfigItem(
			keyName = "hotkey",
			name = "Wheel hotkey",
			description = "Key that toggles the emote wheel on and off while the emote tab is open.",
			section = inputSection,
			position = 0
	)
	default Keybind hotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
			keyName = "holdToShow",
			name = "Hold to show",
			description = "How the hotkey behaves. Off (default): tap to toggle the wheel on, tap "
					+ "again to switch back to the normal emote menu. On: the wheel shows only "
					+ "while the key is held down.",
			section = inputSection,
			position = 1
	)
	default boolean holdToShow()
	{
		return false;
	}

	@ConfigItem(
			keyName = "rearrangeKey",
			name = "Rearrange key",
			description = "Hold this key and drag an emote on the wheel to rearrange it. Defaults to "
					+ "Control. A plain click (without this key) always performs the emote.",
			section = inputSection,
			position = 3
	)
	default Keybind rearrangeKey()
	{
		return Keybind.CTRL;
	}

	@ConfigItem(
			keyName = "dragMode",
			name = "Drag Mode",
			description = "How dragging an emote rearranges the wheel. Drag and Swap: the dragged "
					+ "emote and the one you drop on trade places. Drag and Slot: the emote drops into "
					+ "the slot you aim at and the others shift to make room. None: rearranging is off.",
			section = inputSection,
			position = 4
	)
	default DragMode dragMode()
	{
		return DragMode.SWAP;
	}

	@ConfigItem(
			keyName = "hidePanelBackground",
			name = "Hide panel background",
			description = "While the wheel is open, hide the emote panel's background and frame "
					+ "graphics so the emotes appear to float. The emote buttons stay. Only applies "
					+ "in the Resizable - Modern layout; Fixed and Resizable - Classic keep the "
					+ "normal panel.",
			section = inputSection,
			position = 2
	)
	default boolean hidePanelBackground()
	{
		return true;
	}

	// ---------------------------------------------------------------- sidebar

	@ConfigItem(
			keyName = "alphabetical",
			name = "Sort emotes A-Z",
			description = "Order the emotes in the side panel's picker alphabetically. Off "
					+ "lists them in the in-game emote tab order.",
			section = sidebarSection,
			position = 0
	)
	default boolean alphabetical()
	{
		return true;
	}

	@ConfigItem(
			keyName = "randomExclude",
			name = "Random exclude list",
			description = "Emotes to keep out of the Random slot's cycle, by name, separated "
					+ "by commas (for example: Goblin Bow, Sit down). Handy for removing emotes "
					+ "you have not unlocked. Case-insensitive.",
			section = sidebarSection,
			position = 2
	)
	default String randomExclude()
	{
		return "";
	}

	// ----------------------------------------------------------------- layout

	/** Internal: saved presets. One per line, "name\tEMOTE1,...,EMOTE6"; managed by the panel. */
	@ConfigItem(
			keyName = "presets",
			name = "",
			description = "",
			hidden = true
	)
	default String presets()
	{
		return "";
	}

	@ConfigItem(
			keyName = "slot1",
			name = "Slot 1",
			description = "Top of the wheel. The remaining slots fill clockwise. Set a slot to "
					+ "None to leave it out, or Random for a slot that lands on a random emote.",
			hidden = true,
			position = 0
	)
	default Emote slot1()
	{
		return Emote.YES;
	}

	@ConfigItem(
			keyName = "slot2",
			name = "Slot 2",
			description = "",
			hidden = true,
			position = 1
	)
	default Emote slot2()
	{
		return Emote.NO;
	}

	@ConfigItem(
			keyName = "slot3",
			name = "Slot 3",
			description = "",
			hidden = true,
			position = 2
	)
	default Emote slot3()
	{
		return Emote.BOW;
	}

	@ConfigItem(
			keyName = "slot4",
			name = "Slot 4",
			description = "",
			hidden = true,
			position = 3
	)
	default Emote slot4()
	{
		return Emote.ANGRY;
	}

	@ConfigItem(
			keyName = "slot5",
			name = "Slot 5",
			description = "",
			hidden = true,
			position = 4
	)
	default Emote slot5()
	{
		return Emote.THINK;
	}

	@ConfigItem(
			keyName = "slot6",
			name = "Slot 6",
			description = "",
			hidden = true,
			position = 5
	)
	default Emote slot6()
	{
		return Emote.WAVE;
	}
}
