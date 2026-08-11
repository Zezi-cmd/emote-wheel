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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import javax.inject.Inject;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Draws the one thing the wheel paints: the hovered emote's name in the centre of
 * the ring, fading in and out. The layout itself is real widgets driven from the
 * plugin's {@link EmoteWheelPlugin#tickLayout()} on BeforeRender, not here.
 */
public class EmoteWheelOverlay extends Overlay
{
	// The two typed messages of the rearrange tip: "Drag and Drop" then "Emotes to
	// rearrange", one word/phrase per line.
	private static final String[] TIP_MSG_1 = {"Drag", "and", "Drop"};
	private static final String[] TIP_MSG_2 = {"Emotes to", "Rearrange", "Them"};

	// Tuned appearance values for the rearrange-mode frame stroke and centre prompt.
	private static final int LABEL_OFFSET_X = 3;
	private static final int STROKE_THICKNESS = 2;
	private static final int STROKE_OPACITY = 110;
	private static final float LABEL_FONT_SIZE = 16f;
	private static final double LABEL_MAX_ALPHA = 0.8;

	// Typewriter timing (ms): per-char speed, hold after message 1, hold after the final
	// message, clear fade, and a blank pause before the loop restarts.
	private static final long CHAR_MS = 90;
	private static final long HOLD_MS = 300;
	private static final long END_HOLD_MS = 900;
	private static final long CLEAR_MS = 110;
	private static final long LOOP_PAUSE_MS = 900;

	private final EmoteWheelPlugin plugin;

	@Inject
	EmoteWheelOverlay(EmoteWheelPlugin plugin)
	{
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Widget viewport = plugin.getEmoteViewport();
		if (viewport == null)
		{
			return null;
		}

		int vx = viewport.getCanvasLocation().getX();
		int vy = viewport.getCanvasLocation().getY();
		int vw = viewport.getWidth();
		int vh = viewport.getHeight();

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		// Rearrange-mode frame stroke, on the whole panel frame (UNIVERSE), square
		// corners. Thickness and max opacity are tunable while dialing the effect in.
		double ra = plugin.getRearrangeAlpha();
		if (ra > 0.02)
		{
			Widget frame = plugin.getEmoteFrame();
			if (frame != null)
			{
				int fx = frame.getCanvasLocation().getX();
				int fy = frame.getCanvasLocation().getY();
				int fw = frame.getWidth();
				int fh = frame.getHeight();
				int sa = (int) Math.round(Math.max(0.0, Math.min(1.0, ra)) * STROKE_OPACITY);
				int inset = 1;
				Stroke prev = g.getStroke();
				g.setStroke(new BasicStroke(STROKE_THICKNESS));
				g.setColor(new Color(255, 255, 255, sa));
				g.drawRect(fx + inset, fy + inset, fw - 2 * inset - 1, fh - 2 * inset - 1);
				g.setStroke(prev);
			}
		}

		// Centred label: the typewriter "Drag and Drop / Emotes to rearrange" tip while the
		// rearrange key is held (fades out once a drag starts), else the Random hover label.
		int cx = vx + vw / 2;
		double dla = plugin.getDragLabelAlpha();
		long start = plugin.getLabelShowStart();
		if (dla > 0.02 && start != 0)
		{
			drawTypewriterTip(g, cx, vy + vh / 2, System.currentTimeMillis() - start, dla * LABEL_MAX_ALPHA * plugin.getTipDim());
		}
		else
		{
			double alpha = plugin.getLabelAlpha();
			String label = plugin.getHoverLabel();
			if (alpha > 0.02 && label != null && !label.isEmpty())
			{
				drawCenterLabel(g, cx, vy + vh / 2, vw, label, alpha);
			}
		}

		return null;
	}

	/** Renders the looping two-message typewriter tip: each message types out, holds,
	 *  fades clear, then the next; the pair repeats while the key stays held. 'elapsed' is
	 *  ms since the tip began; 'alpha' the overall fade. */
	private void drawTypewriterTip(Graphics2D g, int cx, int cy, long elapsed, double alpha)
	{
		// Message 1 uses the standard hold; message 2 uses the tunable end-message hold.
		long h1 = HOLD_MS;
		long h2 = END_HOLD_MS;
		long w1 = windowFor(TIP_MSG_1, h1);
		long w2 = windowFor(TIP_MSG_2, h2);
		long t = elapsed % (w1 + w2 + LOOP_PAUSE_MS);
		if (t < w1)
		{
			drawMessagePhase(g, cx, cy, TIP_MSG_1, t, alpha, h1);
		}
		else if (t < w1 + w2)
		{
			drawMessagePhase(g, cx, cy, TIP_MSG_2, t - w1, alpha, h2);
		}
		// else: blank pause before the loop restarts - draw nothing.
	}

	/** Total ms one message occupies: type all chars, hold, then fade clear. */
	private long windowFor(String[] msg, long holdMs)
	{
		return totalChars(msg) * CHAR_MS + holdMs + CLEAR_MS;
	}

	/** Draws one message at time t within its window (type -> hold -> clear). */
	private void drawMessagePhase(Graphics2D g, int cx, int cy, String[] msg, long t, double alpha, long holdMs)
	{
		int c = totalChars(msg);
		long type = c * CHAR_MS;
		int revealFull;
		double revealPartial;
		double msgAlpha;
		if (t < type)
		{
			revealFull = (int) (t / CHAR_MS);
			revealPartial = (t % CHAR_MS) / (double) CHAR_MS;
			msgAlpha = 1.0;
		}
		else if (t < type + holdMs)
		{
			revealFull = c;
			revealPartial = 0.0;
			msgAlpha = 1.0;
		}
		else
		{
			revealFull = c;
			revealPartial = 0.0;
			msgAlpha = 1.0 - (t - type - holdMs) / (double) CLEAR_MS;
		}
		drawRevealed(g, cx, cy, msg, revealFull, revealPartial, alpha * clamp01(msgAlpha));
	}

	/** Draws stacked lines centred on (cx, cy), revealing characters up to revealFull (full)
	 *  plus the next one at revealPartial alpha - the typewriter reveal. */
	private void drawRevealed(Graphics2D g, int cx, int cy, String[] lines, int revealFull, double revealPartial, double baseAlpha)
	{
		g.setFont(FontManager.getRunescapeBoldFont().deriveFont(LABEL_FONT_SIZE));
		FontMetrics fm = g.getFontMetrics();
		int lineH = fm.getAscent() + fm.getDescent();
		int startY = cy - (lineH * lines.length) / 2 + fm.getAscent();
		int gi = 0;
		for (int li = 0; li < lines.length; li++)
		{
			String line = lines[li];
			int x = cx - fm.stringWidth(line) / 2 + LABEL_OFFSET_X;
			int y = startY + li * lineH;
			for (int ci = 0; ci < line.length(); ci++)
			{
				char ch = line.charAt(ci);
				double ca = gi < revealFull ? 1.0 : (gi == revealFull ? revealPartial : 0.0);
				gi++;
				int cw = fm.charWidth(ch);
				if (ca > 0.001 && ch != ' ')
				{
					int a = (int) Math.round(clamp01(baseAlpha * ca) * 255);
					String s = String.valueOf(ch);
					g.setColor(new Color(0, 0, 0, a));
					g.drawString(s, x + 1, y + 1);
					g.setColor(new Color(255, 255, 255, a));
					g.drawString(s, x, y);
				}
				x += cw;
			}
		}
	}

	private static int totalChars(String[] lines)
	{
		int n = 0;
		for (String l : lines)
		{
			n += l.length();
		}
		return n;
	}

	private static double clamp01(double v)
	{
		return Math.max(0.0, Math.min(1.0, v));
	}

	/** Draws a label centred in the ring, shrinking the font for long text, with a
	 *  drop shadow and the given fade alpha. */
	private void drawCenterLabel(Graphics2D g, int cx, int cy, int vw, String label, double alpha)
	{
		Font font = FontManager.getRunescapeBoldFont();
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();
		int maxWidth = vw - 8;
		if (fm.stringWidth(label) > maxWidth)
		{
			font = FontManager.getRunescapeSmallFont();
			g.setFont(font);
			fm = g.getFontMetrics();
		}

		int tw = fm.stringWidth(label);
		int tx = cx - tw / 2 + LABEL_OFFSET_X;
		int ty = cy + (fm.getAscent() - fm.getDescent()) / 2;
		int a = (int) Math.round(Math.max(0.0, Math.min(1.0, alpha)) * 255);

		g.setColor(new Color(0, 0, 0, a));
		g.drawString(label, tx + 1, ty + 1);
		g.setColor(new Color(255, 255, 255, a));
		g.drawString(label, tx, ty);
	}
}
