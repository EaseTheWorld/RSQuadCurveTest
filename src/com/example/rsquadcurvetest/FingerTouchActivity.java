package com.example.rsquadcurvetest;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;

import com.easetheworld.renderscript.curve.AbsQuadCurve;
import com.easetheworld.renderscript.curve.CircleQuadCurve;

public class FingerTouchActivity extends Activity {

    private static final float RADIUS_DIP = 12f;
    private static final float MIN_RADIUS_DIP = 2f;

    private ImageView curveView;
    private Bitmap bitmap;
    private AbsQuadCurve quadCurve;
    private SeekBar sensitivitySeekBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finger_touch);

        curveView = (ImageView) findViewById(android.R.id.icon);
        curveView.post(new Runnable() {
            @Override
            public void run() {
                initBitmap();
            }
        });

        quadCurve = new CircleQuadCurve(this);
        quadCurve.setBlurRadiusInDip(MIN_RADIUS_DIP);
        quadCurve.setColor(0xff000000);

        curveView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                final float x = event.getX();
                final float y = event.getY();
                final long t = event.getEventTime();
                switch (event.getActionMasked()) {
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
                curveView.invalidate();
                return true;
            }
        });

        sensitivitySeekBar = (SeekBar) findViewById(R.id.seekBarVelocitySensitivity);
        sensitivitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float radiusDip = RADIUS_DIP - (RADIUS_DIP - MIN_RADIUS_DIP) * progress / seekBar.getMax();
                quadCurve.setRadiusWithVelocityInDip(RADIUS_DIP, radiusDip, 1f, 3f);
            }
        });
        sensitivitySeekBar.setProgress(sensitivitySeekBar.getMax() / 2);
    }

    private void initBitmap() {
        bitmap = Bitmap.createBitmap(curveView.getWidth(), curveView.getHeight(), Bitmap.Config.ARGB_8888);
        quadCurve.setBitmap(bitmap);
        curveView.setImageBitmap(bitmap);
    }

    private void clear() {
        bitmap.eraseColor(0);
        quadCurve.setBitmap(bitmap);
        curveView.invalidate();
    }

    public void clickHandler(View v) {
        switch (v.getId()) {
        case android.R.id.button1:
            clear();
            break;
        case android.R.id.button2:
            startActivity(new Intent(this, SingleCurveTestActivity.class)
                    .putExtra(SingleCurveTestActivity.CURVE_METHOD, SingleCurveTestActivity.CurveMethod.CIRCLE.name()));
            break;
        case android.R.id.button3:
            startActivity(new Intent(this, SingleCurveTestActivity.class)
                    .putExtra(SingleCurveTestActivity.CURVE_METHOD, SingleCurveTestActivity.CurveMethod.DISTANCE.name()));
            break;
        }
    }
}
