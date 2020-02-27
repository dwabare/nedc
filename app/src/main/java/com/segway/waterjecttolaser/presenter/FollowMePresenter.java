package com.segway.waterjecttolaser.presenter;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.Surface;

import com.segway.robot.algo.Pose2D;
import com.segway.robot.algo.dts.BaseControlCommand;
import com.segway.robot.algo.dts.DTSPerson;
import com.segway.robot.algo.dts.PersonDetectListener;
import com.segway.robot.algo.dts.PersonTrackingListener;
import com.segway.robot.algo.dts.PersonTrackingProfile;
import com.segway.robot.algo.dts.PersonTrackingWithPlannerListener;
import com.segway.waterjecttolaser.CustomApplication;
import com.segway.waterjecttolaser.interfaces.PresenterChangeInterface;
import com.segway.waterjecttolaser.interfaces.ViewChangeInterface;
import com.segway.waterjecttolaser.util.HeadControlHandlerImpl;
import com.segway.waterjecttolaser.view.AutoFitDrawableView;
import com.segway.robot.sdk.base.bind.ServiceBinder;
import com.segway.robot.sdk.locomotion.head.Head;
import com.segway.robot.sdk.locomotion.sbv.Base;
import com.segway.robot.sdk.vision.DTS;
import com.segway.robot.sdk.vision.Vision;
import com.segway.robot.support.control.HeadPIDController;

import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

/**
 * @author jacob
 * @date 5/29/18
 */

public class FollowMePresenter {

    private static final String TAG = "FollowMePresenter";

    private static final int TIME_OUT = 10 * 1000;

    private PresenterChangeInterface mPresenterChangeInterface;
    private ViewChangeInterface mViewChangeInterface;

    private HeadPIDController mHeadPIDController = new HeadPIDController();
    private Vision mVision;
    private Head mHead;
    private Base mBase;

    private boolean isVisionBind;
    private boolean isHeadBind;
    private boolean isBaseBind;

    private DTS mDts;

    /**
     * true means obstacle avoidance function is available, otherwise false
     */
    private boolean isObstacleAvoidanceOpen;
    private PersonTrackingProfile mPersonTrackingProfile;

    private long startTime;

    private RobotStateType mCurrentState;

    //private Pose2D sPose;
    //private Timer timer;

    private Set<String> rDistance;
    private Set<String> rAngle;

    public enum RobotStateType {
        INITIATE_DETECT, TERMINATE_DETECT, INITIATE_TRACK, TERMINATE_TRACK;
    }

    /*
    private TimerTask doAsynchronousTask = new TimerTask() {
        @Override
        public void run() {
            Pose2D curPose = mBase.getOdometryPose(-1);
            if(!curPose.equals(sPose)) {
                float dPathX = curPose.getX() - sPose.getX();
                float dPathY = curPose.getY() - sPose.getY();
                float dist = (float) Math.hypot(dPathX, dPathY);
                float theta = (float) Math.atan2(dPathY, dPathX);

                rDistance.add(String.valueOf(dist));
                rAngle.add(String.valueOf(theta));

                sPose = curPose;
            }
        }
    };*/

    public FollowMePresenter(PresenterChangeInterface mPresenterChangeInterface, ViewChangeInterface mViewChangeInterface) {
        this.mPresenterChangeInterface = mPresenterChangeInterface;
        this.mViewChangeInterface = mViewChangeInterface;
    }

    public void startPresenter() {
        mVision = Vision.getInstance();
        mHead = Head.getInstance();
        mBase = Base.getInstance();

        mVision.bindService(CustomApplication.getContext(), mVisionBindStateListener);
        mHead.bindService(CustomApplication.getContext(), mHeadBindStateListener);
        mBase.bindService(CustomApplication.getContext(), mBaseBindStateListener);

        /**
         * the second parameter is the distance between loomo and the followed target. must > 1.0f
         */
        mPersonTrackingProfile = new PersonTrackingProfile(3, 1.0f);

        try {
            setObstacleAvoidanceOpen(true);
        }catch (Exception ex){ex.printStackTrace();}

        try{
            rDistance = new HashSet<>();
            rAngle = new HashSet<>();
        }catch (Exception ex){ex.printStackTrace();}
    }

    public void stopPresenter() {
        if (mDts != null) {
            mDts.stop();
            mDts = null;
        }
        try {
            mVision.unbindService();
            mHead.unbindService();
            mBase.unbindService();
        }catch (Exception ex){ex.printStackTrace();}
        try{
            mHeadPIDController.stop();
        }catch (Exception ex){ex.printStackTrace();}
    }


    /******************************************* button actions ***********************************/


    public void actionInitiateDetect() {
        if (mCurrentState == RobotStateType.INITIATE_DETECT) {
            return;
        } else if (mCurrentState == RobotStateType.INITIATE_TRACK) {
            mPresenterChangeInterface.showToast("Please terminate tracking first.");
            return;
        }
        startTime = System.currentTimeMillis();
        mCurrentState = RobotStateType.INITIATE_DETECT;
        mDts.startDetectingPerson(mPersonDetectListener);
        mPresenterChangeInterface.showToast("initiate detecting....");
    }

    public void actionTerminateDetect() {
        if (mCurrentState == RobotStateType.INITIATE_DETECT) {
            mCurrentState = RobotStateType.TERMINATE_DETECT;
            mDts.stopDetectingPerson();
            mPresenterChangeInterface.showToast("terminate detecting....");
        } else {
            mPresenterChangeInterface.showToast("The app is not in detecting mode yet.");
        }
    }



    public void actionInitiateTrack() {
        if (mCurrentState == RobotStateType.INITIATE_TRACK) {
            return;
        } else if (mCurrentState == RobotStateType.INITIATE_DETECT) {
            mPresenterChangeInterface.showToast("Please terminate detecting first.");
            return;
        }
        startTime = System.currentTimeMillis();
        mCurrentState = RobotStateType.INITIATE_TRACK;
        if (isObstacleAvoidanceOpen) {
            // Loomo will detect obstacles and avoid them when invoke startPlannerPersonTracking()
            mDts.startPlannerPersonTracking(null, mPersonTrackingProfile, 60 * 1000 * 1000, mPersonTrackingWithPlannerListener);
        } else {
            // Without obstacle detection and avoidance
            mDts.startPersonTracking(null, 60 * 1000 * 1000, mPersonTrackingListener);
        }
        mPresenterChangeInterface.showToast("initiate tracking....");

        //sPose = mBase.getOdometryPose(-1);
        //timer = new Timer();
        //timer.schedule(doAsynchronousTask, 0, 3*1000);

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(CustomApplication.getContext());
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean("isArrived",false);
        editor.apply();
    }

    public void actionTerminateTrack() {
        if (mCurrentState == RobotStateType.INITIATE_TRACK) {
            mCurrentState = RobotStateType.TERMINATE_TRACK;
            if (isObstacleAvoidanceOpen) {
                mDts.stopPlannerPersonTracking();
            } else {
                mDts.stopPersonTracking();
            }
            mPresenterChangeInterface.showToast("terminate tracking....");

            /*
            Pose2D curPose = mBase.getOdometryPose(-1);
            if(!curPose.equals(sPose)) {
                float dPathX = curPose.getX() - sPose.getX();
                float dPathY = curPose.getY() - sPose.getY();
                float dist = (float) Math.hypot(dPathX, dPathY);
                float theta = (float) Math.atan2(dPathY, dPathX);

                rDistance.add(String.valueOf(dist));
                rAngle.add(String.valueOf(theta));
            }*/
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(CustomApplication.getContext());
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putStringSet("robotDistance",rDistance);
            editor.putStringSet("robotAngle",rAngle);
            editor.putBoolean("isArrived",true);
            editor.apply();
        } else {
            mPresenterChangeInterface.showToast("The app is not in tracking mode yet.");
        }

        //timer.cancel();
    }

    /**************************  detecting and tracking listeners   *****************************/

    private PersonDetectListener mPersonDetectListener = new PersonDetectListener() {
        @Override
        public void onPersonDetected(DTSPerson[] person) {
            if (person == null || person.length == 0) {
                if (System.currentTimeMillis() - startTime > TIME_OUT) {
                    resetHead();
                }
                return;
            }
            startTime = System.currentTimeMillis();
            mPresenterChangeInterface.drawPersons(person);
            mPresenterChangeInterface.showToast("Person Detected!");
            if (isServicesAvailable()) {
                mHead.setMode(Head.MODE_ORIENTATION_LOCK);
                mHeadPIDController.updateTarget(person[0].getTheta(), person[0].getDrawingRect(), 480);
            }
        }

        @Override
        public void onPersonDetectionResult(DTSPerson[] person) {

        }

        @Override
        public void onPersonDetectionError(int errorCode, String message) {
            mCurrentState = null;
            mPresenterChangeInterface.showToast("PersonDetectListener: " + message);
        }
    };

    /**
     * person tracking without obstacle avoidance
     */
    private PersonTrackingListener mPersonTrackingListener = new PersonTrackingListener() {
        @Override
        public void onPersonTracking(DTSPerson person) {
            if (person == null) {
                if (System.currentTimeMillis() - startTime > TIME_OUT) {
                    resetHead();
                }
                return;
            }
            startTime = System.currentTimeMillis();
            mPresenterChangeInterface.drawPerson(person);

            if (isServicesAvailable()) {
                mHead.setMode(Head.MODE_ORIENTATION_LOCK);
                mHeadPIDController.updateTarget(person.getTheta(), person.getDrawingRect(), 480);

                mBase.setControlMode(Base.CONTROL_MODE_FOLLOW_TARGET);
                float personDistance = person.getDistance();
                // There is a bug in DTS, while using person.getDistance(), please check the result
                // The correct distance is between 0.35 meters and 5 meters
                if (personDistance > 0.35 && personDistance < 5) {
                    float followDistance = (float) (personDistance - 1.2);
                    float theta = person.getTheta();
                    mBase.updateTarget(followDistance, theta);
                }
            }
        }

        @Override
        public void onPersonTrackingResult(DTSPerson person) { }

        @Override
        public void onPersonTrackingError(int errorCode, String message) {
            mCurrentState = null;
            mPresenterChangeInterface.showToast("PersonTrackingListener: " + message);
        }
    };

    /**
     * person tracking with obstacle avoidance
     */
    private PersonTrackingWithPlannerListener mPersonTrackingWithPlannerListener = new PersonTrackingWithPlannerListener() {
        @Override
        public void onPersonTrackingWithPlannerResult(DTSPerson person, BaseControlCommand baseControlCommand) {
            if (person == null) {
                if (System.currentTimeMillis() - startTime > TIME_OUT) {
                    resetHead();
                }
                return;
            }

            startTime = System.currentTimeMillis();
            mPresenterChangeInterface.drawPerson(person);
            mHead.setMode(Head.MODE_ORIENTATION_LOCK);
            mHeadPIDController.updateTarget(person.getTheta(), person.getDrawingRect(), 480);

            switch (baseControlCommand.getFollowState()) {
                case BaseControlCommand.State.NORMAL_FOLLOW:
                    setBaseVelocity(baseControlCommand.getLinearVelocity(), baseControlCommand.getAngularVelocity());
                    rDistance.add(String.valueOf(baseControlCommand.getLinearVelocity()));
                    rAngle.add(String.valueOf(baseControlCommand.getAngularVelocity()));
                    break;
                case BaseControlCommand.State.HEAD_FOLLOW_BASE:
                    mBase.setControlMode(Base.CONTROL_MODE_FOLLOW_TARGET);
                    mBase.updateTarget(0, person.getTheta());
                    break;
                case BaseControlCommand.State.SENSOR_ERROR:
                    setBaseVelocity(0, 0);
                    break;
            }
        }

        @Override
        public void onPersonTrackingWithPlannerError(int errorCode, String message) {
            mCurrentState = null;
            mPresenterChangeInterface.showToast("PersonTrackingWithPlannerListener: " + message);
        }
    };

    private void setBaseVelocity(float linearVelocity, float angularVelocity) {
        mBase.setControlMode(Base.CONTROL_MODE_RAW);
        mBase.setLinearVelocity(linearVelocity);
        mBase.setAngularVelocity(angularVelocity);
    }

    /***********************************  switch mode  *******************************************/

    public void setObstacleAvoidanceOpen(boolean isObstacleAvoidanceOpen) {
        this.isObstacleAvoidanceOpen = isObstacleAvoidanceOpen;
    }

    public boolean getObstacleAvoidanceOpen() {
        closeDetectorTrack();
        return isObstacleAvoidanceOpen;
    }

    private void closeDetectorTrack() {
        mDts.stopDetectingPerson();
        mDts.stopPersonTracking();
        mDts.stopPlannerPersonTracking();
        mCurrentState = null;
    }


    /***************************************** bind services **************************************/

    private ServiceBinder.BindStateListener mVisionBindStateListener = new ServiceBinder.BindStateListener() {
        @Override
        public void onBind() {
            isVisionBind = true;
            mDts = mVision.getDTS();
            mDts.setVideoSource(DTS.VideoSource.CAMERA);
            AutoFitDrawableView autoFitDrawableView = mViewChangeInterface.getAutoFitDrawableView();
            Surface surface = new Surface(autoFitDrawableView.getPreview().getSurfaceTexture());
            mDts.setPreviewDisplay(surface);
            mDts.start();
            checkAvailable();
        }

        @Override
        public void onUnbind(String reason) {
            isVisionBind = false;
            mPresenterChangeInterface.showToast("Vision service: " + reason);
        }
    };

    private ServiceBinder.BindStateListener mHeadBindStateListener = new ServiceBinder.BindStateListener() {
        @Override
        public void onBind() {
            isHeadBind = true;
            resetHead();
            mHeadPIDController.init(new HeadControlHandlerImpl(mHead));
            mHeadPIDController.setHeadFollowFactor(1.0f);
            checkAvailable();
        }

        @Override
        public void onUnbind(String reason) {
            isHeadBind = false;
            mPresenterChangeInterface.showToast("Head service: " + reason);
        }
    };

    private ServiceBinder.BindStateListener mBaseBindStateListener = new ServiceBinder.BindStateListener() {
        @Override
        public void onBind() {
            isBaseBind = true;
            checkAvailable();
        }

        @Override
        public void onUnbind(String reason) {
            isBaseBind = false;
            mPresenterChangeInterface.showToast("Base service: " + reason);
        }
    };

    public boolean isServicesAvailable() {
        return isVisionBind && isHeadBind && isBaseBind;
    }

    private void checkAvailable() {
        if (isServicesAvailable()) {
            mPresenterChangeInterface.dismissLoading();
        }
    }

    /**
     * reset head when timeout
     */
    private void resetHead() {
        mHead.setMode(Head.MODE_SMOOTH_TACKING);
        mHead.setWorldYaw(0);
        mHead.setWorldPitch(0.7f);
    }
}
