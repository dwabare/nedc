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
import com.segway.robot.sdk.locomotion.head.Head;
import com.segway.robot.sdk.locomotion.sbv.Base;

import java.util.Objects;
import java.util.Set;

public class SubmitLoomoActivity extends AppCompatActivity {
    private Base mBase;
    private Head mHead;
    private SharedPreferences sharedPref;
    private Set<String> rDistance;
    private Set<String> rAngle;
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
        rDistance = sharedPref.getStringSet("robotDistance",null);
        rAngle = sharedPref.getStringSet("robotAngle",null);

        if((rDistance==null)&&(rAngle==null)){
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

        mHead = Head.getInstance();
        mHead.bindService(CustomApplication.getContext(), new ServiceBinder.BindStateListener() {
            @Override
            public void onBind() {
                resetHead();
            }

            @Override
            public void onUnbind(String reason) { }
        });

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
                //Toast.makeText(CustomApplication.getContext(), "onCheckPointArrived: x: " + checkPoint.getX() + " y: " + checkPoint.getY(),Toast.LENGTH_SHORT).show();
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

                mBase.setUltrasonicObstacleAvoidanceEnabled(true);
                mBase.setUltrasonicObstacleAvoidanceDistance(0.45f);

                mBase.addCheckPoint(0f,0f,(float) (Math.PI));
                resetHead();

                String[] distances = rDistance.toArray(new String[0]);
                String[] angles = rAngle.toArray(new String[0]);

                float sPointX = Float.parseFloat(distances[0]) * (-1.0f);
                float sPointY = 0f;
                float sAngle = 0f;

                mBase.addCheckPoint(sPointX,sPointY);

                for (int i = 1 ; i < distances.length ; i++) {
                    float distance = Float.parseFloat(distances[i]) * (-1.0f);
                    float angle = Float.parseFloat(angles[i]);

                    sAngle = sAngle + angle;

                    float xPath = distance * (float) Math.cos(sAngle);
                    float yPath = distance * (float) Math.sin(sAngle);

                    sPointX = sPointX + xPath;
                    sPointY = sPointY + yPath;

                    String message = "Distance: " + distance * (-1.0f) + "  " + "Angle: " + (angle/Math.PI)*180;
                    //Toast.makeText(CustomApplication.getContext(), message , Toast.LENGTH_SHORT).show();

                    mBase.addCheckPoint(sPointX,sPointY);
                    //mBase.addCheckPoint(0f,0f,angle);
                    //resetHead();
                    //mBase.addCheckPoint(distance,0f);
                    //float xPath = distance * (float) Math.cos(angle);
                    //float yPath = distance * (float) Math.sin(angle);
                    //mBase.addCheckPoint(xPath,yPath,angle);
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

                mBase.setUltrasonicObstacleAvoidanceEnabled(true);
                mBase.setUltrasonicObstacleAvoidanceDistance(0.45f);

                mBase.addCheckPoint(0f,0f,(float) (Math.PI));
                resetHead();

                String[] distances = rDistance.toArray(new String[0]);
                String[] angles = rAngle.toArray(new String[0]);

                float sPointX = Float.parseFloat(distances[distances.length-1]) * (-1.0f);
                float sPointY = 0f;
                float sAngle = 0f;

                mBase.addCheckPoint(sPointX,sPointY);

                for (int i = distances.length-2 ; i >=0 ; i--) {
                    float distance = Float.parseFloat(distances[i]) * (-1.0f);
                    float angle = Float.parseFloat(angles[i+1]) * (-1.0f);

                    sAngle = sAngle + angle;

                    float xPath = distance * (float) Math.cos(sAngle);
                    float yPath = distance * (float) Math.sin(sAngle);

                    sPointX = sPointX + xPath;
                    sPointY = sPointY + yPath;

                    //String message = "Distance: " + distance * (-1.0f) + "  " + "Angle: " + (angle/Math.PI) * 180 * (-1.0f);
                    //Toast.makeText(CustomApplication.getContext(), message , Toast.LENGTH_SHORT).show();

                    mBase.addCheckPoint(sPointX,sPointY);
                    //mBase.addCheckPoint(0f,0f,-angle);
                    //resetHead();
                    //mBase.addCheckPoint(distance,0f);
                    //float xPath = distance * (float) Math.cos(angle+Math.PI);
                    //float yPath = distance * (float) Math.sin(angle+Math.PI);
                    //mBase.addCheckPoint(xPath,yPath,angle);
                }
            }
        });

        Button stopBtn = findViewById(R.id.stoploomoBtn);
        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    mHead.unbindService();
                    mBase.unbindService();
                    mBase.clearCheckPointsAndStop();
                }catch (Exception ex){ex.printStackTrace();}
                try {
                    Intent i = new Intent(SubmitLoomoActivity.this, MainActivity.class);
                    startActivity(i);
                }catch (Exception ex){ex.printStackTrace();}
            }
        });
    }

    private void resetHead() {
        mHead.setMode(Head.MODE_SMOOTH_TACKING);
        mHead.setWorldYaw(0);
        mHead.setWorldPitch(0.7f);
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
