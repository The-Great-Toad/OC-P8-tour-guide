package com.openclassrooms.tourguide.service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

@Service
public class RewardsService {
    private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

	// proximity in miles
    private final int defaultProximityBuffer = 10;
	private int proximityBuffer = defaultProximityBuffer;
	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;
	private final ForkJoinPool forkJoinPool;

	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;

		// Init threads pool size
		int processors = Runtime.getRuntime().availableProcessors();
		int poolSize = processors * 10;
		forkJoinPool = new ForkJoinPool(poolSize);
	}
	
	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}

	public void calculateRewards(User user) {
		List<VisitedLocation> userLocations = user.getVisitedLocations();
		List<Attraction> attractions = gpsUtil.getAttractions();

		Set<String> rewardedAttractions = user.getUserRewards().stream()
				.map(r -> r.attraction.attractionName)
				.collect(Collectors.toSet());

		for (VisitedLocation visitedLocation : userLocations) {
			attractions.stream()
					// Filter out attractions already rewarded
					.filter(attraction -> !rewardedAttractions.contains(attraction.attractionName))
					// Filter attractions near user's visited location
					.filter(attraction -> nearAttraction(visitedLocation, attraction))
					// Calculate rewards and add them to the user
					.forEach(attraction -> {
						int rewardPoints = getRewardPoints(attraction, user);
						user.addUserReward(new UserReward(visitedLocation, attraction, rewardPoints));
					});
		}
	}

	public void calculateAllUsersRewards(List<User> users) {
		forkJoinPool.submit(() ->
				users.parallelStream().forEach(this::calculateRewards)
		).join();

//		// Adapt pool size to user's list
//		ExecutorService executorService = Executors.newFixedThreadPool(
//				Math.min(users.size(), Runtime.getRuntime().availableProcessors())
//		);
//
//		// Execute calculateRewards method using multi-threading
//		users.forEach(user -> executorService.submit(new Thread(() -> calculateRewards(user))));
//
//		// Shutting down
//		executorService.shutdown();
//		try {
//			if (!executorService.awaitTermination(20, TimeUnit.MINUTES)) {
//				executorService.shutdownNow();
//			}
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//			executorService.shutdownNow();
//		}
	}

	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		int attractionProximityRange = 200;
		return !(getDistance(attraction, location) > attractionProximityRange);
	}
	
	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		return !(getDistance(attraction, visitedLocation.location) > proximityBuffer);
	}
	
	public int getRewardPoints(Attraction attraction, User user) {
		return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
	}
	
	public double getDistance(Location loc1, Location loc2) {
        double lat1 = Math.toRadians(loc1.latitude);
        double lon1 = Math.toRadians(loc1.longitude);
        double lat2 = Math.toRadians(loc2.latitude);
        double lon2 = Math.toRadians(loc2.longitude);

        double angle = Math.acos(Math.sin(lat1) * Math.sin(lat2)
                               + Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));

        double nauticalMiles = 60 * Math.toDegrees(angle);
		return STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
	}

}
