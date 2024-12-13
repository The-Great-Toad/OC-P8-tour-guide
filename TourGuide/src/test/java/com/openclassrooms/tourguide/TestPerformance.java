package com.openclassrooms.tourguide;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.time.StopWatch;
import org.junit.jupiter.api.Test;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.service.RewardsService;
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;

class TestPerformance {

	private static final Logger logger = LoggerFactory.getLogger("TestPerformance");

	/*
	 * A note on performance improvements:
	 * 
	 * The number of users generated for the high volume tests can be easily
	 * adjusted via this method:
	 * 
	 * InternalTestHelper.setInternalUserNumber(100000);
	 * 
	 * 
	 * These tests can be modified to suit new solutions, just as long as the
	 * performance metrics at the end of the tests remains consistent.
	 * 
	 * These are performance metrics that we are trying to hit:
	 * 
	 * highVolumeTrackLocation: 100,000 users within 15 minutes:
	 * assertTrue(TimeUnit.MINUTES.toSeconds(15) >=
	 * TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	 *
	 * highVolumeGetRewards: 100,000 users within 20 minutes:
	 * assertTrue(TimeUnit.MINUTES.toSeconds(20) >=
	 * TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	 */

	private static final int INTERNAL_USER_NUMBER = 50000;

	@Test
	public void highVolumeTrackLocation() {
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		/* Set up the number of users */
		InternalTestHelper.setInternalUserNumber(INTERNAL_USER_NUMBER);
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		/* Fetch all users */
		List<User> allUsers = tourGuideService.getAllUsers();
		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		/* Track locations for all users asynchronously */
		List<CompletableFuture<VisitedLocation>> futures = new ArrayList<>();
		for (User user : allUsers) {
			futures.add(tourGuideService.trackUserLocationAsync(user));
		}

		/* Wait for all futures to complete with the join method */
		CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
		allFutures.join();

		stopWatch.stop();
		tourGuideService.tracker.stopTracking();
		logger.info("highVolumeTrackLocation: Time Elapsed: {} seconds.", TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));

		/* Assert that the performance metric is met */
		assertTrue(TimeUnit.MINUTES.toSeconds(15) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	}

	@Test
	public void highVolumeGetRewards() {
		logger.info("highVolumeGetRewards - START");

		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		/* Set up the number of users */
		InternalTestHelper.setInternalUserNumber(INTERNAL_USER_NUMBER);
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		/* Fetch all users */
		List<User> allUsers = tourGuideService.getAllUsers();
		/* Fetch an attraction */
		Attraction attraction = gpsUtil.getAttractions().get(0);

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		List<CompletableFuture<Void>> futures = new ArrayList<>();

		/* Calculate rewards for all users asynchronously */
		for (User user : allUsers) {
			user.addToVisitedLocations(
					new VisitedLocation(user.getUserId(), attraction, new Date())
			);
			CompletableFuture<Void> future = CompletableFuture.runAsync(() -> rewardsService.calculateRewards(user))
					.exceptionally(ex -> {
						logger.error("Error in async task for user {}: {}", user.getUserName(), ex.getMessage(), ex);
						return null;
					});
			futures.add(future);
		}

		/* Wait for all futures to complete with a timeout */
		try {
			CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
			allFutures.get(25, TimeUnit.MINUTES);
			logger.info("Async tasks completed.");
		} catch (Exception e) {
			logger.error("Error waiting for async tasks: {}", e.getMessage(), e);
		}

		stopWatch.stop();

		logger.info("Stopping tracker...");
		tourGuideService.tracker.stopTracking();
		logger.info("Tracker stopped.");

		logger.info("Checking user rewards...");
		for (User user : allUsers) {
			assertFalse(user.getUserRewards().isEmpty(), "User rewards should not be empty for user: " + user.getUserName());
		}

		logger.info("Verifying time constraints...");
		assertTrue(TimeUnit.MINUTES.toSeconds(20) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()),
				"Execution time exceeded the limit.");

		logger.info("highVolumeGetRewards: Time Elapsed: {} seconds.", TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	}

}
