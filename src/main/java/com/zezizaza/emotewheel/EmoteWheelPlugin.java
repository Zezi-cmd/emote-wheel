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
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.input.MouseWheelListener;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;

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
	/** Pixel shift of the whole ring (compensates the scrollbar gutter). */
	private static final int RING_OFFSET_X = -1;
	/** Transparency applied to non-hovered figures when something is hovered (0=opaque, 255=clear). */
	private static final int FADE_OPACITY = 130;
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
	@Inject private OverlayManager overlayManager;
	@Inject private EmoteWheelConfig config;
	@Inject private EmoteWheelOverlay overlay;

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

	/** Tracks the left button so the hovered figure can dip while pressed. */
	private final MouseListener pressListener = new MouseAdapter()
	{
		@Override
		public MouseEvent mousePressed(MouseEvent e)
		{
			if (e.getButton() == MouseEvent.BUTTON1)
			{
				mousePressed = true;
			}
			return e;
		}

		@Override
		public MouseEvent mouseReleased(MouseEvent e)
		{
			if (e.getButton() == MouseEvent.BUTTON1)
			{
				mousePressed = false;
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

	/** True while the wheel is active (favourites arranged). Toggled by the hotkey. */
	@Getter private boolean active;

	/** Emote name shown in the wheel centre while hovering (kept during fade-out). */
	@Getter private String hoverLabel = "";
	/** 0..1 fade of the centre label, eased each frame. */
	@Getter private double labelAlpha;
	/** 0..1 fan-out entrance progress; 0 = figures stacked at centre, 1 = at the ring. */
	private double entranceProgress;

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

	private final HotkeyListener hotkeyListener = new HotkeyListener(() -> config.hotkey())
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
		mouseManager.registerMouseWheelListener(scrollBlocker);
		mouseManager.registerMouseListener(pressListener);
		// Restore the remembered on/off state from the previous session.
		active = Boolean.TRUE.equals(
				configManager.getConfiguration(EmoteWheelConfig.GROUP, "active", Boolean.class));
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		keyManager.unregisterKeyListener(hotkeyListener);
		mouseManager.unregisterMouseWheelListener(scrollBlocker);
		mouseManager.unregisterMouseListener(pressListener);
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
		// Drive the layout just before the client draws widgets, so the wheel is in
		// place on the very frame the emote tab opens - no flash of the raw grid
		// (an ABOVE_WIDGETS overlay would run after the draw and be one frame late).
		tickLayout();
	}

	// Config changes need no handler: applyLayout reads the live config every
	// frame, so slot edits are picked up on the next frame without a
	// restore-then-reapply, which used to flash the whole grid mid-edit.

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
			// removed correctly rather than the stand-in emote.
			Segment seg = segmentForWidget(emoteEntry.getWidget());
			if (seg == null)
			{
				return;
			}
			Emote toRemove = seg.getEmote();
			client.getMenu().createMenuEntry(1)
					.setOption("Remove")
					.setTarget(emoteEntry.getTarget())
					.setType(MenuAction.RUNELITE)
					.onClick(e -> removeFromSlots(toRemove));
			return;
		}

		// Off the wheel: offer to favourite this emote into a slot.
		Emote emote = emoteByName(emoteEntry.getWidget().getName());
		if (emote == null)
		{
			return;
		}

		MenuEntry parent = client.getMenu().createMenuEntry(1)
				.setOption("Favorite")
				.setTarget(emoteEntry.getTarget())
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

	public boolean isTabOpen()
	{
		Widget c = getEmoteContainer();
		Widget v = getEmoteViewport();
		return c != null && !c.isHidden() && v != null && !v.isHidden();
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
		wanted.add(config.slot1());
		wanted.add(config.slot2());
		wanted.add(config.slot3());
		wanted.add(config.slot4());
		wanted.add(config.slot5());
		wanted.add(config.slot6());

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

			Widget w = findEmoteWidget(children, emote.getDisplayName());
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
					favNames.add(normalise(p2.widget.getName()));
				}
			}

			// Only performable (unlocked) emotes are cycling candidates - the slot
			// machine lands on one and it must actually perform on click.
			List<Widget> candidates = new ArrayList<>();
			for (Widget child : children)
			{
				if (child != null && isPerformButton(child)
						&& !favNames.contains(normalise(child.getName())))
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

		// Centre on the VIEWPORT, expressed in CONTENTS-relative coordinates.
		// With scrollY pinned to 0 the two share an origin.
		int vw = viewport.getWidth();
		int vh = viewport.getHeight();

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

		int cx = vw / 2;
		int cy = vh / 2;

		int hOff = HOVER_OFFSET_X;

		// The emote tab is tall and narrow (roughly 171x261), so a circle does not
		// fit. Auto-fit an ellipse and clamp to the configured radius.
		int margin = 4;
		int rx = Math.min(RADIUS, (vw / 2) - (iconW / 2) - margin);
		int ry = Math.min(RADIUS, (vh / 2) - (iconH / 2) - margin);
		rx = Math.max(rx, 8);
		ry = Math.max(ry, 8);

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
			double spiralAngle = ang[i] - (1.0 - localT) * entTurns * (2 * Math.PI);
			int centreX = (int) Math.round(cx + Math.cos(spiralAngle) * rx * localT) + RING_OFFSET_X;
			int centreY = (int) Math.round(cy + Math.sin(spiralAngle) * ry * localT);
			// Never allow a forced position of -1: that is the sentinel that CLEARS
			// the forced position, snapping the widget back to its default (centred)
			// spot. Clamping to >= 0 keeps figures pinned as they scale / shift.
			int x = Math.max(0, centreX - targetW / 2);
			int y = Math.max(0, centreY - targetH / 2);

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
			// Only the Random slot gets a centre label ("Random"); normal emotes
			// already show their name in the game tooltip, so a label would just
			// duplicate it. The Random slot's tooltip cycles candidate names, so the
			// steady "Random" label is where it earns its keep.
			if (hovered && p.emote == Emote.RANDOM)
			{
				hoverLabel = p.name;
			}

			// Targets: hovered figure grows (and dips while the button is held);
			// non-hovered figures fade when something else is hovered.
			double targetScale = hovered ? ICON_SCALE * HOVER_SCALE : ICON_SCALE;
			if (hovered && mousePressed)
			{
				targetScale *= PRESS_SCALE;
			}
			double targetOpacity = (hoveredEmote != null && !hovered) ? FADE_OPACITY : 0.0;

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
				int fx = Math.max(0, (int) Math.round(figCX - aw / 2.0) + (hovered ? hOff : 0));
				int fy = Math.max(0, (int) Math.round(figCY - ah / 2.0));

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

		// Fade the centre label in/out - only while the Random slot is hovered.
		double targetLabelAlpha = (hoveredEmote == Emote.RANDOM) ? 1.0 : 0.0;
		labelAlpha += (targetLabelAlpha - labelAlpha) * ANIM_EASE;

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
			if (a != null && a.trim().equalsIgnoreCase(PERFORM_ACTION))
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
		String nm = normalise(w.getName());
		return !nm.isEmpty() && EMOTE_NAMES.contains(nm);
	}

	// --------------------------------------------------------------- matching

	/**
	 * getName() returns the plain emote name ("bow", "jump for joy") on every
	 * child, so name matching is the only path used. Exact match first so "bow"
	 * cannot swallow "goblin bow".
	 */
	private Widget findEmoteWidget(Widget[] children, String emoteName)
	{
		String needle = normalise(emoteName);

		for (Widget w : children)
		{
			if (w == null) continue;
			if (normalise(w.getName()).equals(needle))
			{
				return w;
			}
		}

		for (Widget w : children)
		{
			if (w == null) continue;
			String nm = normalise(w.getName());
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

	private void restoreAll()
	{
		activeViewportBounds = null;
		anim.clear();
		labelAlpha = 0;
		entranceProgress = 0;

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

		int dz = DEAD_ZONE;
		long dx = mouseX - cx;
		long dy = mouseY - cy;
		if (dx * dx + dy * dy < (long) dz * dz)
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
