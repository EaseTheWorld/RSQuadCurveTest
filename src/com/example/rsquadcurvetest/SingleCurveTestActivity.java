package com.example.rsquadcurvetest;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Toast;

import com.easetheworld.renderscript.curve.AbsQuadCurve;
import com.easetheworld.renderscript.curve.CircleQuadCurve;
import com.easetheworld.renderscript.curve.DistanceQuadCurve;

public class SingleCurveTestActivity extends Activity {

    enum CurveMethod {
        CIRCLE("Good for radius variant and connected-curves."),
        DISTANCE("Good for color variant and single curve.");

        private String desc;

        private CurveMethod(String desc) {
            this.desc = desc;
        }
    }

    static final String CURVE_METHOD = "curveType";

    private ImageView canvas;
    private float maxDistance2;

    private final PointF[] points = new PointF[3];
    private AbsQuadCurve quadCurve;
    private Bitmap bitmap;

    private static final float MIN_RADIUS_DIP = 10f;
    private static final float MAX_RADIUS_DIP = 30f;
    private static final float MAX_BLUR_RADIUS = MIN_RADIUS_DIP;

    private SeekBar seekBarRadiusStart;
    private SeekBar seekBarRadiusEnd;

    private SeekBar seekBarColorStart;
    private SeekBar seekBarColorEnd;

    private SeekBar seekBarBlurRadius;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_single_curve_test);
        canvas = (ImageView) findViewById(R.id.canvas);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int w = dm.widthPixels;
        int h = dm.widthPixels;
        float maxDistance = dm.density * 20f;
        maxDistance2 = maxDistance * maxDistance;

        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        String curveMethodName = getIntent().getStringExtra(CURVE_METHOD);
        CurveMethod curveMethod = CurveMethod.valueOf(curveMethodName);
        if (curveMethod == CurveMethod.CIRCLE) {
            quadCurve = new CircleQuadCurve(this);
        } else {
            quadCurve = new DistanceQuadCurve(this);
        }

        Toast.makeText(this, curveMethod.desc, Toast.LENGTH_LONG).show();

        points[0] = new PointF(w / 4, h / 4);
        points[1] = new PointF(w / 2, h * 3 / 4);
        points[2] = new PointF(w * 3 / 4, h / 4);

        canvas.setImageBitmap(bitmap);
        canvas.setOnTouchListener(new View.OnTouchListener() {
            private PointF dragPoint;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                final float x = event.getX();
                final float y = event.getY();
                switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    int pi = getNearestPointIndex(x, y);
                    if (pi < 0) {
                        dragPoint = null;
                        return false;
                    }
                    dragPoint = points[pi];
                case MotionEvent.ACTION_MOVE:
                case MotionEvent.ACTION_UP:
                    dragPoint.set(x, y);
                    updateBitmap();
                    break;
                }
                return true;
            }
        });
        
        seekBarRadiusStart = (SeekBar) findViewById(R.id.seekBarRadiusStart);
        seekBarRadiusEnd = (SeekBar) findViewById(R.id.seekBarRadiusEnd);
        seekBarColorStart = (SeekBar) findViewById(R.id.seekBarColorStart);
        seekBarColorEnd = (SeekBar) findViewById(R.id.seekBarColorEnd);
        seekBarBlurRadius = (SeekBar) findViewById(R.id.seekBarBlurRadius);

        seekBarRadiusStart.setOnSeekBarChangeListener(onSeekBarChangeListener);
        seekBarRadiusEnd.setOnSeekBarChangeListener(onSeekBarChangeListener);
        seekBarColorStart.setOnSeekBarChangeListener(onSeekBarChangeListener);
        seekBarColorEnd.setOnSeekBarChangeListener(onSeekBarChangeListener);
        seekBarBlurRadius.setOnSeekBarChangeListener(onSeekBarChangeListener);

        seekBarColorStart.setProgress(0);
        seekBarColorEnd.setProgress(120);
        seekBarRadiusStart.setProgress(0);
        seekBarRadiusEnd.setProgress(100);
        seekBarBlurRadius.setProgress(50);

        updateBitmap();
    }

    private SeekBar.OnSeekBarChangeListener onSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser)
                updateBitmap();
        }
    };

    private void updateBitmap() {
        bitmap.eraseColor(0);
        quadCurve.setBitmap(bitmap);

        float blurRadius = MAX_BLUR_RADIUS * seekBarBlurRadius.getProgress() / seekBarBlurRadius.getMax();
        quadCurve.setBlurRadiusInDip(blurRadius);

        float density = getResources().getDisplayMetrics().density;
        float radius0 = MIN_RADIUS_DIP + (MAX_RADIUS_DIP - MIN_RADIUS_DIP) * seekBarRadiusStart.getProgress()
                / seekBarRadiusStart.getMax();
        float radius2 = MIN_RADIUS_DIP + (MAX_RADIUS_DIP - MIN_RADIUS_DIP) * seekBarRadiusEnd.getProgress()
                / seekBarRadiusEnd.getMax();
        float radius1 = (radius0 + radius2) / 2f;

        radius0 *= density;
        radius1 *= density;
        radius2 *= density;

        float[] hsv = new float[3];
        hsv[1] = 1f;
        hsv[2] = 1f;
        hsv[0] = seekBarColorStart.getProgress();
        int color0 = Color.HSVToColor(hsv);
        hsv[0] = seekBarColorEnd.getProgress();
        int color2 = Color.HSVToColor(hsv);
        hsv[0] = (seekBarColorStart.getProgress() + seekBarColorEnd.getProgress()) / 2f;
        int color1 = Color.HSVToColor(hsv);

        quadCurve.drawCurve(points[0].x, points[0].y, radius0, color0,
                points[1].x, points[1].y, radius1, color1,
                points[2].x, points[2].y, radius2, color2);
        canvas.invalidate();
    }

    private static float getDistance(PointF p, float x, float y) {
        float dx = p.x - x;
        float dy = p.y - y;
        return dx * dx + dy * dy;
    }

    private int getNearestPointIndex(float x, float y) {
        int min = 1;
        float minDistance = Float.MAX_VALUE;
        for (int i = 0; i < points.length; i++) {
            PointF p = points[i];
            float distance = getDistance(p, x, y);
            if (distance < minDistance && distance < maxDistance2) {
                min = i;
                minDistance = distance;
            }
        }
        return min;
    }
}
