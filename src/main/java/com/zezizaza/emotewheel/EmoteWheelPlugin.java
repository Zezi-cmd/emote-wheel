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

import com.google.inject.Provides;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.vars.InputType;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.input.MouseWheelListener;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
		name = "Emote Wheel",
		description = "Arranges your favourite emotes in a radial layout inside the emote tab",
		tags = {"emote", "wheel", "radial", "ui", "quality of life"}
)
public class EmoteWheelPlugin extends Plugin
{
	/** Unlocked emote buttons carry this action; locked ones have the name but not it. */
	private static final String PERFORM_ACTION = "perform";

	/** Normalised names of every real emote, used to recognise locked emote buttons. */
	private static final Set<String> EMOTE_NAMES;

	static
	{
		Set<String> names = new HashSet<>();
		for (Emote e : Emote.values())
		{
			if (e != Emote.NONE && e != Emote.RANDOM)
			{
				names.add(normalise(e.getDisplayName()));
			}
		}
		EMOTE_NAMES = names;
	}

	/** How fast the Random slot cycles through real emotes, in milliseconds. */
	private static final long RANDOM_CYCLE_MS = 120;

	// Fixed appearance values (previously configurable, kept as tuned defaults).
	/** Radius around the centre where nothing is selected. */
	private static final int DEAD_ZONE = 25;
	/** Figure size inside a wedge, as a fraction of native size. */
	private static final double ICON_SCALE = 1.35;
	/** Extra growth of the hovered figure, on top of ICON_SCALE. */
	private static final double HOVER_SCALE = 1.18;
	/** Pixel nudge of the hovered figure. */
	private static final int HOVER_OFFSET_X = -1;
	/** Pixel shift of the whole ring. 0 now that the ring centres in the full panel width
	 *  (the scrollbar gutter is reclaimed in the usable-width calc, not nudged around). */
	private static final int RING_OFFSET_X = 0;
	/** Baked-in nudge of the wheel centre within the viewport, tuned for the floating look. */
	private static final int WHEEL_OFFSET_X = -5;
	private static final int WHEEL_OFFSET_Y = 25;
	/** Wheel X nudge when the background is SHOWN (Classic/Fixed) - re-centres the wheel. */
	private static final int WHEEL_OFFSET_X_SHOWN = 3;
	/** Transparency applied to non-hovered figures when something is hovered (0=opaque, 255=clear). */
	private static final int FADE_OPACITY = 130;
	/** Per-frame ease for the rearrange-mode frame stroke fade (higher = quicker). */
	private static final double REARRANGE_EASE = 0.28;
	/** Per-frame ease for figures sliding to new ring spots during a reorder (higher = snappier). */
	private static final double POS_EASE = 0.35;
	/** Dim applied to all figures while the rearrange key is held but none is hovered. */
	private static final int ICON_FADE_OPACITY = 120;
	/** Per-frame easing factor for scale/opacity tweens (0..1; higher = snappier). */
	private static final double ANIM_EASE = 0.60;
	/** Ring size (auto-fitted to an ellipse and clamped to what the panel allows). */
	private static final int RADIUS = 70;
	/** Hovered figure dips to this fraction of its hover size while the button is held. */
	private static final double PRESS_SCALE = 0.90;
	/** Per-frame easing for the entrance/exit timeline (higher = quicker). */
	private static final double ENTRANCE_EASE = 0.15;
	/** Fraction of the timeline spread across emotes for the stagger. */
	private static final double ENTRANCE_STAGGER = 0.20;
	/** Turns each emote spirals through as it flies out/in (1.0 = a full turn). */
	private static final double ENTRANCE_TURNS = 0.75;

	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private KeyManager keyManager;
	@Inject private MouseManager mouseManager;
	@Inject private ConfigManager configManager;
	@Inject private SpriteManager spriteManager;
	@Inject private OverlayManager overlayManager;
	@Inject private ClientToolbar clientToolbar;
	@Inject private EmoteWheelConfig config;
	@Inject private EmoteWheelOverlay overlay;

	/** The custom slot-editor side panel and its toolbar button. */
	private EmoteWheelPanel panel;
	private NavigationButton navButton;

	/** Emote tab icons for the side panel, loaded on demand from each emote's sprite id
	 *  ({@link Emote#getSpriteId()}). Written on the client thread by the async sprite
	 *  callback and read on the AWT thread in the panel's paint, so kept concurrent. */
	private final Map<Emote, BufferedImage> emoteIcons = new ConcurrentHashMap<>();
	private final Set<Emote> iconRequested = ConcurrentHashMap.newKeySet();
	/** Emotes with no API sprite id, awaiting a sprite harvested off their live tab button. */
	private final Set<Emote> pendingWidgetIcons = ConcurrentHashMap.newKeySet();

	/** Normalised names the player has excluded from the Random slot's cycle. */
	private volatile Set<String> randomExcludes = new HashSet<>();

	/**
	 * Maps a locked emote sprite to its unlocked (coloured) twin. The emote tab shows the
	 * locked, greyed sprite for emotes the player has not unlocked, but the side panel wants
	 * the unlocked icon for every emote. These are the unnamed emote sprite batches (the ones
	 * with no {@link net.runelite.api.gameval.SpriteID.Emotes} constant): unlocked and locked
	 * sit in parallel blocks, so this pairs them by position.
	 */
	private static final Map<Integer, Integer> UNLOCKED_SPRITE = new HashMap<>();

	static
	{
		int[][] pairs = {
			{2427, 2423}, {2428, 2424}, {2429, 2425}, {2430, 2426},
			{6339, 3604}, {6340, 3606}, {6341, 3607}, {6342, 3608},
		};
		for (int[] p : pairs)
		{
			UNLOCKED_SPRITE.put(p[0], p[1]);
		}
	}

	/**
	 * Canvas bounds of the emote viewport while the wheel is active, or null when
	 * it is not. Updated on the client thread each layout tick and read on the AWT
	 * thread by the scroll blocker, so it is volatile and never dereferences a
	 * widget off-thread.
	 */
	private volatile Rectangle activeViewportBounds;

	/** True while the left mouse button is held; drives the click "press" dip. */
	private volatile boolean mousePressed;

	/** Per-emote animated {scale, opacity}, eased toward their targets each frame. */
	private final Map<Emote, double[]> anim = new HashMap<>();

	/** Per-emote current animated {x, y} ring centre, eased toward its target each frame. */
	private final Map<Emote, double[]> emotePos = new HashMap<>();

	/** Order just committed by a drop, used until the live config catches up, so the
	 *  layout never reverts to the pre-drop order for a frame (which caused a jump). */
	private List<Emote> pendingOrder;

	/** Tracks the left button so the hovered figure can dip while pressed. */
	private final MouseListener pressListener = new MouseAdapter()
	{
		@Override
		public MouseEvent mousePressed(MouseEvent e)
		{
			if (e.getButton() == MouseEvent.BUTTON1)
			{
				mousePressed = true;
				// A left-press with the rearrange key held, inside the active wheel,
				// begins a rearrange drag. Swallow it here so the emote under the cursor
				// never performs; plain presses are left untouched so clicks work as before.
				Rectangle vb = activeViewportBounds;
				if (active && rearrangeHeld && vb != null && vb.contains(e.getPoint()))
				{
					shiftDragPress = true;
					e.consume();
				}
				else
				{
					shiftDragPress = false;
				}
			}
			return e;
		}

		@Override
		public MouseEvent mouseReleased(MouseEvent e)
		{
			if (e.getButton() == MouseEvent.BUTTON1)
			{
				mousePressed = false;
				// If this release ends a drag, swallow it so the game doesn't also
				// perform the emote under the cursor.
				if (suppressClick)
				{
					suppressClick = false;
					e.consume();
				}
			}
			return e;
		}
	};

	/** Eats scroll-wheel input over the emote panel while the wheel is active. */
	private final MouseWheelListener scrollBlocker = new MouseWheelListener()
	{
		@Override
		public MouseWheelEvent mouseWheelMoved(MouseWheelEvent event)
		{
			Rectangle vb = activeViewportBounds;
			if (vb != null && vb.contains(event.getPoint()))
			{
				event.consume();
			}
			return event;
		}
	};

	/** Tracks whether the configured rearrange key is physically held - drives the
	 *  rearrange-mode affordances and gates the drag on whatever key the user chose. */
	private final KeyListener rearrangeKeyListener = new KeyListener()
	{
		@Override
		public void keyTyped(KeyEvent e)
		{
		}

		@Override
		public void keyPressed(KeyEvent e)
		{
			if (config.dragMode() != DragMode.NONE && isRearrangeKey(e))
			{
				rearrangeHeld = true;
			}
		}

		@Override
		public void keyReleased(KeyEvent e)
		{
			if (isRearrangeKey(e))
			{
				rearrangeHeld = false;
			}
		}
	};

	private boolean isRearrangeKey(KeyEvent e)
	{
		Keybind kb = config.rearrangeKey();
		// matches() correctly handles both key-pressed and key-released, and works for
		// modifier-only binds (Shift) as well as regular keys.
		return kb != null && kb.matches(e);
	}

	/** True while the wheel is active (favourites arranged). Toggled by the hotkey. */
	@Getter private boolean active;

	/** 0..1 fan-out entrance progress; 0 = figures stacked at centre, 1 = at the ring. */
	private double entranceProgress;

	/** Cached each frame on the client thread: is the player entering text right now?
	 *  Read by the AWT hotkey listener so a letter hotkey types instead of toggling. */
	private volatile boolean typing;

	// --- Shift-drag to reorder (insert; snaps into place on drop) ---
	/** Emote currently being dragged around the ring, or null. */
	private Emote dragEmote;
	/** True while a Shift-drag is in progress. */
	private boolean dragging;
	/** Latched by the mouse listener: the current left-press was a Shift-press inside the wheel. */
	private volatile boolean shiftDragPress;
	/** Set on the layout thread, read by the mouse listener to swallow the drag's release. */
	private volatile boolean suppressClick;
	/** Previous frame's press state, to detect press/release edges on the layout thread. */
	private boolean prevMousePressed;
	/** True while the configured rearrange key is physically held. */
	private volatile boolean rearrangeHeld;
	/** 0..1 eased alpha of the rearrange-mode frame stroke (fades in while the key is held
	 *  OR a drag is in progress). */
	@Getter private double rearrangeAlpha;

	/**
	 * Cached original geometry keyed by widget identity:
	 * {0:origX, 1:origY, 2:origWidth, 3:origHeight, 4:wasSelfHidden, 5:modelZoom,
	 *  6:actualWidth, 7:actualHeight, 8:widthMode, 9:heightMode,
	 *  10:canvasX, 11:canvasY, 12:opacity}.
	 * origWidth/Height and origX/Y are the raw fields (mode-dependent); the actual*
	 * and canvas* values are resolved pixels, which is what placement and scaling
	 * must be based on so odd size/position modes cannot throw a figure off.
	 */
	private final Map<Long, int[]> originalState = new HashMap<>();

	@Getter private final List<Segment> segments = new ArrayList<>();

	private boolean layoutApplied;
	/** Deduplicates the layout-error warning so a per-frame failure can't spam the log. */
	private boolean layoutErrored;
	/** Player's list scroll position saved when the wheel took over; -1 = none saved. */
	private int preLayoutScrollY = -1;
	private String lastLog = "";

	// While the chatbox is focused, hand back NOT_SET so the hotkey doesn't match. That
	// stops HotkeyListener from firing AND from consuming the key, so a letter hotkey
	// (e.g. "p") types normally while typing instead of toggling and being swallowed.
	// The 'typing' flag is refreshed each frame on the client thread (valid varc reads).
	private final HotkeyListener hotkeyListener = new HotkeyListener(
			() -> typing ? Keybind.NOT_SET : config.hotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			// Hold mode: pressing activates. Toggle mode: pressing flips state.
			setActive(config.holdToShow() || !active);
		}

		@Override
		public void hotkeyReleased()
		{
			if (config.holdToShow())
			{
				setActive(false);
			}
		}
	};

	private void setActive(boolean v)
	{
		if (active != v)
		{
			active = v;
			// Remember the on/off state between sessions.
			configManager.setConfiguration(EmoteWheelConfig.GROUP, "active", v);
		}
	}

	@Provides
	EmoteWheelConfig provideConfig(ConfigManager cm)
	{
		return cm.getConfig(EmoteWheelConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		keyManager.registerKeyListener(hotkeyListener);
		keyManager.registerKeyListener(rearrangeKeyListener);
		mouseManager.registerMouseWheelListener(scrollBlocker);
		mouseManager.registerMouseListener(pressListener);

		randomExcludes = parseExcludes(config.randomExclude());

		// A few emotes have no named icon sprite in the API; their icons are harvested off
		// the live emote-tab button instead, once the tab is open.
		pendingWidgetIcons.clear();
		for (Emote e : Emote.values())
		{
			if (e != Emote.NONE && e != Emote.RANDOM && e.getSpriteId() == Emote.NO_SPRITE)
			{
				pendingWidgetIcons.add(e);
			}
		}

		// Custom slot editor in the sidebar. It gatekeeps duplicates visually, which the
		// auto-generated config dropdowns cannot do (the config panel never refreshes a
		// value change from code), so slot editing lives here instead.
		panel = new EmoteWheelPanel(config, configManager, this::getEmoteIcon);
		navButton = NavigationButton.builder()
				.tooltip("Emote Wheel")
				.icon(ImageUtil.loadImageResource(getClass(), "icon.png"))
				.priority(7)
				.panel(panel)
				.build();
		clientToolbar.addNavigation(navButton);

		// Restore the remembered on/off state from the previous session.
		active = Boolean.TRUE.equals(
				configManager.getConfiguration(EmoteWheelConfig.GROUP, "active", Boolean.class));
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		keyManager.unregisterKeyListener(hotkeyListener);
		keyManager.unregisterKeyListener(rearrangeKeyListener);
		mouseManager.unregisterMouseWheelListener(scrollBlocker);
		mouseManager.unregisterMouseListener(pressListener);
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		panel = null;
		active = false;
		activeViewportBounds = null;
		clientThread.invoke(this::restoreAll);
	}


	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		GameState s = e.getGameState();
		// Only reset on a genuine interface rebuild (logout / world hop). LOADING
		// also fires on area changes (POH portal, teleports) where the emote
		// interface survives - clearing the cache there forced a re-measure from the
		// still-moved widgets and collapsed the figures into the corner.
		if (s == GameState.LOGIN_SCREEN || s == GameState.HOPPING)
		{
			// Caches are tied to the (rebuilt) interface, so clear them. 'active' is
			// user intent and is intentionally NOT reset here - it persists across
			// logins / hops (and is remembered between sessions via config).
			originalState.clear();
			segments.clear();
			anim.clear();
			layoutApplied = false;
			activeViewportBounds = null;
			preLayoutScrollY = -1;
		}
	}

	@Subscribe
	public void onBeforeRender(BeforeRender e)
	{
		// Cache the typing state here (client thread) so the AWT-thread hotkey listener
		// can read it cheaply and correctly when deciding whether to swallow the key.
		typing = typingInChat();

		// Harvest icons for the handful of emotes with no API sprite id, off the live tab.
		if (!pendingWidgetIcons.isEmpty())
		{
			harvestMissingIcons();
		}

		// Drive the layout just before the client draws widgets, so the wheel is in
		// place on the very frame the emote tab opens - no flash of the raw grid
		// (an ABOVE_WIDGETS overlay would run after the draw and be one frame late).
		tickLayout();
	}

	// The LAYOUT needs no config handler: applyLayout reads the live config every
	// frame, so slot edits are picked up on the next frame without a
	// restore-then-reapply, which used to flash the whole grid mid-edit.

	/**
	 * Keeps the side panel's combos in sync when a slot changes from somewhere else - a
	 * right-click Favorite/Remove or a drag reorder. Duplicate prevention itself lives in
	 * the panel (it rejects a duplicate pick visually); right-click and drag never produce
	 * duplicates, so there is nothing to reconcile here beyond refreshing the display.
	 */
	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!EmoteWheelConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		String key = event.getKey();
		if ("randomExclude".equals(key))
		{
			randomExcludes = parseExcludes(config.randomExclude());
		}
		// Slot edits change the names shown; a Drag Mode change shows or hides the grab
		// handles; the A-Z toggle reorders the picker; the exclude list changes the Random
		// preview. Repaint for all.
		if (key != null && panel != null
				&& (key.startsWith("slot") || "dragMode".equals(key)
				|| "alphabetical".equals(key) || "randomExclude".equals(key)))
		{
			panel.refresh();
		}
	}

	/**
	 * Adds a right-click option to emotes in the emote tab. With the wheel OFF it is
	 * "Favorite" with a Slot 1-6 submenu (each showing that slot's current emote);
	 * with the wheel ON it is "Remove", which clears the emote's slot back to None.
	 * Both only edit configuration - no game action is performed - so this stays
	 * within Plugin Hub rules. Menu entries insert at index 1 (just above Cancel),
	 * so the option sits at the bottom of the list.
	 */
	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		MenuEntry emoteEntry = null;
		for (MenuEntry entry : event.getMenuEntries())
		{
			Widget w = entry.getWidget();
			if (w != null && isEmoteButton(w))
			{
				emoteEntry = entry;
				break;
			}
		}
		if (emoteEntry == null)
		{
			return;
		}

		if (active)
		{
			// On the wheel: offer to remove this emote's slot. Resolve it from the
			// segment so the Random slot (whose widget is a cycling stand-in) is
			// removed correctly rather than the stand-in emote. If the emote is NOT
			// on the wheel (e.g. the wheel is empty), fall through to Favorite so it
			// can still be added.
			Segment seg = segmentForWidget(emoteEntry.getWidget());
			if (seg != null)
			{
				Emote toRemove = seg.getEmote();
				client.getMenu().createMenuEntry(1)
						.setOption("Remove")
						.setTarget(emoteTarget(emoteEntry, toRemove))
						.setType(MenuAction.RUNELITE)
						.onClick(e -> removeFromSlots(toRemove));
				return;
			}
		}

		// Off the wheel (or wheel empty): offer to favourite this emote into a slot.
		Emote emote = emoteByName(buttonLabel(emoteEntry.getWidget()));
		if (emote == null)
		{
			return;
		}

		// Already assigned to a slot? Don't offer Favorite at all - an emote lives in
		// at most one slot, so there's nothing to add. (Use Remove, or drag, instead.)
		for (int slot = 1; slot <= 6; slot++)
		{
			if (slotConfig(slot) == emote)
			{
				return;
			}
		}

		// If any slot is open, one click on "Favorite" fills the first empty one - no
		// need to drill into a submenu. Only when every slot is taken do we show the
		// submenu, so you can pick which existing emote to replace.
		int firstEmpty = 0;
		for (int slot = 1; slot <= 6; slot++)
		{
			if (slotConfig(slot) == Emote.NONE)
			{
				firstEmpty = slot;
				break;
			}
		}

		if (firstEmpty != 0)
		{
			final int target = firstEmpty;
			client.getMenu().createMenuEntry(1)
					.setOption("Favorite")
					.setTarget(emoteTarget(emoteEntry, emote))
					.setType(MenuAction.RUNELITE)
					.onClick(e -> configManager.setConfiguration(
							EmoteWheelConfig.GROUP, "slot" + target, emote));
			return;
		}

		MenuEntry parent = client.getMenu().createMenuEntry(1)
				.setOption("Favorite")
				.setTarget(emoteTarget(emoteEntry, emote))
				.setType(MenuAction.RUNELITE);
		Menu submenu = parent.createSubMenu();

		// Added last-appears-first, so iterate 6..1 to show Slot 1 at the top.
		for (int slot = 6; slot >= 1; slot--)
		{
			final int s = slot;
			Emote current = slotConfig(slot);
			String label = "Slot " + slot
					+ (current != Emote.NONE ? " - " + current.getDisplayName() : " - empty");
			submenu.createMenuEntry(-1)
					.setOption(label)
					.setType(MenuAction.RUNELITE)
					.onClick(e -> configManager.setConfiguration(
							EmoteWheelConfig.GROUP, "slot" + s, emote));
		}
	}

	/** The wheel segment whose button is this widget, or null. */
	private Segment segmentForWidget(Widget w)
	{
		if (w == null)
		{
			return null;
		}
		long k = key(w);
		for (Segment s : segments)
		{
			if (s.getWidget() != null && key(s.getWidget()) == k)
			{
				return s;
			}
		}
		return null;
	}

	/** Clears any slot holding the given emote back to None. */
	private void removeFromSlots(Emote emote)
	{
		for (int slot = 1; slot <= 6; slot++)
		{
			if (slotConfig(slot) == emote)
			{
				configManager.setConfiguration(EmoteWheelConfig.GROUP, "slot" + slot, Emote.NONE);
			}
		}
	}

	/** Resolves a widget's clean emote name to the matching enum, exact match only. */
	private Emote emoteByName(String name)
	{
		String needle = normalise(name);
		if (needle.isEmpty())
		{
			return null;
		}
		for (Emote e : Emote.values())
		{
			if (e == Emote.NONE || e == Emote.RANDOM)
			{
				continue;
			}
			if (normalise(e.getDisplayName()).equals(needle))
			{
				return e;
			}
		}
		// Fallback for emotes whose button is renamed at runtime (only Skillcape,
		// shown as "Attack cape", "Max cape", etc.): match on the shared term.
		for (Emote e : Emote.values())
		{
			if (e == Emote.NONE || e == Emote.RANDOM)
			{
				continue;
			}
			String term = normalise(e.getMatchTerm());
			if (!term.equals(normalise(e.getDisplayName())) && needle.contains(term))
			{
				return e;
			}
		}
		return null;
	}

	private Emote slotConfig(int slot)
	{
		switch (slot)
		{
			case 1: return config.slot1();
			case 2: return config.slot2();
			case 3: return config.slot3();
			case 4: return config.slot4();
			case 5: return config.slot5();
			case 6: return config.slot6();
			default: return Emote.NONE;
		}
	}

	/** Splits the comma-separated exclude list into normalised emote names. */
	private Set<String> parseExcludes(String raw)
	{
		Set<String> out = new HashSet<>();
		if (raw != null)
		{
			for (String tok : raw.split(","))
			{
				String n = normalise(tok);
				if (!n.isEmpty())
				{
					out.add(n);
				}
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ icons

	/**
	 * The side panel's icon source: returns the emote's tab icon, loading it from the
	 * emote's sprite id on first request (async) and caching it. Returns null while the
	 * sprite is still loading or when the emote has no icon sprite; the panel is repainted
	 * once a sprite lands. Called on the AWT thread from the panel's paint.
	 */
	BufferedImage getEmoteIcon(Emote e)
	{
		if (e == null)
		{
			return null;
		}
		BufferedImage img = emoteIcons.get(e);
		if (img != null)
		{
			return img;
		}
		// Emotes with an API sprite id load straight away; the rest are handled by the
		// widget harvest on the client thread and appear once the tab has been open.
		if (e.getSpriteId() != Emote.NO_SPRITE)
		{
			loadIcon(e, e.getSpriteId());
		}
		return null;
	}

	/** Requests a sprite once, caching it and repainting the panel when it arrives. */
	private void loadIcon(Emote e, int spriteId)
	{
		if (spriteId <= 0 || !iconRequested.add(e))
		{
			return;
		}
		spriteManager.getSpriteAsync(spriteId, 0, sprite ->
		{
			if (sprite != null)
			{
				emoteIcons.put(e, sprite);
				if (panel != null)
				{
					panel.iconsUpdated();
				}
			}
		});
	}

	/**
	 * For the emotes with no API sprite id, reads the sprite off their live emote-tab
	 * button (or a child of it) and loads it. Runs on the client thread; each emote is
	 * resolved once, then dropped from the pending set.
	 */
	private void harvestMissingIcons()
	{
		Widget container = getEmoteContainer();
		if (container == null)
		{
			return;
		}
		Widget[] children = container.getDynamicChildren();
		if (children == null || children.length == 0)
		{
			children = container.getStaticChildren();
		}
		if (children == null || children.length == 0)
		{
			return;
		}
		for (Emote e : new ArrayList<>(pendingWidgetIcons))
		{
			int sid = harvestSprite(findEmoteWidget(children, e.getMatchTerm()), children);
			if (sid > 0)
			{
				pendingWidgetIcons.remove(e);
				// Swap a locked (greyed) sprite for its unlocked twin so the side panel
				// shows the coloured icon even for emotes the player has not unlocked.
				int unlocked = UNLOCKED_SPRITE.getOrDefault(sid, sid);
				log.debug("[emotewheel] harvested {} sprite {} -> {}", e, sid, unlocked);
				loadIcon(e, unlocked);
			}
		}
	}

	/**
	 * Finds the emote's icon sprite. The clickable button itself usually has no sprite
	 * (it is a bare hotspot), so we also check its children and, failing that, any sibling
	 * graphic stacked at the same spot in the grid, which is where the icon actually sits.
	 */
	private int harvestSprite(Widget button, Widget[] siblings)
	{
		if (button == null)
		{
			return -1;
		}
		if (button.getSpriteId() > 0)
		{
			return button.getSpriteId();
		}
		Widget[] kids = button.getChildren();
		if (kids != null)
		{
			for (Widget c : kids)
			{
				if (c != null && c.getSpriteId() > 0)
				{
					return c.getSpriteId();
				}
			}
		}
		int bx = button.getRelativeX();
		int by = button.getRelativeY();
		for (Widget c : siblings)
		{
			if (c == null || c == button || c.getSpriteId() <= 0)
			{
				continue;
			}
			if (c.getRelativeX() == bx && c.getRelativeY() == by)
			{
				return c.getSpriteId();
			}
		}
		return -1;
	}

	// ------------------------------------------------------------------ state

	/** The content pane holding the emote buttons. 216,2 - tall, scrolled. */
	public Widget getEmoteContainer()
	{
		return client.getWidget(InterfaceID.Emote.CONTENTS);
	}

	/**
	 * The scroll viewport. 216,1 - this is what the player can actually SEE.
	 * All centring must be derived from this, not from CONTENTS, which is
	 * roughly 2.6x taller than the visible area.
	 */
	public Widget getEmoteViewport()
	{
		return client.getWidget(InterfaceID.Emote.SCROLLABLE);
	}

	/** The whole emote interface panel (the visible bordered frame) - used for the
	 *  rearrange-mode stroke, which should hug the frame, not the inner viewport. */
	public Widget getEmoteFrame()
	{
		return client.getWidget(InterfaceID.Emote.UNIVERSE);
	}



	/**
	 * True only in the Resizable - Modern layout. The floating look (hidden background,
	 * reclaimed scrollbar, downward offsets) is built for and only sane in that layout;
	 * Fixed - Classic and Resizable - Classic get the plain, centred wheel instead.
	 */
	public boolean isModernResizable()
	{
		return client.isResized()
				&& client.getVarbitValue(VarbitID.RESIZABLE_STONE_ARRANGEMENT) == 1;
	}

	/** Whether the panel background is actually being hidden right now: Modern layout AND
	 *  the config option on. Everything floating-specific keys off this. */
	public boolean isBackgroundHidden()
	{
		return isModernResizable() && config.hidePanelBackground();
	}

	/** The wheel's horizontal nudge: the baked value while floating, the user's slider when
	 *  the background is shown. The centre rearrange text follows the same value. */
	public int getWheelOffsetX()
	{
		return isBackgroundHidden() ? WHEEL_OFFSET_X : WHEEL_OFFSET_X_SHOWN;
	}

	public boolean isTabOpen()
	{
		Widget c = getEmoteContainer();
		Widget v = getEmoteViewport();
		return c != null && !c.isHidden() && v != null && !v.isHidden();
	}

	/** True while the player is entering text - a public message, search, private
	 *  message, enter-amount, etc. - so a key hotkey shouldn't fire and toggle the wheel. */
	private boolean typingInChat()
	{
		// Meslayer inputs (search, PM, enter-amount, name entry) set a non-NONE mode.
		if (client.getVarcIntValue(VarClientID.MESLAYERMODE) != InputType.NONE.getType())
		{
			return true;
		}
		// Public chat: the input line contains the typing cursor "*" whenever the chat is
		// focused - even before any character is typed - and shows only the "Press Enter to
		// Chat" placeholder (no cursor) when idle. The cursor is colour-tagged, so match on
		// "contains" rather than "endsWith" (the raw text ends with a </col> tag).
		Widget input = client.getWidget(InterfaceID.Chatbox.INPUT);
		String txt = input == null ? null : input.getText();
		return txt != null && txt.contains("*");
	}

	// ----------------------------------------------------------------- layout

	public void tickLayout()
	{
		// Layout runs only while active and the tab is open; otherwise the grid is
		// restored instantly. originalState is NOT cleared here - the cached grid
		// geometry must survive tab open/close, or it re-measures from stale moved
		// bounds and the offset compounds. It is only reset on login / hop.
		if (!isTabOpen() || !active)
		{
			if (layoutApplied)
			{
				restoreAll();
				segments.clear();
			}
			return;
		}

		// Safety net: this method rewrites live game widgets, so if anything throws,
		// restore the default grid rather than leaving the emote tab wrecked.
		try
		{
			applyLayout();
			applyPanelBackground();
			layoutErrored = false;
		}
		catch (Exception ex)
		{
			if (!layoutErrored)
			{
				layoutErrored = true;
				log.warn("[emotewheel] layout failed, restoring grid", ex);
			}
			restoreAll();
			segments.clear();
		}
	}



	private void applyLayout()
	{
		Widget container = getEmoteContainer();
		Widget viewport = getEmoteViewport();
		if (container == null || viewport == null)
		{
			return;
		}

		Widget[] children = container.getDynamicChildren();
		if (children == null || children.length == 0)
		{
			children = container.getStaticChildren();
		}
		if (children == null || children.length == 0)
		{
			logOnce("Emote.CONTENTS has no children");
			return;
		}

		List<Emote> wanted = new ArrayList<>();
		if (pendingOrder != null)
		{
			// A drop just happened; use the committed order directly until the live
			// config reflects it, so the layout never snaps back to the pre-drop order.
			wanted.addAll(pendingOrder);
			if (configMatchesPending())
			{
				pendingOrder = null;
			}
		}
		else
		{
			wanted.add(config.slot1());
			wanted.add(config.slot2());
			wanted.add(config.slot3());
			wanted.add(config.slot4());
			wanted.add(config.slot5());
			wanted.add(config.slot6());
		}

		// Remove duplicates while preserving order, then drop "None".
		wanted = new ArrayList<>(new LinkedHashSet<>(wanted));
		wanted.remove(Emote.NONE);

		if (wanted.isEmpty())
		{
			if (layoutApplied)
			{
				restoreAll();
				segments.clear();
			}
			// Emptying the wheel drops back to classic view. Deactivate so it stays
			// there: favouriting an emote afterwards must NOT re-open the wheel on its
			// own - only the hotkey brings it back.
			active = false;
			return;
		}

		// Determine which segment the cursor is over using the PREVIOUS frame's
		// layout, so its artwork can be scaled up this frame. Ring positions are
		// stable frame-to-frame, so last frame's hit-test is accurate, and reusing
		// segmentAt keeps hover feedback identical to what a click would select.
		Emote hoveredEmote = null;
		Point mp = client.getMouseCanvasPosition();
		if (mp != null)
		{
			Segment hs = segmentAt(mp.getX(), mp.getY());
			if (hs != null)
			{
				hoveredEmote = hs.getEmote();
			}
		}

		// Drag-to-reorder input, evaluated here (client thread) against LAST frame's
		// segments before they're cleared. Press over an emote arms a drag; moving
		// past the threshold makes it a real drag (and swallows the ending click);
		// releasing while dragging reorders the slots.
		handleDragInput(mp);

		segments.clear();

		// Remember the player's scroll position the first frame we take over, so it
		// can be restored when the wheel is dismissed - otherwise toggling off snaps
		// the list to the top while the scrollbar still shows the old spot.
		if (preLayoutScrollY < 0)
		{
			preLayoutScrollY = Math.max(viewport.getScrollY(), container.getScrollY());
		}

		// Reset scroll FIRST so originalState captures un-scrolled canvas positions
		if (viewport.getScrollY() != 0)
		{
			viewport.setScrollY(0);
		}
		if (container.getScrollY() != 0)
		{
			container.setScrollY(0);
		}
		container.revalidateScroll();
		// Cache every emote button's ORIGINAL rectangle up front. The artwork
		// pairing below compares against these, so they must all exist before any
		// widget is moved - once a button is repositioned, its original rectangle
		// is the only way to work out which artwork belonged to it.
		for (Widget w : children)
		{
			if (w != null && isEmoteButton(w))
			{
				cacheState(w);
			}
		}



		// Resolve wanted -> placed, carrying the Emote alongside its widget so the
		// two never desync. Any emote present in the grid is placed, locked or not -
		// a locked one just can't be performed, which the game handles. Only slots
		// with no matching widget at all are skipped, which compacts the ring so the
		// N placed slices are evenly spaced with no holes.
		List<Placed> placed = new ArrayList<>();
		for (Emote emote : wanted)
		{
			if (emote == Emote.NONE)
			{
				continue;
			}

			if (emote == Emote.RANDOM)
			{
				// Resolved to a live emote button after favourites are known, so
				// its cycling candidates can exclude them. Placeholder for now.
				placed.add(new Placed(emote, "Random", null));
				continue;
			}

			Widget w = findEmoteWidget(children, emote.getMatchTerm());
			if (w == null)
			{
				continue;
			}
			placed.add(new Placed(emote, emote.getDisplayName(), w));
		}

		// The Random slot is a live "slot machine": while the wheel is active it
		// cycles a real, unlocked emote button through its position. Clicking it
		// performs whatever real emote is showing at that instant - a genuine click
		// on a genuine Perform button, never a plugin-initiated action (which would
		// be automation and against Plugin Hub rules).
		for (int i = 0; i < placed.size(); i++)
		{
			if (placed.get(i).emote != Emote.RANDOM)
			{
				continue;
			}

			Set<String> favNames = new HashSet<>();
			for (Placed p2 : placed)
			{
				if (p2.emote != Emote.RANDOM && p2.widget != null)
				{
					favNames.add(buttonLabel(p2.widget));
				}
			}

			// Only performable emotes are cycling candidates - the slot machine lands on
			// one and it must actually perform on click. Emotes on the player's exclude
			// list (e.g. locked ones they never unlocked) are skipped too.
			List<Widget> candidates = new ArrayList<>();
			for (Widget child : children)
			{
				if (child != null && isPerformButton(child)
						&& !favNames.contains(buttonLabel(child))
						&& !randomExcludes.contains(buttonLabel(child)))
				{
					candidates.add(child);
				}
			}

			if (candidates.isEmpty())
			{
				placed.remove(i);
				i--;
				continue;
			}

			int idx = (int) ((System.currentTimeMillis() / RANDOM_CYCLE_MS) % candidates.size());
			placed.set(i, new Placed(Emote.RANDOM, "Random", candidates.get(idx)));
		}

		if (placed.isEmpty())
		{
			if (layoutApplied)
			{
				restoreAll();
				segments.clear();
			}
			return;
		}

		int n = placed.size();

		// Rearrange mode = Shift held, OR a drag already in progress (so releasing Shift
		// mid-grab keeps the fade + stroke until the emote is dropped). Ease the frame
		// stroke's alpha toward it so the indicator fades in/out quickly.
		boolean rearrangeMode = rearrangeHeld || dragging;
		rearrangeAlpha += ((rearrangeMode ? 1.0 : 0.0) - rearrangeAlpha) * REARRANGE_EASE;

		// While dragging, which ring index is the drop aimed at right now? Used below
		// to keep that slot brighter than the faded others so the target is readable.
		int dropTargetIndex = dragging ? nearestRingIndex(mp, n) : -1;

		// The other emotes hold their positions during a drag (no live reflow) - you
		// just hover over the slot you want, which is highlighted. Only after the drop
		// does the committed order take over and everything slides into place. Keeping
		// the drag-time and drop-time layouts from fighting is what makes the drop clean.
		int[] targetIdx = new int[n];
		for (int i = 0; i < n; i++)
		{
			targetIdx[i] = i;
		}

		// Pair each artwork widget to exactly one owning button (the placed button
		// whose ORIGINAL rectangle contains the artwork's centre point; nearest
		// centre wins on overlap). One owner each, so a neighbour's figure can no
		// longer be grabbed.
		Map<Long, List<Widget>> artByButton = assignArtwork(children, placed);

		// Icon footprint for the ring auto-fit, from the buttons' own size. Fitting
		// to the enlarged figure crammed the ring, so the ring stays wide and the
		// hovered figure is instead clamped to the panel below to avoid clipping.
		int iconW = 0;
		int iconH = 0;
		for (Placed p : placed)
		{
			cacheState(p.widget);
			int[] o = originalState.get(key(p.widget));
			iconW = Math.max(iconW, o[2]);
			iconH = Math.max(iconH, o[3]);
		}

		// The scrollbar sits in a ~16px column to the RIGHT of the viewport and is hidden
		// while the wheel is active. WIDEN the viewport + content widgets to reclaim that
		// column, so emotes can be positioned AND drawn (they clip at the widget edge, so
		// math alone isn't enough) across the full panel. Without this only the RIGHT side
		// falls short - the left has no scrollbar. Widths are restored on toggle-off.
		Widget scrollbarW = client.getWidget(InterfaceID.Emote.SCROLLBAR);
		int sbW = (scrollbarW != null) ? scrollbarW.getWidth() : 0;
		if (isBackgroundHidden())
		{
			reclaimWidth(viewport, sbW);
			reclaimWidth(container, sbW);
		}
		else
		{
			// Not the floating look - leave the widths vanilla (undo if we'd widened).
			restoreWidth(viewport);
			restoreWidth(container);
		}

		// Centre on the VIEWPORT, expressed in CONTENTS-relative coordinates.
		// With scrollY pinned to 0 the two share an origin.
		int vw = viewport.getWidth();
		int vh = viewport.getHeight();

		// Edge clamp for dragged icons: the (now full-panel) viewport, both axes. The TOP
		// bound follows the downward wheel offset, so you can't drag up into the empty gap
		// the offset opens above the wheel. The downward Y offset only applies in the
		// floating (Modern + hidden background) look; other layouts get a centred wheel.
		int maxX = vw;
		int maxY = vh;
		int wheelOffY = isBackgroundHidden() ? WHEEL_OFFSET_Y : 0;
		int minY = Math.max(0, wheelOffY);

		// If the viewport reports a degenerate size for a frame, bail rather than
		// laying out against it - writing geometry derived from a bad reading is
		// how the previous shrink loop started.
		if (vw < 40 || vh < 40)
		{
			logOnce("viewport degenerate (" + vw + "x" + vh + ") - skipping layout");
			return;
		}

		// Publish the viewport bounds so the scroll blocker can eat wheel input
		// over the panel while the wheel is active.
		activeViewportBounds = viewport.getBounds();

		// Floating look uses the baked X nudge; when the background is shown the user's
		// slider re-centres the wheel for whatever Classic/Fixed panel they're in.
		int cx = vw / 2 + getWheelOffsetX();
		int cy = vh / 2 + wheelOffY;

		int hOff = HOVER_OFFSET_X;

		// The emote tab is tall and narrow (roughly 171x261), so a circle does not
		// fit. Auto-fit an ellipse and clamp to the configured radius.
		int margin = 4;
		int rx = Math.min(RADIUS, (vw / 2) - (iconW / 2) - margin);
		int ry = Math.min(RADIUS, (vh / 2) - (iconH / 2) - margin);
		rx = Math.max(rx, 8);
		ry = Math.max(ry, 8);
		// A lone emote sits dead centre rather than at the top of a one-point ring.
		if (n == 1)
		{
			rx = 0;
			ry = 0;
		}

		double[] px = new double[n];
		double[] py = new double[n];
		double[] ang = new double[n];
		for (int i = 0; i < n; i++)
		{
			double a = (2 * Math.PI * i / n) - (Math.PI / 2);
			ang[i] = a;
			px[i] = cx + Math.cos(a) * rx;
			py[i] = cy + Math.sin(a) * ry;
		}

		// Spiral entrance: the timeline eases 0 -> 1 while active; figures spiral out
		// from the centre to their ring spots, staggered so they arrive in turn.
		// Exit is instant (restoreAll resets the timeline to 0 for the next show).
		double entStagger = ENTRANCE_STAGGER;
		double entTurns = ENTRANCE_TURNS;
		entranceProgress += (1.0 - entranceProgress) * ENTRANCE_EASE;
		double ent = entranceProgress;

		Set<Long> inWheel = new HashSet<>();

		for (int i = 0; i < n; i++)
		{
			Placed p = placed.get(i);
			Widget w = p.widget;
			int[] orig = originalState.get(key(w));

			int targetW = orig[2];
			int targetH = orig[3];

			// Staggered spiral entrance: each emote starts a beat after the previous
			// (phase) and spirals out from the wheel centre to its ring spot - radius
			// grows 0->1 while its angle winds in to the final angle. Ring offset
			// shifts the whole wheel to clear the scrollbar gutter.
			double phase = (n > 1) ? (double) i / n : 0.0;
			double localT = Math.max(0.0, Math.min(1.0,
					(ent - phase * entStagger) / (1.0 - entStagger)));
			// Target centre this emote should occupy: the picked-up emote follows the
			// cursor; everyone else heads for their (possibly reflowed) ring spot so a
			// gap opens where the drag will drop.
			boolean isDragged = dragging && p.emote == dragEmote && mp != null;
			double targetCX;
			double targetCY;
			if (isDragged)
			{
				targetCX = mp.getX() - container.getCanvasLocation().getX();
				targetCY = mp.getY() - container.getCanvasLocation().getY();
			}
			else
			{
				targetCX = px[targetIdx[i]] + RING_OFFSET_X;
				targetCY = py[targetIdx[i]];
			}

			double ecx;
			double ecy;
			if (localT < 0.999 && !isDragged)
			{
				// Still entering: the spiral drives the position; keep the eased centre
				// synced to it so the handoff to easing is seamless once entrance ends.
				double spiralAngle = ang[i] - (1.0 - localT) * entTurns * (2 * Math.PI);
				ecx = cx + Math.cos(spiralAngle) * rx * localT + RING_OFFSET_X;
				ecy = cy + Math.sin(spiralAngle) * ry * localT;
				emotePos.put(p.emote, new double[]{ecx, ecy});
			}
			else if (isDragged)
			{
				// Dragged figure tracks the cursor tightly (no lag).
				ecx = targetCX;
				ecy = targetCY;
				emotePos.put(p.emote, new double[]{ecx, ecy});
			}
			else
			{
				// Steady state / reflow: glide toward the target ring spot.
				double[] pp = emotePos.get(p.emote);
				if (pp == null)
				{
					pp = new double[]{targetCX, targetCY};
					emotePos.put(p.emote, pp);
				}
				pp[0] += (targetCX - pp[0]) * POS_EASE;
				pp[1] += (targetCY - pp[1]) * POS_EASE;
				ecx = pp[0];
				ecy = pp[1];
			}

			// Never allow a forced position of -1: that is the sentinel that CLEARS
			// the forced position, snapping the widget back to its default (centred)
			// spot. Clamping to >= 0 keeps figures pinned as they scale / shift.
			int centreX = (int) Math.round(ecx);
			int centreY = (int) Math.round(ecy);
			// Clamp inside the visible viewport on ALL sides. Math.max keeps the top/left
			// edges in; Math.min keeps the right/bottom in so a dragged icon stops at the
			// edge instead of clipping out of existence.
			// Keep the whole icon inside the viewport (no overhang, so nothing clips). The
			// dragged icon is NOT hover-enlarged (see targetScale), so its full width fits
			// and it can still reach the edge.
			int x = Math.max(0, Math.min(maxX - targetW, centreX - targetW / 2));
			int y = Math.max(minY, Math.min(maxY - targetH, centreY - targetH / 2));

			// Reposition the real button (core behaviour). Forced position survives
			// the interface revalidating itself.
			w.setHidden(false);
			w.setOriginalWidth(targetW);
			w.setOriginalHeight(targetH);
			w.revalidate();
			w.setForcedPosition(x, y);
			layoutApplied = true;
			inWheel.add(key(w));

			// Carry the artwork to the same centre so the figure lands inside the
			// wedge rather than staying back in the grid. The hovered emote grows by
			// the configured hover scale for cursor feedback. The Random slot is
			// treated identically - its cycling candidate shows its real figure.
			List<Widget> art = artByButton.getOrDefault(key(w), Collections.emptyList());

			boolean hovered = (p.emote == hoveredEmote);

			// Targets: hovered figure grows (and dips while the button is held); non-hovered
			// figures fade when something else is hovered. The DRAGGED icon is kept at base
			// size (not grown) so its full width fits in the viewport and it can reach the
			// edge without clipping.
			boolean grow = hovered && !isDragged;
			double targetScale = grow ? ICON_SCALE * HOVER_SCALE : ICON_SCALE;
			if (grow && mousePressed)
			{
				targetScale *= PRESS_SCALE;
			}
			double targetOpacity = (hoveredEmote != null && !hovered) ? FADE_OPACITY : 0.0;
			// Holding the rearrange key with nothing hovered dims everything, signalling
			// rearrange mode; hovering one brings it back so you see your grab target.
			if (rearrangeMode && hoveredEmote == null)
			{
				targetOpacity = ICON_FADE_OPACITY;
			}
			// Bring the drop-target slot mostly back so it's clear where the drag lands.
			if (dragging && i == dropTargetIndex)
			{
				targetOpacity = FADE_OPACITY * 0.25;
			}

			// Ease scale and opacity toward their targets so hover / press / fade
			// transitions glide instead of snapping.
			double[] an = anim.computeIfAbsent(p.emote, k -> new double[]{ICON_SCALE, 0.0});
			an[0] += (targetScale - an[0]) * ANIM_EASE;
			an[1] += (targetOpacity - an[1]) * ANIM_EASE;

			double baseScale = ICON_SCALE;
			double scale = an[0];
			int figureOpacity = (int) Math.round(an[1]);

			// Placement from ACTUAL on-screen geometry (getBounds/getWidth) so odd
			// size/position modes can't throw a figure off; size mode is forced to
			// ABSOLUTE while placed (restored on toggle-off).
			//
			// The figure's CENTRE is pinned to its BASE-scale position and the box is
			// grown about that centre. Growing about the ring point (the old way) slid
			// off-centre emotes (slots 5-6) sideways as they enlarged on hover; this
			// keeps the figure exactly where it already sits and only makes it bigger.
			// The horizontal-offset slider nudges the hovered figure only.
			double buttonCanvasCX = orig[10] + orig[6] / 2.0;
			double buttonCanvasCY = orig[11] + orig[7] / 2.0;
			for (Widget a : art)
			{
				int[] ao = originalState.get(key(a));
				if (ao == null)
				{
					continue;
				}
				double offX = (ao[10] + ao[6] / 2.0) - buttonCanvasCX;
				double offY = (ao[11] + ao[7] / 2.0) - buttonCanvasCY;

				// Fixed figure centre from the base scale; only the SIZE changes on
				// hover, so the figure grows in place instead of sliding.
				double figCX = centreX + baseScale * offX;
				double figCY = centreY + baseScale * offY;

				int aw = Math.max(1, (int) Math.round(ao[6] * scale * localT));
				int ah = Math.max(1, (int) Math.round(ao[7] * scale * localT));

				// Clamp to >= 0 so a forced position never lands on -1 (the clear
				// sentinel), which would snap the figure to the panel centre.
				int fx = Math.max(0, Math.min(maxX - aw, (int) Math.round(figCX - aw / 2.0) + (hovered ? hOff : 0)));
				int fy = Math.max(minY, Math.min(maxY - ah, (int) Math.round(figCY - ah / 2.0)));

				a.setHidden(false);
				a.setWidthMode(WidgetSizeMode.ABSOLUTE);
				a.setHeightMode(WidgetSizeMode.ABSOLUTE);
				a.setOriginalWidth(aw);
				a.setOriginalHeight(ah);
				a.revalidate();
				a.setForcedPosition(fx, fy);
				a.setOpacity(figureOpacity);
				inWheel.add(key(a));
			}

			segments.add(new Segment(p.emote, w));
		}

		// Hide every emote-related widget that is not on the wheel - both the
		// buttons AND their artwork - so only the ring is left in the panel.
		for (Widget w : children)
		{
			if (w == null || inWheel.contains(key(w)))
			{
				continue;
			}
			if (!isEmoteButton(w) && !isEmoteArtwork(children, w))
			{
				continue;
			}
			cacheState(w);
			w.setHidden(true);
		}

		// Hide the scrollbar while the wheel is active so the panel can't be
		// scrolled (the ring is pinned to the viewport centre; scrolling would
		// slide it away). Restored on toggle-off.
		Widget scrollbar = client.getWidget(InterfaceID.Emote.SCROLLBAR);
		if (scrollbar != null)
		{
			cacheState(scrollbar);
			scrollbar.setHidden(true);
		}

		container.revalidateScroll();
		logOnce("Placed " + n + "/" + wanted.size() + " emotes, ring " + rx + "x" + ry
				+ " in viewport " + vw + "x" + vh);
	}

	/**
	 * Assigns each artwork widget to exactly one owning button. An artwork belongs
	 * to a button when the artwork's centre point falls inside that button's
	 * ORIGINAL rectangle; if two placed buttons contain it (overlapping originals),
	 * the nearer button centre wins. Only placed buttons are considered as owners.
	 */
	private Map<Long, List<Widget>> assignArtwork(Widget[] children, List<Placed> placed)
	{
		Map<Long, List<Widget>> out = new HashMap<>();
		for (Placed p : placed)
		{
			out.put(key(p.widget), new ArrayList<>());
		}

		for (Widget w : children)
		{
			if (w == null || isEmoteButton(w))
			{
				continue;
			}
			cacheState(w);
			int[] o = originalState.get(key(w));
			if (o == null)
			{
				continue;
			}
			double acx = o[0] + Math.max(o[2], 1) / 2.0;
			double acy = o[1] + Math.max(o[3], 1) / 2.0;

			Placed owner = null;
			double best = Double.MAX_VALUE;
			for (Placed p : placed)
			{
				int[] bo = originalState.get(key(p.widget));
				if (bo == null)
				{
					continue;
				}
				Rectangle br = new Rectangle(bo[0], bo[1], Math.max(bo[2], 1), Math.max(bo[3], 1));
				if (br.contains(acx, acy))
				{
					double bcx = bo[0] + Math.max(bo[2], 1) / 2.0;
					double bcy = bo[1] + Math.max(bo[3], 1) / 2.0;
					double d = (bcx - acx) * (bcx - acx) + (bcy - acy) * (bcy - acy);
					if (d < best)
					{
						best = d;
						owner = p;
					}
				}
			}
			if (owner != null)
			{
				out.get(key(owner.widget)).add(w);
			}
		}
		return out;
	}

	/** True if this child overlaps any emote button's original rectangle. */
	private boolean isEmoteArtwork(Widget[] children, Widget candidate)
	{
		int[] co = originalState.get(key(candidate));
		if (co == null)
		{
			cacheState(candidate);
			co = originalState.get(key(candidate));
			if (co == null)
			{
				return false;
			}
		}
		Rectangle cr = new Rectangle(co[0], co[1], Math.max(co[2], 1), Math.max(co[3], 1));

		for (Widget w : children)
		{
			if (w == null || w == candidate || !isEmoteButton(w))
			{
				continue;
			}
			int[] o = originalState.get(key(w));
			if (o == null)
			{
				continue;
			}
			if (cr.intersects(new Rectangle(o[0], o[1], Math.max(o[2], 1), Math.max(o[3], 1))))
			{
				return true;
			}
		}
		return false;
	}

	/** True only for unlocked, performable emote buttons (carry the Perform action). */
	private boolean isPerformButton(Widget w)
	{
		String[] actions = w.getActions();
		if (actions == null)
		{
			return false;
		}
		for (String a : actions)
		{
			if (a == null)
			{
				continue;
			}
			String na = a.trim().toLowerCase();
			// Most emotes carry a plain "Perform"; a few (Relic unlock) carry the
			// emote in the action itself as "Perform <name>".
			if (na.equals(PERFORM_ACTION) || na.startsWith(PERFORM_ACTION + " "))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * True for any emote button, locked or unlocked. Unlocked ones carry the
	 * Perform action; locked ones don't, but still carry the emote's name, so a
	 * name match catches them. This lets locked emotes be placed on the wheel like
	 * any other - clicking one simply does nothing, which the game already handles.
	 */
	private boolean isEmoteButton(Widget w)
	{
		if (isPerformButton(w))
		{
			return true;
		}
		String nm = buttonLabel(w);
		return !nm.isEmpty() && EMOTE_NAMES.contains(nm);
	}

	// --------------------------------------------------------------- matching

	/**
	 * Text shown after the menu option (e.g. "Favorite <em>Bow</em>"). Normal emote
	 * buttons already provide a coloured target; the blank-named ones (Relic unlock)
	 * provide none, so fall back to the resolved emote's name.
	 */
	private String emoteTarget(MenuEntry entry, Emote emote)
	{
		String t = entry.getTarget();
		// The game colours emote targets orange (ff9040); match it for the blank-named
		// ones so "Favorite Relic unlock" looks identical to every other emote.
		return normalise(t).isEmpty() ? "<col=ff9040>" + emote.getDisplayName() + "</col>" : t;
	}

	/**
	 * The emote's identifying text. Almost every button exposes it via getName()
	 * ("bow", "jump for joy"). A few special emotes (Relic unlock) leave the name
	 * and text blank and instead carry it in their action as "Perform &lt;name&gt;";
	 * for those we recover the name from the action.
	 */
	private String buttonLabel(Widget w)
	{
		String name = normalise(w.getName());
		if (!name.isEmpty())
		{
			return name;
		}
		String[] actions = w.getActions();
		if (actions != null)
		{
			for (String a : actions)
			{
				if (a == null)
				{
					continue;
				}
				String na = normalise(a);
				if (na.startsWith(PERFORM_ACTION + " "))
				{
					return na.substring(PERFORM_ACTION.length() + 1).trim();
				}
			}
		}
		return "";
	}

	/**
	 * Match a wanted emote to its button by label. Exact match first so "bow"
	 * cannot swallow "goblin bow"; then substring so a match term like "cape"
	 * catches the renamed Skillcape button.
	 */
	private Widget findEmoteWidget(Widget[] children, String emoteName)
	{
		String needle = normalise(emoteName);

		for (Widget w : children)
		{
			if (w == null) continue;
			if (buttonLabel(w).equals(needle))
			{
				return w;
			}
		}

		for (Widget w : children)
		{
			if (w == null) continue;
			String nm = buttonLabel(w);
			if (!nm.isEmpty() && nm.contains(needle))
			{
				return w;
			}
		}

		return null;
	}

	private static String normalise(String s)
	{
		if (s == null)
		{
			return "";
		}
		return s.replaceAll("<[^>]*>", " ")
				.replaceAll("\\s+", " ")
				.trim()
				.toLowerCase();
	}

	// -------------------------------------------------------------- geometry

	private long key(Widget w)
	{
		return ((long) w.getId() << 32) | (w.getIndex() & 0xffffffffL);
	}

	private void cacheState(Widget w)
	{
		originalState.computeIfAbsent(key(w), k ->
		{
			Rectangle b = w.getBounds();
			int canvasX = (b != null) ? b.x : w.getOriginalX();
			int canvasY = (b != null) ? b.y : w.getOriginalY();
			return new int[]{
					w.getOriginalX(), w.getOriginalY(),
					w.getOriginalWidth(), w.getOriginalHeight(),
					w.isSelfHidden() ? 1 : 0,
					w.getModelZoom(),
					w.getWidth(), w.getHeight(),
					w.getWidthMode(), w.getHeightMode(),
					canvasX, canvasY,
					w.getOpacity()
			};
		});
	}

	/**
	 * Widens a widget by 'extra' px (forcing ABSOLUTE width) so the emote area can reclaim
	 * the hidden scrollbar's column. Idempotent, and caches the original width/mode first
	 * so {@link #restoreWidth} can put it back exactly. Based on the ORIGINAL on-screen
	 * width (o[6]), not getOriginalWidth, which for a non-ABSOLUTE mode isn't the px width.
	 */
	private void reclaimWidth(Widget w, int extra)
	{
		if (w == null || extra <= 0)
		{
			return;
		}
		cacheState(w);
		int[] o = originalState.get(key(w));
		if (o == null)
		{
			return;
		}
		int target = o[6] + extra;
		// Check getOriginalWidth (reflects the set value immediately) rather than getWidth
		// (which only updates after revalidate) so we don't revalidate every frame.
		if (w.getOriginalWidth() != target || w.getWidthMode() != WidgetSizeMode.ABSOLUTE)
		{
			w.setWidthMode(WidgetSizeMode.ABSOLUTE);
			w.setOriginalWidth(target);
			w.revalidate();
		}
	}

	/** Restores a widget's original width mode and width (cached by {@link #reclaimWidth}). */
	private void restoreWidth(Widget w)
	{
		if (w == null)
		{
			return;
		}
		int[] o = originalState.get(key(w));
		if (o == null)
		{
			return;
		}
		if (w.getOriginalWidth() != o[2] || w.getWidthMode() != o[8])
		{
			w.setWidthMode(o[8]);
			w.setOriginalWidth(o[2]);
			w.revalidate();
		}
	}

	/** Background/frame graphics we've hidden for the "Hide panel background" option. */
	private final List<Widget> hiddenBg = new ArrayList<>();

	/**
	 * Hides the emote panel's own background/frame graphics (GRAPHIC and RECTANGLE widgets
	 * that are DIRECT children of the panel root) so the emotes appear to float. The emote
	 * artwork is also GRAPHIC, but it lives deeper inside CONTENTS, so walking only the
	 * root's own children leaves the emotes untouched. A no-op unless the option is on.
	 */
	private void applyPanelBackground()
	{
		if (!isBackgroundHidden())
		{
			restorePanelBackground();
			return;
		}
		Widget root = getEmoteFrame();
		if (root == null)
		{
			return;
		}
		// The background/frame live on an ANCESTOR of the emote interface (the side-panel
		// container), not inside it - so hide the graphics there, keeping the emote
		// widgets (which are deeper, under the container's layer children) untouched.
		Widget container = findPanelContainer(root);
		if (container != null)
		{
			hidePanelGraphics(container.getStaticChildren());
			hidePanelGraphics(container.getDynamicChildren());
		}
	}

	/** Walks up from the emote panel to the widget that owns the panel background/frame:
	 *  the first ancestor with a panel-sized GRAPHIC child (the side-panel background). */
	private Widget findPanelContainer(Widget root)
	{
		Widget p = root.getParent();
		int up = 0;
		while (p != null && up < 6)
		{
			if (hasPanelSizedGraphic(p.getStaticChildren()) || hasPanelSizedGraphic(p.getDynamicChildren()))
			{
				return p;
			}
			p = p.getParent();
			up++;
		}
		return null;
	}

	private boolean hasPanelSizedGraphic(Widget[] children)
	{
		if (children == null)
		{
			return false;
		}
		for (Widget c : children)
		{
			if (c == null || c.getType() != WidgetType.GRAPHIC)
			{
				continue;
			}
			Rectangle b = c.getBounds();
			if (b != null && b.width >= 150 && b.height >= 200)
			{
				return true;
			}
		}
		return false;
	}

	private void hidePanelGraphics(Widget[] children)
	{
		if (children == null)
		{
			return;
		}
		for (Widget c : children)
		{
			if (c == null)
			{
				continue;
			}
			int t = c.getType();
			if (t != WidgetType.GRAPHIC && t != WidgetType.RECTANGLE)
			{
				continue;
			}
			if (hiddenBg.contains(c))
			{
				// One we hid: keep it hidden if the interface revalidated it back on.
				c.setHidden(true);
			}
			else if (!c.isHidden())
			{
				// Newly seen and visible: hide it and remember it so we only ever
				// restore what WE hid (never a widget that was hidden already).
				c.setHidden(true);
				hiddenBg.add(c);
			}
		}
	}

	private void restorePanelBackground()
	{
		for (Widget w : hiddenBg)
		{
			if (w != null)
			{
				w.setHidden(false);
			}
		}
		hiddenBg.clear();
	}

	private void restoreAll()
	{
		restorePanelBackground();
		// Put the reclaimed scrollbar column back (viewport + content widths).
		restoreWidth(getEmoteViewport());
		restoreWidth(getEmoteContainer());
		activeViewportBounds = null;
		anim.clear();
		emotePos.clear();
		pendingOrder = null;
		entranceProgress = 0;

		// Drop any in-progress drag so it can't carry across a toggle.
		dragEmote = null;
		dragging = false;
		suppressClick = false;
		shiftDragPress = false;
		prevMousePressed = false;
		rearrangeAlpha = 0;

		Widget scrollbar = client.getWidget(InterfaceID.Emote.SCROLLBAR);
		if (scrollbar != null)
		{
			int[] sb = originalState.get(key(scrollbar));
			if (sb != null)
			{
				scrollbar.setHidden(sb[4] == 1);
			}
		}

		Widget container = getEmoteContainer();
		if (container != null)
		{
			Widget[] children = container.getDynamicChildren();
			if (children == null || children.length == 0)
			{
				children = container.getStaticChildren();
			}
			if (children != null)
			{
				for (Widget w : children)
				{
					if (w == null) continue;
					int[] o = originalState.get(key(w));
					if (o == null) continue;

					w.setForcedPosition(-1, -1);
					w.setOriginalX(o[0]);
					w.setOriginalY(o[1]);
					w.setWidthMode(o[8]);
					w.setHeightMode(o[9]);
					w.setOriginalWidth(o[2]);
					w.setOriginalHeight(o[3]);
					w.setHidden(o[4] == 1);
					w.setModelZoom(o[5]);
					w.setOpacity(o[12]);
					w.revalidate();
				}
			}
			// Restore the player's original scroll position (saved when the wheel
			// took over) and rebuild the scroll layout, so dismissing the wheel
			// returns the list exactly where they left it with the scrollbar and
			// content in sync - not snapped to the top while the bar shows the old
			// spot.
			int restoreScroll = preLayoutScrollY >= 0 ? preLayoutScrollY : 0;
			container.setScrollY(restoreScroll);
			container.revalidateScroll();

			Widget viewport = getEmoteViewport();
			if (viewport != null)
			{
				viewport.setScrollY(restoreScroll);
				viewport.revalidateScroll();
			}
			preLayoutScrollY = -1;
		}
		layoutApplied = false;
	}

	// ------------------------------------------------------------- hit testing

	/**
	 * Selection source of truth. Hit-tests real widget rectangles rather than
	 * cursor angle, so the highlight can never disagree with what a click hits.
	 */
	public Segment segmentAt(int mouseX, int mouseY)
	{
		Widget viewport = getEmoteViewport();
		if (viewport == null || segments.isEmpty())
		{
			return null;
		}

		int cx = viewport.getCanvasLocation().getX() + viewport.getWidth() / 2;
		int cy = viewport.getCanvasLocation().getY() + viewport.getHeight() / 2;

		// The centre dead zone keeps the label area from selecting a wedge - but a lone
		// emote sits dead centre, so skip the dead zone when there's only one to hit.
		int dz = DEAD_ZONE;
		long dx = mouseX - cx;
		long dy = mouseY - cy;
		if (segments.size() > 1 && dx * dx + dy * dy < (long) dz * dz)
		{
			return null;
		}

		Segment best = null;
		double bestDist = Double.MAX_VALUE;

		for (Segment s : segments)
		{
			Widget w = s.getWidget();
			if (w == null || w.isHidden()) continue;
			Rectangle b = w.getBounds();
			if (!b.contains(mouseX, mouseY)) continue;
			double ddx = b.getCenterX() - mouseX;
			double ddy = b.getCenterY() - mouseY;
			double d = ddx * ddx + ddy * ddy;
			if (d < bestDist)
			{
				bestDist = d;
				best = s;
			}
		}
		return best;
	}

	// ----------------------------------------------------------- drag reorder

	/**
	 * Shift-drag reorder state machine. Runs on the layout thread each frame using
	 * the shared {@link #mousePressed}/{@link #shiftDragPress} flags and the cursor,
	 * so it never touches widgets off the client thread. Plain clicks are never
	 * intercepted, so they perform emotes exactly as before.
	 */
	private void handleDragInput(Point cursor)
	{
		boolean pressedNow = mousePressed;

		if (pressedNow && !prevMousePressed)
		{
			// Press edge: a drag begins only on a Shift+left-press that landed on a ring
			// emote. That press was already swallowed by the mouse listener, so no emote
			// performs. Plain presses arm nothing and click through as normal.
			Segment hit = (shiftDragPress && cursor != null)
					? segmentAt(cursor.getX(), cursor.getY()) : null;
			if (hit != null)
			{
				dragEmote = hit.getEmote();
				dragging = true;
				suppressClick = true;
			}
			else
			{
				dragEmote = null;
				dragging = false;
			}
		}
		else if (!pressedNow && prevMousePressed)
		{
			// Release edge: a drag drops into the nearest ring position.
			if (dragging && dragEmote != null && cursor != null)
			{
				reorderByDrop(dragEmote, cursor);
			}
			dragEmote = null;
			dragging = false;
		}

		prevMousePressed = pressedNow;
	}

	/**
	 * Moves the dragged emote to the ring position nearest the drop point, shifting
	 * the others to make room, then writes the new order back to slots 1..N.
	 */
	private void reorderByDrop(Emote dragged, Point drop)
	{
		if (segments.size() < 2)
		{
			return;
		}

		List<Emote> order = new ArrayList<>();
		for (Segment s : segments)
		{
			order.add(s.getEmote());
		}
		int n = order.size();
		if (!order.contains(dragged))
		{
			return;
		}

		int k = nearestRingIndex(drop, n);
		if (k < 0)
		{
			return;
		}

		if (config.dragMode() == DragMode.SWAP)
		{
			// Swap: the dragged emote and whatever sits at the drop slot trade places;
			// nothing else moves.
			int s = order.indexOf(dragged);
			if (s >= 0 && k < order.size() && k != s)
			{
				Collections.swap(order, s, k);
			}
		}
		else
		{
			// Move: the dragged emote drops into the slot; the rest shift to make room.
			order.remove(dragged);
			order.add(Math.min(k, order.size()), dragged);
		}

		// Persist: slots 1..N take the new order, any leftover slots become None.
		for (int i = 0; i < 6; i++)
		{
			Emote val = (i < order.size()) ? order.get(i) : Emote.NONE;
			configManager.setConfiguration(EmoteWheelConfig.GROUP, "slot" + (i + 1), val);
		}

		// Drive the layout from this committed order until the config write propagates.
		pendingOrder = new ArrayList<>(order);
	}

	/** True once the live config matches the just-dropped order (config has caught up). */
	private boolean configMatchesPending()
	{
		if (pendingOrder == null)
		{
			return true;
		}
		for (int i = 0; i < 6; i++)
		{
			Emote want = (i < pendingOrder.size()) ? pendingOrder.get(i) : Emote.NONE;
			if (slotConfig(i + 1) != want)
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * The ring index (0..n-1) whose evenly-spaced angle is nearest the given canvas
	 * point, measured from the viewport centre. -1 if the viewport isn't available.
	 * Shared by the drop (where to insert) and the live drop-target highlight.
	 */
	private int nearestRingIndex(Point cursor, int n)
	{
		Widget viewport = getEmoteViewport();
		if (viewport == null || cursor == null || n < 1)
		{
			return -1;
		}
		int vcx = viewport.getCanvasLocation().getX() + viewport.getWidth() / 2;
		int vcy = viewport.getCanvasLocation().getY() + viewport.getHeight() / 2;
		double theta = Math.atan2(cursor.getY() - vcy, cursor.getX() - vcx);

		int k = 0;
		double best = Double.MAX_VALUE;
		for (int i = 0; i < n; i++)
		{
			double a = (2 * Math.PI * i / n) - (Math.PI / 2);
			double d = Math.abs(angleDelta(theta, a));
			if (d < best)
			{
				best = d;
				k = i;
			}
		}
		return k;
	}

	/** Smallest signed difference between two angles, in radians (-PI..PI). */
	private static double angleDelta(double a, double b)
	{
		double d = a - b;
		while (d > Math.PI)
		{
			d -= 2 * Math.PI;
		}
		while (d < -Math.PI)
		{
			d += 2 * Math.PI;
		}
		return d;
	}

	// -------------------------------------------------------------- debugging

	/** log.debug is invisible without --debug; use info and dedupe. */
	private void logOnce(String msg)
	{
		if (!msg.equals(lastLog))
		{
			lastLog = msg;
			log.info("[emotewheel] {}", msg);
		}
	}

	/** One placed emote: the Emote, its display name, and its button widget. */
	private static final class Placed
	{
		private final Emote emote;
		private final String name;
		private final Widget widget;

		Placed(Emote emote, String name, Widget widget)
		{
			this.emote = emote;
			this.name = name;
			this.widget = widget;
		}
	}

	/** One laid-out emote: the emote it represents and its click-area widget. */
	public static class Segment
	{
		@Getter private final Emote emote;
		/** The widget carrying the Perform action - the transparent hit area. */
		@Getter private final Widget widget;

		Segment(Emote emote, Widget widget)
		{
			this.emote = emote;
			this.widget = widget;
		}
	}
}
