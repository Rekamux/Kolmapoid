package net.axelschumacher.kolmapoid;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
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
	private static final String KEY = "AIzaSyDpZ0J_YzzI7v_BgzOCIcXnDt-GZi-yZAA";
	private String language = "fr";

	private double lattitude;
	private double longitude;
	private int radius;
	private MapView mapv;
	private Context context;

	public UpdatePlacesTask(Context c) {
		Log.d(TAG, "Created task");
		context = c;
	}

	private String generateURL() {
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
			mapv.invalidate();
			Toast toast = Toast.makeText(context, "Update done",
					Toast.LENGTH_SHORT);
			toast.show();
		} else {
			Log.d(TAG, "Task aborted");
		}
	}

	@Override
	protected Object doInBackground(Object... params) {
		Log.d(TAG, "Do in background");
		Semaphore placesLock = null;
		try {
			@SuppressWarnings("unchecked")
			ArrayList<PlaceData> places = (ArrayList<PlaceData>) params[0];
			placesLock = (Semaphore) params[1];
			lattitude = (Double) params[2];
			longitude = (Double) params[3];
			radius = (Integer) params[4];
			mapv = (MapView) params[5];
			Location myLocation = (Location) params[6];
			//int width = mapv.getWidth();
			//int height = mapv.getHeight();
			Bitmap picture = (Bitmap) params[7];
			Canvas canvas = new Canvas(picture);
			placesLock.acquire();
			places.clear();

			Paint linePaint = new Paint();
			linePaint.setDither(true);
			linePaint.setStrokeWidth(1);

			Paint textPaint = new Paint();
			textPaint.setColor(Color.BLUE);
			textPaint.setTextSize(25);
			textPaint.setStrokeWidth(5);
			JSONArray result = updateJSON();
			boolean wentWell = true;
			Projection projection = mapv.getProjection();
			int myLat = (int) (myLocation.getLatitude() * 1E6);
			int myLng = (int) (myLocation.getLongitude() * 1E6);
			GeoPoint myPoint = new GeoPoint(myLat, myLng);
			
			Log.d(TAG, result.length() + " places found");
			ArrayList<Point> placesPoints = new ArrayList<Point>();
			placesPoints.add(projection.toPixels(myPoint, null));
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
					PlaceData placeData = new PlaceData(placeName, lat, lng);
					places.add(placeData);

					GeoPoint gP1 = placeData.getGeoPoint();
					Point p1 = new Point();
					placesPoints.add(p1);
					projection.toPixels(gP1, p1);
					canvas.drawPoint(p1.x, p1.y, textPaint);
					canvas.drawText(placeName, p1.x, p1.y, textPaint);
					Log.d(TAG, "Treated " + placeName);
				} catch (Exception e) {
					// To keep on getting the other values
					wentWell = false;
					Log.e(TAG, "Failed to get place " + placeName);
					Log.d(TAG, "Reason: " + e.getMessage());
				}
			}
			int radiusPixel = (int) PointF.length(mapv.getWidth() / 2,
					mapv.getHeight() / 2);
			for (int x = 0; x < mapv.getWidth(); x++) {
				for (int y = 0; y < mapv.getHeight(); y++) {
					int distance = radiusPixel;
					for (int i = 0; i < placesPoints.size(); i++) {
						Point p = placesPoints.get(i);
						int placeDist = (int) Math.sqrt((p.x - x) * (p.x - x)
								+ (p.y - y) * (p.y - y));
						if (distance > placeDist) {
							distance = placeDist;
						}
					}
					int ratio = distance * 255 / radiusPixel;
					linePaint.setColor(Color.argb(128, ratio,
							255 - ratio, 0));
					canvas.drawPoint(x, y, linePaint);
				}
			}
			placesLock.release();
			//picture.endRecording();
			return Boolean.valueOf(wentWell);
		} catch (Exception e) {
			if (placesLock != null) {
				placesLock.release();
			}
			Log.d(TAG, e.getMessage());
			return Boolean.FALSE;
		}
	}
}
