package dev.bryrich.credapp.onboarding;

import dev.bryrich.credapp.onboarding.xlsx.XlsxCell;
import dev.bryrich.credapp.onboarding.xlsx.XlsxWriter.Format;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * What a template column holds: how a cell becomes a value, what the template shows for
 * it, and how the instructions describe it. Conversion is forgiving about how people type
 * things into Excel (a date typed as 3/14/1980, a ZIP that lost its leading zero, "Y" for
 * yes) and strict about what it produces.
 */
public abstract class ValueType {

    /** A cell that can't be read as this type; the message is shown next to the row. */
    public static class BadValue extends Exception {
        public BadValue(String message) {
            super(message);
        }
    }

    public abstract Object convert(XlsxCell cell) throws BadValue;

    public abstract String describe();

    public Format format() {
        return Format.GENERAL;
    }

    public List<String> choices() {
        return List.of();
    }

    // ============ the types ============

    public static final ValueType TEXT = new ValueType() {
        @Override
        public Object convert(XlsxCell cell) throws BadValue {
            return plainText(cell);
        }

        @Override
        public String describe() {
            return "Text";
        }
    };

    /** A comma-separated list, stored as typed. */
    public static final ValueType LIST = new ValueType() {
        @Override
        public Object convert(XlsxCell cell) throws BadValue {
            return plainText(cell);
        }

        @Override
        public String describe() {
            return "Text; separate several with commas";
        }
    };

    /** A row's own ID, or a link to one on another sheet. Compared without case. */
    public static final ValueType KEY = new ValueType() {
        @Override
        public Object convert(XlsxCell cell) throws BadValue {
            return plainText(cell).toUpperCase(Locale.ROOT);
        }

        /** The column's note says which ID goes here. */
        @Override
        public String describe() {
            return "";
        }

        @Override
        public Format format() {
            return Format.TEXT;
        }
    };

    public static final ValueType PHONE = new ValueType() {
        @Override
        public Object convert(XlsxCell cell) throws BadValue {
            return plainText(cell);
        }

        @Override
        public String describe() {
            return "Phone number";
        }

        @Override
        public Format format() {
            return Format.TEXT;
        }
    };

    public static final ValueType STATE = new ValueType() {
        @Override
        public Object convert(XlsxCell cell) throws BadValue {
            return plainText(cell).toUpperCase(Locale.ROOT);
        }

        @Override
        public String describe() {
            return "Two-letter state code, such as OH";
        }

        @Override
        public Format format() {
            return Format.TEXT;
        }
    };

    public static final ValueType ZIP = new ValueType() {
        @Override
        public Object convert(XlsxCell cell) throws BadValue {
            // Excel drops the leading zero from 02134 when the cell is a number.
            if (cell.kind() == XlsxCell.Kind.NUMBER && cell.text().matches("\\d{1,5}")) {
                return "0".repeat(5 - cell.text().length()) + cell.text();
            }
            return plainText(cell);
        }

        @Override
        public String describe() {
            return "5-digit or ZIP+4";
        }

        @Override
        public Format format() {
            return Format.TEXT;
        }
    };

    public static final ValueType TAXONOMY = new ValueType() {
        @Override
        public Object convert(XlsxCell cell) throws BadValue {
            return plainText(cell).toUpperCase(Locale.ROOT);
        }

        @Override
        public String describe() {
            return "NUCC taxonomy code, such as 207Q00000X";
        }

        @Override
        public Format format() {
            return Format.TEXT;
        }
    };

    public static final ValueType DATE = new ValueType() {
        private final List<DateTimeFormatter> formats = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("M/d/uuuu").withResolverStyle(ResolverStyle.STRICT),
                DateTimeFormatter.ofPattern("M-d-uuuu").withResolverStyle(ResolverStyle.STRICT),
                DateTimeFormatter.ofPattern("uuuu/M/d").withResolverStyle(ResolverStyle.STRICT));

        @Override
        public Object convert(XlsxCell cell) throws BadValue {
            if (cell.date() != null) {
                return cell.date();
            }
            if (cell.kind() == XlsxCell.Kind.NUMBER && cell.number() != null) {
                // A date typed into a cell that lost its date format is still a day count.
                LocalDate date = XlsxCell.serialToDate(cell.number(), false);
                if (date != null && date.getYear() >= 1900) {
                    return date;
                }
            }
            String text = plainText(cell);
            for (DateTimeFormatter format : formats) {
                try {
                    return LocalDate.parse(text, format);
                } catch (DateTimeParseException ignored) {
                    // try the next one
                }
            }
            throw new BadValue("\"" + text + "\" isn't a date. Use 2025-01-31 or 1/31/2025");
        }

        @Override
        public String describe() {
            return "Date, such as 2025-01-31";
        }

        @Override
        public Format format() {
            return Format.DATE;
        }
    };

    public static final ValueType YES_NO = new ValueType() {
        @Override
        public Object convert(XlsxCell cell) throws BadValue {
            if (cell.kind() == XlsxCell.Kind.BOOLEAN) {
                return "TRUE".equals(cell.text());
            }
            String text = plainText(cell).toLowerCase(Locale.ROOT);
            return switch (text) {
                case "yes", "y", "true", "1", "x" -> true;
                case "no", "n", "false", "0" -> false;
                default -> throw new BadValue("\"" + plainText(cell) + "\" should be Yes or No");
            };
        }

        @Override
        public String describe() {
            return "Yes or No";
        }

        @Override
        public List<String> choices() {
            return List.of("Yes", "No");
        }
    };

    /** 25 means 25%. A cell formatted as a percentage (stored as 0.25) reads the same. */
    public static final ValueType PERCENT = new ValueType() {
        @Override
        public Object convert(XlsxCell cell) throws BadValue {
            if (cell.kind() == XlsxCell.Kind.PERCENT && cell.number() != null) {
                return cell.number().movePointRight(2).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros();
            }
            if (cell.number() != null) {
                return cell.number();
            }
            String text = plainText(cell).replace("%", "").trim();
            try {
                return new BigDecimal(text);
            } catch (NumberFormatException ex) {
                throw new BadValue("\"" + plainText(cell) + "\" isn't a percentage. Use 25 for 25%");
            }
        }

        @Override
        public String describe() {
            return "Percentage; 25 means 25%";
        }
    };

    public static final ValueType MONEY = new ValueType() {
        @Override
        public Object convert(XlsxCell cell) throws BadValue {
            if (cell.number() != null) {
                return cell.number();
            }
            String text = plainText(cell).replaceAll("[$,\\s]", "");
            try {
                return new BigDecimal(text);
            } catch (NumberFormatException ex) {
                throw new BadValue("\"" + plainText(cell) + "\" isn't an amount. Use 1000000 or $1,000,000");
            }
        }

        @Override
        public String describe() {
            return "Dollar amount";
        }
    };

    /** A fixed-length number kept as text: NPI, tax ID, SSN. Leading zeros are restored. */
    public static ValueType digits(int length, String name) {
        return new ValueType() {
            @Override
            public Object convert(XlsxCell cell) throws BadValue {
                String text = plainText(cell);
                if (cell.kind() == XlsxCell.Kind.NUMBER && text.matches("\\d+") && text.length() < length) {
                    return "0".repeat(length - text.length()) + text;
                }
                // Dashes and spaces are how people write these; the stored form has none.
                return text.replaceAll("[\\s-]", "");
            }

            @Override
            public String describe() {
                return name + ", " + length + " digits";
            }

            @Override
            public Format format() {
                return Format.TEXT;
            }
        };
    }

    /**
     * One of a fixed set. labels are what the dropdown offers, in order; aliases are other
     * spellings people use. Matching ignores case, spaces and punctuation.
     */
    public static <E extends Enum<E>> ValueType choice(Class<E> type, Function<E, String> label,
                                                       Map<String, E> aliases) {
        Map<String, E> byName = new LinkedHashMap<>();
        List<String> labels = new java.util.ArrayList<>();
        for (E constant : type.getEnumConstants()) {
            labels.add(label.apply(constant));
            byName.put(normalize(label.apply(constant)), constant);
            byName.put(normalize(constant.name()), constant);
        }
        aliases.forEach((alias, constant) -> byName.put(normalize(alias), constant));
        return new ValueType() {
            @Override
            public Object convert(XlsxCell cell) throws BadValue {
                E match = byName.get(normalize(plainText(cell)));
                if (match == null) {
                    throw new BadValue("\"" + plainText(cell) + "\" isn't one of: " + String.join(", ", labels));
                }
                return match;
            }

            @Override
            public String describe() {
                return "One of: " + String.join(", ", labels);
            }

            @Override
            public List<String> choices() {
                return labels;
            }
        };
    }

    public static <E extends Enum<E>> ValueType choice(Class<E> type) {
        return choice(type, ValueType::titleCase, Map.of());
    }

    // ============ helpers ============

    static String titleCase(Enum<?> constant) {
        String words = constant.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** The cell as text: numbers without a trailing .0, and never an Excel error code. */
    static String plainText(XlsxCell cell) throws BadValue {
        if (cell.kind() == XlsxCell.Kind.ERROR) {
            throw new BadValue("The cell shows an Excel error (" + cell.text() + ")");
        }
        return cell.text().strip();
    }
}
