package sn.chezketchup.backend.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void routeInconnue404EtNon500() {
        ResponseEntity<ApiError> r = handler.handleRequeteMvc(
                new NoResourceFoundException(HttpMethod.GET, "/v3/api-docs", "v3/api-docs"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(r.getBody().code()).isEqualTo("NOT_FOUND");
    }

    @Test
    void mauvaiseMethode405() {
        ResponseEntity<ApiError> r = handler.handleRequeteMvc(
                new HttpRequestMethodNotSupportedException("DELETE", List.of("GET")));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void jsonMalForme400() {
        ResponseEntity<ApiError> r = handler.handleCorpsIllisible(
                new HttpMessageNotReadableException("json", new MockHttpInputMessage(new byte[0])));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody().code()).isEqualTo("INVALID_BODY");
    }

    @Test
    void erreurInattendue500SansDetailTechnique() {
        ResponseEntity<ApiError> r = handler.handleGeneric(new IllegalStateException("secret interne"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(r.getBody().message()).doesNotContain("secret interne");
    }
}
