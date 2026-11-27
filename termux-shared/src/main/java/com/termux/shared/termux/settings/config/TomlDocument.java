package com.termux.shared.termux.settings.config;

import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlPosition;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/** Uses TOML parser locations to change a single assignment without reformatting the file. */
final class TomlDocument {
    final String text;
    final TomlParseResult parsed;

    TomlDocument(String text) throws IOException {
        this.text = text;
        try {
            parsed = Toml.parse(text);
        } catch (StackOverflowError e) {
            throw new IOException("TOML nesting is too deep", e);
        }
        if (parsed.hasErrors()) throw new IOException(parsed.errors().get(0).toString());
    }

    TomlDocument set(ConfigKeys.Key<?> key, Object value) throws IOException {
        String encoded = encode(value);
        TomlPosition existing = parsed.inputPositionOf(key.path);
        if (existing != null) {
            int begin = assignmentValueStart(offset(existing));
            if (begin >= 0) {
                TomlDocument result = change(key, value, begin, valueEnd(begin), encoded);
                if (result != null) return result;
            }
        } else {
            TomlDocument result = insert(key, value, encoded);
            if (result != null) return result;
        }
        throw new IOException("Cannot safely edit " + key.path + " in this TOML layout. Edit the key in the file and reload.");
    }

    private TomlDocument insert(ConfigKeys.Key<?> key, Object value, String encoded) {
        int dot = key.path.indexOf('.');
        if (dot < 0) return change(key, value, 0, 0, key.path + " = " + encoded + newline());
        String section = key.path.substring(0, dot);
        String leaf = key.path.substring(dot + 1);
        String line = leaf + " = " + encoded + newline();
        TomlPosition sectionPosition = parsed.inputPositionOf(section);
        if (sectionPosition != null) {
            int begin = offset(sectionPosition);
            int after = endOfLine(begin);
            if (text.charAt(begin) == '[') {
                int next = after < text.length() ? after + 1 : after;
                String separator = next == after ? newline() : "";
                TomlDocument result = change(key, value, next, next, separator + line);
                if (result != null) return result;
            } else {
                int start = assignmentValueStart(begin);
                if (start >= 0 && text.charAt(start) == '{') {
                    int closing = valueEnd(start) - 1;
                    String separator = text.substring(start + 1, closing).trim().isEmpty() ? " " : ", ";
                    TomlDocument result = change(key, value, closing, closing, separator + leaf + " = " + encoded + " ");
                    if (result != null) return result;
                }
            }
        }
        TomlDocument result = change(key, value, text.length(), text.length(),
            newline() + "[" + section + "]" + newline() + line);
        if (result != null) return result;
        return change(key, value, 0, 0, key.path + " = " + encoded + newline());
    }

    private String newline() {
        return text.contains("\r\n") ? "\r\n" : "\n";
    }

    private TomlDocument change(ConfigKeys.Key<?> key, Object value, int begin, int end, String replacement) {
        try {
            TomlDocument result = new TomlDocument(text.substring(0, begin) + replacement + text.substring(end));
            Object decoded = key.decoder.apply(result.parsed.get(key.path));
            if (!Objects.equals(value, decoded)) return null;
            // Confirm that locations in unusual but valid TOML layouts did not affect any other key.
            for (String name : parsed.dottedKeySet(false)) {
                if (name.equals(key.path) || name.startsWith(key.path + ".")) continue;
                if (!sameValue(parsed.get(name), result.parsed.get(name))) return null;
            }
            return result;
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean sameValue(Object expected, Object actual) {
        if (expected instanceof TomlArray && actual instanceof TomlArray) return Toml.equals((TomlArray) expected, (TomlArray) actual);
        if (expected instanceof TomlTable && actual instanceof TomlTable) return Toml.equals((TomlTable) expected, (TomlTable) actual);
        return Objects.equals(expected, actual);
    }

    private int offset(TomlPosition position) {
        int index = 0;
        for (int line = 1; line < position.line(); line++) index = endOfLine(index) + 1;
        return text.offsetByCodePoints(index, position.column() - 1);
    }

    private int endOfLine(int index) {
        int next = text.indexOf('\n', index);
        return next == -1 ? text.length() : next;
    }

    private int assignmentValueStart(int start) {
        for (int index = start; index < text.length(); index++) {
            char c = text.charAt(index);
            if (c == '"' || c == '\'') {
                index = quotedEnd(index) - 1;
            } else if (c == '=') {
                int value = index + 1;
                while (value < text.length() && (text.charAt(value) == ' ' || text.charAt(value) == '\t')) value++;
                return value < text.length() ? value : -1;
            } else if (c == '[' || c == '\n' || c == '#' || c == '}' || c == ',') {
                return -1;
            }
        }
        return -1;
    }

    private int valueEnd(int start) {
        int depth = 0;
        int index = start;
        for (; index < text.length(); index++) {
            char c = text.charAt(index);
            if (depth == 0 && (c == '\n' || c == '\r' || c == '#' || c == ',' || c == '}')) break;
            if (c == '"' || c == '\'') index = quotedEnd(index) - 1;
            else if (c == '[' || c == '{') depth++;
            else if (c == ']' || c == '}') depth--;
            else if (c == '#') index = endOfLine(index);
        }
        while (index > start && (text.charAt(index - 1) == ' ' || text.charAt(index - 1) == '\t')) index--;
        return index;
    }

    private int quotedEnd(int start) {
        char quote = text.charAt(start);
        boolean multiline = start + 2 < text.length() && text.charAt(start + 1) == quote && text.charAt(start + 2) == quote;
        for (int index = start + (multiline ? 3 : 1); index < text.length(); index++) {
            char c = text.charAt(index);
            if (quote == '"' && c == '\\') {
                index++;
            } else if (c == quote) {
                if (!multiline) return index + 1;
                int end = index + 1;
                while (end < text.length() && text.charAt(end) == quote) end++;
                if (end - index >= 3) return end;
                index = end - 1;
            }
        }
        return text.length();
    }

    private static String encode(Object value) {
        if (value instanceof String) return "\"" + Toml.tomlEscape((String) value) + "\"";
        if (value instanceof List) {
            StringJoiner entries = new StringJoiner(", ", "[", "]");
            for (Object item : (List<?>) value) entries.add(encode(item));
            return entries.toString();
        }
        if (value instanceof Map) {
            StringJoiner entries = new StringJoiner(", ", "{", "}");
            for (Map.Entry<?, ?> item : ((Map<?, ?>) value).entrySet()) {
                entries.add(encode(item.getKey()) + " = " + encode(item.getValue()));
            }
            return entries.toString();
        }
        return value.toString();
    }
}
