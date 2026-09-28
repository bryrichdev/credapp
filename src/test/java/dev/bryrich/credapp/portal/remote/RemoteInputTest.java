package dev.bryrich.credapp.portal.remote;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteInputTest {

    @Test
    void readsEventsThatLeaveOutWhatTheyDontUse() {
        RemoteInput[] events = JsonMapper.builder().build().readValue("""
                [{"type":"move","x":10,"y":12},
                 {"type":"wheel","x":1,"y":2,"dx":0,"dy":-120},
                 {"type":"key","key":"Tab","modifiers":["Shift"]},
                 {"type":"paste","text":"s3cret"}]""", RemoteInput[].class);
        assertThat(events).hasSize(4);
        assertThat(events[0].px()).isEqualTo(10);
        assertThat(events[0].clickCount()).isEqualTo(1);
        assertThat(events[1].scrollY()).isEqualTo(-120);
        assertThat(events[2].chord()).isEqualTo("Shift+Tab");
        assertThat(events[3].text()).isEqualTo("s3cret");
    }

    @Test
    void chords() {
        assertThat(key("a", "Meta").chord()).as("Cmd on a Mac is Control on the remote Linux").isEqualTo("Control+a");
        assertThat(key("a", "Control", "Meta").chord()).isEqualTo("Control+a");
        assertThat(key("Enter").chord()).isEqualTo("Enter");
        assertThat(key("F12").chord()).as("keys CredCloud doesn't pass on").isNull();
        assertThat(key("Delete", "Control", "Alt").chord()).isEqualTo("Control+Alt+Delete");
    }

    private static RemoteInput key(String key, String... modifiers) {
        return new RemoteInput("key", null, null, null, null, null, null, key, List.of(modifiers), null);
    }
}
