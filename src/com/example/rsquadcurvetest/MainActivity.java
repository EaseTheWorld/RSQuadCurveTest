package com.example.rsquadcurvetest;

import java.util.LinkedList;
import java.util.List;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.os.Bundle;
import android.util.FloatMath;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static int BG_COLOR = 0xff808080;
    private static int POINT_COLOR = 0xff80ff80;
    private static int MY_PATH_COLOR = 0xffff8080;
    private static int SKIA_PATH_COLOR = 0xff80ff80;
    private Canvas canvas;
    private Paint myPathPaint;
    private Paint skiaPathPaint;
    private Paint pointPaint;
    private ImageView iv;
    private ListView lv;
    private List<PointFT> points = new LinkedList<PointFT>();

    private static class PointFT extends PointF {
        long t;

        public PointFT(float x, float y, long t) {
            super(x, y);
            this.t = t;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        iv = (ImageView) findViewById(android.R.id.icon);

        Bitmap bmp = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bmp);
        myPathPaint = new Paint();
        myPathPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
        myPathPaint.setColor(MY_PATH_COLOR);
        skiaPathPaint = new Paint();
        skiaPathPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
        skiaPathPaint.setColor(SKIA_PATH_COLOR);
        skiaPathPaint.setStyle(Paint.Style.STROKE);
        skiaPathPaint.setStrokeWidth(2f);
        pointPaint = new Paint();
        pointPaint.setColor(POINT_COLOR);

        clearPath();

        iv.setImageBitmap(bmp);
        iv.setOnTouchListener(touchListener);

        lv = (ListView) findViewById(android.R.id.list);
        lv.setAdapter(new ArrayAdapter<PointFT>(this, android.R.layout.simple_list_item_1, points) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextSize(8f);
                PointFT p = getItem(position);
                tv.setText("i=" + position + "\nx=" + p.x + "\ny=" + p.y + "\nt=" + p.t);
                return tv;
            }
        });
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                drawPath(position);
            }
        });
        points.add(new PointFT(283.0f, 447.75f, 138));
        points.add(new PointFT(310.67178f, 329.38342f, 199));
        points.add(new PointFT(322.95428f, 251.96268f, 215));
        points.add(new PointFT(333.97583f, 225.75574f, 232));
        // points.add(new PointFT(352.62143f, 219.73898f, 249));
    }

    private OnTouchListener touchListener = new OnTouchListener() {

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            int action = event.getActionMasked();
            PointFT p = new PointFT(event.getX(), event.getY(), event.getEventTime() - event.getDownTime());
            switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_UP:
                points.add(p);
                drawTouchPoint(p);
                iv.invalidate();
                break;
            }
            return true;
        }
    };

    private void clearPath() {
        points.clear();
        canvas.drawColor(BG_COLOR, PorterDuff.Mode.SRC);
        iv.invalidate();
    }

    private void drawTouchPoint(PointFT p) {
        canvas.drawCircle(p.x, p.y, 2f, pointPaint);
    }

    private void drawPath(int position) {
        float v1 = 0f, r1 = 10f, v2 = 5f, r2 = 2f;
        Path path = new Path();
        PathMeasure pm = new PathMeasure();
        float wolframLength = 0f;
        int start = 1;
        int end = points.size() - 1;
        if (position > 0) {
            start = Math.max(start, position - 1);
            end = Math.min(end, position + 1);
            canvas.save();
            float cx = canvas.getWidth() / 2;
            float cy = canvas.getHeight() / 2;
            float tx = (points.get(position).x + points.get(position + 1).x) / 2f;
            float ty = (points.get(position).y + points.get(position + 1).y) / 2f;
            canvas.translate(cx - tx, cy - ty);
            float scale = 8f;
            canvas.scale(scale, scale, cx, cy);
        }
        canvas.drawColor(BG_COLOR, PorterDuff.Mode.SRC);
        float vPrev = 0f;
        float dvPrev = 0f;
        float tPrev = 0f;
        for (int i = start; i < end; i++) {
            float x0 = (points.get(i - 1).x + points.get(i).x) / 2f;
            float y0 = (points.get(i - 1).y + points.get(i).y) / 2f;
            float t0 = (points.get(i - 1).t + points.get(i).t) / 2f;
            float x1 = points.get(i).x;
            float y1 = points.get(i).y;
            float t1 = points.get(i).t;
            float x2 = (points.get(i).x + points.get(i + 1).x) / 2f;
            float y2 = (points.get(i).y + points.get(i + 1).y) / 2f;
            float t2 = (points.get(i).t + points.get(i + 1).t) / 2f;
            if (i == 1) {
                path.moveTo(x0, y0);
            }
            path.quadTo(x1, y1, x2, y2);

            int step = 20;
            float sPrev = 0f;
            for (int ui = 0; ui <= step; ui++) {
                float u = (float) ui / (float) step;
                float x = u * u * (x0 - 2f * x1 + x2) + 2f * u * (x1 - x0) + x0;
                float y = u * u * (y0 - 2f * y1 + y2) + 2f * u * (y1 - y0) + y0;
                float t = u * u * (t0 - 2f * t1 + t2) + 2f * u * (t1 - t0) + t0;
                float v = getQuadCurveVelocity(x0, y0, t0, x1, y1, t1, x2, y2, t2, u);
                float s = getQuadCurveLength(x0, y0, x1, y1, x2, y2, u);
                
                float radius;
                if (v < v1) {
                    radius = r1;
                } else if (v > v2) {
                    radius = r2;
                } else {
                    float rc = 2f * (r1 - r2) / ((v2 - v1) * (v2 - v1));
                    if ((v1 + v2) / 2f > v) {
                        radius = -rc * (v - v1) * (v - v1) + r1;
                    } else {
                        radius = rc * (v - v2) * (v - v2) + r2;
                    }
                    
                    float a = (r1 - r2) / (v1 - v2);
                    float b = r1 - a * v1;
                    radius = a * v + b;
                }
                radius = v * 3f;
                float dv = v - vPrev;
                float dt = t - tPrev;
                tPrev = t;
                dvPrev = dv;
                vPrev = v;
                sPrev = s;
                canvas.drawCircle(x, y, radius, myPathPaint);
            }
            
            // pm.setPath(path, false);
            // float pathLength = pm.getLength();
            // wolframLength += getQuadCurveLength(x0, y0, x1, y1, x2, y2, 1f);
        }

        for (PointFT p : points) {
            drawTouchPoint(p);
        }
        
        if (position > 0) {
            canvas.restore();
        }
        // canvas.drawPath(path, skiaPathPaint);
        iv.invalidate();
    }

    private float getQuadCurveLength(float x0, float y0, float x1, float y1, float x2, float y2, float u) {
        float ax = x0 - 2f * x1 + x2;
        float bx = 2f * (x1 - x0);
        float ay = y0 - 2f * y1 + y2;
        float by = 2f * (y1 - y0);

        float a = 4f * (ax * ax + ay * ay);
        float b = 4f * (ax * bx + ay * by);
        float c = bx * bx + by * by;

        float result;
        if (a == 0) {
            if (b == 0) {
                result = FloatMath.sqrt(c);
            } else {
                float buc = b * u + c;
                result = 2f * (buc * FloatMath.sqrt(buc) - c * FloatMath.sqrt(c)) / (3f * b);
            }
        } else {
            float pu = 2f * a * u + b;
            float qu = FloatMath.sqrt(u * (a * u + b) + c);
            float p0 = b;
            float q0 = FloatMath.sqrt(c);
            float rc = 2f * FloatMath.sqrt(a);

            result = (rc * (pu * qu - p0 * q0) - (b * b - 4f * a * c)
                    * (float) Math.log((rc * qu + pu) / (rc * q0 + p0)))
                    / (8f * a * FloatMath.sqrt(a));
        }
        return result;
    }

    // dv = ds / dt = (ds/du) / (dt/du)
    private float getQuadCurveVelocity(float x0, float y0, float t0, float x1, float y1, float t1, float x2, float y2,
            float t2, float u) {
        float ax = x0 - 2f * x1 + x2;
        float bx = 2f * (x1 - x0);
        float ay = y0 - 2f * y1 + y2;
        float by = 2f * (y1 - y0);

        float a = 4f * (ax * ax + ay * ay);
        float b = 4f * (ax * bx + ay * by);
        float c = bx * bx + by * by;
        
        float ds_du = FloatMath.sqrt(u * (a * u + b) + c);
        float dt_du = 2f * ((t0 - 2f * t1 + t2) * u + (t1 - t0));
        return ds_du / dt_du;
    }

    public void clickHandler(View v) {
        switch (v.getId()) {
        case android.R.id.button1:
            clearPath();
            break;
        case android.R.id.button2:
            drawPath(-1);
            break;
        }
        lv.invalidateViews();
    }

}
