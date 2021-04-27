package se325.assignment01.concert.service.mapper;

import se325.assignment01.concert.common.dto.ConcertDTO;
import se325.assignment01.concert.common.dto.ConcertSummaryDTO;
import se325.assignment01.concert.service.domain.Concert;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class ConcertMapper {
    public static ConcertDTO toDTO(Concert concert) {
        ConcertDTO dto = new ConcertDTO(concert.getId(),
                concert.getTitle(),
                concert.getImage(),
                concert.getBlurb());
        dto.setDates(new ArrayList<>(concert.getDates()));
        dto.setPerformers(concert.getPerformers().stream().map(PerformerMapper::toDto).collect(Collectors.toList()));
        return dto;
    }

    public static ConcertSummaryDTO toSummaryDTO(Concert concert) {
        return new ConcertSummaryDTO(concert.getId(),
                concert.getTitle(),
                concert.getImage());
    }
}
