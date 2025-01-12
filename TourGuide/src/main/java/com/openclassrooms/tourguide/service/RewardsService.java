package com.openclassrooms.tourguide.service;

import java.util.*;
import java.util.concurrent.*;
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
		System.out.println("Available processors: " + processors);

		// Minimum pool size of 50 threads (for CICD)
		int poolSize = Math.max(50, processors * 10);
		forkJoinPool = new ForkJoinPool(poolSize);

		System.out.println("Initialized ForkJoinPool with pool size: " + poolSize);
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

//		Map<VisitedLocation, Attraction> toBeRewardAttractions = new HashMap<>();

		for (VisitedLocation visitedLocation : userLocations) {
			attractions.stream()
					// Filter out attractions already rewarded
					.filter(attraction -> !rewardedAttractions.contains(attraction.attractionName))
					// Filter attractions near user's visited location
					.filter(attraction -> nearAttraction(visitedLocation, attraction))
					// Calculate rewards and add them to the user
					.forEach(attraction -> {
//						toBeRewardAttractions.put(visitedLocation, attraction);
						int rewardPoints = getRewardPoints(attraction, user);
						user.addUserReward(new UserReward(visitedLocation, attraction, rewardPoints));
					});
		}

//		toBeRewardAttractions.entrySet().parallelStream()
//				.forEach(entrySet -> {
//						int rewardPoints = getRewardPoints(entrySet.getValue(), user);
//						user.addUserReward(new UserReward(entrySet.getKey(), entrySet.getValue(), rewardPoints));
//				});
	}

	public void calculateAllUsersRewards(List<User> users) {
		try {
			// Submit the task to the ForkJoinPool
			ForkJoinTask<?> task = forkJoinPool.submit(() ->
					users.parallelStream().forEach(this::calculateRewards)
			);

			// Wait for the task to complete with a timeout of 20 minutes
			task.get(20, TimeUnit.MINUTES);

		} catch (TimeoutException e) {
			System.err.println("calculateAllUsersRewards: Execution exceeded 20 minutes!");
			throw new RuntimeException("Reward calculation exceeded the allowed time of 20 minutes", e);

		} catch (InterruptedException | ExecutionException e) {
			System.err.println("calculateAllUsersRewards: An error occurred during execution.");
			throw new RuntimeException("Error during reward calculation", e);
		}

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
