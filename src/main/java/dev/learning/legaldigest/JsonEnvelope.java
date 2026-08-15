package dev.learning.legaldigest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JsonEnvelope {
    record Envelope(boolean ok, Map<String, Object> data, Map<String, Object> error) {
    }

    static Envelope parse(String source) {
        Object root = new Parser(source).readValue();
        if (!(root instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("Response envelope must be a JSON object");
        }
        Map<String, Object> body = stringMap(raw);
        boolean ok = Boolean.TRUE.equals(body.get("ok"));
        return new Envelope(ok, mapValue(body.get("data")), mapValue(body.get("error")));
    }

    private static Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? stringMap(map) : Map.of();
    }

    private static Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static final class Parser {
        private final String source;
        private int position;

        private Parser(String source) {
            this.source = source;
        }

        private Object readValue() {
            skipWhitespace();
            if (peek('{')) return readObject();
            if (peek('[')) return readArray();
            if (peek('"')) return readString();
            if (source.startsWith("true", position)) { position += 4; return true; }
            if (source.startsWith("false", position)) { position += 5; return false; }
            if (source.startsWith("null", position)) { position += 4; return null; }
            if (peek('-') || (position < source.length() && Character.isDigit(source.charAt(position)))) {
                return readNumber();
            }
            throw new IllegalArgumentException("Unexpected JSON value at character " + position);
        }

        private List<Object> readArray() {
            expect('[');
            List<Object> values = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) { position++; return values; }
            while (true) {
                values.add(readValue());
                skipWhitespace();
                if (peek(']')) { position++; return values; }
                expect(',');
            }
        }

        private Number readNumber() {
            int start = position;
            if (peek('-')) position++;
            while (position < source.length() && Character.isDigit(source.charAt(position))) position++;
            if (peek('.')) {
                position++;
                while (position < source.length() && Character.isDigit(source.charAt(position))) position++;
            }
            if (peek('e') || peek('E')) {
                position++;
                if (peek('+') || peek('-')) position++;
                while (position < source.length() && Character.isDigit(source.charAt(position))) position++;
            }
            String number = source.substring(start, position);
            return number.contains(".") || number.contains("e") || number.contains("E")
                    ? Double.parseDouble(number)
                    : Long.parseLong(number);
        }

        private Map<String, Object> readObject() {
            expect('{');
            Map<String, Object> values = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) { position++; return values; }
            while (true) {
                String key = readString();
                skipWhitespace();
                expect(':');
                values.put(key, readValue());
                skipWhitespace();
                if (peek('}')) { position++; return values; }
                expect(',');
                skipWhitespace();
            }
        }

        private String readString() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (position < source.length()) {
                char current = source.charAt(position++);
                if (current == '"') return value.toString();
                if (current == '\\') {
                    char escaped = source.charAt(position++);
                    value.append(switch (escaped) {
                        case '"', '\\', '/' -> escaped;
                        case 'b' -> '\b';
                        case 'f' -> '\f';
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        case 'u' -> readUnicode();
                        default -> throw new IllegalArgumentException("Invalid JSON escape");
                    });
                } else {
                    value.append(current);
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string");
        }

        private char readUnicode() {
            int end = position + 4;
            if (end > source.length()) throw new IllegalArgumentException("Invalid Unicode escape");
            char decoded = (char) Integer.parseInt(source.substring(position, end), 16);
            position = end;
            return decoded;
        }

        private void skipWhitespace() {
            while (position < source.length() && Character.isWhitespace(source.charAt(position))) position++;
        }

        private boolean peek(char expected) {
            return position < source.length() && source.charAt(position) == expected;
        }

        private void expect(char expected) {
            skipWhitespace();
            if (!peek(expected)) throw new IllegalArgumentException("Expected '" + expected + "'");
            position++;
        }
    }
}
