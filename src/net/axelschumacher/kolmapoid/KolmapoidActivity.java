package net.axelschumacher.kolmapoid;

import java.util.List;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.os.Bundle;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;

// This Activity will draw a line between two selected points on Map

public class KolmapoidActivity extends MapActivity {
	protected boolean isRouteDisplayed() {
		return false;
	}

	private List<Overlay> mapOverlays;

	private Projection projection;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		MapView mapView = (MapView) findViewById(R.id.mapview);
		mapView.setBuiltInZoomControls(true);

		mapOverlays = mapView.getOverlays();
		projection = mapView.getProjection();
		mapOverlays.add(new MyOverlay());
	}

	class MyOverlay extends Overlay {

		public MyOverlay() {

		}

		public void draw(Canvas canvas, MapView mapv, boolean shadow) {
			super.draw(canvas, mapv, shadow);

			Paint mPaint = new Paint();
			mPaint.setDither(true);
			mPaint.setColor(Color.RED);
			mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
			mPaint.setStrokeJoin(Paint.Join.ROUND);
			mPaint.setStrokeCap(Paint.Cap.ROUND);
			mPaint.setStrokeWidth(2);

			GeoPoint gP1 = new GeoPoint(19240000, -99120000);
			GeoPoint gP2 = new GeoPoint(37423157, -122085008);

			Point p1 = new Point();
			Point p2 = new Point();
			Path path = new Path();

			projection.toPixels(gP1, p1);
			projection.toPixels(gP2, p2);

			path.moveTo(p2.x, p2.y);
			path.lineTo(p1.x, p1.y);

			canvas.drawPath(path, mPaint);
		}
	}

}