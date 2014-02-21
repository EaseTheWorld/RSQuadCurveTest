package com.easetheworld.renderscript.curve;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.support.v8.renderscript.Allocation;
import android.support.v8.renderscript.RenderScript;
import android.util.Log;

// TODO make abstract class to test both rs-cardano method and rs-circle method and skia-circle method
// TODO in circlebezier.rs, improve binary search. (prune search area)
public class CircleQuadCurve extends AbsQuadCurve {

    private final ScriptC_circlcebezier script;

    private final RenderScript rs;

    private Allocation inAllocation;

    private Bitmap bitmap;

    public CircleQuadCurve(Context context) {
        super(context);
        rs = RenderScript.create(context);

        script = new ScriptC_circlcebezier(rs);
        script.set_script(script);
    }

    @Override
    public void setBlurRadius(float blurRadius) {
        script.set_blurRadius(blurRadius);
    }

    @Override
    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
        inAllocation = Allocation.createFromBitmap(rs, bitmap, Allocation.MipmapControl.MIPMAP_NONE,
                Allocation.USAGE_SCRIPT);
        script.set_allocation(inAllocation);
    }

    @Override
    public void drawCurve(float x0, float y0, float r0, int c0, float x1, float y1, float r1, int c1, float x2,
            float y2, float r2, int c2) {
        long t1 = SystemClock.elapsedRealtime();
        super.drawCurve(x0, y0, r0, c0, x1, y1, r1, c1, x2, y2, r2, c2);
        inAllocation.copyTo(this.bitmap);
        long t2 = SystemClock.elapsedRealtime();
        Log.i(TAG, "drawCurveRS time=" + (t2 - t1));
    }

    @Override
    public void moveTo(float x, float y, float r, int color) {
        script.invoke_moveTo(x, y, r, color);
    }

    @Override
    public void quadTo(float x1, float y1, float r1, int c1, float x2, float y2, float r2, int c2) {
        long t1 = SystemClock.elapsedRealtime();
        script.invoke_quadTo(x1, y1, r1, c1, x2, y2, r2, c2);
        long t2 = SystemClock.elapsedRealtime();
        inAllocation.copyTo(this.bitmap);
        long t3 = SystemClock.elapsedRealtime();
        Log.i(TAG, "circleQuadTo time=" + (t2 - t1) + "+" + (t3 - t2) + "=" + (t3 - t1));
    }
}