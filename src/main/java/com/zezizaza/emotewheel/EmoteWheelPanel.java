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

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The custom "Favorites" side panel. Six slot buttons show each favourite by name;
 * tapping a slot slides the slots below it down and reveals an emote picker in the gap,
 * and picking one slides it back closed. Emotes already used in another slot are disabled
 * in the picker, so a duplicate can never be chosen. Slots can also be dragged to reorder,
 * with a lifted row and an insertion line for feedback. All hand-drawn so we fully control
 * the look - unlike the auto config dropdowns, which can neither reject a pick nor refresh.
 */
class EmoteWheelPanel extends PluginPanel
{
	private static final int SLOTS = 6;
	private static final float FONT_SIZE = 16f;
	/** Minimum row height; rows grow past this to fit larger emote icons. */
	private static final int SLOT_H = 42;
	private static final int STRUT = 4;
	/** Vertical padding above and below the icon when it sets the row height. */
	private static final int ICON_PAD_V = 5;
	/** Safety cap so a huge icon scale can't make an unusably tall row. */
	private static final int ROW_MAX_H = 160;
	private static final int OPTION_H = 34;
	private static final int PICKER_H = 340;
	/** Press-and-hold this long on a slot to arm a drag (the grab bars fade in). */
	private static final int HOLD_MS = 200;
	/** Base left inset of the slot text, before the config inset is added. */
	private static final int TEXT_LEFT_PAD = 10;
	/** Horizontal room the grab bars occupy; the text slides right by this while dragging. */
	private static final int BAR_ROOM = 18;
	/** Baked-in layout: icon on the left, content inset 10, no extra icon-text gap. */
	private static final int FIXED_INSET = 10;
	private static final int FIXED_ICON_GAP = 10;
	private static final float DIM = 0.35f;
	/** Slot grows a touch on hover and dips on press, like the in-game emote icons. */
	private static final float SLOT_HOVER_SCALE = 1.05f;
	private static final float SLOT_PRESS_SCALE = 0.90f;
	/** Overshoot the dropped slot pops to before settling, for a little landing bounce. */
	private static final float SLOT_DROP_POP = 1.20f;

	private static final Font FONT = FontManager.getRunescapeBoldFont().deriveFont(FONT_SIZE);
	/** Larger fonts for the panel title and the section headers. */
	private static final Font TITLE_FONT = FontManager.getRunescapeBoldFont().deriveFont(20f);
	private static final Font HEADER_FONT = FontManager.getRunescapeBoldFont().deriveFont(17f);
	/** Emote icons render at a fixed 125% of their native size. */
	private static final double FIXED_ICON_SCALE = 1.25;
	/** Preset preview icons render at a fixed 130%. */
	private static final int FIXED_PRESET_ICON_SCALE = 130;

	/** Pointing hand on the buttons, the move/cross cursor on the drag handle. */
	private static final Cursor POINT = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
	private static final Cursor MOVE = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);

	private final EmoteWheelConfig config;
	private final ConfigManager configManager;
	/** Supplies each emote's in-game icon (native size), or null until it has loaded. */
	private final Function<Emote, BufferedImage> iconProvider;

	/** Current height of every slot row, grown to fit the largest scaled icon. */
	private int rowHeight = SLOT_H;
	/** Reserved width of the icon column, so the label doesn't jump as Random cycles icons
	 *  of different widths. */
	private int iconColW = 24;

	/** "Favorites - <loaded preset name>", so you can see which ring is active. */
	private final JLabel favoritesHeader = new JLabel("Favorites");

	private final SlotRow[] slotRows = new SlotRow[SLOTS];
	private final JPanel stack = new JPanel();
	private final JPanel optionList = new JPanel();
	private final FadePanel picker = new FadePanel();
	/** Type-to-search box at the top of the picker; filters the emote list live. */
	private final JTextField pickerSearch = new JTextField();

	/** The Random slot cycles through emote icons in the panel; these drive that. */
	private final List<Emote> randomCycle = new ArrayList<>();
	private final Timer randomTimer;
	private int randomTick;

	/** Slot (1..6) whose picker is open, or -1 if none. */
	private int editing = -1;

	/** Eases every slot row's dim toward its target each frame. */
	private final Timer dimTimer;

	/** Drives the inline picker's slide-open / slide-closed reveal (height + fade). */
	private final Timer revealTimer;
	private float reveal;
	private float revealTarget;

	/** Row a slot is about to be dropped before while dragging, or -1. */
	private int dragTarget = -1;

	// --- Presets ---
	private final JPanel presetsBox = new JPanel();
	private final FadePanel presetReveal = new FadePanel();
	private final Timer presetRevealTimer;
	private float pReveal;
	private float pRevealTarget;
	/** Target open height of the preset reveal, sized to its current content. */
	private float pRevealH = 84;
	private final List<Preset> presets = new ArrayList<>();
	/** Index of the preset whose Apply/Delete confirm is open, or -1 if none. */
	private int editingPreset = -1;
	/** True while the create-preset name field is open. */
	private boolean creating;

	EmoteWheelPanel(EmoteWheelConfig config, ConfigManager configManager,
			Function<Emote, BufferedImage> iconProvider)
	{
		this.config = config;
		this.configManager = configManager;
		this.iconProvider = iconProvider;

		// Build timers against locals so the lambdas don't touch the blank-final fields
		// before they're assigned (which the compiler rejects).
		final Timer dim = new Timer(16, null);
		dim.addActionListener(e ->
		{
			boolean moving = false;
			for (SlotRow r : slotRows)
			{
				if (r.stepAnim())
				{
					moving = true;
				}
			}
			if (!moving)
			{
				dim.stop();
			}
		});
		this.dimTimer = dim;

		final Timer rv = new Timer(16, null);
		rv.addActionListener(e ->
		{
			float step = 0.16f;
			reveal = reveal < revealTarget
					? Math.min(revealTarget, reveal + step)
					: Math.max(revealTarget, reveal - step);
			int h = Math.round(reveal * PICKER_H);
			picker.setAlpha(reveal);
			picker.setPreferredSize(new Dimension(0, h));
			picker.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
			stack.revalidate();
			stack.repaint();
			if (Math.abs(reveal - revealTarget) < 0.001f)
			{
				reveal = revealTarget;
				rv.stop();
				if (revealTarget == 0f)
				{
					stack.remove(picker);
					stack.revalidate();
					stack.repaint();
				}
			}
		});
		this.revealTimer = rv;

		final Timer prv = new Timer(16, null);
		prv.addActionListener(e ->
		{
			float step = 0.16f;
			pReveal = pReveal < pRevealTarget
					? Math.min(pRevealTarget, pReveal + step)
					: Math.max(pRevealTarget, pReveal - step);
			int h = Math.round(pReveal * pRevealH);
			presetReveal.setAlpha(pReveal);
			presetReveal.setPreferredSize(new Dimension(0, h));
			presetReveal.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
			presetsBox.revalidate();
			presetsBox.repaint();
			if (Math.abs(pReveal - pRevealTarget) < 0.001f)
			{
				pReveal = pRevealTarget;
				prv.stop();
				if (pRevealTarget == 0f)
				{
					presetsBox.remove(presetReveal);
					presetsBox.revalidate();
					presetsBox.repaint();
				}
			}
		});
		this.presetRevealTimer = prv;

		JLabel title = new JLabel("Emote Wheel");
		title.setFont(TITLE_FONT);
		title.setForeground(Color.WHITE);
		add(title);

		add(Box.createVerticalStrut(8));

		favoritesHeader.setFont(HEADER_FONT);
		favoritesHeader.setForeground(ColorScheme.BRAND_ORANGE);
		add(favoritesHeader);

		// One vertical stack of slot rows; the picker is inserted between rows on demand,
		// so opening it pushes the rows below down (BoxLayout honours the animated height).
		stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
		stack.setOpaque(false);
		for (int i = 0; i < SLOTS; i++)
		{
			SlotRow row = new SlotRow(i + 1);
			slotRows[i] = row;
			stack.add(row);
			stack.add(Box.createVerticalStrut(STRUT));
		}
		add(stack);

		add(Box.createVerticalStrut(8));

		JLabel presetsHeader = new JLabel("Presets");
		presetsHeader.setFont(HEADER_FONT);
		presetsHeader.setForeground(ColorScheme.BRAND_ORANGE);
		add(presetsHeader);

		// Saved rings of six favourites. Same stack trick as the slots: a preset row can
		// slide open a confirm below it, and the Create button slides open a name field.
		presetsBox.setLayout(new BoxLayout(presetsBox, BoxLayout.Y_AXIS));
		presetsBox.setOpaque(false);
		presetReveal.setLayout(new BorderLayout());
		add(presetsBox);

		loadPresets();
		rebuildPresets();
		updateFavoritesHeader();

		optionList.setLayout(new BoxLayout(optionList, BoxLayout.Y_AXIS));
		optionList.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JScrollPane scroll = new JScrollPane(optionList,
				ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		pickerSearch.setFont(FONT);
		pickerSearch.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		pickerSearch.setForeground(Color.WHITE);
		pickerSearch.setCaretColor(Color.WHITE);
		pickerSearch.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
				BorderFactory.createEmptyBorder(2, 6, 2, 6)));
		pickerSearch.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				buildOptions();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				buildOptions();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				buildOptions();
			}
		});
		// Enter jumps straight to the first match.
		pickerSearch.addActionListener(e -> chooseFirstMatch());
		picker.setLayout(new BorderLayout());
		picker.add(pickerSearch, BorderLayout.NORTH);
		picker.add(scroll, BorderLayout.CENTER);

		// The Random slot animates through the real emotes' icons in the panel.
		rebuildRandomCycle();
		randomTimer = new Timer(120, e ->
		{
			randomTick++;
			for (SlotRow r : slotRows)
			{
				if (slotValue(r.index) == Emote.RANDOM)
				{
					r.repaint();
				}
			}
			// Also animate a Random icon inside an open preset preview.
			if (presetReveal.getParent() != null)
			{
				presetReveal.repaint();
			}
		});

		recomputeRowHeight();
		updateRandomTimer();
	}

	// ------------------------------------------------------------ public API

	/** Re-reads the slots from config and repaints. Safe from any thread. */
	void refresh()
	{
		SwingUtilities.invokeLater(() ->
		{
			if (editing >= 1)
			{
				buildOptions();
			}
			rebuildRandomCycle();
			recomputeRowHeight();
			updateFavoritesHeader();
			refreshRevealContent();
			updateRandomTimer();
			stack.repaint();
			optionList.repaint();
		});
	}

	/** Runs the Random-icon animation while any slot, or an open preset preview, holds
	 *  Random; stops it otherwise. */
	private void updateRandomTimer()
	{
		boolean any = false;
		for (int i = 1; i <= SLOTS; i++)
		{
			if (slotValue(i) == Emote.RANDOM)
			{
				any = true;
			}
		}
		if (!any && editingPreset >= 0 && editingPreset < presets.size())
		{
			for (Emote e : presets.get(editingPreset).slots)
			{
				if (e == Emote.RANDOM)
				{
					any = true;
				}
			}
		}
		if (any && !randomTimer.isRunning())
		{
			randomTimer.start();
		}
		else if (!any && randomTimer.isRunning())
		{
			randomTimer.stop();
		}
	}

	/** Fills the Random preview's cycle with the real emotes, minus the exclude list, so
	 *  the sidebar animation matches what the wheel actually rolls. */
	private void rebuildRandomCycle()
	{
		List<String> excl = new ArrayList<>();
		for (String tok : config.randomExclude().split(","))
		{
			String n = tok.trim().toLowerCase();
			if (!n.isEmpty())
			{
				excl.add(n);
			}
		}
		randomCycle.clear();
		for (Emote e : Emote.values())
		{
			if (e == Emote.NONE || e == Emote.RANDOM)
			{
				continue;
			}
			if (!excl.contains(e.getDisplayName().toLowerCase()))
			{
				randomCycle.add(e);
			}
		}
	}

	/** The emote icon the Random slot should show this frame, or null while none is loaded. */
	private BufferedImage randomIcon()
	{
		int n = randomCycle.size();
		for (int k = 0; k < n; k++)
		{
			BufferedImage img = iconProvider.apply(randomCycle.get((randomTick + k) % n));
			if (img != null)
			{
				return img;
			}
		}
		return null;
	}

	/** Called when an emote icon finishes loading; recompute heights and repaint. */
	void iconsUpdated()
	{
		SwingUtilities.invokeLater(() ->
		{
			recomputeRowHeight();
			stack.repaint();
		});
	}

	/** The fixed scale factor applied to the native emote icons. */
	private double iconScale()
	{
		return FIXED_ICON_SCALE;
	}

	/** Grows every slot row to fit the tallest scaled icon among the six favourites. */
	private void recomputeRowHeight()
	{
		double factor = iconScale();
		int maxIconH = 0;
		int maxIconW = 0;
		boolean anyRandom = false;
		for (int i = 1; i <= SLOTS; i++)
		{
			Emote se = slotValue(i);
			if (se == Emote.RANDOM)
			{
				anyRandom = true;
			}
			BufferedImage img = se == Emote.RANDOM ? randomIcon() : iconProvider.apply(se);
			if (img != null)
			{
				maxIconH = Math.max(maxIconH, (int) Math.round(img.getHeight() * factor));
				maxIconW = Math.max(maxIconW, (int) Math.round(img.getWidth() * factor));
			}
		}
		// A Random slot can show any emote, so size the row and icon column to the widest/
		// tallest in the cycle - keeps the layout still while it animates.
		if (anyRandom)
		{
			for (Emote e : randomCycle)
			{
				BufferedImage img = iconProvider.apply(e);
				if (img != null)
				{
					maxIconH = Math.max(maxIconH, (int) Math.round(img.getHeight() * factor));
					maxIconW = Math.max(maxIconW, (int) Math.round(img.getWidth() * factor));
				}
			}
		}
		iconColW = Math.max(24, maxIconW);
		int target = Math.min(ROW_MAX_H, Math.max(SLOT_H, maxIconH + 2 * ICON_PAD_V));
		if (target != rowHeight)
		{
			rowHeight = target;
			for (SlotRow r : slotRows)
			{
				r.setPreferredSize(new Dimension(0, rowHeight));
				r.setMaximumSize(new Dimension(Integer.MAX_VALUE, rowHeight));
			}
			stack.revalidate();
		}
		stack.repaint();
	}

	// -------------------------------------------------------------- picker

	private void openPicker(int slot)
	{
		if (editing == slot)
		{
			closePicker();
			return;
		}
		boolean wasOpen = editing >= 1;
		editing = slot;
		for (SlotRow r : slotRows)
		{
			r.setSelected(r.index == slot);
			r.setDimTarget(r.index == slot ? 1f : DIM);
		}
		startAnim();
		pickerSearch.setText("");
		buildOptions();

		// Slot rows sit at even indices (row, strut, row, strut ...); drop the picker in
		// right after the chosen row so the rows below slide down to make room.
		stack.remove(picker);
		int idx = Math.min((slot - 1) * 2 + 1, stack.getComponentCount());
		stack.add(picker, idx);
		picker.setVisible(true);
		if (!wasOpen)
		{
			reveal = 0f;
			picker.setAlpha(0f);
			picker.setPreferredSize(new Dimension(0, 0));
			picker.setMaximumSize(new Dimension(Integer.MAX_VALUE, 0));
		}
		revealTarget = 1f;
		startReveal();
		stack.revalidate();
		stack.repaint();
		// Focus the search box so you can just start typing to filter.
		SwingUtilities.invokeLater(pickerSearch::requestFocusInWindow);
	}

	private void closePicker()
	{
		editing = -1;
		for (SlotRow r : slotRows)
		{
			r.setSelected(false);
			r.setDimTarget(1f);
		}
		startAnim();
		revealTarget = 0f;
		startReveal();
	}

	/** Immediate close with no slide, used when a drag begins so drop maths stays simple. */
	private void closeInstant()
	{
		editing = -1;
		for (SlotRow r : slotRows)
		{
			r.setSelected(false);
			r.setDimTarget(1f);
		}
		startAnim();
		revealTimer.stop();
		reveal = 0f;
		revealTarget = 0f;
		stack.remove(picker);
		stack.revalidate();
		stack.repaint();
	}

	private void buildOptions()
	{
		optionList.removeAll();
		if (editing < 1)
		{
			optionList.revalidate();
			optionList.repaint();
			return;
		}

		Emote current = slotValue(editing);
		String q = pickerSearch.getText().trim().toLowerCase();
		boolean searching = !q.isEmpty();

		// Current emote pinned first, then None and Random, then the rest - matching the
		// wheel's own pinning. While searching, nothing is pinned; matches show in order.
		if (!searching)
		{
			optionList.add(new OptionRow(current, false, true));
		}

		List<Emote> reals = new ArrayList<>();
		boolean showNone = false;
		boolean showRandom = false;
		for (Emote e : Emote.values())
		{
			if (!searching && e == current)
			{
				continue;
			}
			if (searching && !e.getDisplayName().toLowerCase().contains(q))
			{
				continue;
			}
			if (e == Emote.NONE)
			{
				showNone = true;
			}
			else if (e == Emote.RANDOM)
			{
				showRandom = true;
			}
			else
			{
				reals.add(e);
			}
		}
		if (config.alphabetical())
		{
			reals.sort(Comparator.comparing(Emote::getDisplayName, String.CASE_INSENSITIVE_ORDER));
		}

		if (showNone)
		{
			optionList.add(new OptionRow(Emote.NONE, false, searching && current == Emote.NONE));
		}
		if (showRandom)
		{
			optionList.add(new OptionRow(Emote.RANDOM, false, searching && current == Emote.RANDOM));
		}
		for (Emote e : reals)
		{
			optionList.add(new OptionRow(e, isTaken(editing, e), searching && e == current));
		}
		optionList.revalidate();
		optionList.repaint();
	}

	/** Picks the first matching, selectable emote - used when Enter is pressed in search. */
	private void chooseFirstMatch()
	{
		if (editing < 1)
		{
			return;
		}
		String q = pickerSearch.getText().trim().toLowerCase();
		if (q.isEmpty())
		{
			return;
		}
		List<Emote> reals = new ArrayList<>();
		for (Emote e : Emote.values())
		{
			if (e == Emote.NONE || e == Emote.RANDOM)
			{
				continue;
			}
			if (!e.getDisplayName().toLowerCase().contains(q) || isTaken(editing, e))
			{
				continue;
			}
			reals.add(e);
		}
		if (config.alphabetical())
		{
			reals.sort(Comparator.comparing(Emote::getDisplayName, String.CASE_INSENSITIVE_ORDER));
		}
		if (!reals.isEmpty())
		{
			choose(reals.get(0));
		}
	}

	private void choose(Emote e)
	{
		configManager.setConfiguration(EmoteWheelConfig.GROUP, "slot" + editing, e);
		closePicker();
	}

	// ------------------------------------------------------------- reorder

	private void reorder(int from, int to)
	{
		if (from == to || from < 1 || to < 1 || from > SLOTS || to > SLOTS)
		{
			return;
		}
		List<Emote> vals = new ArrayList<>();
		for (int i = 1; i <= SLOTS; i++)
		{
			vals.add(slotValue(i));
		}
		Emote moved = vals.remove(from - 1);
		vals.add(to - 1, moved);
		for (int i = 0; i < SLOTS; i++)
		{
			configManager.setConfiguration(EmoteWheelConfig.GROUP, "slot" + (i + 1), vals.get(i));
		}
		stack.repaint();
	}

	private void updateDragTarget(int target)
	{
		dragTarget = target;
		for (SlotRow r : slotRows)
		{
			r.repaint();
		}
	}

	/** Pops the slot an emote was just dropped into, as a little landing bounce. */
	private void popRow(int slot)
	{
		if (slot >= 1 && slot <= SLOTS)
		{
			slotRows[slot - 1].pop();
		}
	}

	/** True while the wheel's Drag Mode allows rearranging. When None, the sidebar rows
	 *  can't be dragged and their grab handles are hidden, matching the wheel. */
	private boolean dragEnabled()
	{
		return config.dragMode() != DragMode.NONE;
	}

	/** Applies a sidebar drag with the same rule as the wheel: Drag and Swap trades the two
	 *  slots, Drag and Slot slides the dragged row into place and shifts the rest. */
	private void applyDrag(int from, int to)
	{
		if (config.dragMode() == DragMode.SWAP)
		{
			swapSlots(from, to);
		}
		else
		{
			reorder(from, to);
		}
	}

	private void swapSlots(int from, int to)
	{
		if (from == to || from < 1 || to < 1 || from > SLOTS || to > SLOTS)
		{
			return;
		}
		Emote a = slotValue(from);
		Emote b = slotValue(to);
		configManager.setConfiguration(EmoteWheelConfig.GROUP, "slot" + from, b);
		configManager.setConfiguration(EmoteWheelConfig.GROUP, "slot" + to, a);
		stack.repaint();
	}

	// -------------------------------------------------------------- helpers

	private boolean isTaken(int slot, Emote emote)
	{
		for (int i = 1; i <= SLOTS; i++)
		{
			if (i != slot && slotValue(i) == emote)
			{
				return true;
			}
		}
		return false;
	}

	private void startAnim()
	{
		if (!dimTimer.isRunning())
		{
			dimTimer.start();
		}
	}

	private void startReveal()
	{
		if (!revealTimer.isRunning())
		{
			revealTimer.start();
		}
	}

	/** Splits a multi-word emote name into two balanced lines; a single word stays one line. */
	private static String[] wrapName(String name)
	{
		if (name.indexOf(' ') < 0)
		{
			return new String[]{ name };
		}
		// Break at the space nearest the middle so the two lines are roughly even.
		int mid = name.length() / 2;
		int best = -1;
		int bestDist = Integer.MAX_VALUE;
		for (int i = 0; i < name.length(); i++)
		{
			if (name.charAt(i) == ' ')
			{
				int d = Math.abs(i - mid);
				if (d < bestDist)
				{
					bestDist = d;
					best = i;
				}
			}
		}
		return new String[]{ name.substring(0, best), name.substring(best + 1) };
	}

	private Emote slotValue(int slot)
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

	// -------------------------------------------------------------- presets

	/** The always-present, non-deletable Default ring: the plugin's six starting emotes. */
	private static final String DEFAULT_PRESET_NAME = "Default";
	private static final Emote[] DEFAULT_PRESET =
	{
		Emote.YES, Emote.NO, Emote.BOW, Emote.ANGRY, Emote.THINK, Emote.WAVE
	};

	/** A saved ring: a name plus the six emotes to drop into the slots. The built-in
	 *  Default ring can be applied but not deleted, and is never written to config. */
	private static class Preset
	{
		private final String name;
		private final Emote[] slots;
		private final boolean builtIn;

		Preset(String name, Emote[] slots, boolean builtIn)
		{
			this.name = name;
			this.slots = slots;
			this.builtIn = builtIn;
		}
	}

	/** Parses the hidden "presets" config string (one "name\tE1,...,E6" per line). The
	 *  built-in Default ring is always first and is not part of the config string. */
	private void loadPresets()
	{
		presets.clear();
		presets.add(new Preset(DEFAULT_PRESET_NAME, DEFAULT_PRESET.clone(), true));
		String raw = config.presets();
		if (raw == null || raw.isEmpty())
		{
			return;
		}
		for (String line : raw.split("\n"))
		{
			if (line.trim().isEmpty())
			{
				continue;
			}
			int tab = line.indexOf('\t');
			if (tab < 0)
			{
				continue;
			}
			String name = line.substring(0, tab);
			String[] parts = line.substring(tab + 1).split(",");
			Emote[] slots = new Emote[SLOTS];
			for (int i = 0; i < SLOTS; i++)
			{
				Emote e = Emote.NONE;
				if (i < parts.length)
				{
					try
					{
						e = Emote.valueOf(parts[i].trim());
					}
					catch (IllegalArgumentException ex)
					{
						e = Emote.NONE;
					}
				}
				slots[i] = e;
			}
			presets.add(new Preset(name, slots, false));
		}
	}

	/** Serialises the user presets back to the hidden config string, skipping the built-in. */
	private void savePresets()
	{
		StringBuilder sb = new StringBuilder();
		for (Preset p : presets)
		{
			if (p.builtIn)
			{
				continue;
			}
			if (sb.length() > 0)
			{
				sb.append('\n');
			}
			sb.append(p.name).append('\t');
			for (int i = 0; i < SLOTS; i++)
			{
				if (i > 0)
				{
					sb.append(',');
				}
				sb.append(p.slots[i].name());
			}
		}
		configManager.setConfiguration(EmoteWheelConfig.GROUP, "presets", sb.toString());
	}

	/** Rebuilds the preset rows + Create button from the current list, closing any reveal. */
	private void rebuildPresets()
	{
		presetRevealTimer.stop();
		pReveal = 0f;
		pRevealTarget = 0f;
		editingPreset = -1;
		creating = false;

		presetsBox.removeAll();
		for (int i = 0; i < presets.size(); i++)
		{
			presetsBox.add(new PresetRow(i));
			presetsBox.add(Box.createVerticalStrut(STRUT));
		}
		presetsBox.add(new CreateRow());
		presetsBox.revalidate();
		presetsBox.repaint();
	}

	private void startPresetReveal()
	{
		if (!presetRevealTimer.isRunning())
		{
			presetRevealTimer.start();
		}
	}

	/** Drops {@code content} into the reveal panel and slides it open at {@code insertIndex}
	 *  (canonical index, i.e. as if no reveal were currently inserted). */
	private void showReveal(int insertIndex, JComponent content)
	{
		presetsBox.remove(presetReveal);
		setRevealContent(content);
		presetsBox.add(presetReveal, Math.min(insertIndex, presetsBox.getComponentCount()));
		pReveal = 0f;
		presetReveal.setAlpha(0f);
		presetReveal.setPreferredSize(new Dimension(0, 0));
		presetReveal.setMaximumSize(new Dimension(Integer.MAX_VALUE, 0));
		pRevealTarget = 1f;
		startPresetReveal();
		presetsBox.revalidate();
		presetsBox.repaint();
	}

	/** Puts {@code content} in the reveal and sizes the open height to fit it. */
	private void setRevealContent(JComponent content)
	{
		presetReveal.removeAll();
		presetReveal.add(content, BorderLayout.CENTER);
		pRevealH = Math.max(1, content.getPreferredSize().height);
	}

	/** Rebuilds the open reveal's content in place (e.g. after the preset icon size
	 *  changes), resizing it without a fresh slide. */
	private void refreshRevealContent()
	{
		if (presetReveal.getParent() == null)
		{
			return;
		}
		if (editingPreset >= 0 && editingPreset < presets.size())
		{
			setRevealContent(buildConfirm(editingPreset));
		}
		else if (creating)
		{
			setRevealContent(buildNameForm());
		}
		pRevealTarget = 1f;
		startPresetReveal();
	}

	private void closePresetPanel()
	{
		editingPreset = -1;
		creating = false;
		pRevealTarget = 0f;
		startPresetReveal();
		syncHolds();
	}

	/** Re-targets every preset button's grow after the held state changes. */
	private void syncHolds()
	{
		for (Component c : presetsBox.getComponents())
		{
			if (c instanceof HoverButton)
			{
				((HoverButton) c).refreshHold();
			}
		}
		// A preset preview opening/closing can start or stop the Random animation.
		updateRandomTimer();
	}

	/** Toggles the Apply/Delete confirm under preset {@code index}. */
	private void togglePresetConfirm(int index)
	{
		boolean wasOpen = editingPreset == index;
		editingPreset = -1;
		creating = false;
		if (wasOpen)
		{
			pRevealTarget = 0f;
			startPresetReveal();
		}
		else
		{
			editingPreset = index;
			showReveal(index * 2 + 1, buildConfirm(index));
		}
		syncHolds();
	}

	/** Toggles the create-preset name field under the Create button. */
	private void openCreate()
	{
		boolean wasOpen = creating;
		editingPreset = -1;
		creating = false;
		if (wasOpen)
		{
			pRevealTarget = 0f;
			startPresetReveal();
		}
		else
		{
			creating = true;
			showReveal(presets.size() * 2 + 1, buildNameForm());
		}
		syncHolds();
	}

	private void applyPreset(int index)
	{
		if (index < 0 || index >= presets.size())
		{
			return;
		}
		Preset p = presets.get(index);
		for (int i = 0; i < SLOTS; i++)
		{
			configManager.setConfiguration(EmoteWheelConfig.GROUP, "slot" + (i + 1), p.slots[i]);
		}
		closePresetPanel();
	}

	/** Name of the preset whose six emotes match the current favourites, or "Custom". */
	private String currentPresetName()
	{
		Emote[] cur = new Emote[SLOTS];
		for (int i = 0; i < SLOTS; i++)
		{
			cur[i] = slotValue(i + 1);
		}
		for (Preset p : presets)
		{
			if (Arrays.equals(p.slots, cur))
			{
				return p.name;
			}
		}
		return "Custom";
	}

	private void updateFavoritesHeader()
	{
		favoritesHeader.setText("Favorites - " + currentPresetName());
	}

	private void deletePreset(int index)
	{
		if (index < 0 || index >= presets.size() || presets.get(index).builtIn)
		{
			return;
		}
		presets.remove(index);
		savePresets();
		rebuildPresets();
	}

	private void createPreset(String rawName)
	{
		// Name is stored before a tab and emotes are comma-joined, so strip both plus
		// newlines; fall back to a default if the field was left blank.
		String name = rawName == null ? "" : rawName.replace("\t", " ").replace("\n", " ").trim();
		if (name.isEmpty())
		{
			name = "Preset " + (presets.size() + 1);
		}
		Emote[] slots = new Emote[SLOTS];
		for (int i = 0; i < SLOTS; i++)
		{
			slots[i] = slotValue(i + 1);
		}
		presets.add(new Preset(name, slots, false));
		savePresets();
		rebuildPresets();
	}

	private JComponent buildConfirm(int index)
	{
		Preset preset = presets.get(index);

		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setOpaque(false);
		p.setBorder(BorderFactory.createEmptyBorder(6, 2, 2, 2));

		// The six emotes of this preset shown as a row of icons - no names needed.
		IconStrip strip = new IconStrip(preset.slots);
		strip.setAlignmentX(LEFT_ALIGNMENT);
		p.add(strip);
		p.add(Box.createVerticalStrut(6));

		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setOpaque(false);
		row.setAlignmentX(LEFT_ALIGNMENT);
		TextButton apply = new TextButton("Apply", ColorScheme.BRAND_ORANGE);
		apply.onClick(() -> applyPreset(index));
		row.add(apply);
		// The built-in Default ring can be applied but not deleted.
		if (!preset.builtIn)
		{
			TextButton delete = new TextButton("Delete", ColorScheme.PROGRESS_ERROR_COLOR);
			delete.onClick(() -> deletePreset(index));
			row.add(Box.createHorizontalStrut(6));
			row.add(delete);
		}
		p.add(row);
		return p;
	}

	private JComponent buildNameForm()
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setOpaque(false);
		p.setBorder(BorderFactory.createEmptyBorder(6, 2, 2, 2));

		JTextField field = new JTextField();
		field.setFont(FONT);
		field.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		field.setForeground(Color.WHITE);
		field.setCaretColor(Color.WHITE);
		field.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		field.setAlignmentX(LEFT_ALIGNMENT);
		field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		field.addActionListener(e -> createPreset(field.getText()));
		p.add(field);
		p.add(Box.createVerticalStrut(6));

		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setOpaque(false);
		row.setAlignmentX(LEFT_ALIGNMENT);
		TextButton save = new TextButton("Save", ColorScheme.BRAND_ORANGE);
		save.onClick(() -> createPreset(field.getText()));
		TextButton cancel = new TextButton("Cancel", ColorScheme.LIGHT_GRAY_COLOR);
		cancel.onClick(this::closePresetPanel);
		row.add(save);
		row.add(Box.createHorizontalStrut(6));
		row.add(cancel);
		p.add(row);

		SwingUtilities.invokeLater(field::requestFocusInWindow);
		return p;
	}

	// ----------------------------------------------------------- components

	/** One of the six slot buttons: slot number + emote name, with hover highlight,
	 *  click-to-edit and drag-to-reorder (lifted row + an insertion line). Dims toward
	 *  {@link #DIM} while another slot is being edited. */
	private class SlotRow extends JPanel
	{
		private final int index;
		private boolean selected;
		private boolean hover;
		private float dim = 1f;
		private float dimTarget = 1f;
		private float scale = 1f;
		private float scaleTarget = 1f;

		/** The whole row is the drag handle: hold to arm, then move to reorder. The grab
		 *  bars fade in once armed as feedback, and are hidden the rest of the time. */
		private final Timer holdTimer;
		private boolean pressed;
		private boolean dragArmed;
		private boolean dragging;
		private float handleAlpha;
		private float handleAlphaTarget;

		SlotRow(int index)
		{
			this.index = index;
			setOpaque(false);
			setCursor(POINT);
			setPreferredSize(new Dimension(0, rowHeight));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, rowHeight));

			// Fires once, HOLD_MS after a press: if still held, arm the drag and fade the
			// grab bars in. A quick tap releases before this and just opens the picker.
			holdTimer = new Timer(HOLD_MS, ae ->
			{
				if (pressed && !dragging && dragEnabled())
				{
					dragArmed = true;
					handleAlphaTarget = 1f;
					setCursor(MOVE);
					closeInstant();
					startAnim();
					repaint();
				}
			});
			holdTimer.setRepeats(false);

			MouseAdapter ma = new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					pressed = true;
					dragging = false;
					dragArmed = false;
					// Snap down for the press "pop", then spring back to the hover size.
					scale = SLOT_PRESS_SCALE;
					scaleTarget = SLOT_HOVER_SCALE;
					startAnim();
					if (dragEnabled())
					{
						holdTimer.restart();
					}
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					hover = true;
					scaleTarget = SLOT_HOVER_SCALE;
					startAnim();
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					hover = false;
					if (!pressed)
					{
						scaleTarget = 1f;
						setCursor(POINT);
					}
					startAnim();
					repaint();
				}

				@Override
				public void mouseDragged(MouseEvent e)
				{
					// Only starts moving once the hold has armed the drag.
					if (dragArmed)
					{
						dragging = true;
						Point inStack = SwingUtilities.convertPoint(SlotRow.this, e.getPoint(), stack);
						int pitch = rowHeight + STRUT;
						int t = 1 + Math.max(0, Math.min(SLOTS - 1, inStack.y / pitch));
						updateDragTarget(t);
					}
				}

				@Override
				public void mouseReleased(MouseEvent e)
				{
					pressed = false;
					holdTimer.stop();
					if (dragging)
					{
						int drop = dragTarget < 1 ? index : dragTarget;
						applyDrag(index, drop);
						updateDragTarget(-1);
						popRow(drop);
					}
					else if (!dragArmed)
					{
						// A quick tap (never armed) edits the slot; a hold that didn't move
						// just cancels.
						openPicker(index);
					}
					dragging = false;
					dragArmed = false;
					handleAlphaTarget = 0f;
					scaleTarget = hover ? SLOT_HOVER_SCALE : 1f;
					setCursor(POINT);
					startAnim();
				}
			};
			addMouseListener(ma);
			addMouseMotionListener(ma);
		}

		void setSelected(boolean s)
		{
			selected = s;
			repaint();
		}

		/** Snap up to the overshoot then ease back down - the drop bounce. */
		void pop()
		{
			scale = SLOT_DROP_POP;
			scaleTarget = hover ? SLOT_HOVER_SCALE : 1f;
			startAnim();
		}

		void setDimTarget(float t)
		{
			dimTarget = t;
		}

		boolean stepAnim()
		{
			boolean moving = false;
			if (Math.abs(dim - dimTarget) >= 0.01f)
			{
				dim += (dimTarget - dim) * 0.25f;
				moving = true;
			}
			else
			{
				dim = dimTarget;
			}
			if (Math.abs(scale - scaleTarget) >= 0.002f)
			{
				scale += (scaleTarget - scale) * 0.30f;
				moving = true;
			}
			else
			{
				scale = scaleTarget;
			}
			if (Math.abs(handleAlpha - handleAlphaTarget) >= 0.02f)
			{
				handleAlpha += (handleAlphaTarget - handleAlpha) * 0.30f;
				moving = true;
			}
			else
			{
				handleAlpha = handleAlphaTarget;
			}
			if (moving)
			{
				repaint();
			}
			return moving;
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, dim))));
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

			int w = getWidth();
			int h = getHeight();
			// Grow/dip the whole row about its centre for the hover-grow and press-pop.
			if (scale != 1f)
			{
				g2.translate(w / 2.0, h / 2.0);
				g2.scale(scale, scale);
				g2.translate(-w / 2.0, -h / 2.0);
			}
			boolean drag = dragging;
			Color bg = selected ? ColorScheme.DARKER_GRAY_HOVER_COLOR
					: (hover || drag ? ColorScheme.DARKER_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR);
			g2.setColor(bg);
			g2.fillRoundRect(0, 0, w - 1, h - 1, 6, 6);

			if (selected)
			{
				g2.setColor(ColorScheme.BRAND_ORANGE);
				g2.fillRect(0, 3, 3, h - 6);
			}
			if (drag)
			{
				g2.setColor(ColorScheme.BRAND_ORANGE);
				g2.setStroke(new BasicStroke(2f));
				g2.drawRoundRect(1, 1, w - 3, h - 3, 6, 6);
			}
			// Insertion line where a dragged row would drop.
			if (dragTarget == index && !drag)
			{
				g2.setColor(ColorScheme.BRAND_ORANGE);
				g2.fillRect(0, 0, w, 2);
			}

			int inset = FIXED_INSET;
			boolean iconLeft = true;

			// The emote icon: on the right by default, on the left when the toggle is set.
			// The Random slot cycles through the emotes' icons like the in-game slot machine.
			// It is centred in a fixed-width column so the label doesn't jump as Random
			// cycles icons of different widths.
			Emote shown = slotValue(index);
			BufferedImage icon = shown == Emote.RANDOM ? randomIcon() : iconProvider.apply(shown);
			int col = iconColW;
			if (icon != null)
			{
				g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
						RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				double f = iconScale();
				int iw = (int) Math.round(icon.getWidth() * f);
				int ih = (int) Math.round(icon.getHeight() * f);
				col = Math.max(col, iw);
				int colLeft = iconLeft ? (8 + inset) : (w - 8 - inset - col);
				int ix = colLeft + (col - iw) / 2;
				g2.drawImage(icon, ix, (h - ih) / 2, iw, ih, null);
			}

			// Leading edge of the text: the base pad, or just past the fixed icon column plus
			// the configurable gap when the icon sits on the left. The grab bars go just
			// before the text, which slides right by BAR_ROOM as they fade in.
			int iconGap = FIXED_ICON_GAP;
			int textBase = iconLeft ? (8 + inset + col + iconGap) : (TEXT_LEFT_PAD + inset);

			if (handleAlpha > 0.02f)
			{
				int a = (int) Math.round(Math.min(1f, handleAlpha) * 255);
				g2.setColor(new Color(255, 255, 255, a));
				g2.setStroke(new BasicStroke(2.4f));
				int bx = textBase - 4;
				int hcy = h / 2;
				g2.drawLine(bx, hcy - 5, bx + 12, hcy - 5);
				g2.drawLine(bx, hcy, bx + 12, hcy);
				g2.drawLine(bx, hcy + 5, bx + 12, hcy + 5);
			}

			int sx = textBase + Math.round(handleAlpha * BAR_ROOM);

			// "Slot N" on top, then the emote name below it - wrapped to two lines when the
			// name has a space, so long names like "Hypermobile Drinker" fit the wider rows.
			Font small = FontManager.getRunescapeSmallFont();
			g2.setFont(small);
			FontMetrics sfm = g2.getFontMetrics();
			int slotLabelH = sfm.getAscent() + sfm.getDescent();

			g2.setFont(FONT);
			FontMetrics fm = g2.getFontMetrics();
			int nameLineH = fm.getAscent() + fm.getDescent();

			String[] lines = wrapName(slotValue(index).getDisplayName());
			int blockH = slotLabelH + 2 + lines.length * nameLineH;
			int top = Math.max(2, (h - blockH) / 2);

			g2.setFont(small);
			g2.setColor(ColorScheme.LIGHT_GRAY_COLOR);
			g2.drawString("Slot " + index, sx, top + sfm.getAscent());

			g2.setFont(FONT);
			g2.setColor(Color.WHITE);
			int by = top + slotLabelH + 2 + fm.getAscent();
			for (String line : lines)
			{
				g2.drawString(line, sx, by);
				by += nameLineH;
			}

			g2.dispose();
		}
	}

	/** One selectable emote in the picker, with hover highlight. Disabled (dimmed,
	 *  unclickable) when the emote already sits in another slot, which blocks duplicates. */
	private class OptionRow extends JPanel
	{
		private final Emote emote;
		private final boolean disabled;
		private final boolean current;
		private boolean hover;

		OptionRow(Emote emote, boolean disabled, boolean current)
		{
			this.emote = emote;
			this.disabled = disabled;
			this.current = current;
			setOpaque(false);
			setAlignmentX(LEFT_ALIGNMENT);
			setPreferredSize(new Dimension(0, OPTION_H));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, OPTION_H));
			if (!disabled)
			{
				setCursor(POINT);
			}
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseEntered(MouseEvent e)
				{
					hover = true;
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					hover = false;
					repaint();
				}

				@Override
				public void mouseReleased(MouseEvent e)
				{
					if (!disabled)
					{
						choose(emote);
					}
				}
			});
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, disabled ? 0.35f : 1f));
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

			Color bg = current ? ColorScheme.BRAND_ORANGE.darker()
					: (hover ? ColorScheme.DARKER_GRAY_HOVER_COLOR : ColorScheme.DARK_GRAY_COLOR);
			g2.setColor(bg);
			g2.fillRect(0, 0, getWidth(), getHeight());

			g2.setFont(FONT);
			FontMetrics fm = g2.getFontMetrics();
			int midY = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
			g2.setColor(Color.WHITE);
			g2.drawString(emote.getDisplayName(), 10, midY);

			g2.dispose();
		}
	}

	/** A button with the slot rows' feel: hover highlight + grow, press-pop. Subclasses
	 *  paint their own label. Backs the preset rows, the Create button and the small
	 *  Apply/Delete/Save/Cancel buttons in the confirm and name forms. */
	private abstract class HoverButton extends JPanel
	{
		private boolean hover;
		private float scale = 1f;
		private float scaleTarget = 1f;
		private final Timer anim = new Timer(16, null);
		private Runnable onClick;

		HoverButton(int height)
		{
			setOpaque(false);
			setCursor(POINT);
			// Leave alignmentX at the default (centre, 0.5) so it matches the reveal panel
			// and struts. Mixing LEFT with the reveal's default made the buttons shrink and
			// shift right whenever a confirm/name field was inserted between them.
			setPreferredSize(new Dimension(0, height));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
			anim.addActionListener(e ->
			{
				if (Math.abs(scale - scaleTarget) >= 0.002f)
				{
					scale += (scaleTarget - scale) * 0.30f;
					repaint();
				}
				else
				{
					scale = scaleTarget;
					anim.stop();
				}
			});
			MouseAdapter ma = new MouseAdapter()
			{
				@Override
				public void mouseEntered(MouseEvent e)
				{
					hover = true;
					scaleTarget = SLOT_HOVER_SCALE;
					startPop();
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					hover = false;
					// Stay grown while this button's reveal is open, so opening a confirm or
					// the name field doesn't make the button pop back down to normal size.
					scaleTarget = isHeld() ? SLOT_HOVER_SCALE : 1f;
					startPop();
					repaint();
				}

				@Override
				public void mousePressed(MouseEvent e)
				{
					// Snap down for the press "pop", then spring back to the hover size.
					scale = SLOT_PRESS_SCALE;
					scaleTarget = SLOT_HOVER_SCALE;
					startPop();
				}

				@Override
				public void mouseReleased(MouseEvent e)
				{
					if (contains(e.getPoint()) && onClick != null)
					{
						onClick.run();
					}
				}
			};
			addMouseListener(ma);
		}

		void onClick(Runnable r)
		{
			onClick = r;
		}

		boolean isHover()
		{
			return hover;
		}

		/** True while this button's reveal (confirm or name field) is open; keeps it grown
		 *  and highlighted. Overridden by the preset and create buttons. */
		boolean isHeld()
		{
			return false;
		}

		/** Re-targets the scale after the held state changes, so a button that is no longer
		 *  active shrinks back and a newly active one grows, even without a mouse event. */
		void refreshHold()
		{
			scaleTarget = (hover || isHeld()) ? SLOT_HOVER_SCALE : 1f;
			startPop();
			repaint();
		}

		private void startPop()
		{
			if (!anim.isRunning())
			{
				anim.start();
			}
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			int w = getWidth();
			int h = getHeight();
			if (scale != 1f)
			{
				g2.translate(w / 2.0, h / 2.0);
				g2.scale(scale, scale);
				g2.translate(-w / 2.0, -h / 2.0);
			}
			paintButton(g2, w, h);
			g2.dispose();
		}

		abstract void paintButton(Graphics2D g2, int w, int h);
	}

	/** One saved preset: its name, highlighted while its confirm is open. */
	private class PresetRow extends HoverButton
	{
		private final int index;
		private final String name;

		PresetRow(int index)
		{
			super(SLOT_H);
			this.index = index;
			this.name = presets.get(index).name;
			onClick(() -> togglePresetConfirm(index));
		}

		@Override
		boolean isHeld()
		{
			return editingPreset == index;
		}

		@Override
		void paintButton(Graphics2D g2, int w, int h)
		{
			boolean sel = editingPreset == index;
			g2.setColor(sel || isHover() ? ColorScheme.DARKER_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR);
			g2.fillRoundRect(0, 0, w - 1, h - 1, 6, 6);
			if (sel)
			{
				g2.setColor(ColorScheme.BRAND_ORANGE);
				g2.fillRect(0, 3, 3, h - 6);
			}
			g2.setFont(FONT);
			FontMetrics fm = g2.getFontMetrics();
			int midY = (h + fm.getAscent() - fm.getDescent()) / 2;
			g2.setColor(Color.WHITE);
			g2.drawString(name, 10, midY);
		}
	}

	/** The "+ Create preset" button under the list; highlighted while the name field is open. */
	private class CreateRow extends HoverButton
	{
		CreateRow()
		{
			super(SLOT_H);
			onClick(EmoteWheelPanel.this::openCreate);
		}

		@Override
		boolean isHeld()
		{
			return creating;
		}

		@Override
		void paintButton(Graphics2D g2, int w, int h)
		{
			g2.setColor(creating || isHover() ? ColorScheme.DARKER_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR);
			g2.fillRoundRect(0, 0, w - 1, h - 1, 6, 6);
			g2.setFont(FONT);
			FontMetrics fm = g2.getFontMetrics();
			int midY = (h + fm.getAscent() - fm.getDescent()) / 2;
			String label = "+ Create preset";
			int tx = (w - fm.stringWidth(label)) / 2;
			g2.setColor(ColorScheme.BRAND_ORANGE);
			g2.drawString(label, tx, midY);
		}
	}

	/** A small centred text button for the confirm/name forms (Apply, Delete, Save, Cancel). */
	private class TextButton extends HoverButton
	{
		private final String label;
		private final Color color;

		TextButton(String label, Color color)
		{
			super(28);
			this.label = label;
			this.color = color;
		}

		@Override
		void paintButton(Graphics2D g2, int w, int h)
		{
			g2.setColor(isHover() ? ColorScheme.DARKER_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR);
			g2.fillRoundRect(0, 0, w - 1, h - 1, 6, 6);
			g2.setFont(FONT);
			FontMetrics fm = g2.getFontMetrics();
			int midY = (h + fm.getAscent() - fm.getDescent()) / 2;
			int tx = (w - fm.stringWidth(label)) / 2;
			g2.setColor(color);
			g2.drawString(label, tx, midY);
		}
	}

	/** The preset's six emote icons, previewed in two centered rows (4 then 2) above the
	 *  buttons. Sized by the preset icon scale; a Random emote animates through icons. */
	private class IconStrip extends JPanel
	{
		private static final int BASE_CELL = 34;
		private static final int ROW_GAP = 3;
		private final Emote[] emotes;
		private final int cell;

		IconStrip(Emote[] emotes)
		{
			this.emotes = emotes;
			this.cell = Math.max(16, (int) Math.round(BASE_CELL * FIXED_PRESET_ICON_SCALE / 100.0));
			setOpaque(false);
			int h = 2 * cell + ROW_GAP;
			setPreferredSize(new Dimension(0, h));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
					RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
					RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			int w = getWidth();
			// Never let four across spill past the panel width.
			int c = Math.min(cell, (w - 4) / 4);
			drawRow(g2, 0, Math.min(4, emotes.length), w, c, 0);
			drawRow(g2, 4, emotes.length, w, c, c + ROW_GAP);
			g2.dispose();
		}

		private void drawRow(Graphics2D g2, int from, int to, int w, int c, int y)
		{
			int count = to - from;
			if (count <= 0)
			{
				return;
			}
			int startX = (w - count * c) / 2;
			for (int i = from; i < to; i++)
			{
				Emote e = emotes[i];
				int x = startX + (i - from) * c;
				BufferedImage icon = e == Emote.RANDOM ? randomIcon() : iconProvider.apply(e);
				if (icon != null)
				{
					double f = Math.min((c - 4.0) / icon.getWidth(), (c - 4.0) / icon.getHeight());
					int iw = (int) Math.round(icon.getWidth() * f);
					int ih = (int) Math.round(icon.getHeight() * f);
					g2.drawImage(icon, x + (c - iw) / 2, y + (c - ih) / 2, iw, ih, null);
				}
				else if (e != Emote.NONE)
				{
					// A few emotes have no icon sprite: show a short text stand-in.
					g2.setFont(FONT.deriveFont(10f));
					FontMetrics fm = g2.getFontMetrics();
					String s = e.getDisplayName();
					s = s.length() > 3 ? s.substring(0, 3) : s;
					g2.setColor(ColorScheme.LIGHT_GRAY_COLOR);
					g2.drawString(s, x + (c - fm.stringWidth(s)) / 2,
							y + (c + fm.getAscent() - fm.getDescent()) / 2);
				}
			}
		}
	}

	/** A panel that composites its whole subtree at an animated alpha. */
	private static class FadePanel extends JPanel
	{
		private float alpha;

		FadePanel()
		{
			setOpaque(false);
		}

		void setAlpha(float a)
		{
			alpha = Math.max(0f, Math.min(1f, a));
			repaint();
		}

		@Override
		public void paint(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
			super.paint(g2);
			g2.dispose();
		}
	}
}
