package org.opentripplanner.raptor.rangeraptor.multicriteria.arrivals;

import org.opentripplanner.raptor.api.model.RaptorTripSchedule;
import org.opentripplanner.raptor.api.view.ArrivalView;
import org.opentripplanner.raptor.util.paretoset.ParetoComparator;

/**
 * Abstract super class for multi-criteria stop arrival.
 *
 * @param <T> The TripSchedule type defined by the user of the raptor API.
 */
public abstract class AbstractStopArrival<T extends RaptorTripSchedule> implements ArrivalView<T> {

  private final AbstractStopArrival<T> previous;
  /**
   * We want transits to dominate transfers so we increment the round not only between RangeRaptor
   * rounds, but for transits and transfers also. The access path is paretoRound 0, the first
   * transit path is 1. The following transfer path, if it exists, is paretoRound 2, and the next
   * transit is 3, and so on.
   * <p/>
   * The relationship between Range Raptor round and paretoRound can be described by this formula:
   * <pre>
   *     Range Raptor round =  (paretoRound + 1) / 2
   * </pre>
   */
  private final int paretoRound;
  private final int stop;
  private final int arrivalTime;
  private final int travelDuration;
  private final int generalizedCost;

  /**
   * Transit or transfer.
   *
   * @param previous             the previous arrival visited for the current trip
   * @param paretoRoundIncrement the increment to add to the paretoRound
   * @param stop                 stop index for this arrival
   * @param arrivalTime          the arrival time for this stop index
   * @param generalizedCost                 the total accumulated cost at this stop arrival
   */
  AbstractStopArrival(
    AbstractStopArrival<T> previous,
    int paretoRoundIncrement,
    int stop,
    int arrivalTime,
    int generalizedCost
  ) {
    this.previous = previous;
    this.paretoRound = previous.paretoRound + paretoRoundIncrement;
    this.stop = stop;
    this.arrivalTime = arrivalTime;
    this.travelDuration = previous.travelDuration() + (arrivalTime - previous.arrivalTime());
    this.generalizedCost = generalizedCost;
  }

  /**
   * Initial state - first stop visited during the RAPTOR algorithm.
   */
  AbstractStopArrival(
    int stop,
    int departureTime,
    int travelDuration,
    int initialCost,
    int paretoRound
  ) {
    this.previous = null;
    this.paretoRound = paretoRound;
    this.stop = stop;
    this.arrivalTime = departureTime + travelDuration;
    this.travelDuration = travelDuration;
    this.generalizedCost = initialCost;
  }

  /**
   * This comparator is used to compare regular stop arrivals. It uses {@code arrivalTime},
   * {@code paretoRound} and {@code generalizedCost} to compare arrivals. It does NOT include
   * {@code arrivedOnBoard}. Normally arriving on-board should give the arrival an advantage
   * - you can continue on foot, walking to the next stop. But, we only do this if it happens
   * in the same Raptor iteration and round - if it does it is taken care of by the order
   * which the algorithm work - not by this comparator.
   */
  public static <
    T extends RaptorTripSchedule
  > ParetoComparator<AbstractStopArrival<T>> compareArrivalTimeRoundAndCost() {
    // This is important with respect to performance. Using the short-circuit logical OR(||) is
    // faster than bitwise inclusive OR(|) (even between boolean expressions)
    return (l, r) ->
      l.arrivalTime < r.arrivalTime ||
      l.paretoRound < r.paretoRound ||
      l.generalizedCost < r.generalizedCost;
  }

  /**
   * This include {@code arrivedOnBoard} in the comparison compared with
   * {@link #compareArrivalTimeRoundAndCost()}.
   */
  public static <
    T extends RaptorTripSchedule
  > ParetoComparator<AbstractStopArrival<T>> compareArrivalTimeRoundCostAndOnBoardArrival() {
    // This is important with respect to performance. Using the short-circuit logical OR(||) is
    // faster than bitwise inclusive OR(|) (even between boolean expressions)
    return (l, r) ->
      l.arrivalTime < r.arrivalTime ||
      l.paretoRound < r.paretoRound ||
      l.generalizedCost < r.generalizedCost ||
      (l.arrivedOnBoard() && !r.arrivedOnBoard());
  }

  @Override
  public final int stop() {
    return stop;
  }

  @Override
  public final int round() {
    return (paretoRound + 1) / 2;
  }

  @Override
  public final int arrivalTime() {
    return arrivalTime;
  }

  public int cost() {
    return generalizedCost;
  }

  @Override
  public final AbstractStopArrival<T> previous() {
    return previous;
  }

  public int travelDuration() {
    return travelDuration;
  }

  public AbstractStopArrival<T> timeShiftNewArrivalTime(int newArrivalTime) {
    throw new UnsupportedOperationException("No accessEgress for transfer stop arrival");
  }

  @Override
  public final int hashCode() {
    throw new IllegalStateException("Avoid using hashCode() and equals() for this class.");
  }

  @Override
  public final boolean equals(Object o) {
    throw new IllegalStateException("Avoid using hashCode() and equals() for this class.");
  }

  @Override
  public String toString() {
    return asString();
  }

  /**
   * @return previous state or throw a NPE if no previousArrival exist.
   */
  final int previousStop() {
    return previous.stop;
  }
}