package com.hansung.hsp.space;

public record SpaceResponse(Long id, String spaceCode, String name, String type, String location,
        Integer minCapacity, Integer maxCapacity, String venue, String facilities, boolean bookingEnabled) {
    public static SpaceResponse from(Space s) {
        return new SpaceResponse(s.getId(), s.getSpaceCode(), s.getName(), s.getType(), s.getLocation(),
                s.getMinCapacity(), s.getMaxCapacity(), s.getVenue(), s.getFacilities(), s.isBookingEnabled());
    }
}

