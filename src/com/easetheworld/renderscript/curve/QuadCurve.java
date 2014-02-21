package com.easetheworld.renderscript.curve;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.support.v8.renderscript.Allocation;
import android.support.v8.renderscript.RenderScript;
import android.util.FloatMath;
import android.util.Log;

// TODO make abstract class to test both rs-cardano method and rs-circle method and skia-circle method
// TODO in circlebezier.rs, improve binary search. (prune search area)
public class QuadCurve {

    private static final String TAG = "QuadCurve";

    private final ScriptC_circlcebezier circlebezierScript;

    private final RenderScript rs;

    private Allocation inAllocation;

    private Bitmap bitmap;

    private float prevX;
    private float prevY;
    private float prevR;
    private float prevT;

    protected float radiusForDefault;
    protected float radiusAtMaxVelocity = 0f;
    protected float maxVelocity = 5f;
    protected float minVelocity = 1f;

    protected int colorForDefault;

    protected final float density;

    public QuadCurve(Context context) {
        rs = RenderScript.create(context);
        density = context.getResources().getDisplayMetrics().density;

        circlebezierScript = new ScriptC_circlcebezier(rs);
        circlebezierScript.set_script(circlebezierScript);
    }

    public void setRadius(float radius) {
        this.radiusForDefault = radius;
        this.radiusAtMaxVelocity = radius;
    }

    public void setRadiusInDip(float radiusDip) {
        setRadius(radiusDip * density);
    }

    public void setRadiusWithVelocity(float radiusAtMinVelocity, float radiusAtMaxVelocity, float minVelocity,
            float maxVelocity) {
        this.radiusForDefault = radiusAtMinVelocity;
        this.radiusAtMaxVelocity = radiusAtMaxVelocity;
        this.minVelocity = minVelocity;
        this.maxVelocity = maxVelocity;
    }

    public void setRadiusWithVelocityInDip(float radiusDipAtMinVelocity, float radiusDipAtMaxVelocity,
            float minVelocityDip, float maxVelocityDip) {
        setRadiusWithVelocity(radiusDipAtMinVelocity * density, radiusDipAtMaxVelocity * density,
                minVelocityDip * density, maxVelocityDip * density);
    }

    public void setBlurRadius(float blurRadius) {
        circlebezierScript.set_blurRadius(blurRadius);
    }

    public void setBlurRadiusInDip(float blurRadiusDip) {
        setBlurRadius(blurRadiusDip * density);
    }

    public void setColor(int color) {
        colorForDefault = color;
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
        inAllocation = Allocation.createFromBitmap(rs, bitmap, Allocation.MipmapControl.MIPMAP_NONE,
                Allocation.USAGE_SCRIPT);
        circlebezierScript.set_allocation(inAllocation);
    }

    public void drawCurve(float x0, float y0, float x1, float y1, float x2, float y2) {
        drawCurve(x0, y0, radiusForDefault, colorForDefault, x1, y1, radiusForDefault, colorForDefault, x2, y2,
                radiusForDefault, colorForDefault);
    }

    public void drawCurve(float x0, float y0, float r0, int c0, float x1, float y1, float r1, int c1, float x2,
            float y2, float r2, int c2) {
        long t1 = SystemClock.elapsedRealtime();
        moveTo(x0, y0, r0, c0);
        quadTo(x1, y1, r1, c1, x2, y2, r2, c2);
        long t2 = SystemClock.elapsedRealtime();
        inAllocation.copyTo(this.bitmap);
        long t3 = SystemClock.elapsedRealtime();
        Log.i(TAG, "drawCurveRS time=" + (t2 - t1) + "+" + (t3 - t2) + "=" + (t3 - t1));
    }

    public void testCurve(float x0, float y0, float x1, float y1, float x2, float y2, int x, int y) {
        long t1 = SystemClock.elapsedRealtime();
        // curveScript.invoke_testQuadCurve(curveScript, inAllocation, x0, y0,
        // x1, y1, x2, y2, x, y);
        long t2 = SystemClock.elapsedRealtime();
        inAllocation.copyTo(this.bitmap);
        long t3 = SystemClock.elapsedRealtime();
        Log.i(TAG, "drawCurveRS time=" + (t2 - t1) + "+" + (t3 - t2) + "=" + (t3 - t1));
    }

    public void moveTo(float x, float y) {
        moveTo(x, y, radiusForDefault, colorForDefault);
    }

    private void moveTo(float x, float y, float r, int color) {
        circlebezierScript.invoke_moveTo(x, y, r, color);
    }

    public void quadTo(float x1, float y1, float x2, float y2) {
        quadTo(x1, y1, radiusForDefault, colorForDefault, x2, y2, radiusForDefault, colorForDefault);
    }

    public void quadTo(float x1, float y1, float r1, int c1, float x2, float y2, float r2, int c2) {
        long t1 = SystemClock.elapsedRealtime();
        circlebezierScript.invoke_quadTo(x1, y1, r1, c1, x2, y2, r2, c2);
        long t2 = SystemClock.elapsedRealtime();
        inAllocation.copyTo(this.bitmap);
        long t3 = SystemClock.elapsedRealtime();
        Log.i(TAG, "circleQuadTo time=" + (t2 - t1) + "+" + (t3 - t2) + "=" + (t3 - t1));
    }

    public void start(float x, float y) {
        moveTo(x, y);
        prevX = x;
        prevY = y;
        prevR = 0f;
        Log.i(TAG, "quadCurve draw1 start " + x + " " + y);
    }

    public void start(float x, float y, long t) {
        float r = radiusForDefault;
        moveTo(x, y, r, colorForDefault);
        prevX = x;
        prevY = y;
        prevR = r;
        prevT = t;
        Log.i(TAG, "quadCurve draw1 start " + x + " " + y + " " + r);
    }

    public void draw(float x, float y) {
        float x1 = prevX;
        float y1 = prevY;
        float x2 = (prevX + x) / 2f;
        float y2 = (prevY + y) / 2f;
        Log.i(TAG, "quadCurve draw1 move " + x1 + "f," + y1 + "f," + x2 + "f," + y2 + "f");
        quadTo(x1, y1, x2, y2);
        prevX = x;
        prevY = y;
    }

    public void draw(float x, float y, long t) {
        float x1 = prevX;
        float y1 = prevY;
        float r1 = prevR;
        float x2 = (prevX + x) / 2f;
        float y2 = (prevY + y) / 2f;
        float r = getRadiusFromVelocity(prevX, x, prevY, y, prevT, t);
        float r2 = (prevR + r) / 2f;
        Log.i(TAG, "quadCurve draw1 move " + x1 + "f," + y1 + "f," + x2 + "f," + y2 + "f" + " r=" + r1
                + " " + r2);
        quadTo(x1, y1, r1, colorForDefault, x2, y2, r2, colorForDefault);
        prevX = x;
        prevY = y;
        prevR = r;
        prevT = t;
    }

    public void end() {
        float x1 = prevX;
        float y1 = prevY;
        float r1 = prevR;
        float x2 = prevX;
        float y2 = prevY;
        float r2 = prevR;
        if (prevR == 0f) {
            quadTo(x1, y1, x2, y2);
        } else {
            quadTo(x1, y1, r1, colorForDefault, x2, y2, r2, colorForDefault);
        }
        Log.i(TAG, "quadCurve draw1 end " + x1 + "f," + y1 + "f," + x2 + "f," + y2 + "f" + " r=" + r1
                + " " + r2);
    }

    protected float getRadiusFromVelocity(float x0, float x1, float y0, float y1, float t0, float t1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float dt = t1 - t0;
        float v;
        if (dt == 0f) {
            v = 0f;
        } else {
            v = FloatMath.sqrt(dx * dx + dy * dy) / dt;
        }

        float radius;
        if (v <= minVelocity) {
            radius = radiusForDefault;
        } else if (v >= maxVelocity) {
            radius = radiusAtMaxVelocity;
        } else {
            radius = radiusForDefault - (radiusForDefault - radiusAtMaxVelocity) * (v - minVelocity)
                    / (maxVelocity - minVelocity);
        }
        return radius;
    }
}