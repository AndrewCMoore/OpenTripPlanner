package org.opentripplanner.apis.transmodel.mapping.preferences;

import org.opentripplanner.apis.transmodel.support.DataFetcherDecorator;
import org.opentripplanner.routing.api.request.preference.TransferPreferences;

import graphql.schema.DataFetchingEnvironment;

public class TransferPreferencesMapper {

  public static void mapTransferPreferences(
    TransferPreferences.Builder transfer,
    DataFetchingEnvironment environment,
    DataFetcherDecorator callWith
  ) {
    callWith.argument("transferPenalty", transfer::withCost);

    // 'minimumTransferTime' is deprecated, that's why we are mapping 'slack' twice.
    callWith.argument("minimumTransferTime", transfer::withSlack);
    callWith.argument("transferSlack", transfer::withSlack);
    callWith.argument("tunnelReluctance", transfer::withTunnelReluctance);
    callWith.argument("waitReluctance", transfer::withWaitReluctance);
    callWith.argument("maximumTransfers", transfer::withMaxTransfers);
    callWith.argument("maximumAdditionalTransfers", transfer::withMaxAdditionalTransfers);
  }
}
