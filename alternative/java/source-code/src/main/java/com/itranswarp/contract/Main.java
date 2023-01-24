package com.itranswarp.contract;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;

import org.bouncycastle.jcajce.provider.digest.Keccak;

public class Main {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage:\njava -jar contract-address-generator.jar <bytecode-file>");
            System.exit(1);
            return;
        }
        byte[] contractBytecode = null;
        String file = args[0];
        try {
            List<String> lines = Files.readAllLines(Paths.get(file), StandardCharsets.UTF_8);
            StringBuilder sb = new StringBuilder(256 * 1024);
            for (String line : lines) {
                line = line.strip();
                if (line.startsWith("0x")) {
                    line = line.substring(2);
                }
                sb.append(line);
            }
            String hex = sb.toString();
            contractBytecode = HexFormat.of().parseHex(hex);
        } catch (Exception e) {
            System.err.println("Error when read file: " + file + ". " + e.getMessage());
            System.exit(1);
            return;
        }
        Scanner scanner = new Scanner(System.in);
        byte[] constructorBytecode = readConstructor(scanner);
        byte[] address = readAddress(scanner);
        String prefix = readPrefix(scanner);
        long start = readStartFrom(scanner);
        System.out.print("Calculating...");
        byte[] bytecode = new byte[contractBytecode.length + constructorBytecode.length];
        System.arraycopy(contractBytecode, 0, bytecode, 0, contractBytecode.length);
        System.arraycopy(constructorBytecode, 0, bytecode, contractBytecode.length, constructorBytecode.length);
        byte[] bytecodeHash = keccak(bytecode);
        byte[] payload = new byte[1 + 20 + 32 + 32];
        payload[0] = (byte) 0xff;
        System.arraycopy(address, 0, payload, 1, 20);
        System.arraycopy(bytecodeHash, 0, payload, payload.length - 32, 32);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            long last = lastPoint;
            if (last != Long.MAX_VALUE) {
                System.out.println("Breaked at " + last);
            }
        }));
        for (long i = start; i < Long.MAX_VALUE; i++) {
            BigInteger bi = BigInteger.valueOf(i);
            byte[] biArr = bi.toByteArray();
            System.arraycopy(zeroSalt, 0, payload, 21, 32);
            System.arraycopy(biArr, 0, payload, 53 - biArr.length, biArr.length);
            byte[] payloadHash = keccak(payload);
            byte[] targetAddress = Arrays.copyOfRange(payloadHash, 12, 32);
            String targetChecksumAddress = toChecksumAddress(targetAddress);
            if (targetChecksumAddress.startsWith(prefix)) {
                System.out.println();
                System.out.println("FOUND! salt = 0x" + HexFormat.of().toHexDigits(i));
                System.out.println("target address = " + targetChecksumAddress);
                return;
            }
            lastPoint = i;
            if ((i & 0xfffffL) == 0L) {
                System.out.print('.');
            }
        }
        System.out.println("Sorry, salt not found.");
    }

    static long lastPoint = 0;

    static byte[] zeroSalt = new byte[32];

    static Pattern prefixPattern = Pattern.compile("^0x[0-9a-fA-F]+$");

    static String readPrefix(Scanner scanner) {
        String defaultPrefix = "0xCafe";
        for (;;) {
            System.out.print("Input contract prefix (" + defaultPrefix + "): ");
            String prefix = scanner.nextLine().strip();
            if (prefix.isEmpty()) {
                prefix = defaultPrefix;
            }
            try {
                if (!prefixPattern.matcher(prefix).matches()) {
                    throw new IllegalArgumentException("Invalid prefix: " + prefix);
                }
                if (prefix.length() > 10) {
                    throw new IllegalArgumentException("Prefix too long: " + prefix);
                }
                return prefix;
            } catch (IllegalArgumentException e) {
                System.err.println(e.getMessage());
            }
        }
    }

    static long readStartFrom(Scanner scanner) {
        for (;;) {
            System.out.print("Input start from (0): ");
            String start = scanner.nextLine().strip();
            if (start.isEmpty()) {
                return 0L;
            }
            long n = 0;
            try {
                if (start.startsWith("0x")) {
                    n = Long.parseLong(start.substring(2), 16);
                } else {
                    n = Long.parseLong(start);
                }
                if (n < 0) {
                    throw new NumberFormatException();
                }
                return n;
            } catch (NumberFormatException e) {
                System.err.println("Invalid start: " + start);
            }
        }
    }

    static byte[] readConstructor(Scanner scanner) {
        for (;;) {
            System.out.print("Input constructor bytecode (empty): ");
            String bytecode = scanner.nextLine();
            if (bytecode.startsWith("0x")) {
                bytecode = bytecode.substring(2);
            }
            try {
                return HexFormat.of().parseHex(bytecode);
            } catch (IllegalArgumentException e) {
                System.err.println("Invalid constructor bytecode.");
            }
        }
    }

    static byte[] readAddress(Scanner scanner) {
        String defaultAddr = "0xEa5837e1F89e3cf23027dA7866e6492458383B59";
        for (;;) {
            System.out.print("Input deploy address (" + defaultAddr + "): ");
            String address = scanner.nextLine().strip();
            if (address.isEmpty()) {
                address = defaultAddr;
            }
            try {
                if (!address.startsWith("0x")) {
                    throw new IllegalArgumentException("Invalid address: " + address);
                }
                byte[] addr = HexFormat.of().parseHex(address.substring(2));
                if (!address.toLowerCase().equals(address)) {
                    String checkedAddress = toChecksumAddress(addr);
                    if (!checkedAddress.equals(address)) {
                        throw new IllegalArgumentException("Invalid address: " + address);
                    }
                }
                return addr;
            } catch (IllegalArgumentException e) {
                System.err.println(e.getMessage());
            }
        }
    }

    static String toChecksumAddress(byte[] address) {
        String lowerAddress = "0x" + HexFormat.of().formatHex(address);
        char[] cs = lowerAddress.substring(2).toCharArray();
        byte[] hash = keccak(lowerAddress.substring(2).getBytes());
        for (int i = 0; i < 40; i++) {
            byte h = hash[i / 2];
            int n = i % 2 == 0 ? (h & 0xf0) >> 4 : (h & 0x0f);
            char c = cs[i];
            if (n >= 8 && c >= 'a' && c <= 'f') {
                cs[i] = Character.toUpperCase(c);
            }
        }
        return "0x" + new String(cs);
    }

    static byte[] keccak(byte[] input) {
        Keccak.DigestKeccak kd = new Keccak.Digest256();
        kd.update(input);
        return kd.digest();
    }
}
