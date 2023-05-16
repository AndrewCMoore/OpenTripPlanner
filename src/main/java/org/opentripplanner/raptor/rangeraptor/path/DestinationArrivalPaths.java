package org.opentripplanner.raptor.rangeraptor.path;

import java.util.ArrayList;
import java.util.Collection;
import javax.annotation.Nullable;
import org.opentripplanner.framework.lang.OtpNumberFormat;
import org.opentripplanner.framework.logging.ThrottleLogger;
import org.opentripplanner.raptor.api.model.RaptorAccessEgress;
import org.opentripplanner.raptor.api.model.RaptorConstants;
import org.opentripplanner.raptor.api.model.RaptorTripSchedule;
import org.opentripplanner.raptor.api.path.RaptorPath;
import org.opentripplanner.raptor.api.path.RaptorStopNameResolver;
import org.opentripplanner.raptor.api.view.ArrivalView;
import org.opentripplanner.raptor.path.Path;
import org.opentripplanner.raptor.rangeraptor.debug.DebugHandlerFactory;
import org.opentripplanner.raptor.rangeraptor.internalapi.DebugHandler;
import org.opentripplanner.raptor.rangeraptor.internalapi.SlackProvider;
import org.opentripplanner.raptor.rangeraptor.internalapi.WorkerLifeCycle;
import org.opentripplanner.raptor.rangeraptor.transit.RaptorTransitCalculator;
import org.opentripplanner.raptor.spi.RaptorCostCalculator;
import org.opentripplanner.raptor.util.paretoset.ParetoComparator;
import org.opentripplanner.raptor.util.paretoset.ParetoSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The responsibility of this class is to collect result paths for destination arrivals. It does so
 * using a pareto set. The comparator is passed in as an argument to the constructor. This make is
 * possible to collect different sets in different scenarios.
 * <p/>
 * Depending on the pareto comparator passed into the constructor this class grantee that the best
 * paths with respect to <em>arrival time</em>, <em>rounds</em> and <em>travel duration</em> are
 * found. You may also add <em>cost</em> as a criteria (multi-criteria search).
 * <p/>
 * This class is a thin wrapper around a ParetoSet of {@link RaptorPath}s. Before paths are added
 * the arrival time is checked against the arrival time limit.
 *
 * @param <T> The TripSchedule type defined by the user of the raptor API.
 */
public class DestinationArrivalPaths<T extends RaptorTripSchedule> {

  private static final Logger LOG = LoggerFactory.getLogger(DestinationArrivalPaths.class);
  private static final Logger LOG_MISS_MATCH = ThrottleLogger.throttle(LOG);

  private final ParetoSet<RaptorPath<T>> paths;
  private final RaptorTransitCalculator<T> transitCalculator;

  @Nullable
  private final RaptorCostCalculator<T> costCalculator;

  private final SlackProvider slackProvider;
  private final PathMapper<T> pathMapper;
  private final DebugHandler<RaptorPath<?>> debugPathHandler;
  private final RaptorStopNameResolver stopNameResolver;
  private boolean reachedCurrentRound = false;
  private int iterationDepartureTime = -1;

  public DestinationArrivalPaths(
    ParetoComparator<RaptorPath<T>> paretoComparator,
    RaptorTransitCalculator<T> transitCalculator,
    @Nullable RaptorCostCalculator<T> costCalculator,
    SlackProvider slackProvider,
    PathMapper<T> pathMapper,
    DebugHandlerFactory<T> debugHandlerFactory,
    RaptorStopNameResolver stopNameResolver,
    WorkerLifeCycle lifeCycle
  ) {
    this.paths =
      new ParetoSet<>(paretoComparator, debugHandlerFactory.paretoSetDebugPathListener());
    this.transitCalculator = transitCalculator;
    this.costCalculator = costCalculator;
    this.slackProvider = slackProvider;
    this.pathMapper = pathMapper;
    this.debugPathHandler = debugHandlerFactory.debugPathArrival();
    this.stopNameResolver = stopNameResolver;
    lifeCycle.onPrepareForNextRound(round -> clearReachedCurrentRoundFlag());
    lifeCycle.onSetupIteration(this::setRangeRaptorIterationDepartureTime);
  }

  public void add(ArrivalView<T> stopArrival, RaptorAccessEgress egressPath) {
    // TODO: 2023-05-16 via pass through: HERE

    var destArrival = createDestinationArrivalView(stopArrival, egressPath);

    if (destArrival == null) {
      return;
    }

    if (transitCalculator.exceedsTimeLimit(destArrival.arrivalTime())) {
      debugRejectByTimeLimitOptimization(destArrival);
    } else {
      RaptorPath<T> path = pathMapper.mapToPath(destArrival);

      assertGeneralizedCostIsCalculatedCorrectByMapper(destArrival, path);

      boolean added = paths.add(path);
      if (added) {
        reachedCurrentRound = true;
      }
    }
  }

  /**
   * Check if destination was reached in the current round.
   */
  public boolean isReachedCurrentRound() {
    return reachedCurrentRound;
  }

  public void setRangeRaptorIterationDepartureTime(int iterationDepartureTime) {
    this.iterationDepartureTime = iterationDepartureTime;
  }

  public boolean isEmpty() {
    return paths.isEmpty();
  }

  public boolean qualify(int departureTime, int arrivalTime, int numberOfTransfers, int cost) {
    return paths.qualify(
      Path.dummyPath(iterationDepartureTime, departureTime, arrivalTime, numberOfTransfers, cost)
    );
  }

  public Collection<RaptorPath<T>> listPaths() {
    return paths;
  }

  public void debugReject(ArrivalView<T> stopArrival, RaptorAccessEgress egress, String reason) {
    if (isDebugOn()) {
      var destinationArrival = createDestinationArrivalView(stopArrival, egress);
      if (destinationArrival != null) {
        debugReject(destinationArrival, reason);
      }
    }
  }

  public void debugReject(DestinationArrival<T> arrival, String reason) {
    if (isDebugOn()) {
      var path = pathMapper.mapToPath(arrival);
      debugPathHandler.reject(path, null, reason);
    }
  }

  @Override
  public String toString() {
    return paths.toString(p -> p.toString(stopNameResolver));
  }

  public final boolean isDebugOn() {
    return debugPathHandler != null;
  }

  /* private methods */

  private void clearReachedCurrentRoundFlag() {
    reachedCurrentRound = false;
  }

  private void debugRejectByTimeLimitOptimization(DestinationArrival<T> destArrival) {
    if (isDebugOn()) {
      debugReject(destArrival, transitCalculator.exceedsTimeLimitReason());
    }
  }

  @Nullable
  private DestinationArrival<T> createDestinationArrivalView(
    ArrivalView<T> stopArrival,
    RaptorAccessEgress egressPath
  ) {
    int departureTime = transitCalculator.calculateEgressDepartureTime(
      stopArrival.arrivalTime(),
      egressPath,
      slackProvider.transferSlack()
    );
    if (departureTime == RaptorConstants.TIME_NOT_SET) {
      return null;
    }

    int arrivalTime = transitCalculator.plusDuration(departureTime, egressPath.durationInSeconds());

    int waitTimeInSeconds = Math.abs(departureTime - stopArrival.arrivalTime());

    // If the aggregatedCost is zero(StdRaptor), then cost calculation is skipped.
    // If the aggregatedCost exist(McRaptor), then the cost of waiting is added.
    int additionalCost = 0;

    if (costCalculator != null) {
      additionalCost += costCalculator.waitCost(waitTimeInSeconds);
      additionalCost += costCalculator.costEgress(egressPath);
    }

    return new DestinationArrival<>(egressPath, stopArrival, arrivalTime, additionalCost);
  }

  /**
   * If the total cost generated by the mapper is not equal to the total cost calculated by Raptor,
   * there is probably a mistake in the mapper! This is a rather critical error and should be fixed.
   * To avoid dropping legal paths from the result set, we log this as an error and allow the path
   * to be included in the result!!!
   * <p>
   * The path mapper might not map the cost to each leg exactly as the Raptor does but the total
   * should be the same. Raptor only have stop-arrival, while the path have legs. A transit leg
   * alight BEFORE the transit stop arrival due to alight-slack.
   */
  private void assertGeneralizedCostIsCalculatedCorrectByMapper(
    DestinationArrival<T> destArrival,
    RaptorPath<T> path
  ) {
    if (path.c1() != destArrival.c1()) {
      // TODO - Bug: Cost mismatch stop-arrivals and paths #3623
      LOG_MISS_MATCH.warn(
        "Cost mismatch - Mapper: {}, stop-arrivals: {}, path: {}",
        OtpNumberFormat.formatCostCenti(path.c1()),
        raptorCostsAsString(destArrival),
        path.toStringDetailed(stopNameResolver)
      );
    }
  }

  /**
   * Return the cost of all stop arrivals including the destination in reverse order:
   * <p>
   * {@code $1200 $950 $600} (Egress, bus, and access arrival)
   */
  private String raptorCostsAsString(DestinationArrival<T> destArrival) {
    var arrivalCosts = new ArrayList<String>();
    ArrivalView<?> it = destArrival;
    while (it != null) {
      arrivalCosts.add(OtpNumberFormat.formatCostCenti(it.c1()));
      it = it.previous();
    }
    // Remove decimals if zero
    return String.join(" ", arrivalCosts).replaceAll("\\.00", "");
  }
}
