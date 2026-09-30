/*
 * Copyright (C) 2026 The DiamaneOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.calculator2;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * A pad key drawn as a Tally keycap: a face rounded at 22 % of its shorter side, with a 1 dp
 * highlight along its top edge and a shaded skirt along its bottom edge that compresses while the
 * key is pressed. The face sits 4 dp inside the key, which leaves an 8 dp gap between keys and room
 * for the 2 dp keyboard focus ring, drawn 2 dp clear of the face. On the small keys of small
 * windows the gap and the skirt shrink with the key, so the face keeps room for its label. The face
 * colour is the theme's {@code keyColor}, which may be a colour state list (the inverse toggle
 * lights when selected).
 */
public class KeycapDrawable extends Drawable {

    private static final float HIGHLIGHT_DP = 1f;
    private static final float SKIRT_DP = 3f;
    private static final float SKIRT_PRESSED_DP = 1f;
    /** On small keys: the most of the key's shorter side the gap and the skirt may take. */
    private static final float MAX_INSET_FRACTION = 0.1f;
    private static final float MAX_SKIRT_FRACTION = 0.08f;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mFace = new RectF();
    private final RectF mPart = new RectF();

    private ColorStateList mFaceColors = ColorStateList.valueOf(Color.GRAY);
    private int mFaceColor = Color.GRAY;
    private int mHighlightColor;
    private int mShadeColor;
    private int mPressColor;
    private int mRingColor;
    private float mRadiusFraction = 0.22f;
    private float mRingWidth;
    private float mFaceInset;
    private float mHighlight;
    private float mSkirt;
    private float mSkirtPressed;
    private boolean mPressed;
    private boolean mFocused;
    private int mAlpha = 255;

    /** For inflation from XML ({@code <drawable class="...KeycapDrawable" />}). */
    public KeycapDrawable() {
    }

    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs,
            Resources.Theme theme) throws XmlPullParserException, IOException {
        super.inflate(r, parser, attrs, theme);
        mRadiusFraction = r.getFraction(R.fraction.tally_keycap_radius, 1, 1);
        mRingWidth = r.getDimension(R.dimen.tally_focus_ring_width);
        mFaceInset = mRingWidth + r.getDimension(R.dimen.tally_focus_ring_offset);
        mHighlight = dp(r, HIGHLIGHT_DP);
        mSkirt = dp(r, SKIRT_DP);
        mSkirtPressed = dp(r, SKIRT_PRESSED_DP);
        mHighlightColor = r.getColor(R.color.tally_keycap_highlight, theme);
        mShadeColor = r.getColor(R.color.tally_keycap_shade, theme);
        mPressColor = r.getColor(R.color.tally_press, theme);
        mRingColor = r.getColor(R.color.tally_accent, theme);
        if (theme != null) {
            applyTheme(theme);
        }
    }

    @Override
    public boolean canApplyTheme() {
        // Resources inflates drawables without a theme and then applies the view's theme.
        return true;
    }

    @Override
    public void applyTheme(Resources.Theme theme) {
        super.applyTheme(theme);
        final TypedArray a = theme.obtainStyledAttributes(new int[] { R.attr.keyColor });
        final ColorStateList faceColors = a.getColorStateList(0);
        a.recycle();
        if (faceColors != null) {
            mFaceColors = faceColors;
        }
        mFaceColor = mFaceColors.getColorForState(getState(), mFaceColors.getDefaultColor());
        invalidateSelf();
    }

    private static float dp(Resources r, float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, r.getDisplayMetrics());
    }

    private float faceInset() {
        final Rect b = getBounds();
        return Math.min(mFaceInset, MAX_INSET_FRACTION * Math.min(b.width(), b.height()));
    }

    private boolean updateFace() {
        final Rect b = getBounds();
        final float inset = faceInset();
        mFace.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset);
        return mFace.width() > 0 && mFace.height() > 0;
    }

    private float faceRadius() {
        return mRadiusFraction * Math.min(mFace.width(), mFace.height());
    }

    private void fill(Canvas canvas, RectF rect, float radius, int color) {
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(color);
        canvas.drawRoundRect(rect, radius, radius, mPaint);
    }

    @Override
    public void draw(Canvas canvas) {
        if (!updateFace()) {
            return;
        }
        final boolean layer = mAlpha < 255;
        if (layer) {
            canvas.saveLayerAlpha(new RectF(getBounds()), mAlpha);
        }
        final float radius = faceRadius();
        final float fullSkirt = Math.min(mSkirt,
                MAX_SKIRT_FRACTION * Math.min(mFace.width(), mFace.height()));
        final float skirt = mPressed ? Math.min(mSkirtPressed, fullSkirt / 3f) : fullSkirt;
        // The face, shaded where the skirt shows along the bottom edge ...
        fill(canvas, mFace, radius, mFaceColor);
        fill(canvas, mFace, radius, mShadeColor);
        mPart.set(mFace.left, mFace.top, mFace.right, mFace.bottom - skirt);
        fill(canvas, mPart, radius, mFaceColor);
        // ... and lit along the top edge.
        fill(canvas, mPart, radius, mHighlightColor);
        mPart.top += mHighlight;
        fill(canvas, mPart, radius, mFaceColor);
        if (mPressed) {
            fill(canvas, mFace, radius, mPressColor);
        }
        if (mFocused) {
            final float half = mRingWidth / 2f;
            final Rect b = getBounds();
            mPart.set(b.left + half, b.top + half, b.right - half, b.bottom - half);
            final float ringRadius = radius + faceInset() - half;
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(mRingWidth);
            mPaint.setColor(mRingColor);
            canvas.drawRoundRect(mPart, ringRadius, ringRadius, mPaint);
        }
        if (layer) {
            canvas.restore();
        }
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    public boolean hasFocusStateSpecified() {
        return true;
    }

    @Override
    protected boolean onStateChange(int[] state) {
        boolean pressed = false;
        boolean focused = false;
        for (int s : state) {
            if (s == android.R.attr.state_pressed) {
                pressed = true;
            } else if (s == android.R.attr.state_focused) {
                focused = true;
            }
        }
        final int faceColor = mFaceColors.getColorForState(state, mFaceColors.getDefaultColor());
        final boolean changed =
                pressed != mPressed || focused != mFocused || faceColor != mFaceColor;
        mPressed = pressed;
        mFocused = focused;
        mFaceColor = faceColor;
        if (changed) {
            invalidateSelf();
        }
        return changed;
    }

    @Override
    public void getOutline(Outline outline) {
        if (updateFace()) {
            final Rect face = new Rect();
            mFace.round(face);
            outline.setRoundRect(face, faceRadius());
        }
    }

    @Override
    public void setAlpha(int alpha) {
        if (alpha != mAlpha) {
            mAlpha = alpha;
            invalidateSelf();
        }
    }

    @Override
    public int getAlpha() {
        return mAlpha;
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
