package com.hansung.hsp.space;

import jakarta.persistence.*;

@Entity
@Table(name = "spaces")
public class Space {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "space_code", nullable = false, length = 50, unique = true)
    private String spaceCode;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false, length = 30)
    private String type;
    @Column(length = 100)
    private String location;
    @Column(name = "min_capacity")
    private Integer minCapacity;
    @Column(name = "max_capacity")
    private Integer maxCapacity;
    @Column(nullable = false, length = 30)
    private String venue;
    @Column(length = 255)
    private String facilities;
    @Column(name = "booking_enabled", nullable = false)
    private boolean bookingEnabled;

    protected Space() {}
    public Space(String spaceCode, String name, String type, String location, Integer minCapacity,
            Integer maxCapacity, String venue, String facilities, boolean bookingEnabled) {
        this.spaceCode = spaceCode;
        this.name = name;
        this.type = type;
        this.location = location;
        this.minCapacity = minCapacity;
        this.maxCapacity = maxCapacity;
        this.venue = venue;
        this.facilities = facilities;
        this.bookingEnabled = bookingEnabled;
    }

    public void update(SpacePatchRequest r) {
        if (r.spaceCode() != null) spaceCode = r.spaceCode();
        if (r.name() != null) name = r.name();
        if (r.type() != null) type = r.type();
        if (r.location() != null) location = r.location();
        if (r.minCapacity() != null) minCapacity = r.minCapacity();
        if (r.maxCapacity() != null) maxCapacity = r.maxCapacity();
        if (r.venue() != null) venue = r.venue();
        if (r.facilities() != null) facilities = r.facilities();
        if (r.bookingEnabled() != null) bookingEnabled = r.bookingEnabled();
    }
    public Long getId() { return id; }
    public String getSpaceCode() { return spaceCode; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getLocation() { return location; }
    public Integer getMinCapacity() { return minCapacity; }
    public Integer getMaxCapacity() { return maxCapacity; }
    public String getVenue() { return venue; }
    public String getFacilities() { return facilities; }
    public boolean isBookingEnabled() { return bookingEnabled; }
}

