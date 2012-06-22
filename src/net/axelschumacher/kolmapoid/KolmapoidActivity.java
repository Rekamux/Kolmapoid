package net.axelschumacher.kolmapoid;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Toast;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.Overlay;
import com.google.android.maps.OverlayItem;

public class KolmapoidActivity extends MapActivity {
	private static final String TAG = "KolmapoidActivity";

	private MapController mapController;
	private MapView mapView;
	private LocationManager locationManager;
	private Location location;
	private MyLocationOverlay myLocationOverlay;
	private ArrayList<PlaceData> places;
	private Semaphore placesLock;
	private UpdatePlacesTask updatePlacesTask = null;
	private Bitmap layerPicture = null;

	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		setContentView(R.layout.main); // bind the layout to the activity
		places = new ArrayList<PlaceData>();
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

	private GeoPoint getCenter() {
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

	private int getRadius() {
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
			//int lat = (int) (location.getLatitude() * 1E6);
			//int lng = (int) (location.getLongitude() * 1E6);
			//GeoPoint point = new GeoPoint(lat, lng);
			// createMarker();
			//mapController.animateTo(point); // mapController.setCenter(point);
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
		layerPicture = Bitmap.createBitmap(mapView.getWidth(),
				mapView.getHeight(), Bitmap.Config.ARGB_8888);

		if (updatePlacesTask != null) {
			updatePlacesTask.cancel(true);
		}
		updatePlacesTask = new UpdatePlacesTask(this);
		GeoPoint center = getCenter();
		Object params[] = { places, placesLock,
				center.getLatitudeE6() / 1000000.0,
				center.getLongitudeE6() / 1000000.0, getRadius(), mapView, location,
				layerPicture };
		updatePlacesTask.execute(params);
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		int actionType = ev.getAction();
		switch (actionType) {
		case MotionEvent.ACTION_UP:
			updatePlaces();
		}

		return super.dispatchTouchEvent(ev);
	}

	@Override
	protected void onPause() {
		super.onResume();
		myLocationOverlay.disableMyLocation();
	}

	private static int maxNum = 5;

	public class MyOverlays extends ItemizedOverlay<OverlayItem> {

		private OverlayItem overlays[] = new OverlayItem[maxNum];
		private int index = 0;
		private boolean full = false;
		private Context context;
		private OverlayItem previousoverlay;

		public MyOverlays(Context context, Drawable defaultMarker) {
			super(boundCenterBottom(defaultMarker));
			this.context = context;
		}

		@Override
		protected OverlayItem createItem(int i) {
			return overlays[i];
		}

		@Override
		public int size() {
			if (full) {
				return overlays.length;
			} else {
				return index;
			}

		}

		public void addOverlay(OverlayItem overlay) {
			if (previousoverlay != null) {
				if (index < maxNum) {
					overlays[index] = previousoverlay;
				} else {
					index = 0;
					full = true;
					overlays[index] = previousoverlay;
				}
				index++;
				populate();
			}
			this.previousoverlay = overlay;
		}

		protected boolean onTap(int index) {
			// OverlayItem overlayItem = overlays[index];
			Builder builder = new AlertDialog.Builder(context);
			builder.setMessage("This will end the activity");
			builder.setCancelable(true);
			builder.setPositiveButton("I agree", new OkOnClickListener());
			builder.setNegativeButton("No, no", new CancelOnClickListener());
			AlertDialog dialog = builder.create();
			dialog.show();
			return true;
		};

		private final class CancelOnClickListener implements
				DialogInterface.OnClickListener {
			public void onClick(DialogInterface dialog, int which) {
				Toast.makeText(context, "You clicked yes", Toast.LENGTH_LONG)
						.show();
			}
		}

		private final class OkOnClickListener implements
				DialogInterface.OnClickListener {
			public void onClick(DialogInterface dialog, int which) {
				Toast.makeText(context, "You clicked no", Toast.LENGTH_LONG)
						.show();
			}
		}

	}

	class MyOverlay extends Overlay {

		public MyOverlay() {

		}

		public void draw(Canvas canvas, MapView mapv, boolean shadow) {
			super.draw(canvas, mapv, shadow);

			if (layerPicture == null) {
				return;
			}
			// Log.d(TAG, "Drawing picture");
			if (placesLock.tryAcquire()) {
				//Log.d(TAG, "Height: " + layerPicture.getHeight());
				canvas.drawBitmap(layerPicture, 0, 0, new Paint());
				// layerPicture.draw(canvas);
				placesLock.release();
			}
		}
	}
}