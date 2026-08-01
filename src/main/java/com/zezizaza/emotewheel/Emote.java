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

/**
 * The OSRS emote set, in emote-tab order. Names must match the in-game emote
 * button names (matched case-insensitively), taken from the OSRS Wiki emote list.
 * NONE and RANDOM are plugin-specific slot options, not real emotes.
 */
public enum Emote
{
	NONE("None"),
	RANDOM("Random"),

	// Default emotes (available from account creation).
	YES("Yes"),
	NO("No"),
	BOW("Bow"),
	ANGRY("Angry"),
	THINK("Think"),
	WAVE("Wave"),
	SHRUG("Shrug"),
	CHEER("Cheer"),
	BECKON("Beckon"),
	LAUGH("Laugh"),
	JUMP_FOR_JOY("Jump for Joy"),
	YAWN("Yawn"),
	DANCE("Dance"),
	JIG("Jig"),
	SPIN("Spin"),
	HEADBANG("Headbang"),
	CRY("Cry"),
	BLOW_KISS("Blow Kiss"),
	PANIC("Panic"),
	RASPBERRY("Raspberry"),
	CLAP("Clap"),
	SALUTE("Salute"),
	PREMIER_SHIELD("Premier Shield"),
	SIT_DOWN("Sit down"),

	// Unlockable emotes.
	GOBLIN_BOW("Goblin Bow"),
	GOBLIN_SALUTE("Goblin Salute"),
	FLEX("Flex"),
	GLASS_BOX("Glass Box"),
	CLIMB_ROPE("Climb Rope"),
	LEAN("Lean"),
	GLASS_WALL("Glass Wall"),
	ZOMBIE_WALK("Zombie Walk"),
	ZOMBIE_DANCE("Zombie Dance"),
	SIT_UP("Sit up"),
	PUSH_UP("Push up"),
	STAR_JUMP("Star jump"),
	JOG("Jog"),
	SKILLCAPE("Skillcape"),
	AIR_GUITAR("Air Guitar"),
	URI_TRANSFORM("Uri transform"),
	EXPLORE("Explore"),
	FORTIS_SALUTE("Fortis Salute"),
	CRAB_DANCE("Crab dance"),
	IDEA("Idea"),
	STAMP("Stamp"),
	FLAP("Flap"),
	SLAP_HEAD("Slap Head"),
	RABBIT_HOP("Rabbit Hop"),
	SCARED("Scared"),
	ZOMBIE_HAND("Zombie Hand"),
	HYPERMOBILE_DRINKER("Hypermobile Drinker"),
	SMOOTH_DANCE("Smooth dance"),
	CRAZY_DANCE("Crazy dance"),
	PARTY("Party"),
	TRICK("Trick");

	private final String displayName;

	Emote(String displayName)
	{
		this.displayName = displayName;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
