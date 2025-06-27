package no.elixir.e2eTests.core;

import no.elixir.crypt4gh.util.KeyUtils;
import no.elixir.e2eTests.config.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class State {

    public static final Environment env = new Environment();
    public static final Logger log = LoggerFactory.getLogger(State.class);
    public static final KeyUtils keyUtils = KeyUtils.getInstance();

    public static File rawFile;
    public static File encFile;
    public static String rawSHA256Checksum;
    public static String encSHA256Checksum;
    public static String rawMD5Checksum;
    public static String stableId;
    public static String datasetId;
    public static String archivePath;
    public static String correlationId;

}
