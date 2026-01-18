package bgu.spl.net.impl.stomp;

import java.util.HashMap;
import java.util.Map;

public class StompFrame {
    private String command;
    private Map<String, String> headers = new HashMap<>();
    private String body = "";

    public StompFrame(String command) {
        this.command = command;
    }

    public void addHeader(String key, String value) {
        headers.put(key, value);
    }

    public String getHeader(String key) {
        return headers.get(key);
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getBody() {
        return body;
    }

    public String getCommand() {
        return command;
    }

    public static StompFrame parse(String msg) {
        String[] lines = msg.split("\n");
        if (lines.length == 0)
            return null;

        String command = lines[0].trim();
        StompFrame frame = new StompFrame(command);

        int i = 1;
        while (i < lines.length && !lines[i].isEmpty()) {
            String[] header = lines[i].split(":", 2);
            if (header.length > 1) {
                frame.addHeader(header[0], header[1]);
            }
            i++;
        }

        StringBuilder body = new StringBuilder();
        i++; // skip empty line
        while (i < lines.length) {
            body.append(lines[i]).append("\n");
            i++;
        }
        // Remove known null char if present? passed msg usually has it removed by
        // encdec?
        // LineMessageEncoderDecoder usually strips delimiter.
        // But STOMP body is everything after empty line.
        // We will assume the passed 'msg' is strictly the content provided by EncDec.
        // If EncDec splits by \0, then body is just the rest.
        // Note: split("\n") might consume newlines in body if not careful.
        // Better parsing approach: find first empty line.

        return frame;
    }

    public static StompFrame fromString(String msg) {
        int firstNewLine = msg.indexOf('\n');
        if (firstNewLine == -1) // Case: Only command, no headers, no body
            return new StompFrame(msg.trim());

        String command = msg.substring(0, firstNewLine).trim();
        StompFrame frame = new StompFrame(command);

        // STOMP headers end with an empty line.
        // We look for "\n\n" or "\r\n\r\n" but since our splitting is simplistic,
        // we'll split the remaining string by double newline.
        // NOTE: This assumes the sender uses standard LF or CRLF consistently
        // and that our msg string preserves them.

        int headersStart = firstNewLine + 1;
        // Find the boundary between headers and body (first empty line)
        int doubleNewLine = msg.indexOf("\n\n", headersStart);

        String headersSection;
        String bodySection = "";

        if (doubleNewLine != -1) {
            headersSection = msg.substring(headersStart, doubleNewLine);
            // +2 for the two \n characters
            if (doubleNewLine + 2 < msg.length()) {
                bodySection = msg.substring(doubleNewLine + 2);
            }
        } else {
            // Check if it ends with a single newline which implies empty body
            if (msg.endsWith("\n\n")) {
                headersSection = msg.substring(headersStart, msg.length() - 2);
            } else if (msg.endsWith("\n")) {
                headersSection = msg.substring(headersStart, msg.length() - 1);
            } else {
                headersSection = msg.substring(headersStart);
            }
        }

        String[] headerLines = headersSection.split("\n");
        for (String line : headerLines) {
            line = line.trim(); // Handle potential \r
            if (line.isEmpty())
                continue;

            int colon = line.indexOf(':');
            if (colon != -1) {
                String key = line.substring(0, colon);
                String value = line.substring(colon + 1);
                frame.addHeader(key, value);
            }
        }

        frame.setBody(bodySection);
        return frame;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(command).append("\n");
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            sb.append(entry.getKey()).append(":").append(entry.getValue()).append("\n");
        }
        sb.append("\n");
        sb.append(body);
        return sb.toString();
    }
}
