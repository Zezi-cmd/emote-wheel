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

import net.runelite.api.gameval.SpriteID;

/**
 * The OSRS emote set, in emote-tab order. Names must match the in-game emote
 * button names (matched case-insensitively), taken from the OSRS Wiki emote list.
 * NONE and RANDOM are plugin-specific slot options, not real emotes. The sprite id
 * (from {@link SpriteID.Emotes}) is the emote's tab icon, drawn in the side panel; a
 * handful of emotes have no such sprite and use {@link #NO_SPRITE}.
 */
public enum Emote
{
	NONE("None"),
	RANDOM("Random"),

	// Default emotes (available from account creation).
	YES("Yes", SpriteID.Emotes.YES),
	NO("No", SpriteID.Emotes.NO),
	BOW("Bow", SpriteID.Emotes.BOW),
	ANGRY("Angry", SpriteID.Emotes.ANGRY),
	THINK("Think", SpriteID.Emotes.THINK),
	WAVE("Wave", SpriteID.Emotes.WAVE),
	SHRUG("Shrug", SpriteID.Emotes.SHRUG),
	CHEER("Cheer", SpriteID.Emotes.CHEER),
	BECKON("Beckon", SpriteID.Emotes.BECKON),
	LAUGH("Laugh", SpriteID.Emotes.LAUGH),
	JUMP_FOR_JOY("Jump for Joy", SpriteID.Emotes.JUMP_FOR_JOY),
	YAWN("Yawn", SpriteID.Emotes.YAWN),
	DANCE("Dance", SpriteID.Emotes.DANCE),
	JIG("Jig", SpriteID.Emotes.JIG),
	SPIN("Spin", SpriteID.Emotes.SPIN),
	HEADBANG("Headbang", SpriteID.Emotes.HEADBANG),
	CRY("Cry", SpriteID.Emotes.CRY),
	BLOW_KISS("Blow Kiss", SpriteID.Emotes.BLOW_KISS),
	PANIC("Panic", SpriteID.Emotes.PANIC),
	RASPBERRY("Raspberry", SpriteID.Emotes.RASPBERRY),
	CLAP("Clap", SpriteID.Emotes.CLAP),
	SALUTE("Salute", SpriteID.Emotes.SALUTE),
	PREMIER_SHIELD("Premier Shield", SpriteID.Emotes.PREMIER_SHIELD),
	SIT_DOWN("Sit down"),

	// Unlockable emotes.
	GOBLIN_BOW("Goblin Bow", SpriteID.Emotes.GOBLIN_BOW),
	GOBLIN_SALUTE("Goblin Salute", SpriteID.Emotes.GOBLIN_SALUTE),
	FLEX("Flex"),
	GLASS_BOX("Glass Box", SpriteID.Emotes.GLASS_BOX),
	CLIMB_ROPE("Climb Rope", SpriteID.Emotes.CLIMB_ROPE),
	LEAN("Lean", SpriteID.Emotes.LEAN),
	GLASS_WALL("Glass Wall", SpriteID.Emotes.GLASS_WALL),
	ZOMBIE_WALK("Zombie Walk", SpriteID.Emotes.ZOMBIE_WALK),
	ZOMBIE_DANCE("Zombie Dance", SpriteID.Emotes.ZOMBIE_DANCE),
	SIT_UP("Sit up", SpriteID.Emotes.SIT_UP),
	PUSH_UP("Push up", SpriteID.Emotes.PUSH_UP),
	STAR_JUMP("Star jump", SpriteID.Emotes.STAR_JUMP),
	JOG("Jog", SpriteID.Emotes.JOG),
	SKILLCAPE("Skillcape", "cape", SpriteID.Emotes.SKILLCAPE),
	AIR_GUITAR("Air Guitar", SpriteID.Emotes.AIR_GUITAR),
	URI_TRANSFORM("Uri transform", SpriteID.Emotes.URI_TRANSFORM),
	EXPLORE("Explore"),
	FORTIS_SALUTE("Fortis Salute", SpriteID.Emotes.FORTIS_SALUTE),
	CRAB_DANCE("Crab dance", SpriteID.Emotes.CRAB_DANCE),
	IDEA("Idea", SpriteID.Emotes.IDEA),
	STAMP("Stamp", SpriteID.Emotes.STAMP),
	FLAP("Flap", SpriteID.Emotes.FLAP),
	SLAP_HEAD("Slap Head", SpriteID.Emotes.SLAP_HEAD),
	RABBIT_HOP("Rabbit Hop", SpriteID.Emotes.RABBIT_HOP),
	SCARED("Scared", SpriteID.Emotes.SCARED),
	ZOMBIE_HAND("Zombie Hand", SpriteID.Emotes.ZOMBIE_HAND),
	HYPERMOBILE_DRINKER("Hypermobile Drinker", SpriteID.Emotes.HYPERMOBILE_DRINKER),
	SMOOTH_DANCE("Smooth dance", SpriteID.Emotes.SMOOTH_DANCE),
	CRAZY_DANCE("Crazy dance", SpriteID.Emotes.CRAZY_DANCE),
	PARTY("Party"),
	TRICK("Trick"),
	RELIC_UNLOCK("Relic unlock");

	/** Sentinel for emotes with no side-panel icon sprite. */
	public static final int NO_SPRITE = -1;

	private final String displayName;
	private final String matchTerm;
	private final int spriteId;

	Emote(String displayName)
	{
		this(displayName, displayName, NO_SPRITE);
	}

	Emote(String displayName, int spriteId)
	{
		this(displayName, displayName, spriteId);
	}

	/**
	 * @param matchTerm text used to find this emote's button in the tab. Usually the
	 *                  display name, but the Skillcape button is renamed after the
	 *                  worn cape (e.g. "Attack cape", "Max cape"), so it matches on
	 *                  the shared substring "cape" instead.
	 * @param spriteId  the emote tab icon sprite, or {@link #NO_SPRITE} if none.
	 */
	Emote(String displayName, String matchTerm, int spriteId)
	{
		this.displayName = displayName;
		this.matchTerm = matchTerm;
		this.spriteId = spriteId;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public String getMatchTerm()
	{
		return matchTerm;
	}

	/** The emote tab icon sprite id, or {@link #NO_SPRITE} if this emote has none. */
	public int getSpriteId()
	{
		return spriteId;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
