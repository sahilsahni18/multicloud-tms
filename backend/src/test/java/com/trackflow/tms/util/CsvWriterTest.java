package com.trackflow.tms.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CsvWriterTest {

    @Test
    void quotesAndEscapesPerRfc4180() {
        String csv = new CsvWriter("A", "B").row("plain", "with, comma").row("say \"hi\"", null).toString();
        assertThat(csv).isEqualTo("A,B\r\nplain,\"with, comma\"\r\n\"say \"\"hi\"\"\",\r\n");
    }

    @Test
    void neutralisesSpreadsheetFormulas() {
        assertThat(CsvWriter.escape("=HYPERLINK(\"x\")")).startsWith("\"'=HYPERLINK");
        assertThat(CsvWriter.escape("+1")).isEqualTo("'+1");
        assertThat(CsvWriter.escape("@cmd")).isEqualTo("'@cmd");
        assertThat(CsvWriter.escape("TMS-4")).isEqualTo("TMS-4");
    }
}
