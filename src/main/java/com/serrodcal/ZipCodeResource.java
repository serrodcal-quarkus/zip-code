package com.serrodcal;

import java.util.NoSuchElementException;

import javax.inject.Singleton;
import javax.transaction.Transactional;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

import org.jboss.logging.Logger;

import io.quarkus.vertx.web.Body;
import io.quarkus.vertx.web.Param;
import io.quarkus.vertx.web.ReactiveRoutes;
import io.quarkus.vertx.web.Route;
import io.quarkus.vertx.web.Route.HandlerType;
import io.quarkus.vertx.web.Route.HttpMethod;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.mutiny.core.http.HttpServerResponse;

@Singleton
public class ZipCodeResource {

    private static final Logger log = Logger.getLogger(ZipCodeResource.class.getName());
    
    private ZipCodeRepo zipRepo;

    public ZipCodeResource(ZipCodeRepo zipRepo) {
        this.zipRepo = zipRepo;
    }

    @Route(path = "/zipcode/:zipcode", methods = HttpMethod.GET, produces = "application/json")
    public Uni<ZipCode> findById(@Param @NotBlank String zipcode) {
        log.debug("findById=" + zipcode);
        return zipRepo.findById(zipcode);
    }

    @Route(path = "/zipcode", methods = HttpMethod.GET, produces = "application/json")
    public Multi<ZipCode> postZipCode(@Param @NotBlank String city) {
        log.debug("postZipCode=" + city);
        return ReactiveRoutes.asJsonArray(zipRepo.findByCity(city));
    }

    @Transactional
    @Route(path="/zipcode", methods = HttpMethod.POST, consumes = "application/json", produces = "application/json")
    public Uni<ZipCode> create(@Body @Valid ZipCode zipCode, HttpServerResponse response) {
        log.debug("create=" + zipCode.toString());
        return zipRepo.findById(zipCode.getZip())
            .onItem().ifNull().switchTo(createZipCode(zipCode))
            .onItem().ifNotNull().transform(i -> {
                response.setStatusCode(201);
                return i;
            });
    }

    private Uni<ZipCode> createZipCode(ZipCode zipCode) {
        return Uni.createFrom().deferred(() -> zipRepo.save(zipCode));
    }

    @Route(path = "/*", type = HandlerType.FAILURE)
    public void error(RoutingContext context) {
        Throwable t = context.failure();
        if (t != null) {
            log.error("Failed to handle request", t);
            int status = context.statusCode();
            String chunk = "";
            if (t instanceof NoSuchElementException) {
                status = 404;
            } else if (t instanceof IllegalArgumentException) {
                status = 422;
                chunk = new JsonObject().put("code", status)
                        .put("exceptionType", t.getClass().getName()).put("error", t.getMessage()).encode();
            }
            context.response().setStatusCode(status).end(chunk);
        } else {
            // Continue with the default error handler
            context.next();
        }
    }

}
