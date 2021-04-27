package se325.assignment01.concert.service.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se325.assignment01.concert.common.dto.*;
import se325.assignment01.concert.common.types.BookingStatus;
import se325.assignment01.concert.service.domain.Booking;
import se325.assignment01.concert.service.domain.Concert;
import se325.assignment01.concert.service.domain.Seat;
import se325.assignment01.concert.service.domain.User;
import se325.assignment01.concert.service.jaxrs.LocalDateTimeParam;
import se325.assignment01.concert.service.mapper.BookingMapper;
import se325.assignment01.concert.service.mapper.SeatMapper;

import javax.persistence.*;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.*;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Path("/concert-service")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BookingResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(BookingResource.class);
    private final ExecutorService threadPool = Executors.newSingleThreadExecutor();
    private static final Map<ConcertInfoSubscriptionDTO, AsyncResponse> subscriptions = new ConcurrentHashMap<>();

    @POST
    @Path("bookings")
    public Response makeBooking(BookingRequestDTO requestDTO, @CookieParam("auth") Cookie authToken) {
        LOGGER.info("Received booking request");
        // Reject booking if auth token is not provided (i.e. not logged in)
        if (authToken == null) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        }

        EntityManager em = PersistenceManager.instance().createEntityManager();
        try {
            em.getTransaction().begin();

            // Check if a user exists with provided auth token
            TypedQuery<User> query = em.createQuery("select u from User u where u.authToken = :authToken", User.class)
                    .setParameter("authToken", authToken.getValue());
            User user = query.getSingleResult();

            // Find concert with specified ID
            Concert concert = em.find(Concert.class, requestDTO.getConcertId());
            if (concert == null) {
                throw new WebApplicationException(Response.Status.BAD_REQUEST);
            }

            // Check if booking request date is a valid Concert date
            if (!concert.getDates().contains(requestDTO.getDate())) {
                throw new WebApplicationException(Response.Status.BAD_REQUEST);
            }

            // Retrieve all Seat objects wanting to be booked that are currently unbooked
            TypedQuery<Seat> seatQuery = em.createQuery("select s from Seat s where s.date = :date AND s.label IN :labels AND s.isBooked = false", Seat.class)
                    .setParameter("date", requestDTO.getDate())
                    .setParameter("labels", requestDTO.getSeatLabels())
                    .setLockMode(LockModeType.OPTIMISTIC);
            List<Seat> requestedSeats = seatQuery.getResultList();

            // Check if any requested seats are already booked
            if (requestedSeats.size() != requestDTO.getSeatLabels().size()) {
                throw new WebApplicationException(Response.Status.FORBIDDEN);
            }

            // All checks passed; mark seats as booked and create Booking for user
            requestedSeats.forEach(seat -> seat.setBooked(true));
            Booking booking = new Booking(concert, user, requestDTO.getDate(), new HashSet<>(requestedSeats));
            em.persist(booking);
            em.getTransaction().commit();

            // Run check to see if any subscribers need to be notified of booking
            int totalSeats = em.createQuery("select s from Seat s where s.date = :date", Seat.class)
                    .setParameter("date", requestDTO.getDate())
                    .getResultList()
                    .size();
            int unbookedSeats = em.createQuery("select s from Seat s where s.date = :date AND s.isBooked = false", Seat.class)
                    .setParameter("date", requestDTO.getDate())
                    .getResultList()
                    .size();
            this.notifySubscribers(concert, requestDTO.getDate(), totalSeats, unbookedSeats);

            Response.ResponseBuilder rb = Response.created(URI.create("/concert-service/bookings/" + booking.getId()));
            return rb.build();
        } catch (OptimisticLockException e) {
            // Could not lock Seats for Booking as another Booking has booked them; return 409
            throw new WebApplicationException(Response.Status.CONFLICT);
        } catch (NoResultException e) {
            // No user found with provided token; return 401
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        } finally {
            em.close();
        }
    }

    @GET
    @Path("bookings")
    public Response getBookings(@CookieParam("auth") Cookie authToken) {
        LOGGER.info("Retrieving all Bookings");
        if (authToken == null) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        }

        EntityManager em = PersistenceManager.instance().createEntityManager();
        try {
            em.getTransaction().begin();

            // Get all Bookings for provided auth token
            TypedQuery<Booking> query = em.createQuery("select b from Booking b where b.user.authToken = :authToken", Booking.class)
                    .setParameter("authToken", authToken.getValue());
            List<Booking> bookings = query.getResultList();
            List<BookingDTO> bookingDTOS = bookings.stream().map(BookingMapper::toDTO).collect(Collectors.toList());
            GenericEntity<List<BookingDTO>> entity = new GenericEntity<>(bookingDTOS) {};

            Response.ResponseBuilder rb = Response.ok(entity);
            return rb.build();
        } finally {
            em.close();
        }
    }

    @GET
    @Path("bookings/{id}")
    public Response getBooking(@PathParam("id") long id, @CookieParam("auth") Cookie authToken) {
        LOGGER.info("Retrieving booking with ID " + id);
        // Reject request if auth token not provided
        if (authToken == null) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        }

        EntityManager em = PersistenceManager.instance().createEntityManager();
        try {
            em.getTransaction().begin();

            // Fetch the Booking object
            Booking booking = em.find(Booking.class, id);
            if (booking == null) {
                throw new WebApplicationException(Response.Status.NOT_FOUND);
            }

            // Check that the Booking belongs to the User
            User user = booking.getUser();
            if (!user.getAuthToken().equals(authToken.getValue())) {
                throw new WebApplicationException(Response.Status.FORBIDDEN);
            }

            BookingDTO bookingDTO = BookingMapper.toDTO(booking);
            Response.ResponseBuilder rb = Response.ok(bookingDTO);
            return rb.build();
        } finally {
            em.close();
        }
    }

    @POST
    @Path("subscribe/concertInfo")
    public void subscribeToConcertInfo(@Suspended AsyncResponse sub,
                                       @CookieParam("auth") Cookie authToken,
                                       ConcertInfoSubscriptionDTO concertInfoSubscriptionDTO) {
        LOGGER.info("Received subscription to concert info");
        // Ensure user provides authentication
        if (authToken == null) {
            threadPool.submit(() -> {
                sub.resume(Response.status(Response.Status.UNAUTHORIZED).build());
            });
            return;
        }
        EntityManager em = PersistenceManager.instance().createEntityManager();
        try {
            em.getTransaction().begin();

            // Authenticate user
            User user = em.createQuery("select u from User u where u.authToken = :authToken", User.class)
                    .setParameter("authToken", authToken.getValue())
                    .getSingleResult();

            // Find Concert to which subscription is related
            Concert concert = em.find(Concert.class, concertInfoSubscriptionDTO.getConcertId());
            if (concert == null || !concert.getDates().contains(concertInfoSubscriptionDTO.getDate())) {
                // Concert doesn't exist or date provided is incorrect
                threadPool.submit(() -> {
                   sub.resume(Response.status(Response.Status.BAD_REQUEST).build());
                });
                return;
            }

            subscriptions.put(concertInfoSubscriptionDTO, sub);
        } catch (NoResultException e) {
            // No user exists with the provided auth token; could not authenticate
            threadPool.submit(() -> {
                sub.resume(Response.status(Response.Status.UNAUTHORIZED).build());
            });
        } finally {
            em.close();
        }
    }

    @GET
    @Path("seats/{date}")
    public Response getSeats(@PathParam("date") LocalDateTimeParam dateTimeParam, @QueryParam("status") BookingStatus bookingStatus) {
        LOGGER.info("Retrieving seats for " + dateTimeParam.getLocalDateTime());
        EntityManager em = PersistenceManager.instance().createEntityManager();
        try {
            em.getTransaction().begin();

            // Get all seats for given date with given booking status
            TypedQuery<Seat> query;
            if (bookingStatus == BookingStatus.Booked || bookingStatus == BookingStatus.Unbooked) {
                query = em.createQuery("select s from Seat s where s.date = :date AND s.isBooked = :isBooked", Seat.class)
                        .setParameter("date", dateTimeParam.getLocalDateTime())
                        .setParameter("isBooked", bookingStatus == BookingStatus.Booked);
            } else if (bookingStatus == BookingStatus.Any){
                query = em.createQuery("select s from Seat s where s.date = :date", Seat.class)
                        .setParameter("date", dateTimeParam.getLocalDateTime());
            } else {
                throw new WebApplicationException(Response.Status.BAD_REQUEST);
            }
            List<Seat> seatList = query.getResultList();
            List<SeatDTO> seatDTOs = seatList.stream().map(SeatMapper::toDTO).collect(Collectors.toList());
            GenericEntity<List<SeatDTO>> genericEntity = new GenericEntity<>(seatDTOs) {};

            Response.ResponseBuilder rb = Response.ok(genericEntity);
            return rb.build();
        } finally {
            em.close();
        }
    }

    // Notify subscribers if the subscribed Concert has seats below the prescribed threshold
    private void notifySubscribers(Concert concert, LocalDateTime concertDate, int totalSeats, int unbookedSeats) {
        int percentageBooked = (int) Math.round((((double)(totalSeats - unbookedSeats) / totalSeats) * 100));
        for (Map.Entry<ConcertInfoSubscriptionDTO, AsyncResponse> subscription : subscriptions.entrySet()) {
            // If notification pertains to same Concert and Date as subscription
            if (subscription.getKey().getConcertId() == concert.getId() && subscription.getKey().getDate().equals(concertDate)) {
                if (percentageBooked > subscription.getKey().getPercentageBooked()) {
                    // Notify subscriber with notification
                    ConcertInfoNotificationDTO notificationDTO = new ConcertInfoNotificationDTO(unbookedSeats);
                    subscription.getValue().resume(Response.ok(notificationDTO).build());
                }
            }
        }
    }
}
