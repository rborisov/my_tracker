/*

 * Copyright 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.android.apps.mytracks.samples.api;

import com.google.android.apps.mytracks.content.MyTracksProviderUtils;
import com.google.android.apps.mytracks.content.Track;
import com.google.android.apps.mytracks.content.Waypoint;
import com.google.android.apps.mytracks.services.ITrackRecordingService;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;

/**
 * An activity to access MyTracks content provider and service.
 *
 * Note you must first install MyTracks before installing this app.
 *
 * You also need to enable third party application access inside MyTracks
 * MyTracks -> menu -> Settings -> Sharing -> Allow access
 *
 * @author Jimmy Shih
 */
public class MainActivity extends Activity {

  private static final String TAG = MainActivity.class.getSimpleName();

  // utils to access the MyTracks content provider
  private MyTracksProviderUtils myTracksProviderUtils;

  // display output from the MyTracks content provider
  private TextView outputTextView, latTV, lonTV, timeTV, accuracyTV, altitudeTV, speedTV, bearingTV;

  // MyTracks service
  private ITrackRecordingService myTracksService;
  
  // intent to access the MyTracks service
  private Intent intent;
  
  private Timer timer;

  // connection to the MyTracks service
  private ServiceConnection serviceConnection = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
      myTracksService = ITrackRecordingService.Stub.asInterface(service);
    }

    @Override
    public void onServiceDisconnected(ComponentName className) {
      myTracksService = null;
    }
  };

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);

    // for the MyTracks content provider
    myTracksProviderUtils = MyTracksProviderUtils.Factory.get(this);
    outputTextView = (TextView) findViewById(R.id.output);
    latTV = (TextView) findViewById(R.id.lat);
    lonTV = (TextView) findViewById(R.id.lon);
    timeTV = (TextView) findViewById(R.id.time);
    accuracyTV = (TextView) findViewById(R.id.accuracy);
    altitudeTV = (TextView) findViewById(R.id.altitude);
    speedTV = (TextView) findViewById(R.id.speed);
    bearingTV = (TextView) findViewById(R.id.bearing);

    Button addWaypointsButton = (Button) findViewById(R.id.add_waypoints_button);
    addWaypointsButton.setOnClickListener(new View.OnClickListener() {
	@Override
    public void onClick(View v) {
	    List<Track> tracks = myTracksProviderUtils.getAllTracks();
	    Calendar now = Calendar.getInstance();   
	    for (Track track : tracks) {
	      Waypoint waypoint = new Waypoint();
	      waypoint.setTrackId(track.getId());
	      waypoint.setName(now.getTime().toLocaleString());
	      waypoint.setDescription(now.getTime().toLocaleString());
	      myTracksProviderUtils.insertWaypoint(waypoint);
	    }   	
        }
    });
    
    // for the MyTracks service
    intent = new Intent();
    ComponentName componentName = new ComponentName(
        getString(R.string.mytracks_service_package), getString(R.string.mytracks_service_class));
    intent.setComponent(componentName);

    Button startRecordingButton = (Button) findViewById(R.id.start_recording_button);
    startRecordingButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (myTracksService != null) {
          try {
            myTracksService.startNewTrack();
          } catch (RemoteException e) {
            Log.e(TAG, "RemoteException", e);
          }
          // rborisov start posting every X seconds
          timer = new Timer();
          timer.schedule(new TimerTask()
          {
              @SuppressWarnings("unused")
              @Override
              public void run()
              {
//                Track lastTrack;
                Location loc;
//                lastTrack = myTracksProviderUtils.getLastTrack();
//                outputTextView.append(" -" + lastTrack.getId() + "- ");
                loc = myTracksProviderUtils.getLastValidTrackPoint();//lastTrack.getId());
                if (loc != null) {
              
                  // Create a new HttpClient and Post Header
                  HttpClient httpclient = new DefaultHttpClient();
                  HttpPost httppost = new HttpPost("http://track.rborisov.me/trackmspost.php");
  
                  try {
                      // Add your data
                      List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
                      nameValuePairs.add(new BasicNameValuePair("lat", String.valueOf(loc.getLatitude())));
                      nameValuePairs.add(new BasicNameValuePair("lon", String.valueOf(loc.getLongitude())));
                      nameValuePairs.add(new BasicNameValuePair("time", String.valueOf(loc.getTime())));
                      nameValuePairs.add(new BasicNameValuePair("accuracy", String.valueOf(loc.getAccuracy())));
                      nameValuePairs.add(new BasicNameValuePair("altitude", String.valueOf(loc.getAltitude())));
                      nameValuePairs.add(new BasicNameValuePair("speed", String.valueOf(loc.getSpeed())));
                      nameValuePairs.add(new BasicNameValuePair("bearing", String.valueOf(loc.getBearing())));
                      httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
  
                      // Execute HTTP Post Request
                      HttpResponse response = httpclient.execute(httppost);
                      
                  } catch (ClientProtocolException e) {
                      Log.e(TAG, "ClientProtocolException", e);
                  } catch (IOException e) {
                    Log.e(TAG, "IOException", e);
                  }
                }
               }
          }, 0, 30*1000);
          // rborisov end
        }
      }
    });

    Button stopRecordingButton = (Button) findViewById(R.id.stop_recording_button);
    stopRecordingButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (myTracksService != null) {
          try {
            myTracksService.endCurrentTrack();
            timer.cancel();
            timer.purge();
          } catch (RemoteException e) {
            Log.e(TAG, "RemoteException", e);
          }
        }
      }
    });
  }

  @Override
  protected void onStart() {
    super.onStart();

    // use the MyTracks content provider to get all the tracks
    List<Track> tracks = myTracksProviderUtils.getAllTracks();
    for (Track track : tracks) {
      outputTextView.append(track.getId() + " ");
      
    }
    Location loc = myTracksProviderUtils.getLastValidTrackPoint();
    lonTV.setText(loc.toString());

    // start and bind the MyTracks service
    startService(intent);
    bindService(intent, serviceConnection, 0);
  }

  @Override
  protected void onStop() {
    super.onStop();
    
    // unbind and stop the MyTracks service
    if (myTracksService != null) {
      unbindService(serviceConnection);
    }
    stopService(intent);
  }
}
