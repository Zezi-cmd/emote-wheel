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
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
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
	private static final int SLOT_H = 42;
	private static final int STRUT = 4;
	private static final int ROW_PITCH = SLOT_H + STRUT;
	private static final int OPTION_H = 34;
	private static final int PICKER_H = 340;
	private static final int DRAG_THRESHOLD = 5;
	private static final int HANDLE_W = 22;
	private static final float DIM = 0.35f;

	private static final Font FONT = FontManager.getRunescapeBoldFont().deriveFont(FONT_SIZE);

	/** Pointing hand on the buttons, the move/cross cursor on the drag handle. */
	private static final Cursor POINT = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
	private static final Cursor MOVE = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);

	private final EmoteWheelConfig config;
	private final ConfigManager configManager;

	private final SlotRow[] slotRows = new SlotRow[SLOTS];
	private final JPanel stack = new JPanel();
	private final JPanel optionList = new JPanel();
	private final FadePanel picker = new FadePanel();

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

	EmoteWheelPanel(EmoteWheelConfig config, ConfigManager configManager)
	{
		this.config = config;
		this.configManager = configManager;

		// Build timers against locals so the lambdas don't touch the blank-final fields
		// before they're assigned (which the compiler rejects).
		final Timer dim = new Timer(16, null);
		dim.addActionListener(e ->
		{
			boolean moving = false;
			for (SlotRow r : slotRows)
			{
				if (r.stepDim())
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

		JLabel title = new JLabel("Emote Wheel");
		title.setFont(FontManager.getRunescapeBoldFont().deriveFont(FONT_SIZE));
		title.setForeground(Color.WHITE);
		add(title);

		add(Box.createVerticalStrut(8));

		JLabel favorites = new JLabel("Favorites");
		favorites.setFont(FontManager.getRunescapeBoldFont());
		favorites.setForeground(ColorScheme.BRAND_ORANGE);
		add(favorites);

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

		JLabel presets = new JLabel("Presets");
		presets.setFont(FontManager.getRunescapeBoldFont());
		presets.setForeground(ColorScheme.BRAND_ORANGE);
		add(presets);

		JLabel comingSoon = new JLabel("coming soon");
		comingSoon.setFont(FontManager.getRunescapeSmallFont());
		comingSoon.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		add(comingSoon);

		optionList.setLayout(new BoxLayout(optionList, BoxLayout.Y_AXIS));
		optionList.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JScrollPane scroll = new JScrollPane(optionList,
				ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		picker.setLayout(new BorderLayout());
		picker.add(scroll, BorderLayout.CENTER);
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
			stack.repaint();
			optionList.repaint();
		});
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
		startDim();
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
	}

	private void closePicker()
	{
		editing = -1;
		for (SlotRow r : slotRows)
		{
			r.setSelected(false);
			r.setDimTarget(1f);
		}
		startDim();
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
		startDim();
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
		// The slot's current emote is pinned to the top, then everything else in order.
		Emote current = slotValue(editing);
		optionList.add(new OptionRow(current, false, true));
		for (Emote e : Emote.values())
		{
			if (e == current)
			{
				continue;
			}
			boolean used = e != Emote.NONE && isTaken(editing, e);
			optionList.add(new OptionRow(e, used, false));
		}
		optionList.revalidate();
		optionList.repaint();
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

	private void startDim()
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

	// ----------------------------------------------------------- components

	/** One of the six slot buttons: slot number + emote name, with hover highlight,
	 *  click-to-edit and drag-to-reorder (lifted row + an insertion line). Dims toward
	 *  {@link #DIM} while another slot is being edited. */
	private class SlotRow extends JPanel
	{
		private final int index;
		private boolean selected;
		private boolean hover;
		private boolean handleHover;
		private float dim = 1f;
		private float dimTarget = 1f;

		private Point pressAt;
		private boolean handlePress;
		private boolean dragging;

		SlotRow(int index)
		{
			this.index = index;
			setOpaque(false);
			setCursor(POINT);
			setPreferredSize(new Dimension(0, SLOT_H));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, SLOT_H));

			MouseAdapter ma = new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					pressAt = e.getPoint();
					handlePress = e.getX() < HANDLE_W;
					dragging = false;
				}

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
					handleHover = false;
					setCursor(POINT);
					repaint();
				}

				@Override
				public void mouseMoved(MouseEvent e)
				{
					// Light up the grab handle and show the open-hand grab cursor while the
					// cursor is over the three lines; the pointing hand elsewhere on the row.
					boolean onHandle = e.getX() < HANDLE_W;
					if (onHandle != handleHover)
					{
						handleHover = onHandle;
						setCursor(onHandle ? MOVE : POINT);
						repaint();
					}
				}

				@Override
				public void mouseDragged(MouseEvent e)
				{
					if (!dragging && handlePress && pressAt != null
							&& Math.abs(e.getY() - pressAt.y) > DRAG_THRESHOLD)
					{
						dragging = true;
						setCursor(MOVE);
						closeInstant();
					}
					if (dragging)
					{
						Point inStack = SwingUtilities.convertPoint(SlotRow.this, e.getPoint(), stack);
						int t = 1 + Math.max(0, Math.min(SLOTS - 1, inStack.y / ROW_PITCH));
						updateDragTarget(t);
					}
				}

				@Override
				public void mouseReleased(MouseEvent e)
				{
					if (dragging)
					{
						reorder(index, dragTarget < 1 ? index : dragTarget);
						updateDragTarget(-1);
					}
					else
					{
						openPicker(index);
					}
					dragging = false;
					pressAt = null;
					// Back to the open hand if we're still over the handle, else pointing hand.
					setCursor(e.getX() < HANDLE_W ? MOVE : POINT);
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

		void setDimTarget(float t)
		{
			dimTarget = t;
		}

		boolean stepDim()
		{
			if (Math.abs(dim - dimTarget) < 0.01f)
			{
				dim = dimTarget;
				return false;
			}
			dim += (dimTarget - dim) * 0.25f;
			repaint();
			return true;
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

			// Drag handle: three short lines on the left. Only a press here starts a drag.
			// Lights up white and thicker while the cursor is over it, separate from the
			// row's own hover highlight.
			g2.setColor(handleHover ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
			g2.setStroke(new BasicStroke(handleHover ? 2.6f : 2f));
			int hcy = h / 2;
			g2.drawLine(6, hcy - 5, HANDLE_W - 6, hcy - 5);
			g2.drawLine(6, hcy, HANDLE_W - 6, hcy);
			g2.drawLine(6, hcy + 5, HANDLE_W - 6, hcy + 5);

			g2.setFont(FONT);
			FontMetrics fm = g2.getFontMetrics();
			int midY = (h + fm.getAscent() - fm.getDescent()) / 2;
			int sx = HANDLE_W + 8;
			g2.setColor(ColorScheme.LIGHT_GRAY_COLOR);
			g2.drawString("Slot " + index, sx, midY);
			g2.setColor(Color.WHITE);
			g2.drawString(slotValue(index).getDisplayName(), sx + fm.stringWidth("Slot 6") + 12, midY);

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
