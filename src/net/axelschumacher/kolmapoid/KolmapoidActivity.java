package net.axelschumacher.kolmapoid;

import java.util.concurrent.Semaphore;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;

public class KolmapoidActivity extends MapActivity {
	private static final String TAG = "KolmapoidActivity";
	public static final int maxComplexity = 10;

	public MapController mapController;
	public MapView mapView;
	public LocationManager locationManager;
	public Location location;
	public MyLocationOverlay myLocationOverlay;
	public Semaphore placesLock;
	public UpdatePlacesTask updatePlacesTask = null;
	public Bitmap bitmapToBeDisplayed = null;
	public GeoPoint center = null;

	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		setContentView(R.layout.main); // bind the layout to the activity
		placesLock = new Semaphore(1);

		// Configure the Map
		mapView = (MapView) findViewById(R.id.mapview);
		mapView.setBuiltInZoomControls(true);
		mapController = mapView.getController();
		mapController.setZoom(20); // Zoom 1 is world view
		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0,
				0, new GeoUpdateHandler());

		myLocationOverlay = new MyLocationOverlay(this, mapView);
		mapView.getOverlays().add(myLocationOverlay);

		myLocationOverlay.runOnFirstFix(new Runnable() {
			public void run() {
				mapView.getController().animateTo(
						myLocationOverlay.getMyLocation());
			}
		});
		location = locationManager.getLastKnownLocation(locationManager
				.getBestProvider(new Criteria(), false));
		Log.d(TAG, "Current location: " + location.getLatitude() + " "
				+ location.getLongitude());

		// createMarker();
		mapView.getOverlays().add(new MyOverlay());

	}

	public GeoPoint getCenter() {
		return mapView.getMapCenter();
	}

	static public int getDistance(GeoPoint p1, GeoPoint p2) {
		float distance[] = { 0 };
		Location.distanceBetween(
				(double) (p1.getLatitudeE6()) / (double) (1e6),
				(double) (p1.getLongitudeE6()) / (double) (1e6),
				(double) (p2.getLatitudeE6()) / (double) (1e6),
				(double) (p2.getLongitudeE6()) / (double) (1e6), distance);
		return (int) (distance[0]);
	}

	public int getRadius() {
		GeoPoint topLeftPoint = mapView.getProjection().fromPixels(0, 0);
		GeoPoint currentPoint = getCenter();
		int radius = getDistance(topLeftPoint, currentPoint);
		Log.d(TAG, "Center: " + mapView.getMapCenter());
		Log.d(TAG, "getRadius: " + radius);
		return radius;
	}

	@Override
	protected boolean isRouteDisplayed() {
		return false;
	}

	public class GeoUpdateHandler implements LocationListener {

		public void onLocationChanged(Location location) {
			// int lat = (int) (location.getLatitude() * 1E6);
			// int lng = (int) (location.getLongitude() * 1E6);
			// GeoPoint point = new GeoPoint(lat, lng);
			// createMarker();
			// mapController.animateTo(point); //
			// mapController.setCenter(point);
			location = locationManager.getLastKnownLocation(locationManager
					.getBestProvider(new Criteria(), false));
		}

		public void onProviderDisabled(String provider) {
		}

		public void onProviderEnabled(String provider) {
		}

		public void onStatusChanged(String provider, int status, Bundle extras) {
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		menu.add(0, 0, 0, "Search places");

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case 0:
			updatePlaces();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onResume() {
		super.onResume();
		myLocationOverlay.enableMyLocation();
	}

	/**
	 * Updates places in background
	 */
	private void updatePlaces() {

		if (updatePlacesTask != null) {
			updatePlacesTask.cancel(true);
		}
		center = getCenter();
		updatePlacesTask = new UpdatePlacesTask(this);
		updatePlacesTask.execute((Object[]) (null));
	}

	@Override
	protected void onPause() {
		super.onResume();
		myLocationOverlay.disableMyLocation();
	}

	class MyOverlay extends Overlay {

		public MyOverlay() {

		}

		public void draw(Canvas bigCanvas, MapView mapv, boolean shadow) {
			super.draw(bigCanvas, mapv, shadow);

			if (bitmapToBeDisplayed == null) {
				return;
			}

			if (center == null) {
				return;
			}

			Projection projection = mapv.getProjection();

			Point pC = new Point();
			projection.toPixels(center, pC);
			GeoPoint actualCenter = getCenter();
			Point pAC = new Point();
			projection.toPixels(actualCenter, pAC);
			if ((pC.x - pAC.x) * (pC.x - pAC.x) + (pC.y - pAC.y)
					* (pC.y - pAC.y) < 500) {
				try {
					placesLock.acquire();
					Paint bigPaint = new Paint();
					bigPaint.setAlpha(100);
					bigCanvas.drawBitmap(bitmapToBeDisplayed, 0, 0, bigPaint);
					placesLock.release();
				} catch (InterruptedException e) {
					Log.d(TAG, e.getMessage());
				}
			}
			else {
				Paint paint = new Paint();
				paint.setColor(Color.RED);
				paint.setTextSize(25);
				bigCanvas.drawCircle(pC.x, pC.y, 5, paint);
				bigCanvas.drawText("Center me !", pC.x+5, pC.y, paint);
			}
		}
	}
}