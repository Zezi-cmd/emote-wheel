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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
	/** Horizontal nudge of the centred label (tuned). */
	private static final int LABEL_OFFSET_X = 5;

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
		double alpha = plugin.getLabelAlpha();
		String label = plugin.getHoverLabel();
		if (alpha <= 0.02 || label == null || label.isEmpty())
		{
			return null;
		}

		Widget viewport = plugin.getEmoteViewport();
		if (viewport == null)
		{
			return null;
		}

		int cx = viewport.getCanvasLocation().getX() + viewport.getWidth() / 2;
		int cy = viewport.getCanvasLocation().getY() + viewport.getHeight() / 2;

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		// Shrink the font a touch for long names so they stay inside the panel.
		Font font = FontManager.getRunescapeBoldFont();
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();
		int maxWidth = viewport.getWidth() - 8;
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

		return null;
	}
}
