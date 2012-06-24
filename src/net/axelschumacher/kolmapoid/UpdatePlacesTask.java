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
	private static final String KEY = "AIzaSyDpZ0J_YzzI7v_BgzOCIcXnDt-GZi-yZAA";
	private String language = "fr";

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

	@Override
	protected Object doInBackground(Object... params) {
		Log.d(TAG, "Do in background");
		Semaphore placesLock = null;
		try {
			ArrayList<PlaceData> places = activity.places;
			placesLock = activity.placesLock;
			MapView mapv = activity.mapView;
			Location myLocation = activity.location;

			places.clear();

			JSONArray result = updateJSON();
			boolean wentWell = true;

			// Add my position
			Projection projection = mapv.getProjection();
			int myLat = (int) (myLocation.getLatitude() * 1E6);
			int myLng = (int) (myLocation.getLongitude() * 1E6);
			GeoPoint myPoint = new GeoPoint(myLat, myLng);
			Point p = new Point();
			projection.toPixels(myPoint, p);
			places.add(new PlaceData("My position", myLat, myLng, p.x, p.y, 0));
/*
			Log.d(TAG, result.length() + 1 + " places found");
			for (int i = 0; i < result.length(); i++) {
				String placeName = "Unknown place";
				// Update places list for each result
				try {
					JSONObject place = result.getJSONObject(i);
					placeName = place.getString("name");
					Log.d(TAG, "Treating place " + placeName);
					int nbBits = (int) (Math.log(i + 1) / KolmapoidActivity.log10 + 1);
					Log.d(TAG, "Nb bits: "+nbBits);
					JSONObject geometry = place.getJSONObject("geometry");
					JSONObject location = geometry.getJSONObject("location");
					double lat = location.getDouble("lat");
					double lng = location.getDouble("lng");
					GeoPoint gP = new GeoPoint((int) (lat * 1e6),
							(int) (lng * 1e6));
					projection.toPixels(gP, p);
					PlaceData placeData = new PlaceData(placeName, lat, lng,
							p.x, p.y, nbBits);
					places.add(placeData);
					Log.d(TAG, "Treated " + placeName);
				} catch (Exception e) {
					// To keep on getting the other values
					wentWell = false;
					Log.e(TAG, "Failed to get place " + placeName);
					Log.d(TAG, "Reason: " + e.getMessage());
				}
			}
			*/
			// Swap maps
			placesLock.acquire();
			activity.placesToBeDisplayed = new ArrayList<PlaceData>(places);
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
}
