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

        List<Integer> exampleIds = new ArrayList<>();
        try (Stream<Path> paths = Files.list(examplesDir)) {
            paths.forEach(path -> {
                String fileName = path.getFileName().toString();
                Matcher matcher = examplePattern.matcher(fileName);
                if (matcher.matches()) {
                    exampleIds.add(Integer.parseInt(matcher.group(1)));
                }
            });
        }
        exampleIds.sort(Comparator.naturalOrder());

        if (exampleIds.isEmpty()) {
            throw new IllegalStateException("No example*.json files found under: " + examplesDir.toAbsolutePath());
        }

        return exampleIds.stream()
                .flatMap(exampleId -> Stream.of(1, 4).map(threads -> Arguments.of(exampleId, threads)));
    }

    @ParameterizedTest(name = "example{0}.json with {1} threads")
    @MethodSource("exampleInputsAndExpectedOutputs")
    void examplesMatchExpectedOutputs(int exampleId, int threads, @TempDir Path tempDir) throws Exception {
        Path input = Path.of("examples", "example" + exampleId + ".json");
        Path expectedOutput = Path.of("examples", "out" + exampleId + ".json");
        assertTrue(Files.isRegularFile(input), "Missing input file: " + input.toAbsolutePath());
        assertTrue(Files.isRegularFile(expectedOutput), "Missing expected output file: " + expectedOutput.toAbsolutePath());

        Path actualOutput = tempDir.resolve("out" + exampleId + "_t" + threads + ".json");
        Main.main(new String[] { String.valueOf(threads), input.toString(), actualOutput.toString() });

        assertTrue(Files.isRegularFile(actualOutput), "Main did not create output file: " + actualOutput.toAbsolutePath());

        JsonNode expected = mapper.readTree(expectedOutput.toFile());
        JsonNode actual = mapper.readTree(actualOutput.toFile());
        assertEquals(expected, actual);
    }

    @Test
    void examplesFolderLooksComplete() {
        Path examplesDir = Path.of("examples");
        assertTrue(Files.isDirectory(examplesDir), "Missing examples directory: " + examplesDir.toAbsolutePath());
        assertTrue(Files.exists(examplesDir.resolve("example1.json")), "Expected examples/example1.json to exist");
        assertTrue(Files.exists(examplesDir.resolve("out1.json")), "Expected examples/out1.json to exist");
    }
}

