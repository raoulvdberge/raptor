package com.raoulvdberge.raptor;

import com.raoulvdberge.raptor.model.*;
import com.raoulvdberge.raptor.provider.RouteDetailsProvider;
import com.raoulvdberge.raptor.provider.StopDetailsProvider;
import com.raoulvdberge.raptor.provider.TransferDetailsProvider;
import com.raoulvdberge.raptor.provider.TripDetailsProvider;

import java.time.LocalDateTime;
import java.util.*;

/**
 * The Raptor algorithm.
 *
 * @param <R> the route
 * @param <S> the stop
 */
public class Raptor<R, S> {
    private final RouteDetailsProvider<R, S> routeDetailsProvider;
    private final TripDetailsProvider<R, S> tripDetailsProvider;
    private final StopDetailsProvider<S, R> stopDetailsProvider;
    private final TransferDetailsProvider<S> transferDetailsProvider;

    public Raptor(RouteDetailsProvider<R, S> routeDetailsProvider,
                  TripDetailsProvider<R, S> tripDetailsProvider,
                  StopDetailsProvider<S, R> stopDetailsProvider,
                  TransferDetailsProvider<S> transferDetailsProvider) {
        this.routeDetailsProvider = routeDetailsProvider;
        this.tripDetailsProvider = tripDetailsProvider;
        this.stopDetailsProvider = stopDetailsProvider;
        this.transferDetailsProvider = transferDetailsProvider;
    }

    /**
     * Plans a journey from origin to destination.
     *
     * @param originName      the name of the origin, decided by {@link S#toString()}
     * @param destinationName the name of the destination, decided by {@link S#toString()}
     * @param date            the date and time that the journey should depart after
     * @return a list of journeys for the query
     */
    public List<Journey<S>> plan(String originName, String destinationName, LocalDateTime date) {
        return plan(
            stopDetailsProvider.getStops().stream().filter(s -> s.toString().equals(originName)).findFirst().orElseThrow(),
            stopDetailsProvider.getStops().stream().filter(s -> s.toString().equals(destinationName)).findFirst().orElseThrow(),
            date
        );
    }

    /**
     * Plans a journey from origin to destination.
     *
     * @param origin      the origin
     * @param destination the destination
     * @param date        the date and time that the journey should depart after
     * @return a list of journeys for the query
     */
    public List<Journey<S>> plan(S origin, S destination, LocalDateTime date) {
        var kArrivals = createKArrivals(origin, date);
        var kConnections = createKConnections();

        var markedStops = new HashSet<S>();
        markedStops.add(origin);

        for (var k = 1; !markedStops.isEmpty(); ++k) {
            var newMarkedStops = new HashSet<S>();

            var queue = getQueue(markedStops);

            kArrivals.put(k, new HashMap<>(kArrivals.get(k - 1)));

            for (var entry : queue.entrySet()) {
                var boardingPoint = -1;
                var route = entry.getKey();
                var stop = entry.getValue();

                Optional<Trip<S>> foundTrip = Optional.empty();

                var routePath = routeDetailsProvider.getRoutePath(route);

                for (var stopIndex = routeDetailsProvider.getRouteStopIndex(route, stop); stopIndex < routePath.size(); ++stopIndex) {
                    var stopInRoute = routePath.get(stopIndex);

                    if (foundTrip.isPresent() && foundTrip.get().getStopTimes().get(stopIndex).getArrivalTime().isBefore(kArrivals.get(k).get(stopInRoute))) {
                        kArrivals.get(k).put(stopInRoute, foundTrip.get().getStopTimes().get(stopIndex).getArrivalTime());
                        kConnections.get(stopInRoute).put(k, new KConnection<>(
                            foundTrip.get(),
                            boardingPoint,
                            stopIndex
                        ));

                        newMarkedStops.add(stopInRoute);
                    }

                    if (foundTrip.isEmpty() || kArrivals.get(k - 1).get(stopInRoute).isBefore(foundTrip.get().getStopTimes().get(stopIndex).getArrivalTime())) {
                        foundTrip = this.tripDetailsProvider.getEarliestTripAtStop(
                            route,
                            stopIndex,
                            kArrivals.get(k - 1).get(stopInRoute)
                        );

                        boardingPoint = stopIndex;
                    }
                }
            }

            for (var stop : markedStops) {
                for (var transfer : transferDetailsProvider.getTransfersForStop(stop)) {
                    var dest = transfer.getDestination();
                    var arrivalTime = kArrivals.get(k - 1).get(stop).plus(transfer.getDuration());

                    if (arrivalTime.isBefore(kArrivals.get(k).get(dest))) {
                        kArrivals.get(k).put(dest, arrivalTime);
                        kConnections.get(dest).put(k, new KConnection<>(
                            stop,
                            dest,
                            transfer.getDuration(),
                            transfer.getDistance()
                        ));

                        newMarkedStops.add(dest);
                    }
                }
            }

            markedStops = newMarkedStops;
        }

        return getJourneys(kConnections, destination);
    }

    private Map<S, Map<Integer, KConnection<S>>> createKConnections() {
        var kConnections = new HashMap<S, Map<Integer, KConnection<S>>>();
        for (var stop : stopDetailsProvider.getStops()) {
            kConnections.put(stop, new HashMap<>());
        }

        return kConnections;
    }

    private Map<Integer, Map<S, LocalDateTime>> createKArrivals(S origin, LocalDateTime date) {
        var kArrivals = new HashMap<Integer, Map<S, LocalDateTime>>();

        var initialArrivals = new HashMap<S, LocalDateTime>();
        for (var stop : stopDetailsProvider.getStops()) {
            initialArrivals.put(stop, LocalDateTime.MAX);
        }

        kArrivals.put(0, initialArrivals);
        kArrivals.get(0).put(origin, date);

        return kArrivals;
    }

    private Map<R, S> getQueue(Set<S> markedStops) {
        var queue = new HashMap<R, S>();

        for (var stop : markedStops) {
            for (var route : stopDetailsProvider.getRoutesByStop(stop)) {
                if (!queue.containsKey(route) || routeDetailsProvider.isStopBefore(route, stop, queue.get(route))) {
                    queue.put(route, stop);
                }
            }
        }

        return queue;
    }

    private List<Journey<S>> getJourneys(Map<S, Map<Integer, KConnection<S>>> kConnections, S finalDestination) {
        var journeys = new ArrayList<Journey<S>>();

        for (var k : kConnections.get(finalDestination).keySet()) {
            var dest = finalDestination;

            var legs = new ArrayList<Leg<S>>();

            while (k > 0) {
                var connection = kConnections.get(dest).get(k);

                if (connection.getType() == KConnectionType.TRANSFER) {
                    legs.add(new TransferLeg<>(
                        connection.getOrigin(),
                        connection.getDestination(),
                        connection.getDuration(),
                        connection.getDistance()
                    ));

                    dest = connection.getOrigin();
                } else {
                    var stopTimes = connection.getTrip().getStopTimes();

                    var origin = stopTimes.get(connection.getBoardingPoint()).getStop();
                    dest = stopTimes.get(connection.getStopIndex()).getStop();

                    legs.add(new TimetableLeg<>(
                        origin,
                        dest,
                        connection.getTrip()
                    ));

                    dest = stopTimes.get(connection.getBoardingPoint()).getStop();
                }

                k--;
            }

            Collections.reverse(legs);

            journeys.add(new Journey<>(legs));
        }

        return journeys;
    }
}
