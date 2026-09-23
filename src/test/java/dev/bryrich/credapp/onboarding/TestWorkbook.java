package dev.bryrich.credapp.onboarding;

import dev.bryrich.credapp.onboarding.OnboardingTemplate.Column;
import dev.bryrich.credapp.onboarding.OnboardingTemplate.Sheet;
import dev.bryrich.credapp.onboarding.xlsx.XlsxWriter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a filled-in onboarding workbook for tests: the real template's sheets and headers,
 * with rows given as header/value pairs. Values are written as text, the way a person
 * typing into Excel's text-formatted cells would leave them.
 */
final class TestWorkbook {

    private final Map<Sheet, List<Map<String, String>>> rows = new LinkedHashMap<>();
    private final List<XlsxWriter.TableSheet> extraSheets = new ArrayList<>();

    /** pairs: header, value, header, value... Headers are the template's, without the " *". */
    TestWorkbook row(Sheet sheet, String... pairs) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            row.put(pairs[i], pairs[i + 1]);
        }
        rows.computeIfAbsent(sheet, key -> new ArrayList<>()).add(row);
        return this;
    }

    /** A sheet with any headers at all, for testing what the parser does with the unexpected. */
    TestWorkbook rawSheet(String name, List<String> headers, List<List<String>> values) {
        extraSheets.add(new XlsxWriter.TableSheet(name, headers.stream()
                .map(header -> new XlsxWriter.Column(header, 12, false, XlsxWriter.Format.GENERAL, List.of()))
                .toList(), values));
        return this;
    }

    byte[] bytes() {
        List<XlsxWriter.Sheet> sheets = new ArrayList<>();
        for (Sheet sheet : OnboardingTemplate.SHEETS) {
            List<Column> columns = sheet.columns();
            List<List<String>> values = new ArrayList<>();
            for (Map<String, String> row : rows.getOrDefault(sheet, List.of())) {
                for (String header : row.keySet()) {
                    if (columns.stream().noneMatch(column -> column.header().equals(header))) {
                        throw new IllegalArgumentException(sheet.name() + " has no column " + header);
                    }
                }
                values.add(columns.stream().map(column -> row.get(column.header())).toList());
            }
            sheets.add(new XlsxWriter.TableSheet(sheet.name(), columns.stream()
                    .map(column -> new XlsxWriter.Column(column.header(), column.width(), column.required(),
                            column.type().format(), column.type().choices()))
                    .toList(), values));
        }
        sheets.addAll(extraSheets);
        return XlsxWriter.write(sheets);
    }
}
