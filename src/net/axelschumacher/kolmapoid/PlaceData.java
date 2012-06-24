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
	private int x;
	private int y;
	private int nbBits;

	public PlaceData(String name, double lattitude, double longitude, int x, int y, int n) {
		super();
		this.name = name;
		this.lattitude = lattitude;
		this.longitude = longitude;
		this.x = x;
		this.y = y;
		this.nbBits = n;
	}

	public String getName() {
		return name;
	}

	public double getLattitude() {
		return lattitude;
	}

	public int getLattitude6() {
		return (int) (lattitude * 1e6);
	}

	public double getLongitude() {
		return longitude;
	}

	public int getLongitude6() {
		return (int) (longitude * 1e6);
	}

	public GeoPoint getGeoPoint() {
		return new GeoPoint(getLattitude6(), getLongitude6());
	}

	public int getX() {
		return x;
	}

	public int getY() {
		return y;
	}
	
	public int getNbBits() {
		return nbBits;
	}
}
