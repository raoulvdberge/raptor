package com.raoulvdberge.raptor;

import com.raoulvdberge.raptor.model.Stop;
import com.raoulvdberge.raptor.model.TransferLeg;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InMemoryTransferDetailsProvider implements TransferDetailsProvider {
    private final Map<Stop, List<TransferLeg>> transfers;

    public InMemoryTransferDetailsProvider(Map<Stop, List<TransferLeg>> transfers) {
        this.transfers = transfers;
    }

    @Override
    public List<TransferLeg> getTransfersForStop(Stop stop) {
        return this.transfers.getOrDefault(stop, new ArrayList<>());
    }
}