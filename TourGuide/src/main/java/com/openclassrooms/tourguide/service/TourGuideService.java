package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.NearbyAttractionDTO;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;

import tripPricer.Provider;
import tripPricer.TripPricer;

@Service
public class TourGuideService {
	private final Logger logger = LoggerFactory.getLogger(TourGuideService.class);
	private final GpsUtil gpsUtil;
	private final RewardsService rewardsService;
	private final TripPricer tripPricer = new TripPricer();
	public final Tracker tracker;
	boolean testMode = true;

	private final SecureRandom random = new SecureRandom();
//	private final ExecutorService executorService;
	private final ForkJoinPool forkJoinPool;

	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
		this.gpsUtil = gpsUtil;
		this.rewardsService = rewardsService;

		// Init threads pool size
//		int availableProcessors = Runtime.getRuntime().availableProcessors();
//		this.executorService = Executors.newFixedThreadPool(availableProcessors);

		int processors = Runtime.getRuntime().availableProcessors();
		int poolSize = processors * 10;
		forkJoinPool = new ForkJoinPool(poolSize);

		Locale.setDefault(Locale.US);

		if (testMode) {
			logger.info("TestMode enabled");
			logger.debug("Initializing users");
			initializeInternalUsers();
			logger.debug("Finished initializing users");
		}
		tracker = new Tracker(this);
		addShutDownHook();
	}

	public List<UserReward> getUserRewards(User user) {
		return user.getUserRewards();
	}

	public VisitedLocation getUserLocation(User user) {
        return (!user.getVisitedLocations().isEmpty()) ? user.getLastVisitedLocation() : trackUserLocation(user);
	}

	public User getUser(String userName) {
		return internalUserMap.get(userName);
	}

	public List<User> getAllUsers() {
		return new ArrayList<>(internalUserMap.values());
	}

	public void addUser(User user) {
		if (!internalUserMap.containsKey(user.getUserName())) {
			internalUserMap.put(user.getUserName(), user);
		}
	}

	public List<Provider> getTripDeals(User user) {
		/* Sum up all user's reward points */
		int cumulativeRewardPoints = user.getUserRewards().stream()
				.mapToInt(UserReward::getRewardPoints)
				.sum();

		CompletableFuture<List<Provider>> futureProviders = new CompletableFuture<>();
		futureProviders.completeAsync(() -> tripPricer.getPrice(
				TRIP_PRICER_API_KEY,
                user.getUserId(),
                user.getUserPreferences().getNumberOfAdults(),
                user.getUserPreferences().getNumberOfChildren(),
                user.getUserPreferences().getTripDuration(),
                cumulativeRewardPoints
        ));

        List<Provider> providers;
        try {
            providers = futureProviders.get();
			logger.info("Got {} providers", providers.size());

        } catch (InterruptedException | ExecutionException e) {
            logger.error("TourGuideService.getTripDeals error : {}", e.getMessage());
            throw new RuntimeException(e);
        }

        user.setTripDeals(providers);

		return providers;
	}

	public VisitedLocation trackUserLocation(User user) {
		VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
		user.addToVisitedLocations(visitedLocation);
		rewardsService.calculateRewards(user);
		return visitedLocation;
	}

	public List<VisitedLocation> trackAllUsersLocation(List<User> users) {
		return forkJoinPool.submit(() ->
				users.parallelStream()
						.map(this::trackUserLocation)
						.toList()
		).join();

//		return users.parallelStream()
//				.map(this::trackUserLocation)
//				.collect(Collectors.toList());

//		List<VisitedLocation> visitedLocations = Collections.synchronizedList(new ArrayList<>());
//		List<CompletableFuture<Void>> futures = new ArrayList<>();
//
//		for (User user : users) {
//			CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> {
//				VisitedLocation visitedLocation = trackUserLocation(user);
//				visitedLocations.add(visitedLocation);
//				return visitedLocation;
//			}, executorService).thenAccept(result -> {});
//
//			futures.add(future);
//		}
//
//		CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
//		allOf.join();
//
//		return visitedLocations;
	}

//	public List<Attraction> getNearByAttractions(VisitedLocation visitedLocation) {
//		List<Attraction> nearbyAttractions = new ArrayList<>();
//		for (Attraction attraction : gpsUtil.getAttractions()) {
//			if (rewardsService.isWithinAttractionProximity(attraction, visitedLocation.location)) {
//				nearbyAttractions.add(attraction);
//			}
//		}
//
//		return nearbyAttractions;
//	}

	public List<NearbyAttractionDTO> getNearByAttractions(VisitedLocation visitedLocation, User user) {
		List<NearbyAttractionDTO> nearbyAttractionsDTO = new ArrayList<>();
		List<Attraction> allAttractions = gpsUtil.getAttractions();

		/* Sort attractions by their distance from user's location in ASC */
		allAttractions.sort(
				Comparator.comparingDouble(
						attraction -> rewardsService.getDistance(attraction, visitedLocation.location)
				)
		);

		/* Get the 5 closest attractions */
		for (int i = 0; i < 5; i++) {
			Attraction attraction = allAttractions.get(i);

			/* Calculate distance between attraction and user's location */
			double distance = rewardsService.getDistance(attraction, visitedLocation.location);

			int rewardPoints = rewardsService.getRewardPoints(attraction, user);

			nearbyAttractionsDTO.add(new NearbyAttractionDTO(
					attraction.attractionName,
					attraction.latitude,
					attraction.longitude,
					visitedLocation.location.latitude,
					visitedLocation.location.longitude,
					distance,
					rewardPoints
			));
		}

		return nearbyAttractionsDTO;
	}

	private void addShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				tracker.stopTracking();
			}
		});
	}

	/**********************************************************************************
	 * 
	 * Methods Below: For Internal Testing
	 * 
	 **********************************************************************************/
	private static final String TRIP_PRICER_API_KEY = "test-server-api-key";
	// Database connection will be used for external users, but for testing purposes
	// internal users are provided and stored in memory
	private final Map<String, User> internalUserMap = new HashMap<>();

	private void initializeInternalUsers() {
		IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
			String userName = "internalUser" + i;
			String phone = "000";
			String email = userName + "@tourGuide.com";
			User user = new User(UUID.randomUUID(), userName, phone, email);
			generateUserLocationHistory(user);

			internalUserMap.put(userName, user);
		});
		logger.debug("Created {} internal test users.", InternalTestHelper.getInternalUserNumber());
	}

	private void generateUserLocationHistory(User user) {
		IntStream.range(0, 3).forEach(i ->
			user.addToVisitedLocations(
					new VisitedLocation(user.getUserId(),
					new Location(generateRandomLatitude(), generateRandomLongitude()), getRandomTime()
					)
			)
		);
	}

	private double generateRandomLongitude() {
		double leftLimit = -180;
		double rightLimit = 180;
		return leftLimit + random.nextDouble() * (rightLimit - leftLimit);
	}

	private double generateRandomLatitude() {
		double leftLimit = -85.05112878;
		double rightLimit = 85.05112878;
		return leftLimit + random.nextDouble() * (rightLimit - leftLimit);
	}

	private Date getRandomTime() {
		LocalDateTime localDateTime = LocalDateTime.now().minusDays(random.nextInt(30));
		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}

}
