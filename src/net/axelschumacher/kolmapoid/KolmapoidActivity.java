package net.axelschumacher.kolmapoid;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
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

	private MapController mapController;
	private MapView mapView;
	private LocationManager locationManager;
	private MyOverlays itemizedoverlay;
	private MyLocationOverlay myLocationOverlay;

	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		setContentView(R.layout.main); // bind the layout to the activity

		// Configure the Map
		mapView = (MapView) findViewById(R.id.mapview);
		mapView.setBuiltInZoomControls(true);
		mapView.setSatellite(true);
		mapController = mapView.getController();
		mapController.setZoom(14); // Zoom 1 is world view
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

		Drawable drawable = this.getResources().getDrawable(R.drawable.point);
		itemizedoverlay = new MyOverlays(this, drawable);
		//createMarker();
		mapView.getOverlays().add(new MyOverlay());
		updatePlaces();
	}

	@Override
	protected boolean isRouteDisplayed() {
		return false;
	}

	public class GeoUpdateHandler implements LocationListener {

		public void onLocationChanged(Location location) {
			int lat = (int) (location.getLatitude() * 1E6);
			int lng = (int) (location.getLongitude() * 1E6);
			GeoPoint point = new GeoPoint(lat, lng);
			//createMarker();
			mapController.animateTo(point); // mapController.setCenter(point);
			updatePlaces();
		}

		public void onProviderDisabled(String provider) {
		}

		public void onProviderEnabled(String provider) {
		}

		public void onStatusChanged(String provider, int status, Bundle extras) {
		}
	}

	private void createMarker() {
		GeoPoint p = mapView.getMapCenter();
		OverlayItem overlayitem = new OverlayItem(p, "", "");
		itemizedoverlay.addOverlay(overlayitem);
		if (itemizedoverlay.size() > 0) {
			mapView.getOverlays().add(itemizedoverlay);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		myLocationOverlay.enableMyLocation();
		myLocationOverlay.enableCompass();
	}

	/**
	 * Updates places in background
	 */
	private void updatePlaces() {
		Toast toast = Toast.makeText(this, R.string.start_update,
				Toast.LENGTH_SHORT);
		toast.show();
		Location loc = locationManager.getLastKnownLocation(locationManager.getBestProvider(new Criteria(), false));
		UpdatePlacesTask task = new UpdatePlacesTask(this, loc.getLatitude(), loc.getLongitude(), 100);
		Object[] o = null;
		task.execute(o);
	}

	@Override
	protected void onPause() {
		super.onResume();
		myLocationOverlay.disableMyLocation();
		myLocationOverlay.disableCompass();
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

			Paint mPaint = new Paint();
			mPaint.setDither(true);
			mPaint.setColor(Color.argb(128, 255, 0, 0));
			mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
			mPaint.setStrokeJoin(Paint.Join.ROUND);
			mPaint.setStrokeCap(Paint.Cap.ROUND);
			mPaint.setStrokeWidth(2);

			canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), mPaint);
		}
	}

}