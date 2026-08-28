package web.tosunsaeng.billing.reservation.domain;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "reservation_allocations")
public class ReservationAllocation {

    public enum Status {
        HELD,
        CONSUMED,
        RELEASED
    }

    @Id
    private String id;
    private String allocationId;
    private String reservationId;
    private String grantId;
    private int units;
    private Status status;
    private Instant createdAt;
    private Instant terminalAt;
    private long version;

    protected ReservationAllocation() {
    }

    private ReservationAllocation(
            String allocationId,
            String reservationId,
            String grantId,
            Instant createdAt
    ) {
        this.id = allocationId;
        this.allocationId = allocationId;
        this.reservationId = reservationId;
        this.grantId = grantId;
        this.units = 1;
        this.status = Status.HELD;
        this.createdAt = createdAt;
        this.version = 1;
    }

    public static ReservationAllocation held(
            String allocationId,
            String reservationId,
            String grantId,
            Instant createdAt
    ) {
        return new ReservationAllocation(allocationId, reservationId, grantId, createdAt);
    }

    public String getAllocationId() {
        return allocationId;
    }

    public String getReservationId() {
        return reservationId;
    }

    public String getGrantId() {
        return grantId;
    }

    public Status getStatus() {
        return status;
    }
}
