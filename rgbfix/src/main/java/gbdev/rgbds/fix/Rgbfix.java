package gbdev.rgbds.fix;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import java.util.concurrent.Callable;
import java.util.Arrays;
import java.util.List;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

@Command(
    name = "rgbfix",
    mixinStandardHelpOptions = true,
    version = "rgbfix 1.0.0",
    description = "Game Boy ROM header utility and checksum fixer."
)
public class Rgbfix implements Callable<Integer> {

    @Option(
        names = {"-C", "--color-only"},
        description = "Set the Game Boy Color-only flag (0x143) to 0xC0."
    )
    private boolean colorOnly;

    @Option(
        names = {"-c", "--color-compatible"},
        description = "Set the Game Boy Color-compatible flag (0x143) to 0x80."
    )
    private boolean colorCompatible;

    @Option(
        names = {"-f", "--fix-spec"},
        description = "Fix certain header values (l=logo, h=header checksum, g=global checksum)."
    )
    private String fixSpec;

    @Option(
        names = {"-i", "--game-id"},
        description = "Set the game ID string (0x13F-0x142)."
    )
    private String gameId;

    @Option(
        names = {"-j", "--non-japanese"},
        description = "Set the non-Japanese region flag (0x14A) to 0x01."
    )
    private boolean nonJapanese;

    @Option(
        names = {"-k", "--new-licensee"},
        description = "Set the new licensee string (0x144-0x145)."
    )
    private String newLicensee;

    @Option(
        names = {"-l", "--old-licensee"},
        description = "Set the old licensee code (0x14B) to a given value from 0 to 0xFF."
    )
    private String oldLicensee;

    @Option(
        names = {"-m", "--mbc-type"},
        description = "Set the MBC type (0x147) to a given value from 0 to 0xFF."
    )
    private String mbcType;

    @Option(
        names = {"-n", "--rom-version"},
        description = "Set the ROM version (0x14C) to a given value from 0 to 0xFF."
    )
    private String romVersion;

    @Option(
        names = {"-o", "--output"},
        description = "Write the modified ROM image to the given file."
    )
    private File outputFile;

    @Option(
        names = {"-p", "--pad-value"},
        description = "Pad the ROM image to a valid size with a given pad value from 0 to 255."
    )
    private String padValue;

    @Option(
        names = {"-r", "--ram-size"},
        description = "Set the RAM size (0x149) to a given value from 0 to 0xFF."
    )
    private Integer ramSize;

    @Option(
        names = {"-s", "--sgb-compatible"},
        description = "Set the SGB flag (0x146) to 0x03."
    )
    private boolean sgbCompatible;

    @Option(
        names = {"-t", "--title"},
        description = "Set the title string (0x134-0x143)."
    )
    private String title;

    @Option(
        names = {"-v", "--validate"},
        description = "Equivalent to -f lhg."
    )
    private boolean validate;

    @Option(
        names = {"-w"},
        description = "Disable all warning output."
    )
    private boolean disableWarnings;

    @Option(
        names = {"-W", "--warning"},
        description = "Set warning flag."
    )
    private String warning;

    @Parameters(
        paramLabel = "FILE",
        description = "ROM file(s) to fix."
    )
    private List<File> inputFiles;

    @Override
    public Integer call() throws Exception {
        if (inputFiles == null || inputFiles.isEmpty()) {
            System.err.println("Error: No input file specified.");
            return 1;
        }

        for (File inputFile : inputFiles) {
            byte[] rom = Files.readAllBytes(inputFile.toPath());
            if (rom.length < 0x150) {
                System.err.println("Error: ROM too small to contain a valid header.");
                return 1;
            }

            // Appliquer les options
            if (colorOnly) {
                rom[0x143] = (byte) 0xC0;
            } else if (colorCompatible) {
                rom[0x143] = (byte) 0x80;
            }

            // Fix certain header values (l=logo, h=header checksum, g=global checksum, L/H/G=trash).
            if (fixSpec != null) {
                applyFixSpec(rom, fixSpec);
            }

            // Option validate : Equivalent to -f lhg 
            if (validate) {
                applyFixSpec(rom, "lhg");
            }

            // Set the game ID string (0x13F-0x142).
            if (gameId != null) {
                setGameId(rom, gameId);
            }

            // Set the non-Japanese region flag (0x14A) to 0x01.
            rom[0x14A] = (byte)(nonJapanese ? 0x01 : 0x00);
            

        
            if (newLicensee != null) {
                setNewLicensee(rom, newLicensee);
            }

            // Set the old licensee code (0x14B) to a given value from 0 to 0xFF.
            if (oldLicensee != null) {
                rom[0x14B] = parseByte(oldLicensee);
            }

            // Set the MBC type (0x147) to a given value from 0 to 0xFF.
            if (mbcType != null) {
                setMbcType(rom, mbcType);
            }

            // Set the ROM version (0x14C) to a given value from 0 to 0xFF.
            if (romVersion != null) {
                rom[0x14C] = parseByte(romVersion);
            }

            // Set the SGB flag (0x146) to 0x03. 
            // This flag will be ignored by the SGB unless the old licensee code (--l) is 0x33!
            if (sgbCompatible && rom[0x14B] == 0x33) {
                rom[0x146] = 0x03;
            }

            // Sets the game title in the ROM header (addresses 0x134-0x143).
            if (title != null) {
                setTitle(rom, title);
            }

            // Pad the ROM image to a valid size with a given pad value from 0 to 255.
            if (padValue != null) {
                rom = padRom(rom, padValue);
            }

            // Écrire le résultat
            File outFile = outputFile != null ? outputFile : inputFile;
            Files.write(outFile.toPath(), rom, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("Fixed ROM written to: " + outFile.getName());
        }

        return 0;
    }

    private void applyFixSpec(byte[] rom, String spec) {
        if (spec.contains("l")) fixLogo(rom);
        if (spec.contains("h")) fixHeaderChecksum(rom);
        if (spec.contains("g")) fixGlobalChecksum(rom);
    }

    /**
     * Fixes the Nintendo logo in the ROM header (addresses 0x104–0x133).
     * The logo must match the official hexadecimal dump, otherwise the Game Boy boot ROM will reject the game.
     * The expected logo data is:
     * CE ED 66 66 CC 0D 00 0B 03 73 00 83 00 0C 00 0D
     * 00 08 11 1F 88 89 00 0E DC CC 6E E6 DD DD D9 99
     * BB BB 67 63 6E 0E EC CC DD DC 99 9F BB B9 33 3E
     */
    private void fixLogo(byte[] rom) {
        // Official Nintendo logo bytes (48 bytes total)
        byte[] officialLogo = new byte[] {
            (byte) 0xCE, (byte) 0xED, (byte) 0x66, (byte) 0x66, (byte) 0xCC, (byte) 0x0D, (byte) 0x00, (byte) 0x0B,
            (byte) 0x03, (byte) 0x73, (byte) 0x00, (byte) 0x83, (byte) 0x00, (byte) 0x0C, (byte) 0x00, (byte) 0x0D,
            (byte) 0x00, (byte) 0x08, (byte) 0x11, (byte) 0x1F, (byte) 0x88, (byte) 0x89, (byte) 0x00, (byte) 0x0E,
            (byte) 0xDC, (byte) 0xCC, (byte) 0x6E, (byte) 0xE6, (byte) 0xDD, (byte) 0xDD, (byte) 0xD9, (byte) 0x99,
            (byte) 0xBB, (byte) 0xBB, (byte) 0x67, (byte) 0x63, (byte) 0x6E, (byte) 0x0E, (byte) 0xEC, (byte) 0xCC,
            (byte) 0xDD, (byte) 0xDC, (byte) 0x99, (byte) 0x9F, (byte) 0xBB, (byte) 0xB9, (byte) 0x33, (byte) 0x3E
        };

        // Copy the official logo into the ROM header (0x104–0x133)
        System.arraycopy(officialLogo, 0, rom, 0x104, officialLogo.length);
        System.out.println("Fixed Nintendo logo in ROM header.");
    }

    /**
     * Fixes the header checksum at address 0x14D in the ROM.
     * The checksum is calculated from bytes 0x134-0x14C using 8-bit arithmetic:
     *   checksum = 0
     *   for each byte from 0x134 to 0x14C:
     *       checksum = checksum - byte - 1 (using 8-bit arithmetic)
     * The final 8-bit checksum value is stored at 0x14D.
     *
     * @param rom The ROM byte array to modify
     */
    private void fixHeaderChecksum(byte[] rom) {
        // Initialize checksum to 0 as a byte (8-bit)
        byte checksum = 0;

        // Calculate checksum from bytes 0x134 to 0x14C using 8-bit arithmetic
        for (int address = 0x134; address <= 0x14C; address++) {
            // Apply the checksum formula with proper 8-bit arithmetic
            checksum = (byte)(checksum - rom[address] - 1);
        }

        // Store the final checksum at 0x14D
        rom[0x14D] = checksum;

        System.out.println("Fixed header checksum at 0x14D. Value: 0x" +
                        String.format("%02X", checksum & 0xFF));
    }


    private void fixGlobalChecksum(byte[] rom) {
        // Implémenter la correction du checksum global
        System.out.println("Fixing global checksum...");
    }


    /**
     * Sets the Game ID (Manufacturer Code) in the ROM header.
     * The code is written to addresses 0x13F–0x142 as 4 uppercase ASCII characters.
     * If the input string is shorter than 4 characters, it is padded with spaces.
     * If the input string is longer than 4 characters, it is truncated to 4 characters.
     *
     * @param rom The ROM byte array to modify.
     * @param id  The Game ID (Manufacturer Code) string to set.
     */
    private void setGameId(byte[] rom, String id) {
        // Truncate to 4 characters or pad with spaces if shorter
        String gameId = (id.length() >= 4 ? id.substring(0, 4) : String.format("%-4s", id)).toUpperCase();

        // Write each character as a byte in the ROM header
        for (int i = 0; i < 4; i++) {
            char c = gameId.charAt(i);
            rom[0x13F + i] = (byte) c;
        }
    }


    private void setNewLicensee(byte[] rom, String licensee) {
        // Implémenter la définition du nouveau licensee
        System.out.println("Setting new licensee: " + licensee);
    }



    /**
     * Sets the MBC (Memory Bank Controller) type in the ROM header at address 0x147.
     * Supports both numeric values (0x00–0xFF) and named MBC types (e.g., "MBC1", "MBC5+RAM+BATTERY").
     * If the input is a named type, it is converted to the corresponding byte value.
     *
     * @param rom  The ROM byte array to modify.
     * @param type The MBC type as a string (name or hex value).
     */
    private void setMbcType(byte[] rom, String type) {
        byte mbcByte;

        // Try to parse as a hexadecimal number first
        try {
            mbcByte = parseByte(type);
        } catch (NumberFormatException e) {
            // If not a number, try to parse as a named MBC type
            mbcByte = parseMbcName(type.trim());
        }

        // Write the MBC type to the ROM header
        rom[0x147] = mbcByte;
    }

    /**
     * Maps a named MBC type (e.g., "MBC1", "MBC5+RAM+BATTERY") to its corresponding byte value.
     * Returns 0x00 (ROM ONLY) if the name is not recognized.
     *
     * @param mbcName The named MBC type.
     * @return The corresponding byte value for the MBC type.
     */
    private byte parseMbcName(String mbcName) {
        // Remove all whitespace and convert to uppercase for case-insensitive comparison
        String normalizedName = mbcName.replaceAll("\\s+", "").toUpperCase();

        // Map named MBC types to their byte values
        switch (normalizedName) {
            case "ROMONLY":
                return (byte) 0x00;
            case "MBC1":
                return (byte) 0x01;
            case "MBC1+RAM":
                return (byte) 0x02;
            case "MBC1+RAM+BATTERY":
                return (byte) 0x03;
            case "MBC2":
                return (byte) 0x05;
            case "MBC2+BATTERY":
                return (byte) 0x06;
            case "ROM+RAM":
                return (byte) 0x08;
            case "ROM+RAM+BATTERY":
                return (byte) 0x09;
            case "MMM01":
                return (byte) 0x0B;
            case "MMM01+RAM":
                return (byte) 0x0C;
            case "MMM01+RAM+BATTERY":
                return (byte) 0x0D;
            case "MBC3+TIMER+BATTERY":
                return (byte) 0x0F;
            case "MBC3+TIMER+RAM+BATTERY":
                return (byte) 0x10;
            case "MBC3":
                return (byte) 0x11;
            case "MBC3+RAM":
                return (byte) 0x12;
            case "MBC3+RAM+BATTERY":
                return (byte) 0x13;
            case "MBC5":
                return (byte) 0x19;
            case "MBC5+RAM":
                return (byte) 0x1A;
            case "MBC5+RAM+BATTERY":
                return (byte) 0x1B;
            case "MBC5+RUMBLE":
                return (byte) 0x1C;
            case "MBC5+RUMBLE+RAM":
                return (byte) 0x1D;
            case "MBC5+RUMBLE+RAM+BATTERY":
                return (byte) 0x1E;
            case "MBC6":
                return (byte) 0x20;
            case "MBC7+SENSOR+RUMBLE+RAM+BATTERY":
                return (byte) 0x22;
            case "POCKETCAMERA":
                return (byte) 0xFC;
            case "BANDAITAMA5":
                return (byte) 0xFD;
            case "HUC3":
                return (byte) 0xFE;
            case "HUC1+RAM+BATTERY":
                return (byte) 0xFF;
            default:
                System.err.println("Warning: Unknown MBC type '" + mbcName + "'. Defaulting to ROM ONLY (0x00).");
                return (byte) 0x00;
        }
    }

    /**
     * Parses a string representing a number in hexadecimal (e.g., "0xFF" or "FF"),
     * binary (e.g., "0b11110000"), or decimal (e.g., "255") and returns the corresponding byte value.
     *
     * @param input The string to parse (e.g., "0xFF", "0b11110000", or "255").
     * @return The parsed byte value.
     */
    public static byte parseByte(String input) {
        String trimmedInput = input.trim().toUpperCase();

        // Check for hexadecimal format (e.g., "0xFF" or "FF")
        if (trimmedInput.startsWith("0X")) {
            return (byte) Integer.parseInt(trimmedInput.substring(2), 16);
        }
        // Check for binary format (e.g., "0b11110000")
        else if (trimmedInput.startsWith("0B")) {
            return (byte) Integer.parseInt(trimmedInput.substring(2), 2);
        }
        // Check for hexadecimal without prefix (e.g., "FF")
        else if (trimmedInput.matches("[0-9A-F]+")) {
            return (byte) Integer.parseInt(trimmedInput, 16);
        }
        // Default to decimal format (e.g., "255")
        else {
            int decimalValue = Integer.parseInt(trimmedInput);
            if (decimalValue < 0 || decimalValue > 255) {            
                throw new NumberFormatException("Value out of byte range (0x00-0xFF): " + decimalValue);       
             }
            return (byte) decimalValue;
        }
    }

    /**
     * Sets the game title in the ROM header (addresses 0x134-0x143).
     * The title is converted to uppercase ASCII and truncated if necessary.
     * Remaining bytes are padded with null bytes (0x00).
     *
     * @param rom The ROM byte array to modify
     * @param title The title string to set
     * @param hasGameId Whether a game ID is specified (-i)
     * @param hasCgbFlag Whether the CGB flag is set (-c or -C)
     */
    private void setTitle(byte[] rom, String title) {
        // Determine maximum title length based on conditions
        int maxLength = 16;  // 0x134-0x143 (16 bytes)
        if (gameId != null) {
            maxLength = 11;  // 0x134-0x13E (11 bytes)
        } else if (colorCompatible || colorOnly) {
            maxLength = 15;  // 0x134-0x142 (15 bytes)
        }

        // Process the title: convert to uppercase and truncate if needed
        String processedTitle = title.toUpperCase();
        if (processedTitle.length() > maxLength) {
            processedTitle = processedTitle.substring(0, maxLength);
        }

        // Convert title to ASCII bytes
        byte[] titleBytes = processedTitle.getBytes(StandardCharsets.US_ASCII);

        // Clear the title area with null bytes
        Arrays.fill(rom, 0x134, 0x134 + maxLength, (byte) 0x00);

        // Copy the title bytes to the ROM header
        System.arraycopy(titleBytes, 0, rom, 0x134, titleBytes.length);

        System.out.println("Set title to: \"" + processedTitle + "\" (max " + maxLength + " chars)");
    }

    /**
     * Pads the ROM to the nearest valid Game Boy cartridge size (32 KiB, 64 KiB, ..., 8192 KiB)
     * using the specified pad value (0-255). Updates the cartridge size byte at 0x148 to reflect
     * the new size. Valid sizes are powers of 2 multiplied by 32 KiB (e.g., 32 KiB, 64 KiB, etc.).
     *
     * @param rom       The original ROM byte array.
     * @param padValue  The byte value (0-255) used to pad the ROM.
     * @return          The padded ROM as a new byte array.
     */
    private byte[] padRom(byte[] rom, String value) {
        // Validate pad value (must be 0-255)
        byte padValue = parseByte(value);

        // Valid ROM sizes in bytes (32 KiB to 8192 KiB)
        int[] validSizes = {
            32768, 65536, 131072, 262144, 524288, 1048576, 2097152, 4194304, 8388608
        };

        // Find the smallest valid size >= current ROM size
        int newSize = rom.length;
        for (int size : validSizes) {
            if (rom.length <= size) {
                newSize = size;
                break;
            }
        }

        // If ROM is already larger than the maximum valid size, don't pad
        if (newSize == rom.length) {
            System.out.println("ROM is already at a valid size (" + (newSize / 1024) + " KiB). No padding needed.");
            return rom;
        }

        // Create new array and copy original ROM
        byte[] paddedRom = new byte[newSize];
        Arrays.fill(paddedRom, padValue);
        System.arraycopy(rom, 0, paddedRom, 0, rom.length);

        // Update cartridge size byte at 0x148
        int sizeCode = 0;
        while (32768 << sizeCode < newSize) {
            sizeCode++;
        }
        paddedRom[0x148] = (byte) sizeCode;

        System.out.println("Padded ROM from " + (rom.length / 1024) + " KiB to " +
                        (newSize / 1024) + " KiB with value 0x" +
                        String.format("%02X", padValue) + ".");
        return paddedRom;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Rgbfix()).execute(args);
        System.exit(exitCode);
    }
}