package org.jabref.logic.journals;

import java.net.MalformedURLException;
import java.net.URL;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.jabref.logic.net.URLDownload;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PredatoryJournalLoader {
    private static class PJSource {
        final URL URL;
        final String ELEMENT_REGEX;

        PJSource(String URL, String ELEMENT_REGEX) throws MalformedURLException {
            this.URL = new URL(URL);
            this.ELEMENT_REGEX = ELEMENT_REGEX;
        }
    }

    private static final Logger                 LOGGER              = LoggerFactory.getLogger(PredatoryJournalLoader.class);
    private static List<PJSource>               PREDATORY_SOURCES;
    private static List<String>                 linkElements;
    private static PredatoryJournalRepository   repository;

    public PredatoryJournalLoader() {
        try {
            PREDATORY_SOURCES = List.of(
                new PJSource("https://raw.githubusercontent.com/stop-predatory-journals/stop-predatory-journals.github.io/master/_data/journals.csv",
                            null),
                /*
                new PJSource("https://raw.githubusercontent.com/stop-predatory-journals/stop-predatory-journals.github.io/master/_data/hijacked.csv",
                            null,
                            null,
                            "journal", "journalname", "bookname"),
                */
                new PJSource("https://raw.githubusercontent.com/stop-predatory-journals/stop-predatory-journals.github.io/master/_data/publishers.csv",
                            null),
                new PJSource("https://beallslist.net/",
                            "<li>.*?</li>"),
                new PJSource("https://beallslist.net/standalone-journals/",
                            "<li>.*?</li>"),
                new PJSource("https://beallslist.net/hijacked-journals/",
                            "<tr>.*?</tr>")
            );
        }
        catch (MalformedURLException ex) { logException(ex); }
        this.linkElements = new ArrayList<>();
    }

    public static PredatoryJournalRepository loadRepository() {
        // Initialize in-memory repository
        repository = new PredatoryJournalRepository();

        // Update from external sources
        update();

        return repository;
    }

    private static void update() {
        PREDATORY_SOURCES   .forEach(PredatoryJournalLoader::crawl);            // populates linkElements (and predatoryJournals if CSV)
        linkElements        .forEach(PredatoryJournalLoader::clean);            // adds cleaned HTML to predatoryJournals

        LOGGER.info("UPDATED PREDATORY JOURNAL LIST");
    }

    private static void crawl(PJSource source) {
        try {
            URLDownload download = new URLDownload(source.URL);

            if (!download.canBeReached())                   { LOGGER.warn("URL UNREACHABLE"); }
            else if (source.URL.getPath().contains(".csv")) { handleCSV(new InputStreamReader(download.asInputStream())); }
            else                                            { handleHTML(source.ELEMENT_REGEX, download.asString()); }
        }
        catch (IOException ex) { logException(ex); }
    }

    private static void handleCSV(Reader reader) throws IOException {
        CSVParser csvParser = new CSVParser(reader, CSVFormat.EXCEL);

        for (CSVRecord record : csvParser) {
            repository.addToPredatoryJournals(record.get(1), record.get(2), record.get(0));    // changes column order from CSV (source: url, name, abbr)
        }

    }

    private static void handleHTML(String regex, String body) {
        var pattern = Pattern.compile(regex);
        var matcher = pattern.matcher(body);

        while (matcher.find()) linkElements.add(matcher.group());
    }

    private static void clean(String item) {
        var m_name = Pattern.compile("(?<=\">).*?(?=<)").matcher(item);
        var m_url  = Pattern.compile("http.*?(?=\")").matcher(item);
        var m_abbr = Pattern.compile("(?<=\\()[^\s]*(?=\\))").matcher(item);

        // using `if` gets only first link in element, `while` gets all, but this may not be desirable
        // e.g. this way only the hijacked journals are recorded and not the authentic originals
        if (m_name.find() && m_url.find()) repository.addToPredatoryJournals(m_name.group(), m_abbr.find() ? m_abbr.group() : "", m_url.group());
    }

    private static void logException(Exception ex) { if (LOGGER.isErrorEnabled()) LOGGER.error(ex.getMessage(), ex); }
}
