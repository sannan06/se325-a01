package se325.assignment01.concert.service.services;

import se325.assignment01.concert.common.dto.UserDTO;
import se325.assignment01.concert.service.domain.User;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import java.util.UUID;

@Path("/concert-service")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

    @POST
    @Path("login")
    public Response authoriseUser(UserDTO userDTO) {
        EntityManager em = PersistenceManager.instance().createEntityManager();

        try {
            em.getTransaction().begin();

            // Select user with matching username & password
            TypedQuery<User> typedQuery =
                    em.createQuery("select u from User u where u.username = :providedUsername AND u.password = :providedPassword", User.class)
                    .setParameter("providedUsername", userDTO.getUsername())
                    .setParameter("providedPassword", userDTO.getPassword());
            try {
                User user = typedQuery.getSingleResult();
                // Create an authentication token for user to mark them as authorised
                NewCookie cookie = new NewCookie("auth", UUID.randomUUID().toString());
                user.setAuthToken(cookie.getValue());
                em.merge(user);
                em.getTransaction().commit();

                // Return 200 response with auth token in cookie header
                Response.ResponseBuilder rb = Response.ok().cookie(cookie);
                return rb.build();
            } catch (NoResultException e) {
                // No user found with provided username/password
                throw new WebApplicationException(Response.Status.UNAUTHORIZED);
            } finally {
                em.close();
            }
        } finally {
            em.close();
        }
    }
}
