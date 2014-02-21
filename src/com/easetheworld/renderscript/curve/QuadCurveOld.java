package com.easetheworld.renderscript.curve;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.support.v8.renderscript.Allocation;
import android.support.v8.renderscript.RenderScript;
import android.support.v8.renderscript.RenderScript.RSErrorHandler;
import android.support.v8.renderscript.RenderScript.RSMessageHandler;
import android.util.FloatMath;
import android.util.Log;

public class QuadCurveOld {

    private static final String TAG = "QuadCurve";

    private final ScriptC_curve curveScript;

    private final RenderScript rs;

    private Allocation inAllocation;

    private Bitmap bitmap;

    private float prevX;
    private float prevY;
    private float prevR;
    private float prevX2;
    private float prevY2;
    private float prevR2;
    private float prevT;
    private boolean useFirstCap;

    private float radius; // default. and for min velocity
    private float radiusAtMaxVelocity;

    public QuadCurveOld(Context context) {
        rs = RenderScript.create(context);
        rs.setMessageHandler(new RSMessageHandler() {

            @Override
            public void run() {
                super.run();
                android.util.Log.i(TAG, "Message run " + mID + " " + mData + " " + mLength);
            }
        });
        rs.setErrorHandler(new RSErrorHandler() {

            @Override
            public void run() {
                super.run();
                android.util.Log.i(TAG, "Error run " + mErrorMessage + " " + mErrorNum);
            }
        });

        curveScript = new ScriptC_curve(rs);
    }

    public void setRadius(float radius) {
        curveScript.set_radius0(radius);
        curveScript.set_radius1(radius);
    }

    public void setRadius(float radius0, float radius1) {
        curveScript.set_radius0(radius0);
        curveScript.set_radius1(radius1);
    }

    public void setBlurRadius(float blurRadius) {
        curveScript.set_blurRadius(blurRadius);
    }

    public void setColor(int color) {
        curveScript.invoke_setColor(color);
    }

    public void setColor(int color0, int color1) {
        curveScript.invoke_setColor(color0, color1);
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
        inAllocation = Allocation.createFromBitmap(rs, bitmap, Allocation.MipmapControl.MIPMAP_NONE,
                Allocation.USAGE_SCRIPT);
    }

    private float lastX;
    private float lastY;

    public void drawCurve(float x0, float y0, float x1, float y1, float x2, float y2) {
        long t1 = SystemClock.elapsedRealtime();
        curveScript.invoke_drawQuadCurve(curveScript, inAllocation, x0, y0, x1, y1, x2, y2, useFirstCap);
        long t2 = SystemClock.elapsedRealtime();
        inAllocation.copyTo(this.bitmap);
        long t3 = SystemClock.elapsedRealtime();
        Log.i(TAG, "drawCurveRS time=" + (t2 - t1) + "+" + (t3 - t2) + "=" + (t3 - t1));
    }

    public void testCurve(float x0, float y0, float x1, float y1, float x2, float y2, int x, int y) {
        long t1 = SystemClock.elapsedRealtime();
        curveScript.invoke_testQuadCurve(curveScript, inAllocation, x0, y0, x1, y1, x2, y2, x, y);
        long t2 = SystemClock.elapsedRealtime();
        inAllocation.copyTo(this.bitmap);
        long t3 = SystemClock.elapsedRealtime();
        Log.i(TAG, "drawCurveRS time=" + (t2 - t1) + "+" + (t3 - t2) + "=" + (t3 - t1));
    }

    public void start(float x, float y, long t) {
        float r = maxRadius;
        prevX = x;
        prevY = y;
        prevR = r;
        prevT = t;
        prevX2 = prevX;
        prevY2 = prevY;
        prevR2 = prevR;
        useFirstCap = true;
        Log.i(TAG, "quadCurve draw1 start " + x + " " + y + " " + r);
    }

    public void draw(float x, float y, long t) {
        float x0 = prevX2;
        float y0 = prevY2;
        float r0 = prevR2;
        float x1 = prevX;
        float y1 = prevY;
        float r1 = prevR;
        float x2 = (prevX + x) / 2f;
        float y2 = (prevY + y) / 2f;
        float r = getRadiusFromVelocity(prevX, x, prevY, y, prevT, t);
        float r2 = (prevR + r) / 2f;
        prevX = x;
        prevY = y;
        prevR = r;
        prevX2 = x2;
        prevY2 = y2;
        prevR2 = r2;
        prevT = t;
        Log.i(TAG, "quadCurve draw1 move " + x0 + "f," + y0 + "f," + x1 + "f,"
                + y1 + "f," + x2 + "f," + y2 + "f" + " r=" + r0 + " " + r1 + " " + r2);
            drawCurve(x0, y0, x1, y1, x2, y2);
            useFirstCap = false;
    }

    public void end() {
        float x0 = prevX2;
        float y0 = prevY2;
        float r0 = prevR2;
        float x1 = prevX;
        float y1 = prevY;
        float r1 = prevR;
        float x2 = prevX;
        float y2 = prevY;
        float r2 = prevR;
        Log.i(TAG, "quadCurve draw1 end " + x0 + "f," + y0 + "f," + x1 + "f," +
                y1 + "f," + x2 + "f," + y2 + "f" + " r=" + r0 + " " + r1 + " " + r2);
            drawCurve(x0, y0, x1, y1, x2, y2);
    }

    private float maxRadius = 30f;
    private float minRadius = 15f;
    private float maxVelocity = 5f;
    private float minVelocity = 0f;

    private float getRadiusFromVelocity(float x0, float x1, float y0, float y1, float t0, float t1) {
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
            radius = maxRadius;
        } else if (v >= maxVelocity) {
            radius = minRadius;
        } else {
            radius = maxRadius - (maxRadius - minRadius) * (v - minVelocity) / (maxVelocity - minVelocity);
        }
        return radius;
    }
}