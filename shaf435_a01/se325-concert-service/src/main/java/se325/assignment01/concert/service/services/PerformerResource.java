package se325.assignment01.concert.service.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se325.assignment01.concert.common.dto.PerformerDTO;
import se325.assignment01.concert.service.domain.Performer;
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
public class PerformerResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConcertResource.class);

    @GET
    @Path("performers/{id}")
    public Response getPerformer(@PathParam("id") long id) {
        LOGGER.info("Retrieving performer with id: " + id);
        EntityManager em = PersistenceManager.instance().createEntityManager();
        try {
            em.getTransaction().begin();

            // Load Performer with supplied ID
            Performer performer = em.find(Performer.class, id);

            // Check if Performer could be found
            if (performer == null) {
                throw new WebApplicationException(Response.Status.NOT_FOUND);
            }

            Response.ResponseBuilder rb = Response.ok(PerformerMapper.toDto(performer));
            return rb.build();
        } finally {
            em.close();
        }
    }

    @GET
    @Path("performers")
    public Response getAllPerformers() {
        LOGGER.info("Retrieving all performers");
        EntityManager em = PersistenceManager.instance().createEntityManager();
        try {
            em.getTransaction().begin();
            TypedQuery<Performer> performerTypedQuery = em.createQuery("select p from Performer p", Performer.class);
            List<Performer> performerList = performerTypedQuery.getResultList();
            List<PerformerDTO> performerDTOS = performerList.stream().map(PerformerMapper::toDto).collect(Collectors.toList());
            GenericEntity<List<PerformerDTO>> genericEntity = new GenericEntity<>(performerDTOS) {};

            Response.ResponseBuilder rb = Response.ok(genericEntity);
            return rb.build();
        } finally {
            em.close();
        }
    }
}
