package com.example.rsquadcurvetest;

import java.util.Arrays;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.FloatMath;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.easetheworld.renderscript.curve.QuadCurve;

public class QuadraticCurveView extends View {

    private static final String TAG = "CurveView";

    private static float CURVE_RADIUS = 5f;
    private static float POINT_RADIUS = 10f;
    private static float ZERO_BASE_OFFSET = 200f;

    private final QuadCurve quadCurve;

    private static int CURVE_STEP = 100;

    private boolean isCurveChanging = true;

    // quadratic curve
    private float x0;
    private float y0;
    private float x1;
    private float y1;
    private float x2;
    private float y2;
    private QuadraticCurveEquation equation;

    // touched point
    private float px;
    private float py;

    // nearest point
    private float nx;
    private float ny;

    private Bitmap curveBitmap;
    private Canvas curveCanvas;
    private Paint pointPaint;

    public QuadraticCurveView(Context context, AttributeSet attrs) {
        super(context, attrs);

        pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pointPaint.setStyle(Paint.Style.FILL);

        quadCurve = new QuadCurve(context);
        quadCurve.setRadiusWithVelocity(30f, 15f, 1f, 5f);
        quadCurve.setBlurRadius(20f);
        quadCurve.setColor(0xffff0000);
    }

    public void setCurveChanging(boolean changingCurve) {
        px = 0f;
        py = 0f;
        isCurveChanging = changingCurve;
        invalidate();
    }

    public void clear() {
        curveBitmap.eraseColor(0);
        quadCurve.setBitmap(curveBitmap);
        invalidate();
    }

    private void changingControlPoint(float x1, float y1) {
        drawCurve(x0, y0, x1, y1, x2, y2, 50f);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        Log.i(TAG, "onSizeChanged " + w + " " + h);
        initBitmap();
    }

    private void initBitmap() {
        if (curveBitmap == null) {
            curveBitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
            Log.i(TAG, "curveBitmap " + curveBitmap.getWidth() + " " + curveBitmap.getHeight());
            curveBitmap.eraseColor(0);
            // curveBitmap.setPixel(340, 260, 0x00ffffff);
            quadCurve.setBitmap(curveBitmap);
            curveCanvas = new Canvas(curveBitmap);
        }
    }
    
    private Path path = new Path();

    public void drawCurve(float x0, float y0, float x1, float y1, float x2, float y2, float radius) {
        // Log.i(TAG, "drawCurve " + x0 + " " + y0 + " " + x1 + " " + y1 +
        // " " + x2 + " " + y2);
        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        equation = new QuadraticCurveEquation(x0, y0, x1, y1, x2, y2);
        Canvas canvas = curveCanvas;
        canvas.drawColor(0xffffffff);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xff000000);
        if (true) { // renderscript
            quadCurve.drawCurve(x0, y0, x1, y1, x2, y2);
            // quadCurve.testCurve(x0, y0, x1, y1, x2, y2, 585, 359 - 242);
            // quadCurve.testCurve(x0, y0, x1, y1, x2, y2, 340, 255);
            // quadCurve.testCurve(x0, y0, x1, y1, x2, y2, 340, 260);
            // quadCurve.testCurve(x0, y0, x1, y1, x2, y2, 340, 265);
        } else {
            if (true) {
                paint.setStrokeWidth(radius);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setColor(0xffff0000);
                paint.setMaskFilter(new BlurMaskFilter(radius / 2f, BlurMaskFilter.Blur.NORMAL));
                paint.setStrokeJoin(Paint.Join.ROUND);
            long t1 = SystemClock.elapsedRealtime();
                // path.moveTo(x0, y0);
            path.quadTo(x1, y1, x2, y2);
            long t2 = SystemClock.elapsedRealtime();
            canvas.drawPath(path, paint);
            long t3 = SystemClock.elapsedRealtime();
                Log.i(TAG, "drawCurveSkia time=" + (t2 - t1) + "+" + (t3 - t2) + "=" + (t3 - t1));
            return;
        }
        if (radius > 0f) {
            int l = (int) (Math.min(Math.min(x0, x1), x2) - radius);
            int r = (int) (Math.max(Math.max(x0, x1), x2) + radius);
            int t = (int) (Math.min(Math.min(y0, y1), y2) - radius);
            int b = (int) (Math.max(Math.max(y0, y1), y2) + radius);
            float[] result = new float[2];
            float size = radius * radius;
            long t1 = SystemClock.elapsedRealtime();
            for (int x = l; x <= r; x++) {
                for (int y = t; y <= b; y++) {
                    equation.findDistanceByRange2((float) x, (float) y, result);
                        // Log.i(TAG, "Result (" + x + "," + y + ") dist=" +
                    // result[1]);
                    if (result[1] < size) {
                        int color = Color.argb((int) (255f * (size - result[1]) / size), (int) (result[0] * 255f),
                                (int) ((1f - result[0]) * 255f), 255);
                        color = 0xff000000;
                        curveBitmap.setPixel(x, y, color);
                    }
                }
            }
            long t2 = SystemClock.elapsedRealtime();
                Log.i(TAG, "Result time=" + (t2 - t1));
        } else {
            for (int i = 0; i <= CURVE_STEP; i++) {
                float t = (float) i / (float) CURVE_STEP;
                float x = equation.x(t);
                float y = equation.y(t);
                canvas.drawCircle(x, y, CURVE_RADIUS, paint);
            }
        }
        }
        // paint.setTextSize(24f);
        // canvas.drawText("P0", x0, y0, paint);
        // canvas.drawText("P1", x1, y1, paint);
        // canvas.drawText("P2", x2, y2, paint);

        float gradientBase = canvas.getHeight() - ZERO_BASE_OFFSET;
        canvas.drawLine(0f, gradientBase, canvas.getWidth(), gradientBase, paint);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (curveBitmap != null) {
            canvas.drawBitmap(curveBitmap, 0f, 0f, null);
        }
        
        if (isCurveChanging) {
            return;
        }

        if (px > 0f || py > 0f) {
        pointPaint.setColor(0xff808080);
        canvas.drawCircle(px, py, POINT_RADIUS, pointPaint);

        pointPaint.setColor(0xff0000ff);
        canvas.drawCircle(nx, ny, POINT_RADIUS, pointPaint);
        equation.drawGraph(px, py, canvas, 200);
        }
    }

    public void feedXY(float... x) {
        // initBitmap();
        int l = x.length;
        quadCurve.start(x[0], x[1], 0);
        for (int i = 2; i < l; i += 2) {
            quadCurve.draw(x[i], x[i + 1], 0);
        }
        quadCurve.end();
        // for (int i = 0; i < l; i += 2) {
        // curveBitmap.setPixel((int) x[i], (int) x[i + 1], 0xff80ff80);
        // }
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        float x = event.getX();
        float y = event.getY();
        long t = event.getEventTime();
        Log.i(TAG, "action=" + action + " x=" + x + " y=" + y);
        if (isCurveChanging) {
            switch (-1) {
            case MotionEvent.ACTION_MOVE:
                changingControlPoint(x, y);
                break;
            }
            switch (action) {
            case MotionEvent.ACTION_DOWN:
                quadCurve.start(x, y, t);
                break;
            case MotionEvent.ACTION_MOVE:
                quadCurve.draw(x, y, t);
                break;
            case MotionEvent.ACTION_UP:
                quadCurve.end();
                break;
            }
        } else {
            float[] result = new float[2];
            equation.findDistanceByRange(x, y, result);
            px = x;
            py = y;
            nx = equation.x(result[0]);
            ny = equation.y(result[0]);
        }
        invalidate();
        return true;
    }

    private static class QuadraticCurveEquation {
        final float x0;
        final float y0;
        final float x1;
        final float y1;
        final float x2;
        final float y2;
        final float x10;
        final float x21;
        final float y10;
        final float y21;

        final float ax;
        final float bx;
        final float cx;
        final float ay;
        final float by;
        final float cy;

        final float ax2ay2;
        final float bx2by2;
        final float axbxayby;
        final float tc;

        float axcxaycy2bx2by2; // coeff for t2
        float bxcxbycy; // coeff for t
        float cx2cy2; // coeff for 1

        QuadraticCurveEquation(float x0, float y0, float x1, float y1, float x2, float y2) {
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.x10 = x1 - x0;
            this.x21 = x2 - x1;
            this.y10 = y1 - y0;
            this.y21 = y2 - y1;

            this.ax = x0 - 2f * x1 + x2;
            this.bx = x10;
            this.cx = x0;
            this.ay = y0 - 2f * y1 + y2;
            this.by = y10;
            this.cy = y0;
            
            this.ax2ay2 = ax * ax + ay * ay;
            this.bx2by2 = bx * bx + by * by;
            this.axbxayby = (ax * bx + ay * by);
            this.tc = -axbxayby / ax2ay2;

            float[] roots = new float[3];
            // findRoots(1f, 0f, 0f, 0f, roots);
            // findRoots(1f, 0f, 0f, 8f, roots);
            // findRoots(1f, 0f, -1f, 0f, roots);
            // findRoots(1f, -2f, 1f, 0f, roots);
            // findRoots(1f, 2.047630f, -1.446516f, -0.727837f, roots);
        }

        float x(float t) {
            return t * t * ax + t * 2f * bx + cx;
        }

        float y(float t) {
            return t * t * ay + t * 2f * by + cy;
        }

        float f(float t) {
            // (x(t)-px)^2 + (y(t)-px)^2
            return t * (t * (t * (t * ax2ay2 + 4f * axbxayby) + 2f * axcxaycy2bx2by2) + 4f * bxcxbycy) + cx2cy2;
        }

        float df(float t) {
            return t * (t * (t * ax2ay2 + 3f * axbxayby) + axcxaycy2bx2by2) + bxcxbycy;
        }

        float ddf(float t) {
            return t * (t * 3f * ax2ay2 + 6f * axbxayby) + axcxaycy2bx2by2;
        }

        float dfRootByNewton(float t) {
            int i = 0;
            float t2;
            // Log.i(TAG, "newton method start at " + t + " " + px + " " +
            // py);
            do {
                t2 = t;
                // float df = df(t, px, py);
                // float ddf = ddf(t, px, py);
                // t = t - df / ddf;
                t = (t * t * (t * 2f * ax2ay2 + 3f * axbxayby) - bxcxbycy) /
                        (t * (t * 3f * ax2ay2 + 6f * axbxayby) + axcxaycy2bx2by2);
                i++;
                // Log.i(TAG, "newton method i=" + i + " " + t + " <- " +
                // t2);
            } while (Math.abs(t2 - t) > EPSILON);
            return t;
        }

        private static final float EPSILON = 0.001f;

        void drawGraph(float px, float py, Canvas canvas, int step) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            float radius = canvas.getWidth() / step / 2f;
            float gradientBase = canvas.getHeight() - ZERO_BASE_OFFSET;
            // int gradientColor = (gradientTc > 0f) ? 0xffff8040 : 0xffff4080;
            final float DISTANCE_SCALE = 0.003f;
            final float GRADIENT_SCALE = 0.003f;
            final float GRADIENT2_SCALE = 0.001f;
            float minX = 0f;
            float minDistance = -1000f;
            for (int i = 0; i <= step; i++) {
                float t = (float) i / (float) step;
                float f = f(t);
                float df = df(t);
                float ddf = ddf(t);
                float x = t * canvas.getWidth();
                p.setColor(0xff00ff00);
                canvas.drawCircle(x, canvas.getHeight() - f * DISTANCE_SCALE, radius * 3f, p);
                p.setColor(0xffff0000);
                canvas.drawCircle(x, gradientBase - df * GRADIENT_SCALE, radius * 2f, p);
                p.setColor(0xff0000ff);
                canvas.drawCircle(x, gradientBase - ddf * GRADIENT2_SCALE, radius, p);
                // Log.i(TAG, "i=" + i + "distance=" + distance +
                // " gradient=" + gradient + " " + gradient2);
                if (minDistance < 0f || f < minDistance) {
                    minDistance = f;
                    minX = x;
                }
            }

            p.setColor(0xff00cc00);
            canvas.drawCircle(minX, canvas.getHeight() - minDistance * DISTANCE_SCALE, POINT_RADIUS, p);
            float[] result = new float[2];
            findDistanceByRange(px, py, result);
            p.setColor(0xffff0000);
            canvas.drawCircle(result[0] * canvas.getWidth(), gradientBase, POINT_RADIUS, p);
        }

        private void findDistanceByRange2(float px, float py, float[] result) {
            float x0px = x0 - px;
            float y0py = y0 - py;
            float x2px = x2 - px;
            float y2py = y2 - py;
            float f0 = x0px * x0px + y0py * y0py;
            float f1 = x2px * x2px + y2py * y2py;
            float df0 = x10 * x0px + y10 * y0py;
            float df1 = x21 * x2px + y21 * y2py;
            axcxaycy2bx2by2 = ax * x0px + ay * y0py + 2f * bx2by2;
            bxcxbycy = df0;
            cx2cy2 = f0;
            float c = tc;
            float min = -1f;
            float minDistance = Float.MAX_VALUE;
            float ddfDet = 9f * axbxayby * axbxayby - 3f * ax2ay2 * axcxaycy2bx2by2;
            if (ddfDet > 0f) {
                float detSqrt_a2 = FloatMath.sqrt(ddfDet) / (3f * ax2ay2);
                float a2 = c - detSqrt_a2;
                float b2 = c + detSqrt_a2;

                // Divide range by ddf roots a2, b2.
                // So newton's method converge easily in the range.

                // first critical point. df is increasing
                float min1 = -1f;
                float minDistance1 = Float.MAX_VALUE;
                if (0f < a2) {
                    if (df0 >= 0f) { // 0 is the candidate.
                        min1 = 0f;
                        minDistance1 = f0;
                    } else if (1f <= a2) {
                        if (df1 <= 0f) { // 1 is the candidate.
                            min1 = 1f;
                            minDistance1 = f1;
                        } else { // local minimum is candidate.
                            min1 = dfRootByNewton(0f);
                            minDistance1 = f(min1);
                        }
                    } else { // local minimum is candidate.
                        if (df(a2) > 0f) {
                            min1 = dfRootByNewton(0f);
                            minDistance1 = f(min1);
                        }
                    }
                }

                // second critical point. df is decreasing
                float min2 = -1f;
                float minDistance2 = Float.MAX_VALUE;
                if (f0 < f1) {
                    min2 = 0f;
                    minDistance2 = f0;
                } else {
                    min2 = 1f;
                    minDistance2 = f1;
                }

                // third critical point. df is increasing
                float min3 = -1f;
                float minDistance3 = Float.MAX_VALUE;
                if (b2 < 1f) {
                    if (df1 <= 0f) { // 1 is the candidate.
                        min3 = 1f;
                        minDistance3 = f1;
                    } else if (0f >= b2) {
                        if (df0 >= 0f) { // 0 is the candidate.
                            min3 = 0f;
                            minDistance3 = f0;
                        } else { // local minimum is candidate.
                            min3 = dfRootByNewton(1f);
                            minDistance3 = f(min3);
                        }
                    } else { // local minimum is candidate.
                        if (df(b2) <= 0f) {
                            min3 = dfRootByNewton(1f);
                            minDistance3 = f(min3);
                        }
                    }
                }

                if (minDistance1 < minDistance2) {
                    if (minDistance1 < minDistance3) {
                        min = min1;
                        minDistance = minDistance1;
                    } else {
                        min = min3;
                        minDistance = minDistance3;
                    }
                } else {
                    if (minDistance2 < minDistance3) {
                        min = min2;
                        minDistance = minDistance2;
                    } else {
                        min = min3;
                        minDistance = minDistance3;
                    }
                }
            } else {
                // df is increasing
                if (df0 >= 0f) { // f is increasing
                    min = 0f;
                    minDistance = f0;
                } else if (df1 <= 0f) { // f is decreasing
                    min = 1f;
                    minDistance = f1;
                } else { // f is decreasing and increasing
                    float dfc = df(c);
                    min = dfRootByNewton(dfc > 0f ? 0f : 1f);
                    minDistance = f(min);
                }
            }
            result[0] = min;
            result[1] = minDistance;
        }

        private void findDistanceByRange(float px, float py, float[] result) {
            float cxpx = cx - px;
            float cypy = cy - py;
            axcxaycy2bx2by2 = ax * cxpx + ay * cypy + 2f * bx2by2;
            bxcxbycy = bx * cxpx + by * cypy;
            cx2cy2 = cxpx * cxpx + cypy * cypy;
            float c = tc;
            float min = -1f;
            float minDistance = Float.MAX_VALUE;
            float ddfDet = 9f * axbxayby * axbxayby - 3f * ax2ay2 * axcxaycy2bx2by2;
            if (ddfDet > 0f) {
                float detSqrt_a2 = FloatMath.sqrt(ddfDet) / (3f * ax2ay2);
                float a2 = c - detSqrt_a2;
                float b2 = c + detSqrt_a2;
                float s, e;

                // Divide range by ddf roots a2, b2.
                // So newton's method converge easily in the range.

                // df is increasing
                s = Math.min(a2, 0f);
                e = Math.min(a2, 1f);
                if (s < e) {
                    float min1;
                    float minDistance1;
                    if (df(s) > 0f) { // f is increasing
                        min1 = s;
                    } else if (df(e) < 0f) { // f is decreasing
                        min1 = e;
                    } else { // f is decreasing and increasing
                        min1 = dfRootByNewton(s);
                    }
                    minDistance1 = f(min1);
                    // Log.i(TAG, "Solution1 range=" + s + "-" + e +
                    // " distance=" + minDistance1 + " " + min1);
                    if (minDistance1 < minDistance) {
                        min = min1;
                        minDistance = minDistance1;
                    }
                }

                // df is decreasing
                s = Math.max(a2, 0f);
                e = Math.min(b2, 1f);
                if (s < e) {
                    float min2;
                    float minDistance2;
                    float fs = f(s);
                    float fe = f(e);
                    if (fs < fe) {
                        min2 = s;
                        minDistance2 = fs;
                    } else {
                        min2 = e;
                        minDistance2 = fe;
                    }
                    // Log.i(TAG, "Solution2 range=" + s + "-" + e +
                    // " distance=" + minDistance2 + " " + min2);
                    if (minDistance2 < minDistance) {
                        min = min2;
                        minDistance = minDistance2;
                    }
                }

                // df is increasing
                s = Math.max(b2, 0f);
                e = Math.max(b2, 1f);
                if (s < e) {
                    float min3;
                    float minDistance3;
                    if (df(s) > 0f) { // f is increasing
                        min3 = s;
                    } else if (df(e) < 0f) { // f is decreasing
                        min3 = e;
                    } else { // f is decreasing and increasing
                        min3 = dfRootByNewton(e);
                    }
                    minDistance3 = f(min3);
                    // Log.i(TAG, "Solution3 range=" + s + "-" + e +
                    // " distance=" + minDistance3 + " " + min3);
                    if (minDistance3 < minDistance) {
                        min = min3;
                        minDistance = minDistance3;
                    }
                }
            } else {
                // df is increasing
                float s = 0f;
                float e = 1f;
                if (df(s) > 0f) { // f is increasing
                    min = s;
                } else if (df(e) < 0f) { // f is decreasing
                    min = e;
                } else { // f is decreasing and increasing
                    float dfc = df(c);
                    min = dfRootByNewton(dfc > 0f ? s : e);
                }
                minDistance = f(min);
                // Log.i(TAG, "Solution4 range=" + s + "-" + e + " distance="
                // + minDistance + " " + min);
            }
            result[0] = min;
            result[1] = minDistance;
        }

        void findDistance(float px, float py, float[] roots) {
            float a = ax2ay2;
            float b = axbxayby * 3f;
            float c = 2f * bx2by2 + ax * (cx - px) + ay * (cy - py);
            float d = bx * (cx - px) + by * (cy - py);
            findRoots(a, b, c, d, roots);
        }

        static void findRoots(float a, float b, float c, float d, float[] roots) {
            float p = (3.f * a * c - b * b) / (3.f * a * a);
            float q = (2.f * b * b * b - 9.f * a * b * c + 27.f * a * a * d) / (27.f * a * a * a);
            float det = p * p * p / 27.f + q * q / 4.f;
            float offset = b / (3.f * a);

            Log.i(TAG, "p=" + p + " q=" + q + " det=" + det);
            if (p == 0.f) {
                float t;
                if (q == 0.f) {
                    t = 0.f;
                    Log.i(TAG, "solution1");
                } else {
                    t = (float) Math.cbrt(-q);
                    Log.i(TAG, "solution2");
                }
                roots[0] = t;
                roots[1] = t;
                roots[2] = t;
            } else {
                if (q == 0.f) {
                    roots[0] = 0.f;
                    float sqrt_p = FloatMath.sqrt(-p);
                    roots[1] = sqrt_p;
                    roots[2] = -sqrt_p;
                    Log.i(TAG, "solution3");
                } else if (Math.abs(det) < 0.00001f) {
                    roots[0] = 3.f * q / p;
                    roots[1] = roots[2] = -3.f * q / (2.f * p);
                    Log.i(TAG, "solution4");
                } else if (det < 0.f) {
                    float p32 = 2.f * FloatMath.sqrt(-p / 3.f);
                    float acosarg = (3.f * q) / (2.f * p) * FloatMath.sqrt(-3.f / p);
                    float acos = (float) Math.acos(acosarg) / 3.f;
                    float pi23 = (float) Math.PI * 2.f / 3.f;
                    roots[0] = p32 * FloatMath.cos(acos);
                    roots[1] = p32 * FloatMath.cos(acos + pi23);
                    roots[2] = p32 * FloatMath.cos(acos - pi23);
                    Log.i(TAG, "solution5 " + Math.acos(-0.5) + " " + Math.acos(1));
                } else {
                    float detSqrt = FloatMath.sqrt(det);
                    float u = (float) Math.cbrt(-q / 2.f + detSqrt);
                    float v = (float) Math.cbrt(-q / 2.f - detSqrt);
                    roots[0] = u + v;
                    roots[1] = Float.MAX_VALUE;
                    roots[2] = Float.MAX_VALUE;
                    Log.i(TAG, "solution6");
                }
            }
            roots[0] -= offset;
            if (roots[1] != Float.MAX_VALUE) {
                roots[1] -= offset;
                roots[2] -= offset;
            }
            Log.i(TAG, "roots=" + Arrays.toString(roots));
            verifyRoots(roots[0], roots[1], roots[2]);
            Log.i(TAG, "df=" + df(roots[0], a, b, c, d));
            Log.i(TAG, "df=" + df(roots[1], a, b, c, d));
            Log.i(TAG, "df=" + df(roots[2], a, b, c, d));

            // findRootsDummy(a, b, c, d);
        }

        static void findRootsDummy(float a, float b, float c, float d) {
            for (float f = -2.6f; f < 1f; f += 0.01f) {
                float df = df(f, a, b, c, d);
                // if (Math.abs(df) < 0.1)
                Log.i(TAG, "find dummy " + f + " " + df);
            }
        }

        static void verifyRoots(float x0, float x1, float x2) {
            Log.i(TAG, "roots=" + " a+b+c=" + (-(x0 + x1 + x2)) + " ab+bc+ca=" + (x0 * x1 + x1 * x2 + x2 * x0)
                    + " abc=" + (-x0 * x1 * x2));
        }

        static float df(float t, float a, float b, float c, float d) {
            float t2 = t * t;
            float t3 = t2 * t;
            return t3 * a + t2 * b + t * c + d;
        }
    }
}
