package cn.dreamingfish.dispatch.server.model;

import java.time.*;
import java.util.*;
import java.util.function.BooleanSupplier;

public record CallContext(String requestId,Instant deadline,BooleanSupplier cancelled) {
    public CallContext {UUID.fromString(requestId);Objects.requireNonNull(deadline);Objects.requireNonNull(cancelled);}
    public static CallContext create(Duration budget){return new CallContext(UUID.randomUUID().toString(),Instant.now().plus(budget),()->Thread.currentThread().isInterrupted());}
    public void check(){if(cancelled.getAsBoolean())throw RemoteFailure.of("CANCELLED");if(!deadline.isAfter(Instant.now()))throw RemoteFailure.of("CONNECT_FAILED");}
}
