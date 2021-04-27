package se325.assignment01.concert.service.mapper;

import se325.assignment01.concert.common.dto.BookingDTO;
import se325.assignment01.concert.service.domain.Booking;

import java.util.ArrayList;
import java.util.stream.Collectors;

public class BookingMapper {
    public static BookingDTO toDTO(Booking booking) {
        return new BookingDTO(booking.getConcert().getId(),
                booking.getDate(),
                booking.getSeats().stream().map(SeatMapper::toDTO).collect(Collectors.toList()));
    }
}
