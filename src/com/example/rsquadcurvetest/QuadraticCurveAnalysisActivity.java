package com.example.rsquadcurvetest;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class QuadraticCurveAnalysisActivity extends Activity {

    private QuadraticCurveView curveView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analysis);
        curveView = (QuadraticCurveView) findViewById(android.R.id.icon);

        /*
         * curveView.post(new Runnable() {
         * 
         * @Override public void run() { curveView.drawCurve(300, 100, 200, 400,
         * 600, 300, 50); curveView.drawCurve(600, 300, 200, 450, 400, 550, 50);
         * curveView.drawCurve(400, 550, 410, 600, 420, 650, 50); //
         * curveView.drawCurve(510.74542f, 500.42282f, 504.27774f, //
         * 510.08337f, 504.13885f, 510.2917f, 50); // curveView.feedXY(147.0f,
         * 726.5f, 148.5f, 719.13715f, // 149.18283f, 716.2687f, 153.03078f,
         * 706.92615f); } });
         */
    }

    public void clickHandler(View v) {
        switch (v.getId()) {
        case android.R.id.button1:
            curveView.setCurveChanging(false);
            break;
        case android.R.id.button2:
            startActivity(new Intent(this, SingleCurveTestActivity.class));
            // curveView.setCurveChanging(true);
            break;
        case android.R.id.button3:
            curveView.clear();
            break;
        }
    }

}
