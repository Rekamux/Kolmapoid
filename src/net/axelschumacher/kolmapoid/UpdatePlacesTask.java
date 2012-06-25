package net.axelschumacher.kolmapoid;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.TreeSet;
import java.util.concurrent.Semaphore;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.location.Location;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.Projection;

/**
 * Update rates in an asynchronous way
 */
public class UpdatePlacesTask extends AsyncTask<Object, Object, Object> {
	private static final String TAG = "UpdateRatesTask";
	//private static final String KEY = "AIzaSyDpZ0J_YzzI7v_BgzOCIcXnDt-GZi-yZAA";
	private static final String KEY = "AIzaSyBTqZWHSLWf58WV6UWSJZBeDC-fcEVeFzw";
	private String language = "fr";
	public static final double log2 = Math.log(2);

	private KolmapoidActivity activity;

	public UpdatePlacesTask(KolmapoidActivity a) {
		Log.d(TAG, "Created task");
		activity = a;
	}

	private String generateURL() {
		GeoPoint center = activity.getCenter();
		double lattitude = center.getLatitudeE6() / 1000000.0;
		double longitude = center.getLongitudeE6() / 1000000.0;
		int radius = activity.getRadius();
		return "https://maps.googleapis.com/maps/api/place/search/json?key="
				+ KEY + "&location=" + Double.toString(lattitude) + ","
				+ Double.toString(longitude) + "&radius="
				+ Integer.toString(radius) + "&sensor=true&language="
				+ language;
	}

	/**
	 * Get data from web and update shared preferences
	 * 
	 * @return
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	private JSONArray updateJSON() throws ClientProtocolException, IOException {
		String feed = readFeed();
		JSONArray places = null;
		try {
			JSONObject values = (JSONObject) new JSONTokener(feed).nextValue();
			places = values.getJSONArray("results");
		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
		}
		return places;
	}

	/**
	 * Build JSON string from web
	 * 
	 * @return
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	private String readFeed() throws ClientProtocolException, IOException {
		StringBuilder builder = new StringBuilder();
		HttpClient client = new DefaultHttpClient();
		HttpGet httpGet = new HttpGet(generateURL());
		HttpResponse response = client.execute(httpGet);
		StatusLine statusLine = response.getStatusLine();
		int statusCode = statusLine.getStatusCode();
		if (statusCode == 200) {
			HttpEntity entity = response.getEntity();
			InputStream content = entity.getContent();
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					content));
			String line;
			while ((line = reader.readLine()) != null) {
				builder.append(line);
			}
		} else {
			Log.e(TAG, "Failed to download file");
		}

		return builder.toString();
	}

	@Override
	protected void onPostExecute(Object result) {
		boolean success = (Boolean) result;
		if (success) {
			Log.d(TAG, "Task completed");
			activity.mapView.invalidate();
			Toast toast = Toast.makeText(activity, "Update done",
					Toast.LENGTH_SHORT);
			toast.show();
		} else {
			Log.d(TAG, "Task aborted");
		}
	}
	
	private boolean isOut(MapView mapv, int x, int y) {
		return x < -mapv.getWidth()/2 || x >= 1.5*mapv.getWidth() || y < -mapv.getHeight()/2
				|| y >= 1.5*mapv.getHeight();
	}

	@Override
	protected Object doInBackground(Object... params) {
		Log.d(TAG, "Do in background");
		Semaphore placesLock = null;
		try {
			ArrayList<PlaceData> places = new ArrayList<PlaceData>();
			placesLock = activity.placesLock;
			MapView mapv = activity.mapView;
			Location myLocation = activity.location;
			Bitmap bitmap = Bitmap.createBitmap(mapv.getWidth(),
					mapv.getHeight(), Bitmap.Config.ARGB_4444);
			TreeSet<CircleData> circles = new TreeSet<CircleData>();

			JSONArray result = updateJSON();
			boolean wentWell = true;
			int nbCircles = 10;
			int radius = mapv.getHeight() / 2;
			Projection projection = mapv.getProjection();
			double pixelsPerMeter = projection.metersToEquatorPixels(1);
			int placeComplexity = 1;

			// Add my position
			int myLat = (int) (myLocation.getLatitude() * 1E6);
			int myLng = (int) (myLocation.getLongitude() * 1E6);
			GeoPoint myPoint = new GeoPoint(myLat, myLng);
			Point p = new Point();
			projection.toPixels(myPoint, p);
			if (!(isOut(mapv, p.x, p.y))) {
				places.add(new PlaceData("My position", myLat, myLng, p.x, p.y,
						(int) (Math.ceil(Math.log(placeComplexity) / log2))));
				placeComplexity++;
			}

			int maxPlaceBits =
			// + (int) (radius / pixelsPerMeter);
			+(int) (Math.ceil(Math.log(result.length()) / log2))
					+ 1
					+ (int) (Math
							.ceil(Math.log(radius / pixelsPerMeter) / log2))
					+ 1;

			Log.d(TAG, result.length() + 1 + " places found");
			for (int i = 0; i < result.length(); i++) {
				String placeName = "Unknown place";
				// Update places list for each result
				try {
					JSONObject place = result.getJSONObject(i);
					placeName = place.getString("name");
					Log.d(TAG, "Treating place " + placeName);
					JSONObject geometry = place.getJSONObject("geometry");
					JSONObject location = geometry.getJSONObject("location");
					double lat = location.getDouble("lat");
					double lng = location.getDouble("lng");
					GeoPoint gP = new GeoPoint((int) (lat * 1e6),
							(int) (lng * 1e6));
					projection.toPixels(gP, p);
					// Only treat visible ones
					if (isOut(mapv, p.x, p.y)) {
						Log.d(TAG, "Discarded "+placeName+" x: "+p.x+" y: "+p.y);
						continue;
					}
					PlaceData placeData = new PlaceData(placeName, lat, lng,
							p.x, p.y, (int) (Math.ceil(Math.log(placeComplexity) / log2)));
					places.add(placeData);
					Log.d(TAG, "Treated " + placeName);
					placeComplexity++;
				} catch (Exception e) {
					// To keep on getting the other values
					wentWell = false;
					Log.e(TAG, "Failed to get place " + placeName);
					Log.d(TAG, "Reason: " + e.getMessage());
				}
			}

			Canvas canvas = new Canvas(bitmap);
			canvas.drawRGB(255, 0, 0);

			Paint paint = new Paint();
			paint.setStyle(Paint.Style.FILL);

			Paint textPaint = new Paint();
			textPaint.setColor(Color.BLUE);
			textPaint.setTextSize(25);
			textPaint.setStrokeWidth(5);

			// Draw the color circles
			for (int i = 0; i < nbCircles; i++) {
				int circleRadius = radius * (nbCircles - i - 1) / nbCircles;
				int nbMeters = (int) (circleRadius / pixelsPerMeter);
				int distanceBits = (int) Math.ceil(Math.log(nbMeters + 1)
						/ log2);
				// int distanceBits = nbMeters;
				Log.d(TAG, "Circle " + i + " meters: " + nbMeters + " bits "
						+ distanceBits);
				for (int j = places.size() - 1; j >= 0; j--) {
					PlaceData place = places.get(j);
					int complexity = place.nbBits + distanceBits;
					Log.d(TAG, "Place " + place.name + " cpx: " + complexity);
					circles.add(new CircleData(place.x, place.y, circleRadius,
							complexity, place.name));
				}
			}
			int circlesCount = circles.size();
			int counted = 0;
			while (!circles.isEmpty()) {
				counted++;
				CircleData circle = circles.last();
				circles = (TreeSet<CircleData>) circles.headSet(circle, false);
				int ratio = circle.complexity * 255 / maxPlaceBits;
				paint.setColor(Color.rgb(ratio, 255 - ratio, 0));
				canvas.drawCircle(circle.x, circle.y, circle.radius, paint);

				Log.d(TAG, "Circle: " + circle.name + " cpx: "
						+ circle.complexity + "/" + maxPlaceBits + " x: "
						+ circle.x + " y: " + circle.y + " radius: "
						+ circle.radius + " ratio: " + ratio);

			}
			if (counted != circlesCount) {
				Log.e(TAG, "Wrong number of circles ! "+counted+"/"+circlesCount);
			}

			// Draw the places
			for (int i = 0; i < places.size(); i++) {
				PlaceData place = places.get(i);
				canvas.drawPoint(place.x, place.y, textPaint);
				canvas.drawText(place.name, place.x, place.y, textPaint);
			}

			// Swap maps
			placesLock.acquire();
			activity.bitmapToBeDisplayed = Bitmap.createBitmap(bitmap);
			placesLock.release();

			return Boolean.valueOf(wentWell);
		} catch (Exception e) {
			if (placesLock != null) {
				placesLock.release();
			}
			Log.d(TAG, e.getMessage());
			return Boolean.FALSE;
		}
	}

	public class PlaceData {
		public String name;
		public double lattitude;
		public double longitude;
		public int x;
		public int y;
		public int nbBits;

		public PlaceData(String name, double lattitude, double longitude,
				int x, int y, int n) {
			super();
			this.name = name;
			this.lattitude = lattitude;
			this.longitude = longitude;
			this.x = x;
			this.y = y;
			this.nbBits = n;
		}

		public int lattitude6() {
			return (int) (lattitude * 1e6);
		}

		public int longitude6() {
			return (int) (longitude * 1e6);
		}

		public GeoPoint geoPoint() {
			return new GeoPoint(lattitude6(), longitude6());
		}
	}

	public class CircleData implements Comparable<CircleData> {
		public int x;
		public int y;
		public int radius;
		public int complexity;
		public String name;

		public CircleData(int x, int y, int radius, int complexity, String name) {
			this.x = x;
			this.y = y;
			this.radius = radius;
			this.complexity = complexity;
			this.name = name;
		}

		public int compareTo(CircleData another) {
			int result = complexity - another.complexity;
			if (result == 0) {
				result = radius - another.radius;
				if (result == 0) {
					result = x - another.x;
					if (result == 0) {
						result = y - another.y;
					}
				}
			}
			return result;
		}
	}

}
