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

	@ConfigSection(name = "Layout", description = "Which emotes appear on the wheel", position = 1)
	String layoutSection = "layout";


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

	// ----------------------------------------------------------------- layout

	@ConfigItem(
			keyName = "slot1",
			name = "Slot 1",
			description = "Top of the wheel. The remaining slots fill clockwise. Set a slot to "
					+ "None to leave it out, or Random for a slot that lands on a random emote.",
			section = layoutSection,
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
			section = layoutSection,
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
			section = layoutSection,
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
			section = layoutSection,
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
			section = layoutSection,
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
			section = layoutSection,
			position = 5
	)
	default Emote slot6()
	{
		return Emote.WAVE;
	}
}
