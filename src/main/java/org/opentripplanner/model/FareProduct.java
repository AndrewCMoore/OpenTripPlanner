package org.opentripplanner.model;

import java.time.Duration;
import java.util.Objects;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.routing.core.Money;
import org.opentripplanner.transit.model.framework.FeedScopedId;

public record FareProduct(FeedScopedId id, String name, Money amount, Duration duration) {
  public boolean coversItinerary(Itinerary i) {
    var transitLegs = i.getTransitLegs();
    var allLegsInProductFeed = transitLegs
      .stream()
      .allMatch(leg -> leg.getAgency().getId().getFeedId().equals(id.getFeedId()));

    return (
      allLegsInProductFeed && (transitLegs.size() == 1 || coversDuration(i.getTransitDuration()))
    );
  }

  public boolean coversDuration(Duration journeyDuration) {
    return Objects.nonNull(duration) && duration.toSeconds() > journeyDuration.toSeconds();
  }
}
