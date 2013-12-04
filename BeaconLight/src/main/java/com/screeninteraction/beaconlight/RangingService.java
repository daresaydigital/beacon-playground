package com.screeninteraction.beaconlight;

import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.widget.EditText;

import com.philips.lighting.hue.listener.PHLightListener;
import com.philips.lighting.hue.sdk.PHHueSDK;
import com.philips.lighting.model.PHBridge;
import com.philips.lighting.model.PHHueError;
import com.philips.lighting.model.PHLight;
import com.philips.lighting.model.PHLightState;
import com.radiusnetworks.ibeacon.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;

public class RangingService extends Service implements IBeaconConsumer {
    protected static final String TAG = "RangingActivity";
    private IBeaconManager iBeaconManager = IBeaconManager.getInstanceForApplication(this);
    private final IBinder mBinder = new BeaconBinder();
    private PHHueSDK mPhHueSDK;
    private BeaconRegion mRegionState = BeaconRegion.OUTSIDE;
    private boolean mIsMonitoring;
    private static final int MAX_HUE=65535;

    public enum BeaconRegion {
        INSIDE,
        OUTSIDE
    }


    @Override
    public void onIBeaconServiceConnect() {
        mPhHueSDK = PHHueSDK.getInstance(getApplicationContext());
        iBeaconManager.setRangeNotifier(new RangeNotifier() {
        @Override 
        public void didRangeBeaconsInRegion(Collection<IBeacon> iBeacons, Region region) {
            if (iBeacons.size() > 0) {
                IBeacon beacon = iBeacons.iterator().next();
                //logToDisplay("The first iBeacon I see is about "+ beacon.getAccuracy()+" meters away.");
                for(BeaconCallbacksListener l : mBeaconListeners) {
                    l.beaconRegistered(beacon);
                }
                if(beacon.getAccuracy() < 1.0d && mRegionState == BeaconRegion.OUTSIDE)
                {
                    updateLight(MAX_HUE / 120);
                    mRegionState = BeaconRegion.INSIDE;
                    for(BeaconCallbacksListener l : mBeaconListeners) {
                        l.enteredRegion();
                    }
                } else if(beacon.getAccuracy() >= 1.0d && mRegionState == BeaconRegion.INSIDE){
                    updateLight(0);
                    mRegionState = BeaconRegion.OUTSIDE;
                    for(BeaconCallbacksListener l : mBeaconListeners) {
                        l.leftRegion();
                    }
                }
            }
        }

        });

        try {
            iBeaconManager.startRangingBeaconsInRegion(new Region("myRangingUniqueId", "e314593b-5ff6-4079-ae89-5ec9dbae2cfd", null, null));
        } catch (RemoteException e) {   }
    }

    private void updateLight(int color) {
        PHBridge bridge = mPhHueSDK.getSelectedBridge();
        if(bridge != null) {
            List<PHLight> allLights = bridge.getResourceCache().getAllLights();
            for (PHLight light : allLights) {
                PHLightState lightState = new PHLightState();
                lightState.setHue(color);
                // To validate your lightstate is valid (before sending to the bridge, you can use:  (a null validState indicates a valid value
                // String validState = lightState.validateState();
                bridge.updateLightState(light, lightState, listener);
            }
        }
    }
    // If you want to handle the response from the bridge, create a PHLightListener object.
    PHLightListener listener = new PHLightListener() {

        @Override
        public void onSuccess() {

        }

        @Override
        public void onStateUpdate(Hashtable<String, String> arg0, List<PHHueError> arg1) {
            Log.w(TAG, "Light has updated");
            for(BeaconCallbacksListener l : mBeaconListeners) {
                l.lightUpdated(0);
            }
        }

        @Override
        public void onError(int arg0, String arg1) {
            for(BeaconCallbacksListener l : mBeaconListeners) {
                l.lightUpdated(0);
            }
        }
    };


    public class BeaconBinder extends Binder {
        RangingService getService() {
            return RangingService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }


    private List<BeaconCallbacksListener> mBeaconListeners = new ArrayList<BeaconCallbacksListener>();

    /** method for clients */
    public void startListening(BeaconCallbacksListener listener) {
        mBeaconListeners.add(listener);
        iBeaconManager.bind(this);
        mIsMonitoring = true;
    }

    public void stopListening(BeaconCallbacksListener listener) {
        mBeaconListeners.remove(listener);
        iBeaconManager.unBind(this);
        mIsMonitoring = false;
    }

    public boolean isMonitoring() {
        return mIsMonitoring;
    }

    public interface BeaconCallbacksListener {
        void beaconRegistered(IBeacon beacon);
        void lightUpdated(int number);
        void lightFailed();
        void enteredRegion();
        void leftRegion();
    }

}
