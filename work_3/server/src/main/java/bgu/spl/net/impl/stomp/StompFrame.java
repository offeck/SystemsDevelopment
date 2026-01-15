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
            String[] header = lines[i].split(":");
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

    // Better parse method
    public static StompFrame fromString(String msg) {
        int firstNewLine = msg.indexOf('\n');
        if (firstNewLine == -1)
            return new StompFrame(msg.trim()); // Just command?

        String command = msg.substring(0, firstNewLine).trim();
        StompFrame frame = new StompFrame(command);

        int headersStart = firstNewLine + 1;
        int emptyLineIndex = msg.indexOf("\n\n", headersStart);
        // Handle \r\n
        // Simply parsing line by line until empty line is safer.

        String remaining = msg.substring(headersStart);
        if (emptyLineIndex == -1) {
            // No body, just headers maybe? Or malformed?
            // Case: Headers end, then EOF.
            // Check if there is an empty line at all.
            // Let's assume split is safer for headers.
            String[] parts = remaining.split("\n\n", 2);
            String headersPart = parts[0];
            String bodyPart = parts.length > 1 ? parts[1] : "";

            // Parse headers
            for (String line : headersPart.split("\n")) {
                int colon = line.indexOf(':');
                if (colon != -1) {
                    frame.addHeader(line.substring(0, colon), line.substring(colon + 1));
                }
            }
            frame.setBody(bodyPart);
        } else {
            // ...
        }
        // Actually, let's redo parse logic to be robust.
        // Split by first "\n\n" to separate headers and body
        // The command is the first line of the first part.

        int doubleNewLine = msg.indexOf("\n\n");
        String headersSection;
        String bodySection = "";

        if (doubleNewLine != -1) {
            headersSection = msg.substring(0, doubleNewLine);
            bodySection = msg.substring(doubleNewLine + 2);
        } else {
            headersSection = msg;
        }

        String[] headerLines = headersSection.split("\n");
        if (headerLines.length > 0) {
            frame = new StompFrame(headerLines[0].trim());
            for (int k = 1; k < headerLines.length; k++) {
                String line = headerLines[k];
                int colon = line.indexOf(':');
                if (colon != -1) {
                    frame.addHeader(line.substring(0, colon), line.substring(colon + 1));
                }
            }
        }
        frame.setBody(bodySection); // Body might contain null char? EncDec removes it.
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
        sb.append("\u0000");
        return sb.toString();
    }
}
