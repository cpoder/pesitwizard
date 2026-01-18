package com.pesitwizard.fpdu;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;

public enum ParameterValueType {
    // Chaîne de caractères / String
    C {
        @Override
        public String renderValue(ParameterValue value) {
            return new String(value.getValue());
        }
    },
    // Nombre sans signe / Unsigned Number
    N {
        @Override
        public String renderValue(ParameterValue value) {
            int n = 0;
            for (byte b : value.getValue()) {
                n = (n << 8) | (b & 0xFF);
            }
            return "" + n;
        }
    },
    // Symbol
    S {
        @Override
        public String renderValue(ParameterValue value) {
            int n = 0;
            for (byte b : value.getValue()) {
                n = (n << 8) | (b & 0xFF);
            }
            return "" + n;
        }
    },
    // Masque de bits / Bitmask
    M {
        @Override
        public String renderValue(ParameterValue value) {
            int n = 0;
            for (byte b : value.getValue()) {
                n = (n << 8) | (b & 0xFF);
            }
            return Integer.toBinaryString(n);
        }
    },
    // Date/Heure / DateTime
    D {
        @Override
        public String renderValue(ParameterValue value) {
            String dateTime = new String(value.getValue());
            if (dateTime.equals("000000000000") || dateTime.isBlank()) {
                return "N/A";
            }
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyMMddHHmmss", Locale.ENGLISH);
                return LocalDateTime.parse(dateTime, formatter).toString();
            } catch (Exception e) {
                return dateTime + " (invalid)";
            }
        }
    },
    // Aggregation of submentioned types
    A {
        @Override
        public String renderValue(ParameterValue value) {
            return "0x" + HexFormat.of().formatHex(value.getValue());
        }
    };

    abstract String renderValue(ParameterValue value);
}
