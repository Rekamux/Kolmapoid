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
import android.os.AsyncTask;
import android.util.Log;

/**
 * Update rates in an asynchronous way
 */
public class UpdatePlacesTask extends AsyncTask<Object, Object, Object> {
	private static final String TAG = "UpdateRatesTask";
	private static final String KEY = "AIzaSyDpZ0J_YzzI7v_BgzOCIcXnDt-GZi-yZAA";

	private double lattitude;
	private double longitude;
	private int radius;

	public UpdatePlacesTask(Context c) {
		Log.d(TAG, "Created task");
	}

	private String generateURL() {
		return "https://maps.googleapis.com/maps/api/place/search/json?key="
				+ KEY + "&location=" + Double.toString(lattitude) + ","
				+ Double.toString(longitude) + "&radius="
				+ Integer.toString(radius) + "&sensor=true";
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
			placesLock.acquire();
			places.clear();
			JSONArray result = updateJSON();
			boolean wentWell = true;
			Log.d(TAG, result.length() + " places found");
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
					places.add(new PlaceData(placeName, lat, lng));
					Log.d(TAG, "Treated " + placeName);
				} catch (Exception e) {
					// To keep on getting the other values
					wentWell = false;
					Log.e(TAG, "Failed to get place " + placeName);
					Log.d(TAG, "Reason: " + e.getMessage());
				}
			}
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
