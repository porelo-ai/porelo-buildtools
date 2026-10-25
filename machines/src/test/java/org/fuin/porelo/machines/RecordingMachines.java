package org.fuin.porelo.machines;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Records requests at the point where a real source would begin renting. */
class RecordingMachines implements Machines {
    final List<Spec> requested = new ArrayList<>();
    final List<String> images = new ArrayList<>();
    Duration limit;
    boolean closed;

    @Override
    public Machines forJobsOf(Duration limit) {
        this.limit = limit;
        return this;
    }

    @Override
    public Lease acquire(Spec spec) throws IOException {
        requested.add(spec);
        throw new IOException("acquisition recorded");
    }

    @Override
    public Lease acquireFromStock(Spec spec, String image) throws IOException {
        images.add(image);
        return acquire(spec);
    }

    @Override
    public String runId() {
        return "recording";
    }

    @Override
    public void close() {
        closed = true;
    }
}
