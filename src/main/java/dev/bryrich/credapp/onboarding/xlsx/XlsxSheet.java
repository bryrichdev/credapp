package dev.bryrich.credapp.onboarding.xlsx;

import java.util.List;

public record XlsxSheet(String name, List<XlsxRow> rows) {
}
