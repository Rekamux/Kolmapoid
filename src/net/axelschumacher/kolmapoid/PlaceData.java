package net.axelschumacher.kolmapoid;

import com.google.android.maps.GeoPoint;

/**
 * A set of information about a nearby place
 * 
 * @author ax
 * 
 */
public class PlaceData {
	private String name;
	private double lattitude;
	private double longitude;

	public PlaceData(String name, double lattitude, double longitude) {
		super();
		this.name = name;
		this.lattitude = lattitude;
		this.longitude = longitude;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public double getLattitude() {
		return lattitude;
	}

	public int getLattitude6() {
		return (int) (lattitude * 1e6);
	}

	public void setLattitude(double lattitude) {
		this.lattitude = lattitude;
	}

	public double getLongitude() {
		return longitude;
	}

	public int getLongitude6() {
		return (int) (longitude * 1e6);
	}

	public void setLongitude(double longitude) {
		this.longitude = longitude;
	}

	public GeoPoint getGeoPoint() {
		return new GeoPoint(getLattitude6(), getLongitude6());
	}

}
