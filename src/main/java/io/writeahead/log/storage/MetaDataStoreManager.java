package io.writeahead.log.storage;

import io.writeahead.log.logging.Logger;
import io.writeahead.log.logging.LoggerFactory;
import io.writeahead.log.models.WalConfiguration;
import io.writeahead.log.models.WalMetadata;
import io.writeahead.log.utils.MetadataParserUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;

public class MetaDataStoreManager implements MetadataStore {

  private final String metaPath;
  private final WalConfiguration config;

  private static final Logger log = LoggerFactory.getLogger(MetaDataStoreManager.class);

  public MetaDataStoreManager(WalConfiguration config) {
    this.config = config;
    this.metaPath = config.logDir() + "/.meta";
  }

  @Override
  public WalMetadata read() throws IOException {
    Path metadataPath = Paths.get(metaPath);
    if (!Files.exists(metadataPath)) {
      log.debug("Metadata file not found, returning empty metadata");
      return new WalMetadata(null, new ArrayList<>());
    }

    String content = new String(Files.readAllBytes(metadataPath));
    WalMetadata metadata = MetadataParserUtils.parseJson(content);
    log.debug("Metadata read successfully: {} segments", metadata.segments().size());
    return metadata;
  }

  @Override
  public void write(WalMetadata metadata) throws IOException {
    String tempPath = metaPath + ".tmp";
    Path temporaryPath = Paths.get(tempPath);
    String metadataAsJson = MetadataParserUtils.toJson(metadata);

    Files.write(temporaryPath, metadataAsJson.getBytes());
    log.debug("Metadata written to temp file");
    Files.move(
        temporaryPath,
        Paths.get(metaPath),
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING);
    log.info("Metadata persisted atomically: {} segments", metadata.segments().size());
  }
}
