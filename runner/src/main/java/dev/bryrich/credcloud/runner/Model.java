package dev.bryrich.credcloud.runner;

import java.util.List;
import java.util.Map;

/** The JSON the CredCloud server sends and takes. Mirrors the server's portal package. */
final class Model {

    private Model() {
    }

    /** One box on a portal and the answer it gets. See the server's PortalField. */
    record Field(String label, String by, String locator, String kind, String source, String format,
                 String defaultValue, String page) {
    }

    record Answer(int field, String value) {
    }

    record Source(String key, String label) {
    }

    record Job(long id, String kind, long templateId, String templateName, String payerName, String startUrl,
               int revision, List<Field> fields, String providerName, List<Answer> answers, List<Source> sources,
               Map<String, String> formats) {

        boolean fill() {
            return "fill".equals(kind);
        }
    }
}
