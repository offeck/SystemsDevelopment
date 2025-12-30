package spl.lae;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExamplesTest {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Pattern examplePattern = Pattern.compile("^example(\\d+)\\.json$");

    static Stream<Arguments> exampleInputsAndExpectedOutputs() throws IOException {
        Path examplesDir = Path.of("examples");
        if (!Files.isDirectory(examplesDir)) {
            throw new IllegalStateException("Expected examples directory at: " + examplesDir.toAbsolutePath());
        }

        List<Arguments> testCases = new ArrayList<>();
        try (Stream<Path> paths = Files.list(examplesDir)) {
            List<Path> allFiles = paths.filter(Files::isRegularFile).collect(Collectors.toList());

            for (Path path : allFiles) {
                String fileName = path.getFileName().toString();

                // Case 1: exampleN.json -> outN.json
                Matcher matcher = examplePattern.matcher(fileName);
                if (matcher.matches()) {
                    String id = matcher.group(1);
                    Path expectedOutput = examplesDir.resolve("out" + id + ".json");
                    if (Files.exists(expectedOutput)) {
                        testCases.add(Arguments.of(fileName, expectedOutput.getFileName().toString(), 1));
                        testCases.add(Arguments.of(fileName, expectedOutput.getFileName().toString(), 4));
                    } else {
                        throw new IllegalStateException("Input file " + fileName + " exists but output file out" + id + ".json is missing.");
                    }
                }
                // Case 2: X.json -> X_out.json (excluding files that are themselves outputs)
                else if (fileName.endsWith(".json") && !fileName.endsWith("_out.json") && !fileName.startsWith("out")) {
                    String baseName = fileName.substring(0, fileName.length() - 5);
                    Path expectedOutput = examplesDir.resolve(baseName + "_out.json");
                    if (Files.exists(expectedOutput)) {
                        testCases.add(Arguments.of(fileName, expectedOutput.getFileName().toString(), 1));
                        testCases.add(Arguments.of(fileName, expectedOutput.getFileName().toString(), 4));
                    } else {
                         throw new IllegalStateException("Input file " + fileName + " exists but output file " + baseName + "_out.json is missing.");
                    }
                }
            }
        }

        if (testCases.isEmpty()) {
            throw new IllegalStateException("No valid test pairs found under: " + examplesDir.toAbsolutePath());
        }

        // Sort by input filename for consistent test order
        testCases.sort(Comparator.comparing(arg -> (String) arg.get()[0]));

        return testCases.stream();
    }

    @ParameterizedTest(name = "{0} with {2} threads")
    @MethodSource("exampleInputsAndExpectedOutputs")
    void examplesMatchExpectedOutputs(String inputFileName, String outputFileName, int threads, @TempDir Path tempDir) throws Exception {
        Path input = Path.of("examples", inputFileName);
        Path expectedOutput = Path.of("examples", outputFileName);
        assertTrue(Files.isRegularFile(input), "Missing input file: " + input.toAbsolutePath());
        assertTrue(Files.isRegularFile(expectedOutput), "Missing expected output file: " + expectedOutput.toAbsolutePath());

        Path actualOutput = tempDir.resolve("actual_" + outputFileName);
        Main.main(new String[] { String.valueOf(threads), input.toString(), actualOutput.toString() });

        assertTrue(Files.isRegularFile(actualOutput), "Main did not create output file: " + actualOutput.toAbsolutePath());

        JsonNode expected = mapper.readTree(expectedOutput.toFile());
        JsonNode actual = mapper.readTree(actualOutput.toFile());
        assertJsonEquals(expected, actual);
    }

    private void assertJsonEquals(JsonNode expected, JsonNode actual) {
        if (expected.isNumber() && actual.isNumber()) {
            assertEquals(expected.asDouble(), actual.asDouble(), 0.0001, "Number mismatch: expected " + expected + " but was " + actual);
        } else if (expected.isArray() && actual.isArray()) {
            assertEquals(expected.size(), actual.size(), "Array size mismatch");
            for (int i = 0; i < expected.size(); i++) {
                assertJsonEquals(expected.get(i), actual.get(i));
            }
        } else if (expected.isObject() && actual.isObject()) {
            assertEquals(expected.size(), actual.size(), "Object size mismatch");
            expected.fieldNames().forEachRemaining(field -> {
                assertTrue(actual.has(field), "Missing field: " + field);
                assertJsonEquals(expected.get(field), actual.get(field));
            });
        } else {
            assertEquals(expected, actual);
        }
    }

    @Test
    void examplesFolderLooksComplete() {
        Path examplesDir = Path.of("examples");
        assertTrue(Files.isDirectory(examplesDir), "Missing examples directory: " + examplesDir.toAbsolutePath());
        assertTrue(Files.exists(examplesDir.resolve("example1.json")), "Expected examples/example1.json to exist");
        assertTrue(Files.exists(examplesDir.resolve("out1.json")), "Expected examples/out1.json to exist");
    }
}

