package com.segway.waterjecttolaser;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.segway.robot.algo.Pose2D;
import com.segway.robot.algo.minicontroller.CheckPoint;
import com.segway.robot.algo.minicontroller.CheckPointStateListener;
import com.segway.robot.sdk.base.bind.ServiceBinder;
import com.segway.robot.sdk.locomotion.sbv.Base;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class SubmitLoomoActivity extends AppCompatActivity {
    Base mBase;
    SharedPreferences sharedPref;
    private Set<String> rPathX;
    private Set<String> rPathY;
    private Set<String> rPathTheta;
    private Button moveBtn;
    private Button backBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_submit_loomo);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        moveBtn = findViewById(R.id.moveloomoBtn);
        backBtn = findViewById(R.id.backloomoBtn);

        sharedPref = PreferenceManager.getDefaultSharedPreferences(CustomApplication.getContext());
        rPathX = sharedPref.getStringSet("robotPathX",null);
        rPathY = sharedPref.getStringSet("robotPathY",null);
        rPathTheta = sharedPref.getStringSet("robotPathTheta",null);

        if((rPathX==null)&&(rPathY==null)&&(rPathTheta==null)){
            Toast.makeText(CustomApplication.getContext(), "Path is not tracked",Toast.LENGTH_LONG).show();
            moveBtn.setEnabled(false);
            backBtn.setEnabled(false);
        }else{
            boolean isArrived = sharedPref.getBoolean("isArrived", false);
            if (isArrived) {
                moveBtn.setEnabled(false);
                backBtn.setEnabled(true);
            } else {
                moveBtn.setEnabled(true);
                backBtn.setEnabled(false);
            }
        }

        mBase = Base.getInstance();
        mBase.bindService(CustomApplication.getContext(), new ServiceBinder.BindStateListener() {
            @Override
            public void onBind() { }

            @Override
            public void onUnbind(String reason) { }
        });

        mBase.setOnCheckPointArrivedListener(new CheckPointStateListener() {
            @Override
            public void onCheckPointArrived(CheckPoint checkPoint, final Pose2D realPose, boolean isLast) {
                Toast.makeText(CustomApplication.getContext(), "onCheckPointArrived: x: " + checkPoint.getX() + " y: " + checkPoint.getY(),Toast.LENGTH_SHORT).show();
                if(isLast){
                    SharedPreferences.Editor editor = sharedPref.edit();
                    boolean isArrived = sharedPref.getBoolean("isArrived",false);
                    if(isArrived){
                        editor.putBoolean("isArrived",false);
                        editor.apply();

                        moveBtn.setEnabled(true);
                        backBtn.setEnabled(false);
                    }else{
                        editor.putBoolean("isArrived",true);
                        editor.apply();

                        moveBtn.setEnabled(false);
                        backBtn.setEnabled(true);
                    }
                }
            }

            @Override
            public void onCheckPointMiss(CheckPoint checkPoint, Pose2D realPose, boolean isLast, int reason) { }
        });

        moveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBase.setControlMode(Base.CONTROL_MODE_NAVIGATION);
                mBase.cleanOriginalPoint();
                Pose2D pose2D = mBase.getOdometryPose(-1);
                mBase.setOriginalPoint(pose2D);

                String[] pathX = rPathX.toArray(new String[0]);
                String[] pathY = rPathY.toArray(new String[0]);
                String[] pathTheta = rPathTheta.toArray(new String[0]);

                for (int i = 0 ; i < pathX.length ; i++) {
                    float valueX = Float.parseFloat(pathX[i]);
                    float valueY = Float.parseFloat(pathY[i]);
                    float valueTheta = Float.parseFloat(pathTheta[i]);

                    mBase.addCheckPoint(valueX,valueY,valueTheta);
                }
            }
        });

        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBase.setControlMode(Base.CONTROL_MODE_NAVIGATION);
                mBase.cleanOriginalPoint();
                Pose2D pose2D = mBase.getOdometryPose(-1);
                mBase.setOriginalPoint(pose2D);

                String[] pathX = rPathX.toArray(new String[0]);
                String[] pathY = rPathY.toArray(new String[0]);
                String[] pathTheta = rPathTheta.toArray(new String[0]);

                for (int i = pathX.length - 1 ; i >= 0 ; i--) {
                    float valueX = Float.parseFloat(pathX[i]);
                    float valueY = Float.parseFloat(pathY[i]);
                    float valueTheta = Float.parseFloat(pathTheta[i]);

                    mBase.addCheckPoint(valueX,valueY,valueTheta -(float) (Math.PI /2));
                }
            }
        });

        Button stopBtn = findViewById(R.id.stoploomoBtn);
        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBase.clearCheckPointsAndStop();
                try {
                    Intent i = new Intent(SubmitLoomoActivity.this, MainActivity.class);
                    startActivity(i);
                }catch (Exception ex){ex.printStackTrace();}
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }
}
