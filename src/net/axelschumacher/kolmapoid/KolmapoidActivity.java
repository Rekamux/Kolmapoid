package net.axelschumacher.kolmapoid;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.Overlay;

public class KolmapoidActivity extends MapActivity {
	private static final String TAG = "KolmapoidActivity";
	public static final double log10 = Math.log(10);
	public static final int maxComplexity = 10;

	public MapController mapController;
	public MapView mapView;
	public LocationManager locationManager;
	public Location location;
	public MyLocationOverlay myLocationOverlay;
	public ArrayList<PlaceData> places;
	public ArrayList<PlaceData> placesToBeDisplayed;
	public Semaphore placesLock;
	public UpdatePlacesTask updatePlacesTask = null;

	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		setContentView(R.layout.main); // bind the layout to the activity
		places = new ArrayList<PlaceData>();
		placesToBeDisplayed = new ArrayList<PlaceData>();
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
			updatePlaces();
		}

		public void onProviderDisabled(String provider) {
		}

		public void onProviderEnabled(String provider) {
		}

		public void onStatusChanged(String provider, int status, Bundle extras) {
		}
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
		updatePlacesTask = new UpdatePlacesTask(this);
		updatePlacesTask.execute((Object[]) (null));
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		int actionType = ev.getAction();
		switch (actionType) {
		case MotionEvent.ACTION_DOWN:
			updatePlaces();
		}

		return super.dispatchTouchEvent(ev);
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
			Bitmap bitmap = Bitmap.createBitmap(mapv.getWidth(), mapv.getHeight(), Bitmap.Config.ARGB_4444);
			Canvas canvas = new Canvas(bitmap);
			int radius = mapView.getHeight() / 2;

			Paint paint = new Paint();
			paint.setStyle(Paint.Style.FILL);

			Paint textPaint = new Paint();
			textPaint.setColor(Color.BLUE);
			textPaint.setTextSize(25);
			textPaint.setStrokeWidth(5);

			// Draw the color circles
			for (int i = 0; i < 10; i++) {
				int distanceBits = 10 - i;
				Log.d(TAG, "Circle " + i + " bits " + distanceBits);
				for (int j = 0; j < placesToBeDisplayed.size(); j++) {
					PlaceData place = placesToBeDisplayed.get(j);
					int complexity = place.getNbBits() + distanceBits;
					int ratio = complexity * 255 / maxComplexity;
					Log.d(TAG, "Complexity: "+complexity+" ratio: "+ratio);
					paint.setColor(Color.rgb(ratio, 255 - ratio, 0));
					canvas.drawCircle(place.getX(), place.getY(), radius
							/ (i+1), paint);
				}
			}

			// Draw the places
			try {
				placesLock.acquire();
				for (int i = 0; i < placesToBeDisplayed.size(); i++) {
					PlaceData place = placesToBeDisplayed.get(i);
					canvas.drawPoint(place.getX(), place.getY(), textPaint);
					canvas.drawText(place.getName(), place.getX(), place.getY(),
							textPaint);
				}
				placesLock.release();
			} catch (InterruptedException e) {
				Log.d(TAG, e.getMessage());
			}
			Paint bigPaint = new Paint();
			bigPaint.setAlpha(100);
			bigCanvas.drawBitmap(bitmap, 0, 0, bigPaint);
		}
	}
}