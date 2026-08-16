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
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import javax.inject.Inject;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Draws the one thing the wheel paints: a white frame stroke around the panel while the
 * rearrange key is held, fading in and out. The layout itself is real widgets driven from
 * {@link EmoteWheelPlugin#tickLayout()} on BeforeRender, not here.
 */
public class EmoteWheelOverlay extends Overlay
{
	private static final int STROKE_THICKNESS = 2;
	private static final int STROKE_OPACITY = 110;

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
		// Rearrange-mode frame stroke on the whole panel frame (UNIVERSE), square corners.
		// It only makes sense framing the panel background, so it's hidden when the
		// background is hidden (floating emotes) - otherwise it's a stray rectangle.
		double ra = plugin.getRearrangeAlpha();
		if (ra <= 0.02 || plugin.isBackgroundHidden())
		{
			return null;
		}
		Widget frame = plugin.getEmoteFrame();
		if (frame == null)
		{
			return null;
		}

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
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
		return null;
	}
}
