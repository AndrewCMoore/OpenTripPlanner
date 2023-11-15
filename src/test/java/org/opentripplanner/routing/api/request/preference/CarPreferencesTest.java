package org.opentripplanner.routing.api.request.preference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.opentripplanner.routing.api.request.preference.ImmutablePreferencesAsserts.assertEqualsAndHashCode;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.opentripplanner.framework.model.Cost;

class CarPreferencesTest {

  private static final double SPEED = 20.111;
  private static final double EXPECTED_SPEED = 20.0;
  private static final double RELUCTANCE = 5.111;
  private static final double EXPECTED_RELUCTANCE = 5.1;
  private static final double TUNNEL_RELUCTANCE = 2.341;
  private static final double EXPECTED_TUNNEL_RELUCTANCE = 2.3;
  private static final int PARK_TIME = 300;
  private static final int PARK_COST = 250;
  private static final int PICKUP_TIME = 600;
  private static final int PICKUP_COST = 500;
  private static final double ACCELERATION_SPEED = 3.1;
  private static final double DECELERATION_SPEED = 3.5;
  public static final int RENTAL_PICKUP_TIME = 30;
  public static final int PARK_COST = 30;

  private final CarPreferences subject = CarPreferences
    .of()
    .withSpeed(SPEED)
    .withReluctance(RELUCTANCE)
    .withTunnelReluctance(TUNNEL_RELUCTANCE)
    .withParkTime(PARK_TIME)
    .withParkCost(PARK_COST)
    .withPickupTime(PICKUP_TIME)
    .withPickupCost(PICKUP_COST)
    .withAccelerationSpeed(ACCELERATION_SPEED)
    .withDecelerationSpeed(DECELERATION_SPEED)
    .withRental(rental -> rental.withPickupTime(RENTAL_PICKUP_TIME).build())
    .withParking(parking -> parking.withCost(PARK_COST).build())
    .build();

  @Test
  void speed() {
    assertEquals(EXPECTED_SPEED, subject.speed());
  }

  @Test
  void reluctance() {
    assertEquals(EXPECTED_RELUCTANCE, subject.reluctance());
  }

  @Test
  void tunnelReluctance() {
    assertEquals(EXPECTED_TUNNEL_RELUCTANCE, subject.tunnelReluctance());
  }

  @Test
  void parkTime() {
    assertEquals(PARK_TIME, subject.parkTime());
  }

  @Test
  void parkCost() {
    assertEquals(PARK_COST, subject.parkCost());
  }

  @Test
  void pickupTime() {
    assertEquals(Duration.ofSeconds(PICKUP_TIME), subject.pickupTime());
  }

  @Test
  void pickupCost() {
    assertEquals(Cost.costOfSeconds(PICKUP_COST), subject.pickupCost());
  }

  @Test
  void accelerationSpeed() {
    assertEquals(ACCELERATION_SPEED, subject.accelerationSpeed());
  }

  @Test
  void decelerationSpeed() {
    assertEquals(DECELERATION_SPEED, subject.decelerationSpeed());
  }

  @Test
  void rental() {
    var vehicleRental = VehicleRentalPreferences.of().withPickupTime(RENTAL_PICKUP_TIME).build();
    assertEquals(vehicleRental, subject.rental());
  }

  @Test
  void parking() {
    var vehicleParking = VehicleParkingPreferences.of().withCost(PARK_COST).build();
    assertEquals(vehicleParking, subject.parking());
  }

  @Test
  void testCopyOfEqualsAndHashCode() {
    // Return same object if no value is set
    assertSame(CarPreferences.DEFAULT, CarPreferences.of().build());
    assertSame(subject, subject.copyOf().build());

    // Create a copy, make a change and set it back again to force creating a new object
    var other = subject.copyOf().withSpeed(0.0).build();
    var same = other.copyOf().withSpeed(SPEED).build();
    assertEqualsAndHashCode(subject, other, same);
  }

  @Test
  void testToString() {
    assertEquals("CarPreferences{}", CarPreferences.DEFAULT.toString());
    assertEquals(
      "CarPreferences{" +
      "speed: 20.0, " +
      "reluctance: 5.1, " +
      "tunnelReluctance: 2.3, " +
      "parkTime: 300, " +
      "parkCost: $250, " +
      "pickupTime: 600, " +
      "pickupCost: $500, " +
      "accelerationSpeed: 3.1, decelerationSpeed: 3.5" +
      "}",
      subject.toString()
    );
  }
}
