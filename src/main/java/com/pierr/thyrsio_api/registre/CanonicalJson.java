package com.pierr.thyrsio_api.registre;

import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * Un JSON toujours écrit de la même façon (clés triées, nombres sans zéros inutiles) :
 * c'est ce texte exact qui est scellé par l'empreinte, puis relu tel quel pour la vérifier.
 * Volontairement minimal, sans dépendre des réglages de la bibliothèque JSON de l'application.
 */
final class CanonicalJson {

    private CanonicalJson() {
    }

    static String write(Object value) {
        StringBuilder out = new StringBuilder();
        append(out, value);
        return out.toString();
    }

    private static void append(StringBuilder out, Object value) {
        switch (value) {
            case null -> out.append("null");
            case Boolean b -> out.append(b);
            case BigDecimal d -> out.append(d.signum() == 0 ? "0" : d.stripTrailingZeros().toPlainString());
            case Integer i -> out.append(i);
            case Long l -> out.append(l);
            case Short s -> out.append(s);
            case Number n -> append(out, new BigDecimal(n.toString()));
            case Map<?, ?> map -> {
                out.append('{');
                boolean first = true;
                for (Map.Entry<String, Object> entry : sorted(map).entrySet()) {
                    if (!first) out.append(',');
                    first = false;
                    string(out, entry.getKey());
                    out.append(':');
                    append(out, entry.getValue());
                }
                out.append('}');
            }
            case Collection<?> list -> {
                out.append('[');
                boolean first = true;
                for (Object item : list) {
                    if (!first) out.append(',');
                    first = false;
                    append(out, item);
                }
                out.append(']');
            }
            case TemporalAccessor temporal -> string(out, temporal.toString());
            default -> string(out, value.toString());
        }
    }

    private static Map<String, Object> sorted(Map<?, ?> map) {
        Map<String, Object> sorted = new TreeMap<>();
        map.forEach((key, value) -> sorted.put(String.valueOf(key), value));
        return sorted;
    }

    private static void string(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
