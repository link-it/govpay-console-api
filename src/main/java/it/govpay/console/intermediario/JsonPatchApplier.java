package it.govpay.console.intermediario;

import java.util.List;
import java.util.Objects;

import org.openapitools.jackson.nullable.JsonNullable;

import it.govpay.console.model.JsonPatchOperation;
import it.govpay.console.web.BadRequestException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Applicazione minimale di un documento JSON Patch (RFC 6902) su una
 * rappresentazione piatta (pointer a singolo segmento top-level, es.
 * {@code /denominazione}). Supporta {@code add}, {@code replace}, {@code remove},
 * {@code test}, {@code move}, {@code copy}. Pointer annidati o sull'intero
 * documento non sono supportati e causano 400.
 */
public final class JsonPatchApplier {

    private JsonPatchApplier() {
    }

    public static ObjectNode apply(ObjectNode target, List<JsonPatchOperation> operations,
                                   ObjectMapper objectMapper) {
        ObjectNode result = target.deepCopy();
        for (JsonPatchOperation op : operations) {
            applyOne(result, op, objectMapper);
        }
        return result;
    }

    private static void applyOne(ObjectNode node, JsonPatchOperation op, ObjectMapper objectMapper) {
        if (op.getOp() == null) {
            throw new BadRequestException("Operazione JSON Patch priva di 'op'.");
        }
        String field = field(op.getPath());
        switch (op.getOp()) {
            case ADD, REPLACE -> {
                if (op.getOp() == JsonPatchOperation.OpEnum.REPLACE && !node.has(field)) {
                    throw new BadRequestException(
                            "Operazione 'replace' su campo inesistente: " + op.getPath() + ".");
                }
                node.set(field, valueNode(op, objectMapper));
            }
            case REMOVE -> {
                requireExisting(node, field, op.getPath());
                node.remove(field);
            }
            case TEST -> {
                requireExisting(node, field, op.getPath());
                JsonNode expected = valueNode(op, objectMapper);
                if (!Objects.equals(node.get(field), expected)) {
                    throw new BadRequestException(
                            "Operazione 'test' fallita sul campo " + op.getPath() + ".");
                }
            }
            case MOVE -> {
                String from = field(op.getFrom());
                requireExisting(node, from, op.getFrom());
                JsonNode moved = node.get(from);
                node.remove(from);
                node.set(field, moved);
            }
            case COPY -> {
                String from = field(op.getFrom());
                requireExisting(node, from, op.getFrom());
                node.set(field, node.get(from).deepCopy());
            }
        }
    }

    private static String field(String pointer) {
        if (pointer == null || !pointer.startsWith("/")) {
            throw new BadRequestException(
                    "JSON Pointer non valido: '" + pointer + "'. Atteso un campo top-level (es. /denominazione).");
        }
        String segment = pointer.substring(1);
        if (segment.isEmpty() || segment.contains("/")) {
            throw new BadRequestException(
                    "JSON Pointer non supportato: '" + pointer + "'. Ammessi solo campi top-level.");
        }
        return segment.replace("~1", "/").replace("~0", "~");
    }

    private static void requireExisting(ObjectNode node, String field, String pointer) {
        if (!node.has(field)) {
            throw new BadRequestException("Campo inesistente per l'operazione JSON Patch: " + pointer + ".");
        }
    }

    private static JsonNode valueNode(JsonPatchOperation op, ObjectMapper objectMapper) {
        JsonNullable<Object> value = op.getValue();
        if (value == null || !value.isPresent()) {
            throw new BadRequestException(
                    "Operazione '" + op.getOp().getValue() + "' priva di 'value' su " + op.getPath() + ".");
        }
        return objectMapper.valueToTree(value.get());
    }
}
