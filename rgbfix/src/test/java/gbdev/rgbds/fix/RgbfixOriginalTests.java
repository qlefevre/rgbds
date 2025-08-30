/**
 * MIT License
 * 
 * Copyright (c) 2014-2025, Quentin Lefèvre and the RGBDS-JVM contributors.
 * Copyright (c) 1996-2025, Carsten Sørensen and the RGBDS contributors (test materials).
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package gbdev.rgbds.fix;

import org.junit.Test;
import org.junit.BeforeClass;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * JUnit tests for the Rgbfix class using the full set of test materials
 * originally created by the RGBDS project.
 */
public class RgbfixOriginalTests {

    private static File testOutputDir;
    private static File testsRootDir;

    @BeforeClass
    public static void setup() throws IOException {
        testsRootDir = new File("src/test/resources/fix");
        testOutputDir = new File("target/test-output/fix");
        if (!testOutputDir.exists()) {
            testOutputDir.mkdirs();
        }
    }

    @Test
    public void testAllCases() throws IOException {
        File[] binFiles = testsRootDir.listFiles(file -> file.getName().endsWith(".bin"));
        Arrays.sort(binFiles);

        assertNotNull("Test directory not found or empty", binFiles);
        for (File binFile : binFiles) {
            String baseName = binFile.getName().replace(".bin", "");
            File flagsFile = new File(testsRootDir, baseName + ".flags");
            File referenceGb = new File(testsRootDir, baseName + ".gb");
            File outputGb = new File(testOutputDir, baseName + ".out.gb");

            System.out.println("Test: "+baseName);

            // Copie de l'input
            Files.copy(binFile.toPath(), outputGb.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // Lire les flags (ligne unique)
            List<String> flagLines = Collections.emptyList();
            if(flagsFile.exists()){
                flagLines = Files.readAllLines(flagsFile.toPath());
            }
            // extract arguments
            String line = flagLines.isEmpty() ? "" : flagLines.get(0).trim();
            List<String> tokens = new ArrayList<>();
            Matcher m = Pattern.compile("\"([^\"]*)\"|'([^']*)'|(\\S+)").matcher(line);
            while (m.find()) {
                if (m.group(1) != null) {
                    tokens.add(m.group(1));
                } else if(m.group(2) != null) {
                    tokens.add(m.group(2));
                } else {
                    tokens.add(m.group(3));
                }
            }
            String[] flags = tokens.toArray(new String[0]);

            // Concaténer les flags + le chemin de sortie
            String[] args = Stream.concat(
                    Arrays.stream(flags),
                    Stream.of(outputGb.getAbsolutePath())
            ).toArray(String[]::new);

            String msg = String.format("Test failed for: %s\nrgbfix %s\n\n"
                        +"meld <(hexdump -C %s) <(hexdump -C %s)\n\n", baseName,
                        Arrays.stream(args).collect(Collectors.joining(" ")),
                        referenceGb.getAbsolutePath(),outputGb.getAbsolutePath());

            // Exécuter le programme
            assertEquals(msg,0,Rgbfix.execute(args));

            // Lire les deux fichiers binaires
            byte[] actual = Files.readAllBytes(outputGb.toPath());
            byte[] expected = Files.readAllBytes(referenceGb.toPath());

            // Vérification binaire
            assertArrayEquals(msg, expected, actual);
            
        }
    }
}
