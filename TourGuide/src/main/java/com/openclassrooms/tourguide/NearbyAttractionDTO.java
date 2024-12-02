package com.openclassrooms.tourguide;

import java.util.Objects;

public class NearbyAttractionDTO {

    private String attractionName;
    private double attractionLatitude;
    private double attractionLongitude;
    private double userLatitude;
    private double userLongitude;
    private double distanceInMiles;
    private int rewardPoints;

    public NearbyAttractionDTO(
            String attractionName,
            double attractionLatitude,
            double attractionLongitude,
            double userLatitude,
            double userLongitude,
            double distanceInMiles,
            int rewardPoints) {
        this.attractionName = attractionName;
        this.attractionLatitude = attractionLatitude;
        this.attractionLongitude = attractionLongitude;
        this.userLatitude = userLatitude;
        this.userLongitude = userLongitude;
        this.distanceInMiles = distanceInMiles;
        this.rewardPoints = rewardPoints;
    }

    public String getAttractionName() {
        return attractionName;
    }

    public void setAttractionName(String attractionName) {
        this.attractionName = attractionName;
    }

    public double getAttractionLatitude() {
        return attractionLatitude;
    }

    public void setAttractionLatitude(double attractionLatitude) {
        this.attractionLatitude = attractionLatitude;
    }

    public double getAttractionLongitude() {
        return attractionLongitude;
    }

    public void setAttractionLongitude(double attractionLongitude) {
        this.attractionLongitude = attractionLongitude;
    }

    public double getUserLatitude() {
        return userLatitude;
    }

    public void setUserLatitude(double userLatitude) {
        this.userLatitude = userLatitude;
    }

    public double getUserLongitude() {
        return userLongitude;
    }

    public void setUserLongitude(double userLongitude) {
        this.userLongitude = userLongitude;
    }

    public double getDistanceInMiles() {
        return distanceInMiles;
    }

    public void setDistanceInMiles(double distanceInMiles) {
        this.distanceInMiles = distanceInMiles;
    }

    public int getRewardPoints() {
        return rewardPoints;
    }

    public void setRewardPoints(int rewardPoints) {
        this.rewardPoints = rewardPoints;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NearbyAttractionDTO that = (NearbyAttractionDTO) o;
        return Double.compare(that.attractionLatitude, attractionLatitude) == 0 && Double.compare(that.attractionLongitude, attractionLongitude) == 0 && Double.compare(that.userLatitude, userLatitude) == 0 && Double.compare(that.userLongitude, userLongitude) == 0 && Double.compare(that.distanceInMiles, distanceInMiles) == 0 && rewardPoints == that.rewardPoints && Objects.equals(attractionName, that.attractionName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attractionName, attractionLatitude, attractionLongitude, userLatitude, userLongitude, distanceInMiles, rewardPoints);
    }

    @Override
    public String toString() {
        return "NearbyAttractionDTO{" + "attractionName='" + attractionName + '\'' +
                ", attractionLatitude=" + attractionLatitude +
                ", attractionLongitude=" + attractionLongitude +
                ", userLatitude=" + userLatitude +
                ", userLongitude=" + userLongitude +
                ", distanceInMiles=" + distanceInMiles +
                ", rewardPoints=" + rewardPoints +
                '}';
    }
}

