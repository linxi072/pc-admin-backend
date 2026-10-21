package com.acme.scaffold.observability.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OtlpSpanSerializerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializesServerSpanWithResource() throws Exception {
        OtlpSpan span = new OtlpSpan(
                "4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", null,
                "GET /api/users", OtlpSpan.Kind.SERVER, 1000L, 2000L,
                List.of(new OtlpSpan.Attribute("http.status_code", "200")),
                OtlpSpan.StatusCode.OK, null);
        String json = OtlpSpanSerializer.toJson(List.of(span), "svc");

        JsonNode root = mapper.readTree(json);
        JsonNode rs = root.get("resourceSpans").get(0);
        assertEquals("svc", rs.get("resource").get("attributes").get(0).get("value").get("stringValue").asText());
        JsonNode out = rs.get("scopeSpans").get(0).get("spans").get(0);
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", out.get("traceId").asText());
        assertEquals("00f067aa0ba902b7", out.get("spanId").asText());
        assertEquals(2, out.get("kind").asInt());            // SERVER
        assertEquals("1000", out.get("startTimeUnixNano").asText());
        assertEquals("2000", out.get("endTimeUnixNano").asText());
        assertEquals(1, out.get("status").get("code").asInt()); // OK
        assertEquals("200", out.get("attributes").get(0).get("value").get("stringValue").asText());
    }

    @Test
    void omitsParentWhenNullAndMapsUnusedEnums() throws Exception {
        OtlpSpan span = new OtlpSpan("t", "s", null, "x",
                OtlpSpan.Kind.INTERNAL, 0, 1, List.of(), OtlpSpan.StatusCode.UNSET, null);
        JsonNode out = mapper.readTree(OtlpSpanSerializer.toJson(List.of(span), "s"))
                .get("resourceSpans").get(0).get("scopeSpans").get(0).get("spans").get(0);
        assertFalse(out.has("parentSpanId"));
        assertEquals(1, out.get("kind").asInt());   // INTERNAL
        assertEquals(0, out.get("status").get("code").asInt()); // UNSET
        assertTrue(out.get("status").has("code"));
    }

    @Test
    void mapsErrorStatusAndClientKind() throws Exception {
        OtlpSpan span = new OtlpSpan("t", "s", "p", "x",
                OtlpSpan.Kind.CLIENT, 0, 1, List.of(), OtlpSpan.StatusCode.ERROR, "boom");
        JsonNode out = mapper.readTree(OtlpSpanSerializer.toJson(List.of(span), "s"))
                .get("resourceSpans").get(0).get("scopeSpans").get(0).get("spans").get(0);
        assertEquals("p", out.get("parentSpanId").asText());
        assertEquals(3, out.get("kind").asInt());   // CLIENT
        assertEquals(2, out.get("status").get("code").asInt()); // ERROR
        assertEquals("boom", out.get("status").get("message").asText());
    }
}
