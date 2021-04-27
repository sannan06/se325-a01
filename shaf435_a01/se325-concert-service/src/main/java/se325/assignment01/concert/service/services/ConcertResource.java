package se325.assignment01.concert.service.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se325.assignment01.concert.common.dto.ConcertDTO;
import se325.assignment01.concert.common.dto.ConcertSummaryDTO;
import se325.assignment01.concert.common.dto.PerformerDTO;
import se325.assignment01.concert.service.domain.Concert;
import se325.assignment01.concert.service.domain.Performer;
import se325.assignment01.concert.service.mapper.ConcertMapper;
import se325.assignment01.concert.service.mapper.PerformerMapper;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.ws.rs.*;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;

@Path("/concert-service")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ConcertResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConcertResource.class);

    @GET
    @Path("concerts/{id}")
    public Response getConcert(@PathParam("id") long id) {
        LOGGER.info("Retrieving concert with ID: " + id);
        EntityManager em = PersistenceManager.instance().createEntityManager();

        try {
            em.getTransaction().begin();

            // Load Concert object
            Concert concert = em.find(Concert.class, id);
            em.getTransaction().commit();

            // Concert does not exist
            if (concert == null) {
                throw new WebApplicationException(Response.Status.NOT_FOUND);
            }

            Response.ResponseBuilder rb = Response.ok(ConcertMapper.toDTO(concert));
            return rb.build();
        } finally {
            em.close();
        }
    }

    @GET
    @Path("concerts")
    public Response getAllConcerts() {
        LOGGER.info("Retrieving all concerts");
        EntityManager em = PersistenceManager.instance().createEntityManager();
        try {
            em.getTransaction().begin();
            // Select all concerts and create DTOs
            TypedQuery<Concert> concertTypedQuery = em.createQuery("select c from Concert c", Concert.class);
            List<Concert> concertList = concertTypedQuery.getResultList();
            List<ConcertDTO> concertDTOList = concertList.stream().map(ConcertMapper::toDTO).collect(Collectors.toList());
            GenericEntity<List<ConcertDTO>> genericEntity = new GenericEntity<>(concertDTOList) {};

            Response.ResponseBuilder rb = Response.ok(genericEntity);
            return rb.build();
        } finally {
            em.close();
        }
    }

    @GET
    @Path("concerts/summaries")
    public Response getConcertSummaries() {
        LOGGER.info("Retrieving all concert summaries");
        EntityManager em = PersistenceManager.instance().createEntityManager();
        try {
            em.getTransaction().begin();

            TypedQuery<Concert> concertTypedQuery = em.createQuery("select c from Concert c", Concert.class);
            List<Concert> concertList = concertTypedQuery.getResultList();
            List<ConcertSummaryDTO> concertSummaryDTOS = concertList.stream().map(ConcertMapper::toSummaryDTO).collect(Collectors.toList());
            GenericEntity<List<ConcertSummaryDTO>> genericEntity = new GenericEntity<>(concertSummaryDTOS) {};

            Response.ResponseBuilder rb = Response.ok(genericEntity);
            return rb.build();
        } finally {
            em.close();
        }
    }

}
