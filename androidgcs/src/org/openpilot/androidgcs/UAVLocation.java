/**
 ******************************************************************************
 * @file       UAVLocation.java
 * @author     The OpenPilot Team, http://www.openpilot.org Copyright (C) 2012.
 * @brief      Display the UAV location on google maps
 * @see        The GNU Public License (GPL) Version 3
 *****************************************************************************/
/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

package org.openpilot.androidgcs;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;

import org.openpilot.androidgcs.fragments.ObjectManagerFragment;
import org.openpilot.androidgcs.telemetry.OPTelemetryService;
import org.openpilot.androidgcs.telemetry.OPTelemetryService.LocalBinder;
import org.openpilot.androidgcs.telemetry.OPTelemetryService.TelemTask;
import org.openpilot.uavtalk.UAVObject;
import org.openpilot.uavtalk.UAVObjectManager;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;

public class UAVLocation extends MapActivity
{
	private final String TAG = "UAVLocation";
	private static int LOGLEVEL = 0;
//	private static boolean WARN = LOGLEVEL > 1;
	private static boolean DEBUG = LOGLEVEL > 0;

	//private MapView mapView;
	private MyCustomMapView mapView;
	private MapController mapController;
	private GeoPoint mContextMenuGeoPoint = null;
	
	UAVObjectManager objMngr;
    boolean mBound = false;
    boolean mConnected = false;
    BroadcastReceiver connectedReceiver;
	org.openpilot.androidgcs.telemetry.OPTelemetryService.LocalBinder binder;

    GeoPoint homeLocation;
    GeoPoint uavLocation;

	boolean towerEnabled;
	boolean gpsEnabled;
	
	LocationManager gpsLocationManager;
	LocationManager towerLocationManager;
	MyLocationListener gpsLocationListener;
	MyLocationListener towerLocationListener;
	private TextView myLongitude, myLatitude, myAltitude;
    
    @Override public void onCreate(Bundle icicle) {
    	super.onCreate(icicle);
        setContentView(R.layout.mycustommapview);
        mapView = (MyCustomMapView)findViewById(R.id.mapview);
		mapController = mapView.getController();
	    registerForContextMenu(mapView);

		mapView.displayZoomControls(true);
		Double lat = 37.422006*1E6;
		Double lng = -122.084095*1E6;
		homeLocation = new GeoPoint(lat.intValue(), lng.intValue());
		uavLocation = homeLocation;
		//mapController.setCenter(homeLocation);
		mapController.setZoom(16);

		List<Overlay> overlays = mapView.getOverlays();
		UAVOverlay myOverlay = new UAVOverlay();
		overlays.add(myOverlay);
		
		  myLongitude = (TextView)findViewById(R.id.longitude);
		  myLatitude = (TextView)findViewById(R.id.latitude);
		  myAltitude = (TextView)findViewById(R.id.altitude);

		MyLocationOverlay myLocationOverlay = new MyLocationOverlay(this, mapView);
		myLocationOverlay.enableMyLocation();
		myLocationOverlay.enableCompass();
		overlays.add(myLocationOverlay);

		mapView.postInvalidate();
		gpsLocationListener = new MyLocationListener();
		towerLocationListener = new MyLocationListener();
		gpsLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		towerLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

		CheckTowerAndGpsStatus();
		if(gpsEnabled)
		{
			gpsLocationManager.requestLocationUpdates( LocationManager.GPS_PROVIDER, 0, 0, gpsLocationListener);
	
			  //Get the current location in start-up
			Location last = gpsLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
			if(last != null)
			{
			  GeoPoint initGeoPoint = new GeoPoint(
			   (int)(last.getLatitude()*1000000),
			   (int)(last.getLongitude()*1000000));			  
			   CenterLocation(initGeoPoint,(last.getAltitude()));
			}
		}
		mapView.setOnLongpressListener(new MyCustomMapView.OnLongpressListener() {
        public void onLongpress(final MapView view, final GeoPoint longpressLocation) {
        	mContextMenuGeoPoint = longpressLocation;
            runOnUiThread(new Runnable() {
            public void run() {
                // Insert your longpress action here
            	//Toast.makeText(mapView.getContext(), "You pressed here: Lat:", Toast.LENGTH_LONG).show();
            	openContextMenu(view);
            }
        });
        }
    });
    }

    private void CenterLocation(GeoPoint centerGeoPoint, double Altitude)
    {
    	mapController.animateTo(centerGeoPoint);
     
    
	     myLongitude.setText("Longitude: "+
	      String.valueOf((float)centerGeoPoint.getLongitudeE6()/1000000)
	      );
	     myLatitude.setText("Latitude: "+
	    	      String.valueOf((float)centerGeoPoint.getLatitudeE6()/1000000)
	    	      );
	     myAltitude.setText("Altitude: "+
	    	      String.valueOf(Altitude)
	    	      );
    };

	private void CheckTowerAndGpsStatus() {
		towerEnabled = towerLocationManager
				.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
		gpsEnabled = gpsLocationManager
				.isProviderEnabled(LocationManager.GPS_PROVIDER);
	}
    
    @Override
	protected boolean isRouteDisplayed() {
		// IMPORTANT: This method must return true if your Activity // is displaying driving directions. Otherwise return false.
		return false;
	}
	
	  @Override
	  public void onCreateContextMenu(ContextMenu menu, View v,
	      ContextMenuInfo menuInfo) {
	    super.onCreateContextMenu(menu, v, menuInfo);
	
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.map_menu, menu);
	  }
	  
	  @Override
	  public boolean onContextItemSelected(MenuItem item) {
		  int lat = mContextMenuGeoPoint.getLatitudeE6();
		  int lon = mContextMenuGeoPoint.getLongitudeE6();
	      switch (item.getItemId()) {
		      case R.id.poi1:

		        return true;
		      case R.id.poi2:
		    	  Toast.makeText(mapView.getContext(), "You pressed here: Lat:" + lat/1000000.0 + " Lon:" + lon/1000000.0, Toast.LENGTH_LONG).show();
		        return true;
			case R.id.view1:
				mapView.setSatellite(true);
				mapView.invalidate();
		        return true;
			case R.id.view2:	
				mapView.setSatellite(false);
				mapView.invalidate();
		        return true;
			default:
				return super.onContextItemSelected(item);
	      }
	  }

	  
	  /* Class My Location Listener */

	  public class MyLocationListener implements LocationListener
	  {
		  @Override
		  public void onLocationChanged(Location loc)
		  {
			GeoPoint myGeoPoint = new GeoPoint(
				(int)(loc.getLatitude()*1000000),
				(int)(loc.getLongitude()*1000000));
			
			CenterLocation(myGeoPoint,loc.getAltitude());
		  }
		  @Override
		  public void onProviderDisabled(String provider)
		  {
			  Toast.makeText( getApplicationContext(),
					  "Gps Disabled",
					  Toast.LENGTH_SHORT ).show();
		  }
		  @Override
		  public void onProviderEnabled(String provider)
		  {
			  Toast.makeText( getApplicationContext(),
					  "Gps Enabled",
					  Toast.LENGTH_SHORT).show();
		  }

		  @Override
		  public void onStatusChanged(String provider, int status, Bundle extras)
		  {
		  }
	  }/* End of Class MyLocationListener */
	  
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
			case KeyEvent.KEYCODE_DPAD_UP:
				mapController.zoomIn();
				break;
			case KeyEvent.KEYCODE_DPAD_DOWN:
				mapController.zoomOut();
				break;
			case KeyEvent.KEYCODE_DPAD_LEFT:
				mapController.setZoom(17);
				mapView.setSatellite(true);
				mapView.invalidate();
				break;
			case KeyEvent.KEYCODE_DPAD_RIGHT:	
				mapController.setZoom(17);
				mapView.setSatellite(false);
				mapView.invalidate();	
				break;
				
		}
		return super.onKeyDown(keyCode, event);
	}

	public class UAVOverlay extends Overlay {
		Bitmap homeSymbol = BitmapFactory.decodeResource(getResources(), R.drawable.ic_home);
		Bitmap uavSymbol = BitmapFactory.decodeResource(getResources(), R.drawable.ic_uav);
		@Override
		public void draw(Canvas canvas, MapView mapView, boolean shadow) {

			Projection projection = mapView.getProjection();

			if (shadow == false) {

				Point myPoint = new Point();
				projection.toPixels(uavLocation, myPoint);

				//// Draw UAV
				// Create and setup your paint brush
				Paint paint = new Paint();
				paint.setARGB(250, 255, 0, 0);
				paint.setAntiAlias(true);
				paint.setFakeBoldText(true);

				// Draw on the canvas
				canvas.drawBitmap(uavSymbol, myPoint.x - uavSymbol.getWidth() / 2,
						myPoint.y - uavSymbol.getHeight() / 2, paint);
				canvas.drawText("UAV", myPoint.x+uavSymbol.getWidth() / 2, myPoint.y, paint);

				//// Draw Home
				myPoint = new Point();
				projection.toPixels(homeLocation, myPoint);

				// Create and setup your paint brush
				paint.setARGB(250, 0, 0, 0);
				paint.setAntiAlias(true);
				paint.setFakeBoldText(true);

				canvas.drawBitmap(homeSymbol, myPoint.x - homeSymbol.getWidth() / 2,
						myPoint.y - homeSymbol.getHeight() / 2, paint);
				canvas.drawText("Home", myPoint.x+homeSymbol.getWidth(), myPoint.y, paint);

			}
		}

		@Override
		public boolean onTap(GeoPoint point, MapView mapView1) {
			// Return true if screen tap is handled by this overlay
			return false;
		}
	}

	void onOPConnected() {
		UAVObject obj = objMngr.getObject("HomeLocation");
		registerObjectUpdates(obj);
		objectUpdated(obj);

		obj = objMngr.getObject("PositionActual");
		registerObjectUpdates(obj);
		objectUpdated(obj);
	}

	private GeoPoint getUavLocation() {
		UAVObject pos = objMngr.getObject("PositionActual");
		if (pos == null)
			return new GeoPoint(0,0);

		UAVObject home = objMngr.getObject("HomeLocation");
		if (home == null)
			return new GeoPoint(0,0);

		double lat, lon, alt;
		lat = home.getField("Latitude").getDouble() / 10.0e6;
		lon = home.getField("Longitude").getDouble() / 10.0e6;
		alt = home.getField("Altitude").getDouble();

		// Get the home coordinates
		double T0, T1;
		T0 = alt+6.378137E6;
		T1 = Math.cos(lat * Math.PI / 180.0)*(alt+6.378137E6);

		// Get the NED coordinates
		double NED0, NED1;
		NED0 = pos.getField("North").getDouble();
		NED1 = pos.getField("East").getDouble();

		// Compute the LLA coordinates
		lat = lat + (NED0 / T0) * 180.0 / Math.PI;
		lon = lon + (NED1 / T1) * 180.0 / Math.PI;

		return new GeoPoint((int) (lat * 1e6), (int) (lon * 1e6));
	}

	void onOPDisconnected() {
		unregisterObjectUpdates();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case R.id.menu_connect:
			binder.openConnection();
			return true;
		case R.id.menu_disconnect:
			binder.stopConnection();
			return true;
		case R.id.menu_settings:
			startActivity(new Intent(this, Preferences.class));
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.options_menu, menu);
		return true;
	}

	@Override
	public void onStart() {
		super.onStart();
		// ObjectManager related stuff (can't inherit standard class)
		connectedReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				Log.d(TAG, "Received intent");
				TelemTask task;
				if(intent.getAction().compareTo(OPTelemetryService.INTENT_ACTION_CONNECTED) == 0) {

					if(binder  == null)
						return;
					if((task = binder.getTelemTask(0)) == null)
						return;
					objMngr = task.getObjectManager();
					mConnected = true;
					onOPConnected();
					Log.d(TAG, "Connected()");
				} else if (intent.getAction().compareTo(OPTelemetryService.INTENT_ACTION_DISCONNECTED) == 0) {
					objMngr = null;
					mConnected = false;
					onOPDisconnected();
					Log.d(TAG, "Disonnected()");
				}
			}
		};

		IntentFilter filter = new IntentFilter();
		filter.addCategory(OPTelemetryService.INTENT_CATEGORY_GCS);
		filter.addAction(OPTelemetryService.INTENT_ACTION_CONNECTED);
		filter.addAction(OPTelemetryService.INTENT_ACTION_DISCONNECTED);
		registerReceiver(connectedReceiver, filter);

		Intent intent = new Intent(this, OPTelemetryService.class);
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
	}

	/**
	 * When stopping disconnect form the service and the broadcast receiver
	 */
	@Override
	public void onStop() {
		super.onStop();
		if (DEBUG) Log.d(TAG, "onStop()");
		unbindService(mConnection);
		unregisterReceiver(connectedReceiver);
		connectedReceiver = null;

		// TODO: The register and unregister probably should move to onPause / onResume
		unregisterObjectUpdates();
	}

	public void onBind() {

	}

	/** Defines callbacks for service binding, passed to bindService() */
	private final ServiceConnection mConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName arg0, IBinder service) {
			// We've bound to LocalService, cast the IBinder and attempt to open a connection
			if (DEBUG) Log.d(TAG,"Service bound");
			mBound = true;
			binder = (LocalBinder) service;

			if(binder.isConnected()) {
				TelemTask task;
				if((task = binder.getTelemTask(0)) != null) {
					objMngr = task.getObjectManager();
					mConnected = true;
					onOPConnected();
				}

			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			mBound = false;
			binder = null;
			mConnected = false;
			objMngr = null;
			objMngr = null;
			mConnected = false;
			onOPDisconnected();
		}
	};

/******* STRAIGHT COPY PASTE FROM ObjectManagerActivity *************/
	/**
	 * Called whenever any objects subscribed to via registerObjects
	 */
	protected void objectUpdated(UAVObject obj) {
		if (obj == null)
			return;
		if (obj.getName().compareTo("HomeLocation") == 0) {
			Double lat = obj.getField("Latitude").getDouble() / 10;
			Double lon = obj.getField("Longitude").getDouble() / 10;
			homeLocation = new GeoPoint(lat.intValue(), lon.intValue());
			mapController.setCenter(homeLocation);
		} else if (obj.getName().compareTo("PositionActual") == 0) {
			uavLocation = getUavLocation();
			mapView.invalidate();
		}
	}

	/**
	 * A message handler and a custom Observer to use it which calls
	 * objectUpdated with the right object type
	 */
	final Handler uavobjHandler = new Handler();
	private class ActivityUpdatedObserver implements Observer  {
		UAVObject obj;
		ActivityUpdatedObserver(UAVObject obj) { this.obj = obj; };
		@Override
		public void update(Observable observable, Object data) {
			uavobjHandler.post(new Runnable() {
				@Override
				public void run() { objectUpdated(obj); }
			});
		}
	};
	private class FragmentUpdatedObserver implements Observer  {
		UAVObject obj;
		ObjectManagerFragment frag;
		FragmentUpdatedObserver(UAVObject obj, ObjectManagerFragment frag) {
			this.obj = obj;
			this.frag = frag;
		};
		@Override
		public void update(Observable observable, Object data) {
			uavobjHandler.post(new Runnable() {
				@Override
				public void run() { frag.objectUpdated(obj); }
			});
		}
	};


	/**
	 * Register an activity to receive updates from this object
	 *
	 * the objectUpdated() method will be called in the original UI thread
	 */
	HashMap<Observer, UAVObject> listeners = new HashMap<Observer,UAVObject>();
	protected void registerObjectUpdates(UAVObject object) {
		Observer o = new ActivityUpdatedObserver(object);
		object.addUpdatedObserver(o);
		listeners.put(o,  object);
	}
	/**
	 * Unregister all the objects connected to this activity
	 */
	protected void unregisterObjectUpdates()
	{
		Set<Observer> s = listeners.keySet();
		Iterator<Observer> i = s.iterator();
		while (i.hasNext()) {
			Observer o = i.next();
			UAVObject obj = listeners.get(o);
			obj.removeUpdatedObserver(o);
		}
		listeners.clear();
	}
	public void registerObjectUpdates(UAVObject object,
			ObjectManagerFragment frag) {
		Observer o = new FragmentUpdatedObserver(object, frag);
		object.addUpdatedObserver(o);
		listeners.put(o, object);
	}
	protected void registerObjectUpdates(List<List<UAVObject>> objects) {
		ListIterator<List<UAVObject>> li = objects.listIterator();
		while(li.hasNext()) {
			ListIterator<UAVObject> li2 = li.next().listIterator();
			while(li2.hasNext())
				registerObjectUpdates(li2.next());
		}
	}

}
